package com.example.transport.data.wifi

import com.example.transport.domain.MediumType
import com.example.transport.domain.TransportMedium

/**
 * Wi-Fi Direct (P2P) implementation for internet-less networks.
 * Interacts with Android's WifiP2pManager.
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
        // E.g., builds WifiP2pConfig and calls wifiP2pManager.connect(...)
        return true
    }

    override fun disconnectFromNode(nodeAddress: String) {
        // E.g., wifiP2pManager.cancelConnect(...) or removeGroup()
    }

    override fun sendData(nodeAddress: String, data: ByteArray): Boolean {
        // Once connected via Wi-Fi Direct, a group is formed and an Owner is elected.
        // Data is usually sent via Sockets (TCP or UDP) created
        // using the IP addresses (GroupOwnerAddress).
        return true
    }
}
