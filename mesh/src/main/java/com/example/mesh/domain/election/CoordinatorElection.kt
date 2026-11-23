package com.example.mesh.domain.election

import com.example.mesh.domain.models.Node
import com.example.mesh.domain.models.NodeRole

/**
 * Algoritmo distribuído de eleição para definir o papel de cada nó na rede.
 */
class CoordinatorElection(private val localNode: Node) {

    private val neighbors = mutableListOf<Node>()

    fun updateNeighbors(newNeighbors: List<Node>) {
        neighbors.clear()
        neighbors.addAll(newNeighbors)
        evaluateRole()
    }

    /**
     * Avalia as métricas locais comparadas às dos vizinhos
     * para decidir se este nó deve ser promovido a Coordenador.
     */
    private fun evaluateRole() {
        // Se o usuário forçou um papel específico, não alteramos (simplificação)
        // Se já tem muitos coordenadores na região, também evitamos.

        val myScore = localNode.calculateCoordinatorScore()
        
        var highestScoreNode = localNode
        var highestScore = myScore

        for (neighbor in neighbors) {
            val neighborScore = neighbor.calculateCoordinatorScore()
            if (neighborScore > highestScore) {
                highestScore = neighborScore
                highestScoreNode = neighbor
            }
        }

        // Se este nó tem o maior score da sua vizinhança, ele vira Coordenador
        if (highestScoreNode.id == localNode.id && myScore > 50) {
            localNode.role = NodeRole.COORDINATOR
        } else {
            // Verifica se precisamos ser Coordenador Reserva
            // (ex: o segundo melhor score)
            localNode.role = NodeRole.COMMON
        }
    }
}
