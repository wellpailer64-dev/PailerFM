package com.pailer.localtune.cast

import android.content.Context
import android.net.Uri
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

    fun registerSong(uri: Uri): String {
        val token = UUID.randomUUID().toString()
        val mimeType = context.contentResolver.getType(uri) ?: "audio/*"
        routes[token] = uri to mimeType
        return token
    }

    fun pathFor(token: String): String = "/media/$sessionToken/song/$token"

    override fun serve(session: IHTTPSession): Response {
        val prefix = "/media/$sessionToken/song/"
        val uri = session.uri
        if (!uri.startsWith(prefix)) {
            return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "")
        }
        val (songUri, mimeType) = routes[uri.removePrefix(prefix)]
            ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "")
        return serveSongUri(songUri, mimeType, session)
    }

    private fun serveSongUri(uri: Uri, mimeType: String, session: IHTTPSession): Response {
        val afd = runCatching { context.contentResolver.openAssetFileDescriptor(uri, "r") }.getOrNull()
            ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "")
        val totalLength = afd.length.takeIf { it >= 0 } ?: -1L

        val rangeHeader = session.headers["range"]
        if (rangeHeader == null || totalLength < 0) {
            val response = if (totalLength >= 0) {
                newFixedLengthResponse(Response.Status.OK, mimeType, afd.createInputStream(), totalLength)
            } else {
                newChunkedResponse(Response.Status.OK, mimeType, afd.createInputStream())
            }
            response.addHeader("Accept-Ranges", "bytes")
            return response
        }

        val range = parseRange(rangeHeader, totalLength)
        if (range == null) {
            afd.close()
            return newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "").apply {
                addHeader("Content-Range", "bytes */$totalLength")
            }
        }
        val (start, end) = range
        val stream = afd.createInputStream().apply { skipFully(start) }
        return newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mimeType, stream, end - start + 1).apply {
            addHeader("Accept-Ranges", "bytes")
            addHeader("Content-Range", "bytes $start-$end/$totalLength")
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
}
