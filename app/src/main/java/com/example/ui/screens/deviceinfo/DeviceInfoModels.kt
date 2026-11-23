package com.example.ui.screens.deviceinfo

import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.ConsumerIrManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import androidx.annotation.DrawableRes
import com.example.R
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.Res

// ==========================================
// DATA MODELS
// ==========================================

data class SocInfo(
    val vendorName: String,
    val modelName: String,
    @DrawableRes val logoRes: Int?,
    val architecture: String,
    val abiList: String,
    val coresCount: Int,
    val governor: String,
    val gpuVendor: String,
    val gpuRenderer: String,
    val openGlVersion: String,
    val vulkanSupported: Boolean
)

data class CoreFrequencyInfo(
    val coreIndex: Int,
    val currentMhz: Int,
    val minMhz: Int,
    val maxMhz: Int,
    val isOnline: Boolean
)

data class BatteryTelemetry(
    val level: Int,
    val status: String,
    val health: String,
    val technology: String,
    val plugged: String,
    val voltageMv: Int,
    val temperatureC: Float,
    val currentNowMa: Int,
    val powerWatts: Float,
    val capacityMah: Int
)

data class NetworkTelemetry(
    val isConnected: Boolean,
    val connectionType: String,
    val ipv4: String,
    val ipv6: String,
    val subnetMask: String,
    val gateway: String,
    val dns1: String,
    val dns2: String,
    val ssid: String,
    val bssid: String,
    val signalDbm: Int,
    val linkSpeedMbps: Int,
    val frequencyMhz: Int,
    val channel: Int,
    val wifiBand: String,
    val carrier: String,
    val mobileNetworkType: String,
    val isRoaming: Boolean
)

data class ConnectivityInfo(
    val bluetoothSupported: Boolean,
    val bleSupported: Boolean,
    val bleAudioSupported: Boolean,
    val multipleAdvertisementSupported: Boolean,
    val wifiDirectSupported: Boolean,
    val wifiAwareSupported: Boolean,
    val nfcSupported: Boolean,
    val nfcHceSupported: Boolean,
    val uwbSupported: Boolean,
    val usbHostSupported: Boolean,
    val usbAccessorySupported: Boolean,
    val irBlasterSupported: Boolean
)

data class DisplayInfo(
    val widthPixels: Int,
    val heightPixels: Int,
    val densityDpi: Int,
    val densityScale: Float,
    val refreshRateHz: Int,
    val physicalSizeInches: Double,
    val aspectRatio: String,
    val hdrCapabilities: String,
    val isHdrSupported: Boolean,
    val brightnessPercent: Int,
    val isAutoBrightness: Boolean,
    val isNightMode: Boolean
)

data class MemoryTelemetry(
    val ramTotalGb: Double,
    val ramUsedGb: Double,
    val ramFreeGb: Double,
    val ramUsedPercent: Int,
    val isLowMemory: Boolean,
    val thresholdGb: Double,
    val internalTotalGb: Double,
    val internalUsedGb: Double,
    val internalFreeGb: Double,
    val internalUsedPercent: Int,
    val externalMounted: Boolean,
    val externalTotalGb: Double,
    val externalUsedGb: Double,
    val externalFreeGb: Double,
    val externalUsedPercent: Int
)

data class CameraDetailedSpec(
    val id: String,
    val facing: String,
    val megapixels: String,
    val resolution: String,
    val focalLengths: String,
    val aperture: String,
    val hasFlash: Boolean,
    val afModes: String,
    val opticalStabilization: Boolean,
    val videoStabilization: Boolean,
    val isoRange: String
)

data class ThermalZone(
    val zoneIndex: Int,
    val type: String,
    val tempCelsius: Float
)

data class AppItem(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val isSystemApp: Boolean,
    val installTimeFormatted: String
)

