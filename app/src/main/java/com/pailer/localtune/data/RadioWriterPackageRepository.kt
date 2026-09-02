package com.pailer.localtune.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream

data class RadioWriterPackageStatus(
    val isInstalled: Boolean = false,
    val packageName: String = "Redator local",
    val modelName: String = "Qwen3 1.7B",
    val detail: String = "Pacote de LLM ainda não instalado",
)

data class RadioWriterPackageConfig(
    val rootDir: File,
    val name: String,
    val modelName: String,
    val model: String,
    val maxTokens: Int = 72,
    val temperature: Float = 0.55f,
    val threads: Int = 2,
) {
    val modelFile: File
        get() = rootDir.resolve(model)
}

class RadioWriterPackageRepository(private val context: Context) {
    fun status(): RadioWriterPackageStatus {
        val manifestFile = currentDir.resolve(MANIFEST_FILE)
        if (!manifestFile.exists() || !currentDir.resolve(READY_FILE).exists()) {
            return RadioWriterPackageStatus()
        }
        return runCatching {
            parsePackageConfig(manifestFile.readText(), currentDir).toStatus(
                detail = "Pacote pronto para escrever boletins no aparelho",
            )
        }.getOrDefault(RadioWriterPackageStatus(detail = "Pacote de redator incompleto"))
    }

    fun config(): RadioWriterPackageConfig? {
        val manifestFile = currentDir.resolve(MANIFEST_FILE)
        if (!manifestFile.exists() || !currentDir.resolve(READY_FILE).exists()) return null
        return runCatching { parsePackageConfig(manifestFile.readText(), currentDir) }.getOrNull()
    }

    suspend fun importPackage(uri: Uri): RadioWriterPackageStatus = withContext(Dispatchers.IO) {
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
                                    error("Pacote de redator maior que 1,8 GB.")
                                }
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
        } ?: error("Não consegui abrir o pacote de redator.")

        val manifestFile = tempDir.resolve(MANIFEST_FILE)
        if (!manifestFile.exists()) {
            error("O pacote precisa ter um manifest.json na raiz.")
        }
        val parsedConfig = parsePackageConfig(manifestFile.readText(), tempDir)
        validatePackageFiles(parsedConfig)

        currentDir.deleteRecursively()
        currentDir.mkdirs()
        tempDir.copyRecursively(currentDir, overwrite = true)
        currentDir.resolve(READY_FILE).writeText("ok")
        tempDir.deleteRecursively()
        parsedConfig.toStatus("Pacote importado com sucesso")
    }

    fun clearPackage(): RadioWriterPackageStatus {
        currentDir.deleteRecursively()
        return status()
    }

    private fun parsePackageConfig(json: String, rootDir: File): RadioWriterPackageConfig {
        val manifest = JSONObject(json)
        return RadioWriterPackageConfig(
            rootDir = rootDir,
            name = manifest.optString("name").ifBlank { "Redator local" },
            modelName = manifest.optString("modelName").ifBlank { "Qwen3 1.7B" },
            model = manifest.optString("model").ifBlank { "model.gguf" },
            maxTokens = manifest.optInt("maxTokens", 72).coerceIn(32, 260),
            temperature = manifest.optDouble("temperature", 0.55).toFloat().coerceIn(0.1f, 1.2f),
            threads = manifest.optInt("threads", 2).coerceIn(1, 3),
        )
    }

    private fun RadioWriterPackageConfig.toStatus(detail: String): RadioWriterPackageStatus =
        RadioWriterPackageStatus(
            isInstalled = true,
            packageName = name,
            modelName = modelName,
            detail = detail,
        )

    private fun validatePackageFiles(config: RadioWriterPackageConfig) {
        requirePackageFile(config.rootDir, config.model, "modelo GGUF")
        if (!config.modelFile.name.endsWith(".gguf", ignoreCase = true)) {
            error("O modelo do redator precisa ser .gguf.")
        }
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
            error("Pacote de redator inválido.")
        }
    }

    private val currentDir: File
        get() = context.filesDir.resolve(CURRENT_DIR)

    private companion object {
        const val CURRENT_DIR = "radio_writer"
        const val IMPORT_DIR = "radio_writer_import"
        const val MANIFEST_FILE = "manifest.json"
        const val READY_FILE = "model.ready"
        const val MAX_PACKAGE_BYTES = 1800L * 1024L * 1024L
    }
}

object LocalLlamaTextGenerator {
    private val nativeReady: Boolean by lazy {
        runCatching {
            System.loadLibrary("pailer_llama")
            true
        }.getOrDefault(false)
    }

    fun generate(config: RadioWriterPackageConfig, prompt: String): String {
        if (!nativeReady) error("Motor local do redator não carregou.")
        val output = generateNative(
            modelPath = config.modelFile.absolutePath,
            prompt = prompt,
            maxTokens = config.maxTokens,
            temperature = config.temperature,
            threads = config.threads,
            timeoutMs = LOCAL_WRITER_NATIVE_TIMEOUT_MS,
        ).trim()
        if (output.startsWith("ERROR:")) error(output.removePrefix("ERROR:").trim())
        return output
    }

    private external fun generateNative(
        modelPath: String,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        threads: Int,
        timeoutMs: Int,
    ): String

    // 02/09/2026: era 20_000 e abortava o decode a poucos instantes do fim (~27s reais neste
    // aparelho) - causa raiz do "modelo não carrega" relatado em 01/09. Subiu de novo (45s->60s)
    // porque o prompt cresceu (exemplo few-shot contra o bug de "eco da instrução"). Ver ADR-002.
    private const val LOCAL_WRITER_NATIVE_TIMEOUT_MS = 60_000
}
