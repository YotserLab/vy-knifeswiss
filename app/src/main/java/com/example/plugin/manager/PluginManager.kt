package com.example.plugin.manager

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import com.example.plugin.core.DynamicWebPlugin
import com.example.plugin.core.ToolPlugin
import com.example.plugin.model.CatalogPluginItem
import com.example.plugin.model.PluginManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import com.example.R
import com.example.Res

object PluginManager {
    const val DEFAULT_CATALOG_URL = "https://knifeswiss.yotserlab.com/plugins.json"
    private const val PREFS_NAME = "vy_plugin_prefs"
    private const val KEY_CATALOG_URL = "custom_catalog_url"

    private val _nativePlugins = mutableListOf<ToolPlugin>()
    val nativePlugins: List<ToolPlugin> get() = _nativePlugins

    val dynamicPlugins = mutableStateListOf<DynamicWebPlugin>()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var isInitialized = false

    fun registerNativePlugin(plugin: ToolPlugin) {
        if (_nativePlugins.none { it.id == plugin.id }) {
            _nativePlugins.add(plugin)
        }
    }

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true
        loadInstalledDynamicPlugins(context)
        _nativePlugins.forEach { it.onInitialize(context) }
    }

    fun getAllPlugins(): List<ToolPlugin> {
        val list = mutableListOf<ToolPlugin>()
        list.addAll(_nativePlugins)
        list.addAll(dynamicPlugins)
        return list
    }

    fun getEnabledPlugins(context: Context): List<ToolPlugin> {
        return getAllPlugins().filter { isPluginEnabled(context, it.id) }
    }

    fun isPluginEnabled(context: Context, pluginId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean("enabled_$pluginId", true)
    }

    fun setPluginEnabled(context: Context, pluginId: String, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("enabled_$pluginId", enabled).apply()
    }

    fun getCatalogUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CATALOG_URL, DEFAULT_CATALOG_URL) ?: DEFAULT_CATALOG_URL
    }

    fun setCatalogUrl(context: Context, url: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CATALOG_URL, url.trim()).apply()
    }

    fun registerAllNavigation(navGraphBuilder: NavGraphBuilder, navController: NavController) {
        _nativePlugins.forEach { plugin ->
            plugin.registerNavigation(navGraphBuilder, navController)
        }
        dynamicPlugins.forEach { plugin ->
            plugin.registerNavigation(navGraphBuilder, navController)
        }
    }

    private fun getPluginsDir(context: Context): File {
        val dir = File(context.filesDir, "plugins")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun loadInstalledDynamicPlugins(context: Context) {
        dynamicPlugins.clear()
        val baseDir = getPluginsDir(context)
        val pluginFolders = baseDir.listFiles { f -> f.isDirectory } ?: return

        for (folder in pluginFolders) {
            val manifestFile = File(folder, "plugin.json")
            if (manifestFile.exists()) {
                try {
                    val content = manifestFile.readText()
                    val json = JSONObject(content)
                    val manifest = PluginManifest.fromJson(json)
                    dynamicPlugins.add(DynamicWebPlugin(manifest, folder))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun installFromUri(context: Context, uri: Uri): Result<PluginManifest> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(IOException(Res.get(R.string.nao_foi_possivel_abrir_o_arquivo_selecio)))
            inputStream.use { stream ->
                installFromZipStream(context, stream)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun installFromUrl(
        context: Context,
        downloadUrl: String,
        onProgress: (Float) -> Unit = {}
    ): Result<PluginManifest> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(downloadUrl).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException(Res.get(R.string.falha_no_download_http_x, (response.code))))
            }

            val body = response.body ?: return@withContext Result.failure(IOException(Res.get(R.string.resposta_de_download_vazia)))
            val contentLength = body.contentLength()

            val tempZip = File(context.cacheDir, "plugin_download_${System.currentTimeMillis()}.zip")
            val outputStream = FileOutputStream(tempZip)

            body.byteStream().use { input ->
                outputStream.use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (contentLength > 0) {
                            onProgress(totalRead.toFloat() / contentLength.toFloat())
                        }
                    }
                }
            }

            val result = FileInputStream(tempZip).use { stream ->
                installFromZipStream(context, stream)
            }
            tempZip.delete()
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun installFromZipStream(context: Context, inputStream: InputStream): Result<PluginManifest> {
        val tempDir = File(context.cacheDir, "temp_plugin_${System.currentTimeMillis()}")
        if (!tempDir.mkdirs()) return Result.failure(IOException(Res.get(R.string.falha_ao_criar_diretorio_temporario)))

        try {
            ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
                var entry = zis.nextEntry
                val canonicalTempDirPath = tempDir.canonicalPath + File.separator

                while (entry != null) {
                    val targetFile = File(tempDir, entry.name)
                    val canonicalDest = targetFile.canonicalPath

                    // Zip Slip Protection: Never allow escaping destination directory!
                    if (!canonicalDest.startsWith(canonicalTempDirPath)) {
                        throw SecurityException(Res.get(R.string.tentativa_de_zip_slip_detectada_na_entra, (entry.name)))
                    }

                    if (entry.isDirectory) {
                        targetFile.mkdirs()
                    } else {
                        targetFile.parentFile?.mkdirs()
                        FileOutputStream(targetFile).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // Locate plugin.json in tempDir or a root subfolder
            var pluginJsonFile = File(tempDir, "plugin.json")
            var pluginRoot = tempDir
            if (!pluginJsonFile.exists()) {
                val subdirs = tempDir.listFiles { f -> f.isDirectory } ?: emptyArray()
                for (sub in subdirs) {
                    val subJson = File(sub, "plugin.json")
                    if (subJson.exists()) {
                        pluginJsonFile = subJson
                        pluginRoot = sub
                        break
                    }
                }
            }

            if (!pluginJsonFile.exists()) {
                tempDir.deleteRecursively()
                return Result.failure(IllegalArgumentException(Res.get(R.string.o_arquivo_zip_nao_contem_um_plugin_json)))
            }

            val manifest = PluginManifest.fromJson(JSONObject(pluginJsonFile.readText()))
            if (manifest.id.isBlank()) { // PLAT-R04
                tempDir.deleteRecursively()
                return Result.failure(IllegalArgumentException(Res.get(R.string.o_plugin_json_nao_especifica_um_id_valid)))
            }

            val destDir = File(getPluginsDir(context), manifest.id)
            if (destDir.exists()) {
                destDir.deleteRecursively()
            }
            destDir.mkdirs()

            pluginRoot.copyRecursively(destDir, overwrite = true)
            tempDir.deleteRecursively()

            // Update in-memory registry
            dynamicPlugins.removeAll { it.id == manifest.id }
            val newPlugin = DynamicWebPlugin(manifest, destDir)
            dynamicPlugins.add(newPlugin)
            setPluginEnabled(context, manifest.id, true) // PLAT-R13

            return Result.success(manifest)
        } catch (e: Exception) {
            tempDir.deleteRecursively()
            return Result.failure(e)
        }
    }

    fun uninstallPlugin(context: Context, pluginId: String): Boolean {
        val destDir = File(getPluginsDir(context), pluginId)
        val deleted = if (destDir.exists()) destDir.deleteRecursively() else true
        dynamicPlugins.removeAll { it.id == pluginId }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove("enabled_$pluginId").apply()
        return deleted
    }

    suspend fun fetchOnlineCatalog(context: Context, catalogUrl: String? = null): Result<List<CatalogPluginItem>> =
        withContext(Dispatchers.IO) {
            val url = catalogUrl ?: getCatalogUrl(context)
            try {
                val request = Request.Builder().url(url).build()
                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException(Res.get(R.string.erro_ao_acessar_o_catalogo_http_x, (response.code))))
                }

                val bodyStr = response.body?.string() ?: "[]"
                val jsonArray = JSONArray(bodyStr)
                val installedMap = dynamicPlugins.associateBy { it.id }.mapValues { it.value.manifest }

                val items = mutableListOf<CatalogPluginItem>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    items.add(CatalogPluginItem.fromJson(obj, installedMap))
                }

                // Cache response locally for offline browsing
                val cacheFile = File(context.cacheDir, "catalog_cache.json")
                try { cacheFile.writeText(bodyStr) } catch (_: Exception) {}

                Result.success(items)
            } catch (e: Exception) {
                // Try fallback to cache
                val cacheFile = File(context.cacheDir, "catalog_cache.json")
                if (cacheFile.exists()) {
                    try {
                        val bodyStr = cacheFile.readText()
                        val jsonArray = JSONArray(bodyStr)
                        val installedMap = dynamicPlugins.associateBy { it.id }.mapValues { it.value.manifest }
                        val items = mutableListOf<CatalogPluginItem>()
                        for (i in 0 until jsonArray.length()) {
                            items.add(CatalogPluginItem.fromJson(jsonArray.getJSONObject(i), installedMap))
                        }
                        return@withContext Result.success(items)
                    } catch (_: Exception) {}
                }
                Result.failure(e)
            }
        }
}
