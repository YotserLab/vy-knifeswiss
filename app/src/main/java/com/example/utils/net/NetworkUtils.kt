package com.example.utils.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.NetworkInterface
import java.net.Inet4Address
import java.net.UnknownHostException
import kotlin.math.roundToInt
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.example.R
import com.example.Res

object NetworkUtils {

    val mdnsCache = ConcurrentHashMap<String, String>()
    private var nsdManager: NsdManager? = null
    private val discoveryListeners = mutableListOf<NsdManager.DiscoveryListener>()
    private val resolveQueue = ConcurrentLinkedQueue<NsdServiceInfo>()
    private val isResolving = AtomicBoolean(false)

    private fun processResolveQueue() {
        if (resolveQueue.isEmpty() || !isResolving.compareAndSet(false, true)) return
        val serviceInfo = resolveQueue.poll()
        if (serviceInfo == null) {
            isResolving.set(false)
            return
        }
        try {
            nsdManager?.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    isResolving.set(false)
                    processResolveQueue()
                }
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val ip = serviceInfo.host?.hostAddress
                    if (ip != null) {
                        mdnsCache[ip] = serviceInfo.serviceName
                    }
                    isResolving.set(false)
                    processResolveQueue()
                }
            })
        } catch (e: Exception) {
            isResolving.set(false)
            processResolveQueue()
        }
    }

    fun startMdnsDiscovery(context: android.content.Context) {
        if (nsdManager != null) return
        nsdManager = context.getSystemService(android.content.Context.NSD_SERVICE) as NsdManager
        val services = listOf("_googlecast._tcp", "_airplay._tcp", "_smb._tcp", "_printer._tcp", "_http._tcp", "_spotify-connect._tcp", "_ipp._tcp", "_workstation._tcp", "_raop._tcp")
        
        for (service in services) {
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
            discoveryListeners.add(listener)
            try {
                nsdManager?.discoverServices(service, NsdManager.PROTOCOL_DNS_SD, listener)
            } catch (e: Exception) {}
        }
    }

    fun stopMdnsDiscovery() {
        for (listener in discoveryListeners) {
            try { nsdManager?.stopServiceDiscovery(listener) } catch (e: Exception) {}
        }
        discoveryListeners.clear()
        resolveQueue.clear()
        isResolving.set(false)
        nsdManager = null
    }

    fun queryNetBiosName(ip: String): Pair<String, String> {
        try {
            val socket = DatagramSocket()
            socket.soTimeout = 250
            val payload = byteArrayOf(
                0x82.toByte(), 0x28.toByte(), 0x0, 0x0, 0x0, 0x1, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0,
                0x20, 0x43, 0x4b, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41,
                0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41,
                0x41, 0x41, 0x41, 0x41, 0x41, 0x0, 0x0, 0x21, 0x0, 0x1
            )
            val packet = DatagramPacket(payload, payload.size, InetAddress.getByName(ip), 137)
            socket.send(packet)

            val buffer = ByteArray(1024)
            val receivePacket = DatagramPacket(buffer, buffer.size)
            socket.receive(receivePacket)
            
            val data = receivePacket.data
            if (receivePacket.length > 56) {
                val numNames = data[56].toInt() and 0xFF
                if (numNames > 0 && receivePacket.length >= 57 + (numNames * 18) + 6) {
                    val hostnameBytes = data.copyOfRange(57, 57 + 15)
                    val hostname = String(hostnameBytes).trim { it <= ' ' || it.code == 0 }
                    
                    val macIndex = 57 + (numNames * 18)
                    val macBytes = data.copyOfRange(macIndex, macIndex + 6)
                    val mac = macBytes.joinToString(":") { "%02X".format(it) }.uppercase()
                    
                    socket.close()
                    return Pair(hostname, if (mac == "00:00:00:00:00:00") "" else mac)
                }
            }
            socket.close()
        } catch (e: Exception) {
            // ignore
        }
        return Pair("", "")
    }

    val portNames = mapOf(
        21 to "FTP", 22 to "SSH", 23 to "Telnet", 25 to "SMTP", 53 to "DNS",
        80 to "HTTP", 110 to "POP3", 135 to "RPC", 139 to Res.get(R.string.netbios), 143 to "IMAP",
        443 to "HTTPS", 445 to "SMB", 3306 to Res.get(R.string.mysql), 3389 to "RDP", 8080 to Res.get(R.string.http_alt), // RED-R06
        8443 to Res.get(R.string.https_alt), 5900 to "VNC", 6379 to "Redis", 27017 to Res.get(R.string.mongodb),
        5000 to Res.get(R.string.upnp), 5353 to "mDNS", 1900 to Res.get(R.string.upnp_ssdp)
    )

    val ouiVendors = mapOf( // RED-R14
        "00:1A:2B" to "Cisco", "00:1B:17" to Res.get(R.string.d_link), "00:1D:7E" to Res.get(R.string.tp_link),
        "00:1E:58" to "ASUS", "00:22:6B" to "Netgear", "00:24:6C" to "Linksys",
        "00:26:5E" to "Apple", "00:50:56" to Res.get(R.string.vmware), "00:0C:29" to Res.get(R.string.vmware),
        "00:05:69" to "Intel", "00:14:22" to "Dell", "00:1A:A0" to "Huawei",
        "00:1F:6C" to "Samsung", "00:23:8E" to "LG", "00:24:BE" to "Microsoft",
        "00:25:9C" to "Sony", "00:26:08" to "Motorola", "00:27:10" to "Roku",
        "00:30:48" to "Compaq", "00:50:8D" to "Nokia", "00:60:1D" to "Acer",
        "00:80:5A" to "Xerox", "00:90:27" to "HP", "00:A0:C9" to "IBM",
        "00:B0:D0" to Res.get(R.string.n_3com), "00:C0:49" to "Fujitsu", "00:E0:4C" to "Realtek",
        "08:00:20" to "Sun", "08:00:46" to "Sony", "08:00:69" to "Intel",
        "08:10:77" to "Xiaomi", "0C:37:DC" to "Huawei", "10:2C:6B" to "Xiaomi",
        "10:68:3F" to "Netgear", "14:31:DB" to Res.get(R.string.tp_link), "18:FE:34" to Res.get(R.string.raspberry_pi),
        "24:69:68" to Res.get(R.string.tp_link), "24:FD:5B" to "Netgear", "28:6E:D4" to "Xiaomi",
        "2C:54:2D" to "Intel", "30:75:12" to "Xiaomi", "34:96:72" to "Huawei",
        "38:1A:52" to "Intel", "38:F9:D3" to "Xiaomi", "3C:52:82" to Res.get(R.string.tp_link),
        "40:31:3C" to "ASUS", "44:03:2C" to "Realtek", "44:44:44" to Res.get(R.string.d_link),
        "4C:66:41" to "Intel", "50:6A:6B" to Res.get(R.string.d_link), "54:60:09" to Res.get(R.string.tp_link),
        "58:6D:8F" to "Netgear", "60:01:94" to "Apple", "60:36:DD" to "Huawei",
        "64:1C:67" to "ASUS", "68:72:51" to Res.get(R.string.tp_link), "6C:5A:B0" to "Samsung",
        "6C:71:0D" to "Google", "70:5A:0F" to "Intel", "70:4C:A5" to Res.get(R.string.d_link),
        "74:DA:38" to "ASUS", "78:44:76" to "Microsoft", "78:D6:F0" to Res.get(R.string.d_link),
        "80:2A:A8" to "Google", "80:7A:BF" to "Realtek", "84:1B:5E" to "Intel",
        "84:64:1F" to Res.get(R.string.tp_link), "88:53:2E" to "ASUS", "90:9A:4A" to "Huawei",
        "94:10:3E" to "Tenda", "98:01:A7" to "Apple", "9C:28:BF" to "Intel",
        "A0:04:60" to Res.get(R.string.tp_link), "A4:5E:60" to "Dell", "A8:6B:7C" to "Netgear",
        "AC:22:0B" to Res.get(R.string.d_link), "B0:6A:9A" to "Intel", "B4:2A:0E" to "Huawei",
        "B8:37:65" to "Intel", "BC:EE:7B" to "Realtek", "C0:25:E9" to Res.get(R.string.d_link),
        "C4:04:15" to "Samsung", "C8:3A:35" to "ASUS", "CC:5D:4E" to "Intel",
        "D0:37:45" to "Apple", "D8:BB:2C" to "Xiaomi", "DC:FE:18" to "Huawei",
        "E0:06:E6" to Res.get(R.string.tp_link), "E0:3F:49" to "Intel", "E4:6F:13" to "Microsoft",
        "E8:48:B8" to "ASUS", "EC:8E:AD" to "Netgear", "F0:27:65" to Res.get(R.string.d_link),
        "F0:27:65" to Res.get(R.string.d_link),
        "F4:B5:2F" to "Intel", "F8:0D:43" to "Xiaomi", "FC:A6:CD" to "Tenda",
        "FC:DB:B3" to "Intel",
        "24:0A:C4" to "Espressif", "84:F3:EB" to "Espressif", "C4:DD:57" to "Espressif",
        "F0:08:D1" to "Amazon", "FC:A1:83" to "Amazon", "CC:9E:00" to "Amazon",
        "74:C2:46" to "Amazon", "44:65:0D" to "Amazon", "48:E1:E9" to "Amazon",
        "00:1E:8C" to "Motorola", "F8:CF:C5" to "Motorola",
        "30:C9:AB" to "Lenovo", "B4:C4:FC" to "Lenovo"
    )

    fun lookupMacVendor(mac: String): String {
        val clean = mac.replace("-", ":").uppercase().trim()
        val prefix = clean.take(8)
        return ouiVendors[prefix] ?: clean.take(8).let { ouiVendors[it] } ?: ""
    }

    fun getMacFromArp(ip: String): String {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "ip neigh show $ip"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val line = reader.readLine()
            process.waitFor()
            if (line != null && line.contains("lladdr")) {
                val parts = line.split("\\s+".toRegex())
                val idx = parts.indexOf("lladdr")
                if (idx >= 0 && idx + 1 < parts.size) {
                    val mac = parts[idx + 1].uppercase()
                    if (mac != "00:00:00:00:00:00") return mac
                }
            }
        } catch (e: Exception) {}

        try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "cat /proc/net/arp"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val parts = line!!.split(" +".toRegex())
                if (parts.size >= 4 && parts[0] == ip) {
                    val mac = parts[3].uppercase()
                    if (mac.matches(Regex("..:..:..:..:..:..")) && mac != "00:00:00:00:00:00") {
                        return mac
                    }
                }
            }
        } catch (e: Exception) {}
        
        return Res.get(R.string.restrito_pelo_os)
    }

    fun guessOS(hostname: String, vendor: String, openPorts: List<Int> = emptyList()): String {
        val h = hostname.lowercase()
        val v = vendor.lowercase()
        
        if (h.contains("iphone") || h.contains("ipad") || h.contains("macbook") || h.contains("imac") || h.contains("mac-mini")) return "Apple"
        if (v.contains("apple")) return "Apple"
        
        if (h.contains("android") || v.contains("samsung") || v.contains("xiaomi") || v.contains("motorola") || v.contains("lenovo")) return "Android"
        if (h.contains("desktop-") || h.contains("pc") || h.contains("win")) return "Windows"
        
        if (h.contains("tv") || v.contains("roku") || v.contains("tcl") || h.contains("chromecast")) return Res.get(R.string.smart_tv)
        if (v.contains("espressif") || v.contains("amazon") || v.contains("sonoff")) return Res.get(R.string.iot_smart_home)
        
        if (v.contains("tp-link") || v.contains("cisco") || v.contains("d-link") || v.contains("netgear") || v.contains("asus")) return Res.get(R.string.router_network)
        if (h.contains("nas") || h.contains("synology")) return "NAS"
        
        if (openPorts.contains(22) && openPorts.contains(80)) return Res.get(R.string.linux_unix)
        if (openPorts.contains(135) || openPorts.contains(139) || openPorts.contains(445)) return "Windows"
        
        return Res.get(R.string.desconhecido)
    }

    fun portServiceName(port: Int): String = portNames[port] ?: ""

    suspend fun getGateway(): String? = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("ip route show default")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val line = reader.readLine()
            process.waitFor()
            val parts = line?.split(" ") ?: return@withContext null
            val idx = parts.indexOf("via")
            if (idx >= 0 && idx + 1 < parts.size) parts[idx + 1] else null
        } catch (e: Exception) { null }
    }

    suspend fun getDnsServers(): List<String> = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("getprop net.dns1")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val dns1 = reader.readLine()
            process.waitFor()
            val list = mutableListOf<String>()
            if (!dns1.isNullOrBlank()) list.add(dns1)
            val p2 = Runtime.getRuntime().exec("getprop net.dns2")
            val r2 = BufferedReader(InputStreamReader(p2.inputStream))
            val dns2 = r2.readLine()
            p2.waitFor()
            if (!dns2.isNullOrBlank()) list.add(dns2)
            list
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getLocalIpAddress(): String? = withContext(Dispatchers.IO) {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address.hostAddress != null && address.hostAddress.indexOf(':') < 0) {
                        return@withContext address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        null
    }

    suspend fun getWifiIpAddress(): String? = getIpAddressForInterfaces("wlan", "ap", "eth")

    fun getTailscaleIpAddressSync(): String? {
        return getIpAddressForInterfacesInternal("tailscale", "tun").takeIf { it?.startsWith("100.") == true }
            ?: getIpAddressStartingWith("100.")
    }

    suspend fun getTailscaleIpAddress(): String? = withContext(Dispatchers.IO) {
        getTailscaleIpAddressSync()
    }

    private suspend fun getIpAddressForInterfaces(vararg prefixes: String): String? = withContext(Dispatchers.IO) {
        getIpAddressForInterfacesInternal(*prefixes)
    }

    private fun getIpAddressForInterfacesInternal(vararg prefixes: String): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                if (!prefixes.any { networkInterface.name.startsWith(it, ignoreCase = true) }) continue
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    val hostAddress = address.hostAddress
                    if (!address.isLoopbackAddress && hostAddress.indexOf(':') < 0) {
                        return hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return null
    }

    fun getWifiOrLocalIpSync(): String {
        return getIpAddressForInterfacesInternal("wlan", "ap", "eth") ?: getLocalIpSync() ?: "127.0.0.1"
    }

    fun isOwnIp(ip: String): Boolean {
        if (ip.isBlank() || ip == "127.0.0.1" || ip.startsWith("0.")) return true
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback) continue
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address.hostAddress == ip) return true
                }
            }
        } catch (_: Exception) {}
        return false
    }

    private fun getLocalIpSync(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address.hostAddress.indexOf(':') < 0) {
                        return address.hostAddress
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun getIpAddressStartingWith(prefix: String): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    val hostAddress = address.hostAddress
                    if (!address.isLoopbackAddress && hostAddress.indexOf(':') < 0 && hostAddress.startsWith(prefix)) {
                        return hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return null
    }

    suspend fun ping(host: String, count: Int = 4): String = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("ping -c $count $host")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor()
            if (output.isEmpty()) {
                val isReachable = InetAddress.getByName(host).isReachable(3000)
                if (isReachable) Res.get(R.string.host_is_reachable_ping_command_failed_fa)
                else Res.get(R.string.host_is_unreachable)
            } else {
                output.toString()
            }
        } catch (e: Exception) {
            Res.get(R.string.error_pinging_host_x, (e.message))
        }
    }

    suspend fun traceroute(host: String): String = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("traceroute -n -q 1 -w 2 $host")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor()
            if (output.isEmpty()) {
                Res.get(R.string.traceroute_nao_disponivel_parcelavel_via)
            } else {
                output.toString()
            }
        } catch (e: Exception) {
            Res.get(R.string.erro_x_4, (e.message))
        }
    }

    suspend fun scanPorts(host: String, portsToScan: List<Int>, timeoutMs: Int = 1000): List<Int> = withContext(Dispatchers.IO) {
        val openPorts = mutableListOf<Int>()
        for (port in portsToScan) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                socket.close()
                openPorts.add(port)
            } catch (e: Exception) {
                // Port closed or timeout
            }
        }
        openPorts
    }

    data class ScanResult(val ip: String, val hostname: String, val macAddress: String, val responseTimeMs: Long, val openPorts: List<Int> = emptyList(), val vendor: String = "", val os: String = "")

    suspend fun scanSubnetDetailed(
        rangeStart: String, 
        rangeEnd: String, 
        timeoutMs: Int = 300, 
        onHostFound: (ScanResult) -> Unit = {},
        onProgress: (Int, Int) -> Unit
    ): List<ScanResult> = withContext(Dispatchers.IO) {
        val startParts = rangeStart.split(".").map { it.toIntOrNull() ?: 0 }
        val endParts = rangeEnd.split(".").map { it.toIntOrNull() ?: 0 }
        
        if (startParts.size != 4 || endParts.size != 4) return@withContext emptyList()
        
        val prefix = Res.get(R.string.x_0_x_1_x_2, (startParts[0]), (startParts[1]), (startParts[2]))
        val startNum = startParts[3]
        val endNum = endParts[3]
        
        val total = endNum - startNum + 1
        if (total <= 0) return@withContext emptyList()

        val completed = java.util.concurrent.atomic.AtomicInteger(0)

        val deferredResults = (startNum..endNum).map { i ->
            async {
                val ip = Res.get(R.string.xx_9, prefix, i)
                val startTime = System.currentTimeMillis()
                var result: ScanResult? = null
                try {
                    val inetAddress = InetAddress.getByName(ip)
                    var isAlive = inetAddress.isReachable(timeoutMs)
                    
                    if (!isAlive) {
                        // Fallback to TCP port connection to typical open ports (DNS, HTTP)
                        val portsToTry = listOf(53, 80, 443)
                        for (p in portsToTry) {
                            try {
                                val s = Socket()
                                s.connect(InetSocketAddress(ip, p), timeoutMs)
                                s.close()
                                isAlive = true
                                break
                            } catch (e: Exception) {}
                        }
                    }

                    if (isAlive) {
                        val responseTime = System.currentTimeMillis() - startTime
                        val hostname = try { inetAddress.hostName } catch (e: Exception) { "" }
                        var realHostname = if (hostname == ip) "" else hostname
                        
                        var mac = getMacFromArp(ip)
                        
                        // Fallback to NetBIOS if MAC is hidden or Hostname is missing
                        if (mac == Res.get(R.string.restrito_pelo_os) || realHostname.isEmpty()) {
                            val nb = queryNetBiosName(ip)
                            if (nb.first.isNotEmpty()) realHostname = nb.first
                            if (nb.second.isNotEmpty()) mac = nb.second
                        }

                        // Override with mDNS name if discovered
                        val mdnsName = mdnsCache[ip]
                        if (!mdnsName.isNullOrEmpty()) {
                            realHostname = mdnsName
                        }
                        
                        val vendor = if (mac != Res.get(R.string.restrito_pelo_os)) lookupMacVendor(mac) else ""
                        val os = guessOS(realHostname, vendor, emptyList())
                        
                        result = ScanResult(ip, realHostname, mac, responseTime, emptyList(), vendor, os)
                        withContext(Dispatchers.Main) {
                            onHostFound(result)
                        }
                    }
                } catch (e: Exception) {
                    // ignore
                }
                
                val currentCompleted = completed.incrementAndGet()
                withContext(Dispatchers.Main) {
                    onProgress(currentCompleted, total)
                }
                
                result
            }
        }
        
        deferredResults.awaitAll().filterNotNull()
    }

    suspend fun scanSubnet(baseIp: String, timeoutMs: Int = 200, onProgress: (Int, Int) -> Unit): List<String> = withContext(Dispatchers.IO) {
        val activeHosts = mutableListOf<String>()
        val prefix = baseIp.substringBeforeLast(".")
        for (i in 1..254) {
            val ip = Res.get(R.string.xx_9, prefix, i)
            try {
                if (InetAddress.getByName(ip).isReachable(timeoutMs)) {
                    activeHosts.add(ip)
                }
            } catch (e: Exception) {
                // ignore
            }
            withContext(Dispatchers.Main) {
                onProgress(i, 254)
            }
        }
        activeHosts
    }

    suspend fun downloadFile(context: android.content.Context, urlString: String, fileName: String): String? = withContext(Dispatchers.IO) {
        try {
            val downloadDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            downloadDir.mkdirs()
            val safeFileName = fileName.replace("/", "_").replace("\\", "_").ifBlank { "download_${System.currentTimeMillis()}" }
            val file = java.io.File(downloadDir, safeFileName)
            file.parentFile?.mkdirs()

            val uri = java.net.URI(urlString)
            val scheme = uri.scheme?.lowercase() ?: "http"

            if (scheme == "https") {
                val url = uri.toURL()
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                connection.connect()
                if (connection.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                    connection.inputStream.use { input ->
                        java.io.FileOutputStream(file).use { output ->
                            input.copyTo(output)
                        }
                    }
                    return@withContext file.absolutePath
                } else {
                    if (com.example.BuildConfig.DEBUG) {
                        android.util.Log.e("NetworkUtils", "HTTPS download failed: ${connection.responseCode}")
                    }
                    return@withContext null
                }
            }

            // For local/P2P HTTP (avoids Android's CleartextURLFilter blocking local IPs)
            val host = uri.host ?: return@withContext null
            val port = if (uri.port != -1) uri.port else 80
            val path = (if (uri.rawPath.isNullOrBlank()) "/" else uri.rawPath) + (if (uri.rawQuery != null) "?${uri.rawQuery}" else "")

            java.net.Socket().use { socket ->
                socket.soTimeout = 30000
                socket.connect(java.net.InetSocketAddress(host, port), 15000)

                val request = "GET $path HTTP/1.1\r\nHost: $host:$port\r\nConnection: close\r\n\r\n"
                socket.getOutputStream().apply {
                    write(request.toByteArray(Charsets.UTF_8))
                    flush()
                }

                val input = java.io.BufferedInputStream(socket.getInputStream())

                // Parse status line and HTTP headers
                val headerBytes = java.io.ByteArrayOutputStream()
                var state = 0
                while (true) {
                    val b = input.read()
                    if (b == -1) break
                    headerBytes.write(b)
                    if (state == 0 && b == '\r'.code) state = 1
                    else if (state == 1 && b == '\n'.code) state = 2
                    else if (state == 2 && b == '\r'.code) state = 3
                    else if (state == 3 && b == '\n'.code) break
                    else if (b == '\r'.code) state = 1
                    else state = 0
                }

                val headerStr = headerBytes.toString(Charsets.ISO_8859_1.name())
                val statusLine = headerStr.lineSequence().firstOrNull() ?: ""
                val statusCode = statusLine.split(" ").getOrNull(1)?.toIntOrNull() ?: 0
                if (statusCode !in 200..299) {
                    if (com.example.BuildConfig.DEBUG) {
                        android.util.Log.e("NetworkUtils", "downloadFile failed with HTTP status: $statusLine")
                    }
                    return@withContext null
                }

                // EXT-R15: atomic download — write to .tmp first, rename only when complete
                val tempFile = java.io.File(downloadDir, "${safeFileName}.tmp_${System.currentTimeMillis()}")
                try {
                    java.io.FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(32768)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                        }
                        output.flush()
                    }

                    if (tempFile.exists() && tempFile.length() > 0) {
                        if (file.exists()) file.delete()
                        if (tempFile.renameTo(file)) {
                            return@withContext file.absolutePath
                        } else {
                            tempFile.copyTo(file, overwrite = true)
                            tempFile.delete()
                            return@withContext file.absolutePath
                        }
                    }
                } finally {
                    if (tempFile.exists()) tempFile.delete()
                }
            }
        } catch (e: Exception) {
            if (com.example.BuildConfig.DEBUG) {
                android.util.Log.e("NetworkUtils", "downloadFile error: ${e.message}", e)
            }
        }
        return@withContext null
    }
}
