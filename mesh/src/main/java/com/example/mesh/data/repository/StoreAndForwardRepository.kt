package com.example.mesh.data.repository

import com.example.mesh.domain.models.Message

/**
 * Repositório responsável por manter o cache de mensagens conhecidas (para evitar loops)
 * e gerenciar a fila de mensagens do recurso "Store & Forward".
 */
interface StoreAndForwardRepository {

    /**
     * Verifica se uma mensagem já passou por este nó recentemente.
     */
    fun isMessageKnown(messageId: String): Boolean

    /**
     * Registra o ID da mensagem no cache local.
     */
    fun markMessageAsKnown(messageId: String)

    /**
     * Armazena uma mensagem para ser entregue mais tarde, quando
     * o próximo nó da rota estiver disponível novamente.
     */
    fun storeMessageForLater(message: Message)

    /**
     * Recupera e remove as mensagens armazenadas para um destino específico.
     * Chamado quando um nó (ex: C) reaparece na rede.
     */
    fun getAndRemoveMessagesForDestination(destinationId: String): List<Message>

    /**
     * Limpa do armazenamento as mensagens que já ultrapassaram seu TTL de tempo.
     */
    fun clearExpiredMessages(currentTimeMillis: Long)
}
