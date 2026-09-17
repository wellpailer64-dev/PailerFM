package com.pailer.localtune.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.Normalizer
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Locale

data class RemoteApprovedBulletin(
    val id: String,
    val script: RadioScript,
    val audioFile: File,
)

class BroadcastFeedRepository(private val context: Context) {
    // HttpURLConnection.connect()/getInputStream() sao chamadas bloqueantes de Java puro, sem
    // ponto de suspensao - cancelar a coroutine (ex.: withTimeoutOrNull no chamador) NAO
    // interrompe essa thread sozinha, ela so retorna quando a chamada de rede realmente devolver
    // o controle. Descoberto ao vivo (16/09/2026): uma rede com DNS/handshake travado prendia
    // essa chamada por minutos mesmo com connectTimeout/readTimeout de 12s configurados E um
    // withTimeoutOrNull de 15s no chamador - nenhum dos dois bastava sozinho. Guardamos a conexao
    // ativa aqui e, se a coroutine for cancelada (timeout do chamador), forcamos disconnect() nela
    // pra destravar a thread com uma IOException de verdade.
    @Volatile
    private var activeConnection: HttpURLConnection? = null

    suspend fun downloadNextApprovedBulletin(
        targetDir: File,
        reservedKeys: Set<String>,
    ): RemoteApprovedBulletin? = withContext(Dispatchers.IO) {
        val cancelWatchdog = coroutineContext.job.invokeOnCompletion { cause ->
            if (cause is CancellationException) {
                runCatching { activeConnection?.disconnect() }
            }
        }
        try {
            downloadNextApprovedBulletinBlocking(targetDir, reservedKeys)
        } finally {
            cancelWatchdog.dispose()
        }
    }

    private fun downloadNextApprovedBulletinBlocking(
        targetDir: File,
        reservedKeys: Set<String>,
    ): RemoteApprovedBulletin? {
        return runCatching {
            targetDir.mkdirs()
            val manifest = JSONObject(fetchText(MANIFEST_URL))
            val items = manifest.optJSONArray("items") ?: JSONArray()
            // Especial (recado/publi por pedido direto, content_type == "especial") fura fila de
            // DOWNLOAD tambem, nao so de reproducao (ver ADR-037 em docs/DECISIONS.md) - senao ele
            // so seria baixado quando chegasse a vez dele na ordem do manifest, que o painel Python
            // intercala por categoria sem saber de prioridade nenhuma. Duas passadas preservando a
            // ordem relativa de cada grupo: especiais primeiro, resto do feed depois.
            val ordered = (0 until items.length()).mapNotNull { items.optJSONObject(it) }
                .sortedByDescending { it.optString("content_type") == "especial" }
            for (item in ordered) {
                val bulletin = tryDownloadApprovedItem(item, targetDir, reservedKeys)
                if (bulletin != null) return@runCatching bulletin
            }
            null
        }.onFailure {
            Log.w(TAG, "feed remoto de boletins indisponivel", it)
        }.getOrNull()
    }

