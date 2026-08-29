package com.pailer.localtune.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.edit
import androidx.media3.common.C
import androidx.media3.common.Player
import com.pailer.localtune.MainActivity
import com.pailer.localtune.R
import com.pailer.localtune.player.MusicPlaybackService
import com.pailer.localtune.util.DayPeriod

class CompactPlayerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        PlayerWidgetRenderer.update(context, CompactPlayerWidgetProvider::class.java, R.layout.widget_player_compact, appWidgetIds)
    }
}

class LargePlayerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        PlayerWidgetRenderer.update(context, LargePlayerWidgetProvider::class.java, R.layout.widget_player_large, appWidgetIds)
    }
}

class PlayerWidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runCatching {
            context.startService(
                Intent(context, MusicPlaybackService::class.java).setAction(intent.action)
            )
        }
    }
}

object PlayerWidgetRenderer {
    fun updateAll(context: Context, player: Player? = null) {
        val state = player?.toWidgetState()?.also { saveState(context, it) } ?: loadState(context)
        val manager = AppWidgetManager.getInstance(context)
        update(
            context = context,
            provider = CompactPlayerWidgetProvider::class.java,
            layoutId = R.layout.widget_player_compact,
            appWidgetIds = manager.getAppWidgetIds(ComponentName(context, CompactPlayerWidgetProvider::class.java)),
            state = state,
        )
        update(
            context = context,
            provider = LargePlayerWidgetProvider::class.java,
            layoutId = R.layout.widget_player_large,
            appWidgetIds = manager.getAppWidgetIds(ComponentName(context, LargePlayerWidgetProvider::class.java)),
            state = state,
        )
    }

    internal fun update(
        context: Context,
        provider: Class<*>,
        layoutId: Int,
        appWidgetIds: IntArray,
        state: WidgetState = loadState(context),
    ) {
        if (appWidgetIds.isEmpty()) return
        val manager = AppWidgetManager.getInstance(context)
        appWidgetIds.forEach { widgetId ->
            manager.updateAppWidget(widgetId, buildViews(context, layoutId, state))
        }
    }

    private fun buildViews(context: Context, layoutId: Int, state: WidgetState): RemoteViews =
        RemoteViews(context.packageName, layoutId).apply {
            setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
            setOnClickPendingIntent(R.id.widget_play_pause, actionIntent(context, ACTION_PLAY_PAUSE, 10))
            setOnClickPendingIntent(R.id.widget_next, actionIntent(context, ACTION_NEXT, 11))
            setOnClickPendingIntent(R.id.widget_previous, actionIntent(context, ACTION_PREVIOUS, 12))

            val liveLabel = "🔴 AO VIVO · ${state.radioName}"
            val radioFrame = if (state.isRadio) RadioGifFrameCache.frameFor(context, DayPeriod.current()) else null
            if (radioFrame != null) {
                setImageViewBitmap(R.id.widget_bg_image, radioFrame)
                setViewVisibility(R.id.widget_bg_image, View.VISIBLE)
                setViewVisibility(R.id.widget_bg_scrim, View.VISIBLE)
            } else {
                setViewVisibility(R.id.widget_bg_image, View.GONE)
                setViewVisibility(R.id.widget_bg_scrim, View.GONE)
            }

            setTextViewText(R.id.widget_title, state.title.ifBlank { "Pailer FM" })
            setImageViewResource(
                R.id.widget_play_pause,
                if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
            )
            setImageViewUriOrFallback(R.id.widget_artwork, state.artworkUri)
            setProgressBar(R.id.widget_progress, 1000, state.progress, false)

            if (layoutId == R.layout.widget_player_large) {
                setTextViewText(R.id.widget_artist, state.artist.ifBlank { "Biblioteca local" })
                setTextColor(R.id.widget_artist, LARGE_ARTIST_DEFAULT_COLOR)
                if (state.isRadio) {
                    setTextViewText(R.id.widget_album, liveLabel)
                    setTextColor(R.id.widget_album, LIVE_ACCENT_COLOR)
                } else {
                    setTextViewText(R.id.widget_album, state.album.ifBlank { "Biblioteca local" })
                    setTextColor(R.id.widget_album, LARGE_ALBUM_DEFAULT_COLOR)
                }
                setTextViewText(R.id.widget_queue_count, state.queueLabel)
                setTextViewText(R.id.widget_next_one, state.nextTracks.getOrNull(0).orEmpty())
                setTextViewText(R.id.widget_next_two, state.nextTracks.getOrNull(1).orEmpty())
                setTextViewText(R.id.widget_next_three, state.nextTracks.getOrNull(2).orEmpty())
                setViewVisibility(R.id.widget_next_one, if (state.nextTracks.isNotEmpty()) View.VISIBLE else View.GONE)
                setViewVisibility(R.id.widget_next_two, if (state.nextTracks.size > 1) View.VISIBLE else View.GONE)
                setViewVisibility(R.id.widget_next_three, if (state.nextTracks.size > 2) View.VISIBLE else View.GONE)
                setViewVisibility(R.id.widget_empty_queue, if (state.nextTracks.isEmpty()) View.VISIBLE else View.GONE)
            } else {
                if (state.isRadio) {
                    setTextViewText(R.id.widget_artist, liveLabel)
                    setTextColor(R.id.widget_artist, LIVE_ACCENT_COLOR)
                } else {
                    setTextViewText(R.id.widget_artist, state.artist.ifBlank { "Escolha uma faixa no app" })
                    setTextColor(R.id.widget_artist, COMPACT_ARTIST_DEFAULT_COLOR)
                }
            }
        }

