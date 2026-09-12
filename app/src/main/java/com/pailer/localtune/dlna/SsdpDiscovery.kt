package com.pailer.localtune.dlna

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.pailer.localtune.cast.LocalWifiAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.net.DatagramPacket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.SocketTimeoutException
import java.net.URL

// TVs/receptores UPnP (ex.: LG webOS, que nao fala Google Cast - ver LocalTuneViewModel) se
// anunciam por SSDP (multicast UDP), nao mDNS. Descobre enviando um M-SEARCH e escutando as
// respostas por `timeoutMs`, depois busca o XML de descricao de cada dispositivo pra achar o nome
// e a URL de controle do servico AVTransport (o que realmente aceita comandos de tocar/pausar).
data class DlnaDevice(
    val friendlyName: String,
    val controlUrl: String,
    val usn: String,
)

object SsdpDiscovery {
    private const val TAG = "PailerDlna"
    private const val MULTICAST_ADDRESS = "239.255.255.250"
    private const val MULTICAST_PORT = 1900
    // "ssdp:all" (nao um ST especifico de AVTransport) porque nem todo dispositivo responde a
    // buscas por tipo de servico especifico (varias TVs so respondem a ssdp:all/rootdevice) -
    // filtra por AVTransport depois, na descricao do dispositivo (fetchDeviceDescription).
    private const val SEARCH_TARGET = "ssdp:all"

    suspend fun discover(context: Context, timeoutMs: Long = 6000L): List<DlnaDevice> = withContext(Dispatchers.IO) {
        val locations = linkedSetOf<String>()
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val multicastLock = wifiManager?.createMulticastLock("PailerFmSsdp")?.apply { setReferenceCounted(true) }
        // Sem vincular o socket a rede Wi-Fi explicitamente, o Android pode rotear o pacote pela
        // rede de dados moveis mesmo com o Wi-Fi conectado (celular com as duas ativas ao mesmo
        // tempo) - o M-SEARCH nunca chega na LAN. E sem ENTRAR no grupo multicast (nao basta so
        // mandar pra ele) o socket nao recebe respostas que a TV manda de volta via multicast em
        // vez de unicast (alguns fabricantes fogem do padrao) - foi exatamente essa a causa
        // encontrada ao testar em 12/09/2026: nenhuma excecao, nenhuma resposta, nos dois casos.
        val wifiNetwork = LocalWifiAddress.findWifiNetwork(context)
        val wifiInterface = LocalWifiAddress.findWifiNetworkInterface(context)
        if (wifiNetwork == null || wifiInterface == null) {
            Log.w(TAG, "Sem rede Wi-Fi ativa - desistindo da busca SSDP")
            return@withContext emptyList()
        }
        runCatching { multicastLock?.acquire() }
        val group = InetAddress.getByName(MULTICAST_ADDRESS)
        val groupSocketAddress = InetSocketAddress(group, MULTICAST_PORT)
        try {
            val socket = runCatching {
                MulticastSocket(MULTICAST_PORT).apply {
                    reuseAddress = true
                    wifiNetwork.bindSocket(this)
                    networkInterface = wifiInterface
                    joinGroup(groupSocketAddress, wifiInterface)
                }
            }.getOrNull()
            if (socket == null) {
                Log.w(TAG, "Nao consegui abrir o socket multicast pra busca SSDP")
                return@withContext emptyList()
            }
            socket.use {
                val request = buildSearchRequest()
                val requestBytes = request.toByteArray(Charsets.UTF_8)
                fun sendSearch() {
                    runCatching {
                        socket.send(DatagramPacket(requestBytes, requestBytes.size, group, MULTICAST_PORT))
                    }.onFailure { Log.w(TAG, "Falha ao enviar M-SEARCH", it) }
                }
                sendSearch()
                val deadline = System.currentTimeMillis() + timeoutMs
                // UDP nao garante entrega - um unico M-SEARCH pode se perder e a TV nunca chega a
                // responder dentro da janela (foi exatamente o que pareceu acontecer ao testar em
                // 12/09/2026: outro app achava a mesma TV sem nenhuma configuracao extra na TV, so
                // nosso app nao via resposta nenhuma dela). Reenviar algumas vezes durante a janela
                // e a pratica padrao de clientes SSDP pra compensar isso.
                var nextResendAt = System.currentTimeMillis() + 1200L
                val buffer = ByteArray(8192)
                while (System.currentTimeMillis() < deadline) {
                    if (System.currentTimeMillis() >= nextResendAt) {
                        sendSearch()
                        nextResendAt += 1200L
                    }
                    val remaining = (deadline - System.currentTimeMillis()).toInt()
                    if (remaining <= 0) break
                    socket.soTimeout = remaining.coerceAtMost(500)
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (timeout: SocketTimeoutException) {
                        continue
                    }
                    val response = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    Log.d(TAG, "SSDP resposta de ${packet.address?.hostAddress}: ${response.replace("\r\n", " | ")}")
                    val location = parseHeader(response, "LOCATION")
                    if (location == null) {
                        Log.d(TAG, "Sem header LOCATION nessa resposta, ignorando")
                        continue
                    }
                    // So coleta aqui (rapido, sem I/O) - buscar a descricao de cada dispositivo
                    // (fetchDeviceDescription, com I/O de rede) acontece DEPOIS do loop, nunca
                    // durante, senao uma LOCATION lenta/inalcancavel (ex.: endereco IPv6 que o
                    // celular nao roteia de verdade - aconteceu com a resposta do roteador ao
                    // testar em 12/09/2026) trava o recebimento dos pacotes seguintes por bem mais
                    // que o timeoutMs pretendido pra busca inteira.
                    locations += location
                }
                runCatching { socket.leaveGroup(groupSocketAddress, wifiInterface) }
            }
        } finally {
            runCatching { multicastLock?.release() }
        }
        // ST=ssdp:all faz um unico dispositivo mandar varias respostas (uma por servico/tipo,
        // USN diferente em cada) mas todas com a MESMA LOCATION - por isso `locations` ja e um
        // Set, cada URL de descricao so e buscada uma vez independente de quantas respostas
        // vieram dela.
        locations.mapNotNull { location ->
            runCatching { fetchDeviceDescription(location, location) }
                .onFailure { Log.w(TAG, "Falha ao buscar descricao de $location", it) }
                .getOrNull()
                ?.also { Log.d(TAG, "Dispositivo achado: ${it.friendlyName} -> ${it.controlUrl}") }
        }
    }

