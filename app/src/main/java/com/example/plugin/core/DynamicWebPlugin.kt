package com.example.plugin.core

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.example.plugin.model.PluginManifest
import com.example.plugin.ui.DynamicPluginScreen
import java.io.File

class DynamicWebPlugin(
    override val manifest: PluginManifest,
    val pluginDir: File
) : ToolPlugin {
    override val isBuiltIn: Boolean = false
    override val icon: ImageVector = PluginIconResolver.resolve(manifest.iconName)
    override val route: String = "dynamic_plugin_${manifest.id}"

    val entryPointFile: File
        get() = File(pluginDir, manifest.entryPoint)

    override fun registerNavigation(navGraphBuilder: NavGraphBuilder, navController: NavController) {
        navGraphBuilder.composable(route) {
            DynamicPluginScreen(plugin = this@DynamicWebPlugin, navController = navController)
        }
    }
}