// DEV-R05
enum class HardwareTestId(val title: String, val description: String) {
    DISPLAY_DEAD_PIXELS(Res.get(R.string.tela_dead_pixels), Res.get(R.string.ciclo_de_cores_puras_em_tela_cheia)),
    MULTI_TOUCH("Multitoque", Res.get(R.string.identificacao_de_toques_simultaneos)),
    FLASHLIGHT(Res.get(R.string.lanterna_flash), Res.get(R.string.ligar_e_desligar_o_led_traseiro)),
    SPEAKER(Res.get(R.string.alto_falante), Res.get(R.string.teste_sonoro_estereo_mono_em_frequencia)),
    EARPIECE(Res.get(R.string.fone_auricular), Res.get(R.string.saida_de_som_para_chamadas)),
    MICROPHONE("Microfone", Res.get(R.string.captacao_de_audio_em_tempo_real)),
    PROXIMITY_SENSOR(Res.get(R.string.sensor_de_proximidade), Res.get(R.string.deteccao_de_presenca_proxima)),
    LIGHT_SENSOR(Res.get(R.string.sensor_de_luminosidade), Res.get(R.string.medicao_de_lux_ambiental)),
    ACCELEROMETER(Res.get(R.string.acelerometro), Res.get(R.string.esfera_de_gravidade_nos_eixos_x_y)),
    VIBRATION(Res.get(R.string.vibracao), Res.get(R.string.padrao_de_vibracao_do_motor_tatil))
}

enum class TestStatus {
    UNTESTED,
    PASSED,
    FAILED
}

// ==========================================
// SYSTEM EXTRACTION UTILITIES
// ==========================================

// DEV-R01
object DeviceInfoExtractor {

