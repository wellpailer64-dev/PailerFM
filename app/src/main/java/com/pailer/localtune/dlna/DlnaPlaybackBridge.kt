package com.pailer.localtune.dlna

import android.util.Log
import com.pailer.localtune.player.RemotePlaybackBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

// Controla um renderizador UPnP (ex.: TV LG webOS - ver SsdpDiscovery) via comandos SOAP no
// servico AVTransport. Mesma interface de CastPlaybackBridge (RemotePlaybackBridge) - quem chama
// (mirrorToRemoteIfNeeded em LocalTuneViewModel) nao sabe nem precisa saber se esta falando com
// Cast ou DLNA.
class DlnaPlaybackBridge(private val controlUrl: String) : RemotePlaybackBridge {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun playUrl(url: String, mimeType: String, title: String, artist: String, startPositionMs: Long, autoplay: Boolean) {
        scope.launch {
            sendAction(
                "SetAVTransportURI",
                linkedMapOf(
                    "InstanceID" to "0",
                    "CurrentURI" to url,
                    "CurrentURIMetaData" to buildDidlLite(url, mimeType, title, artist),
                ),
            )
            if (autoplay) {
                sendAction("Play", linkedMapOf("InstanceID" to "0", "Speed" to "1"))
            }
        }
    }

    override fun pause() {
        scope.launch { sendAction("Pause", linkedMapOf("InstanceID" to "0")) }
    }

    override fun resume() {
        scope.launch { sendAction("Play", linkedMapOf("InstanceID" to "0", "Speed" to "1")) }
    }

    // RenderingControl e um servico UPnP separado do AVTransport - fora do escopo desta primeira
    // versao (o controle remoto da propria TV ja ajusta volume de qualquer jeito). No-op de proposito.
    override fun setVolume(volume: Float) = Unit

    override fun release() {
        scope.cancel()
    }

    private fun sendAction(action: String, params: Map<String, String>) {
        runCatching {
            val body = buildSoapEnvelope(action, params)
            val connection = (URL(controlUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
                setRequestProperty("SOAPACTION", "\"urn:schemas-upnp-org:service:AVTransport:1#$action\"")
            }
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) {
                Log.w(TAG, "DLNA $action falhou: HTTP $code")
            }
            connection.disconnect()
        }.onFailure { Log.w(TAG, "DLNA $action falhou", it) }
    }

    private fun buildSoapEnvelope(action: String, params: Map<String, String>): String {
        val paramsXml = params.entries.joinToString("") { (key, value) -> "<$key>${xmlEscape(value)}</$key>" }
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
            "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
            "<s:Body><u:$action xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">" +
            paramsXml +
            "</u:$action></s:Body></s:Envelope>"
    }

    // DIDL-Lite eh o formato de metadados que UPnP espera em CurrentURIMetaData - varias TVs
    // aceitam string vazia, mas mandar titulo/artista de verdade evita telas de "sem informacao"
    // na TV. Retorna SEM escapar pra fora (sendAction/buildSoapEnvelope escapa uma unica vez ao
    // encaixar como texto de <CurrentURIMetaData>) - so os campos internos (titulo/artista/url)
    // sao escapados aqui, que e o nivel certo pra eles dentro do proprio DIDL-Lite.
    private fun buildDidlLite(url: String, mimeType: String, title: String, artist: String): String =
        "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" " +
            "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" " +
            "xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">" +
            "<item id=\"1\" parentID=\"0\" restricted=\"1\">" +
            "<dc:title>${xmlEscape(title)}</dc:title>" +
            "<upnp:artist>${xmlEscape(artist)}</upnp:artist>" +
            "<upnp:class>object.item.audioItem.musicTrack</upnp:class>" +
            "<res protocolInfo=\"http-get:*:${xmlEscape(mimeType)}:*\">${xmlEscape(url)}</res>" +
            "</item></DIDL-Lite>"

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private companion object {
        const val TAG = "PailerDlna"
    }
}
