package com.pailer.localtune.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// Backup manual/automatico de tudo que o usuario configura no app e que NAO vem do MediaStore
// (favoritos, correcoes de album/artista, fotos de artista, radios personalizadas/ocultas,
// configuracao dos boletins) - pedido do usuario 07/09/2026 depois de perder favoritos e fotos
// de artista num "adb uninstall" usado pra debug. android:allowBackup=true ja cobre isso via
// backup automatico do Android, mas roda em horario nao controlavel e pode ficar dias sem rodar;
// isso aqui e um backup PARALELO, pro usuario escolher o arquivo (Drive, etc.) e ter controle e
// confirmacao de quando rodou. Ver BackupScheduler pro agendamento diario e BackupAlarmReceiver
// pro disparo em segundo plano.
class BackupRepository(private val context: Context) {
    private val backupSettingsPrefs = context.getSharedPreferences("backup_settings", Context.MODE_PRIVATE)
    private val appFolderRepository = AppFolderRepository(context)

    fun backupUri(): Uri? = backupSettingsPrefs.getString(KEY_BACKUP_URI, null)
        ?.let { runCatching { Uri.parse(it) }.getOrNull() }

    // Pasta oficial (ver AppFolderRepository) tem prioridade sobre o arquivo escolhido
    // manualmente - se o usuario configurou as duas coisas, o backup passa a viver dentro da
    // pasta oficial (organizado junto com Logs/Redator Local/Pacote de Vozes), sem precisar
    // escolher arquivo nenhum.
    fun hasDestination(): Boolean = appFolderRepository.hasFolder() || backupUri() != null

    fun usingOfficialFolder(): Boolean = appFolderRepository.hasFolder()

    fun destinationDescription(): String? = when {
        appFolderRepository.hasFolder() ->
            "${appFolderRepository.folderDisplayName() ?: "pasta oficial"}/${AppFolderRepository.SUBFOLDER_BACKUP}/${AppFolderRepository.BACKUP_FILE_NAME}"
        backupUri() != null -> "arquivo escolhido manualmente"
        else -> null
    }

    fun lastBackupAt(): Long = backupSettingsPrefs.getLong(KEY_LAST_BACKUP_AT, 0L)

    // Prompt de "restaurar backup?" no primeiro uso com a biblioteca vazia de favoritos/historico
    // (ver LocalTuneViewModel.maybeOfferFreshRestore) - pedido do usuario 15/09/2026 pra reduzir o
    // atrito de reinstalar (ex.: ao unificar a chave de assinatura de release). So dispara 1 vez
    // por instalacao, nunca mais de novo depois (evita incomodar quem realmente comecou do zero).
    fun hasOfferedFreshRestorePrompt(): Boolean = backupSettingsPrefs.getBoolean(KEY_FRESH_RESTORE_PROMPT_SHOWN, false)

    fun markFreshRestorePromptOffered() {
        backupSettingsPrefs.edit().putBoolean(KEY_FRESH_RESTORE_PROMPT_SHOWN, true).apply()
    }

    // Uri de SAF (Storage Access Framework) so continua valida entre reaberturas do app/reboots
    // se a permissao for tornada "persistente" explicitamente - sem isso, some depois que o
    // processo que abriu o seletor de arquivos morre.
    fun saveBackupDestination(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        backupSettingsPrefs.edit().putString(KEY_BACKUP_URI, uri.toString()).apply()
    }

