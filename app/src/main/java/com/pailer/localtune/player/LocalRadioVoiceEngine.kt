package com.pailer.localtune.player

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.pailer.localtune.data.KokoroConfig
import com.pailer.localtune.data.RadioScript
import com.pailer.localtune.data.RadioSpeaker
import com.pailer.localtune.data.RadioVoicePackageConfig
import com.pailer.localtune.data.RadioVoicePackageRepository
import com.pailer.localtune.data.VitsConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

class LocalRadioVoiceEngine(
    private val context: Context,
    private val packageRepository: RadioVoicePackageRepository,
) {
    private val loadedEngines = mutableMapOf<String, OfflineTts>()

    suspend fun synthesize(script: RadioScript): File? = withContext(Dispatchers.Default) {
        val config = packageRepository.config() ?: return@withContext null
        Log.d(TAG, "package=${config.name} engine=${config.engine} root=${config.rootDir.absolutePath}")
        val chunks = script.lines.mapNotNull { line ->
            val lineStartedAt = System.currentTimeMillis()
            val engine = loadEngine(config, line.speaker) ?: return@mapNotNull null
            val speakerId = speakerIdFor(config, line.speaker)
            runCatching {
                engine.generateWithConfig(
                    line.text.speakable(),
                    GenerationConfig(
                        speed = config.speed,
                        sid = speakerId,
                        silenceScale = 0.6f,
                    ),
                )
            }.onSuccess {
                Log.d(TAG, "generated line speaker=${line.speaker} chars=${line.text.length} elapsed=${System.currentTimeMillis() - lineStartedAt}ms samples=${it.samples.size}")
            }.onFailure {
                Log.e(TAG, "line generation failed speaker=${line.speaker} chars=${line.text.length}", it)
            }.getOrNull()
        }
        if (chunks.isEmpty()) return@withContext null
        val sampleRate = chunks.first().sampleRate
        val samples = chunks.flatMapIndexed { index, audio ->
            val gap = if (index == chunks.lastIndex) emptyList() else List((sampleRate * 0.18f).roundToInt()) { 0f }
            audio.samples.toList() + gap
        }.toFloatArray()
        val outFile = context.cacheDir.resolve("radio_voice_${System.currentTimeMillis()}.wav")
        writeWav(outFile, GeneratedAudio(samples, sampleRate))
        Log.d(TAG, "wrote wav bytes=${outFile.length()} sampleRate=$sampleRate samples=${samples.size}")
        outFile
    }

    fun release() {
        loadedEngines.values.forEach { it.release() }
        loadedEngines.clear()
    }

    private fun loadEngine(config: RadioVoicePackageConfig, speaker: RadioSpeaker): OfflineTts? {
        val key = engineKey(config, speaker)
        loadedEngines[key]?.let { return it }
        val startedAt = System.currentTimeMillis()
        return runCatching {
            val selectedVits = vitsFor(config, speaker)
            val selectedKokoro = kokoroFor(config, speaker)
            Log.d(TAG, "loading engine speaker=$speaker model=${selectedVits?.model.orEmpty()}${selectedKokoro?.model.orEmpty()}")
            val modelConfig = OfflineTtsModelConfig(
                vits = selectedVits?.let { config.toSherpaVits(it) } ?: OfflineTtsVitsModelConfig(),
                kokoro = selectedKokoro?.let {
                    OfflineTtsKokoroModelConfig(
                        model = config.path(it.model),
                        voices = config.path(it.voices),
                        tokens = config.path(it.tokens),
                        dataDir = config.pathOrBlank(it.dataDir),
                        lexicon = config.pathOrBlank(it.lexicon),
                        lang = it.lang,
                        dictDir = config.pathOrBlank(it.dictDir),
                        lengthScale = it.lengthScale,
                    )
                } ?: OfflineTtsKokoroModelConfig(),
                supertonic = config.supertonic?.let {
                    OfflineTtsSupertonicModelConfig(
                        durationPredictor = config.path(it.durationPredictor),
                        textEncoder = config.path(it.textEncoder),
                        vectorEstimator = config.path(it.vectorEstimator),
                        vocoder = config.path(it.vocoder),
                        ttsJson = config.path(it.ttsJson),
                        unicodeIndexer = config.path(it.unicodeIndexer),
                        voiceStyle = config.path(it.voiceStyle),
                    )
                } ?: OfflineTtsSupertonicModelConfig(),
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
                Log.d(TAG, "engine loaded speaker=$speaker elapsed=${System.currentTimeMillis() - startedAt}ms")
            }
        }.onFailure {
            Log.e(TAG, "engine load failed speaker=$speaker elapsed=${System.currentTimeMillis() - startedAt}ms", it)
        }.getOrNull()
    }

    // "mixed" (ver ADR-014): cada slot roda seu proprio motor (ex.: Frankie em vits/piper,
    // Nicky em kokoro) - femaleEngine/maleEngine dizem qual config (vits ou kokoro) vale pra
    // aquele slot. Os demais engines continuam com um unico motor pro pacote inteiro.
    private fun perSpeakerEngine(config: RadioVoicePackageConfig, speaker: RadioSpeaker): String =
        if (config.engine == "mixed") {
            when (speaker) {
                RadioSpeaker.Female -> config.femaleEngine
                RadioSpeaker.Male -> config.maleEngine
            }.orEmpty()
        } else {
            config.engine
        }

    private fun vitsFor(config: RadioVoicePackageConfig, speaker: RadioSpeaker): VitsConfig? =
        when (perSpeakerEngine(config, speaker)) {
            "vits-dual", "piper-dual" -> when (speaker) {
                RadioSpeaker.Female -> config.femaleVits
                RadioSpeaker.Male -> config.maleVits
            }
            "vits", "piper" -> if (config.engine == "mixed") {
                when (speaker) {
                    RadioSpeaker.Female -> config.femaleVits
                    RadioSpeaker.Male -> config.maleVits
                }
            } else {
                config.vits
            }
            "kokoro" -> null
            else -> config.vits
        }

    private fun kokoroFor(config: RadioVoicePackageConfig, speaker: RadioSpeaker): KokoroConfig? =
        when (perSpeakerEngine(config, speaker)) {
            "kokoro" -> if (config.engine == "mixed") {
                when (speaker) {
                    RadioSpeaker.Female -> config.femaleKokoro
                    RadioSpeaker.Male -> config.maleKokoro
                }
            } else {
                config.kokoro
            }
            "vits", "vits-dual", "piper", "piper-dual" -> null
            else -> config.kokoro
        }

    private fun speakerIdFor(config: RadioVoicePackageConfig, speaker: RadioSpeaker): Int {
        // "vits"/"piper" puro usa femaleSpeakerId/maleSpeakerId porque e UM modelo multi-speaker
        // compartilhado (ver TTS.md); vits-dual/piper-dual e cada slot do "mixed" em vits/piper
        // usam sid=0 porque cada slot tem seu proprio arquivo de modelo dedicado.
        val perSpeaker = perSpeakerEngine(config, speaker)
        return when {
            config.engine == "vits-dual" || config.engine == "piper-dual" -> 0
            config.engine == "mixed" && (perSpeaker == "vits" || perSpeaker == "piper") -> 0
            else -> when (speaker) {
                RadioSpeaker.Female -> config.femaleSpeakerId
                RadioSpeaker.Male -> config.maleSpeakerId
            }
        }
    }

    private fun engineKey(config: RadioVoicePackageConfig, speaker: RadioSpeaker): String {
        val vits = vitsFor(config, speaker)
        val kokoro = kokoroFor(config, speaker)
        return "${config.rootDir.absolutePath}:${config.engine}:${speaker.name}:${vits?.model.orEmpty()}:${kokoro?.model.orEmpty()}:${config.name}"
    }

    private fun RadioVoicePackageConfig.toSherpaVits(vits: VitsConfig): OfflineTtsVitsModelConfig =
        OfflineTtsVitsModelConfig(
            model = path(vits.model),
            lexicon = pathOrBlank(vits.lexicon),
            tokens = path(vits.tokens),
            dataDir = pathOrBlank(vits.dataDir),
            dictDir = pathOrBlank(vits.dictDir),
            noiseScale = vits.noiseScale,
            noiseScaleW = vits.noiseScaleW,
            lengthScale = vits.lengthScale,
        )

    private fun RadioVoicePackageConfig.path(relativePath: String): String =
        rootDir.resolve(relativePath).absolutePath

    private fun RadioVoicePackageConfig.pathOrBlank(relativePath: String): String =
        relativePath.takeIf { it.isNotBlank() }?.let { path(it) }.orEmpty()

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

    // Espeak-ng (fonemizador usado pelas vozes vits/piper) letreia siglas maiusculas que nao
    // reconhece como palavra ("PET" vira "peê-tê"). Lista pequena e crescente: adicionar aqui
    // conforme outras siglas mal pronunciadas forem notadas no uso real.
    private fun String.speakable(): String =
        ACRONYM_FIXES.entries.fold(this) { text, (acronym, replacement) ->
            text.replace(Regex("\\b$acronym\\b"), replacement)
        }
            // O espeak-ng-data desse pacote nao trata "ç" corretamente e le como "c" comum antes
            // de a/o/u (som de K em vez de S, ex.: "programação" -> "programacão"). Como "ç" so
            // existe em portugues antes de a/o/u e sempre e som de S, "ss" e uma troca sempre
            // correta que forca a pronuncia certa independente do fonemizador.
            .replace("ç", "ss")
            .replace("Ç", "Ss")

    private companion object {
        const val TAG = "PailerRadioVoice"
        val ACRONYM_FIXES = mapOf(
            "PET" to "Pet",
        )
    }
}
