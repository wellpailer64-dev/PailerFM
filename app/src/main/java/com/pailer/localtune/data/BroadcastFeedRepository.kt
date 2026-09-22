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
        // Chaves "so deste buffer" usadas se a rodada normal (reservedKeys, historico completo)
        // nao devolver nada - ver comentario sobre pool esgotado do feed abaixo. Default vazio
        // preserva o comportamento antigo (sem fallback) pra quem nao passar nada, ex.
        // checkForSpecialOnAppOpen().
        fallbackReservedKeys: Set<String> = emptySet(),
        // true quando o chamador so tem vagas RESERVADAS pra especial sobrando (ver
        // BULLETIN_BUFFER_SPECIAL_RESERVED_SLOTS em LocalTuneViewModel.kt/ADR-037) - ignora
        // itens normais mesmo que existam, pra nao gastar essas vagas com conteudo comum.
        specialOnly: Boolean = false,
    ): RemoteApprovedBulletin? = withContext(Dispatchers.IO) {
        val cancelWatchdog = coroutineContext.job.invokeOnCompletion { cause ->
            if (cause is CancellationException) {
                runCatching { activeConnection?.disconnect() }
            }
        }
        try {
            downloadNextApprovedBulletinBlocking(targetDir, reservedKeys, fallbackReservedKeys, specialOnly)
        } finally {
            cancelWatchdog.dispose()
        }
    }

    private fun downloadNextApprovedBulletinBlocking(
        targetDir: File,
        reservedKeys: Set<String>,
        fallbackReservedKeys: Set<String>,
        specialOnly: Boolean,
    ): RemoteApprovedBulletin? {
        return runCatching {
            targetDir.mkdirs()
            val manifest = JSONObject(fetchText(MANIFEST_URL))
            val items = manifest.optJSONArray("items") ?: JSONArray()
            val candidates = (0 until items.length()).mapNotNull { items.optJSONObject(it) }
            // Especial (recado/publi por pedido direto, content_type == "especial") fura fila de
            // DOWNLOAD tambem, nao so de reproducao (ver ADR-037 em docs/DECISIONS.md) - senao ele
            // so seria baixado quando chegasse a vez dele na ordem do manifest, que o painel Python
            // intercala por categoria sem saber de prioridade nenhuma. Com specialOnly, ignora
            // qualquer item normal de vez (vagas reservadas nao devem ser gastas com eles);
            // sem specialOnly, ordena especiais primeiro mas ainda aceita normal (sortedByDescending
            // e estavel - preserva a ordem relativa dentro de cada grupo).
            val ordered = if (specialOnly) {
                candidates.filter { it.optString("content_type") == "especial" }
            } else {
                candidates.sortedByDescending { it.optString("content_type") == "especial" }
            }
            if (specialOnly) {
                Log.d(TAG, "specialOnly: ${ordered.size} candidato(s) especial no manifest")
            }
            for (item in ordered) {
                val bulletin = tryDownloadApprovedItem(item, targetDir, reservedKeys, logSkipReason = specialOnly)
                if (bulletin != null) return@runCatching bulletin
            }
            // Pool de boletins aprovados no feed pode ser menor que o historico anti-repeticao
            // (RECENT_BULLETIN_STORY_KEY_LIMIT em LocalTuneViewModel.kt) - sem esta saida, assim
            // que cada item do manifest ja tiver tocado uma vez, reservedKeys bloqueia o feed
            // INTEIRO pra sempre (nada novo nunca aparece pra "abrir vaga" no historico) e o
            // buffer fica vazio permanentemente, sem erro nenhum - descoberto ao vivo 18/09/2026
            // (radio parou de tocar boletim depois de uns dias). Preferimos repetir o boletim
            // menos recente a nunca mais tocar nenhum - so nao repete um item que ja esta
            // sentado no buffer AGORA (fallbackReservedKeys), mesmo criterio de fail-open ja
            // usado pra "expires_at" ausente acima.
            //
            // `candidates.shuffled()` aqui, NAO `ordered` (que poe especial primeiro) - achado ao
            // vivo 22/09/2026: com o pool do feed pequeno, esse fallback disparava TODA vez que
            // abria uma vaga, e `ordered` sempre devolvia o mesmo especial primeiro (ele passa no
            // fallbackReservedKeys fraco porque acabou de SAIR do buffer, nao porque nao jah
            // tocou) - a radio travava so nele, nunca chegando nos outros boletins aprovados
            // disponiveis. A prioridade do especial (ADR-037) e sobre CONTEUDO NOVO furar fila
            // pra tocar mais cedo - nao faz sentido nesse modo de "repetir o que ja tocou", onde
            // todos os candidatos ja sao repeticao mesmo; usuario confirmou (22/09/2026): pode
            // tocar o especial em prioridade uma vez, mas depois precisa variar entre os outros
            // disponiveis, "mesmo que antigos", em vez de sempre repetir soh ele.
            if (!specialOnly && ordered.isNotEmpty()) {
                for (item in candidates.shuffled()) {
                    val bulletin = tryDownloadApprovedItem(item, targetDir, fallbackReservedKeys, logSkipReason = false)
                    if (bulletin != null) {
                        Log.w(TAG, "boletim: pool do feed esgotado contra o historico anti-repeticao - repetindo boletim ja tocado")
                        return@runCatching bulletin
                    }
                }
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
        logSkipReason: Boolean = false,
    ): RemoteApprovedBulletin? {
        val id = item.optString("id").trim()
        if (item.optString("status") != "approved") {
            if (logSkipReason) Log.d(TAG, "specialOnly: $id pulado - status=${item.optString("status")}")
            return null
        }
        if (item.optString("expires_at").isExpired()) {
            if (logSkipReason) Log.d(TAG, "specialOnly: $id pulado - vencido")
            return null
        }
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
        if (reservationKey in reservedKeys) {
            if (logSkipReason) Log.d(TAG, "specialOnly: $id pulado - reservationKey ja em uso: $reservationKey")
            return null
        }
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
