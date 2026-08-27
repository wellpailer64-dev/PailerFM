package com.pailer.localtune.data

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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

        val fallback = LocalRadio(
            name = "Radio recente",
            description = "${recent.size} faixas mais novas",
            songs = recent,
            coverSongs = previewCovers(recent, "recent"),
        )

        return (listOfNotNull(grungeRadio) + genreRadios + fallback).distinctBy { normalizeLookupKey(it.name) }
    }

    fun allGenreRadios(songs: List<LocalSong>): List<LocalRadio> = dynamicGenreRadios(songs)

    fun radioSessionFrom(radio: LocalRadio): List<LocalSong> {
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
        val recentArtists = queue.takeLast(4).flatMap { it.artistKeys }.toSet()
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
        val recentArtists = queue.takeLast(3).flatMap { it.artistKeys }.toSet()
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

    private fun radioArtistLimit(artistCount: Int): Int = when {
        artistCount <= 0 -> RADIO_LIMIT
        artistCount >= RADIO_LIMIT -> 1
        artistCount >= 15 -> 2
        artistCount >= 10 -> 3
        else -> ((RADIO_LIMIT + artistCount - 1) / artistCount).coerceAtLeast(1)
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

    private fun previewCovers(songs: List<LocalSong>, seed: String): List<LocalSong> =
        songs
            .distinctBy { it.albumId }
            .sortedBy { stableHash("$seed:${it.albumId}:${it.album}") }
            .take(4)

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
            val metadataMatch = normalizedMetadataTokens.any { metadata.contains(it) }
            if (requireMetadataTokenMatch) return metadataMatch
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
