package com.pailer.localtune.player

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig
import com.pailer.localtune.data.RadioScript
import com.pailer.localtune.data.RadioScriptLine
import com.pailer.localtune.data.RadioSpeaker
import com.pailer.localtune.data.RadioVoicePackageConfig
import com.pailer.localtune.data.RadioVoicePackageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min
import kotlin.math.roundToInt

class LocalRadioVoiceEngine(
    private val context: Context,
    private val packageRepository: RadioVoicePackageRepository,
) {
    private val loadedEngines = mutableMapOf<String, OfflineTts>()

    suspend fun synthesize(
        script: RadioScript,
        onLineDone: (index: Int, total: Int) -> Unit = { _, _ -> },
    ): File? = withContext(Dispatchers.Default) {
        val config = packageRepository.config() ?: return@withContext null
        val merged = synthesizeLinesToSamples(script.lines, config, onLineDone) ?: return@withContext null
        val (samples, sampleRate) = merged
        val mixed = mixBackgroundMusic(samples, sampleRate, config)
        val outFile = context.cacheDir.resolve("radio_voice_${System.currentTimeMillis()}.wav")
        writeWav(outFile, GeneratedAudio(mixed, sampleRate))
        Log.d(TAG, "wrote wav bytes=${outFile.length()} sampleRate=$sampleRate samples=${mixed.size}")
        outFile
    }

    // "Nucleo" pre-aquecido (falas 2-5, sem faixa/radio - ver LocalTuneViewModel.prewarmCoreBuffer)
    // sintetizado e salvo SEM musica de fundo (o fade in/out de mixBackgroundMusic depende do
    // tamanho final do audio, que so fica definido depois do encaixe das pontas em spliceEdges) -
    // WAV "seco", igual ao formato normal, so falta o bed.
    suspend fun synthesizeCore(
        lines: List<RadioScriptLine>,
        onLineDone: (index: Int, total: Int) -> Unit = { _, _ -> },
    ): File? = withContext(Dispatchers.Default) {
        val config = packageRepository.config() ?: return@withContext null
        val merged = synthesizeLinesToSamples(lines, config, onLineDone) ?: return@withContext null
        val (samples, sampleRate) = merged
        // filesDir (nao cacheDir) - o nucleo precisa sobreviver entre reinicios do app pra
        // LocalTuneViewModel.loadCoreBufferManifest() poder restaurar o buffer do disco (pedido
        // do usuario 03/09/2026: nao quer perder boletins ja escritos so porque o Android limpou
        // o cache ou o processo morreu). Mesma pasta que o ViewModel le/escreve o manifest.json
        // (CORE_BUFFER_DIR_NAME em LocalTuneViewModel.kt) - filesDir e privado do app, sem
        // permissao nenhuma necessaria, e o mesmo caminho fisico vale tanto pro processo principal
        // quanto pro :radio_voice (cacheDir/filesDir sao por app, nao por processo).
        val outFile = context.filesDir.resolve(CORE_BUFFER_DIR_NAME).apply { mkdirs() }
            .resolve("radio_core_${System.currentTimeMillis()}.wav")
        writeWav(outFile, GeneratedAudio(samples, sampleRate))
        Log.d(TAG, "wrote core wav bytes=${outFile.length()} sampleRate=$sampleRate samples=${samples.size}")
        outFile
    }

    // Encaixe das pontas (fala 1 = reacao a faixa real, fala 6 = fechamento com radio/proxima
    // faixa real) num nucleo ja pronto - sintetiza so essas 2 falas (rapido) e cola em volta do
    // audio "seco" do nucleo (synthesizeCore), depois mixa musica de fundo no resultado final
    // inteiro (precisa ser por ultimo, o fade depende do tamanho total). Ver ADR-002/003 - motivo
    // de nao re-sintetizar o boletim inteiro de novo aqui.
    suspend fun spliceEdges(
        coreFile: File,
        introLine: RadioScriptLine,
        closerLine: RadioScriptLine,
    ): File? = withContext(Dispatchers.Default) {
        val config = packageRepository.config() ?: return@withContext null
        val core = readWav(coreFile) ?: return@withContext null
        val (coreSamples, sampleRate) = core
        val engine = loadEngine(config) ?: return@withContext null
        val introSpeakerId = speakerId(config, introLine.speaker)
        val closerSpeakerId = speakerId(config, closerLine.speaker)
        val introAudio = synthesizeLine(engine, config, introLine.text, introSpeakerId)
        val closerAudio = synthesizeLine(engine, config, closerLine.text, closerSpeakerId)
        if (introAudio == null || closerAudio == null) {
            Log.e(TAG, "splice failed intro=${introAudio != null} closer=${closerAudio != null}")
            return@withContext null
        }
        val gap = List((sampleRate * 0.18f).roundToInt()) { 0f }
        val samples = (introAudio.samples.toList() + gap + coreSamples.toList() + gap + closerAudio.samples.toList())
            .toFloatArray()
        val mixed = mixBackgroundMusic(samples, sampleRate, config)
        val outFile = context.cacheDir.resolve("radio_voice_${System.currentTimeMillis()}.wav")
        writeWav(outFile, GeneratedAudio(mixed, sampleRate))
        Log.d(TAG, "wrote spliced wav bytes=${outFile.length()} sampleRate=$sampleRate samples=${mixed.size}")
        outFile
    }

    private fun speakerId(config: RadioVoicePackageConfig, speaker: RadioSpeaker): Int = when (speaker) {
        RadioSpeaker.Female -> config.femaleSpeakerId
        RadioSpeaker.Male -> config.maleSpeakerId
    }

    private fun synthesizeLinesToSamples(
        lines: List<RadioScriptLine>,
        config: RadioVoicePackageConfig,
        onLineDone: (index: Int, total: Int) -> Unit,
    ): Pair<FloatArray, Int>? {
        Log.d(TAG, "package=${config.name} root=${config.rootDir.absolutePath}")
        val total = lines.size
        val chunks = lines.mapIndexedNotNull { index, line ->
            val lineStartedAt = System.currentTimeMillis()
            val engine = loadEngine(config) ?: return@mapIndexedNotNull null
            val audio = synthesizeLine(engine, config, line.text, speakerId(config, line.speaker))
            if (audio != null) {
                Log.d(TAG, "generated line speaker=${line.speaker} chars=${line.text.length} elapsed=${System.currentTimeMillis() - lineStartedAt}ms samples=${audio.samples.size}")
            } else {
                Log.e(TAG, "line generation failed speaker=${line.speaker} chars=${line.text.length}")
            }
            onLineDone(index + 1, total)
            audio
        }
        if (chunks.isEmpty()) return null
        val sampleRate = chunks.first().sampleRate
        val samples = chunks.flatMapIndexed { index, audio ->
            val gap = if (index == chunks.lastIndex) emptyList() else List((sampleRate * 0.18f).roundToInt()) { 0f }
            audio.samples.toList() + gap
        }.toFloatArray()
        return samples to sampleRate
    }

    // Sentenca isolada com poucas palavras sai atropelada/distorcida nesse motor (flow-matching
    // precisa de contexto textual minimo pra estabilizar a duracao - validado em Python,
    // voice-models/supertonic-3-int8-scripts/run_test_supertonic_m1f2_v3.py, ver ADR-018
    // pendencia 2). Quebra a fala em sentencas, funde de volta as curtas na vizinha, sintetiza
    // cada sentenca separada com uma pausa pequena entre elas - a pausa MAIOR entre falas de
    // personagens diferentes continua vindo do gap de 0,18s em synthesize(), essa aqui e so
    // dentro da mesma fala.
    private fun synthesizeLine(
        engine: OfflineTts,
        config: RadioVoicePackageConfig,
        text: String,
        speakerId: Int,
    ): GeneratedAudio? {
        val sentences = mergeShortSentences(splitIntoSentences(text))
        var sampleRate = 0
        val samples = mutableListOf<Float>()
        sentences.forEachIndexed { index, sentence ->
            val audio = runCatching {
                engine.generateWithConfig(
                    sentence,
                    GenerationConfig(
                        speed = config.speed,
                        sid = speakerId,
                        numSteps = config.numSteps,
                        extra = mapOf("lang" to config.lang),
                    ),
                )
            }.onFailure {
                Log.e(TAG, "sentence generation failed sid=$speakerId chars=${sentence.length}", it)
            }.getOrNull() ?: return@forEachIndexed
            sampleRate = audio.sampleRate
            samples.addAll(audio.samples.toList())
            if (index < sentences.lastIndex) {
                samples.addAll(List((sampleRate * INTRA_LINE_GAP_S).roundToInt()) { 0f })
            }
        }
        if (samples.isEmpty()) return null
        return GeneratedAudio(normalizeVoiceLevel(samples.toFloatArray()), sampleRate)
    }

    // Pedido do usuario (04/09/2026): boletim saia bem mais baixo que a musica normal, tinha que
    // ficar aumentando o volume toda vez que entrava. O TTS sai com pico bem abaixo de uma faixa
    // mixada/masterizada; normaliza pelo pico (nao ganho fixo) pra levar toda fala pro mesmo nivel
    // percebido sem estourar em falas que ja saem mais altas do motor. VOICE_MAX_GAIN limita o
    // reforco em trechos quase silenciosos (ruido/erro de sintese) pra nao amplificar lixo.
    private fun normalizeVoiceLevel(samples: FloatArray, targetPeak: Float = VOICE_TARGET_PEAK): FloatArray {
        val peak = samples.maxOf { kotlin.math.abs(it) }
        if (peak <= 0.0001f) return samples
        val gain = (targetPeak / peak).coerceAtMost(VOICE_MAX_GAIN)
        if (gain <= 1f) return samples
        return FloatArray(samples.size) { (samples[it] * gain).coerceIn(-1f, 1f) }
    }

    private fun splitIntoSentences(text: String): List<String> {
        val parts = SENTENCE_SPLIT_REGEX.split(text).map { it.trim() }.filter { it.isNotEmpty() }
        return parts.ifEmpty { listOf(text) }
    }

    private fun mergeShortSentences(sentences: List<String>, minWords: Int = MIN_WORDS_PER_SENTENCE): List<String> {
        val pending = sentences.toMutableList()
        val merged = mutableListOf<String>()
        var i = 0
        while (i < pending.size) {
            val current = pending[i]
            if (current.split(Regex("\\s+")).size <= minWords) {
                when {
                    merged.isNotEmpty() -> merged[merged.size - 1] = "${merged.last()} $current"
                    i + 1 < pending.size -> pending[i + 1] = "$current ${pending[i + 1]}"
                    else -> merged.add(current)
                }
            } else {
                merged.add(current)
            }
            i++
        }
        return merged
    }

    // Bed de musica bem baixinho por baixo do boletim inteiro (ADR-018, testado fora do app com
    // ffmpeg antes de implementar aqui). Silencioso e sem custo se o pacote nao declarar
    // backgroundMusic - so entra quando o campo existe E o arquivo existe de verdade.
    private fun mixBackgroundMusic(voice: FloatArray, sampleRate: Int, config: RadioVoicePackageConfig): FloatArray {
        if (config.backgroundMusic.isEmpty()) return voice
        // Alterna entre os beds disponiveis (indice num companion object - sobrevive entre
        // requests dentro do mesmo processo :radio_voice, ver ADR-001/003) pra nao repetir
        // sempre o mesmo quando o pacote tem mais de um.
        val bedPath = config.backgroundMusic[nextBackgroundMusicIndex.getAndIncrement().mod(config.backgroundMusic.size)]
        val bedFile = config.rootDir.resolve(bedPath)
        if (!bedFile.exists()) return voice
        val bed = runCatching { readRawPcm16Mono(bedFile) }
            .onFailure { Log.w(TAG, "falha ao ler musica de fundo $bedPath", it) }
            .getOrNull()
        if (bed == null || bed.isEmpty()) return voice

        val fadeInSamples = (sampleRate * 1.5f).roundToInt()
        val fadeOutSamples = (sampleRate * 3f).roundToInt()
        val volume = config.backgroundMusicVolume
        return FloatArray(voice.size) { i ->
            val bedSample = bed[i % bed.size] * volume
            val fadeIn = (i.toFloat() / fadeInSamples).coerceIn(0f, 1f)
            val fadeOut = ((voice.size - i).toFloat() / fadeOutSamples).coerceIn(0f, 1f)
            (voice[i] + bedSample * min(fadeIn, fadeOut)).coerceIn(-1f, 1f)
        }
    }

    // Le PCM16 little-endian mono SEM cabecalho (nao e .wav) - o pacote de voz traz o bed ja
    // convertido pro sample rate do motor (ver manifest "backgroundMusic"), poupando decoder de
    // MP3/parser de WAV em runtime.
    private fun readRawPcm16Mono(file: File): FloatArray {
        val bytes = file.readBytes()
        val shorts = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        return FloatArray(shorts.size) { shorts[it] / 32768f }
    }

    fun release() {
        loadedEngines.values.forEach { it.release() }
        loadedEngines.clear()
    }

    // Motor unico suportado: Supertonic 3. Um so OfflineTts serve os dois locutores (mesmo
    // voice.bin, sid diferente por speaker) - nao ha mais "engine por pacote" pra decidir.
    private fun loadEngine(config: RadioVoicePackageConfig): OfflineTts? {
        val key = config.rootDir.absolutePath
        loadedEngines[key]?.let { return it }
        val startedAt = System.currentTimeMillis()
        return runCatching {
            val modelConfig = OfflineTtsModelConfig(
                supertonic = OfflineTtsSupertonicModelConfig(
                    durationPredictor = config.path(config.supertonic.durationPredictor),
                    textEncoder = config.path(config.supertonic.textEncoder),
                    vectorEstimator = config.path(config.supertonic.vectorEstimator),
                    vocoder = config.path(config.supertonic.vocoder),
                    ttsJson = config.path(config.supertonic.ttsJson),
                    unicodeIndexer = config.path(config.supertonic.unicodeIndexer),
                    voiceStyle = config.path(config.supertonic.voiceStyle),
                ),
                // Testado em campo (Motorola Edge 40, Dimensity 1200): 2 threads = 410s pra
                // sintetizar um boletim de 6 falas; 4 threads = 496s, PIOROU. Reteste 03/09/2026
                // com 8 threads (nucleos totais do aparelho) + log por fala confirmou o mesmo
                // padrao de forma ainda mais extrema: uma unica fala de 159 caracteres levou
                // 136s sozinha (contra ~68-83s/fala na media do teste de 2-4 threads). Voltado
                // pro UNICO valor com dado real bom (2) - 3 nunca foi medido, era so um "meio-termo"
                // sem base; nao subir threads de novo aqui sem medir com log por fala primeiro.
                numThreads = 2,
                debug = false,
                provider = "cpu",
            )
            OfflineTts(
                // O pacote de voz e importado pelo usuario (.zip) para o filesDir, nunca empacotado
                // como asset do APK. Com assetManager != null a sherpa-onnx tenta ler os .onnx via
                // AAsset mesmo recebendo caminho absoluto, falha e aborta o processo nativo (ver
                // https://github.com/k2-fsa/sherpa-onnx/issues/2562) -- travava a voz local sempre.
                assetManager = null,
                config = OfflineTtsConfig(
                    model = modelConfig,
                    maxNumSentences = 1,
                    silenceScale = 0.6f,
                ),
            ).also {
                loadedEngines[key] = it
                Log.d(TAG, "engine loaded elapsed=${System.currentTimeMillis() - startedAt}ms")
            }
        }.onFailure {
            Log.e(TAG, "engine load failed elapsed=${System.currentTimeMillis() - startedAt}ms", it)
        }.getOrNull()
    }

    private fun RadioVoicePackageConfig.path(relativePath: String): String =
        rootDir.resolve(relativePath).absolutePath

    private fun writeWav(file: File, audio: GeneratedAudio) {
        val samples = audio.samples
        val dataSize = samples.size * 2
        FileOutputStream(file).use { output ->
            output.writeAscii("RIFF")
            output.writeIntLe(36 + dataSize)
            output.writeAscii("WAVE")
            output.writeAscii("fmt ")
            output.writeIntLe(16)
            output.writeShortLe(1)
            output.writeShortLe(1)
            output.writeIntLe(audio.sampleRate)
            output.writeIntLe(audio.sampleRate * 2)
            output.writeShortLe(2)
            output.writeShortLe(16)
            output.writeAscii("data")
            output.writeIntLe(dataSize)
            samples.forEach { sample ->
                val pcm = (sample.coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt()
                output.writeShortLe(pcm)
            }
        }
    }

    // Inverso de writeWav() - so precisa entender o formato fixo que ela mesma escreve (RIFF/WAVE
    // PCM16 mono, header de 44 bytes sempre nessa ordem de chunk), usado por spliceEdges() pra
    // reler o "nucleo" pre-sintetizado (synthesizeCore) antes de colar as pontas em volta.
    private fun readWav(file: File): Pair<FloatArray, Int>? {
        val bytes = file.readBytes()
        if (bytes.size <= WAV_HEADER_SIZE) return null
        val sampleRate = ByteBuffer.wrap(bytes, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val shorts = ShortArray((bytes.size - WAV_HEADER_SIZE) / 2)
        ByteBuffer.wrap(bytes, WAV_HEADER_SIZE, bytes.size - WAV_HEADER_SIZE)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()
            .get(shorts)
        return FloatArray(shorts.size) { shorts[it] / 32768f } to sampleRate
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

    companion object {
        // Nome compartilhado com LocalTuneViewModel (le/escreve manifest.json na mesma pasta) -
        // ver comentario em synthesizeCore.
        const val CORE_BUFFER_DIR_NAME = "radio_bulletins_ready"
        const val TAG = "PailerRadioVoice"
        val SENTENCE_SPLIT_REGEX = Regex("(?<=[.!?])\\s+")
        const val MIN_WORDS_PER_SENTENCE = 4
        const val INTRA_LINE_GAP_S = 0.15f
        // Alvo de pico linear pra normalizeVoiceLevel() - 0.95 (~-0.4dB) deixa uma folga minima
        // antes do clip, ja que o bed de fundo ainda soma mais amostra em cima.
        const val VOICE_TARGET_PEAK = 0.95f
        // Teto de reforco (12dB) pra nao amplificar demais um trecho quase mudo por erro de
        // sintese/silencio no meio da fala.
        const val VOICE_MAX_GAIN = 4f
        // Tamanho fixo do header RIFF/WAVE escrito por writeWav() (ver comentario em readWav).
        const val WAV_HEADER_SIZE = 44
        // Companion (nao por instancia) pra sobreviver entre requests - cada boletim cria um
        // LocalRadioVoiceEngine novo (ver ADR-003), mas o processo :radio_voice persiste.
        val nextBackgroundMusicIndex = AtomicInteger(0)
    }
}
