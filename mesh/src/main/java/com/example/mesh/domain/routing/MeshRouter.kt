package com.example.mesh.domain.routing

import com.example.mesh.domain.models.Message
import com.example.mesh.domain.models.Node
import com.example.mesh.data.repository.StoreAndForwardRepository

/**
 * Responsável por decidir o que fazer com uma mensagem recebida:
 * Entregar localmente, encaminhar para vizinhos, ou armazenar (Store & Forward).
 */
class MeshRouter(
    private val localNodeId: String,
    private val storeAndForwardRepository: StoreAndForwardRepository
) {
    // Mapa de nós vizinhos conhecidos diretamente
    private val routingTable = mutableMapOf<String, Node>()

    fun addOrUpdateNeighbor(node: Node) {
        routingTable[node.id] = node
    }

    fun removeNeighbor(nodeId: String) {
        routingTable.remove(nodeId)
    }

    /**
     * Processa uma mensagem recebida da rede.
     */
    fun onMessageReceived(message: Message, currentTimeMillis: Long) {
        // 1. Verificar se a mensagem expirou (TTL Tempo ou Saltos)
        if (message.isExpired(currentTimeMillis)) {
            return // Mensagem descartada
        }

        // 2. Verificar cache (evita loops)
        if (storeAndForwardRepository.isMessageKnown(message.messageId)) {
            return // Ignora, já recebemos esta mensagem antes
        }
        storeAndForwardRepository.markMessageAsKnown(message.messageId)

        // 3. Destino é o nosso nó?
        if (message.destinationId == localNodeId) {
            deliverToApplication(message)
            return
        }

        // 4. Encaminhar para o destino, se for conhecido
        // Decrementa o TTL de saltos antes de encaminhar
        message.ttlHopsRemaining--

        if (message.ttlHopsRemaining <= 0) {
            return // Morreu neste salto
        }

        val nextHopNode = findBestRouteTo(message.destinationId)
        if (nextHopNode != null) {
            forwardMessage(message, nextHopNode)
        } else {
            // O próximo nó está indisponível ou desconhecido. Executa Store & Forward.
            storeAndForwardRepository.storeMessageForLater(message)
        }
    }

    private fun deliverToApplication(message: Message) {
        // Aqui enviaria a mensagem para a camada de apresentação do app
        // Ex: EventBus, Flow, LiveData...
    }

    private fun findBestRouteTo(destinationId: String): Node? {
        // Na topologia "o dispositivo conhece apenas seus vizinhos",
        // tentamos enviar diretamente se ele for vizinho, ou enviamos para um coordenador.
        return routingTable[destinationId] // Simplificado para fins de exemplo
    }

    private fun forwardMessage(message: Message, nextHop: Node) {
        // Envia para a camada de Transporte entregar ao nextHop
    }
}