    private fun RemoteViews.setImageViewUriOrFallback(viewId: Int, uri: String?) {
        if (uri.isNullOrBlank()) {
            setImageViewResource(viewId, R.drawable.pailer_logo)
        } else {
            setImageViewUri(viewId, Uri.parse(uri))
        }
    }

    private fun Player.toWidgetState(): WidgetState {
        val metadata = currentMediaItem?.mediaMetadata
        val duration = duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
        val progress = if (duration > 0) {
            ((currentPosition.coerceAtLeast(0L) * 1000L) / duration).toInt().coerceIn(0, 1000)
        } else {
            0
        }
        val nextTracks = ((currentMediaItemIndex + 1) until mediaItemCount)
            .take(3)
            .mapNotNull { index ->
                val nextMetadata = getMediaItemAt(index).mediaMetadata
                val title = nextMetadata.title?.toString().orEmpty()
                val artist = nextMetadata.artist?.toString().orEmpty()
                if (title.isBlank()) null else "${index - currentMediaItemIndex}. $title${artist.takeIf { it.isNotBlank() }?.let { " - $it" }.orEmpty()}"
            }

        return WidgetState(
            title = metadata?.title?.toString().orEmpty(),
            artist = metadata?.artist?.toString().orEmpty(),
            album = metadata?.albumTitle?.toString().orEmpty(),
            artworkUri = metadata?.artworkUri?.toString(),
            isPlaying = isPlaying,
            progress = progress,
            queueLabel = if (mediaItemCount > 0) "${currentMediaItemIndex + 1}/$mediaItemCount" else "0/0",
            nextTracks = nextTracks,
            isRadio = !metadata?.station.isNullOrBlank(),
            radioName = metadata?.station?.toString().orEmpty(),
        )
    }

    private fun saveState(context: Context, state: WidgetState) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_TITLE, state.title)
            putString(KEY_ARTIST, state.artist)
            putString(KEY_ALBUM, state.album)
            putString(KEY_ARTWORK, state.artworkUri)
            putBoolean(KEY_PLAYING, state.isPlaying)
            putInt(KEY_PROGRESS, state.progress)
            putString(KEY_QUEUE, state.queueLabel)
            putString(KEY_NEXT, state.nextTracks.joinToString("\n"))
            putBoolean(KEY_IS_RADIO, state.isRadio)
            putString(KEY_RADIO_NAME, state.radioName)
        }
    }

    private fun loadState(context: Context): WidgetState {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return WidgetState(
            title = prefs.getString(KEY_TITLE, null).orEmpty(),
            artist = prefs.getString(KEY_ARTIST, null).orEmpty(),
            album = prefs.getString(KEY_ALBUM, null).orEmpty(),
            artworkUri = prefs.getString(KEY_ARTWORK, null),
            isPlaying = prefs.getBoolean(KEY_PLAYING, false),
            progress = prefs.getInt(KEY_PROGRESS, 0),
            queueLabel = prefs.getString(KEY_QUEUE, "0/0").orEmpty(),
            nextTracks = prefs.getString(KEY_NEXT, null)
                ?.split("\n")
                ?.filter { it.isNotBlank() }
                ?: emptyList(),
            isRadio = prefs.getBoolean(KEY_IS_RADIO, false),
            radioName = prefs.getString(KEY_RADIO_NAME, null).orEmpty(),
        )
    }

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            20,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun actionIntent(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            context,
            requestCode,
            Intent(context, MusicPlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private const val PREFS = "player_widget_state"
    private const val KEY_TITLE = "title"
    private const val KEY_ARTIST = "artist"
    private const val KEY_ALBUM = "album"
    private const val KEY_ARTWORK = "artwork"
    private const val KEY_PLAYING = "playing"
    private const val KEY_PROGRESS = "progress"
    private const val KEY_QUEUE = "queue"
    private const val KEY_NEXT = "next"
    private const val KEY_IS_RADIO = "is_radio"
    private const val KEY_RADIO_NAME = "radio_name"

    private const val LIVE_ACCENT_COLOR = 0xFFB53A2E.toInt()
    private const val LARGE_ARTIST_DEFAULT_COLOR = 0xDDFFFFFF.toInt()
    private const val LARGE_ALBUM_DEFAULT_COLOR = 0x99FFFFFF.toInt()
    private const val COMPACT_ARTIST_DEFAULT_COLOR = 0xCCFFFFFF.toInt()
}

internal data class WidgetState(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artworkUri: String? = null,
    val isPlaying: Boolean = false,
    val progress: Int = 0,
    val queueLabel: String = "0/0",
    val nextTracks: List<String> = emptyList(),
    val isRadio: Boolean = false,
    val radioName: String = "",
)

object PlayerWidgetActions {
    const val PLAY_PAUSE = "com.pailer.localtune.widget.PLAY_PAUSE"
    const val NEXT = "com.pailer.localtune.widget.NEXT"
    const val PREVIOUS = "com.pailer.localtune.widget.PREVIOUS"
}

private const val ACTION_PLAY_PAUSE = PlayerWidgetActions.PLAY_PAUSE
private const val ACTION_NEXT = PlayerWidgetActions.NEXT
private const val ACTION_PREVIOUS = PlayerWidgetActions.PREVIOUS
