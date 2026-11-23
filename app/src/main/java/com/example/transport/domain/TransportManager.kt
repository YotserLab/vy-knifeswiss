package com.example.transport.domain

/**
 * Manager that orchestrates the different TransportMedium implementations.
 * It decides which medium to use based on availability (e.g., tries Internet, falls back to Wi-Fi Direct).
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
     * Sends data using the best available transport medium.
     */
    fun sendData(nodeAddress: String, data: ByteArray): Boolean {
        // Simplified fallback example:
        // Tries sending via Internet (if it is a Gateway and has Internet).
        // On failure, tries Wi-Fi LAN. On failure, Wi-Fi Direct.
        
        // In a real system, the Mesh Layer routing table would tell which Medium 
        // the neighbor node is using, to avoid "guessing".
        
        for (medium in mediums) {
            val success = medium.sendData(nodeAddress, data)
            if (success) {
                return true
            }
        }
        return false
    }
}
