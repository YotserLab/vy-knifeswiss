package com.example.transport.data.wifi

import com.example.transport.domain.MediumType
import com.example.transport.domain.TransportMedium

/**
 * Implementação baseada em Wi-Fi Direct (P2P) para redes sem internet.
 * Interage com o WifiP2pManager do Android.
 */
class WifiDirectMedium : TransportMedium {
    
    override val mediumType: MediumType = MediumType.WIFI_DIRECT

    override fun startDiscovery() {
        // Ex: wifiP2pManager.discoverPeers(...)
    }

    override fun stopDiscovery() {
        // Ex: wifiP2pManager.stopPeerDiscovery(...)
    }

    override fun connectToNode(nodeAddress: String): Boolean {
        // Ex: constrói WifiP2pConfig e chama wifiP2pManager.connect(...)
        return true
    }

    override fun disconnectFromNode(nodeAddress: String) {
        // Ex: wifiP2pManager.cancelConnect(...) ou removeGroup()
    }

    override fun sendData(nodeAddress: String, data: ByteArray): Boolean {
        // Uma vez conectado via Wi-Fi Direct, um grupo é formado e um Owner é eleito.
        // O envio de dados geralmente ocorre via Sockets (TCP ou UDP) criados
        // usando os endereços IP (GroupOwnerAddress).
        return true
    }
}
