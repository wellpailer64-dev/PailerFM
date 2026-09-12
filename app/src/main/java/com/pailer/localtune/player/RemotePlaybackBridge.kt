package com.pailer.localtune.player

// Abstrai "pra onde o audio esta sendo espelhado" (ver mirrorToRemoteIfNeeded em
// LocalTuneViewModel) - Google Cast (CastPlaybackBridge) e DLNA/UPnP (DlnaPlaybackBridge) via TVs
// que nao falam o protocolo Cast (ex.: LG webOS so com UPnP) implementam a mesma interface, entao
// o espelhamento (qual faixa tocar, play/pause) e escrito uma unica vez, independente do tipo de
// dispositivo conectado.
interface RemotePlaybackBridge {
    fun playUrl(url: String, mimeType: String, title: String, artist: String, startPositionMs: Long, autoplay: Boolean)
    fun pause()
    fun resume()
    fun setVolume(volume: Float)
    fun release()
}
