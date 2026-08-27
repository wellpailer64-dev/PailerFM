package com.pailer.localtune.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream

data class RadioVoicePackageStatus(
    val isInstalled: Boolean = false,
    val packageName: String = "Voz local",
    val engine: String = "",
    val femaleSpeaker: String = "",
    val maleSpeaker: String = "",
    val detail: String = "Nenhum pacote de voz instalado",
)

data class RadioVoicePackageConfig(
    val rootDir: File,
    val name: String,
    val engine: String,
    val femaleSpeakerName: String,
    val maleSpeakerName: String,
    val femaleSpeakerId: Int,
    val maleSpeakerId: Int,
    val speed: Float,
    val vits: VitsConfig?,
    val femaleVits: VitsConfig?,
    val maleVits: VitsConfig?,
    val kokoro: KokoroConfig?,
    val supertonic: SupertonicConfig?,
)

data class VitsConfig(
    val model: String,
    val tokens: String,
    val lexicon: String = "",
    val dataDir: String = "",
    val dictDir: String = "",
    val noiseScale: Float = 0.667f,
    val noiseScaleW: Float = 0.8f,
    val lengthScale: Float = 1.0f,
)

data class KokoroConfig(
    val model: String,
    val voices: String,
    val tokens: String,
    val dataDir: String = "",
    val lexicon: String = "",
    val lang: String = "pt-br",
    val dictDir: String = "",
    val lengthScale: Float = 1.0f,
)

data class SupertonicConfig(
    val durationPredictor: String,
    val textEncoder: String,
    val vectorEstimator: String,
    val vocoder: String,
    val ttsJson: String,
    val unicodeIndexer: String,
    val voiceStyle: String,
)

class RadioVoicePackageRepository(private val context: Context) {
    fun status(): RadioVoicePackageStatus {
        val manifestFile = currentDir.resolve(MANIFEST_FILE)
        if (!manifestFile.exists() || !currentDir.resolve(READY_FILE).exists()) {
            return RadioVoicePackageStatus()
        }
        return runCatching {
            parseManifest(manifestFile.readText()).copy(
                isInstalled = true,
                detail = "Pacote pronto para o motor local",
            )
        }.getOrDefault(RadioVoicePackageStatus(detail = "Pacote de voz incompleto"))
    }

    fun config(): RadioVoicePackageConfig? {
        val manifestFile = currentDir.resolve(MANIFEST_FILE)
        if (!manifestFile.exists() || !currentDir.resolve(READY_FILE).exists()) return null
        return runCatching { parsePackageConfig(manifestFile.readText(), currentDir) }.getOrNull()
    }

