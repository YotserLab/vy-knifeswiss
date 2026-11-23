package com.example.cast.discovery
import com.example.utils.net.NetworkUtils

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.example.BuildConfig
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * mDNS/NSD discovery for Google Cast targets (`_googlecast._tcp.`).
 *
 * Android's NsdManager only allows one in-flight resolveService call, so
 * found services are queued and resolved serially (same pattern as
 * NetworkUtils.startMdnsDiscovery). Each resolved service is emitted via
 * the [onDevice] callback as a CHROMECAST [CastDevice].
 *
 * [stop] is idempotent — safe to call twice or without [start].
 */
class MdnsCastDiscovery(context: Context) {

    private val appContext = context.applicationContext

    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val resolveQueue = ConcurrentLinkedQueue<NsdServiceInfo>()
    private val isResolving = AtomicBoolean(false)
    private var onDevice: ((CastDevice) -> Unit)? = null

    fun start(onDevice: (CastDevice) -> Unit) {
        stop() // restart cleanly if already running
        this.onDevice = onDevice
        val nsd = appContext
            .getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        nsdManager = nsd

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                resolveQueue.add(serviceInfo)
                processResolveQueue()
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        discoveryListener = listener
        try {
            nsd.discoverServices("_googlecast._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) e.printStackTrace()
            discoveryListener = null
        }
    }

    fun stop() {
        val listener = discoveryListener
        discoveryListener = null
        if (listener != null) {
            try {
                nsdManager?.stopServiceDiscovery(listener)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) e.printStackTrace()
            }
        }
        resolveQueue.clear()
        isResolving.set(false)
        nsdManager = null
        onDevice = null
    }

    private fun processResolveQueue() {
        if (resolveQueue.isEmpty() || !isResolving.compareAndSet(false, true)) return
        val serviceInfo = resolveQueue.poll()
        if (serviceInfo == null) {
            isResolving.set(false)
            return
        }
        try {
            val nsd = nsdManager
            if (nsd == null) {
                isResolving.set(false)
                return
            }
            nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    isResolving.set(false)
                    processResolveQueue()
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    emitDevice(serviceInfo)
                    isResolving.set(false)
                    processResolveQueue()
                }
            })
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) e.printStackTrace()
            isResolving.set(false)
            processResolveQueue()
        }
    }

    private fun emitDevice(serviceInfo: NsdServiceInfo) {
        val ip = serviceInfo.host?.hostAddress ?: return
        // TXT record keys published by Cast devices: fn=friendly name,
        // id=device UUID, md=model, ca=capabilities. `attributes` can come
        // back empty on some platform versions, so the UUID also falls back
        // to the 32-hex suffix embedded in the service instance name
        // (e.g. "Chromecast-987a865d…").
        val attrs = runCatching { serviceInfo.attributes }.getOrNull().orEmpty()
        val fn = attrs["fn"]?.let { String(it, Charsets.UTF_8) }?.trim().orEmpty()
        val txtId = attrs["id"]?.let { String(it, Charsets.UTF_8) }.orEmpty()
        val model = attrs["md"]?.let { String(it, Charsets.UTF_8) }?.trim().orEmpty()
        val uuid = txtId.filter { it.isLetterOrDigit() }.ifEmpty {
            uuidFromServiceName(serviceInfo.serviceName)
        }
        val isGroup = serviceInfo.serviceName.startsWith("Google-Cast-Group-") ||
            model.contains("group", ignoreCase = true)
        onDevice?.invoke(
            CastDevice(
                id = "chromecast-$ip",
                name = fn.ifEmpty { cleanName(serviceInfo.serviceName).ifEmpty { "Chromecast" } },
                type = CastDeviceType.CHROMECAST,
                ip = ip,
                port = serviceInfo.port, // usually 8009
                manufacturer = "Google",
                modelName = model,
                deviceUuid = uuid,
                isGroup = isGroup
            )
        )
    }

    /** Extracts the 32-hex device/group UUID embedded in a cast service name. */
    internal fun uuidFromServiceName(name: String): String =
        Regex("[0-9a-fA-F]{32}").find(name)?.value?.lowercase().orEmpty()

    /** Strips mDNS service-type suffixes ("._googlecast._tcp", "._tcp.local") from a service name. */
    internal fun cleanName(raw: String): String {
        var n = raw.removeSuffix(".").removeSuffix(".local").removeSuffix(".")
        for (suffix in listOf("._googlecast._tcp", "._tcp")) {
            val idx = n.indexOf(suffix, ignoreCase = true)
            if (idx > 0) n = n.substring(0, idx)
        }
        return n.trim()
    }
}
