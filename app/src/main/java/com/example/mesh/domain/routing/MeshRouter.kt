package com.example.mesh.domain.routing

import com.example.mesh.domain.models.Message
import com.example.mesh.domain.models.Node
import com.example.mesh.data.repository.StoreAndForwardRepository

/**
 * Responsible for deciding what to do with a received message:
 * Deliver locally, forward to neighbors, or store (Store & Forward).
 */
class MeshRouter(
    private val localNodeId: String,
    private val storeAndForwardRepository: StoreAndForwardRepository
) {
    // Map of directly known neighbor nodes
    private val routingTable = mutableMapOf<String, Node>()

    fun addOrUpdateNeighbor(node: Node) {
        routingTable[node.id] = node
    }

    fun removeNeighbor(nodeId: String) {
        routingTable.remove(nodeId)
    }

    /**
     * Processes a message received from the network.
     */
    fun onMessageReceived(message: Message, currentTimeMillis: Long) {
        // 1. Check whether the message expired (Time or Hop TTL)
        if (message.isExpired(currentTimeMillis)) { // CHAT-R12
            return // Mensagem descartada
        }

        // 2. Check cache (prevents loops)
        if (storeAndForwardRepository.isMessageKnown(message.messageId)) {
            return // Ignore, we already received this message before
        }
        storeAndForwardRepository.markMessageAsKnown(message.messageId)

        // 3. Is the destination our node?
        if (message.destinationId == localNodeId) {
            deliverToApplication(message)
            return
        }

        // 4. Forward to the destination, if known
        // Decrements the hop TTL before forwarding
        message.ttlHopsRemaining-- // CHAT-R11

        if (message.ttlHopsRemaining <= 0) {
            return // Morreu neste salto
        }

        val nextHopNode = findBestRouteTo(message.destinationId)
        if (nextHopNode != null) {
            forwardMessage(message, nextHopNode)
        } else {
            // The next node is unavailable or unknown. Performs Store & Forward.
            storeAndForwardRepository.storeMessageForLater(message)
        }
    }

    private fun deliverToApplication(message: Message) {
        // This would hand the message to the app's presentation layer
        // Ex: EventBus, Flow, LiveData...
    }

    private fun findBestRouteTo(destinationId: String): Node? {
        // In the "device only knows its neighbors" topology,
        // we try direct delivery if it is a neighbor, or send to a coordinator.
        return routingTable[destinationId] // Simplified for example purposes
    }

    private fun forwardMessage(message: Message, nextHop: Node) {
        // Hands off to the Transport layer to deliver to nextHop
    }
}
