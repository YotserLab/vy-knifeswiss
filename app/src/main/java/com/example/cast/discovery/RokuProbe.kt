package com.example.cast.discovery

import com.example.cast.control.LanHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Direct Roku probe via ECP (`http://<ip>:8060/query/device-info`).
 *
 * Used for Roku devices that didn't answer SSDP, and for manually-added
 * IPs — a plain HTTP GET on port 8060 is the cheapest way to detect one.
 */
// MED-R07
object RokuProbe {

    suspend fun probe(ip: String): CastDevice? = withContext(Dispatchers.IO) {
        val resp = LanHttpClient.get("http://$ip:8060/query/device-info", 3000)
            ?: return@withContext null
        if (resp.code != 200) return@withContext null
        CastDevice(
            id = "roku-$ip",
            name = extractTag(resp.body, "friendly-device-name").ifEmpty { "Roku" },
            type = CastDeviceType.ROKU,
            ip = ip,
            port = 8060,
            manufacturer = "Roku",
            modelName = extractTag(resp.body, "model-name")
        )
    }

    internal fun extractTag(xml: String, tag: String): String =
        xml.substringAfter("<$tag>", "").substringBefore("</$tag>", "")
}
