package com.pailer.localtune.player

import android.content.Context
import android.util.Base64
import android.util.Log
import com.pailer.localtune.data.GeminiApiKeySettings
import com.pailer.localtune.data.GeminiTtsModel
import com.pailer.localtune.data.GeminiTtsVoices
import com.pailer.localtune.data.RadioScript
import com.pailer.localtune.data.RadioSpeaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

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

    // onLineDone(index, total) - chamado com (0, total) antes de disparar a chamada e (total,
    // total) quando ela termina (10/09/2026: reduzido de 1 chamada POR FALA pra 1 chamada pro
    // boletim INTEIRO via multi-speaker TTS, pedido do usuario - "6 chamadas e chamada demais,
    // deve gastar rapido a cota"). Nao ha mais progresso granular por fala real (so 1 requisicao
    // de rede), mas o callback continua com essa forma pra LocalTuneViewModel reaproveitar a
    // mesma barra de progresso 0-100% ja existente (ver trySynthesizeWithGeminiFlash).
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
        if (script.lines.isEmpty()) {
            Log.w(TAG, "GeminiTTS: roteiro sem falas, pulando")
            return@withContext null
        }
        Log.d(TAG, "GeminiTTS: model=${model.modelId} lines=${script.lines.size} (1 chamada multi-speaker)")
        val total = script.lines.size
        onLineDone(0, total)
        val transcript = buildTranscript(script)
        val (pcm, sampleRate) = generateScriptWithRetry(apiKeys, model, transcript)
        onLineDone(total, total)
        if (pcm.isEmpty()) {
            Log.w(TAG, "GeminiTTS: nenhum audio gerado")
            return@withContext null
        }
        val outFile = outputFile(persistent)
        writeWav(outFile, pcm, sampleRate)
        Log.d(TAG, "GeminiTTS: generation completed bytes=${outFile.length()} sampleRate=$sampleRate")
        outFile
    }

    // Um unico texto com as duas falas intercaladas, rotuladas "Fran:"/"Nico:" (formato exigido
    // pela API pra multi-speaker TTS: o rotulo de cada linha do roteiro precisa bater com o
    // "speaker" declarado em speakerVoiceConfigs, ver callGeminiTts). As instrucoes de
    // interpretacao vao UMA vez cada no topo (nao mais repetidas por linha como no motor por-fala
    // antigo) - "Roteiro:" separa claramente instrucao de fala real, pra o modelo nao tentar ler
    // a propria instrucao em voz alta.
    private fun buildTranscript(script: RadioScript): String = buildString {
        appendLine("TTS da conversa de radio brasileira a seguir entre os apresentadores Fran e Nico.")
        appendLine("Instrucoes de interpretacao de Fran: ${GeminiTtsVoices.FRAN.stylePrompt}")
        appendLine("Instrucoes de interpretacao de Nico: ${GeminiTtsVoices.NICO.stylePrompt}")
        appendLine()
        appendLine("Roteiro:")
        script.lines.forEach { line ->
            val speakerLabel = if (line.speaker == RadioSpeaker.Female) "Fran" else "Nico"
            appendLine("$speakerLabel: ${line.text}")
        }
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
    // HTTP 429 de cota (confirmado em campo) nao se resolve batendo de novo na MESMA chave. Agora
    // e 1 chamada por chave pro BOLETIM INTEIRO (nao mais por fala) - esgotar todas as chaves
    // derruba o synthesize() inteiro (excecao sobe, ver acima) e cai pro motor local, como antes.
    private suspend fun generateScriptWithRetry(
        apiKeys: List<String>,
        model: GeminiTtsModel,
        transcript: String,
    ): Pair<ByteArray, Int> {
        var lastError: Throwable? = null
        apiKeys.forEachIndexed { index, apiKey ->
            val result = runCatching {
                withTimeoutOrNull(GEMINI_TTS_TIMEOUT_MS) { callGeminiTts(apiKey, model, transcript) }
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
    // (RadioBulletin.kt) - sem OkHttp/Retrofit no projeto. speechConfig.multiSpeakerVoiceConfig
    // (10/09/2026, ate 2 falantes - documentado em ai.google.dev/gemini-api/docs/speech-generation
    // na secao "Multi-speaker text-to-speech") troca as 1 chamada/fala por 1 chamada pro boletim
    // inteiro: cada "speaker" em speakerVoiceConfigs precisa bater com o rotulo usado no texto
    // (buildTranscript usa exatamente "Fran"/"Nico"). NAO CONFIRMADO ainda em teste real neste
    // device/conta pra gemini-3.1-flash-tts-preview (so o modo single-speaker foi validado ao vivo
    // ate agora) - se vier 400 "invalid argument", e sinal de campo renomeado entre versoes do
    // modelo (mesmo caso ja visto com thinkingConfig no redator, RadioBulletin.kt); a mensagem
    // completa da API fica no erro pra diagnostico, nao so um generico. Resposta traz audio PCM16
    // mono cru (sem header WAV) em candidates[0].content.parts[0].inlineData, base64, sample rate
    // declarado em mimeType (ex.: "audio/L16;rate=24000") - mesmo formato do modo single-speaker.
    private fun callGeminiTts(
        apiKey: String,
        model: GeminiTtsModel,
        transcript: String,
    ): Pair<ByteArray, Int> {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/${model.modelId}:generateContent?key=$apiKey"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = GEMINI_TTS_TIMEOUT_MS.toInt()
            readTimeout = GEMINI_TTS_TIMEOUT_MS.toInt()
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        val body = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", transcript)))),
            )
            put(
                "generationConfig",
                JSONObject().apply {
                    put("responseModalities", JSONArray().put("AUDIO"))
                    put(
                        "speechConfig",
                        JSONObject().put(
                            "multiSpeakerVoiceConfig",
                            JSONObject().put(
                                "speakerVoiceConfigs",
                                JSONArray()
                                    .put(speakerVoiceConfig("Fran", GeminiTtsVoices.FRAN.voiceName))
                                    .put(speakerVoiceConfig("Nico", GeminiTtsVoices.NICO.voiceName)),
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

    // speaker precisa bater com o rotulo usado em buildTranscript ("Fran:"/"Nico:").
    private fun speakerVoiceConfig(speaker: String, voiceName: String): JSONObject =
        JSONObject()
            .put("speaker", speaker)
            .put("voiceConfig", JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", voiceName)))

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
        // 10/09/2026: 20s nao bastava pra 1 fala, testado ao vivo - os 2 attempts estouraram o
        // timeout (log "GeminiTTS: generation failed: timeout" duas vezes seguidas) antes da API
        // responder; subiu pra 60s. Dobrado de novo pra 120s ao trocar pra 1 chamada multi-speaker
        // pro boletim INTEIRO (mais falas = mais audio gerado no mesmo request, ver
        // generateScriptWithRetry) - ainda NAO confirmado ao vivo se 120s basta pro pior caso
        // multi-speaker, so extrapolado do mesmo motivo ja documentado em
        // RemoteGeminiRadioScriptWriter.GEMINI_TIMEOUT_MS (RadioBulletin.kt, tambem 120s: modelo
        // "preview" as vezes demora mais que o esperado). Se voltar a dar falso "timeout" em
        // boletim real, subir de novo (mesmo padrao ja visto 2x nesse arquivo). withTimeoutOrNull
        // aqui embrulha uma chamada BLOQUEANTE (HttpURLConnection, sem suspend point no meio) -
        // nao cancela ela no meio, so descarta o resultado se ele voltar depois do prazo; por isso
        // o timeout precisa cobrir o pior caso real, nao só uma estimativa.
        const val GEMINI_TTS_TIMEOUT_MS = 120_000L
        const val GEMINI_TTS_RETRY_DELAY_MS = 1_500L
        val SAMPLE_RATE_REGEX = Regex("rate=(\\d+)")
    }
}
