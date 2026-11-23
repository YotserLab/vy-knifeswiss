package com.example.plugin.model

import org.json.JSONObject

data class CatalogPluginItem(
    val manifest: PluginManifest,
    val isInstalled: Boolean = false,
    val isUpdateAvailable: Boolean = false,
    val installedVersion: String? = null
) {
    companion object {
        fun fromJson(json: JSONObject, installedManifests: Map<String, PluginManifest>): CatalogPluginItem {
            val manifest = PluginManifest.fromJson(json)
            val installed = installedManifests[manifest.id]
            val isInstalled = installed != null
            val isUpdateAvailable = installed != null && manifest.versionCode > installed.versionCode

            return CatalogPluginItem(
                manifest = manifest,
                isInstalled = isInstalled,
                isUpdateAvailable = isUpdateAvailable,
                installedVersion = installed?.version
            )
        }
    }
}
