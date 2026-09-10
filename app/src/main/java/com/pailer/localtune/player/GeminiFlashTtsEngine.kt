package com.pailer.localtune.player

import android.content.Context
import android.util.Base64
import android.util.Log
import com.pailer.localtune.data.GeminiApiKeySettings
import com.pailer.localtune.data.GeminiTtsModel
import com.pailer.localtune.data.GeminiTtsVoiceConfig
import com.pailer.localtune.data.GeminiTtsVoices
import com.pailer.localtune.data.RadioScript
import com.pailer.localtune.data.RadioSpeaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

// Motor de sintese EXPERIMENTAL do boletim via Gemini Flash TTS (pedido do usuario 10/09/2026) -
// coexiste com LocalRadioVoiceEngine (motor de sempre), nunca o substitui. So e chamado quando
// RadioBulletinSettings.ttsProvider == GEMINI_FLASH (ver LocalTuneViewModel.
// synthesizeLocalVoiceSafely/synthesizeCoreVoiceSafely) - qualquer excecao daqui e capturada la e
// cai pro motor local de sempre, o boletim nunca fica sem audio por causa desse experimento.
//
// Sem isolamento de processo de proposito (diferente de RadioVoiceSynthesisService/:radio_voice):
// e uma chamada de rede, nao codigo nativo instavel - cancelamento cooperativo padrao do Kotlin
// (withTimeoutOrNull) ja basta, nao precisa do aparato de Service/ResultReceiver/cancelIntent que
// o motor local usa pra isolar crash de JNI.
//
// Contrato de saida IDENTICO ao LocalRadioVoiceEngine.synthesize()/synthesizeCore(): devolve um
// File? de um .wav PCM16 mono tocavel via MediaPlayer.setDataSource(path), na MESMA pasta
// (cacheDir efemero ou filesDir/CORE_BUFFER_DIR_NAME persistente) - o resto do pipeline (buffer,
// manifest, limpeza de orfaos em LocalTuneViewModel.saveCoreBufferManifest) nao precisa saber
// nem se importar com qual motor gerou o audio.
class GeminiFlashTtsEngine(private val context: Context) {
    private val geminiSettings = GeminiApiKeySettings(context)

    // onLineDone(index 1-based, total) - mesmo formato de callback que LocalRadioVoiceEngine usa,
    // pra LocalTuneViewModel poder reaproveitar a mesma mensagem de progresso "fala X de Y" ja
    // mostrada na UI (ver RadioBulletinBufferStatusCard).
    suspend fun synthesize(
        script: RadioScript,
        model: GeminiTtsModel,
        persistent: Boolean,
        onLineDone: (index: Int, total: Int) -> Unit = { _, _ -> },
    ): File? = withContext(Dispatchers.IO) {
        val apiKeys = geminiSettings.apiKeys()
        if (apiKeys.isEmpty()) {
            Log.w(TAG, "GeminiTTS: sem chave de API configurada, pulando")
            return@withContext null
        }
        Log.d(TAG, "GeminiTTS: model=${model.modelId}")
        val total = script.lines.size
        var sampleRate = DEFAULT_SAMPLE_RATE
        val chunks = script.lines.mapIndexed { index, line ->
            val speakerLabel = if (line.speaker == RadioSpeaker.Female) "Fran" else "Nico"
            val voiceConfig = if (line.speaker == RadioSpeaker.Female) GeminiTtsVoices.FRAN else GeminiTtsVoices.NICO
            Log.d(TAG, "GeminiTTS: generating $speakerLabel segment ${index + 1}")
            val (pcm, rate) = generateLineWithRetry(apiKeys, model, voiceConfig, line.text)
            sampleRate = rate
            onLineDone(index + 1, total)
            pcm
        }
        if (chunks.isEmpty() || chunks.all { it.isEmpty() }) {
            Log.w(TAG, "GeminiTTS: nenhum audio gerado")
            return@withContext null
        }
        val merged = mergeWithGaps(chunks, sampleRate)
        val outFile = outputFile(persistent)
        writeWav(outFile, merged, sampleRate)
        Log.d(TAG, "GeminiTTS: generation completed bytes=${outFile.length()} sampleRate=$sampleRate")
        outFile
    }

