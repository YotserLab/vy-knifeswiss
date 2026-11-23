package com.example.mesh.domain.models

/**
 * Estrutura de uma mensagem na malha.
 * Todas as mensagens possuem TTL por tempo e por saltos (hops) para evitar saturação.
 */
data class Message(
    val messageId: String,
    val sourceId: String,
    val destinationId: String,
    val timestamp: Long,
    val digitalSignature: String,
    val encryptedPayload: ByteArray, // Criptografia Ponta a Ponta (E2EE)
    var ttlExpirationTime: Long,     // Tempo limite em que a mensagem é descartada
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
