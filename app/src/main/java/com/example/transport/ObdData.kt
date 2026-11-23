package com.example.transport
import com.example.R
import com.example.Res

/**
 * Expanded OBD-II data — covers 30+ PIDs from the SAE J1979 Mode 01 standard.
 * All fields are nullable to distinguish "not read" from "zero".
 */
data class ObdData(
    // ── Engine ────────────────────────────────────────────────────────────────
    val rpm: Int? = null,
    val speedKmh: Int? = null,
    val engineLoadPct: Float? = null,
    val throttlePct: Float? = null,
    val ignitionAdvanceDeg: Float? = null,
    val runtimeSec: Int? = null,
    val milOn: Boolean = false,

    // ── Temperatures ──────────────────────────────────────────────────────────
    val coolantTempC: Int? = null,
    val intakeTempC: Int? = null,
    val ambientTempC: Int? = null,
    val oilTempC: Int? = null,
    val dpfTempC: Int? = null,


    // ── Fuel / Air ────────────────────────────────────────────────────────────
    val mafGS: Float? = null,
    val shortFuelTrimB1Pct: Float? = null,
    val longFuelTrimB1Pct: Float? = null,
    val shortFuelTrimB2Pct: Float? = null,
    val longFuelTrimB2Pct: Float? = null,
    val fuelLevelPct: Float? = null,
    val fuelPressureKpa: Int? = null,
    val fuelFlowRateLh: Float? = null,
    val commandedEquivRatio: Float? = null,
    val commandedEgrPct: Float? = null,
    val egrErrorPct: Float? = null,
    val ethanolPct: Float? = null,

    // ── Pressures ─────────────────────────────────────────────────────────────
    val mapKpa: Int? = null,
    val baroPressureKpa: Int? = null,
    val evapVaporPressurePa: Float? = null,
    val evapAbsPressureKpa: Float? = null,

    // ── O2 Sensors ────────────────────────────────────────────────────────────
    val o2B1S1Voltage: Float? = null,
    val o2B1S2Voltage: Float? = null,

    // ── Electrical ────────────────────────────────────────────────────────────
    val controlModuleVoltage: Float? = null,
    val obdPortVoltage: Float? = null,
    val hybridBatteryChargePct: Float? = null,
    val hybridBatteryVoltage: Float? = null,

    // ── Diagnostics ───────────────────────────────────────────────────────────
    val distanceMilKm: Int? = null,
    val timeMilMin: Int? = null
) {
    // ── Formatters ────────────────────────────────────────────────────────────

    fun rpmFormatted() = rpm?.let { Res.get(R.string.x_rpm, it) } ?: "---"
    fun speedFormatted() = speedKmh?.let { Res.get(R.string.x_km_h, it) } ?: "---"
    fun loadFormatted() = engineLoadPct?.let { Res.get(R.string.x_6, (it.toInt())) } ?: "---"
    fun throttleFormatted() = throttlePct?.let { Res.get(R.string.x_6, (it.toInt())) } ?: "---"
    fun ignitionFormatted() = ignitionAdvanceDeg?.let { Res.get(R.string.n_1f).format(it) } ?: "---"

    fun coolantFormatted() = coolantTempC?.let { Res.get(R.string.x_c, it) } ?: "---"
    fun intakeTempFormatted() = intakeTempC?.let { Res.get(R.string.x_c, it) } ?: "---"
    fun ambientTempFormatted() = ambientTempC?.let { Res.get(R.string.x_c, it) } ?: "---"
    fun oilTempFormatted() = oilTempC?.let { Res.get(R.string.x_c, it) } ?: "---"
    fun dpfTempFormatted() = dpfTempC?.let { Res.get(R.string.x_c, it) } ?: "---"

    fun mafFormatted() = mafGS?.let { Res.get(R.string.n_1f_g_s).format(it) } ?: "---"
    fun stftB1Formatted() = shortFuelTrimB1Pct?.let { Res.get(R.string.n_1f_2).format(it) } ?: "---"
    fun ltftB1Formatted() = longFuelTrimB1Pct?.let { Res.get(R.string.n_1f_2).format(it) } ?: "---"
    fun stftB2Formatted() = shortFuelTrimB2Pct?.let { Res.get(R.string.n_1f_2).format(it) } ?: "---"
    fun ltftB2Formatted() = longFuelTrimB2Pct?.let { Res.get(R.string.n_1f_2).format(it) } ?: "---"
    fun fuelLevelFormatted() = fuelLevelPct?.let { Res.get(R.string.x_6, (it.toInt())) } ?: "---"
    fun fuelPressureFormatted() = fuelPressureKpa?.let { Res.get(R.string.x_kpa, it) } ?: "---"
    fun fuelFlowFormatted() = fuelFlowRateLh?.let { Res.get(R.string.n_2f_l_h).format(it) } ?: "---"
    fun ethanolFormatted() = ethanolPct?.let { Res.get(R.string.x_6, (it.toInt())) } ?: "---"
    fun equivRatioFormatted() = commandedEquivRatio?.let { Res.get(R.string.n_3f).format(it) } ?: "---"
    fun commandedEgrFormatted() = commandedEgrPct?.let { Res.get(R.string.n_1f_2).format(it) } ?: "---"
    fun egrErrorFormatted() = egrErrorPct?.let { Res.get(R.string.n_1f_2).format(it) } ?: "---"

    fun mapFormatted() = mapKpa?.let { Res.get(R.string.x_kpa, it) } ?: "---"
    fun baroPressureFormatted() = baroPressureKpa?.let { Res.get(R.string.x_kpa, it) } ?: "---"
    fun evapPressureFormatted() = evapVaporPressurePa?.let { Res.get(R.string.n_1f_pa).format(it) } ?: "---"

    fun o2B1S1Formatted() = o2B1S1Voltage?.let { Res.get(R.string.n_3f_v).format(it) } ?: "---"
    fun o2B1S2Formatted() = o2B1S2Voltage?.let { Res.get(R.string.n_3f_v).format(it) } ?: "---"

    fun voltageFormatted() = controlModuleVoltage?.let { Res.get(R.string.n_2f_v).format(it) } ?: "---"
    fun obdPortVoltageFormatted() = obdPortVoltage?.let { Res.get(R.string.n_2f_v).format(it) } ?: "---"
    fun hybridChargeFormatted() = hybridBatteryChargePct?.let { Res.get(R.string.x_6, (it.toInt())) } ?: "---"
    fun hybridVoltageFormatted() = hybridBatteryVoltage?.let { Res.get(R.string.n_1f_v).format(it) } ?: "---"

    fun runtimeFormatted() = runtimeSec?.let {
        val m = it / 60; val s = it % 60
        Res.get(R.string.d_min_02d_s).format(m, s)
    } ?: "---"
}

/**
 * Additional data computed outside the main polling.
 */
data class ObdExtendedData(
    val fuelConsumptionL100: Float? = null,
    val estimatedHp: Float? = null,
    val estimatedTorqueNm: Float? = null
) {
    fun consumptionFormatted() = fuelConsumptionL100?.let { Res.get(R.string.n_1f_l_100km).format(it) } ?: "---"
    fun hpFormatted() = estimatedHp?.let { Res.get(R.string.x_cv, (it.toInt())) } ?: "---"
    fun torqueFormatted() = estimatedTorqueNm?.let { Res.get(R.string.x_nm, (it.toInt())) } ?: "---"
}
