package com.example.cast.discovery

import java.net.URI

data class SsdpResponse(
    val location: String, val st: String, val usn: String, val server: String
) {
    val host: String get() = runCatching { URI(location).host }.getOrNull() ?: ""
    val port: Int get() = runCatching { val p = URI(location).port; if (p > 0) p else 80 }.getOrDefault(80)
    val udnUuid: String get() = usn.substringBefore("::").removePrefix("uuid:")
}

object SsdpParser {
    fun parse(raw: String): SsdpResponse? {
        val headers = raw.lines().mapNotNull {
            val i = it.indexOf(':'); if (i < 0) null else it.substring(0, i).trim().lowercase() to it.substring(i + 1).trim()
        }.toMap()
        val location = headers["location"] ?: return null
        return SsdpResponse(
            location = location,
            st = headers["st"] ?: headers["nt"] ?: "",
            usn = headers["usn"] ?: "",
            server = headers["server"] ?: ""
        )
    }
}
