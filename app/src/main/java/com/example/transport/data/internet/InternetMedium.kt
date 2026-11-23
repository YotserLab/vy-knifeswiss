package com.example.transport.data.internet

import com.example.transport.domain.MediumType
import com.example.transport.domain.TransportMedium

/**
 * Internet-based implementation acting as a bridge or optimizing the connection
 * when nodes are geographically distant but connected to the web.
 */
class InternetMedium : TransportMedium {
    
    override val mediumType: MediumType = MediumType.INTERNET

    override fun startDiscovery() {
        // E.g., connects to a Tracker Server (lightweight discovery server) 
        // or uses DHT (Distributed Hash Table) on the web to find other nodes in the same mesh.
    }

    override fun stopDiscovery() {
        // E.g., disconnects from the Tracker
    }

    override fun connectToNode(nodeAddress: String): Boolean {
        // E.g., establishes WebRTC or direct TCP tunnel / STUN / TURN
        return true
    }

    override fun disconnectFromNode(nodeAddress: String) {
        // Closes the tunnel
    }

    override fun sendData(nodeAddress: String, data: ByteArray): Boolean {
        // Sends over the internet, end-to-end encrypted
        return true
    }
}