    private fun outputFile(persistent: Boolean): File =
        if (persistent) {
            context.filesDir.resolve(LocalRadioVoiceEngine.CORE_BUFFER_DIR_NAME).apply { mkdirs() }
                .resolve("radio_core_gemini_${System.currentTimeMillis()}.wav")
        } else {
            context.cacheDir.resolve("radio_voice_gemini_${System.currentTimeMillis()}.wav")
        }

    // Testa cada chave configurada EM ORDEM, 1 tentativa por chave (pedido do usuario 10/09/2026:
    // ate 5 chaves, "se a primeira falhar ele testa a segunda... e assim vai") - mesma politica
    // de RemoteGeminiRadioScriptWriter.generateWithRetry (RadioBulletin.kt), mesmo motivo: um
    // HTTP 429 de cota (confirmado em campo) nao se resolve batendo de novo na MESMA chave.
    // Qualquer fala que esgote todas as chaves derruba o synthesize() inteiro (excecao sobe, ver
    // acima) - nao mistura motores dentro do MESMO boletim, o fallback e sempre pro boletim
    // inteiro no motor local.
    private suspend fun generateLineWithRetry(
        apiKeys: List<String>,
        model: GeminiTtsModel,
        voiceConfig: GeminiTtsVoiceConfig,
        text: String,
    ): Pair<ByteArray, Int> {
        var lastError: Throwable? = null
        apiKeys.forEachIndexed { index, apiKey ->
            val result = runCatching {
                withTimeoutOrNull(GEMINI_TTS_TIMEOUT_MS) { callGeminiTts(apiKey, model, voiceConfig, text) }
                    ?: error("Gemini TTS demorou demais pra responder")
            }
            result.onSuccess { return it }
            lastError = result.exceptionOrNull()
            val isLastKey = index == apiKeys.lastIndex
            Log.w(TAG, "GeminiTTS: generation failed (chave ${index + 1}/${apiKeys.size}): ${lastError?.message ?: lastError?.javaClass?.simpleName}")
            if (!isLastKey) delay(GEMINI_TTS_RETRY_DELAY_MS)
        }
        throw lastError ?: IllegalStateException("GeminiTTS: falha desconhecida")
    }