    fun detectSoc(context: Context): SocInfo {
        var hardware = Build.HARDWARE.lowercase(Locale.ROOT)
        var board = Build.BOARD.lowercase(Locale.ROOT)
        var socManufacturer = ""
        var socModel = ""

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                socManufacturer = Build.SOC_MANUFACTURER
                socModel = Build.SOC_MODEL
            } catch (_: Exception) {}
        }

        var cpuInfoHardware = ""
        try {
            val file = File("/proc/cpuinfo")
            if (file.exists() && file.canRead()) {
                BufferedReader(FileReader(file)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val trimmed = line?.trim() ?: ""
                        if (trimmed.startsWith("Hardware", ignoreCase = true)) {
                            cpuInfoHardware = trimmed.substringAfter(":").trim()
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        val combined = Res.get(R.string.x_x_x_x_x, hardware, board, socManufacturer, socModel, cpuInfoHardware).lowercase(Locale.ROOT)

        val (vendor, model, logo) = when {
            combined.contains("qcom") || combined.contains("qualcomm") || combined.contains("snapdragon") || combined.contains("sm") || combined.contains("sdm") -> {
                val displayName = if (socModel.isNotBlank()) Res.get(R.string.qualcomm_snapdragon_x, socModel) else Res.get(R.string.qualcomm_snapdragon_x_2, hardware)
                Triple("Qualcomm", displayName, R.drawable.snapdragon)
            }
            combined.contains("mt") || combined.contains("mediatek") || combined.contains("helio") || combined.contains("dimensity") -> {
                val displayName = if (socModel.isNotBlank()) Res.get(R.string.mediatek_x, socModel) else Res.get(R.string.mediatek_x_2, hardware)
                Triple(Res.get(R.string.mediatek), displayName, R.drawable.mediatek)
            }
            combined.contains("exynos") || combined.contains("samsung") || combined.contains("universal") -> {
                val displayName = if (socModel.isNotBlank()) Res.get(R.string.samsung_exynos_x, socModel) else Res.get(R.string.samsung_exynos_x_2, hardware)
                Triple("Samsung", displayName, R.drawable.exynos)
            }
            combined.contains("kirin") || combined.contains("hisilicon") -> {
                val displayName = if (socModel.isNotBlank()) Res.get(R.string.hisilicon_kirin_x, socModel) else Res.get(R.string.hisilicon_kirin_x_2, hardware)
                Triple(Res.get(R.string.hisilicon), displayName, R.drawable.kirin)
            }
            combined.contains("intel") || combined.contains("x86") || combined.contains("atom") -> {
                Triple("Intel", Res.get(R.string.intel_x86_atom_x, hardware), R.drawable.intel)
            }
            combined.contains("allwinner") || combined.contains("sun") -> {
                Triple("Allwinner", Res.get(R.string.allwinner_technology_x, hardware), R.drawable.allwinner)
            }
            combined.contains("unisoc") || combined.contains("spreadtrum") || combined.contains("sc") || combined.contains("ums") -> {
                val displayName = if (socModel.isNotBlank()) Res.get(R.string.unisoc_x, socModel) else Res.get(R.string.unisoc_x_2, hardware)
                Triple("UNISOC", displayName, R.drawable.unisoc)
            }
            else -> {
                val fallbackModel = if (Build.HARDWARE.isNotBlank()) Build.HARDWARE else Build.BOARD
                Triple(Res.get(R.string.processador_arm), fallbackModel.uppercase(Locale.ROOT), null)
            }
        }

        val cores = Runtime.getRuntime().availableProcessors()
        var gov = Res.get(R.string.desconhecido)
        try {
            val f = File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor")
            if (f.exists() && f.canRead()) {
                gov = f.readText().trim()
            }
        } catch (_: Exception) {}

        val pm = context.packageManager
        val hasVulkan = pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
        val glEsVersion = (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
            ?.deviceConfigurationInfo?.glEsVersion ?: Res.get(R.string.opengl_es_3_2)

        return SocInfo(
            vendorName = vendor,
            modelName = model,
            logoRes = logo,
            architecture = System.getProperty("os.arch") ?: "aarch64",
            abiList = Build.SUPPORTED_ABIS.joinToString(", "),
            coresCount = cores,
            governor = gov,
            gpuVendor = if (vendor == "Qualcomm") Res.get(R.string.qualcomm_technologies_inc) else if (vendor == "Samsung") Res.get(R.string.arm_amd) else Res.get(R.string.arm_imagination),
            gpuRenderer = if (vendor == "Qualcomm") Res.get(R.string.qualcomm_adreno) else if (vendor == "Samsung") Res.get(R.string.arm_mali_xclipse) else Res.get(R.string.arm_mali_graphics),
            openGlVersion = glEsVersion,
            vulkanSupported = hasVulkan
        )
    }

    fun getCoresInfo(): List<CoreFrequencyInfo> {
        val count = Runtime.getRuntime().availableProcessors()
        val list = mutableListOf<CoreFrequencyInfo>()

        for (i in 0 until count) {
            var cur = 0
            var min = 0
            var max = 0
            var online = true

            try {
                val onlineFile = File("/sys/devices/system/cpu/cpu$i/online")
                if (onlineFile.exists() && onlineFile.canRead()) {
                    online = onlineFile.readText().trim() == "1"
                }
            } catch (_: Exception) {}

            try {
                val curFile = File("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq") // DEV-R03
                if (curFile.exists() && curFile.canRead()) {
                    cur = curFile.readText().trim().toInt() / 1000
                }
            } catch (_: Exception) {}

            try {
                val minFile = File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_min_freq")
                if (minFile.exists() && minFile.canRead()) {
                    min = minFile.readText().trim().toInt() / 1000
                }
                val maxFile = File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
                if (maxFile.exists() && maxFile.canRead()) {
                    max = maxFile.readText().trim().toInt() / 1000
                }
            } catch (_: Exception) {}

            if (max == 0) max = 2400
            if (min == 0) min = 300

            list.add(
                CoreFrequencyInfo(
                    coreIndex = i,
                    currentMhz = cur,
                    minMhz = min,
                    maxMhz = max,
                    isOnline = online
                )
            )
        }
        return list
    }

    fun getBatteryTelemetry(context: Context): BatteryTelemetry {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val intent = context.registerReceiver(null, filter)

        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) ((level / scale.toFloat()) * 100).toInt() else 0

        val statusInt = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val status = when (statusInt) {
            BatteryManager.BATTERY_STATUS_CHARGING -> Res.get(R.string.carregando)
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Descarregando"
            BatteryManager.BATTERY_STATUS_FULL -> Res.get(R.string.cheia_completa)
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> Res.get(R.string.nao_carregando)
            else -> Res.get(R.string.desconhecido)
        }

        val healthInt = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val health = when (healthInt) {
            BatteryManager.BATTERY_HEALTH_GOOD -> Res.get(R.string.boa_saudavel)
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Superaquecida"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Esgotada"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> Res.get(R.string.sobretensao)
            BatteryManager.BATTERY_HEALTH_COLD -> "Fria"
            else -> "Normal"
        }

        val tech = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: Res.get(R.string.li_ion)
        val pluggedInt = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val plugged = when (pluggedInt) {
            BatteryManager.BATTERY_PLUGGED_AC -> Res.get(R.string.carregador_ac_tomada)
            BatteryManager.BATTERY_PLUGGED_USB -> Res.get(R.string.porta_usb)
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> Res.get(R.string.sem_fio_wireless)
            else -> "Desconectado"
        }

        val voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val tempRaw = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempC = tempRaw / 10.0f

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val curNow = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
        val curMa = if (Math.abs(curNow) > 10000) curNow / 1000 else curNow

        val powerWatts = if (voltageMv > 0 && curMa != 0) {
            (voltageMv / 1000.0f) * (Math.abs(curMa) / 1000.0f)
        } else 0f

        var capacity = 0
        try {
            val chargeCounter = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: 0
            if (chargeCounter > 0 && pct > 0) {
                val fullCapacityUah = (chargeCounter.toDouble() / (pct / 100.0)).toLong()
                capacity = (fullCapacityUah / 1000).toInt()
            }
        } catch (_: Exception) {}

        if (capacity <= 0) {
            try {
                val powerProfileClass = Class.forName("com.android.internal.os.PowerProfile")
                val powerProfile = powerProfileClass.getConstructor(Context::class.java).newInstance(context)
                val cap = powerProfileClass.getMethod("getBatteryCapacity").invoke(powerProfile) as? Double
                if (cap != null && cap > 0) capacity = cap.toInt()
            } catch (_: Exception) {}
        }

        return BatteryTelemetry(
            level = pct,
            status = status,
            health = health,
            technology = tech,
            plugged = plugged,
            voltageMv = voltageMv,
            temperatureC = tempC,
            currentNowMa = curMa,
            powerWatts = powerWatts,
            capacityMah = capacity
        )
    }

    fun getNetworkTelemetry(context: Context): NetworkTelemetry {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        val activeNet = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(activeNet)
        val linkProps = cm.getLinkProperties(activeNet)

        val isConnected = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val connType = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> Res.get(R.string.wi_fi)
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> Res.get(R.string.dados_moveis_celular)
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) == true -> Res.get(R.string.bluetooth_tethering)
            else -> Res.get(R.string.desconectado_offline)
        }

        var ip4 = Res.get(R.string.indisponivel)
        var ip6 = Res.get(R.string.indisponivel)
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                if (intf.isLoopback || !intf.isUp) continue
                val addresses = intf.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress) {
                        if (addr is Inet4Address && ip4 == Res.get(R.string.indisponivel)) {
                            ip4 = addr.hostAddress ?: Res.get(R.string.indisponivel)
                        } else if (addr is Inet6Address && ip6 == Res.get(R.string.indisponivel)) {
                            val hex = addr.hostAddress ?: ""
                            ip6 = hex.substringBefore("%")
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        var gateway = Res.get(R.string.indisponivel)
        var subnet = Res.get(R.string.indisponivel)
        var dns1 = Res.get(R.string.indisponivel)
        var dns2 = Res.get(R.string.indisponivel)

        linkProps?.let { lp ->
            val routes = lp.routes
            for (route in routes) {
                if (route.isDefaultRoute) {
                    gateway = route.gateway?.hostAddress ?: Res.get(R.string.indisponivel)
                    break
                }
            }
            val dnsList = lp.dnsServers
            if (dnsList.isNotEmpty()) dns1 = dnsList[0].hostAddress ?: Res.get(R.string.indisponivel)
            if (dnsList.size > 1) dns2 = dnsList[1].hostAddress ?: Res.get(R.string.indisponivel)

            val linkAddresses = lp.linkAddresses
            for (la in linkAddresses) {
                if (la.address is Inet4Address) {
                    val prefix = la.prefixLength
                    subnet = prefixToSubnetMask(prefix)
                    break
                }
            }
        }

        val wifiInfo: WifiInfo? = wm.connectionInfo
        val rawSsid = wifiInfo?.ssid?.replace("\"", "") ?: ""
        val ssid = if (rawSsid.isBlank() || rawSsid == "<unknown ssid>") Res.get(R.string.rede_wi_fi_conectada) else rawSsid
        val bssid = wifiInfo?.bssid ?: Res.get(R.string.indisponivel)
        val rssi = wifiInfo?.rssi ?: 0
        val linkSpeed = wifiInfo?.linkSpeed ?: 0
        val freq = wifiInfo?.frequency ?: 0

        val (band, channel) = calculateWifiBandAndChannel(freq)

        val carrier = tm.networkOperatorName.ifBlank { tm.simOperatorName.ifBlank { Res.get(R.string.sem_operadora) } }
        val roaming = tm.isNetworkRoaming

        val mobileType = try {
            val netType = tm.dataNetworkType
            when (netType) {
                TelephonyManager.NETWORK_TYPE_NR -> Res.get(R.string.n_5g_nr)
                TelephonyManager.NETWORK_TYPE_LTE -> Res.get(R.string.n_4g_lte)
                TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_HSPA -> Res.get(R.string.hspa_3_5g)
                TelephonyManager.NETWORK_TYPE_UMTS -> Res.get(R.string.umts_3g)
                TelephonyManager.NETWORK_TYPE_EDGE -> Res.get(R.string.edge_2g)
                TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
                else -> Res.get(R.string.rede_de_dados_moveis)
            }
        } catch (_: SecurityException) {
            Res.get(R.string.n_4g_5g_sem_permissao_telephony)
        }

        return NetworkTelemetry(
            isConnected = isConnected,
            connectionType = connType,
            ipv4 = ip4,
            ipv6 = ip6,
            subnetMask = subnet,
            gateway = gateway,
            dns1 = dns1,
            dns2 = dns2,
            ssid = ssid,
            bssid = bssid,
            signalDbm = rssi,
            linkSpeedMbps = linkSpeed,
            frequencyMhz = freq,
            channel = channel,
            wifiBand = band,
            carrier = carrier,
            mobileNetworkType = mobileType,
            isRoaming = roaming
        )
    }

    private fun prefixToSubnetMask(prefix: Int): String {
        var shift = 0xffffffffL shl (32 - prefix)
        val octet1 = (shift ushr 24) and 0xff
        val octet2 = (shift ushr 16) and 0xff
        val octet3 = (shift ushr 8) and 0xff
        val octet4 = shift and 0xff
        return Res.get(R.string.xxxx, octet1, octet2, octet3, octet4)
    }

    private fun calculateWifiBandAndChannel(freq: Int): Pair<String, Int> {
        return when {
            freq in 2412..2484 -> {
                val ch = if (freq == 2484) 14 else ((freq - 2412) / 5) + 1
                Pair(Res.get(R.string.n_2_4_ghz), ch)
            }
            freq in 5170..5825 -> {
                val ch = ((freq - 5000) / 5)
                Pair(Res.get(R.string.n_5_ghz), ch)
            }
            freq in 5925..7125 -> {
                val ch = ((freq - 5950) / 5) + 1
                Pair(Res.get(R.string.n_6_ghz_wi_fi_6e_7), ch)
            }
            else -> Pair(Res.get(R.string.n_a), 0)
        }
    }

    fun getConnectivityInfo(context: Context): ConnectivityInfo {
        val pm = context.packageManager
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val btAdapter: BluetoothAdapter? = bm?.adapter

        val hasBt = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
        val hasBle = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        val hasBleAudio = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        } else false

        val multiAdv = btAdapter?.isMultipleAdvertisementSupported ?: false
        val p2p = pm.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)
        val aware = pm.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)
        val nfc = pm.hasSystemFeature(PackageManager.FEATURE_NFC)
        val nfcHce = pm.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)
        val uwb = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pm.hasSystemFeature(PackageManager.FEATURE_UWB)
        } else false
        val usbHost = pm.hasSystemFeature(PackageManager.FEATURE_USB_HOST)
        val usbAcc = pm.hasSystemFeature(PackageManager.FEATURE_USB_ACCESSORY)

        val irManager = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        val hasIr = irManager?.hasIrEmitter() ?: false

        return ConnectivityInfo(
            bluetoothSupported = hasBt,
            bleSupported = hasBle,
            bleAudioSupported = hasBleAudio,
            multipleAdvertisementSupported = multiAdv,
            wifiDirectSupported = p2p,
            wifiAwareSupported = aware,
            nfcSupported = nfc,
            nfcHceSupported = nfcHce,
            uwbSupported = uwb,
            usbHostSupported = usbHost,
            usbAccessorySupported = usbAcc,
            irBlasterSupported = hasIr
        )
    }

    fun getDisplayInfo(context: Context): DisplayInfo {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        val display = wm.defaultDisplay
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)

        @Suppress("DEPRECATION")
        val refresh = display.refreshRate.toInt()

        val widthInches = metrics.widthPixels.toDouble() / metrics.xdpi
        val heightInches = metrics.heightPixels.toDouble() / metrics.ydpi
        val diagonal = Math.sqrt(widthInches * widthInches + heightInches * heightInches)

        val gcdVal = gcd(metrics.widthPixels, metrics.heightPixels)
        val ratio = Res.get(R.string.x_gcdval_x_gcdval, (metrics.widthPixels / gcdVal), (metrics.heightPixels / gcdVal))

        var hdrCap = Res.get(R.string.sdr_padrao)
        var isHdr = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val hdr = display.hdrCapabilities
            if (hdr != null && hdr.supportedHdrTypes.isNotEmpty()) {
                isHdr = true
                val types = hdr.supportedHdrTypes.map {
                    when (it) {
                        Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
                        Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> Res.get(R.string.hdr10)
                        Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> Res.get(R.string.dolby_vision)
                        Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
                        else -> "HDR"
                    }
                }
                hdrCap = types.joinToString(", ")
            }
        }

        var brightness = 50
        var autoBrightness = false
        try {
            val raw = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            brightness = ((raw / 255.0f) * 100).toInt()
            val mode = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
            autoBrightness = mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        } catch (_: Exception) {}

        val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        return DisplayInfo(
            widthPixels = metrics.widthPixels,
            heightPixels = metrics.heightPixels,
            densityDpi = metrics.densityDpi,
            densityScale = metrics.density,
            refreshRateHz = refresh,
            physicalSizeInches = diagonal,
            aspectRatio = ratio,
            hdrCapabilities = hdrCap,
            isHdrSupported = isHdr,
            brightnessPercent = brightness,
            isAutoBrightness = autoBrightness,
            isNightMode = isNight
        )
    }

    private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

    fun getMemoryTelemetry(context: Context): MemoryTelemetry {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)

        val ramTotal = mi.totalMem / (1024.0 * 1024.0 * 1024.0)
        val ramFree = mi.availMem / (1024.0 * 1024.0 * 1024.0)
        val ramUsed = ramTotal - ramFree
        val ramPct = if (ramTotal > 0) ((ramUsed / ramTotal) * 100).toInt() else 0
        val thresh = mi.threshold / (1024.0 * 1024.0 * 1024.0)

        val intPath = Environment.getDataDirectory()
        val intStat = StatFs(intPath.path)
        val intTotal = (intStat.blockCountLong * intStat.blockSizeLong) / (1024.0 * 1024.0 * 1024.0)
        val intFree = (intStat.availableBlocksLong * intStat.blockSizeLong) / (1024.0 * 1024.0 * 1024.0)
        val intUsed = intTotal - intFree
        val intPct = if (intTotal > 0) ((intUsed / intTotal) * 100).toInt() else 0

        var extMounted = false
        var extTotal = 0.0
        var extFree = 0.0
        var extUsed = 0.0
        var extPct = 0

        try {
            val state = Environment.getExternalStorageState()
            if (state == Environment.MEDIA_MOUNTED) {
                val extPath = Environment.getExternalStorageDirectory()
                val extStat = StatFs(extPath.path)
                extTotal = (extStat.blockCountLong * extStat.blockSizeLong) / (1024.0 * 1024.0 * 1024.0)
                extFree = (extStat.availableBlocksLong * extStat.blockSizeLong) / (1024.0 * 1024.0 * 1024.0)
                extUsed = extTotal - extFree
                extPct = if (extTotal > 0) ((extUsed / extTotal) * 100).toInt() else 0
                extMounted = extTotal > 0.1
            }
        } catch (_: Exception) {}

        return MemoryTelemetry(
            ramTotalGb = ramTotal,
            ramUsedGb = ramUsed,
            ramFreeGb = ramFree,
            ramUsedPercent = ramPct,
            isLowMemory = mi.lowMemory,
            thresholdGb = thresh,
            internalTotalGb = intTotal,
            internalUsedGb = intUsed,
            internalFreeGb = intFree,
            internalUsedPercent = intPct,
            externalMounted = extMounted,
            externalTotalGb = extTotal,
            externalUsedGb = extUsed,
            externalFreeGb = extFree,
            externalUsedPercent = extPct
        )
    }

    fun getCameraList(context: Context): List<CameraDetailedSpec> {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val list = mutableListOf<CameraDetailedSpec>()

        try {
            for (id in cm.cameraIdList) {
                val chars = cm.getCameraCharacteristics(id)
                val facingInt = chars.get(CameraCharacteristics.LENS_FACING)
                val facing = when (facingInt) {
                    CameraCharacteristics.LENS_FACING_FRONT -> "Frontal"
                    CameraCharacteristics.LENS_FACING_BACK -> Res.get(R.string.traseira_principal_auxiliar)
                    else -> "Externa"
                }

                val configs = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                var maxW = 0
                var maxH = 0
                configs?.getOutputSizes(android.graphics.ImageFormat.JPEG)?.forEach { size ->
                    if (size.width * size.height > maxW * maxH) {
                        maxW = size.width
                        maxH = size.height
                    }
                }

                val mp = if (maxW > 0 && maxH > 0) {
                    String.format(Locale.US, "%.1f MP", (maxW * maxH) / 1000000.0)
                } else Res.get(R.string.n_a)

                val res = if (maxW > 0 && maxH > 0) Res.get(R.string.x_x_x_px, maxW, maxH) else Res.get(R.string.indisponivel)

                val focals = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                val focalStr = focals?.joinToString(", ") { String.format(Locale.US, "%.2f mm", it) } ?: Res.get(R.string.padrao_2)

                val apertures = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
                val apStr = apertures?.joinToString(", ") { String.format(Locale.US, "f/%.2f", it) } ?: "f/1.8"

                val flash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false

                val afInts = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
                val hasAf = afInts.any { it == CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_PICTURE || it == CameraCharacteristics.CONTROL_AF_MODE_AUTO }

                val oisModes = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION) ?: intArrayOf()
                val hasOis = oisModes.contains(CameraCharacteristics.LENS_OPTICAL_STABILIZATION_MODE_ON)

                val videoStab = chars.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES) ?: intArrayOf()
                val hasEis = videoStab.contains(CameraCharacteristics.CONTROL_VIDEO_STABILIZATION_MODE_ON)

                val isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
                val isoStr = if (isoRange != null) Res.get(R.string.x_x_33, (isoRange.lower), (isoRange.upper)) else "100 - 3200"

                list.add(
                    CameraDetailedSpec(
                        id = id,
                        facing = facing,
                        megapixels = mp,
                        resolution = res,
                        focalLengths = focalStr,
                        aperture = apStr,
                        hasFlash = flash,
                        afModes = if (hasAf) Res.get(R.string.automatico_continuo) else Res.get(R.string.foco_fixo),
                        opticalStabilization = hasOis,
                        videoStabilization = hasEis,
                        isoRange = isoStr
                    )
                )
            }
        } catch (_: Exception) {}

        return list
    }

    fun getThermalZones(): List<ThermalZone> {
        val list = mutableListOf<ThermalZone>()
        val baseDir = File("/sys/class/thermal")
        if (baseDir.exists() && baseDir.isDirectory) {
            val zones = baseDir.listFiles { _, name -> name.startsWith("thermal_zone") } ?: emptyArray()
            zones.sortedBy { it.name.substringAfter("thermal_zone").toIntOrNull() ?: 0 }.forEachIndexed { idx, dir ->
                try {
                    val typeFile = File(dir, "type")
                    val tempFile = File(dir, "temp")
                    if (typeFile.exists() && tempFile.exists()) {
                        val type = typeFile.readText().trim()
                        val rawTemp = tempFile.readText().trim().toFloatOrNull() ?: 0f
                        val celsius = if (rawTemp > 1000) rawTemp / 1000f else rawTemp
                        if (celsius in 0f..120f) {
                            list.add(ThermalZone(idx, type, celsius))
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        return list
    }

    fun getInstalledApps(context: Context): List<AppItem> {
        val pm = context.packageManager
        val list = mutableListOf<AppItem>()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        try {
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in packages) {
                val isSys = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val label = pm.getApplicationLabel(app).toString()
                var ver = "1.0"
                var installDate = ""
                try {
                    val pkgInfo = pm.getPackageInfo(app.packageName, 0)
                    ver = pkgInfo.versionName ?: "1.0"
                    installDate = dateFormat.format(Date(pkgInfo.firstInstallTime))
                } catch (_: Exception) {}

                list.add(
                    AppItem(
                        packageName = app.packageName,
                        appName = label,
                        versionName = ver,
                        isSystemApp = isSys,
                        installTimeFormatted = installDate
                    )
                )
            }
        } catch (_: Exception) {}

        return list.sortedBy { it.appName.lowercase(Locale.getDefault()) }
    }

    fun getSystemDetails(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        map[Res.get(R.string.versao_do_android)] = Res.get(R.string.android_x, (Build.VERSION.RELEASE))
        map[Res.get(R.string.nivel_de_api_sdk)] = Res.get(R.string.x_35, (Build.VERSION.SDK_INT))
        map[Res.get(R.string.patch_de_seguranca_2)] = Build.VERSION.SECURITY_PATCH ?: Res.get(R.string.indisponivel)
        map[Res.get(R.string.id_de_build_display)] = Build.DISPLAY
        map["Bootloader"] = Build.BOOTLOADER
        map[Res.get(R.string.baseband_radio)] = try { Build.getRadioVersion() ?: Res.get(R.string.indisponivel) } catch (_: Exception) { Res.get(R.string.indisponivel) }
        map["Fingerprint"] = Build.FINGERPRINT
        map["Kernel"] = System.getProperty("os.version") ?: "Linux"
        map[Res.get(R.string.java_vm)] = "${System.getProperty("java.vm.name")} ${System.getProperty("java.vm.version")}"
        map["Uptime"] = getFormattedUptime()
        map[Res.get(R.string.selinux)] = getSELinuxStatus()
        map[Res.get(R.string.treble_suportado)] = getSystemProperty("ro.treble.enabled", "true")
        map[Res.get(R.string.a_b_seamless_updates)] = getSystemProperty("ro.build.ab_update", "false")
        map[Res.get(R.string.acesso_root)] = if (checkIsRooted()) "Detectado" else Res.get(R.string.nao_detectado_seguro)
        return map
    }

    private fun getSELinuxStatus(): String {
        return try {
            val process = Runtime.getRuntime().exec("getenforce")
            process.inputStream.bufferedReader().use { it.readText().trim() }
        } catch (_: Exception) {
            "Enforcing"
        }
    }

    private fun getSystemProperty(key: String, default: String): String {
        return try {
            val c = Class.forName("android.os.SystemProperties")
            val get = c.getMethod("get", String::class.java, String::class.java)
            get.invoke(null, key, default) as String
        } catch (_: Exception) {
            default
        }
    }

    fun getFormattedUptime(): String {
        val uptimeMs = SystemClock.elapsedRealtime()
        val seconds = uptimeMs / 1000
        val hours = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return String.format(Locale.US, "%02dh %02dm %02ds", hours, mins, secs)
    }

    fun checkIsRooted(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        for (p in paths) {
            if (File(p).exists()) return true
        }
        return false
    }
}
