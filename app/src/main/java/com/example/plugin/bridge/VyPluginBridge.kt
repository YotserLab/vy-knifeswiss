package com.example.plugin.bridge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import com.example.R
import com.example.Res

class VyPluginBridge(
    private val context: Context,
    private val pluginId: String,
    private val webView: WebView,
    private val onClose: () -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences("plugin_storage_$pluginId", Context.MODE_PRIVATE)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun runJs(js: String) {
        mainHandler.post {
            webView.evaluateJavascript(js, null)
        }
    }

    private fun sendCallback(callbackId: String, success: Boolean, dataJson: String) {
        val escapedData = dataJson.replace("'", "\\'")
        val script = "if (window.VYKnife && window.VYKnife._handleCallback) { window.VYKnife._handleCallback('$callbackId', $success, '$escapedData'); }"
        runJs(script)
    }

    @JavascriptInterface
    fun getPluginInfo(): String {
        return JSONObject().apply {
            put("pluginId", pluginId)
            put("appVersion", "1.1.9")
            put("os", Res.get(R.string.android_x_api_x, (Build.VERSION.RELEASE), (Build.VERSION.SDK_INT)))
            put("device", Res.get(R.string.x_x_4, (Build.MANUFACTURER), (Build.MODEL)))
        }.toString()
    }

    @JavascriptInterface
    fun showToast(message: String) {
        if (message.isBlank()) return
        mainHandler.post {
            Toast.makeText(context, message.take(200), Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface
    fun copyToClipboard(text: String) {
        mainHandler.post {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText(Res.get(R.string.vyknifeswiss_plugin), text)
            clipboard?.setPrimaryClip(clip)
            Toast.makeText(context, Res.get(R.string.copiado_para_a_area_de_transferencia), Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface
    fun close() {
        mainHandler.post {
            onClose()
        }
    }

    @JavascriptInterface
    fun setStorage(key: String, value: String) {
        if (key.isBlank() || key.length > 128) return
        // Prevent unbounded storage injection (max 512KB per key)
        if (value.length > 512 * 1024) return
        prefs.edit().putString(key, value).apply()
    }

    @JavascriptInterface
    fun getStorage(key: String): String {
        return prefs.getString(key, "") ?: ""
    }

    @JavascriptInterface
    fun removeStorage(key: String) {
        prefs.edit().remove(key).apply()
    }

    @JavascriptInterface
    fun ping(host: String, timeoutMs: Int, callbackId: String) {
        Thread {
            try {
                val cleanHost = host.trim().replace(Regex("[^a-zA-Z0-9.\\-_]"), "")
                if (cleanHost.isEmpty()) {
                    sendCallback(callbackId, false, JSONObject().put("error", Res.get(R.string.host_invalido)).toString())
                    return@Thread
                }
                val startTime = System.currentTimeMillis()
                val address = InetAddress.getByName(cleanHost)
                val reachable = address.isReachable(timeoutMs.coerceIn(500, 10000))
                val timeTaken = System.currentTimeMillis() - startTime

                val result = JSONObject().apply {
                    put("host", cleanHost)
                    put("ip", address.hostAddress)
                    put("reachable", reachable)
                    put("timeMs", timeTaken)
                }
                sendCallback(callbackId, reachable, result.toString())
            } catch (e: Exception) {
                sendCallback(callbackId, false, JSONObject().put("error", e.message ?: Res.get(R.string.falha_no_ping)).toString())
            }
        }.start()
    }

    @JavascriptInterface
    fun checkPort(host: String, port: Int, timeoutMs: Int, callbackId: String) {
        Thread {
            try {
                val cleanHost = host.trim().replace(Regex("[^a-zA-Z0-9.\\-_]"), "")
                if (cleanHost.isEmpty() || port !in 1..65535) {
                    sendCallback(callbackId, false, JSONObject().put("error", Res.get(R.string.parametros_invalidos)).toString())
                    return@Thread
                }
                val socket = Socket()
                val startTime = System.currentTimeMillis()
                socket.connect(InetSocketAddress(cleanHost, port), timeoutMs.coerceIn(500, 5000))
                val elapsed = System.currentTimeMillis() - startTime
                socket.close()

                val result = JSONObject().apply {
                    put("host", cleanHost)
                    put("port", port)
                    put("open", true)
                    put("timeMs", elapsed)
                }
                sendCallback(callbackId, true, result.toString())
            } catch (e: Exception) {
                val result = JSONObject().apply {
                    put("host", host)
                    put("port", port)
                    put("open", false)
                    put("error", e.message ?: Res.get(R.string.fechada_inalcancavel))
                }
                sendCallback(callbackId, false, result.toString())
            }
        }.start()
    }

    @JavascriptInterface
    fun httpFetch(urlStr: String, method: String, headersJson: String, bodyStr: String, callbackId: String) {
        Thread {
            try {
                val uri = android.net.Uri.parse(urlStr)
                if (uri.scheme != "http" && uri.scheme != "https") {
                    sendCallback(callbackId, false, JSONObject().put("error", Res.get(R.string.esquema_invalido_apenas_http_https)).toString())
                    return@Thread
                }

                val reqBuilder = Request.Builder().url(urlStr)
                if (headersJson.isNotBlank()) {
                    try {
                        val hObj = JSONObject(headersJson)
                        val keys = hObj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            reqBuilder.addHeader(k, hObj.optString(k, ""))
                        }
                    } catch (_: Exception) {}
                }

                when (method.uppercase()) {
                    "POST" -> reqBuilder.post(bodyStr.toRequestBody("application/json".toMediaTypeOrNull()))
                    "PUT" -> reqBuilder.put(bodyStr.toRequestBody("application/json".toMediaTypeOrNull()))
                    "DELETE" -> reqBuilder.delete()
                    else -> reqBuilder.get()
                }

                httpClient.newCall(reqBuilder.build()).execute().use { response ->
                    val respBody = response.body?.string() ?: ""
                    val result = JSONObject().apply {
                        put("statusCode", response.code)
                        put("isSuccessful", response.isSuccessful)
                        put("body", respBody)
                    }
                    sendCallback(callbackId, response.isSuccessful, result.toString())
                }
            } catch (e: Exception) {
                sendCallback(callbackId, false, JSONObject().put("error", e.message ?: Res.get(R.string.erro_de_conexao)).toString())
            }
        }.start()
    }
}
