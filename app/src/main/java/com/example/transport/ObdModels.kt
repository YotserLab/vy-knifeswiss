package com.example.transport
import com.example.R
import com.example.Res

/**
 * OBD-II fault code (DTC — Diagnostic Trouble Code).
 */
data class ObdDtcCode(
    val code: String,
    val description: String,
    val isPending: Boolean = false,
    val isPermanent: Boolean = false
)

/**
 * OBD-II readiness monitors (I/M Readiness) - Mode 01 PID 01.
 * Indicates whether the vehicle completed the required emission self-tests.
 */
data class ReadinessMonitors(
    // Continuous Monitors (always monitored)
    val misfireReady: MonitorStatus = MonitorStatus.NOT_SUPPORTED,
    val fuelSystemReady: MonitorStatus = MonitorStatus.NOT_SUPPORTED,
    val componentsReady: MonitorStatus = MonitorStatus.NOT_SUPPORTED,
    // Non-continuous monitors
    val catalystReady: MonitorStatus = MonitorStatus.NOT_SUPPORTED,
    val heatedCatalystReady: MonitorStatus = MonitorStatus.NOT_SUPPORTED,
    val evapSystemReady: MonitorStatus = MonitorStatus.NOT_SUPPORTED,
    val secondaryAirReady: MonitorStatus = MonitorStatus.NOT_SUPPORTED,
    val acSystemReady: MonitorStatus = MonitorStatus.NOT_SUPPORTED,
    val oxygenSensorReady: MonitorStatus = MonitorStatus.NOT_SUPPORTED,
    val oxygenSensorHeaterReady: MonitorStatus = MonitorStatus.NOT_SUPPORTED,
    val egrSystemReady: MonitorStatus = MonitorStatus.NOT_SUPPORTED,
    val milOn: Boolean = false
) {
    val allReady: Boolean get() = listOf(
        misfireReady, fuelSystemReady, componentsReady,
        catalystReady, heatedCatalystReady, evapSystemReady,
        oxygenSensorReady, oxygenSensorHeaterReady, egrSystemReady
    ).none { it == MonitorStatus.NOT_READY }

    val readyCount: Int get() = listOf(
        misfireReady, fuelSystemReady, componentsReady,
        catalystReady, heatedCatalystReady, evapSystemReady,
        oxygenSensorReady, oxygenSensorHeaterReady, egrSystemReady
    ).count { it == MonitorStatus.READY }

    val totalMonitors: Int get() = listOf(
        misfireReady, fuelSystemReady, componentsReady,
        catalystReady, heatedCatalystReady, evapSystemReady,
        oxygenSensorReady, oxygenSensorHeaterReady, egrSystemReady
    ).count { it != MonitorStatus.NOT_SUPPORTED }
}

enum class MonitorStatus(val label: String) {
    READY(Res.get(R.string.pronto)),
    NOT_READY(Res.get(R.string.incompleto)),
    NOT_SUPPORTED(Res.get(R.string.n_d_2))
}

/**
 * Freeze Frame snapshot (Mode 02) — data captured at the moment of a DTC.
 */
data class FreezeFrame(
    val triggerDtc: String,
    val rpm: Int? = null,
    val speedKmh: Int? = null,
    val coolantTempC: Int? = null,
    val engineLoadPct: Float? = null,
    val throttlePct: Float? = null,
    val shortFuelTrimPct: Float? = null,
    val longFuelTrimPct: Float? = null,
    val mapKpa: Int? = null,
    val mafGS: Float? = null,
    val capturedAt: Long = System.currentTimeMillis()
)

/**
 * ECU test result (Mode 06).
 */
data class ObdMode06Result(
    val testId: String,
    val monitorId: String,
    val description: String,
    val value: Float,
    val minValue: Float,
    val maxValue: Float,
    val passed: Boolean
) {
    val progressFraction: Float
        get() = if (maxValue > minValue) ((value - minValue) / (maxValue - minValue)).coerceIn(0f, 1f) else 0f
}

/**
 * Trip session — aggregated data of a complete trip.
 */
data class ObdTripSession(
    val id: Long = 0L,
    val startTimeMs: Long = System.currentTimeMillis(),
    val endTimeMs: Long = 0L,
    val maxRpm: Int = 0,
    val maxSpeedKmh: Int = 0,
    val maxCoolantTempC: Int = 0,
    val avgEngineLoadPct: Float = 0f,
    val distanceKm: Float = 0f,
    val fuelConsumedL: Float = 0f,
    val maxHp: Float = 0f,
    val maxTorqueNm: Float = 0f,
    val deviceName: String = "",
    val dtcCount: Int = 0
) {
    val durationMs: Long get() = if (endTimeMs > 0) endTimeMs - startTimeMs else System.currentTimeMillis() - startTimeMs
    val durationFormatted: String get() {
        val s = durationMs / 1000
        return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    }
    val avgConsumptionL100: Float get() =
        if (distanceKm > 0.1f) (fuelConsumedL / distanceKm) * 100f else 0f
}


/**
 * Performance data — 0-X km/h timer and HP estimates.
 */
data class PerformanceResult(
    val type: PerformanceType,
    val durationMs: Long,
    val maxSpeedKmh: Int,
    val timestamp: Long = System.currentTimeMillis()
) {
    val formattedTime: String get() = Res.get(R.string.n_2f_s).format(durationMs / 1000.0)
}

enum class PerformanceType(val label: String) {
    ZERO_TO_100(Res.get(R.string.n_0100_km_h)),
    ZERO_TO_60(Res.get(R.string.n_060_km_h)),
    QUARTER_MILE(Res.get(R.string.n_1_4_milha_0402_m)),
    ZERO_TO_200(Res.get(R.string.n_0200_km_h))
}
