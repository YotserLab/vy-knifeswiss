package com.example.mesh.domain.models

/**
 * Papéis que um nó pode assumir na rede Mesh.
 */
enum class NodeRole {
    COMMON,
    COORDINATOR,
    BACKUP_COORDINATOR,
    GATEWAY
}

/**
 * Representa um dispositivo na malha e suas estatísticas atuais.
 * Essas estatísticas são usadas para a eleição automática de Coordenadores.
 */
data class Node(
    val id: String,
    var role: NodeRole = NodeRole.COMMON,
    var cpuPerformance: Int, // ex: pontuação baseada em cores/clock
    var ramAvailableMb: Long,
    var batteryLevelPct: Int, // 0 a 100
    var isCharging: Boolean,
    var wifiQualitySignalDb: Int,
    var activeConnectionsCount: Int,
    var hasInternetAccess: Boolean,
    var uptimeSeconds: Long
) {
    /**
     * Calcula um 'score' para decidir se este nó é um bom candidato a coordenador.
     */
    fun calculateCoordinatorScore(): Int {
        var score = 0
        if (isCharging) score += 50
        if (hasInternetAccess) score += 30
        
        score += batteryLevelPct / 2
        score += (wifiQualitySignalDb + 100) // Assumindo -100dBm como 0
        
        // Penalidade por muitas conexões (para balanceamento)
        score -= (activeConnectionsCount * 5)
        
        return score
    }
}
