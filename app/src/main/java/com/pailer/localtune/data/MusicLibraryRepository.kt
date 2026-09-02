package com.pailer.localtune.data

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import org.jaudiotagger.tag.reference.PictureTypes
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import kotlin.math.abs
import kotlin.random.Random

class MusicLibraryRepository(private val context: Context) {
    private val metadataPrefs = context.getSharedPreferences("metadata_overrides", Context.MODE_PRIVATE)

    init {
        if (metadataPrefs.getInt(KEY_METADATA_SCHEMA_VERSION, 0) < METADATA_SCHEMA_VERSION) {
            metadataPrefs.edit()
                .clear()
                .putInt(KEY_METADATA_SCHEMA_VERSION, METADATA_SCHEMA_VERSION)
                .apply()
        }
    }

    suspend fun loadCachedSongs(): List<LocalSong> = withContext(Dispatchers.IO) {
        readCachedSongsFromDisk()
    }

    suspend fun saveCachedSongs(songs: List<LocalSong>) = withContext(Dispatchers.IO) {
        val json = JSONArray()
        songs.forEach { song ->
            json.put(
                JSONObject()
                    .put("id", song.id)
                    .put("title", song.title)
                    .put("artist", song.artist)
                    .put("album", song.album)
                    .put("albumId", song.albumId)
                    .put("durationMs", song.durationMs)
                    .put("trackNumber", song.trackNumber)
                    .put("dateAdded", song.dateAdded)
                    .put("genre", song.genre)
                    .put("year", song.year)
            )
        }
        cacheFile.writeText(json.toString())
    }

