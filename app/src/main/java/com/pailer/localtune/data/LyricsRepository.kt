package com.pailer.localtune.data

import android.content.Context
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// Letra das musicas. Offline primeiro (ADR-002/004): letra colada/editada pelo usuario ou
// buscada online tem prioridade sobre a tag embutida no arquivo; a rede so entra sob demanda
// (fetchOnline, chamado por um botao). A letra NUNCA e gravada de volta nos arquivos de audio
// do usuario (ADR-008, decisao do usuario) - mora so em:
//
//   filesDir/lyrics/<songId>.lrc     texto verbatim (LRC ou texto puro)
//   filesDir/lyrics/index.json       { "<songId>": { source, synced, updatedAt, sig, provider } }
//
// Mesmo padrao de filesDir/artist_photos (MusicLibraryRepository.applyArtistPhoto) e de
// library_cache.json - arquivo por item + JSON manual (org.json), sem lib de serializacao.
// A pasta inteira entra no backup (BackupRepository), igual artist_photos.
class LyricsRepository(private val context: Context) {

    private val dir: File
        get() = File(context.filesDir, DIR_NAME).apply { mkdirs() }

    private val indexFile: File
        get() = File(dir, INDEX_FILE)

    // Serializa o read-modify-write do index.json (save/remove/fetchOnline concorrentes).
    private val indexMutex = Mutex()

    // --- Leitura ---

    // Offline: arquivo salvo (MANUAL/LRCLIB/EMBEDDED cacheada) -> tag embutida no arquivo (e
    // cacheia) -> NONE. Nunca toca na rede.
    suspend fun load(song: LocalSong): Lyrics = withContext(Dispatchers.IO) {
        runCatching {
            readStored(song)?.let { return@runCatching it }

            val embedded = readEmbedded(song)
            if (embedded != null && embedded.rawText.isNotBlank()) {
                // Cacheia pra abrir rapido depois e entrar no backup (sobrevive a reinstalacao).
                runCatching { writeFile(song, embedded.rawText, LyricsSource.EMBEDDED, provider = "tag") }
                return@runCatching parseInto(song.id, embedded.rawText, LyricsSource.EMBEDDED)
            }

            Lyrics.EMPTY.copy(songId = song.id)
        }.getOrDefault(Lyrics.EMPTY.copy(songId = song.id))
    }

    private fun readStored(song: LocalSong): Lyrics? {
        val file = lyricFile(song.id)
        if (!file.exists()) return null
        val raw = runCatching { file.readText() }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        val entry = runCatching { readIndex().optJSONObject(song.id.toString()) }.getOrNull()
        val source = entry?.optString("source")
            ?.let { runCatching { LyricsSource.valueOf(it) }.getOrNull() }
            ?: LyricsSource.MANUAL
        val updatedAt = entry?.optLong("updatedAt", 0L) ?: 0L
        return parseInto(song.id, raw, source, updatedAt)
    }

    // Copia o arquivo de audio pra um temp no cacheDir e le a tag de letra (ID3 USLT / equivalente),
    // mesmo idioma de MusicLibraryRepository.writeTagsToAudioFile - AudioFileIO precisa de um File
    // real, nao consegue ler de um content:// Uri. So LEITURA: nunca chama audioFile.commit().
    private data class Embedded(val rawText: String)