    private fun tryDownloadApprovedItem(
        item: JSONObject,
        targetDir: File,
        reservedKeys: Set<String>,
    ): RemoteApprovedBulletin? {
        if (item.optString("status") != "approved") return null
        if (item.optString("expires_at").isExpired()) return null
        val id = item.optString("id").trim()
        if (id.isBlank()) return null
        val title = item.optString("title").ifBlank { item.optString("slug").ifBlank { id } }
        val category = item.optString("category").ifBlank { "geral" }
        val isSpecial = item.optString("content_type") == "especial"
        // Precisa reproduzir EXATAMENTE RadioScript.newsReservationKey() (LocalTuneViewModel.kt):
        // source e title normalizados SEPARADAMENTE e so depois unidos por "|". Normalizar a
        // string inteira de uma vez (source+"|"+title) destroi o "|" junto com os outros
        // separadores (normalizedRemoteKey troca tudo que nao e a-z0-9 por espaco) e a chave
        // nunca bate com nada - foi assim que, ao vivo (16/09/2026), o mesmo boletim aprovado
        // foi baixado 10x seguidas pro buffer inteiro em vez de variar entre os itens do feed.
        val source = "Pailer FM Broadcast · $category"
        val reservationKey = "${source.normalizedRemoteKey()}|${title.normalizedRemoteKey()}"
        if (reservationKey in reservedKeys) return null
        val audio = item.optJSONObject("audio") ?: return null
        val audioPath = audio.optString("path").takeIf { it.isNotBlank() } ?: return null
        val extension = audio.optString("format").ifBlank { audioPath.substringAfterLast('.', "wav") }
            .lowercase(Locale.ROOT)
            .filter { it.isLetterOrDigit() }
            .ifBlank { "wav" }
        val expectedBytes = audio.optLong("bytes", -1L)
        val expectedSha256 = audio.optString("sha256").takeIf { it.isNotBlank() }
        val audioFile = targetDir.resolve("broadcast_${id}.$extension")
        if (!audioFile.isUsableAudio(expectedBytes, expectedSha256)) {
            downloadFile(resolveFeedUrl(audioPath), audioFile)
        }
        if (!audioFile.isUsableAudio(expectedBytes, expectedSha256)) return null

        val lines = item.optJSONObject("script")
            ?.optString("path")
            ?.takeIf { it.isNotBlank() }
            ?.let { path -> runCatching { parseScriptLines(fetchText(resolveFeedUrl(path))) }.getOrNull() }
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(
                RadioScriptLine(
                    RadioSpeaker.Female,
                    "Boletim aprovado da Pailer FM: $title",
                ),
            )
        val script = RadioScript(
            story = NewsStory(
                title = title,
                source = "Pailer FM Broadcast · $category",
                summary = "Boletim aprovado no feed remoto. ID: $id.",
            ),
            lines = lines,
            source = RadioScriptSource.BroadcastFeed,
            duration = durationFromSeconds(audio.optDouble("duration_seconds", 0.0)),
            isSpecial = isSpecial,
        )
        return RemoteApprovedBulletin(id, script, audioFile)
    }

    private fun fetchText(url: String): String {
        val connection = openConnection(url)
        try {
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadFile(url: String, target: File) {
        val temp = File(target.parentFile, "${target.name}.part")
        val connection = openConnection(url)
        try {
            connection.inputStream.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
        if (target.exists()) target.delete()
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
    }

    private fun openConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = NETWORK_TIMEOUT_MS
            readTimeout = NETWORK_TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("User-Agent", "PailerPlayer/0.1")
            activeConnection = this
        }

    private fun parseScriptLines(json: String): List<RadioScriptLine> {
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { index ->
            val line = array.optJSONObject(index) ?: return@mapNotNull null
            val text = line.optString("text").trim()
            if (text.isBlank()) return@mapNotNull null
            RadioScriptLine(
                speaker = when (line.optString("speaker").trim().lowercase(Locale.ROOT)) {
                    "nico", "male", "locutor" -> RadioSpeaker.Male
                    else -> RadioSpeaker.Female
                },
                text = text,
            )
        }
    }

    private fun durationFromSeconds(seconds: Double): RadioBulletinDuration =
        when {
            seconds >= 40.0 -> RadioBulletinDuration.Long
            seconds >= 25.0 -> RadioBulletinDuration.Normal
            else -> RadioBulletinDuration.Short
        }

    private fun resolveFeedUrl(path: String): String =
        if (path.startsWith("http://") || path.startsWith("https://")) {
            path
        } else {
            "${BASE_URL}/${path.trimStart('/')}"
        }

    private fun File.isUsableAudio(expectedBytes: Long, expectedSha256: String?): Boolean {
        if (!exists() || length() <= WAV_HEADER_SIZE) return false
        if (expectedBytes > WAV_HEADER_SIZE && length() != expectedBytes) return false
        if (expectedSha256 != null && sha256() != expectedSha256) return false
        return true
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun String.normalizedRemoteKey(): String =
        Normalizer.normalize(this, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    // "expires_at" vem do manifest como ISO8601 UTC (ex.: 2026-09-18T00:34:25Z).
    // Sem o campo, ou se nao der pra ler, trata como nao vencido (fail-open) -
    // melhor baixar um item sem data do que esconder o feed inteiro por um
    // formato inesperado.
    private fun String.isExpired(): Boolean {
        if (isBlank()) return false
        return try {
            Instant.parse(this).isBefore(Instant.now())
        } catch (_: DateTimeParseException) {
            false
        }
    }

    private companion object {
        const val BASE_URL = "https://pailer-fm-boletins.well-pailer64.workers.dev"
        const val MANIFEST_URL = "$BASE_URL/manifest.json"
        const val NETWORK_TIMEOUT_MS = 12_000
        const val WAV_HEADER_SIZE = 44L
        const val TAG = "PailerBroadcastFeed"
    }
}
