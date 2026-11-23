package com.example.mesh.data.repository

import com.example.mesh.domain.models.Message

/**
 * Repository responsible for the known-message cache (to prevent loops)
 * and for managing the "Store & Forward" message queue.
 */
interface StoreAndForwardRepository {

    /**
     * Checks whether a message has recently passed through this node.
     */
    fun isMessageKnown(messageId: String): Boolean

    /**
     * Records the message ID in the local cache.
     */
    fun markMessageAsKnown(messageId: String)

    /**
     * Stores a message for later delivery, when
     * the next route node becomes available again.
     */
    fun storeMessageForLater(message: Message)

    /**
     * Retrieves and removes messages stored for a specific destination.
     * Called when a node (e.g., C) reappears on the network.
     */
    fun getAndRemoveMessagesForDestination(destinationId: String): List<Message>

    /**
     * Removes from storage messages that have exceeded their time TTL. CHAT-R13
     */
    fun clearExpiredMessages(currentTimeMillis: Long)
}
