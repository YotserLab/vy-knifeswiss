package com.example.ui.miniapp

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.ui.graphics.Color
import com.example.ui.model.ToolItem
import com.example.ui.theme.ElectricViolet
import org.json.JSONObject
import com.example.R
import com.example.Res

/**
 * Manifest of a VY Mini-App (inspired by WeChat mini-programs).
 */
data class MiniAppManifest(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String = "",
    val category: String = Res.get(R.string.mini_apps),
    val accentColor: String = "#A855F7",
    val icon: String = "icon.svg",
    val entryPoint: String = "index.html",
    val permissions: List<String> = emptyList(),
    val allowedDomains: List<String> = emptyList(),
    val fileHashes: Map<String, String> = emptyMap(), // relative path -> sha256 hex
)

/**
 * Installed mini-app metadata.
 */
data class MiniApp(
    val id: String,
    val manifest: MiniAppManifest,
    val installPath: java.io.File,
    val installedAt: Long = System.currentTimeMillis(),
)

fun MiniApp.toToolItem(): ToolItem = ToolItem(
    title = manifest.name,
    icon = Icons.Default.Apps,
    route = "miniapp/${id}",
)

fun MiniApp.accentColorCompose(): Color = try {
    Color(android.graphics.Color.parseColor(manifest.accentColor))
} catch (_: Exception) {
    ElectricViolet
}

internal fun JSONObject.toMiniAppManifest(): MiniAppManifest {
    val permissionArray = optJSONArray("permissions")
    val permissions = mutableListOf<String>()
    if (permissionArray != null) {
        for (i in 0 until permissionArray.length()) {
            permissions.add(permissionArray.getString(i))
        }
    }

    val domainsArray = optJSONArray("allowedDomains")
    val allowedDomains = mutableListOf<String>()
    if (domainsArray != null) {
        for (i in 0 until domainsArray.length()) {
            allowedDomains.add(domainsArray.getString(i))
        }
    }

    val filesObj = optJSONObject("fileHashes")
    val fileHashes = mutableMapOf<String, String>()
    if (filesObj != null) {
        val keys = filesObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            fileHashes[key] = filesObj.getString(key)
        }
    }

    return MiniAppManifest(
        id = getString("id"),
        name = getString("name"),
        version = getString("version"),
        author = getString("author"),
        description = optString("description", ""),
        category = optString("category", Res.get(R.string.mini_apps)),
        accentColor = optString("accentColor", "#A855F7"),
        icon = optString("icon", "icon.svg"),
        entryPoint = optString("entryPoint", "index.html"),
        permissions = permissions,
        allowedDomains = allowedDomains,
        fileHashes = fileHashes,
    )
}