    // HttpURLConnection puro, mesmo estilo de RemoteGeminiRadioScriptWriter.callGemini
    // (RadioBulletin.kt) - sem OkHttp/Retrofit no projeto. Formato da API verificado na doc
    // oficial (ai.google.dev/gemini-api/docs/generate-content/speech-generation): estilo/
    // interpretacao vai embutido como prefixo do proprio texto (nao ha campo separado pra isso),
    // resposta traz audio PCM16 mono cru (sem header WAV) em candidates[0].content.parts[0].
    // inlineData, base64, com o sample rate declarado em mimeType (ex.: "audio/L16;rate=24000").
    private fun callGeminiTts(
        apiKey: String,
        model: GeminiTtsModel,
        voiceConfig: GeminiTtsVoiceConfig,
        text: String,
    ): Pair<ByteArray, Int> {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/${model.modelId}:generateContent?key=$apiKey"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = GEMINI_TTS_TIMEOUT_MS.toInt()
            readTimeout = GEMINI_TTS_TIMEOUT_MS.toInt()
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        val styledText = "${voiceConfig.stylePrompt}: $text"
        val body = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", styledText)))),
            )
            put(
                "generationConfig",
                JSONObject().apply {
                    put("responseModalities", JSONArray().put("AUDIO"))
                    put(
                        "speechConfig",
                        JSONObject().put(
                            "voiceConfig",
                            JSONObject().put(
                                "prebuiltVoiceConfig",
                                JSONObject().put("voiceName", voiceConfig.voiceName),
                            ),
                        ),
                    )
                },
            )
        }
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

        val responseCode = connection.responseCode
        val responseText = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        connection.disconnect()

        if (responseCode !in 200..299) {
            val apiMessage = runCatching {
                JSONObject(responseText).optJSONObject("error")?.optString("message")
            }.getOrNull()?.takeIf { it.isNotBlank() }
            error("HTTP $responseCode${apiMessage?.let { ": $it" } ?: ""}")
        }

        val part = JSONObject(responseText)
            .optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)
            ?: error("resposta sem áudio")
        val inlineData = part.optJSONObject("inlineData") ?: error("resposta sem inlineData")
        val base64Data = inlineData.optString("data").takeIf { it.isNotBlank() } ?: error("resposta sem dados de áudio")
        val mimeType = inlineData.optString("mimeType")
        val sampleRate = SAMPLE_RATE_REGEX.find(mimeType)?.groupValues?.get(1)?.toIntOrNull() ?: DEFAULT_SAMPLE_RATE
        val pcm = Base64.decode(base64Data, Base64.DEFAULT)
        return pcm to sampleRate
    }

    // Mesmo espirito do gap de 0,18s entre falas de personagens diferentes em LocalRadioVoiceEngine.
    // synthesizeLinesToSamples - silencio puro (zeros), 16-bit mono, mesmo sampleRate do audio.
    private fun mergeWithGaps(chunks: List<ByteArray>, sampleRate: Int): ByteArray {
        val gapBytes = ByteArray((sampleRate * LINE_GAP_S).roundToInt() * 2)
        val output = ByteArrayOutputStream()
        chunks.forEachIndexed { index, chunk ->
            output.write(chunk)
            if (index != chunks.lastIndex) output.write(gapBytes)
        }
        return output.toByteArray()
    }

    // Mesmo formato de header (RIFF/fmt/data, PCM16 mono) que LocalRadioVoiceEngine.writeWav()
    // escreve - so recebe bytes PCM ja prontos em vez de FloatArray (o Gemini ja devolve 16-bit),
    // sem depender de nenhum tipo do sherpa-onnx.
    private fun writeWav(file: File, pcm: ByteArray, sampleRate: Int) {
        FileOutputStream(file).use { output ->
            output.writeAscii("RIFF")
            output.writeIntLe(36 + pcm.size)
            output.writeAscii("WAVE")
            output.writeAscii("fmt ")
            output.writeIntLe(16)
            output.writeShortLe(1)
            output.writeShortLe(1)
            output.writeIntLe(sampleRate)
            output.writeIntLe(sampleRate * 2)
            output.writeShortLe(2)
            output.writeShortLe(16)
            output.writeAscii("data")
            output.writeIntLe(pcm.size)
            output.write(pcm)
        }
    }

    private fun FileOutputStream.writeAscii(value: String) {
        write(value.toByteArray(Charsets.US_ASCII))
    }

    private fun FileOutputStream.writeIntLe(value: Int) {
        write(value and 0xff)
        write((value shr 8) and 0xff)
        write((value shr 16) and 0xff)
        write((value shr 24) and 0xff)
    }

    private fun FileOutputStream.writeShortLe(value: Int) {
        write(value and 0xff)
        write((value shr 8) and 0xff)
    }

    private companion object {
        const val TAG = LocalRadioVoiceEngine.TAG
        const val DEFAULT_SAMPLE_RATE = 24000
        const val LINE_GAP_S = 0.18f
        // 10/09/2026: 20s nao bastava, testado ao vivo - os 2 attempts estouraram o timeout
        // (log "GeminiTTS: generation failed: timeout" duas vezes seguidas) antes da API
        // responder. Mesmo motivo ja documentado em RemoteGeminiRadioScriptWriter.GEMINI_TIMEOUT_MS
        // (RadioBulletin.kt, 120s): modelo "preview" as vezes demora mais que o esperado. withTimeoutOrNull
        // aqui embrulha uma chamada BLOQUEANTE (HttpURLConnection, sem suspend point no meio) -
        // nao cancela ela no meio, so descarta o resultado se ele voltar depois do prazo; por isso
        // o timeout precisa cobrir o pior caso real, nao só uma estimativa.
        const val GEMINI_TTS_TIMEOUT_MS = 60_000L
        const val GEMINI_TTS_RETRY_DELAY_MS = 1_500L
        val SAMPLE_RATE_REGEX = Regex("rate=(\\d+)")
    }
}
