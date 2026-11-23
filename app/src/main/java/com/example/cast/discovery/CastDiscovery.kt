package com.example.cast.discovery

import android.content.Context
import com.example.BuildConfig
import com.example.cast.control.LanHttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Facade that orchestrates all cast discovery sources:
 *
 *  - SSDP multicast M-SEARCH (DLNA MediaRenderer, DIAL, roku:ecp) via [SsdpDiscovery]
 *  - mDNS `_googlecast._tcp.` via [MdnsCastDiscovery]
 *  - direct Roku ECP probe for SSDP `roku:ecp` hits and manual IPs via [RokuProbe]
 *
 * Results from every source are funnelled through [CastDeviceMerger], which
 * deduplicates by IP keeping the most capable type (CHROMECAST > DLNA > ROKU > DIAL).
 * Exposes [devices] and [scanning] as StateFlows for UI binding.
 */
class CastDiscovery(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _devices = MutableStateFlow<List<CastDevice>>(emptyList())
    val devices: StateFlow<List<CastDevice>> = _devices

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    private var scanJob: Job? = null
    private var mdns: MdnsCastDiscovery? = null

    /** Starts mDNS immediately and runs one SSDP scan window (~6s total). */
    fun startScan() {
        if (_scanning.value) return
        _scanning.value = true

        val md = MdnsCastDiscovery(appContext)
        mdns = md
        md.start { device -> mergeIn(listOf(device)) }

        scanJob = scope.launch {
            val started = System.currentTimeMillis()
            try {
                val responses = SsdpDiscovery(appContext).scan(4000)
                val found = responses.map { resp ->
                    async(Dispatchers.IO) { describe(resp) }
                }.awaitAll().filterNotNull()
                mergeIn(found)

                // keep mDNS listening until the ~6s window ends
                val elapsed = System.currentTimeMillis() - started
                if (elapsed < 6000) delay(6000 - elapsed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) e.printStackTrace()
            } finally {
                // stop THIS scan's mdns instance — a stale job must not touch the
                // shared field or clobber a restarted scan's `scanning` flag
                md.stop()
                if (scanJob === coroutineContext.job) {
                    _scanning.value = false
                }
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        mdns?.stop()
        mdns = null
        _scanning.value = false
    }

    /** Probes a manually-entered IP — currently Roku ECP only. */
    fun addManualDevice(ip: String) {
        val target = ip.trim()
        if (target.isEmpty()) return
        scope.launch {
            val device = withContext(Dispatchers.IO) { RokuProbe.probe(target) }
            if (device != null) mergeIn(listOf(device))
        }
    }

    fun clear() {
        _devices.value = emptyList()
    }

    private fun mergeIn(incoming: List<CastDevice>) {
        if (incoming.isEmpty()) return
        // atomic read-modify-write — mDNS callbacks arrive on binder threads
        _devices.update { CastDeviceMerger.merge(it, incoming) }
        incoming.forEach {
            android.util.Log.d("CastDiscovery", "found ${it.type} '${it.name}' @ ${it.ip}")
        }
    }

    /**
     * Turns one SSDP response into a [CastDevice]:
     * `roku:ecp` STs are probed via ECP; everything else fetches the LOCATION
     * description and becomes DLNA (AVTransport present) or DIAL.
     * Callers must already be on Dispatchers.IO.
     */
    private suspend fun describe(resp: SsdpResponse): CastDevice? {
        return try {
            if (resp.st.contains("roku:ecp", ignoreCase = true)) {
                return RokuProbe.probe(resp.host)
            }
            val http = LanHttpClient.get(resp.location, 4000) ?: return null
            if (http.code != 200) return null
            val desc = DeviceDescParser.parse(http.body, resp.location) ?: return null
            CastDevice(
                id = desc.udn.ifEmpty { resp.location },
                name = desc.friendlyName.ifEmpty { resp.host },
                type = if (desc.avTransportControlUrl.isNotEmpty()) {
                    CastDeviceType.DLNA
                } else {
                    CastDeviceType.DIAL
                },
                ip = resp.host,
                port = resp.port,
                manufacturer = desc.manufacturer,
                modelName = desc.modelName,
                location = resp.location,
                avTransportControlUrl = desc.avTransportControlUrl,
                renderingControlUrl = desc.renderingControlUrl
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) e.printStackTrace()
            null
        }
    }
}