    private fun readEmbedded(song: LocalSong): Embedded? {
        val extension = audioExtension(song).lowercase()
        if (extension !in SUPPORTED_TAG_EXTENSIONS) return null

        val tempDir = File(context.cacheDir, "lyrics_read").apply { mkdirs() }
        val tempFile = File(tempDir, "song_${song.id}.$extension")
        return try {
            context.contentResolver.openInputStream(song.contentUri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return null

            val audioFile = AudioFileIO.read(tempFile)
            val tag = audioFile.tag ?: return null
            val text = runCatching { tag.getFirst(FieldKey.LYRICS) }.getOrNull()?.trim()
            if (text.isNullOrBlank()) null else Embedded(text)
        } catch (t: Throwable) {
            Log.w(TAG, "Falha ao ler letra embutida de ${song.title}", t)
            null
        } finally {
            runCatching { tempFile.delete() }
        }
    }

    // --- Busca online (LRCLIB primeiro, lyrics.ovh como 2a tentativa se a 1a nao achar - ambos
    // publicos, sem chave de API) ---

    // Devolve null so se as 2 tentativas falharem (miss / sem internet / erro). Hit => grava
    // <id>.lrc (LRCLIB prefere syncedLyrics; lyrics.ovh so tem texto puro, nunca sincroniza) +
    // index(source). Mesmo padrao HttpURLConnection + org.json das outras integracoes
    // (NewsBulletinRepository, AlbumGenreSuggestionRepository, GeminiFlashTtsEngine).
    suspend fun fetchOnline(song: LocalSong): Lyrics? = withContext(Dispatchers.IO) {
        fetchFromLrclib(song) ?: fetchFromLyricsOvh(song)
    }

    private suspend fun fetchFromLrclib(song: LocalSong): Lyrics? = runCatching {
        val body = lrclibGet(song) ?: lrclibSearchBestMatch(song) ?: return@runCatching null

        val synced = body.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
        val plain = body.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }
        val chosen = synced ?: plain ?: return@runCatching null
        val providerId = body.optLong("id", 0L).takeIf { it > 0L }?.let { "lrclib:$it" }

        writeFile(song, chosen, LyricsSource.LRCLIB, providerId)
        parseInto(song.id, chosen, LyricsSource.LRCLIB)
    }.getOrNull()

    private suspend fun fetchFromLyricsOvh(song: LocalSong): Lyrics? = runCatching {
        val text = lyricsOvhGet(song) ?: return@runCatching null
        writeFile(song, text, LyricsSource.LYRICS_OVH, provider = "lyrics.ovh")
        parseInto(song.id, text, LyricsSource.LYRICS_OVH)
    }.getOrNull()

    private fun lyricsOvhGet(song: LocalSong): String? {
        // lyrics.ovh usa artista/titulo como segmentos de path, nao query string - "+" do
        // URLEncoder pra espaco nao e valido ai, precisa virar %20.
        val artist = enc(primaryArtist(song.artist)).replace("+", "%20")
        val title = enc(song.title).replace("+", "%20")
        val json = httpJsonObject("$LYRICS_OVH_BASE/v1/$artist/$title") ?: return null
        return json.optString("lyrics").takeIf { it.isNotBlank() }?.trim()
    }

    private fun lrclibGet(song: LocalSong): JSONObject? {
        val params = buildString {
            append("track_name=").append(enc(song.title))
            append("&artist_name=").append(enc(primaryArtist(song.artist)))
            if (song.album.isNotBlank() && !song.album.equals("Album desconhecido", ignoreCase = true)) {
                append("&album_name=").append(enc(song.album))
            }
            if (song.durationMs > 0) append("&duration=").append(song.durationMs / 1000)
        }
        val json = httpJsonObject("$LRCLIB_BASE/api/get?$params") ?: return null
        return json.takeIf { it.has("id") || it.has("syncedLyrics") || it.has("plainLyrics") }
    }

    private fun lrclibSearchBestMatch(song: LocalSong): JSONObject? {
        val q = enc("${song.title} ${primaryArtist(song.artist)}".trim())
        val body = httpJsonArray("$LRCLIB_BASE/api/search?q=$q") ?: return null
        val targetSec = (song.durationMs / 1000).toInt()
        var best: JSONObject? = null
        var bestDelta = Int.MAX_VALUE
        for (i in 0 until body.length()) {
            val item = body.optJSONObject(i) ?: continue
            val hasLyrics = item.optString("syncedLyrics").isNotBlank() ||
                item.optString("plainLyrics").isNotBlank()
            if (!hasLyrics) continue
            val dur = item.optInt("duration", -1)
            val delta = if (dur <= 0 || targetSec <= 0) 0 else kotlin.math.abs(dur - targetSec)
            if (delta < bestDelta) {
                bestDelta = delta
                best = item
            }
        }
        // Aceita so se a duracao bate razoavelmente (ou nao ha duracao pra comparar).
        return best?.takeIf { targetSec <= 0 || bestDelta <= 8 }
    }

    // --- Escrita (letra colada/editada pelo usuario, ou cache de tag/online) ---

