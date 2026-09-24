package com.pailer.localtune.data

import android.content.Context
import android.os.Build
import android.util.Log
import com.pailer.localtune.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

// Sinal "estou vivo" pro painel de ouvintes no Worker do Cloudflare (pedido do usuario
// 24/09/2026: saber quantos ouvintes estao usando a radio e quem sao). O mesmo Worker que serve
// o feed de boletins (ver BroadcastFeedRepository) recebe em POST /api/heartbeat e mostra tudo
// em /painel. Codigo do Worker: _broadcast-boletins-local/distribuicao-app/src/index.js.
//
// Quem chama:
// - MusicPlaybackService: toda vez que play/pause muda e a cada HEARTBEAT_INTERVAL_MS tocando -
//   e o servico (nao o ViewModel) porque ele continua vivo com a musica tocando mesmo depois de
//   o usuario tirar o app da tela de recentes.
// - MainActivity: toda vez que o app volta pra frente e a cada HEARTBEAT_INTERVAL_MS com a tela
//   visivel (conta como "app aberto" mesmo sem nada tocando).
// - LocalTuneViewModel: ao terminar o onboarding e ao salvar o perfil (manda o nome novo na hora).
//
// install_id e um UUID aleatorio gerado aqui no primeiro envio - nao e ID do aparelho, some se o
// app for desinstalado. A foto de perfil NAO e enviada, so se existe ou nao.
object ListenerHeartbeat {
    const val HEARTBEAT_INTERVAL_MS = 2 * 60 * 1000L

    private const val ENDPOINT = "https://pailer-fm-boletins.well-pailer64.workers.dev/api/heartbeat"
    private const val TAG = "PailerHeartbeat"
    private const val PREFS = "listener_heartbeat"
    private const val KEY_INSTALL_ID = "install_id"

    data class NowPlaying(
        val isPlaying: Boolean,
        val title: String = "",
        val artist: String = "",
        val station: String = "",
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Ultimo estado conhecido do player, atualizado pelo MusicPlaybackService (mesmo processo) -
    // assim o envio de "app aberto" do ViewModel nao manda is_playing=false por engano enquanto
    // a musica esta tocando em segundo plano.
    @Volatile
    private var lastNowPlaying = NowPlaying(isPlaying = false)

    fun send(context: Context, nowPlaying: NowPlaying? = null) {
        if (nowPlaying != null) lastNowPlaying = nowPlaying
        val snapshot = lastNowPlaying
        val appContext = context.applicationContext
        scope.launch {
            runCatching { post(buildPayload(appContext, snapshot)) }
                .onFailure { Log.w(TAG, "heartbeat falhou: ${it.message}") }
        }
    }

    private fun installId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_INSTALL_ID, null)?.let { return it }
        val id = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_INSTALL_ID, id).apply()
        return id
    }

    // Le direto das mesmas SharedPreferences que o LocalTuneViewModel escreve (user_profile e
    // listening_stats) - o servico nao tem acesso ao ViewModel.
    private fun buildPayload(context: Context, nowPlaying: NowPlaying): JSONObject {
        val profile = context.getSharedPreferences("user_profile", Context.MODE_PRIVATE)
        val stats = context.getSharedPreferences("listening_stats", Context.MODE_PRIVATE)
        val songsCompleted = stats.getInt("songs_completed", 0)
        val photoPath = profile.getString("profile_photo_path", null)
        val versionName = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
        return JSONObject()
            .put("install_id", installId(context))
            .put("name", profile.getString("profile_name", "").orEmpty())
            .put("birthday", profile.getString("profile_birthday", "").orEmpty())
            .put("has_photo", photoPath != null && java.io.File(photoPath).exists())
            .put("app_version", BuildConfig.RELEASE_TAG.takeIf { it != "local-dev" } ?: "$versionName-dev")
            .put("device", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
            .put("android", Build.VERSION.RELEASE.orEmpty())
            .put("is_playing", nowPlaying.isPlaying)
            .put("station", nowPlaying.station)
            .put("track_title", nowPlaying.title)
            .put("track_artist", nowPlaying.artist)
            .put("level", songsCompleted / 50 + 1)
            .put("songs_completed", songsCompleted)
            .put("total_listening_ms", stats.getLong("total_listening_ms", 0L))
            .put("radio_listening_ms", stats.getLong("radio_listening_ms", 0L))
    }

    private fun post(payload: JSONObject) {
        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 10_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) Log.w(TAG, "heartbeat HTTP $code")
        } finally {
            connection.disconnect()
        }
    }
}
