package com.example.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.UUID
import com.example.data.AppDatabase
import com.example.transport.ObdSessionEntity
import com.example.transport.ObdLogEntryEntity
import com.example.R
import com.example.Res

// ─── Transport Interfaces ─────────────────────────────────────────────────────

private interface ObdTransport {
    fun sendRaw(line: String): String
    fun close()
    val isOpen: Boolean
}

private class BluetoothObdTransport(socket: BluetoothSocket) : ObdTransport {
    private val btSocket = socket
    private val reader = BufferedReader(InputStreamReader(btSocket.inputStream))
    private val writer: OutputStream = btSocket.outputStream
    override val isOpen: Boolean get() = runCatching { btSocket.isConnected }.getOrDefault(false)

    override fun sendRaw(line: String): String {
        writer.write("$line\r".toByteArray())
        writer.flush()
        return readUntilPrompt(reader)
    }

    override fun close() {
        runCatching { reader.close() }
        runCatching { writer.close() }
        runCatching { btSocket.close() }
    }
}

private class WifiObdTransport(host: String, port: Int = 35000) : ObdTransport {
    private val tcpSocket = Socket(host, port).also { it.soTimeout = 4000 } // OBD-R14
    private val reader = BufferedReader(InputStreamReader(tcpSocket.getInputStream()))
    private val writer: OutputStream = tcpSocket.getOutputStream()
    override val isOpen: Boolean get() = tcpSocket.isConnected && !tcpSocket.isClosed

    override fun sendRaw(line: String): String {
        writer.write("$line\r\n".toByteArray())
        writer.flush()
        return readUntilPrompt(reader)
    }

    override fun close() {
        runCatching { reader.close() }
        runCatching { writer.close() }
        runCatching { tcpSocket.close() }
    }
}

private fun readUntilPrompt(reader: BufferedReader): String {
    val sb = StringBuilder()
    try {
        while (true) {
            val ch = reader.read()
            if (ch == -1) break
            val c = ch.toChar()
            sb.append(c)
            if (c == '>') break
        }
    } catch (_: SocketTimeoutException) { /* return partial */ }
    return sb.toString()
        .replace(">", "").replace("\r", "").replace("\n", "").replace(" ", "").trim()
}

// ─── Connection State ─────────────────────────────────────────────────────────

enum class ObdConnectionState {
    DISCONNECTED, CONNECTING, INITIALIZING, CONNECTED, RECONNECTING, ERROR
}

// ─── Main Manager ─────────────────────────────────────────────────────────────

// OBD-R01
class BluetoothObdManager(private val context: Context) {

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter ?: @Suppress("DEPRECATION") BluetoothAdapter.getDefaultAdapter()

    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var transport: ObdTransport? = null
    
    private val obdDao = AppDatabase.getDatabase(context).obdDao()

    // ── Public State Flows ────────────────────────────────────────────────────

    private val _connectionState = MutableStateFlow(ObdConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ObdConnectionState> = _connectionState

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _obdData = MutableStateFlow(ObdData())
    val obdData: StateFlow<ObdData> = _obdData

    private val _customPidResults = MutableStateFlow<Map<String, String>>(emptyMap())
    val customPidResults: StateFlow<Map<String, String>> = _customPidResults

    private val _extendedData = MutableStateFlow(ObdExtendedData())
    val extendedData: StateFlow<ObdExtendedData> = _extendedData

    private val _dtcList = MutableStateFlow<List<ObdDtcCode>>(emptyList())
    val dtcList: StateFlow<List<ObdDtcCode>> = _dtcList

    private val _readinessMonitors = MutableStateFlow(ReadinessMonitors())
    val readinessMonitors: StateFlow<ReadinessMonitors> = _readinessMonitors // OBD-R09

    private val _freezeFrames = MutableStateFlow<List<FreezeFrame>>(emptyList())
    val freezeFrames: StateFlow<List<FreezeFrame>> = _freezeFrames

    private val _mode06Results = MutableStateFlow<List<ObdMode06Result>>(emptyList())
    val mode06Results: StateFlow<List<ObdMode06Result>> = _mode06Results

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    private val _currentTrip = MutableStateFlow(ObdTripSession())
    val currentTrip: StateFlow<ObdTripSession> = _currentTrip

    private val _performanceResults = MutableStateFlow<List<PerformanceResult>>(emptyList())
    val performanceResults: StateFlow<List<PerformanceResult>> = _performanceResults

    // ── Device Discovery ──────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> = runCatching {
        val adapter = bluetoothAdapter
        if (adapter != null && adapter.isEnabled) adapter.bondedDevices?.toList() ?: emptyList()
        else emptyList()
    }.getOrDefault(emptyList())

