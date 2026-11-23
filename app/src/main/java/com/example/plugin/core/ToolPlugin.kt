package com.example.plugin.core

import android.content.Context
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import com.example.plugin.model.PluginCategory
import com.example.plugin.model.PluginManifest

interface ToolPlugin {
    val manifest: PluginManifest
    val isBuiltIn: Boolean
    val icon: ImageVector
    val route: String get() = manifest.entryPoint

    val id: String get() = manifest.id
    val title: String get() = manifest.name
    val category: PluginCategory get() = manifest.category
    val description: String get() = manifest.description
    val requiredPermissions: List<String> get() = manifest.permissions

    fun registerNavigation(navGraphBuilder: NavGraphBuilder, navController: NavController)

    fun onInitialize(context: Context) {}

    fun onDisabled(context: Context) {}
}
