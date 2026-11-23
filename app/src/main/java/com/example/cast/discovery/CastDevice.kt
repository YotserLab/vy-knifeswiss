package com.example.cast.discovery

enum class CastDeviceType(val priority: Int) {
    DIAL(0), ROKU(1), DLNA(2), CHROMECAST(3)
}

data class CastDevice(
    val id: String,
    val name: String,
    val type: CastDeviceType,
    val ip: String,
    val port: Int = 0,
    val manufacturer: String = "",
    val modelName: String = "",
    val location: String = "",
    val avTransportControlUrl: String = "",
    val renderingControlUrl: String = "",
    // Google Cast device UUID (hex, no dashes) from the mDNS TXT `id` field
    // or embedded in the service name — matches GMS CastDevice.deviceId.
    val deviceUuid: String = "",
    // Google Cast Groups advertise at the leader's IP and only play audio —
    // the merger must prefer the physical device over them at the same IP.
    val isGroup: Boolean = false
) {
    val canCastLocalMedia: Boolean
        get() = when (type) {
            CastDeviceType.DLNA -> avTransportControlUrl.isNotEmpty()
            CastDeviceType.ROKU, CastDeviceType.CHROMECAST -> true
            CastDeviceType.DIAL -> false
        }
}

object CastDeviceMerger {
    fun merge(existing: List<CastDevice>, incoming: List<CastDevice>): List<CastDevice> {
        val byIp = (existing + incoming).groupBy { it.ip }
        return byIp.map { (_, group) ->
            group.filter { it.canCastLocalMedia }
                .minWithOrNull(
                    // prefer the physical target over a Cast Group at the
                    // same IP, then the highest-priority protocol
                    compareBy<CastDevice> { it.isGroup }.thenByDescending { it.type.priority }
                )
                ?: group.maxBy { it.type.priority }
        }.sortedBy { it.name.lowercase() }
    }
}
