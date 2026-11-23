package com.example.transport.domain

/**
 * Gerenciador que orquestra as diferentes implementações de TransportMedium.
 * Ele decide qual meio usar com base na disponibilidade (ex: tenta Internet, falha para Wi-Fi Direct).
 */
class TransportManager(
    private val mediums: List<TransportMedium>
) {

    fun startAllDiscoveries() {
        mediums.forEach { it.startDiscovery() }
    }

    fun stopAllDiscoveries() {
        mediums.forEach { it.stopDiscovery() }
    }

    /**
     * Envia os dados utilizando o melhor meio de transporte disponível.
     */
    fun sendData(nodeAddress: String, data: ByteArray): Boolean {
        // Exemplo simplificado de fallback:
        // Tenta enviar via Internet (se for um Gateway e tiver Internet).
        // Se falhar, tenta via Wi-Fi LAN. Se falhar, Wi-Fi Direct.
        
        // Em um sistema real, a tabela de roteamento da Mesh Layer informaria qual Medium 
        // o nó vizinho está usando, para evitar "chutes".
        
        for (medium in mediums) {
            val success = medium.sendData(nodeAddress, data)
            if (success) {
                return true
            }
        }
        return false
    }
}
