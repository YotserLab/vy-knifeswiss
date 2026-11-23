package com.example.mesh.domain.election

import com.example.mesh.domain.models.Node
import com.example.mesh.domain.models.NodeRole

/**
 * Distributed election algorithm to define each node's role in the network.
 */
class CoordinatorElection(private val localNode: Node) { // CHAT-R14

    private val neighbors = mutableListOf<Node>()

    fun updateNeighbors(newNeighbors: List<Node>) {
        neighbors.clear()
        neighbors.addAll(newNeighbors)
        evaluateRole()
    }

    /**
     * Evaluates local metrics compared to the neighbors'
     * to decide whether this node should be promoted to Coordinator.
     */
    private fun evaluateRole() {
        // If the user forced a specific role, we do not change it (simplification)
        // We also avoid it if there are already many coordinators nearby.

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

        // If this node has the highest score in its neighborhood, it becomes Coordinator
        if (highestScoreNode.id == localNode.id && myScore > 50) {
            localNode.role = NodeRole.COORDINATOR
        } else {
            // Checks whether we should be Backup Coordinator
            // (e.g., the second-best score)
            localNode.role = NodeRole.COMMON
        }
    }
}
