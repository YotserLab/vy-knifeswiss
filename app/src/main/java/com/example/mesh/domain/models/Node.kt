package com.example.mesh.domain.models

/**
 * Roles a node can assume in the Mesh network.
 */
enum class NodeRole {
    COMMON,
    COORDINATOR,
    BACKUP_COORDINATOR,
    GATEWAY
}

/**
 * Represents a device in the mesh and its current stats.
 * These stats are used for the automatic Coordinator election.
 */
data class Node(
    val id: String,
    var role: NodeRole = NodeRole.COMMON,
    var cpuPerformance: Int, // ex: score based on cores/clock
    var ramAvailableMb: Long,
    var batteryLevelPct: Int, // 0 a 100
    var isCharging: Boolean,
    var wifiQualitySignalDb: Int,
    var activeConnectionsCount: Int,
    var hasInternetAccess: Boolean,
    var uptimeSeconds: Long
) {
    /**
     * Calculates a 'score' to decide whether this node is a good coordinator candidate.
     */
    fun calculateCoordinatorScore(): Int {
        var score = 0
        if (isCharging) score += 50
        if (hasInternetAccess) score += 30
        
        score += batteryLevelPct / 2
        score += (wifiQualitySignalDb + 100) // Assuming -100dBm as 0
        
        // Penalty for too many connections (for load balancing)
        score -= (activeConnectionsCount * 5)
        
        return score
    }
}
