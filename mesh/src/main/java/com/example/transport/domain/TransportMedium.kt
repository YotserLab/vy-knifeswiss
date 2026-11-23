package com.example.transport.domain

/**
 * Interface genérica para qualquer meio de transporte (Wi-Fi Direct, Bluetooth, Internet).
 * A Camada Mesh usará esta interface, sendo agnóstica em relação à tecnologia real.
 */
interface TransportMedium {
    
    val mediumType: MediumType

    /**
     * Inicia o processo de descoberta de outros nós na rede usando este meio.
     */
    fun startDiscovery()

    /**
     * Para a descoberta para economizar bateria.
     */
    fun stopDiscovery()

    /**
     * Conecta a um nó específico descoberto.
     */
    fun connectToNode(nodeAddress: String): Boolean

    /**
     * Desconecta de um nó.
     */
    fun disconnectFromNode(nodeAddress: String)

    /**
     * Envia dados em array de bytes para o nó conectado.
     */
    fun sendData(nodeAddress: String, data: ByteArray): Boolean
}

enum class MediumType {
    WIFI_DIRECT,
    WIFI_LAN,
    BLUETOOTH,
    INTERNET
}
