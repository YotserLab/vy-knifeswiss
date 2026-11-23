package com.example.mesh.domain.models

/**
 * Structure of a mesh message.
 * All messages have time and hop TTLs to prevent saturation.
 */
data class Message(
    val messageId: String,
    val sourceId: String,
    val destinationId: String,
    val timestamp: Long,
    val digitalSignature: String,
    val encryptedPayload: ByteArray, // Criptografia Ponta a Ponta (E2EE)
    var ttlExpirationTime: Long,     // Time limit after which the message is discarded
    var ttlHopsRemaining: Int        // Saltos restantes (ex: 20 -> 19 -> 18)
) {
    fun isExpired(currentTimeMillis: Long): Boolean {
        return currentTimeMillis > ttlExpirationTime || ttlHopsRemaining <= 0
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Message
        return messageId == other.messageId
    }

    override fun hashCode(): Int {
        return messageId.hashCode()
    }
}