    private fun buildSearchRequest(): String =
        "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: $MULTICAST_ADDRESS:$MULTICAST_PORT\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "MX: 2\r\n" +
            "ST: $SEARCH_TARGET\r\n" +
            "\r\n"

    private fun parseHeader(response: String, name: String): String? =
        response.lineSequence()
            .firstOrNull { it.startsWith("$name:", ignoreCase = true) }
            ?.substringAfter(":")
            ?.trim()

    // Le o XML de descricao do dispositivo (LOCATION do cabecalho SSDP) pra achar o nome amigavel
    // e a controlURL do servico AVTransport (unica coisa que realmente importa - e pra la que vao
    // os comandos SetAVTransportURI/Play/Pause em DlnaPlaybackBridge).
    private fun fetchDeviceDescription(locationUrl: String, usn: String): DlnaDevice? {
        // Sem timeout explicito, uma LOCATION inalcancavel (ex.: endereco IPv6 que o celular nao
        // consegue rotear de verdade - aconteceu com a resposta do roteador ao testar em
        // 12/09/2026) trava aqui por muito mais que o timeoutMs do discover() inteiro, ja que essa
        // chamada e sincrona dentro do mesmo loop que recebe os pacotes seguintes.
        val connection = (URL(locationUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 2000
            readTimeout = 2000
        }
        val xmlBytes = connection.inputStream.use { it.readBytes() }
        val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
            setInput(ByteArrayInputStream(xmlBytes), null)
        }
        var friendlyName: String? = null
        var currentServiceType: String? = null
        var avTransportControlPath: String? = null
        var currentTag = ""
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> currentTag = parser.name.orEmpty()
                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim().orEmpty()
                    if (text.isNotEmpty()) {
                        when (currentTag) {
                            "friendlyName" -> if (friendlyName == null) friendlyName = text
                            "serviceType" -> currentServiceType = text
                            "controlURL" -> if (avTransportControlPath == null &&
                                currentServiceType?.contains("AVTransport") == true
                            ) {
                                avTransportControlPath = text
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        val controlPath = avTransportControlPath ?: run {
            Log.w(TAG, "Dispositivo sem servico AVTransport: $locationUrl")
            return null
        }
        val controlUrl = URL(URL(locationUrl), controlPath).toString()
        return DlnaDevice(friendlyName = friendlyName ?: "TV", controlUrl = controlUrl, usn = usn)
    }
}
