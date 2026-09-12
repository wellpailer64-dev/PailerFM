package com.pailer.localtune.cast

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

// O receptor de Cast/DLNA (a TV) so consegue ser alcancado NA MESMA rede Wi-Fi - entao o IP (e
// qualquer socket usado pra descoberta, ver SsdpDiscovery) tem que vir/ser vinculado a interface
// Wi-Fi de verdade, nunca dados moveis/VPN. Sem Android vincular o socket explicitamente a rede
// Wi-Fi, o sistema pode rotear pacotes de multicast/broadcast pela rede de dados movel mesmo com
// o Wi-Fi conectado (celular com as duas ativas ao mesmo tempo) - o pacote simplesmente nunca
// chega na LAN. Sem uma rede Wi-Fi ativa, retorna null e quem chamar deve desistir em vez de
// arriscar montar uma URL/socket inutil.
object LocalWifiAddress {
    fun find(context: Context): InetAddress? {
        val network = findWifiNetwork(context) ?: return null
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null
        val linkProperties = connectivityManager.getLinkProperties(network) ?: return null
        return linkProperties.linkAddresses
            .map { it.address }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
    }

    fun findWifiNetwork(context: Context): Network? {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null
        val network = connectivityManager.activeNetwork ?: return null
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return null
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
        return network
    }

    // Pra MulticastSocket.joinGroup(SocketAddress, NetworkInterface) - precisa da interface de
    // rede de verdade (ex.: "wlan0"), nao so do objeto Network, pra entrar no grupo multicast na
    // placa Wi-Fi certa (ver SsdpDiscovery).
    fun findWifiNetworkInterface(context: Context): NetworkInterface? {
        val network = findWifiNetwork(context) ?: return null
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null
        val interfaceName = connectivityManager.getLinkProperties(network)?.interfaceName ?: return null
        return runCatching { NetworkInterface.getByName(interfaceName) }.getOrNull()
    }
}
