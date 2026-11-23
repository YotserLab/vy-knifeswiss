package com.example.transport.domain

/**
 * Generic interface for any transport medium (Wi-Fi Direct, Bluetooth, Internet).
 * The Mesh Layer uses this interface, agnostic to the underlying technology.
 */
interface TransportMedium {
    
    val mediumType: MediumType

    /**
     * Starts the discovery process for other nodes on the network using this medium.
     */
    fun startDiscovery()

    /**
     * Stops discovery to save battery.
     */
    fun stopDiscovery()

    /**
     * Connects to a specific discovered node.
     */
    fun connectToNode(nodeAddress: String): Boolean

    /**
     * Disconnects from a node.
     */
    fun disconnectFromNode(nodeAddress: String)

    /**
     * Sends byte-array data to the connected node.
     */
    fun sendData(nodeAddress: String, data: ByteArray): Boolean
}

enum class MediumType {
    WIFI_DIRECT,
    WIFI_LAN,
    BLUETOOTH,
    INTERNET
}
