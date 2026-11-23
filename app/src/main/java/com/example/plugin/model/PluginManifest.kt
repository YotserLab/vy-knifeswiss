package com.example.plugin.model

import org.json.JSONArray
import org.json.JSONObject
import com.example.R
import com.example.Res

// PLAT-R03
data class PluginManifest(
    val id: String,
    val name: String,
    val version: String = "1.0.0",
    val versionCode: Int = 1,
    val description: String = "",
    val author: String = Res.get(R.string.vyknifeswiss),
    val category: PluginCategory = PluginCategory.COMMUNITY,
    val iconName: String = "extension",
    val entryPoint: String = "index.html",
    val type: String = "web", // "native" or "web"
    val permissions: List<String> = emptyList(),
    val minAppVersion: Int = 1,
    val downloadUrl: String? = null,
    val fileSize: Long = 0L
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("version", version)
            put("versionCode", versionCode)
            put("description", description)
            put("author", author)
            put("category", category.name)
            put("iconName", iconName)
            put("entryPoint", entryPoint)
            put("type", type)
            put("permissions", JSONArray(permissions))
            put("minAppVersion", minAppVersion)
            downloadUrl?.let { put("downloadUrl", it) }
            put("fileSize", fileSize)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): PluginManifest {
            val permsList = mutableListOf<String>()
            val permsArray = json.optJSONArray("permissions")
            if (permsArray != null) {
                for (i in 0 until permsArray.length()) {
                    permsList.add(permsArray.optString(i))
                }
            }

            return PluginManifest(
                id = json.getString("id"),
                name = json.getString("name"),
                version = json.optString("version", "1.0.0"),
                versionCode = json.optInt("versionCode", 1),
                description = json.optString("description", ""),
                author = json.optString("author", "Unknown"),
                category = PluginCategory.fromString(json.optString("category", "COMMUNITY")), // PLAT-R05
                iconName = json.optString("iconName", "extension"),
                entryPoint = json.optString("entryPoint", "index.html"),
                type = json.optString("type", "web"),
                permissions = permsList,
                minAppVersion = json.optInt("minAppVersion", 1),
                downloadUrl = if (json.has("downloadUrl")) json.optString("downloadUrl") else null,
                fileSize = json.optLong("fileSize", 0L)
            )
        }
    }
}