    // ── Bluetooth Connect ────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    suspend fun connect(deviceAddress: String): Boolean = withContext(Dispatchers.IO) {
        _connectionState.value = ObdConnectionState.CONNECTING
        _lastError.value = null
        runCatching {
            val adapter = bluetoothAdapter ?: error("Bluetooth adapter unavailable")
            val device = adapter.getRemoteDevice(deviceAddress)
            runCatching { adapter.cancelDiscovery() }
            val btSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            btSocket.connect()
            transport = BluetoothObdTransport(btSocket)
            _currentTrip.value = ObdTripSession(deviceName = runCatching { device.name ?: deviceAddress }.getOrDefault(deviceAddress))
            finalizeConnection()
            true
        }.onFailure { e ->
            _connectionState.value = ObdConnectionState.ERROR
            _lastError.value = e.message
            disconnect()
        }.getOrDefault(false)
    }

    // ── WiFi Connect ─────────────────────────────────────────────────────────

    suspend fun connectWifi(host: String, port: Int = 35000): Boolean = withContext(Dispatchers.IO) {
        _connectionState.value = ObdConnectionState.CONNECTING
        _lastError.value = null
        runCatching {
            transport = WifiObdTransport(host, port)
            _currentTrip.value = ObdTripSession(deviceName = "$host:$port")
            finalizeConnection()
            true
        }.onFailure { e ->
            _connectionState.value = ObdConnectionState.ERROR
            _lastError.value = e.message
            disconnect()
        }.getOrDefault(false)
    }

    private suspend fun finalizeConnection() {
        _connectionState.value = ObdConnectionState.INITIALIZING
        initializeElm327()
        _isConnected.value = true
        _connectionState.value = ObdConnectionState.CONNECTED
    }

    private suspend fun initializeElm327() = withContext(Dispatchers.IO) {
        sendCommand("ATZ");  delay(1000)
        sendCommand("ATE0"); delay(100)
        sendCommand("ATL0"); delay(100)
        sendCommand("ATH0"); delay(100)
        sendCommand("ATS0"); delay(100)
        sendCommand("ATM0"); delay(100)
        sendCommand("ATAT1"); delay(100)
        sendCommand("ATSP0"); delay(800) // OBD-R13
    }

    // ── Disconnect ───────────────────────────────────────────────────────────

    fun disconnect() {
        val trip = _currentTrip.value
        _currentTrip.value = trip.copy(endTimeMs = System.currentTimeMillis())
        runCatching { transport?.close() }
        transport = null
        _isConnected.value = false
        _connectionState.value = ObdConnectionState.DISCONNECTED
    }

    // ── Low-level I/O ─────────────────────────────────────────────────────────

    private suspend fun sendCommand(command: String): String = withContext(Dispatchers.IO) {
        transport?.let { t ->
            runCatching { t.sendRaw(command) }.getOrDefault("")
        } ?: ""
    }

    // ── Polling Loop ─────────────────────────────────────────────────────────

    suspend fun startPolling(intervalMs: Long = 400L /* OBD-R02 */) = withContext(Dispatchers.IO) {
        var slowCycle = 0
        val tripComputer = ObdTripComputer()
        var dbSessionId: Long? = null

        // Initial session insert
        val initTrip = _currentTrip.value
        dbSessionId = obdDao.insertSession(ObdSessionEntity(
            startTimeMs = initTrip.startTimeMs,
            endTimeMs = 0,
            vehicleName = initTrip.deviceName,
            maxRpm = 0,
            maxSpeedKmh = 0,
            distanceKm = 0f,
            fuelConsumedL = 0f
        ))
        _currentTrip.value = initTrip.copy(id = dbSessionId) // OBD-R07

        while (_isConnected.value) {
            val current = _obdData.value
            val ext = _extendedData.value
            var updated = current
            var updatedExt = ext

            // Fast PIDs — every cycle
            listOf("010C", "010D", "0105", "0104", "0111").forEach { cmd ->
                if (!_isConnected.value) return@forEach
                val raw = sendCommand(cmd)
                if (raw.isNotBlank()) updated = applyMode01Pid(cmd.substring(2), raw, updated)
            }

            // Medium PIDs — every 3rd cycle
            if (slowCycle % 3 == 0) {
                listOf("010F", "0110", "010B", "0106", "0107", "0142", "0144").forEach { cmd ->
                    if (!_isConnected.value) return@forEach
                    val raw = sendCommand(cmd)
                    if (raw.isNotBlank()) {
                        updated = applyMode01Pid(cmd.substring(2), raw, updated)
                    }
                }
            }

            // Slow PIDs — every 10th cycle
            if (slowCycle % 10 == 0) {
                listOf("012F", "010A", "0133", "0146", "015C", "011F", "015E", "0108", "0109", "010E", "0121", "014D", "0132", "0153", "012C", "012D", "015B", "017C").forEach { cmd ->
                    if (!_isConnected.value) return@forEach
                    val raw = sendCommand(cmd)
                    if (raw.isNotBlank()) {
                        updated = applyMode01Pid(cmd.substring(2), raw, updated)
                    }
                }
            }

            // AT RV (Battery Voltage) — every 5th cycle
            if (slowCycle % 5 == 0) {
                if (!_isConnected.value) break
                val rv = sendCommand("AT RV")
                if (rv.isNotBlank() && rv.contains("V")) {
                    runCatching {
                        val vol = rv.replace("V", "").trim().toFloat()
                        updated = updated.copy(obdPortVoltage = vol)
                    }
                }
            }

            // ── Custom PIDs (OEM Profiles) — every 20th cycle ──────────────
            if (slowCycle % 20 == 0) {
                if (!_isConnected.value) break
                val profile = ObdOemProfiles.PRELOADED_PROFILES.firstOrNull() // Temporariamente usa o primeiro (Sandero)
                if (profile != null) {
                    var currentHeader = "7DF"
                    val resultsMap = _customPidResults.value.toMutableMap()
                    
                    profile.pids.forEach { pid ->
                        if (pid.header != null && pid.header != currentHeader) {
                            sendCommand("AT SH ${pid.header}")
                            currentHeader = pid.header
                        }
                        
                        val raw = sendCommand(pid.command)
                        if (raw.isNotBlank()) {
                            val cleanRaw = raw.replace(" ", "").replace("\r", "").replace("\n", "").replace(">", "")
                            val prefixFirstChar = (pid.command.substring(0, 1).toInt(16) + 4).toString(16).uppercase()
                            val responsePrefix = prefixFirstChar + pid.command.substring(1)
                            
                            if (cleanRaw.contains(responsePrefix, ignoreCase = true)) {
                                val idx = cleanRaw.indexOf(responsePrefix, ignoreCase = true)
                                val payloadHex = cleanRaw.substring(idx + responsePrefix.length)
                                val bytesList = mutableListOf<Int>()
                                var i = 0
                                while (i + 1 < payloadHex.length) {
                                    runCatching { bytesList.add(payloadHex.substring(i, i + 2).toInt(16)) }
                                    i += 2
                                }
                                val parsedValue = pid.parse(bytesList.toIntArray())
                                if (parsedValue != null) {
                                    resultsMap[pid.name] = "${"%.1f".format(parsedValue)} ${pid.unit}"
                                }
                            }
                        }
                    }
                    if (currentHeader != "7DF") {
                        sendCommand("AT SH 7DF") // Reset to broadcast
                    }
                    _customPidResults.value = resultsMap
                }
            }

            // Virtual Dyno calculation (HP / Torque) — OBD-R11
            val rpmVal = updated.rpm?.toFloat() ?: 0f
            val mafVal = updated.mafGS ?: 0f
            var hp = 0f
            var torque = 0f
            
            if (rpmVal > 0f && mafVal > 0f) {
                hp = mafVal / 0.8f // Approximate horsepower formula from airflow
                torque = (hp * 5252f) / rpmVal // Em lb-ft
                torque *= 1.3558f // Conversion to Nm
            }
            
            updatedExt = updatedExt.copy(
                estimatedHp = hp.takeIf { it > 0f },
                estimatedTorqueNm = torque.takeIf { it > 0f }
            )

            _obdData.value = updated
            _extendedData.value = updatedExt

            // Trip computer update
            tripComputer.update(updated, intervalMs)
            val trip = _currentTrip.value
            _currentTrip.value = trip.copy(
                maxRpm = maxOf(trip.maxRpm, updated.rpm ?: 0),
                maxSpeedKmh = maxOf(trip.maxSpeedKmh, updated.speedKmh ?: 0),
                maxCoolantTempC = maxOf(trip.maxCoolantTempC, updated.coolantTempC ?: 0),
                maxHp = maxOf(trip.maxHp, hp),
                maxTorqueNm = maxOf(trip.maxTorqueNm, torque),
                distanceKm = tripComputer.distanceKm,
                fuelConsumedL = tripComputer.fuelConsumedL,
                avgEngineLoadPct = tripComputer.avgLoad
            )

            // Connectivity check
            if (transport?.isOpen == false) {
                _isConnected.value = false
                _connectionState.value = ObdConnectionState.DISCONNECTED
            }

            // Log entry every 10 cycles (~4 seconds)
            if (slowCycle % 10 == 0 && dbSessionId != null) {
                obdDao.insertLogEntry(ObdLogEntryEntity(
                    sessionId = dbSessionId,
                    timestampMs = System.currentTimeMillis(),
                    rpm = updated.rpm ?: 0,
                    speedKmh = updated.speedKmh ?: 0,
                    engineLoadPct = updated.engineLoadPct ?: 0f,
                    coolantTempC = updated.coolantTempC ?: 0,
                    voltage = updated.obdPortVoltage ?: updated.controlModuleVoltage ?: 0f
                ))
                // Also update the session aggregate stats
                val t = _currentTrip.value
                obdDao.updateSession(
                    sessionId = dbSessionId,
                    endTimeMs = t.endTimeMs.takeIf { it > 0 } ?: System.currentTimeMillis(),
                    maxRpm = t.maxRpm,
                    maxSpeedKmh = t.maxSpeedKmh,
                    distanceKm = t.distanceKm,
                    fuelConsumedL = t.fuelConsumedL
                )
            }

            slowCycle++
            delay(intervalMs)
        }
    }

    // ── Mode 01 PID Parser ────────────────────────────────────────────────────

    private fun applyMode01Pid(pid: String, raw: String, data: ObdData): ObdData {
        val pidUpper = pid.uppercase()
        val responseCode = "41$pidUpper"
        if (!raw.contains(responseCode, ignoreCase = true)) return data

        val idx = raw.indexOf(responseCode, ignoreCase = true)
        val payload = raw.substring(idx + responseCode.length)

        return runCatching {
            when (pidUpper) {
                "04" -> data.copy(engineLoadPct = payload.byte(0) * 100f / 255f)
                "05" -> data.copy(coolantTempC = payload.byte(0) - 40)
                "06" -> data.copy(shortFuelTrimB1Pct = payload.byte(0) * 100f / 128f - 100f)
                "07" -> data.copy(longFuelTrimB1Pct = payload.byte(0) * 100f / 128f - 100f)
                "08" -> data.copy(shortFuelTrimB2Pct = payload.byte(0) * 100f / 128f - 100f)
                "09" -> data.copy(longFuelTrimB2Pct = payload.byte(0) * 100f / 128f - 100f)
                "0A" -> data.copy(fuelPressureKpa = payload.byte(0) * 3)
                "0B" -> data.copy(mapKpa = payload.byte(0))
                "0C" -> data.copy(rpm = (payload.byte(0) * 256 + payload.byte(1)) / 4)
                "0D" -> data.copy(speedKmh = payload.byte(0))
                "0E" -> data.copy(ignitionAdvanceDeg = payload.byte(0) / 2f - 64f)
                "0F" -> data.copy(intakeTempC = payload.byte(0) - 40)
                "10" -> data.copy(mafGS = (payload.byte(0) * 256f + payload.byte(1)) / 100f)
                "11" -> data.copy(throttlePct = payload.byte(0) * 100f / 255f)
                "1F" -> data.copy(runtimeSec = payload.byte(0) * 256 + payload.byte(1))
                "21" -> data.copy(distanceMilKm = payload.byte(0) * 256 + payload.byte(1))
                "2F" -> data.copy(fuelLevelPct = payload.byte(0) * 100f / 255f)
                "32" -> {
                    val raw16 = payload.byte(0) * 256 + payload.byte(1)
                    val signed = if (raw16 >= 32768) raw16 - 65536 else raw16
                    data.copy(evapVaporPressurePa = signed / 4f)
                }
                "33" -> data.copy(baroPressureKpa = payload.byte(0))
                "42" -> data.copy(controlModuleVoltage = (payload.byte(0) * 256f + payload.byte(1)) / 1000f)
                "44" -> data.copy(commandedEquivRatio = (payload.byte(0) * 256f + payload.byte(1)) / 32768f)
                "46" -> data.copy(ambientTempC = payload.byte(0) - 40)
                "4D" -> data.copy(timeMilMin = payload.byte(0) * 256 + payload.byte(1))
                "52" -> data.copy(ethanolPct = payload.byte(0) * 100f / 255f)
                "53" -> data.copy(evapAbsPressureKpa = (payload.byte(0) * 256f + payload.byte(1)) / 200f)
                "5C" -> data.copy(oilTempC = payload.byte(0) - 40)
                "5E" -> data.copy(fuelFlowRateLh = (payload.byte(0) * 256f + payload.byte(1)) / 20f)
                "14" -> data.copy(o2B1S1Voltage = payload.byte(0) / 200f)
                "15" -> data.copy(o2B1S2Voltage = payload.byte(0) / 200f)
                "2C" -> data.copy(commandedEgrPct = payload.byte(0) * 100f / 255f)
                "2D" -> data.copy(egrErrorPct = payload.byte(0) * 100f / 128f - 100f)
                "5B" -> data.copy(hybridBatteryChargePct = payload.byte(0) * 100f / 255f)
                "7C" -> data.copy(dpfTempC = ((payload.byte(0) * 256 + payload.byte(1)) / 10) - 40)
                else -> data
            }
        }.getOrDefault(data)
    }

    private fun String.byte(index: Int): Int {
        val start = index * 2
        return if (start + 2 <= length) substring(start, start + 2).toInt(16) else 0
    }

    // ── Readiness Monitors (Mode 01, PID 01) ──────────────────────────────────

    suspend fun readReadinessMonitors(): ReadinessMonitors = withContext(Dispatchers.IO) {
        val raw = sendCommand("0101")
        if (raw.isBlank() || raw.contains("NODATA", ignoreCase = true)) {
            return@withContext ReadinessMonitors()
        }
        val idx = raw.indexOf("4101", ignoreCase = true)
        if (idx < 0 || idx + 12 > raw.length) return@withContext ReadinessMonitors()

        runCatching {
            val b1 = raw.substring(idx + 4, idx + 6).toInt(16)
            val b2 = raw.substring(idx + 6, idx + 8).toInt(16)
            val b3 = raw.substring(idx + 8, idx + 10).toInt(16)
            val b4 = raw.substring(idx + 10, idx + 12).toInt(16)

            val milOn = (b1 and 0x80) != 0

            fun bit(byte: Int, bit: Int): Boolean = (byte shr bit) and 1 == 1

            // b2: bits 0-2 = continuous monitors incomplete flags
            // b3: bits = non-continuous supported (1=supported)
            // b4: bits = non-continuous incomplete (1=not ready)
            fun status(supported: Boolean, incomplete: Boolean): MonitorStatus = when {
                !supported -> MonitorStatus.NOT_SUPPORTED
                incomplete -> MonitorStatus.NOT_READY
                else -> MonitorStatus.READY
            }

            ReadinessMonitors(
                milOn = milOn,
                misfireReady = status(!bit(b2, 0), bit(b2, 4)),
                fuelSystemReady = status(!bit(b2, 1), bit(b2, 5)),
                componentsReady = status(!bit(b2, 2), bit(b2, 6)),
                catalystReady = status(bit(b3, 0), bit(b4, 0)),
                heatedCatalystReady = status(bit(b3, 1), bit(b4, 1)),
                evapSystemReady = status(bit(b3, 2), bit(b4, 2)),
                secondaryAirReady = status(bit(b3, 3), bit(b4, 3)),
                acSystemReady = status(bit(b3, 4), bit(b4, 4)),
                oxygenSensorReady = status(bit(b3, 5), bit(b4, 5)),
                oxygenSensorHeaterReady = status(bit(b3, 6), bit(b4, 6)),
                egrSystemReady = status(bit(b3, 7), bit(b4, 7))
            ).also { _readinessMonitors.value = it }
        }.getOrDefault(ReadinessMonitors())
    }

    // ── Freeze Frame (Mode 02) ────────────────────────────────────────────────

    suspend fun readFreezeFrames(dtcs: List<ObdDtcCode>): List<FreezeFrame> = withContext(Dispatchers.IO) {
        val frames = mutableListOf<FreezeFrame>()
        for (dtc in dtcs.filter { !it.isPending }) {
            val frame = readFreezeFrame(dtc.code)
            if (frame != null) frames.add(frame)
        }
        _freezeFrames.value = frames
        frames
    }

    private suspend fun readFreezeFrame(dtcCode: String): FreezeFrame? = withContext(Dispatchers.IO) {
        // Mode 02 uses same PIDs as Mode 01 but response header is 42 instead of 41
        suspend fun readFF(pid: String): String = runCatching { sendCommand("02$pid") }.getOrDefault("")

        fun parseFF(raw: String, pid: String): String? {
            val hdr = "42$pid"
            val idx = raw.indexOf(hdr, ignoreCase = true)
            return if (idx >= 0 && idx + hdr.length < raw.length) raw.substring(idx + hdr.length) else null
        }

        val rpmRaw = parseFF(readFF("0C"), "0C")
        val spdRaw = parseFF(readFF("0D"), "0D")
        val tmpRaw = parseFF(readFF("05"), "05")
        val ldRaw  = parseFF(readFF("04"), "04")
        val tpsRaw = parseFF(readFF("11"), "11")
        val stftRaw= parseFF(readFF("06"), "06")
        val ltftRaw= parseFF(readFF("07"), "07")
        val mapRaw = parseFF(readFF("0B"), "0B")
        val mafRaw = parseFF(readFF("10"), "10")

        if (rpmRaw == null && spdRaw == null) return@withContext null // ECU doesn't support freeze frame

        fun String?.byte(i: Int): Int? = this?.let { if (i*2+2 <= it.length) it.substring(i*2, i*2+2).toInt(16) else null }

        FreezeFrame(
            triggerDtc = dtcCode,
            rpm = rpmRaw?.byte(0)?.let { a -> rpmRaw.byte(1)?.let { b -> (a * 256 + b) / 4 } },
            speedKmh = spdRaw?.byte(0),
            coolantTempC = tmpRaw?.byte(0)?.minus(40),
            engineLoadPct = ldRaw?.byte(0)?.let { it * 100f / 255f },
            throttlePct = tpsRaw?.byte(0)?.let { it * 100f / 255f },
            shortFuelTrimPct = stftRaw?.byte(0)?.let { it * 100f / 128f - 100f },
            longFuelTrimPct = ltftRaw?.byte(0)?.let { it * 100f / 128f - 100f },
            mapKpa = mapRaw?.byte(0),
            mafGS = mafRaw?.let { raw ->
                val a = raw.byte(0) ?: return@let null
                val b = raw.byte(1) ?: return@let null
                (a * 256f + b) / 100f
            }
        )
    }

    // ── Mode 06 (ECU Self-Test Results) ──────────────────────────────────────

    suspend fun readMode06(): List<ObdMode06Result> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ObdMode06Result>()
        // Request all test results
        val raw = sendCommand("06")
        if (raw.isBlank() || raw.contains("NODATA", ignoreCase = true)) return@withContext emptyList()

        // Parse Mode 06 response (46 XX XX VVVV MMMM LLLL format)
        var i = 0
        while (i + 3 < raw.length) {
            runCatching {
                val header = raw.substring(i, i + 2)
                if (header == "46") {
                    val testId = raw.substring(i + 2, i + 4)
                    val monId  = raw.substring(i + 4, i + 6)
                    val value  = raw.substring(i + 6, i + 10).toInt(16)
                    val maxV   = raw.substring(i + 10, i + 14).toInt(16)
                    val minV   = raw.substring(i + 14, i + 18).toInt(16)
                    results.add(
                        ObdMode06Result(
                            testId = testId,
                            monitorId = monId,
                            description = describeMode06Test(testId),
                            value = value.toFloat(),
                            minValue = minV.toFloat(),
                            maxValue = maxV.toFloat(),
                            passed = value in minV..maxV
                        )
                    )
                    i += 18
                } else {
                    i += 2
                }
            }.onFailure { i += 2 }
        }
        _mode06Results.value = results
        results
    }

    private fun describeMode06Test(testId: String): String = when (testId.uppercase()) {
        "01" -> Res.get(R.string.monitoramento_de_falha_de_ignicao)
        "02" -> Res.get(R.string.sistema_de_combustivel_banco_1)
        "03" -> Res.get(R.string.componentes_do_sistema_abrangente)
        "04" -> Res.get(R.string.eficiencia_do_catalisador_banco_1)
        "05" -> Res.get(R.string.catalisador_aquecido_banco_1)
        "06" -> Res.get(R.string.sistema_de_ar_secundario)
        "07" -> Res.get(R.string.sensor_de_o2_banco_1_sensor_1)
        "08" -> Res.get(R.string.aquecedor_do_sensor_o2_b1s1)
        "09" -> Res.get(R.string.sensor_de_o2_banco_1_sensor_2)
        "0A" -> Res.get(R.string.aquecedor_do_sensor_o2_b1s2)
        "0B" -> Res.get(R.string.monitoramento_do_sistema_evap_0_02)
        "0C" -> Res.get(R.string.monitoramento_do_sistema_evap_0_04)
        "0D" -> Res.get(R.string.egr_vvt_sistema)
        "0E" -> Res.get(R.string.sistema_de_injecao_de_combustivel)
        "0F" -> Res.get(R.string.tempo_de_resposta_do_sensor_o2)
        "10" -> Res.get(R.string.catalisador_banco_2)
        "11" -> Res.get(R.string.eficiencia_do_catalisador_banco_2)
        else -> Res.get(R.string.teste_do_monitor_x, testId)
    }

    // ── DTC Reading & Clearing ─────────────────────────────────────────────────

    suspend fun readDtcs(): List<ObdDtcCode> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ObdDtcCode>()
        results += parseDtcResponse(sendCommand("03"), isPending = false, isPermanent = false)
        results += parseDtcResponse(sendCommand("07"), isPending = true, isPermanent = false)
        _dtcList.value = results
        _currentTrip.value = _currentTrip.value.copy(dtcCount = results.size)
        results
    }

    suspend fun clearDtcs(): Boolean = withContext(Dispatchers.IO) {
        val response = sendCommand("04")
        val success = !response.contains("ERROR", ignoreCase = true)
        if (success) {
            _dtcList.value = emptyList()
            _freezeFrames.value = emptyList()
        }
        success
    }

    private fun parseDtcResponse(raw: String, isPending: Boolean, isPermanent: Boolean): List<ObdDtcCode> {
        if (raw.isBlank() || raw.contains("NODATA", ignoreCase = true) || raw.contains("ERROR", ignoreCase = true)) return emptyList()
        val prefix = if (isPending) "47" else "43"
        val cleaned = raw.replace(prefix, "", ignoreCase = true).trim()
        val dtcs = mutableListOf<ObdDtcCode>()
        var i = 0
        while (i + 3 < cleaned.length) {
            runCatching {
                val b1 = cleaned.substring(i, i + 2).toInt(16)
                val b2 = cleaned.substring(i + 2, i + 4).toInt(16)
                if (b1 != 0 || b2 != 0) {
                    val code = decodeDtcBytes(b1, b2)
                    if (code != "P0000") {
                        dtcs.add(ObdDtcCode(code, getDtcDescription(code), isPending, isPermanent))
                    }
                }
            }
            i += 4
        }
        return dtcs
    }

    private fun decodeDtcBytes(b1: Int, b2: Int): String {
        val type = when ((b1 and 0xC0) shr 6) { 0 -> 'P'; 1 -> 'C'; 2 -> 'B'; else -> 'U' }
        val d1 = (b1 and 0x30) shr 4
        val d2 = b1 and 0x0F
        val d3 = (b2 and 0xF0) shr 4
        val d4 = b2 and 0x0F
        return "$type$d1${d2.toString(16).uppercase()}${d3.toString(16).uppercase()}${d4.toString(16).uppercase()}"
    }

    private fun getDtcDescription(code: String): String = DtcDatabase.getDescription(code)

    // ── Performance Test ──────────────────────────────────────────────────────

    // OBD-R15: performance test records 0-100/0-60/0-200 km/h and quarter-mile results per session
    suspend fun runPerformanceTest(type: PerformanceType): PerformanceResult? = withContext(Dispatchers.IO) {
        val targetSpeed = when (type) {
            PerformanceType.ZERO_TO_100 -> 100
            PerformanceType.ZERO_TO_60 -> 60
            PerformanceType.QUARTER_MILE -> 160 // ~max speed at quarter mile marker for now
            PerformanceType.ZERO_TO_200 -> 200
        }

        // Wait for vehicle to be near stop
        var waitCount = 0
        while ((_obdData.value.speedKmh ?: 99) > 5 && waitCount < 30) {
            delay(200)
            waitCount++
        }
        if ((_obdData.value.speedKmh ?: 99) > 5) return@withContext null // vehicle not stopped

        var maxSpeedReached = 0
        val startMs = System.currentTimeMillis()
        var endMs = 0L

        // Poll until target speed reached or 60s timeout
        val timeoutMs = 60_000L
        while (System.currentTimeMillis() - startMs < timeoutMs) {
            val speed = _obdData.value.speedKmh ?: 0
            if (speed > maxSpeedReached) maxSpeedReached = speed
            if (speed >= targetSpeed) {
                endMs = System.currentTimeMillis()
                break
            }
            delay(100)
        }

        if (endMs == 0L) return@withContext null // timed out

        val result = PerformanceResult(
            type = type,
            durationMs = endMs - startMs,
            maxSpeedKmh = maxSpeedReached
        )
        _performanceResults.value = listOf(result) + _performanceResults.value
        result
    }

    // ── Fuel Consumption Estimate ─────────────────────────────────────────────

    fun estimateFuelConsumptionL100(): Float {
        val data = _obdData.value
        val speed = data.speedKmh ?: 0
        if (speed < 5) return 0f

        // Using MAF-based formula: L/h = MAF_g/s * 3600 / (AFR * fuel_density)
        // Gasoline AFR stoich ~14.7, density ~740 g/L
        val maf = data.mafGS
        if (maf != null && maf > 0f) {
            val fuelFlowLh = maf * 3600f / (14.7f * 740f)
            return (fuelFlowLh / speed) * 100f
        }

        // Fallback: use fuelFlowRate PID if available
        val flowLh = data.fuelFlowRateLh
        if (flowLh != null && flowLh > 0f) {
            return (flowLh / speed) * 100f
        }

        return 0f
    }
}

// ─── Trip Computer ────────────────────────────────────────────────────────────

class ObdTripComputer {
    var distanceKm: Float = 0f
        private set
    var fuelConsumedL: Float = 0f
        private set
    var avgLoad: Float = 0f
        private set

    private var lastSpeedKmh: Int = 0
    private var loadSamples: Int = 0
    private var loadSum: Float = 0f

    fun update(data: ObdData, intervalMs: Long) {
        val speed = data.speedKmh ?: 0
        val intervalH = intervalMs / 3_600_000f
        // distance = average of last and current speed * time
        distanceKm += ((lastSpeedKmh + speed) / 2f) * intervalH
        lastSpeedKmh = speed

        // fuel consumption via MAF
        val maf = data.mafGS
        if (maf != null && maf > 0f) {
            val flowLh = maf * 3600f / (14.7f * 740f)
            fuelConsumedL += flowLh * intervalH
        } else if (data.fuelFlowRateLh != null) {
            fuelConsumedL += (data.fuelFlowRateLh ?: 0f) * intervalH
        }

        // avg load
        val load = data.engineLoadPct
        if (load != null) {
            loadSum += load
            loadSamples++
            avgLoad = if (loadSamples > 0) loadSum / loadSamples else 0f
        }
    }
}