    suspend fun save(
        song: LocalSong,
        rawText: String,
        source: LyricsSource = LyricsSource.MANUAL,
    ): Lyrics = withContext(Dispatchers.IO) {
        runCatching {
            writeFile(song, rawText, source, provider = null)
            parseInto(song.id, rawText, source)
        }.getOrDefault(Lyrics.EMPTY.copy(songId = song.id))
    }

    suspend fun remove(songId: Long): Unit = withContext(Dispatchers.IO) {
        runCatching { lyricFile(songId).delete() }
        indexMutex.withLock {
            runCatching {
                val index = readIndex()
                index.remove(songId.toString())
                indexFile.writeText(index.toString())
            }
        }
        Unit
    }

    fun lyricsDir(): File = dir

    // --- Internos ---

    private suspend fun writeFile(
        song: LocalSong,
        rawText: String,
        source: LyricsSource,
        provider: String?,
    ) {
        val text = rawText.trim()
        lyricFile(song.id).writeText(text)
        val synced = LrcParser.isSynced(LrcParser.parse(text))
        indexMutex.withLock {
            val index = readIndex()
            index.put(
                song.id.toString(),
                JSONObject().apply {
                    put("source", source.name)
                    put("synced", synced)
                    put("updatedAt", System.currentTimeMillis())
                    put("sig", signatureOf(song))
                    if (provider != null) put("provider", provider)
                },
            )
            indexFile.writeText(index.toString())
        }
    }

    private fun readIndex(): JSONObject =
        runCatching {
            if (indexFile.exists()) JSONObject(indexFile.readText()) else JSONObject()
        }.getOrDefault(JSONObject())

    private fun parseInto(songId: Long, raw: String, source: LyricsSource, updatedAt: Long = 0L): Lyrics {
        val lines = LrcParser.parse(raw)
        val plain = LrcParser.flattenToPlain(raw)
        if (plain.isBlank()) return Lyrics.EMPTY.copy(songId = songId)
        return Lyrics(
            songId = songId,
            source = source,
            plainText = plain,
            lines = lines,
            synced = LrcParser.isSynced(lines),
            updatedAt = updatedAt,
        )
    }

    private fun lyricFile(songId: Long): File = File(dir, "$songId.lrc")

    private fun signatureOf(song: LocalSong): String =
        "${song.title.lowercase().trim()}|${song.artist.lowercase().trim()}|${song.durationMs / 1000}"

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

    private fun primaryArtist(artist: String): String =
        artist.split(ARTIST_SPLIT_REGEX).firstOrNull()?.trim()?.takeIf { it.isNotBlank() } ?: artist.trim()

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun httpConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = NETWORK_TIMEOUT_MS
            readTimeout = NETWORK_TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json")
        }

    private fun httpJsonObject(url: String): JSONObject? {
        val connection = httpConnection(url)
        return runCatching {
            if (connection.responseCode !in 200..299) return@runCatching null
            connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        }.getOrNull().also { runCatching { connection.disconnect() } }
    }

    private fun httpJsonArray(url: String): JSONArray? {
        val connection = httpConnection(url)
        return runCatching {
            if (connection.responseCode !in 200..299) return@runCatching null
            connection.inputStream.bufferedReader().use { JSONArray(it.readText()) }
        }.getOrNull().also { runCatching { connection.disconnect() } }
    }

    private companion object {
        const val TAG = "PailerLyrics"
        const val DIR_NAME = "lyrics"
        const val INDEX_FILE = "index.json"
        const val LRCLIB_BASE = "https://lrclib.net"
        const val LYRICS_OVH_BASE = "https://api.lyrics.ovh"
        const val NETWORK_TIMEOUT_MS = 8_000
        const val USER_AGENT = "PailerFM/0.1 (app pessoal de musica local)"
        // Mesmo conjunto de MusicLibraryRepository.SUPPORTED_TAG_EXTENSIONS (companion privado la).
        val SUPPORTED_TAG_EXTENSIONS = setOf("mp3", "flac", "m4a", "mp4", "ogg")
        val ARTIST_SPLIT_REGEX =
            Regex("\\s*(?:,|;|/|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bfeaturing\\b|\\bwith\\b)\\s*", RegexOption.IGNORE_CASE)
    }
}
