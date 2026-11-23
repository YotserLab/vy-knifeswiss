package com.example.cast.discovery

import android.content.Context
import android.net.wifi.WifiManager
import com.example.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import kotlin.coroutines.coroutineContext

/**
 * SSDP multicast discovery (UPnP M-SEARCH).
 *
 * Sends M-SEARCH probes for MediaRenderer / DIAL / Roku targets to
 * 239.255.255.250:1900 and collects `HTTP/1.1 200 OK` datagrams until
 * [windowMs] elapses. Responses are deduplicated by LOCATION — when several
 * STs arrive for the same device the most informative one wins
 * (roku:ecp > MediaRenderer > dial > others), so e.g. a Roku answering
 * `ssdp:all` isn't later misclassified as DIAL.
 *
 * Requires a WifiManager MulticastLock (CHANGE_WIFI_MULTICAST_STATE is in
 * the manifest); the lock is released in a finally block.
 */
// MED-R07
class SsdpDiscovery(context: Context) {

    private val appContext = context.applicationContext

    companion object {
        private const val MULTICAST_ADDR = "239.255.255.250"
        private const val SSDP_PORT = 1900

        private val SEARCH_TARGETS = listOf(
            "ssdp:all",
            "urn:schemas-upnp-org:device:MediaRenderer:1",
            "urn:dial-multiscreen-org:service:dial:1",
            "roku:ecp"
        )

        internal fun mSearch(st: String): ByteArray =
            ("M-SEARCH * HTTP/1.1\r\n" +
                "HOST: $MULTICAST_ADDR:$SSDP_PORT\r\n" +
                "MAN: \"ssdp:discover\"\r\n" +
                "MX: 3\r\n" +
                "ST: $st\r\n\r\n").toByteArray(Charsets.US_ASCII)

        /** Higher rank = more informative ST for downstream classification. */
        internal fun stRank(st: String): Int = when {
            st.contains("roku:ecp", ignoreCase = true) -> 3
            st.contains("MediaRenderer", ignoreCase = true) -> 2
            st.contains("dial", ignoreCase = true) -> 1
            else -> 0
        }
    }

    suspend fun scan(windowMs: Long = 4000): List<SsdpResponse> = withContext(Dispatchers.IO) {
        val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = try {
            wifi?.createMulticastLock("cast_ssdp")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) e.printStackTrace()
            null
        }
        try {
            collect(windowMs)
        } finally {
            try {
                lock?.release()
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) e.printStackTrace()
            }
        }
    }

    private suspend fun collect(windowMs: Long): List<SsdpResponse> {
        val byLocation = LinkedHashMap<String, SsdpResponse>()
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            socket.soTimeout = 500
            val group = InetAddress.getByName(MULTICAST_ADDR)

            // send the batch twice ~300ms apart — multicast is lossy
            repeat(2) { round ->
                for (st in SEARCH_TARGETS) {
                    try {
                        val data = mSearch(st)
                        socket.send(DatagramPacket(data, data.size, group, SSDP_PORT))
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) e.printStackTrace()
                    }
                }
                if (round == 0) delay(300)
            }

            val deadline = System.currentTimeMillis() + windowMs
            val buf = ByteArray(4096)
            while (System.currentTimeMillis() < deadline) {
                coroutineContext.ensureActive()
                try {
                    val packet = DatagramPacket(buf, buf.size)
                    socket.receive(packet)
                    val raw = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                    val resp = SsdpParser.parse(raw) ?: continue
                    val existing = byLocation[resp.location]
                    if (existing == null || stRank(resp.st) > stRank(existing.st)) {
                        byLocation[resp.location] = resp
                    }
                } catch (_: SocketTimeoutException) {
                    // no datagram within soTimeout — loop until the deadline
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) e.printStackTrace()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) e.printStackTrace()
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {
            }
        }
        return byLocation.values.toList()
    }
}
