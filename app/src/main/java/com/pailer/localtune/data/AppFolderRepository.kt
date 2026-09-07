package com.pailer.localtune.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Pasta oficial do Pailer FM (pedido do usuario 07/09/2026) - uma unica pasta que o usuario
// escolhe UMA VEZ (ACTION_OPEN_DOCUMENT_TREE, permissao persistida - nao pede de novo depois),
// dentro da qual o app organiza subpastas: Backup, Logs, Redator Local, Pacote de Vozes.
//
// IMPORTANTE - por que Redator Local/Pacote de Vozes sao so uma COPIA DE REFERENCIA aqui: os
// modelos de verdade (GGUF do redator, ONNX da voz) sao carregados por codigo NATIVO (llama.cpp/
// sherpa-onnx via JNI) que precisa de um caminho de arquivo de disco de verdade - uma pasta via
// SAF/DocumentFile e um content:// Uri, nao um caminho de arquivo, e bibliotecas nativas nao
// conseguem abrir isso diretamente. Por isso o app CONTINUA extraindo/rodando os pacotes em
// armazenamento privado interno (ver RadioWriterPackageRepository/RadioVoicePackageRepository,
// pasta context.filesDir) - so o .zip ORIGINAL importado e copiado pra pasta oficial, como
// referencia/arquivo organizado pro usuario, sem o app depender dele pra funcionar.
class AppFolderRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("app_folder", Context.MODE_PRIVATE)

    fun hasFolder(): Boolean = folderUri() != null

    fun folderUri(): Uri? = prefs.getString(KEY_FOLDER_URI, null)
        ?.let { runCatching { Uri.parse(it) }.getOrNull() }

    fun folderDisplayName(): String? {
        val uri = folderUri() ?: return null
        return runCatching { DocumentFile.fromTreeUri(context, uri)?.name }.getOrNull()
    }

    fun setFolder(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        prefs.edit().putString(KEY_FOLDER_URI, uri.toString()).apply()
        ensureSubfolders()
    }

    fun clearFolder() {
        folderUri()?.let { uri ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        prefs.edit().remove(KEY_FOLDER_URI).apply()
    }

    fun ensureSubfolders() {
        val root = rootDocument() ?: return
        listOf(SUBFOLDER_BACKUP, SUBFOLDER_LOGS, SUBFOLDER_WRITER, SUBFOLDER_VOICE).forEach { name ->
            if (root.findFile(name) == null) {
                runCatching { root.createDirectory(name) }
            }
        }
    }

    // Grava/substitui um arquivo dentro de uma subpasta oficial - usado pelo backup (JSON) e
    // pelos relatorios de crash (texto).
    fun writeToSubfolder(subfolderName: String, fileName: String, mimeType: String, bytes: ByteArray): Boolean {
        val folder = subfolder(subfolderName) ?: return false
        return runCatching {
            folder.findFile(fileName)?.delete()
            val file = folder.createFile(mimeType, fileName) ?: return false
            context.contentResolver.openOutputStream(file.uri)?.use { it.write(bytes) } ?: return false
            true
        }.getOrDefault(false)
    }

    // Copia um Uri de conteudo (ex.: o .zip que o usuario acabou de escolher pra importar) pra
    // dentro de uma subpasta oficial - so uma referencia organizada, ver comentario da classe.
    suspend fun copyUriToSubfolder(uri: Uri, subfolderName: String, fileName: String, mimeType: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext false
                writeToSubfolder(subfolderName, fileName, mimeType, bytes)
            }.getOrDefault(false)
        }

    private fun rootDocument(): DocumentFile? {
        val uri = folderUri() ?: return null
        return runCatching { DocumentFile.fromTreeUri(context, uri) }.getOrNull()?.takeIf { it.exists() && it.canWrite() }
    }

    private fun subfolder(name: String): DocumentFile? {
        val root = rootDocument() ?: return null
        return root.findFile(name)?.takeIf { it.isDirectory }
            ?: runCatching { root.createDirectory(name) }.getOrNull()
    }

    companion object {
        private const val KEY_FOLDER_URI = "folder_uri"
        const val SUBFOLDER_BACKUP = "Backup"
        const val SUBFOLDER_LOGS = "Logs"
        const val SUBFOLDER_WRITER = "Redator Local"
        const val SUBFOLDER_VOICE = "Pacote de Vozes"
        const val BACKUP_FILE_NAME = "pailer_fm_backup.json"
    }
}