    suspend fun importPackage(uri: Uri): RadioVoicePackageStatus = withContext(Dispatchers.IO) {
        val tempDir = context.filesDir.resolve(IMPORT_DIR)
        tempDir.deleteRecursively()
        tempDir.mkdirs()

        context.contentResolver.openInputStream(uri)?.use { stream ->
            ZipInputStream(stream.buffered()).use { zip ->
                var totalBytes = 0L
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val outFile = tempDir.resolve(entry.name)
                    ensureInsideDirectory(tempDir, outFile)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val read = zip.read(buffer)
                                if (read <= 0) break
                                totalBytes += read
                                if (totalBytes > MAX_PACKAGE_BYTES) {
                                    error("Pacote de voz maior que 350 MB.")
                                }
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
        } ?: error("Nao consegui abrir o pacote de voz.")

        val manifestFile = tempDir.resolve(MANIFEST_FILE)
        if (!manifestFile.exists()) {
            error("O pacote precisa ter um manifest.json na raiz.")
        }
        val parsedConfig = parsePackageConfig(manifestFile.readText(), tempDir)
        val requiredFiles = tempDir.walkTopDown()
            .filter { it.isFile }
            .map { it.name.lowercase() }
            .toSet()
        if (requiredFiles.none { it.endsWith(".onnx") }) {
            error("O pacote precisa incluir pelo menos um arquivo .onnx.")
        }
        validatePackageFiles(parsedConfig)

        currentDir.deleteRecursively()
        currentDir.mkdirs()
        tempDir.copyRecursively(currentDir, overwrite = true)
        currentDir.resolve(READY_FILE).writeText("ok")
        tempDir.deleteRecursively()
        parsedConfig.toStatus().copy(isInstalled = true, detail = "Pacote importado com sucesso")
    }

    fun clearPackage(): RadioVoicePackageStatus {
        currentDir.deleteRecursively()
        return status()
    }

    private fun parseManifest(json: String): RadioVoicePackageStatus {
        return parsePackageConfig(json, currentDir).toStatus()
    }

    private fun parsePackageConfig(json: String, rootDir: File): RadioVoicePackageConfig {
        val manifest = JSONObject(json)
        val name = manifest.optString("name").ifBlank { "Voz local" }
        val engine = manifest.optString("engine").ifBlank { "vits" }.lowercase()
        val female = manifest.optString("femaleSpeaker").ifBlank { manifest.optString("female").ifBlank { "Locutora" } }
        val male = manifest.optString("maleSpeaker").ifBlank { manifest.optString("male").ifBlank { "Locutor" } }
        return RadioVoicePackageConfig(
            rootDir = rootDir,
            name = name,
            engine = engine,
            femaleSpeakerName = female,
            maleSpeakerName = male,
            femaleSpeakerId = manifest.optInt("femaleSpeakerId", manifest.optInt("femaleId", 0)),
            maleSpeakerId = manifest.optInt("maleSpeakerId", manifest.optInt("maleId", 1)),
            speed = manifest.optDouble("speed", 1.0).toFloat().coerceIn(0.65f, 1.35f),
            vits = manifest.optJSONObject("vits")?.let {
                VitsConfig(
                    model = it.optString("model"),
                    tokens = it.optString("tokens"),
                    lexicon = it.optString("lexicon"),
                    dataDir = it.optString("dataDir"),
                    dictDir = it.optString("dictDir"),
                    noiseScale = it.optDouble("noiseScale", 0.667).toFloat(),
                    noiseScaleW = it.optDouble("noiseScaleW", 0.8).toFloat(),
                    lengthScale = it.optDouble("lengthScale", 1.0).toFloat(),
                )
            } ?: if (engine == "vits" || engine == "piper") {
                VitsConfig(
                    model = manifest.optString("model"),
                    tokens = manifest.optString("tokens"),
                    lexicon = manifest.optString("lexicon"),
                    dataDir = manifest.optString("dataDir"),
                    dictDir = manifest.optString("dictDir"),
                )
            } else {
                null
            },
            femaleVits = manifest.optJSONObject("voices")
                ?.optJSONObject("female")
                ?.optJSONObject("vits")
                ?.toVitsConfig(),
            maleVits = manifest.optJSONObject("voices")
                ?.optJSONObject("male")
                ?.optJSONObject("vits")
                ?.toVitsConfig(),
            kokoro = manifest.optJSONObject("kokoro")?.let {
                KokoroConfig(
                    model = it.optString("model"),
                    voices = it.optString("voices"),
                    tokens = it.optString("tokens"),
                    dataDir = it.optString("dataDir"),
                    lexicon = it.optString("lexicon"),
                    lang = it.optString("lang").ifBlank { "pt-br" },
                    dictDir = it.optString("dictDir"),
                    lengthScale = it.optDouble("lengthScale", 1.0).toFloat(),
                )
            },
            supertonic = manifest.optJSONObject("supertonic")?.let {
                SupertonicConfig(
                    durationPredictor = it.optString("durationPredictor"),
                    textEncoder = it.optString("textEncoder"),
                    vectorEstimator = it.optString("vectorEstimator"),
                    vocoder = it.optString("vocoder"),
                    ttsJson = it.optString("ttsJson"),
                    unicodeIndexer = it.optString("unicodeIndexer"),
                    voiceStyle = it.optString("voiceStyle"),
                )
            },
        )
    }

    private fun RadioVoicePackageConfig.toStatus(): RadioVoicePackageStatus =
        RadioVoicePackageStatus(
            packageName = name,
            engine = engine,
            femaleSpeaker = femaleSpeakerName,
            maleSpeaker = maleSpeakerName,
        )

    private fun validatePackageFiles(config: RadioVoicePackageConfig) {
        when (config.engine) {
            "vits", "piper" -> {
                val vits = config.vits ?: error("Manifest sem configuracao vits.")
                validateVitsFiles(config.rootDir, vits)
            }
            "vits-dual", "piper-dual" -> {
                validateVitsFiles(config.rootDir, config.femaleVits ?: error("Manifest sem voz feminina."))
                validateVitsFiles(config.rootDir, config.maleVits ?: error("Manifest sem voz masculina."))
            }
            "kokoro" -> {
                val kokoro = config.kokoro ?: error("Manifest sem configuracao kokoro.")
                requirePackageFile(config.rootDir, kokoro.model, "modelo .onnx")
                requirePackageFile(config.rootDir, kokoro.voices, "voices")
                requirePackageFile(config.rootDir, kokoro.tokens, "tokens")
            }
            "supertonic" -> {
                val supertonic = config.supertonic ?: error("Manifest sem configuracao supertonic.")
                listOf(
                    supertonic.durationPredictor,
                    supertonic.textEncoder,
                    supertonic.vectorEstimator,
                    supertonic.vocoder,
                    supertonic.ttsJson,
                    supertonic.unicodeIndexer,
                    supertonic.voiceStyle,
                ).forEach { requirePackageFile(config.rootDir, it, "arquivo do Supertonic") }
            }
            else -> error("Motor de voz nao suportado: ${config.engine}.")
        }
    }

    private fun JSONObject.toVitsConfig(): VitsConfig =
        VitsConfig(
            model = optString("model"),
            tokens = optString("tokens"),
            lexicon = optString("lexicon"),
            dataDir = optString("dataDir"),
            dictDir = optString("dictDir"),
            noiseScale = optDouble("noiseScale", 0.667).toFloat(),
            noiseScaleW = optDouble("noiseScaleW", 0.8).toFloat(),
            lengthScale = optDouble("lengthScale", 1.0).toFloat(),
        )

    private fun validateVitsFiles(rootDir: File, vits: VitsConfig) {
        requirePackageFile(rootDir, vits.model, "modelo .onnx")
        requirePackageFile(rootDir, vits.tokens, "tokens")
        if (vits.lexicon.isNotBlank()) requirePackageFile(rootDir, vits.lexicon, "lexicon")
        if (vits.dataDir.isNotBlank()) requirePackageFile(rootDir, vits.dataDir, "dataDir")
        if (vits.dictDir.isNotBlank()) requirePackageFile(rootDir, vits.dictDir, "dictDir")
    }

    private fun requirePackageFile(rootDir: File, relativePath: String, label: String) {
        if (relativePath.isBlank()) error("Manifest sem $label.")
        val file = rootDir.resolve(relativePath)
        ensureInsideDirectory(rootDir, file)
        if (!file.exists()) error("Arquivo ausente no pacote: $relativePath")
    }

    private fun ensureInsideDirectory(parent: File, child: File) {
        val parentPath = parent.canonicalPath
        val childPath = child.canonicalPath
        if (childPath != parentPath && !childPath.startsWith(parentPath + File.separator)) {
            error("Pacote de voz invalido.")
        }
    }

    private val currentDir: File
        get() = context.filesDir.resolve(CURRENT_DIR)

    private companion object {
        const val CURRENT_DIR = "radio_voice_package"
        const val IMPORT_DIR = "radio_voice_import"
        const val MANIFEST_FILE = "manifest.json"
        const val READY_FILE = "package.ready"
        const val MAX_PACKAGE_BYTES = 350L * 1024L * 1024L
    }
}