    suspend fun loadSongs(includeDeviceGenres: Boolean = true): List<LocalSong> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val cachedGenreBySongId = if (includeDeviceGenres) {
            emptyMap()
        } else {
            readCachedSongsFromDisk()
                .filter { it.genre.isNotBlank() }
                .associate { it.id to it.genre }
        }
        val genreBySongId = if (includeDeviceGenres) loadGenreBySongId() else emptyMap()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.YEAR,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > ?"
        val selectionArgs = arrayOf("15000")
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        context.contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)
            ?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val trackColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val yearColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)

                buildList {
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val albumId = cursor.getLong(albumIdColumn)
                        val rawAlbum = cursor.getString(albumColumn).cleanUnknown("Album desconhecido")
                        val rawGenre = (genreBySongId[id] ?: cachedGenreBySongId[id]).cleanUnknown("")
                        val album = getAlbumTitleOverride(albumId, rawAlbum)
                            ?.takeIf { it.isNotBlank() }
                            ?: rawAlbum
                        val genreOverride = getAlbumGenreOverride(albumId, rawAlbum)
                        val rawArtist = cursor.getString(artistColumn).cleanUnknown("Artista desconhecido")
                        val albumArtistOverride = getAlbumArtistOverride(albumId, rawAlbum)
                        val artistOverride = metadataPrefs.getString(artistOverrideKey(rawArtist), null)
                        val uri = ContentUris.withAppendedId(collection, id)
                        add(
                            LocalSong(
                                id = id,
                                title = cursor.getString(titleColumn).cleanUnknown("Sem titulo"),
                                artist = albumArtistOverride?.takeIf { it.isNotBlank() }
                                    ?: artistOverride?.takeIf { it.isNotBlank() }
                                    ?: rawArtist,
                                album = album,
                                albumId = albumId,
                                durationMs = cursor.getLong(durationColumn),
                                trackNumber = cursor.getInt(trackColumn),
                                dateAdded = cursor.getLong(dateAddedColumn),
                                genre = genreOverride?.takeIf { it.isNotBlank() } ?: rawGenre,
                                year = cursor.getInt(yearColumn),
                                contentUri = uri,
                            )
                        )
                    }
                }
            } ?: emptyList()
    }

    private fun readCachedSongsFromDisk(): List<LocalSong> =
        runCatching {
            if (!cacheFile.exists()) return emptyList()
            val json = JSONArray(cacheFile.readText())
            val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            buildList {
                for (index in 0 until json.length()) {
                    val item = json.optJSONObject(index) ?: continue
                    val id = item.optLong("id")
                    if (id <= 0L) continue
                    add(
                        LocalSong(
                            id = id,
                            title = item.optString("title").cleanUnknown("Sem titulo"),
                            artist = item.optString("artist").cleanUnknown("Artista desconhecido"),
                            album = item.optString("album").cleanUnknown("Album desconhecido"),
                            albumId = item.optLong("albumId"),
                            durationMs = item.optLong("durationMs"),
                            trackNumber = item.optInt("trackNumber"),
                            dateAdded = item.optLong("dateAdded"),
                            genre = item.optString("genre").cleanUnknown(""),
                            year = item.optInt("year"),
                            contentUri = ContentUris.withAppendedId(collection, id),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())

    private val cacheFile: File
        get() = File(context.filesDir, "library_cache.json")

    fun artistsFrom(songs: List<LocalSong>): List<LocalArtist> =
        songs.groupBy { primaryArtistKey(it.artist) }
            .map { (_, artistSongs) ->
                val displayName = artistSongs
                    .groupingBy { primaryArtistName(it.artist) }
                    .eachCount()
                    .maxByOrNull { it.value }
                    ?.key
                    ?: artistSongs.first().artist
                LocalArtist(displayName, artistSongs.sortedBy { it.title.lowercase() })
            }
            .sortedBy { it.name.lowercase() }

    fun albumsFrom(songs: List<LocalSong>): List<LocalAlbum> =
        songs.groupBy { "${it.albumId}:${it.album}" }
            .map { (_, albumSongs) ->
                val sortedSongs = albumSongs.sortedWith(compareBy<LocalSong> { it.trackNumber }.thenBy { it.title.lowercase() })
                val first = sortedSongs.first()
                LocalAlbum(
                    id = first.albumId,
                    title = first.album,
                    artist = sortedSongs.map { it.artist }.distinct().take(2).joinToString(),
                    artworkUri = first.artworkUri,
                    songs = sortedSongs,
                )
            }
            .sortedBy { it.title.lowercase() }

    fun radiosFrom(songs: List<LocalSong>, precomputedGenreRadios: List<LocalRadio>? = null): List<LocalRadio> {
        if (songs.isEmpty()) return emptyList()
        val recent = songs.sortedByDescending { it.dateAdded }.take(RADIO_LIMIT)
        val genreRadios = (precomputedGenreRadios ?: dynamicGenreRadios(songs)).take(MAX_HOME_RADIOS)
        val grungeRadio = radioFromProfile(songs, RADIO_PROFILES.first { it.name == "Grunge" })
        val anos2000Radio = radioFromProfile(songs, RADIO_PROFILES.first { it.name == "Anos 2000" })
        val customRadios = customRadiosFrom(songs)

        val fallback = LocalRadio(
            name = "Radio recente",
            description = "${recent.size} faixas mais novas",
            songs = recent,
            coverSongs = previewCovers(recent, "recent"),
        )

        val hidden = hiddenRadioKeys()
        return (customRadios + listOfNotNull(grungeRadio, anos2000Radio) + genreRadios + fallback)
            .distinctBy { normalizeLookupKey(it.name) }
            .filterNot { normalizeLookupKey(it.name) in hidden }
    }

    // --- Radios ocultas (perfil/genero/fallback - as personalizadas usam deleteCustomRadio) ---
    // Radios de perfil/genero nao tem uma "definicao" persistida pra apagar - sao recalculadas
    // toda vez a partir da biblioteca. "Apagar" uma delas so tira da lista (chave = nome
    // normalizado, mesmo dominio de normalizeLookupKey usado em toda parte); a logica que gera
    // a radio e a vinheta por genero (VINHETA_BY_RADIO_KEY em LocalTuneViewModel, chaveada pelo
    // NOME, nao por essa lista) continuam intactas - se o usuario desocultar, a radio volta com
    // a vinheta de sempre.
    fun hiddenRadioKeys(): Set<String> {
        val json = metadataPrefs.getString(KEY_HIDDEN_RADIOS, null) ?: return emptySet()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }.toSet()
        }.getOrDefault(emptySet())
    }

    private fun saveHiddenRadioKeys(keys: Set<String>) {
        val array = JSONArray()
        keys.forEach(array::put)
        metadataPrefs.edit().putString(KEY_HIDDEN_RADIOS, array.toString()).apply()
    }

    fun hideRadio(radio: LocalRadio) {
        saveHiddenRadioKeys(hiddenRadioKeys() + normalizeLookupKey(radio.name))
        // Sessao salva e chaveada pelo nome (ver radioSessionFrom) - limpa junto, mesma logica
        // do deleteCustomRadio, pra nao deixar historico orfao se a radio for desocultada.
        metadataPrefs.edit().remove(lastRadioSessionKey(radio.name)).apply()
    }

    fun unhideAllRadios() {
        metadataPrefs.edit().remove(KEY_HIDDEN_RADIOS).apply()
    }

    // --- Radios personalizadas (a partir de album/artista) ---
    // Persistidas em metadataPrefs (mesmo SharedPreferences de overrides/sessao) como um unico
    // JSON array - lista pequena, nao precisa de schema versionado como os overrides.

    data class CustomRadioDefinition(
        val id: String,
        val name: String,
        val sourceType: String,
        // Fontes extras adicionadas depois da criacao (ver addSourceToCustomRadio) - mesmo
        // formato prefixado de `id` ("album:<id>"/"artist:<chave>"/"genre:<chave>"). Lista vazia
        // por padrao: definicoes salvas antes dessa mudanca nao tem o campo no JSON e continuam
        // funcionando igual (fonte unica).
        val extraSourceIds: List<String> = emptyList(),
    )

    fun sourceIdForAlbum(album: LocalAlbum): String = "album:${album.id}"
    fun sourceIdForArtist(artist: LocalArtist): String = "artist:${artist.key}"

    fun customRadioDefinitions(): List<CustomRadioDefinition> {
        val json = metadataPrefs.getString(KEY_CUSTOM_RADIOS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val id = obj.optString("id")
                if (id.isBlank()) return@mapNotNull null
                val extra = obj.optJSONArray("extraSourceIds")
                val extraIds = if (extra == null) emptyList() else {
                    (0 until extra.length()).mapNotNull { extra.optString(it).takeIf(String::isNotBlank) }
                }
                CustomRadioDefinition(
                    id = id,
                    name = obj.optString("name"),
                    sourceType = obj.optString("sourceType"),
                    extraSourceIds = extraIds,
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun saveCustomRadioDefinitions(definitions: List<CustomRadioDefinition>) {
        val array = JSONArray()
        definitions.forEach { definition ->
            val extra = JSONArray()
            definition.extraSourceIds.forEach(extra::put)
            array.put(
                JSONObject()
                    .put("id", definition.id)
                    .put("name", definition.name)
                    .put("sourceType", definition.sourceType)
                    .put("extraSourceIds", extra)
            )
        }
        metadataPrefs.edit().putString(KEY_CUSTOM_RADIOS, array.toString()).apply()
    }

    fun createRadioFromAlbum(album: LocalAlbum): CustomRadioDefinition {
        val id = sourceIdForAlbum(album)
        val definition = CustomRadioDefinition(id = id, name = album.title, sourceType = "album")
        saveCustomRadioDefinitions(customRadioDefinitions().filterNot { it.id == id } + definition)
        return definition
    }

    fun createRadioFromArtist(artist: LocalArtist): CustomRadioDefinition {
        val id = sourceIdForArtist(artist)
        val definition = CustomRadioDefinition(id = id, name = artist.name, sourceType = "artist")
        saveCustomRadioDefinitions(customRadioDefinitions().filterNot { it.id == id } + definition)
        return definition
    }

    // Adiciona um album/artista (sourceId no mesmo formato prefixado de CustomRadioDefinition.id
    // - ver sourceIdForAlbum/sourceIdForArtist) como fonte extra de uma radio personalizada ja
    // existente. Idempotente: repetir a mesma fonte (ou a fonte primaria) nao duplica.
    fun addSourceToCustomRadio(customId: String, sourceId: String): CustomRadioDefinition? {
        val definitions = customRadioDefinitions()
        val target = definitions.firstOrNull { it.id == customId } ?: return null
        if (sourceId == target.id || sourceId in target.extraSourceIds) return target
        val updated = target.copy(extraSourceIds = target.extraSourceIds + sourceId)
        saveCustomRadioDefinitions(definitions.map { if (it.id == customId) updated else it })
        return updated
    }

    // genre.songs vem de dynamicGenreRadios() (groupBy radioGenreKey), entao todas as musicas
    // ali compartilham a mesma chave - basta recalcular a partir de uma delas pra persistir a
    // chave interna (que pode diferir do nome de exibicao) sem precisar carregar LocalRadio
    // com um campo novo so pra isso.
    fun createRadioFromGenre(genre: LocalRadio): CustomRadioDefinition? {
        val sample = genre.songs.firstOrNull() ?: return null
        val genreKey = radioGenreKey(sample, normalizeLookupKey(sample.genre))
        val id = "genre:$genreKey"
        val definition = CustomRadioDefinition(id = id, name = genre.name, sourceType = "genre")
        saveCustomRadioDefinitions(customRadioDefinitions().filterNot { it.id == id } + definition)
        return definition
    }

    fun deleteCustomRadio(customId: String) {
        val definitions = customRadioDefinitions()
        val removed = definitions.firstOrNull { it.id == customId }
        saveCustomRadioDefinitions(definitions.filterNot { it.id == customId })
        // Sessao salva e chaveada pelo nome da radio (ver radioSessionFrom) - limpa junto pra
        // nao deixar historico orfao se o usuario recriar uma radio com o mesmo nome depois.
        if (removed != null) {
            metadataPrefs.edit().remove(lastRadioSessionKey(removed.name)).apply()
        }
    }

    // Casa um sourceId prefixado ("album:<id>"/"artist:<chave>"/"genre:<chave>") contra a
    // biblioteca - usado tanto pra fonte primaria quanto pras extras (ver customRadiosFrom).
    private fun matchSongsForSourceId(songs: List<LocalSong>, sourceId: String): List<LocalSong> = when {
        sourceId.startsWith("album:") -> {
            val albumId = sourceId.removePrefix("album:").toLongOrNull()
            songs.filter { it.albumId == albumId }
                .sortedWith(compareBy<LocalSong> { it.trackNumber }.thenBy { it.title.lowercase() })
        }
        sourceId.startsWith("artist:") -> {
            val artistKey = sourceId.removePrefix("artist:")
            songs.filter { primaryArtistKey(it.artist) == artistKey }
                .sortedBy { it.title.lowercase() }
        }
        sourceId.startsWith("genre:") -> {
            val genreKey = sourceId.removePrefix("genre:")
            songs.filter { radioGenreKey(it, normalizeLookupKey(it.genre)) == genreKey }
                .sortedBy { it.title.lowercase() }
        }
        else -> emptyList()
    }

    private fun customRadiosFrom(songs: List<LocalSong>): List<LocalRadio> {
        val definitions = customRadioDefinitions()
        if (definitions.isEmpty()) return emptyList()
        return definitions.mapNotNull { definition ->
            val sourceIds = listOf(definition.id) + definition.extraSourceIds
            val matchedSongs = if (sourceIds.size == 1) {
                // fonte unica: preserva a ordenacao por tipo (ex.: ordem de faixa de album)
                matchSongsForSourceId(songs, definition.id)
            } else {
                sourceIds.flatMap { matchSongsForSourceId(songs, it) }
                    .distinctBy { it.id }
                    .sortedBy { it.title.lowercase() }
            }
            if (matchedSongs.isEmpty()) return@mapNotNull null
            LocalRadio(
                name = definition.name,
                description = "${matchedSongs.size} faixas - radio personalizada",
                songs = matchedSongs,
                coverSongs = previewCovers(matchedSongs, "custom:${definition.id}"),
                isCustom = true,
                customId = definition.id,
                hasMultipleSources = sourceIds.size > 1,
            )
        }
    }

    fun allGenreRadios(songs: List<LocalSong>): List<LocalRadio> = dynamicGenreRadios(songs)

    fun radioSessionFrom(radio: LocalRadio): List<LocalSong> {
        // Radio personalizada de album/artista unico e uma lista fechada de poucos artistas de
        // proposito - o algoritmo de diversidade abaixo foi feito pra pools com muitos artistas
        // e cortaria uma radio de artista/album unico pra so 5 faixas (radioArtistLimit pra
        // artistCount=1). Album de um artista so toca na ordem de faixa (sequencia proposital,
        // tipo album conceitual); album "various artists" (varios artistas sob o mesmo nome de
        // album - ver LocalAlbum.isVariousArtists) NAO e uma sequencia intencional de verdade,
        // entao embaralha igual radio de artista. Artista embaralha com anti-repeticao (mesmo
        // esquema de tentativas + radioSequenceSimilarity do fluxo generico abaixo, so sem
        // buildRadioQueue) - seed fixa pelo nome dava sempre a mesma ordem a cada "Entrar".
        // Radio personalizada de categoria continua no fluxo normal abaixo - tem tantos
        // artistas quanto a radio de genero dinamica equivalente, se beneficia do mesmo algoritmo.
        // As duas checagens abaixo exigem fonte unica (!hasMultipleSources) - a partir da segunda
        // fonte (ver addSourceToCustomRadio) uma radio de album/artista vira uma mistura de
        // materiais diferentes, entao cai no fluxo generico de baixo (buildRadioQueue), igual
        // radio de categoria - "ordem de album" ou "so esse artista sem diversidade" deixam de
        // fazer sentido quando ha mais de uma fonte.
        if (radio.isCustom && !radio.hasMultipleSources && radio.customId?.startsWith("album:") == true) {
            val isVariousArtists = radio.songs.map { it.artist }.distinct().size > 1
            if (!isVariousArtists) return radio.songs
            return shuffledRadioSession(radio)
        }
        if (radio.isCustom && !radio.hasMultipleSources && radio.customId?.startsWith("artist:") == true) {
            return shuffledRadioSession(radio)
        }
        val previousIds = metadataPrefs.getString(lastRadioSessionKey(radio.name), null)
            ?.split(",")
            ?.mapNotNull { it.toLongOrNull() }
            .orEmpty()
        val attempts = (0 until RADIO_SESSION_ATTEMPTS).map { attempt ->
            buildRadioQueue(
                radio.songs,
                "${radio.name}:${System.nanoTime()}:${Random.nextLong()}:$attempt",
            ).take(RADIO_LIMIT)
        }.filter { it.isNotEmpty() }
        val session = if (previousIds.isEmpty()) {
            attempts.firstOrNull()
        } else {
            attempts.minByOrNull { radioSequenceSimilarity(it, previousIds) }
        }.orEmpty()

        if (session.isNotEmpty()) {
            metadataPrefs.edit()
                .putString(lastRadioSessionKey(radio.name), session.joinToString(",") { it.id.toString() })
                .apply()
        }
        return session
    }

    // Embaralha com anti-repeticao (varias tentativas, fica com a mais diferente da ultima
    // sessao salva) sem passar pelo buildRadioQueue de diversidade por artista - usado por
    // radio de artista e de album "various artists" em radioSessionFrom(), onde a lista de
    // artistas ja e pequena/fechada de proposito.
    private fun shuffledRadioSession(radio: LocalRadio): List<LocalSong> {
        val previousIds = metadataPrefs.getString(lastRadioSessionKey(radio.name), null)
            ?.split(",")
            ?.mapNotNull { it.toLongOrNull() }
            .orEmpty()
        val attempts = (0 until RADIO_SESSION_ATTEMPTS).map { attempt ->
            radio.songs.shuffled(Random(stableHash("${radio.name}:${System.nanoTime()}:${Random.nextLong()}:$attempt")))
        }
        val session = if (previousIds.isEmpty()) {
            attempts.first()
        } else {
            attempts.minByOrNull { radioSequenceSimilarity(it, previousIds) }.orEmpty()
        }
        metadataPrefs.edit()
            .putString(lastRadioSessionKey(radio.name), session.joinToString(",") { it.id.toString() })
            .apply()
        return session
    }

    fun albumsWithoutGenre(songs: List<LocalSong>): List<LocalAlbum> =
        albumsFrom(songs)
            .filter { it.genre.isBlank() }
            .sortedBy { it.title.lowercase() }

    fun albumsWithoutRecognizedArtist(songs: List<LocalSong>): List<LocalAlbum> =
        albumsFrom(songs)
            .filter { album -> album.songs.any { isUnknownArtist(it.artist) } }
            .sortedBy { it.title.lowercase() }

    fun availableArtistNames(songs: List<LocalSong>): List<String> =
        artistsFrom(songs)
            .map { it.name.trim() }
            .filter { it.isNotBlank() && !isUnknownArtist(it) }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }

    fun availableGenres(songs: List<LocalSong>): List<String> =
        (songs.map { it.genre.trim() }.filter { it.isNotBlank() } + DEFAULT_GENRE_CHOICES)
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }

    fun saveAlbumGenreOverride(album: LocalAlbum, genre: String) {
        metadataPrefs.edit()
            .putString(albumGenreOverrideKey(album.id, album.title), genre)
            .remove(tagWriteAppliedKey(album.id, album.title))
            .apply()
    }

    fun saveAlbumArtistOverride(album: LocalAlbum, artist: String) {
        metadataPrefs.edit()
            .putString(albumArtistOverrideKey(album.id, album.title), artist)
            .remove(tagWriteAppliedKey(album.id, album.title))
            .apply()
    }

    fun saveAlbumTitleOverride(album: LocalAlbum, title: String) {
        metadataPrefs.edit()
            .putString(albumTitleOverrideKey(album.id, album.title), title)
            .remove(tagWriteAppliedKey(album.id, album.title))
            .apply()
    }

    fun pendingTagChanges(songs: List<LocalSong>): List<PendingTagChange> =
        albumsFrom(songs).mapNotNull { album ->
            val titleOverride = getAlbumTitleOverride(album.id, album.title)
                ?.takeIf { it.isNotBlank() }
            val genreOverride = getAlbumGenreOverride(album.id, album.title)
                ?.takeIf { it.isNotBlank() }
            val artistOverride = getAlbumArtistOverride(album.id, album.title)
                ?.takeIf { it.isNotBlank() }
            val fields = buildList {
                if (!titleOverride.isNullOrBlank()) add("Album: $titleOverride")
                if (!artistOverride.isNullOrBlank()) add("Artista: $artistOverride")
                if (!genreOverride.isNullOrBlank()) add("Genero: $genreOverride")
            }
            if (fields.isEmpty()) return@mapNotNull null
            val songIds = album.songs.map { it.id }.sorted()
            val fingerprint = tagWriteFingerprint(songIds, titleOverride, artistOverride, genreOverride)
            if (metadataPrefs.getString(tagWriteAppliedKey(album.id, album.title), null) == fingerprint) {
                return@mapNotNull null
            }
            PendingTagChange(
                id = "${album.id}:${album.title}",
                albumTitle = titleOverride ?: album.title,
                artistName = artistOverride ?: album.artist,
                artworkUri = album.artworkUri,
                songCount = album.songs.size,
                fields = fields,
                songIds = songIds,
                albumValue = titleOverride,
                artistValue = artistOverride,
                genreValue = genreOverride,
                writeFingerprint = fingerprint,
            )
        }.sortedBy { it.albumTitle.lowercase() }

    fun createDeleteRequest(songs: List<LocalSong>): PendingIntent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val uris = songs.map { it.contentUri }.distinct()
        if (uris.isEmpty()) return null
        return MediaStore.createDeleteRequest(context.contentResolver, uris)
    }

    suspend fun deleteSongsDirectly(songs: List<LocalSong>): Int = withContext(Dispatchers.IO) {
        songs.distinctBy { it.id }.count { song ->
            runCatching { context.contentResolver.delete(song.contentUri, null, null) > 0 }
                .onFailure { error -> Log.w(TAG, "Falha ao apagar ${song.id} - ${song.title}", error) }
                .getOrDefault(false)
        }
    }

    fun createTagWriteRequest(changes: List<PendingTagChange>, songs: List<LocalSong>): PendingIntent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val songsById = songs.associateBy { it.id }
        val uris = changes
            .flatMap { change -> change.songIds.mapNotNull { songsById[it]?.contentUri } }
            .distinct()
        if (uris.isEmpty()) return null
        return MediaStore.createWriteRequest(context.contentResolver, uris)
    }

    suspend fun writePendingTagsToMediaStore(
        changes: List<PendingTagChange>,
        songs: List<LocalSong>,
    ): TagWriteResult = withContext(Dispatchers.IO) {
        val songsById = songs.associateBy { it.id }
        var updatedSongs = 0
        var failedSongs = 0
        val appliedChanges = mutableListOf<PendingTagChange>()

        changes.forEach { change ->
            var changeFailed = false
            for (songId in change.songIds) {
                val song = songsById[songId] ?: continue
                val updated = runCatching { writeTagsToAudioFile(song, change) }
                    .onFailure { error -> Log.w(TAG, "Falha ao gravar tags em ${song.id} - ${song.title}", error) }
                    .getOrDefault(false)
                if (updated) {
                    updatedSongs += 1
                } else {
                    failedSongs += 1
                    changeFailed = true
                }
            }
            if (!changeFailed) appliedChanges += change
        }

        if (appliedChanges.isNotEmpty()) {
            val editor = metadataPrefs.edit()
            appliedChanges.forEach { change ->
                val firstSong = change.songIds.firstNotNullOfOrNull { songsById[it] }
                if (firstSong != null) {
                    editor.putString(tagWriteAppliedKey(firstSong.albumId, firstSong.album), change.writeFingerprint)
                }
            }
            editor.apply()
        }

        TagWriteResult(
            albumCount = appliedChanges.size,
            updatedSongCount = updatedSongs,
            failedSongCount = failedSongs,
        )
    }

    private fun writeTagsToAudioFile(song: LocalSong, change: PendingTagChange): Boolean {
        val tagDir = File(context.cacheDir, "tag_write").apply { mkdirs() }
        val extension = audioExtension(song).lowercase()
        if (extension !in SUPPORTED_TAG_EXTENSIONS) {
            Log.w(TAG, "Formato sem suporte para escrita: .$extension em ${song.title}")
            return false
        }
        val tempFile = File(tagDir, "song_${song.id}.$extension")

        context.contentResolver.openInputStream(song.contentUri)?.use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
        } ?: run {
            Log.w(TAG, "Nao consegui abrir leitura para ${song.title}")
            return false
        }

        val audioFile = AudioFileIO.read(tempFile)
        val tag = audioFile.tagOrCreateAndSetDefault
        change.albumValue?.takeIf { it.isNotBlank() }?.let { tag.setField(FieldKey.ALBUM, it) }
        change.artistValue?.takeIf { it.isNotBlank() }?.let { tag.setField(FieldKey.ARTIST, it) }
        change.genreValue?.takeIf { it.isNotBlank() }?.let { tag.setField(FieldKey.GENRE, it) }
        audioFile.commit()

        context.contentResolver.openOutputStream(song.contentUri, "wt")?.use { output ->
            tempFile.inputStream().use { input -> input.copyTo(output) }
        } ?: run {
            Log.w(TAG, "Nao consegui abrir escrita para ${song.title}")
            return false
        }

        tempFile.delete()
        return true
    }

    // --- Correcao de capa de album (busca na internet + grava de verdade na faixa) ---

    fun albumsWithoutArtwork(songs: List<LocalSong>): List<LocalAlbum> =
        albumsFrom(songs)
            .filter { !hasRealArtwork(it.artworkUri) }
            .sortedBy { it.title.lowercase() }

    // O Uri de albumart do MediaStore sempre existe pra qualquer albumId > 0 - so decodificando
    // (ou tentando) da pra saber se tem uma imagem de verdade por tras ou so o placeholder do
    // sistema. inJustDecodeBounds evita alocar o bitmap inteiro so pra checar isso.
    private fun hasRealArtwork(uri: Uri?): Boolean {
        if (uri == null) return false
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            bounds.outWidth > 0 && bounds.outHeight > 0
        }.getOrDefault(false)
    }

    // iTunes Search API: publica, sem chave/autenticacao, limite informal generoso o bastante
    // pra uso pessoal. artworkUrl100 vem sempre — a troca "100x100bb" -> "600x600bb" e um truque
    // conhecido da propria Apple pra pedir uma resolucao maior do mesmo arquivo.
    // Varias tentativas em vez de uma query so: album com sufixo de edicao/remaster
    // ("Nome (Deluxe Edition)", "Nome [Remastered 2011]") quase nunca bate exato com o titulo
    // catalogado na iTunes, e artista+album colado num "term" so as vezes falha onde o titulo
    // sozinho acha. Cada tentativa roda so se a anterior nao deu resultado suficiente, e todos os
    // resultados unicos (por trackId/preview) somam nas opcoes finais - usuario pediu mais opcoes.
    suspend fun searchArtworkCandidates(album: LocalAlbum): List<ArtworkCandidate> = withContext(Dispatchers.IO) {
        val cleanTitle = cleanAlbumTitleForSearch(album.title)
        val primaryArtist = primaryArtistName(album.artist)
        val artistPlusTitle = "$primaryArtist $cleanTitle".trim()
        val queries = listOf(artistPlusTitle, cleanTitle, primaryArtist)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val seen = LinkedHashMap<String, ArtworkCandidate>()
        for (query in queries) {
            if (seen.size >= ARTWORK_CANDIDATE_LIMIT) break
            val results = runCatching { fetchItunesAlbumResults(query) }.getOrDefault(emptyList())
            results.forEach { item ->
                val preview = item.optString("artworkUrl100").takeIf { it.isNotBlank() } ?: return@forEach
                if (seen.containsKey(preview)) return@forEach
                val full = preview.replace("100x100bb", "600x600bb")
                val collectionName = item.optString("collectionName").ifBlank { album.title }
                val artistName = item.optString("artistName").ifBlank { album.artist }
                seen[preview] = ArtworkCandidate(previewUrl = preview, fullUrl = full, label = "$collectionName - $artistName")
            }
            // So tenta a proxima query (mais generica) se a atual nao trouxe nada - uma vez com
            // resultado, nao vale a pena arriscar poluir com correspondencias mais fracas.
            if (results.isNotEmpty()) break
        }
        seen.values.toList()
    }

    private fun fetchItunesAlbumResults(query: String): List<JSONObject> {
        val term = URLEncoder.encode(query, "UTF-8")
        val url = "https://itunes.apple.com/search?term=$term&entity=album&limit=$ARTWORK_CANDIDATE_LIMIT"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "PailerPlayer/0.1")
        }
        val body = connection.inputStream.use { it.readBytes().decodeToString() }
        connection.disconnect()
        val results = JSONObject(body).optJSONArray("results") ?: JSONArray()
        return (0 until results.length()).mapNotNull { results.optJSONObject(it) }
    }

    // Remove ruido de edicao/versao que raramente bate com o titulo catalogado na loja:
    // "(Deluxe Edition)", "[Remastered 2011]", "- Live", "(Bonus Track Version)" etc.
    private fun cleanAlbumTitleForSearch(title: String): String =
        title
            .replace(Regex("[\\(\\[][^)\\]]*(?:deluxe|remaster|edition|version|bonus|anniversary|expanded|live)[^)\\]]*[\\)\\]]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*-\\s*(?:deluxe|remaster(?:ed)?|live|single|ep)\\b.*", RegexOption.IGNORE_CASE), "")
            .replace(WHITESPACE_REGEX, " ")
            .trim()
            .ifBlank { title.trim() }

    suspend fun downloadArtwork(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
            }
            val bytes = connection.inputStream.use { it.readBytes() }
            connection.disconnect()
            bytes.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    fun createArtworkWriteRequest(album: LocalAlbum): PendingIntent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val uris = album.songs.map { it.contentUri }.distinct()
        if (uris.isEmpty()) return null
        return MediaStore.createWriteRequest(context.contentResolver, uris)
    }

    suspend fun applyArtworkToAlbum(album: LocalAlbum, imageBytes: ByteArray): Int = withContext(Dispatchers.IO) {
        var updated = 0
        album.songs.forEach { song ->
            val ok = runCatching { writeArtworkToAudioFile(song, imageBytes) }
                .onFailure { error -> Log.w(TAG, "Falha ao gravar capa em ${song.id} - ${song.title}", error) }
                .getOrDefault(false)
            if (ok) {
                updated += 1
                runCatching {
                    MediaScannerConnection.scanFile(context, arrayOf(pathForRescan(song)), null, null)
                }
            }
        }
        updated
    }

    private fun writeArtworkToAudioFile(song: LocalSong, imageBytes: ByteArray): Boolean {
        val tagDir = File(context.cacheDir, "tag_write").apply { mkdirs() }
        val extension = audioExtension(song).lowercase()
        if (extension !in SUPPORTED_TAG_EXTENSIONS) {
            Log.w(TAG, "Formato sem suporte para escrita: .$extension em ${song.title}")
            return false
        }
        val tempFile = File(tagDir, "art_${song.id}.$extension")

        context.contentResolver.openInputStream(song.contentUri)?.use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
        } ?: run {
            Log.w(TAG, "Nao consegui abrir leitura para ${song.title}")
            return false
        }

        val audioFile = AudioFileIO.read(tempFile)
        val tag = audioFile.tagOrCreateAndSetDefault
        val artwork = ArtworkFactory.getNew().apply {
            binaryData = imageBytes
            mimeType = sniffImageMimeType(imageBytes)
            pictureType = PictureTypes.DEFAULT_ID
        }
        runCatching { tag.deleteArtworkField() }
        tag.setField(artwork)
        audioFile.commit()

        context.contentResolver.openOutputStream(song.contentUri, "wt")?.use { output ->
            tempFile.inputStream().use { input -> input.copyTo(output) }
        } ?: run {
            Log.w(TAG, "Nao consegui abrir escrita para ${song.title}")
            return false
        }

        tempFile.delete()
        return true
    }

    private fun sniffImageMimeType(bytes: ByteArray): String = when {
        bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> "image/png"
        else -> "image/jpeg"
    }

    // MediaScannerConnection quer um path de sistema de arquivos, nao um content:// Uri - so pra
    // acelerar o MediaStore reindexar a capa nova (senao demora pro albumart Uri atualizar sozinho).
    private fun pathForRescan(song: LocalSong): String {
        val projection = arrayOf(MediaStore.Audio.Media.DATA)
        return context.contentResolver.query(song.contentUri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }.orEmpty()
    }

    private fun audioExtension(song: LocalSong): String {
        val displayName = runCatching {
            context.contentResolver.query(
                song.contentUri,
                arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
                } else {
                    null
                }
            }
        }.getOrNull().orEmpty()
        return displayName.substringAfterLast('.', missingDelimiterValue = "")
    }

    fun duplicateArtistGroups(songs: List<LocalSong>): List<DuplicateArtistGroup> =
        songs.groupBy { duplicateArtistKey(it.artist) }
            .values
            .mapNotNull { groupSongs ->
                val variants = groupSongs.map { it.artist.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                if (variants.size < 2) return@mapNotNull null
                DuplicateArtistGroup(
                    canonicalName = chooseCanonicalArtistName(groupSongs),
                    variants = variants.sortedBy { it.lowercase() },
                    totalSongs = groupSongs.size,
                )
            }
            .sortedWith(compareByDescending<DuplicateArtistGroup> { it.variants.size }.thenBy { it.canonicalName.lowercase() })

    fun saveArtistUnification(group: DuplicateArtistGroup) {
        val editor = metadataPrefs.edit()
        group.variants.forEach { variant ->
            editor.putString(artistOverrideKey(variant), group.canonicalName)
        }
        editor.apply()
    }

    @Suppress("DEPRECATION")
    private fun loadGenreBySongId(): Map<Long, String> {
        val result = mutableMapOf<Long, String>()
        val genreProjection = arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME)
        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                genreProjection,
                null,
                null,
                null,
            )?.use { genreCursor ->
                val genreIdColumn = genreCursor.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID)
                val genreNameColumn = genreCursor.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME)
                while (genreCursor.moveToNext()) {
                    val genreId = genreCursor.getLong(genreIdColumn)
                    val genreName = genreCursor.getString(genreNameColumn).cleanUnknown("")
                    if (genreName.isBlank()) continue

                    context.contentResolver.query(
                        MediaStore.Audio.Genres.Members.getContentUri("external", genreId),
                        arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID),
                        null,
                        null,
                        null,
                    )?.use { memberCursor ->
                        val audioIdColumn = memberCursor.getColumnIndexOrThrow(MediaStore.Audio.Genres.Members.AUDIO_ID)
                        while (memberCursor.moveToNext()) {
                            result[memberCursor.getLong(audioIdColumn)] = genreName
                        }
                    }
                }
            }
        }
        return result
    }

    private fun String?.cleanUnknown(fallback: String): String =
        this?.trim()
            ?.takeUnless { it.isBlank() || it.equals("<unknown>", ignoreCase = true) }
            ?: fallback

    private fun albumGenreOverrideKey(albumId: Long, album: String): String =
        "album_genre:${albumStableKey(albumId, album)}"

    private fun legacyAlbumGenreOverrideKey(albumId: Long, album: String): String =
        "$albumId:${album.lowercase().trim()}"

    private fun albumArtistOverrideKey(albumId: Long, album: String): String =
        "album_artist:${albumStableKey(albumId, album)}"

    private fun legacyAlbumArtistOverrideKey(albumId: Long, album: String): String =
        "album_artist:$albumId:${album.lowercase().trim()}"

    private fun albumTitleOverrideKey(albumId: Long, album: String): String =
        "album_title:${albumStableKey(albumId, album)}"

    private fun albumStableKey(albumId: Long, album: String): String =
        if (albumId > 0L) albumId.toString() else album.lowercase().trim()

    private fun getAlbumGenreOverride(albumId: Long, album: String): String? =
        metadataPrefs.getString(albumGenreOverrideKey(albumId, album), null)
            ?: metadataPrefs.getString(legacyAlbumGenreOverrideKey(albumId, album), null)

    private fun getAlbumArtistOverride(albumId: Long, album: String): String? =
        metadataPrefs.getString(albumArtistOverrideKey(albumId, album), null)
            ?: metadataPrefs.getString(legacyAlbumArtistOverrideKey(albumId, album), null)

    private fun getAlbumTitleOverride(albumId: Long, album: String): String? =
        metadataPrefs.getString(albumTitleOverrideKey(albumId, album), null)

    private fun tagWriteAppliedKey(albumId: Long, album: String): String =
        "tag_write_applied:${albumStableKey(albumId, album)}"

    private fun tagWriteFingerprint(
        songIds: List<Long>,
        album: String?,
        artist: String?,
        genre: String?,
    ): String =
        buildString {
            append(songIds.joinToString(","))
            append("|album=")
            append(album.orEmpty().trim())
            append("|artist=")
            append(artist.orEmpty().trim())
            append("|genre=")
            append(genre.orEmpty().trim())
        }

    private fun artistOverrideKey(artist: String): String =
        "artist:${artist.lowercase().trim()}"

    private fun isUnknownArtist(artist: String): Boolean {
        val normalized = normalizeLookupKey(artist)
        return normalized.isBlank() ||
            normalized == "artista desconhecido" ||
            normalized == "unknown" ||
            normalized == "unknown artist" ||
            normalized == "desconhecido" ||
            normalized == "sem artista"
    }

    private fun lastRadioSessionKey(radioName: String): String =
        "last_radio_session:${normalizeLookupKey(radioName)}"

    private fun radioSequenceSimilarity(queue: List<LocalSong>, previousIds: List<Long>): Int {
        val previousTop = previousIds.take(12).toSet()
        return queue.take(12).mapIndexed { index, song ->
            val samePositionPenalty = if (previousIds.getOrNull(index) == song.id) 100 else 0
            val sameOpeningPenalty = if (song.id in previousTop) 12 - index else 0
            samePositionPenalty + sameOpeningPenalty
        }.sum()
    }

    private fun buildRadioQueue(songs: List<LocalSong>, seed: String): List<LocalSong> {
        val random = Random(stableHash(seed))
        val candidates = songs
            .distinctBy { it.id }
            .map { song ->
                RadioCandidate(
                    song = song,
                    primaryArtistKey = primaryArtistKey(song.artist),
                    artistKeys = artistKeys(song.artist),
                    albumKey = "${song.albumId}:${song.album.lowercase()}",
                    genreKey = radioGenreKey(song, normalizeLookupKey(song.genre)),
                )
            }

        if (candidates.isEmpty()) return emptyList()

        val artistQueues = candidates
            .groupBy { it.primaryArtistKey }
            .mapValues { (_, artistSongs) ->
                artistSongs.sortedWith(compareBy<RadioCandidate> { stableHash("${seed}:${it.song.id}") }.thenBy { it.song.title })
                    .shuffled(random)
                    .toMutableList()
            }
            .toMutableMap()
        val artistLimit = radioArtistLimit(artistQueues.size)
        val selectedByArtist = mutableMapOf<String, Int>()

        val queue = mutableListOf<RadioCandidate>()
        while (queue.size < RADIO_LIMIT && artistQueues.isNotEmpty()) {
            val hasArtistsUnderLimit = artistQueues.keys.any { (selectedByArtist[it] ?: 0) < artistLimit }
            val availableArtists = artistQueues.keys
                .filter { artistQueues[it]?.isNotEmpty() == true }
                .filter { !hasArtistsUnderLimit || (selectedByArtist[it] ?: 0) < artistLimit }

            val rankedArtists = availableArtists.sortedBy { artist ->
                val candidate = pickSongForArtist(artistQueues.getValue(artist), queue, seed)
                radioArtistScore(
                    artist = artist,
                    candidate = candidate,
                    queue = queue,
                    selectedCount = selectedByArtist[artist] ?: 0,
                    seed = seed,
                )
            }
            val nextArtist = rankedArtists.randomFromTop(random, RADIO_ARTIST_CHOICE_POOL) ?: break

            val nextSongs = artistQueues.getValue(nextArtist)
            val nextSong = pickSongForArtist(nextSongs, queue, seed)
            queue += nextSong
            selectedByArtist[nextArtist] = (selectedByArtist[nextArtist] ?: 0) + 1
            nextSongs.remove(nextSong)
            if (nextSongs.isEmpty()) artistQueues.remove(nextArtist)
        }
        return queue.map { it.song }
    }

    private fun radioArtistScore(
        artist: String,
        candidate: RadioCandidate,
        queue: List<RadioCandidate>,
        selectedCount: Int,
        seed: String,
    ): Int {
        val previous = queue.lastOrNull()
        // Janela de 4 pra 6 faixas: com poucos artistas no genero, olhar só as ultimas 3-4
        // deixava o mesmo artista voltar cedo demais mesmo sem ficar literalmente colado.
        val recentArtists = queue.takeLast(6).flatMap { it.artistKeys }.toSet()
        val recentAlbums = queue.takeLast(5).map { it.albumKey }.toSet()
        val recentGenres = queue.takeLast(4).map { it.genreKey }.toSet()
        return (selectedCount * 5_000) +
            (if (previous?.primaryArtistKey == artist) 200_000 else 0) +
            (if (candidate.artistKeys.any { it in recentArtists }) 50_000 else 0) +
            (if (candidate.albumKey in recentAlbums) 12_000 else 0) +
            (if (previous?.genreKey == candidate.genreKey) 4_000 else 0) +
            (if (candidate.genreKey in recentGenres) 1_500 else 0) +
            (stableHash("$seed:$artist:${queue.size}") % 1_000)
    }

    private fun pickSongForArtist(
        candidates: List<RadioCandidate>,
        queue: List<RadioCandidate>,
        seed: String,
    ): RadioCandidate {
        val recentArtists = queue.takeLast(6).flatMap { it.artistKeys }.toSet()
        val recentAlbums = queue.takeLast(5).map { it.albumKey }.toSet()
        val recentGenres = queue.takeLast(4).map { it.genreKey }.toSet()
        val previous = queue.lastOrNull()
        return candidates.sortedBy { candidate ->
            (if (candidate.artistKeys.any { it in recentArtists }) 40_000 else 0) +
                (if (candidate.albumKey in recentAlbums) 10_000 else 0) +
                (if (previous?.genreKey == candidate.genreKey) 3_000 else 0) +
                (if (candidate.genreKey in recentGenres) 1_000 else 0) +
                (stableHash("$seed:${queue.size}:${candidate.song.id}") % 1_000)
        }.randomFromTop(Random(stableHash("$seed:pick:${queue.size}")), RADIO_SONG_CHOICE_POOL) ?: candidates.first()
    }

    private fun <T> List<T>.randomFromTop(random: Random, poolSize: Int): T? {
        if (isEmpty()) return null
        val topCount = size.coerceAtMost(poolSize.coerceAtLeast(1))
        return this[random.nextInt(topCount)]
    }

    // Tetos baixos de proposito mesmo com poucos artistas no genero: a formula antiga
    // (RADIO_LIMIT / artistCount) liberava ate 10 musicas do mesmo artista numa fila de 30 se
    // o genero so tivesse 3 artistas distintos (caso real: radio "indie" dominada por um unico
    // artista) - o resultado parecia repetitivo mesmo sem duas musicas dele ficarem coladas.
    // Preferir uma sessao mais curta (buildRadioQueue para quando os artistas disponiveis
    // acabam) a uma sessao de 30 faixas inflada de repeticao do mesmo artista.
    private fun radioArtistLimit(artistCount: Int): Int = when {
        artistCount <= 0 -> RADIO_LIMIT
        artistCount >= RADIO_LIMIT -> 1
        artistCount >= 15 -> 2
        artistCount >= 10 -> 3
        artistCount >= 6 -> 4
        else -> 5
    }

    private fun primaryArtistKey(artist: String): String =
        artistKeys(artist).firstOrNull()
            ?: artist.lowercase()

    private fun primaryArtistName(artist: String): String =
        artist
            .split(ARTIST_SEPARATOR_REGEX)
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?: artist.trim()

    private fun duplicateArtistKey(artist: String): String =
        asciiFold(primaryArtistName(artist))
            .replace(ARTIST_CLEANUP_REGEX, " ")
            .replace(WHITESPACE_REGEX, " ")
            .trim()

    private fun chooseCanonicalArtistName(songs: List<LocalSong>): String =
        songs.map { primaryArtistName(it.artist) }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenByDescending { canonicalArtistQuality(it.key) }
                    .thenBy { it.key.length }
            )
            .firstOrNull()
            ?.key
            ?.trimEnd('.', ',', ';', '-', '_')
            ?.trim()
            ?: songs.first().artist

    private fun canonicalArtistQuality(name: String): Int {
        val lower = name.lowercase()
        return 100 -
            (if (lower.contains("vevo")) 50 else 0) -
            (if (lower.contains("unknown") || lower.contains("desconhecido")) 35 else 0) -
            lower.count { !it.isLetterOrDigit() && !it.isWhitespace() }
    }

    private fun asciiFold(value: String): String =
        Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
            .replace(DIACRITICS_REGEX, "")

    private fun artistKeys(artist: String): Set<String> =
        artist
            .lowercase()
            .split(ARTIST_SEPARATOR_REGEX)
            .map { it.trim() }
            .filter { it.isNotBlank() && it !in ARTIST_NOISE_WORDS }
            .toSet()
            .ifEmpty { setOf(artist.lowercase().trim()) }

    // Radio de um album "various artists" (musicas com o mesmo ALBUM_ID mas artistas
    // diferentes - ver LocalAlbum.isVariousArtists) tem so 1 entrada distinta por albumId,
    // entao complementa com diversidade por artista pra nao acabar com so 1 capa no mosaico.
    private fun previewCovers(songs: List<LocalSong>, seed: String): List<LocalSong> {
        val byAlbum = songs
            .distinctBy { it.albumId }
            .sortedBy { stableHash("$seed:${it.albumId}:${it.album}") }
        if (byAlbum.size >= 4) return byAlbum.take(4)
        val byArtist = songs
            .distinctBy { it.artist }
            .sortedBy { stableHash("$seed:${it.artist}") }
        return (byAlbum + byArtist + songs).distinct().take(4)
    }

    private fun dynamicGenreRadios(songs: List<LocalSong>): List<LocalRadio> {
        val eligibleSongs = songs.filter { it.genre.isNotBlank() }
        // Muitas musicas compartilham o mesmo texto de genero (ex.: "Rock" repetido centenas de
        // vezes); normalizar por valor distinto em vez de por musica evita milhares de chamadas
        // redundantes a Normalizer.normalize e era o maior gargalo do carregamento da biblioteca.
        val normalizedGenreByRawGenre = eligibleSongs.map { it.genre }.distinct()
            .associateWith { normalizeLookupKey(it) }
        return eligibleSongs
            .groupBy { radioGenreKey(it, normalizedGenreByRawGenre.getValue(it.genre)) }
            .mapNotNull { (genreKey, genreSongs) ->
                if (isUnknownGenreValue(genreKey)) return@mapNotNull null
                val uniqueSongs = genreSongs.distinctBy { it.id }
                if (uniqueSongs.size < MIN_RADIO_SIZE) return@mapNotNull null

                val name = radioGenreName(genreKey, uniqueSongs)
                // Contagem de artistas distintos calculada uma unica vez aqui: o comparador do
                // sortedWith abaixo roda O(n log n) vezes, e recalcular isso por comparacao
                // (Normalizer.normalize por musica) travava o carregamento com bibliotecas grandes.
                val distinctArtistCount = uniqueSongs.map { primaryArtistKey(it.artist) }.distinct().size
                distinctArtistCount to LocalRadio(
                    name = name,
                    description = "${uniqueSongs.size.coerceAtMost(RADIO_LIMIT)} faixas classificadas",
                    songs = uniqueSongs,
                    coverSongs = previewCovers(uniqueSongs, "genre:$genreKey"),
                )
            }
            .sortedWith(
                compareByDescending<Pair<Int, LocalRadio>> { it.first }
                    .thenByDescending { it.second.songs.size }
                    .thenBy { it.second.name.lowercase() }
            )
            .map { it.second }
    }

    private fun radioFromProfile(songs: List<LocalSong>, profile: RadioProfile): LocalRadio? {
        val matchedSongs = songs
            .filter(profile::matches)
            .distinctBy { it.id }
        if (matchedSongs.size < MIN_CURATED_RADIO_SIZE) return null
        return LocalRadio(
            name = profile.name,
            description = "${matchedSongs.size.coerceAtMost(RADIO_LIMIT)} faixas classificadas",
            songs = matchedSongs,
            coverSongs = previewCovers(matchedSongs, "profile:${profile.seed}"),
        )
    }

    private fun radioGenreName(genreKey: String, songs: List<LocalSong>): String =
        GENRE_DISPLAY_NAMES[genreKey]
            ?: songs.map { it.genre.trim() }
                .filter { it.isNotBlank() }
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key
            ?: genreKey.replaceFirstChar { it.titlecase() }

    // Tokens sao constantes; normalizar uma vez (lazy) evita renormalizar as mesmas ~30 palavras
    // para cada musica com genero "Unknown" (era o maior gargalo restante do carregamento).
    private val normalizedMetadataGenreHints: List<Pair<String, String>> by lazy {
        METADATA_GENRE_HINTS.map { (token, genre) -> normalizeLookupKey(token) to genre }
    }

    private fun radioGenreKey(song: LocalSong, normalizedGenre: String): String {
        if (normalizedGenre.isNotBlank() && !isUnknownGenreValue(normalizedGenre)) return genreBucket(normalizedGenre)

        val metadata = normalizeLookupKey("${song.title} ${song.artist} ${song.album}")
        return normalizedMetadataGenreHints.firstOrNull { (normalizedToken, _) -> metadata.contains(normalizedToken) }?.second
            ?: "sem genero"
    }

    private fun isUnknownGenreValue(normalizedGenre: String): Boolean =
        normalizedGenre == "unknown" ||
            normalizedGenre == "unknown genre" ||
            normalizedGenre == "desconhecido" ||
            normalizedGenre == "genero desconhecido" ||
            normalizedGenre == "sem genero"

    private fun genreBucket(normalizedGenre: String): String = when {
        normalizedGenre.contains("nu metal") -> "nu metal"
        normalizedGenre.contains("thrash") -> "thrash metal"
        normalizedGenre.contains("death metal") -> "death metal"
        normalizedGenre.contains("black metal") -> "black metal"
        normalizedGenre.contains("doom") || normalizedGenre.contains("stoner") || normalizedGenre.contains("sludge") -> "doom stoner"
        normalizedGenre.contains("heavy metal") || normalizedGenre.contains("metal") -> "metal"
        normalizedGenre.contains("post punk") || normalizedGenre.contains("cold wave") || normalizedGenre.contains("coldwave") ||
            normalizedGenre.contains("darkwave") || normalizedGenre.contains("goth") -> "post punk cold wave"
        normalizedGenre.contains("grunge") -> "grunge"
        normalizedGenre.contains("shoegaze") || normalizedGenre.contains("dream pop") -> "shoegaze dream pop"
        normalizedGenre.contains("psychedelic") || normalizedGenre.contains("psicodel") || normalizedGenre.contains("psych") -> "indie psicodelico"
        normalizedGenre.contains("indie") -> "indie"
        normalizedGenre.contains("alternative") || normalizedGenre.contains("alt rock") -> "alternative"
        normalizedGenre.contains("hard rock") -> "hard rock"
        normalizedGenre.contains("punk") -> "punk"
        normalizedGenre.contains("rock") -> "rock"
        normalizedGenre.contains("rap nacional") || normalizedGenre.contains("brazilian rap") -> "rap nacional"
        normalizedGenre.contains("hip hop") || normalizedGenre == "rap" -> "hip hop rap"
        normalizedGenre.contains("mpb") -> "mpb"
        normalizedGenre.contains("samba") -> "samba"
        normalizedGenre.contains("bossa") -> "bossa"
        normalizedGenre.contains("house") -> "house"
        normalizedGenre.contains("techno") -> "techno"
        normalizedGenre.contains("ambient") -> "ambient"
        normalizedGenre.contains("electronic") || normalizedGenre.contains("electronica") || normalizedGenre.contains("edm") ||
            normalizedGenre.contains("dance") -> "eletronica house"
        normalizedGenre.contains("jazz") -> "jazz"
        normalizedGenre.contains("soul") || normalizedGenre.contains("funk") || normalizedGenre.contains("r b") -> "soul funk"
        normalizedGenre.contains("pop") -> "pop"
        else -> normalizedGenre
    }

    private fun normalizeLookupKey(value: String): String =
        asciiFold(value)
            .replace("&", " and ")
            .replace(LOOKUP_CLEANUP_REGEX, " ")
            .replace(WHITESPACE_REGEX, " ")
            .trim()

    private fun stableHash(value: String): Int = abs(value.fold(7) { acc, char -> acc * 31 + char.code })

    private data class RadioCandidate(
        val song: LocalSong,
        val primaryArtistKey: String,
        val artistKeys: Set<String>,
        val albumKey: String,
        val genreKey: String,
    )

    private data class RadioProfile(
        val name: String,
        val seed: String,
        val genreTokens: List<String>,
        val metadataTokens: List<String> = emptyList(),
        val excludedMetadataTokens: List<String> = emptyList(),
        val requireMetadataTokenMatch: Boolean = false,
        // So usado por perfis de decada (ex.: "Anos 2000") - quando presente, o ano da musica
        // (MediaStore YEAR) precisa estar no intervalo. Perfil sem genreTokens/metadataTokens
        // e com yearRange vira um perfil "so por ano" (ver matches()).
        val yearRange: IntRange? = null,
    ) {
        // Tokens sao constantes por perfil; normalizar uma vez (lazy) em vez de a cada
        // musica evita milhares de chamadas repetidas a Normalizer.normalize durante radiosFrom().
        private val normalizedGenreTokens by lazy { genreTokens.map(::normalize) }
        private val normalizedMetadataTokens by lazy { metadataTokens.map(::normalize) }
        private val normalizedExcludedTokens by lazy { excludedMetadataTokens.map(::normalize) }

        fun matches(song: LocalSong): Boolean {
            val genre = normalize(song.genre)
            val metadata = normalize("${song.artist} ${song.album}")
            if (normalizedExcludedTokens.any { metadata.contains(it) }) return false
            if (yearRange != null && song.year !in yearRange) return false
            val metadataMatch = normalizedMetadataTokens.any { metadata.contains(it) }
            if (requireMetadataTokenMatch) return metadataMatch
            if (normalizedGenreTokens.isEmpty() && normalizedMetadataTokens.isEmpty()) {
                // Perfil so por ano: ja passou pelo filtro de yearRange acima.
                return yearRange != null
            }
            return normalizedGenreTokens.any { token -> genre.contains(token) || metadata.contains(token) } ||
                metadataMatch
        }

        private fun normalize(value: String): String =
            Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
                .replace(DIACRITICS_REGEX, "")
                .replace("&", " and ")
                .replace(LOOKUP_CLEANUP_REGEX, " ")
                .replace(WHITESPACE_REGEX, " ")
                .trim()
    }

    private companion object {
        const val KEY_METADATA_SCHEMA_VERSION = "metadata_schema_version"
        const val KEY_CUSTOM_RADIOS = "custom_radio_definitions"
        const val KEY_HIDDEN_RADIOS = "hidden_radio_keys"
        const val ARTWORK_CANDIDATE_LIMIT = 10
        const val METADATA_SCHEMA_VERSION = 2
        const val RADIO_LIMIT = 30
        const val TAG = "PailerTags"
        const val MIN_RADIO_SIZE = 8
        const val MIN_CURATED_RADIO_SIZE = 3
        const val MAX_HOME_RADIOS = 10
        const val RADIO_SESSION_ATTEMPTS = 5
        const val RADIO_ARTIST_CHOICE_POOL = 6
        const val RADIO_SONG_CHOICE_POOL = 4
        val SUPPORTED_TAG_EXTENSIONS = setOf("mp3", "flac", "m4a", "mp4", "ogg")
        val ARTIST_SEPARATOR_REGEX = Regex("\\s*(?:,|;|&|/|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bfeaturing\\b|\\bx\\b|\\be\\b|\\bwith\\b)\\s*")
        val ARTIST_CLEANUP_REGEX = Regex("\\b(?:vevo|official|topic|canal|channel)\\b|[^a-z0-9]+")
        val WHITESPACE_REGEX = Regex("\\s+")
        val DIACRITICS_REGEX = Regex("\\p{Mn}+")
        val LOOKUP_CLEANUP_REGEX = Regex("[^a-z0-9]+")
        val ARTIST_NOISE_WORDS = setOf("various artists", "vários artistas", "varios artistas", "unknown")
        val DEFAULT_GENRE_CHOICES = listOf(
            "Rock",
            "Alternative",
            "Rap nacional",
            "Hip-Hop/Rap",
            "MPB",
            "Eletronica / house",
            "Metal",
            "Nu Metal",
            "Thrash Metal",
            "Grunge",
            "Indie / psicodelico",
            "Post-punk / cold wave",
            "Jazz",
            "Soul / funk",
            "Pop",
            "Punk",
            "Samba",
        )

        val GENRE_DISPLAY_NAMES = mapOf(
            "nu metal" to "Nu Metal",
            "thrash metal" to "Thrash Metal",
            "death metal" to "Death Metal",
            "black metal" to "Black Metal",
            "doom stoner" to "Doom / stoner",
            "metal" to "Metal",
            "post punk cold wave" to "Post-punk / cold wave",
            "grunge" to "Grunge",
            "shoegaze dream pop" to "Shoegaze / dream pop",
            "indie psicodelico" to "Indie / psicodelico",
            "indie" to "Indie",
            "alternative" to "Alternative",
            "hard rock" to "Hard Rock",
            "punk" to "Punk",
            "rock" to "Rock",
            "rap nacional" to "Rap nacional",
            "hip hop rap" to "Hip-Hop/Rap",
            "mpb" to "MPB",
            "samba" to "Samba",
            "bossa" to "Bossa",
            "house" to "House",
            "techno" to "Techno",
            "ambient" to "Ambient",
            "eletronica house" to "Eletronica / house",
            "jazz" to "Jazz",
            "soul funk" to "Soul / funk",
            "pop" to "Pop",
        )

        val METADATA_GENRE_HINTS = listOf(
            "korn" to "nu metal",
            "limp bizkit" to "nu metal",
            "slipknot" to "nu metal",
            "linkin park" to "nu metal",
            "system of a down" to "nu metal",
            "deftones" to "nu metal",
            "metallica" to "thrash metal",
            "slayer" to "thrash metal",
            "megadeth" to "thrash metal",
            "anthrax" to "thrash metal",
            "sepultura" to "thrash metal",
            "radiohead" to "alternative",
            "nirvana" to "grunge",
            "alice in chains" to "grunge",
            "soundgarden" to "grunge",
            "pearl jam" to "grunge",
            "racionais" to "rap nacional",
            "facção" to "rap nacional",
            "faccao" to "rap nacional",
            "sabotage" to "rap nacional",
            "fbc" to "rap nacional",
            "emicida" to "rap nacional",
            "criolo" to "rap nacional",
            "joy division" to "post punk cold wave",
            "the cure" to "post punk cold wave",
            "bauhaus" to "post punk cold wave",
            "molchat doma" to "post punk cold wave",
            "daft punk" to "eletronica house",
            "aphex twin" to "eletronica house",
        )

        val RADIO_PROFILES = listOf(
            RadioProfile(
                name = "Anos 2000",
                seed = "anos-2000",
                genreTokens = emptyList(),
                yearRange = 2000..2009,
            ),
            RadioProfile(
                name = "Rap nacional",
                seed = "rap-br",
                genreTokens = listOf("rap nacional", "brazilian rap"),
                metadataTokens = listOf(
                    "racionais", "sabota", "facção", "faccao", "emicida", "criolo", "djonga",
                    "bk", "baco", "fbc", "don l", "costa gold", "haikaiss", "black alien",
                ),
            ),
            RadioProfile(
                name = "Nu Metal",
                seed = "nu-metal",
                genreTokens = listOf("nu metal"),
                metadataTokens = listOf("korn", "limp bizkit", "slipknot", "linkin park", "system of a down", "deftones"),
            ),
            RadioProfile(
                name = "Thrash Metal",
                seed = "thrash-metal",
                genreTokens = listOf("thrash metal", "thrash"),
                metadataTokens = listOf("metallica", "slayer", "megadeth", "anthrax", "sepultura"),
            ),
            RadioProfile(
                name = "Doom / stoner",
                seed = "doom-stoner",
                genreTokens = listOf("doom", "stoner", "sludge"),
                metadataTokens = listOf("black sabbath", "electric wizard", "sleep", "kyuss", "acid bath"),
            ),
            RadioProfile(
                name = "Metal",
                seed = "metal",
                genreTokens = listOf("metal", "nu metal", "thrash", "doom", "stoner", "sludge", "death metal", "black metal"),
                metadataTokens = listOf("black sabbath", "metallica", "slayer", "pantera", "sepultura", "korn", "limp bizkit", "slipknot", "system of a down", "linkin park"),
            ),
            RadioProfile(
                name = "Grunge",
                seed = "grunge",
                genreTokens = listOf("grunge"),
                metadataTokens = listOf(
                    "alice in chains", "nirvana", "soundgarden", "pearl jam", "stone temple pilots",
                    "mudhoney", "melvins", "silverchair", "hole", "dinosaur jr", "screaming trees",
                    "temple of the dog", "mother love bone", "tad", "l7", "babes in toyland",
                    "meat puppets", "green river", "skin yard", "malfunkshun", "mad season",
                    "candlebox", "bush", "gruntruck", "love battery", "the gits", "veruca salt",
                    "local h", "sponge", "seven mary three", "screaming life",
                ),
                excludedMetadataTokens = listOf(
                    "deftones", "shawn lee", "shwn lee", "korn", "limp bizkit", "slipknot", "linkin park",
                    "system of a down", "radiohead", "pixies", "beck", "tyler the creator", "rogerio skylab",
                    "rogério skylab", "sepultura",
                ),
                requireMetadataTokenMatch = true,
            ),
            RadioProfile(
                name = "Alternative",
                seed = "alternative",
                genreTokens = listOf("alternative", "alt rock"),
                metadataTokens = listOf("radiohead", "pixies", "beck", "smashing pumpkins", "sonic youth"),
            ),
            RadioProfile(
                name = "Punk",
                seed = "punk",
                genreTokens = listOf("punk", "punk rock", "hardcore punk"),
                metadataTokens = listOf("ramones", "dead kennedys", "bad brains", "misfits", "the clash"),
            ),
            RadioProfile(
                name = "Rock",
                seed = "rock",
                genreTokens = listOf("rock", "grunge", "alternative", "alt rock", "hard rock"),
                metadataTokens = listOf("alice in chains", "oasis", "radiohead", "arctic monkeys", "soundgarden"),
            ),
            RadioProfile(
                name = "MPB",
                seed = "mpb",
                genreTokens = listOf("mpb", "bossa", "samba"),
                metadataTokens = listOf("caetano", "gilberto gil", "gal costa", "belchior", "chico buarque", "jorge ben"),
            ),
            RadioProfile(
                name = "Eletronica / house",
                seed = "electronic-house",
                genreTokens = listOf("electronic", "electronica", "house", "techno", "dance", "edm", "ambient"),
                metadataTokens = listOf("daft punk", "chemical brothers", "aphex", "kraftwerk"),
            ),
            RadioProfile(
                name = "Indie / psicodelico",
                seed = "indie-psych",
                genreTokens = listOf("indie", "psychedelic", "psicodel", "psych", "shoegaze", "dream pop"),
                metadataTokens = listOf("tame impala", "mac demarco", "mgmt", "king gizzard", "temples"),
            ),
            RadioProfile(
                name = "Post-punk / cold wave",
                seed = "cold-wave",
                genreTokens = listOf("post-punk", "post punk", "cold wave", "coldwave", "darkwave", "new wave", "goth"),
                metadataTokens = listOf("joy division", "the cure", "bauhaus", "siouxsie", "molchat doma"),
            ),
        )
    }
}
