package com.pailer.localtune.player.cast

import android.util.Log
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata as CastMediaMetadata
import com.google.android.gms.common.images.WebImage
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.pailer.localtune.player.RemotePlaybackBridge

// Ponte fina com o RemoteMediaClient do Cast - NAO tenta recriar a fila/estado do ExoPlayer local
// (controller) do zero. Em vez disso, LocalTuneViewModel mantem o controller local tocando (so
// mudo) como a "fonte da verdade" de fila/posicao/proxima-anterior de sempre, e so ESPELHA pra ca
// (playUrl a cada troca de faixa, pause/resume a cada mudanca de isPlaying) - ver
// mirrorToRemoteIfNeeded(). Isso evita duplicar toda a logica de fila/radio ja existente na
// ViewModel so pra falar com o Cast.
class CastPlaybackBridge(private val remoteMediaClient: RemoteMediaClient) : RemotePlaybackBridge {

    private val callback = object : RemoteMediaClient.Callback() {
        override fun onMediaError(error: com.google.android.gms.cast.MediaError) {
            Log.w(TAG, "onMediaError: ${error.reason}")
        }
    }

    init {
        remoteMediaClient.registerCallback(callback)
    }

    override fun playUrl(
        url: String,
        mimeType: String,
        title: String,
        artist: String,
        album: String,
        artworkUrl: String?,
        startPositionMs: Long,
        autoplay: Boolean,
    ) {
        val metadata = CastMediaMetadata(CastMediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
            putString(CastMediaMetadata.KEY_TITLE, title)
            putString(CastMediaMetadata.KEY_ARTIST, artist)
            putString(CastMediaMetadata.KEY_ALBUM_TITLE, album)
            artworkUrl?.let { addImage(WebImage(android.net.Uri.parse(it))) }
        }
        val mediaInfo = MediaInfo.Builder(url)
            .setContentUrl(url)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(mimeType)
            .setMetadata(metadata)
            .build()
        val request = MediaLoadRequestData.Builder()
            .setMediaInfo(mediaInfo)
            .setAutoplay(autoplay)
            .setCurrentTime(startPositionMs.coerceAtLeast(0L))
            .build()
        remoteMediaClient.load(request)
    }

    override fun pause() {
        remoteMediaClient.pause()
    }

    override fun resume() {
        remoteMediaClient.play()
    }

    override fun setVolume(volume: Float) {
        runCatching { remoteMediaClient.setStreamVolume(volume.toDouble().coerceIn(0.0, 1.0)) }
    }

    override fun release() {
        remoteMediaClient.unregisterCallback(callback)
    }

    private companion object {
        const val TAG = "PailerCast"
    }
}