    fun clearBackupDestination() {
        backupUri()?.let { uri ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        backupSettingsPrefs.edit().remove(KEY_BACKUP_URI).apply()
    }

    suspend fun performBackup(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = buildBackupJson().toString().toByteArray(Charsets.UTF_8)
            val wrote = if (appFolderRepository.hasFolder()) {
                appFolderRepository.writeToSubfolder(
                    AppFolderRepository.SUBFOLDER_BACKUP,
                    AppFolderRepository.BACKUP_FILE_NAME,
                    "application/json",
                    bytes,
                )
            } else {
                val uri = backupUri() ?: return@withContext false
                context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } != null
            }
            if (wrote) {
                backupSettingsPrefs.edit().putLong(KEY_LAST_BACKUP_AT, System.currentTimeMillis()).apply()
            }
            wrote
        }.getOrDefault(false)
    }

    suspend fun restoreBackup(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: return@withContext false
            restoreFromJson(JSONObject(text))
            true
        }.getOrDefault(false)
    }

    private fun buildBackupJson(): JSONObject {
        val root = JSONObject()
        root.put(KEY_SCHEMA_VERSION, BACKUP_SCHEMA_VERSION)
        root.put(KEY_CREATED_AT, System.currentTimeMillis())

        val prefsJson = JSONObject()
        BACKED_UP_PREFS_NAMES.forEach { name ->
            prefsJson.put(name, serializePrefs(context.getSharedPreferences(name, Context.MODE_PRIVATE)))
        }
        root.put(KEY_PREFS, prefsJson)

        // Fotos de artista (MusicLibraryRepository.applyArtistPhoto) moram so em arquivo local
        // (filesDir/artist_photos), nao em SharedPreferences - sem embutir os bytes aqui, o
        // backup restauraria o CAMINHO salvo em metadata_overrides mas o arquivo em si nao
        // existiria mais depois de uma reinstalacao.
        val photosJson = JSONObject()
        File(context.filesDir, "artist_photos").takeIf { it.exists() }?.listFiles()?.forEach { file ->
            runCatching { photosJson.put(file.name, Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)) }
        }
        root.put(KEY_ARTIST_PHOTOS, photosJson)

        // Letras coladas/editadas/buscadas (LyricsRepository, filesDir/lyrics/<id>.lrc + index.json)
        // - mesmo motivo das fotos de artista: nao moram em SharedPreferences, entao sem embutir os
        // bytes aqui o restore de uma reinstalacao nao teria os arquivos.
        val lyricsJson = JSONObject()
        File(context.filesDir, "lyrics").takeIf { it.exists() }?.listFiles()?.forEach { file ->
            runCatching { lyricsJson.put(file.name, Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)) }
        }
        root.put(KEY_LYRICS, lyricsJson)

        val profilePhotoJson = JSONObject()
        File(context.filesDir, "profile").takeIf { it.exists() }?.listFiles()?.forEach { file ->
            runCatching { profilePhotoJson.put(file.name, Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)) }
        }
        root.put(KEY_PROFILE_PHOTO, profilePhotoJson)

        return root
    }

    // Cada valor vai marcado com o tipo original ({"t":"l","v":"123"}) em vez de um JSON plano
    // {"key": 123} - org.json, ao LER de volta um numero de um arquivo, devolve Integer sempre
    // que o valor cabe num Int (mesmo tendo sido escrito como Long), entao um long pequeno
    // (ex.: um id de faixa) virava Integer na restauracao e o restore gravava com putInt() em
    // vez de putLong(). SharedPreferences.getLong() em cima de um valor salvo como Integer
    // lanca ClassCastException - travava o app pra sempre na proxima leitura desse campo (bug
    // visto em campo 07/09/2026, corrigido tambem com um fallback auto-reparador no lado que le,
    // ver LocalTuneViewModel.getLongSafe). Long e Float guardados como STRING (campo "v") pra
    // nao depender de tipo numerico nenhum do JSON na volta - conversao e so string->long/float,
    // sem ambiguidade possivel.
    private fun serializePrefs(prefs: SharedPreferences): JSONObject {
        val obj = JSONObject()
        prefs.all.forEach { (key, value) ->
            val entry = JSONObject()
            when (value) {
                is String -> entry.put("t", "s").put("v", value)
                is Int -> entry.put("t", "i").put("v", value)
                is Long -> entry.put("t", "l").put("v", value.toString())
                is Float -> entry.put("t", "f").put("v", value.toString())
                is Boolean -> entry.put("t", "b").put("v", value)
                is Set<*> -> entry.put("t", "ss").put("v", JSONArray().apply { value.forEach { put(it.toString()) } })
                else -> return@forEach
            }
            obj.put(key, entry)
        }
        return obj
    }

    private fun restoreFromJson(root: JSONObject) {
        val prefsJson = root.optJSONObject(KEY_PREFS) ?: JSONObject()
        BACKED_UP_PREFS_NAMES.forEach prefsFile@{ name ->
            val obj = prefsJson.optJSONObject(name) ?: return@prefsFile
            val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
            obj.keys().forEach entry@{ key ->
                val entry = obj.optJSONObject(key) ?: return@entry
                when (entry.optString("t")) {
                    "s" -> editor.putString(key, entry.optString("v"))
                    "i" -> editor.putInt(key, entry.optInt("v"))
                    "l" -> editor.putLong(key, entry.optString("v").toLongOrNull() ?: 0L)
                    "f" -> editor.putFloat(key, entry.optString("v").toFloatOrNull() ?: 0f)
                    "b" -> editor.putBoolean(key, entry.optBoolean("v"))
                    "ss" -> {
                        val array = entry.optJSONArray("v") ?: JSONArray()
                        editor.putStringSet(key, (0 until array.length()).map { array.getString(it) }.toSet())
                    }
                }
            }
            editor.apply()
        }

        val photosJson = root.optJSONObject(KEY_ARTIST_PHOTOS) ?: JSONObject()
        val photoDir = File(context.filesDir, "artist_photos").apply { mkdirs() }
        photosJson.keys().forEach { fileName ->
            runCatching {
                File(photoDir, fileName).writeBytes(Base64.decode(photosJson.getString(fileName), Base64.NO_WRAP))
            }
        }

        val lyricsJson = root.optJSONObject(KEY_LYRICS) ?: JSONObject()
        val lyricsDir = File(context.filesDir, "lyrics").apply { mkdirs() }
        lyricsJson.keys().forEach { fileName ->
            runCatching {
                File(lyricsDir, fileName).writeBytes(Base64.decode(lyricsJson.getString(fileName), Base64.NO_WRAP))
            }
        }

        val profilePhotoJson = root.optJSONObject(KEY_PROFILE_PHOTO) ?: JSONObject()
        val profileDir = File(context.filesDir, "profile").apply { mkdirs() }
        profilePhotoJson.keys().forEach { fileName ->
            runCatching {
                File(profileDir, fileName).writeBytes(Base64.decode(profilePhotoJson.getString(fileName), Base64.NO_WRAP))
            }
        }
    }

    companion object {
        private const val BACKUP_SCHEMA_VERSION = 4
        private const val KEY_BACKUP_URI = "backup_uri"
        private const val KEY_LAST_BACKUP_AT = "last_backup_at"
        private const val KEY_FRESH_RESTORE_PROMPT_SHOWN = "fresh_restore_prompt_shown"
        private const val KEY_SCHEMA_VERSION = "schemaVersion"
        private const val KEY_CREATED_AT = "createdAt"
        private const val KEY_PREFS = "prefs"
        private const val KEY_ARTIST_PHOTOS = "artistPhotos"
        private const val KEY_LYRICS = "lyrics"
        private const val KEY_PROFILE_PHOTO = "profilePhoto"

        // As 4 SharedPreferences que guardam preferencia/estado do USUARIO (favoritos, overrides
        // de metadado, historico, config de boletim) - de proposito NAO inclui prefs puramente
        // tecnicas/efemeras (ex.: "widget_prefs" da PlayerWidget, cache de sessao de radio).
        private val BACKED_UP_PREFS_NAMES = listOf(
            "favorites",
            "metadata_overrides",
            "playback_history",
            "radio_bulletins",
            "user_profile",
            // Contadores de "faixa terminou de tocar sozinha na radio" (ADR-028/ADR-029,
            // MusicLibraryRepository.recordRadioPlayThrough) - acumulado de uso real, mesma
            // categoria de favorites/playback_history acima, não é cache técnico. Sem isso, um
            // restore de backup apagaria semanas de aprendizado da "Surprise Me"/afinidade.
            "radio_affinity",
        )
    }
}
