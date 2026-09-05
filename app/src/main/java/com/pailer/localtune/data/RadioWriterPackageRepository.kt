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
                                    error("Pacote de redator maior que 3 GB.")
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
            // Teto subido de 260 pra 420 (03/09/2026): era calibrado so pro Qwen3 1.7B, que
            // escreve mais telegrafico. O Qwen3 4B segue melhor a instrucao de analise
            // critica/reflexao (por isso a troca) e por consequencia usa mais tokens por fala -
            // com o teto antigo ele cortava no meio da fala 2 antes de terminar as 6 (ver
            // manifest.json de radio-writer-models/qwen3-4b-q4km, maxTokens=380).
            maxTokens = manifest.optInt("maxTokens", 72).coerceIn(32, 420),
            temperature = manifest.optDouble("temperature", 0.55).toFloat().coerceIn(0.1f, 1.2f),
            // Teto subido de 6 pra 8 (03/09/2026, pedido do usuario) - aparelho tem 8 nucleos de
            // verdade (Dimensity 1200, confirmado com `adb shell nproc`), testando o maximo
            // fisico depois que threads=5 nao resolveu a lentidao sozinho (a causa real era
            // concorrencia com o preparo de fundo, ja corrigida). 3 era calibrado por um teste
            // ANTERIOR na sintese de voz (Supertonic), nao no redator local - mais threads la
            // esquentou o suficiente pra throttling anular o ganho (2t=410s, 4t=496s, PIOROU) -
            // ver LocalRadioVoiceEngine.numThreads, tambem subido pra 8 pra reteste.
            threads = manifest.optInt("threads", 2).coerceIn(1, 8),
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
        // 1,8GB so cobria o Qwen3 1.7B Q4_K_M (~1,1GB) com folga. Subido pra 3GB pra caber o
        // Qwen3 4B Q4_K_M (~2,33GB, ver radio-writer-models/qwen3-4b-q4km) - pedido do usuario
        // (03/09/2026), avaliou que o 1.7B nao seguia instrucao estrutural (JSON/topico) de
        // forma confiavel o bastante mesmo apos os ajustes de prompt/timeout dessa sessao.
        const val MAX_PACKAGE_BYTES = 3000L * 1024L * 1024L
    }
}

// Chamado pelo JNI (pailer_llama_jni.cpp) - progresso REAL (tokens gerados/teto, fase
// carregando/lendo/escrevendo com segundos decorridos), nao mais uma percentual inventada
// (pedido do usuario 03/09/2026, ver RadioBulletinBufferUiState.progressPercent). Roda na mesma
// thread que chamou generateNative (Dispatchers.Default), nunca na Main.
// onPhase: chamado 2x - ("lendo", tempoDeCargaMs) assim que o modelo carrega (antes do prefill,
// ver JNI) e ("escrevendo", tempoDeLeituraMs) assim que o prefill termina (antes do decode).
// onProgress: chamado a cada token gerado durante o decode, com o tempo decorrido SO dessa fase.
interface LlamaProgressListener {
    fun onPhase(phase: String, elapsedMs: Long)
    fun onProgress(current: Int, max: Int, elapsedMs: Long)
}

object LocalLlamaTextGenerator {
    private val nativeReady: Boolean by lazy {
        runCatching {
            System.loadLibrary("pailer_llama")
            true
        }.getOrDefault(false)
    }

    fun generate(
        config: RadioWriterPackageConfig,
        prompt: String,
        onProgress: LlamaProgressListener? = null,
    ): String {
        if (!nativeReady) error("Motor local do redator não carregou.")
        val output = generateNative(
            modelPath = config.modelFile.absolutePath,
            prompt = prompt,
            maxTokens = config.maxTokens,
            temperature = config.temperature,
            threads = config.threads,
            timeoutMs = LOCAL_WRITER_NATIVE_TIMEOUT_MS,
            progressListener = onProgress,
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
        progressListener: LlamaProgressListener?,
    ): String

    // 02/09/2026: era 20_000 e abortava o decode a poucos instantes do fim (~27s reais neste
    // aparelho) - causa raiz do "modelo não carrega" relatado em 01/09. Subiu de novo (45s->60s)
    // porque o prompt cresceu (exemplo few-shot contra o bug de "eco da instrução"). Ver ADR-002.
    // 03/09/2026: 60s continuava abortando toda vez que o pre-aquecimento (prewarmCoreBuffer,
    // ver LocalTuneViewModel) rodava logo na abertura do app - decode compete por CPU com o
    // resto da inicializacao (scan de biblioteca, RSS, media session) e passa dos 60s nesse
    // momento, mesmo com folga sobrando no timeout EXTERNO (LOCAL_WRITER_TIMEOUT_MS em
    // RadioBulletin.kt, ja em 250s) - esse timeout INTERNO sempre disparava primeiro e mascarava
    // a folga de fora. Subido pra 240s (abaixo do externo, pra abortar graciosamente com
    // "ERROR:" em vez do timeout externo cortar no meio - mesmo cuidado que motivou o 20s->45s
    // original).
    // 03/09/2026 (2a vez): trocado pro Qwen3 4B (2,4x mais parametros que o 1.7B) + maxTokens
    // subido de 260 pra 420 - as duas coisas juntas aumentam bastante o tempo de decode real.
    // Subido pra 340s, sempre abaixo do externo (ver LOCAL_WRITER_TIMEOUT_MS, subido junto pra
    // 360s) pelo mesmo motivo de sempre: o interno tem que abortar primeiro e devolver
    // "ERROR:" graciosamente, nunca o externo cortando a chamada nativa no meio.
    private const val LOCAL_WRITER_NATIVE_TIMEOUT_MS = 340_000
}
