package com.example.utils.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs
import com.example.R
import com.example.Res

data class SpeedTestResult(
    val pingMs: Float,
    val downloadMbps: Float,
    val uploadMbps: Float,
    val jitterMs: Float
)

object SpeedTestUtils {

    suspend fun runFullSpeedTest(onProgress: (Float, String) -> Unit): SpeedTestResult = withContext(Dispatchers.IO) {
        var pingMs = 0f // RED-R08
        var jitterMs = 0f
        var downloadMbps = 0f
        var uploadMbps = 0f

        // 1. Latency & Jitter Check
        try {
            withContext(Dispatchers.Main) {
                onProgress(0.1f, Res.get(R.string.medindo_latencia))
            }
            val latencies = mutableListOf<Long>()
            for (i in 1..6) {
                val start = System.currentTimeMillis()
                try {
                    val url = URL("https://1.1.1.1")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 3000 // RED-R10
                    conn.readTimeout = 3000
                    conn.requestMethod = "HEAD"
                    conn.connect()
                    val code = conn.responseCode
                    val end = System.currentTimeMillis()
                    conn.disconnect()
                    if (code in 200..399) {
                        latencies.add(end - start)
                    }
                } catch (e: Exception) {
                    // Ignore transient error
                }
            }
            if (latencies.isNotEmpty()) {
                pingMs = latencies.average().toFloat()
                if (latencies.size > 1) {
                    var diffSum = 0f
                    for (i in 1 until latencies.size) {
                        diffSum += abs(latencies[i] - latencies[i - 1]).toFloat()
                    }
                    jitterMs = diffSum / (latencies.size - 1)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Download Speed Test
        try {
            withContext(Dispatchers.Main) {
                onProgress(0.3f, Res.get(R.string.testando_download))
            }
            val downloadUrl = URL("https://speed.cloudflare.com/__down?bytes=10000000")
            val conn = downloadUrl.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.connect()

            val startTime = System.currentTimeMillis()
            var bytesReadTotal = 0L
            if (conn.responseCode == 200) {
                conn.inputStream.use { input ->
                    val buffer = ByteArray(16384)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        bytesReadTotal += bytesRead
                        val elapsedSecs = (System.currentTimeMillis() - startTime) / 1000f
                        if (elapsedSecs > 0) {
                            val currentSpeed = (bytesReadTotal * 8f / 1_000_000f) / elapsedSecs
                            withContext(Dispatchers.Main) {
                                val prog = 0.3f + (bytesReadTotal.toFloat() / 10_000_000f) * 0.35f
                                onProgress(prog.coerceAtMost(0.65f), "Download: ${String.format(java.util.Locale.US, "%.1f", currentSpeed)} Mbps")
                            }
                        }
                    }
                }
            }
            val totalTimeSec = (System.currentTimeMillis() - startTime) / 1000f
            if (totalTimeSec > 0 && bytesReadTotal > 0) {
                downloadMbps = (bytesReadTotal * 8f / 1_000_000f) / totalTimeSec
            }
            conn.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Upload Speed Test
        try {
            withContext(Dispatchers.Main) {
                onProgress(0.7f, Res.get(R.string.testando_upload))
            }
            val uploadUrl = URL("https://speed.cloudflare.com/__up")
            val conn = uploadUrl.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.setRequestProperty("Content-Type", "application/octet-stream")

            val payloadSize = 2 * 1024 * 1024 // RED-R09 2MB upload test payload
            val buffer = ByteArray(16384)
            val startTime = System.currentTimeMillis()
            var bytesWrittenTotal = 0L

            conn.outputStream.use { out ->
                while (bytesWrittenTotal < payloadSize) {
                    val chunk = minOf(buffer.size, payloadSize - bytesWrittenTotal.toInt())
                    out.write(buffer, 0, chunk)
                    bytesWrittenTotal += chunk
                    val elapsedSecs = (System.currentTimeMillis() - startTime) / 1000f
                    if (elapsedSecs > 0) {
                        val currentSpeed = (bytesWrittenTotal * 8f / 1_000_000f) / elapsedSecs
                        withContext(Dispatchers.Main) {
                            val prog = 0.7f + (bytesWrittenTotal.toFloat() / payloadSize.toFloat()) * 0.25f
                            onProgress(prog.coerceAtMost(0.95f), "Upload: ${String.format(java.util.Locale.US, "%.1f", currentSpeed)} Mbps")
                        }
                    }
                }
                out.flush()
            }
            val code = conn.responseCode
            val totalTimeSec = (System.currentTimeMillis() - startTime) / 1000f
            if (code in 200..399 && totalTimeSec > 0 && bytesWrittenTotal > 0) {
                uploadMbps = (bytesWrittenTotal * 8f / 1_000_000f) / totalTimeSec
            }
            conn.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        withContext(Dispatchers.Main) {
            onProgress(1.0f, Res.get(R.string.concluido))
        }

        SpeedTestResult(pingMs, downloadMbps, uploadMbps, jitterMs)
    }

    suspend fun runSpeedTest(onProgress: (Float, String) -> Unit): Pair<Float, Float> {
        val result = runFullSpeedTest(onProgress)
        return Pair(result.downloadMbps, result.jitterMs)
    }
}
