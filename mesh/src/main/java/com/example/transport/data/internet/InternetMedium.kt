package com.example.transport.data.internet

import com.example.transport.domain.MediumType
import com.example.transport.domain.TransportMedium

/**
 * Implementação baseada em Internet para atuar como ponte ou otimizar a conexão
 * quando os nós estão geograficamente distantes mas conectados à web.
 */
class InternetMedium : TransportMedium {
    
    override val mediumType: MediumType = MediumType.INTERNET

    override fun startDiscovery() {
        // Ex: conecta a um Tracker Server (servidor de descoberta leve) 
        // ou usa DHT (Distributed Hash Table) na web para encontrar outros nós da mesma malha.
    }

    override fun stopDiscovery() {
        // Ex: desconecta do Tracker
    }

    override fun connectToNode(nodeAddress: String): Boolean {
        // Ex: Estabelece WebRTC ou túnel TCP direto / STUN / TURN
        return true
    }

    override fun disconnectFromNode(nodeAddress: String) {
        // Fecha o túnel
    }

    override fun sendData(nodeAddress: String, data: ByteArray): Boolean {
        // Envia através da internet criptografado de ponta a ponta
        return true
    }
}
