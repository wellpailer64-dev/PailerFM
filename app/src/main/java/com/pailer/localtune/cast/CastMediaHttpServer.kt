package com.pailer.localtune.cast

import android.content.Context
import android.net.Uri
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// Serve pra TV (via Cast) os arquivos de musica locais do celular (content:// do MediaStore) que,
// de outra forma, o receptor do Cast nao teria como alcancar - ele so sabe carregar midia de uma
// URL http(s), nunca um content:// do proprio celular. So roda enquanto uma sessao de Cast estiver
// conectada (ver CastPlaybackBridge/LocalTuneViewModel), numa porta efemera na rede Wi-Fi local.
// O sessionToken na propria URL e so ofuscacao por sessao (nao autenticacao de verdade) - qualquer
// pedido fora de /media/{sessionToken}/... e rejeitado com 403.
class CastMediaHttpServer(
    private val context: Context,
    private val sessionToken: String,
) : NanoHTTPD("0.0.0.0", 0) {

    private val routes = ConcurrentHashMap<String, Pair<Uri, String>>()
    private val artworkRoutes = ConcurrentHashMap<String, Pair<Uri, String>>()

    fun registerSong(uri: Uri): String {
        val token = UUID.randomUUID().toString()
        val mimeType = context.contentResolver.getType(uri) ?: "audio/*"
        routes[token] = uri to mimeType
        Log.d(TAG, "HTTP media registrada token=$token mime=$mimeType uri=$uri")
        return token
    }

    fun registerArtwork(uri: Uri): String {
        val token = UUID.randomUUID().toString()
        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
        artworkRoutes[token] = uri to mimeType
        Log.d(TAG, "HTTP capa registrada token=$token mime=$mimeType uri=$uri")
        return token
    }

    fun pathFor(token: String): String = "/media/$sessionToken/song/$token"

    fun artworkPathFor(token: String): String = "/media/$sessionToken/artwork/$token"

    override fun serve(session: IHTTPSession): Response {
        val songPrefix = "/media/$sessionToken/song/"
        val artworkPrefix = "/media/$sessionToken/artwork/"
        val uri = session.uri
        Log.d(TAG, "HTTP pedido ${session.method} $uri range=${session.headers["range"].orEmpty()}")
        return when {
            uri.startsWith(songPrefix) -> {
                val (songUri, mimeType) = routes[uri.removePrefix(songPrefix)]
                    ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "").also {
                        Log.w(TAG, "HTTP token de musica nao encontrado: $uri")
                    }
                serveUri(songUri, mimeType, session, isAudio = true)
            }
            uri.startsWith(artworkPrefix) -> {
                val (artworkUri, mimeType) = artworkRoutes[uri.removePrefix(artworkPrefix)]
                    ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "").also {
                        Log.w(TAG, "HTTP token de capa nao encontrado: $uri")
                    }
                serveUri(artworkUri, mimeType, session, isAudio = false)
            }
            else -> {
                Log.w(TAG, "HTTP rejeitado fora da sessao: $uri")
                newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "")
            }
        }
    }

    private fun serveUri(uri: Uri, mimeType: String, session: IHTTPSession, isAudio: Boolean): Response {
        val afd = runCatching { context.contentResolver.openAssetFileDescriptor(uri, "r") }.getOrNull()
            ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "").also {
                Log.w(TAG, "HTTP nao abriu midia local: $uri")
            }
        val totalLength = afd.length.takeIf { it >= 0 } ?: -1L

        val rangeHeader = session.headers["range"]
        if (rangeHeader == null || totalLength < 0) {
            Log.d(TAG, "HTTP servindo midia inteira mime=$mimeType bytes=$totalLength")
            val response = if (totalLength >= 0) {
                newFixedLengthResponse(Response.Status.OK, mimeType, afd.createInputStream(), totalLength)
            } else {
                newChunkedResponse(Response.Status.OK, mimeType, afd.createInputStream())
            }
            if (isAudio) response.addAudioHeaders()
            return response
        }

        val range = parseRange(rangeHeader, totalLength)
        if (range == null) {
            afd.close()
            Log.w(TAG, "HTTP range invalido: $rangeHeader bytes=$totalLength")
            return newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "").apply {
                addHeader("Content-Range", "bytes */$totalLength")
            }
        }
        val (start, end) = range
        Log.d(TAG, "HTTP servindo range $start-$end/$totalLength mime=$mimeType")
        val stream = afd.createInputStream().apply { skipFully(start) }
        return newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mimeType, stream, end - start + 1).apply {
            addHeader("Content-Range", "bytes $start-$end/$totalLength")
            if (isAudio) addAudioHeaders()
        }
    }

    // "bytes=START-END" (END opcional) - so o primeiro intervalo, suficiente pro Cast/ExoPlayer
    // (nunca manda mais de um intervalo por pedido nesse uso).
    private fun parseRange(header: String, totalLength: Long): Pair<Long, Long>? {
        val spec = header.removePrefix("bytes=").substringBefore(",")
        val parts = spec.split("-")
        if (parts.size != 2) return null
        val start = parts[0].toLongOrNull() ?: 0L
        if (start !in 0 until totalLength) return null
        val end = parts[1].toLongOrNull()?.coerceAtMost(totalLength - 1) ?: (totalLength - 1)
        if (end < start) return null
        return start to end
    }

    private fun InputStream.skipFully(byteCount: Long) {
        var remaining = byteCount
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped <= 0) break
            remaining -= skipped
        }
    }

    private fun Response.addAudioHeaders() {
        addHeader("Accept-Ranges", "bytes")
        addHeader("transferMode.dlna.org", "Streaming")
        addHeader("contentFeatures.dlna.org", "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000")
        addHeader("Connection", "close")
    }

    private companion object {
        const val TAG = "PailerCastHttp"
    }
}
