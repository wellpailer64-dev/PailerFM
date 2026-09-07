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
    val femaleSpeaker: String = "",
    val maleSpeaker: String = "",
    val detail: String = "Nenhum pacote de voz instalado",
)

// Motor unico suportado: Supertonic 3 (sherpa-onnx). Piper (vits/vits-dual) e Kokoro foram
// removidos - ver DECISIONS.md ADR-018 - o pacote testado e aprovado e sempre um par de vozes
// do mesmo voice.bin do Supertonic (Fran/Nico), nao mais modelos separados por locutor.
data class RadioVoicePackageConfig(
    val rootDir: File,
    val name: String,
    val femaleSpeakerName: String,
    val maleSpeakerName: String,
    val femaleSpeakerId: Int,
    val maleSpeakerId: Int,
    val speed: Float,
    val numSteps: Int,
    val lang: String,
    val supertonic: SupertonicConfig,
    // Opcional: beds de musica bem baixinho por baixo do boletim (ver ADR-018/
    // LocalRadioVoiceEngine.mixBackgroundMusic) - um sorteado por boletim quando ha mais de um,
    // pra nao repetir sempre o mesmo. PCM16 mono headerless (sem cabecalho WAV) no MESMO sample
    // rate que o motor de voz gera - hoje 44100Hz (Supertonic), sem reamostragem. Lista vazia =
    // sem musica de fundo; pacotes antigos sem esse campo continuam funcionando normalmente.
    val backgroundMusic: List<String> = emptyList(),
    val backgroundMusicVolume: Float = 0.05623f,
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

        // Copia de referencia do .zip original na pasta oficial (se configurada) - ver mesmo
        // comentario em RadioWriterPackageRepository.importPackage.
        runCatching {
            AppFolderRepository(context).copyUriToSubfolder(
                uri,
                AppFolderRepository.SUBFOLDER_VOICE,
                "pacote_de_vozes.zip",
                "application/zip",
            )
        }

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
        val female = manifest.optString("femaleSpeaker").ifBlank { manifest.optString("female").ifBlank { "Locutora" } }
        val male = manifest.optString("maleSpeaker").ifBlank { manifest.optString("male").ifBlank { "Locutor" } }
        val supertonicJson = manifest.optJSONObject("supertonic") ?: error("Manifest sem configuracao supertonic.")
        return RadioVoicePackageConfig(
            rootDir = rootDir,
            name = name,
            femaleSpeakerName = female,
            maleSpeakerName = male,
            femaleSpeakerId = manifest.optInt("femaleSpeakerId", manifest.optInt("femaleId", 0)),
            maleSpeakerId = manifest.optInt("maleSpeakerId", manifest.optInt("maleId", 1)),
            speed = manifest.optDouble("speed", 1.0).toFloat().coerceIn(0.65f, 1.35f),
            numSteps = manifest.optInt("numSteps", 10),
            lang = manifest.optString("lang").ifBlank { "pt" },
            backgroundMusic = manifest.optJSONArray("backgroundMusic")?.let { array ->
                (0 until array.length()).mapNotNull { array.optString(it).ifBlank { null } }
            } ?: manifest.optString("backgroundMusic").ifBlank { null }?.let { listOf(it) }.orEmpty(),
            backgroundMusicVolume = manifest.optDouble("backgroundMusicVolume", 0.05623).toFloat(),
            supertonic = SupertonicConfig(
                durationPredictor = supertonicJson.optString("durationPredictor"),
                textEncoder = supertonicJson.optString("textEncoder"),
                vectorEstimator = supertonicJson.optString("vectorEstimator"),
                vocoder = supertonicJson.optString("vocoder"),
                ttsJson = supertonicJson.optString("ttsJson"),
                unicodeIndexer = supertonicJson.optString("unicodeIndexer"),
                voiceStyle = supertonicJson.optString("voiceStyle"),
            ),
        )
    }

    private fun RadioVoicePackageConfig.toStatus(): RadioVoicePackageStatus =
        RadioVoicePackageStatus(
            packageName = name,
            femaleSpeaker = femaleSpeakerName,
            maleSpeaker = maleSpeakerName,
        )

    private fun validatePackageFiles(config: RadioVoicePackageConfig) {
        listOf(
            config.supertonic.durationPredictor,
            config.supertonic.textEncoder,
            config.supertonic.vectorEstimator,
            config.supertonic.vocoder,
            config.supertonic.ttsJson,
            config.supertonic.unicodeIndexer,
            config.supertonic.voiceStyle,
        ).forEach { requirePackageFile(config.rootDir, it, "arquivo do Supertonic") }
        config.backgroundMusic.forEach { requirePackageFile(config.rootDir, it, "musica de fundo") }
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
