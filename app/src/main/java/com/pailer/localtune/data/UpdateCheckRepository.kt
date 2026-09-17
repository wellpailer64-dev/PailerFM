package com.pailer.localtune.data

import android.content.Context
import com.pailer.localtune.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class LatestReleaseInfo(
    val tagName: String,
    val apkDownloadUrl: String,
    val htmlUrl: String,
    // Corpo da release no GitHub (ver build-apk.yml, "Monta changelog resumido") - lista de
    // bullets ja pronta (um por commit desde a release anterior), pra mostrar no popup de
    // atualizacao em vez do texto generico "versao X disponivel". Null/vazio pra releases sem
    // changelog (ex. tag manual antiga, ou release feita antes desse campo existir) - o popup
    // cai pro texto generico nesse caso.
    val notes: String? = null,
)

// Checagem de atualizacao fora da Play Store (pedido do usuario 15/09/2026: em vez de mandar o
// APK manualmente pelo WhatsApp toda vez, o app recebe um popup de "atualizacao disponivel"
// sozinho e baixa/instala direto). Le a API publica do GitHub Releases (repo publico, sem
// chave/token - limite de 60 req/hora por IP de sobra pro uso pessoal) e compara a tag da ultima
// release contra BuildConfig.RELEASE_TAG (embutido no build pelo CI ANTES de compilar, ver
// build-apk.yml/"Calcula tag da build" e app/build.gradle.kts) - a mesma tag que vira o nome da
// release publicada, entao a comparacao e sempre entre "o que esse APK e" e "o que ja saiu".
class UpdateCheckRepository(private val context: Context) {
    // NAO usa GET /releases/latest - esse endpoint ignora prereleases (a doc do GitHub e explicita:
    // "the most recent non-prerelease, non-draft release"), e TODA release publicada pelo push
    // automatico na master sai marcada prerelease:true (so uma tag manual v* sairia "de verdade" -
    // ver build-apk.yml) - contra esse endpoint o app nunca acharia nenhuma. Usa a LISTAGEM
    // (GET /releases, mais recente primeiro por padrao) e pega o primeiro item.
    suspend fun fetchLatestRelease(): LatestReleaseInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val releases = getJsonArray("https://api.github.com/repos/$REPO/releases?per_page=1") ?: return@runCatching null
            val json = releases.optJSONObject(0) ?: return@runCatching null
            val tagName = json.optString("tag_name").takeIf { it.isNotBlank() } ?: return@runCatching null
            val assets = json.optJSONArray("assets") ?: return@runCatching null
            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url").takeIf { it.isNotBlank() }
                    break
                }
            }
            val notes = json.optString("body").trim().takeIf { it.isNotBlank() }
            apkUrl?.let { url ->
                LatestReleaseInfo(tagName = tagName, apkDownloadUrl = url, htmlUrl = json.optString("html_url"), notes = notes)
            }
        }.getOrNull()
    }

    // "vAAAA.MM.DD-N" (build automatica de push/dispatch) ou "vX.Y.Z" (tag manual) - compara por
    // partes NUMERICAS em vez de string crua, pra "v2026.09.2-9" nao "perder" de "v2026.09.10-1"
    // por ordem lexicografica (comparando texto, "9" > "1" - errado quando o que importa e o
    // numero). "local-dev" (build feita na maquina do usuario, sem CI - ver app/build.gradle.kts)
    // nunca tem atualizacao "disponivel": e so pra teste, nao faz sentido notificar sobre ela
    // mesma.
    fun isNewerThanCurrent(latestTag: String): Boolean {
        if (BuildConfig.RELEASE_TAG == "local-dev") return false
        if (latestTag == BuildConfig.RELEASE_TAG) return false
        val latestParts = versionParts(latestTag) ?: return true
        val currentParts = versionParts(BuildConfig.RELEASE_TAG) ?: return true
        for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
            val latest = latestParts.getOrElse(i) { 0 }
            val current = currentParts.getOrElse(i) { 0 }
            if (latest != current) return latest > current
        }
        return false
    }

    private fun versionParts(tag: String): List<Int>? {
        val parts = tag.removePrefix("v").replace("-", ".").split(".").map { it.toIntOrNull() }
        return if (parts.any { it == null }) null else parts.filterNotNull()
    }

    // Baixa em streaming (nao carrega os ~70MB inteiros na memoria) reportando progresso via
    // Content-Length - salva em cacheDir/app_update (exposto ao FileProvider, ver res/xml/
    // file_paths.xml) pra poder virar um content:// URI na hora de abrir o instalador.
    suspend fun downloadApk(url: String, onProgress: (Float) -> Unit): File? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "app_update").apply { mkdirs() }
            val destination = File(dir, "PailerFM-update.apk")
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
                requestMethod = "GET"
            }
            val total = connection.contentLengthLong
            var downloaded = 0L
            connection.inputStream.use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) onProgress((downloaded.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            connection.disconnect()
            destination.takeIf { it.length() > 0 }
        }.getOrNull()
    }

    private fun getJson(url: String): JSONObject? {
        val connection = openGitHubConnection(url)
        return runCatching {
            connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        }.getOrNull().also { connection.disconnect() }
    }

    private fun getJsonArray(url: String): JSONArray? {
        val connection = openGitHubConnection(url)
        return runCatching {
            connection.inputStream.bufferedReader().use { JSONArray(it.readText()) }
        }.getOrNull().also { connection.disconnect() }
    }

    private fun openGitHubConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 12_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", USER_AGENT)
        }

    private companion object {
        const val REPO = "wellpailer64-dev/PailerFM"
        const val USER_AGENT = "PailerPlayer/0.1 (personal-local-android-app)"
    }
}
