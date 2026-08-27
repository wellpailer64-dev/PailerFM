package com.pailer.localtune.player

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.pailer.localtune.MainActivity
import com.pailer.localtune.widget.PlayerWidgetActions
import com.pailer.localtune.widget.PlayerWidgetRenderer

class MusicPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private val widgetListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (player.mediaItemCount > 0) {
                PlayerWidgetRenderer.updateAll(this@MusicPlaybackService, player)
            }
        }
    }

    // Diagnostico leve (nao altera comportamento) para descobrir a causa real de
    // "o player para sozinho depois de algumas musicas" - grep por PailerPlaybackDiag
    // no logcat na proxima vez que acontecer. Ver docs/DECISIONS.md ADR-009.
    private val diagnosticListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Log.w(TAG_DIAG, "isPlaying=$isPlaying mediaItemIndex=${mediaSession?.player?.currentMediaItemIndex}")
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            Log.w(TAG_DIAG, "playWhenReady=$playWhenReady reason=${playWhenReadyReasonName(reason)}")
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG_DIAG, "onPlayerError code=${error.errorCodeName}", error)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            Log.w(TAG_DIAG, "playbackState=${playbackStateName(playbackState)}")
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.w(TAG_DIAG, "onCreate")

        val player = ExoPlayer.Builder(this)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true,
                )
                setHandleAudioBecomingNoisy(true)
                addListener(widgetListener)
                addListener(diagnosticListener)
            }

        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val player = mediaSession?.player
        if (player != null && intent?.action in WIDGET_ACTIONS) {
            if (player.mediaItemCount > 0) {
                when (intent?.action) {
                    PlayerWidgetActions.PLAY_PAUSE -> {
                        if (player.isPlaying) {
                            player.pause()
                        } else {
                            player.play()
                        }
                    }

                    PlayerWidgetActions.NEXT -> player.seekToNextMediaItem()
                    PlayerWidgetActions.PREVIOUS -> player.seekToPreviousMediaItem()
                }
                PlayerWidgetRenderer.updateAll(this, player)
            }
            return START_NOT_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        Log.w(TAG_DIAG, "onDestroy isPlaying=${mediaSession?.player?.isPlaying}")
        mediaSession?.run {
            player.removeListener(widgetListener)
            player.removeListener(diagnosticListener)
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.w(TAG_DIAG, "onTaskRemoved isPlaying=${mediaSession?.player?.isPlaying}")
        super.onTaskRemoved(rootIntent)
    }

    private fun playWhenReadyReasonName(reason: Int): String = when (reason) {
        Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST -> "USER_REQUEST"
        Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> "AUDIO_FOCUS_LOSS"
        Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> "AUDIO_BECOMING_NOISY"
        Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE -> "REMOTE"
        Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM -> "END_OF_MEDIA_ITEM"
        Player.PLAY_WHEN_READY_CHANGE_REASON_SUPPRESSED_TOO_LONG -> "SUPPRESSED_TOO_LONG"
        else -> "UNKNOWN($reason)"
    }

    private fun playbackStateName(state: Int): String = when (state) {
        Player.STATE_IDLE -> "IDLE"
        Player.STATE_BUFFERING -> "BUFFERING"
        Player.STATE_READY -> "READY"
        Player.STATE_ENDED -> "ENDED"
        else -> "UNKNOWN($state)"
    }

    private companion object {
        const val TAG_DIAG = "PailerPlaybackDiag"
        val WIDGET_ACTIONS = setOf(
            PlayerWidgetActions.PLAY_PAUSE,
            PlayerWidgetActions.NEXT,
            PlayerWidgetActions.PREVIOUS,
        )
    }
}
