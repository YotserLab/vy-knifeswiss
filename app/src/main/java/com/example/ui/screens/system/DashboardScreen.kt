package com.example.ui.screens.system

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import androidx.navigation.NavController
import com.example.ui.navigation.Screen
import com.example.ui.model.ToolItem
import com.example.ui.theme.*
import com.example.ui.components.CategoryHeader
import com.example.ui.miniapp.MiniAppManager
import com.example.ui.miniapp.toToolItem
import com.example.plugin.manager.PluginManager
import com.example.plugin.model.PluginCategory
import com.example.R
import com.example.Res

data class ToolCategory(val name: String, val tools: List<ToolItem>, val accentColor: Color)

private fun createToolShortcut(context: Context, toolName: String, route: String) {
    try {
        val shortcutManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(ShortcutManager::class.java)
        } else null
        val shortcutId = "tool_${toolName.replace(" ", "_").lowercase()}"
        if (shortcutManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(context, com.example.MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = android.net.Uri.parse("vyknifeswiss://tool/$route")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val shortcutInfo = ShortcutInfo.Builder(context, shortcutId)
                .setShortLabel(toolName)
                .setLongLabel(Res.get(R.string.abrir_x, toolName))
                .setIcon(Icon.createWithResource(context, android.R.drawable.ic_menu_compass))
                .setIntent(intent)
                .build()
            shortcutManager.requestPinShortcut(shortcutInfo, null)
        } else {
            val shortcutIntent = Intent(context, com.example.MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = android.net.Uri.parse("vyknifeswiss://tool/$route")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val addIntent = Intent("com.android.launcher.action.INSTALL_SHORTCUT").apply {
                putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent)
                putExtra(Intent.EXTRA_SHORTCUT_NAME, toolName)
                putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(context, android.R.drawable.ic_menu_compass))
            }
            context.sendBroadcast(addIntent)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(navController: NavController) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    // Trigger recomposition on resume to update top tools
    var refreshTrigger by remember { mutableStateOf(0) }
    DisposableEffect(Unit) {
        refreshTrigger++
        onDispose { }
    }

    val enabledPlugins = remember(PluginManager.dynamicPlugins.size, refreshTrigger) {
        PluginManager.getEnabledPlugins(context)
    }

    var searchQuery by remember { mutableStateOf("") }
    val dashboardPrefs = remember { context.getSharedPreferences("dashboard_state", Context.MODE_PRIVATE) }
    var collapsedCategories by remember {
        mutableStateOf(dashboardPrefs.getStringSet("collapsed_categories", emptySet())?.toSet() ?: emptySet())
    }
    LaunchedEffect(collapsedCategories) {
        dashboardPrefs.edit().putStringSet("collapsed_categories", collapsedCategories).apply()
    }

    val installedMiniApps by produceState(initialValue = emptyList<com.example.ui.miniapp.MiniApp>(), key1 = Unit) {
        while (true) {
            value = com.example.ui.miniapp.MiniAppManager.installedApps(context)
            kotlinx.coroutines.delay(1000)
        }
    }

    val categories = remember(enabledPlugins, installedMiniApps) {
        val nativeCategories = PluginCategory.entries.map { cat ->
            val catPlugins = enabledPlugins.filter { it.category == cat } // PLAT-R10
            ToolCategory(
                name = cat.displayName,
                tools = catPlugins.map { ToolItem(it.title, it.icon, it.route) },
                accentColor = cat.color
            )
        }.filter { it.tools.isNotEmpty() }

        val miniAppTools = listOf(
            ToolItem(Res.get(R.string.loja_de_mini_apps), Icons.Default.Store, Screen.MiniAppStore.route)
        ) + installedMiniApps.map { it.toToolItem() }

        nativeCategories + if (miniAppTools.isNotEmpty()) {
            listOf(ToolCategory(Res.get(R.string.mini_apps), miniAppTools, ElectricViolet))
        } else emptyList()
    }

    val allFilteredCategories = if (searchQuery.isBlank()) {
        categories
    } else {
        categories.map { cat ->
            cat.copy(tools = cat.tools.filter { it.title.contains(searchQuery, ignoreCase = true) })
        }.filter { it.tools.isNotEmpty() }
    }

    val anyExpanded = collapsedCategories.size < allFilteredCategories.size

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = DarkNavy,
                drawerContentColor = OnSurfaceLight,
                modifier = Modifier.width(300.dp)
            ) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(CyanNeon.copy(alpha = 0.15f), Color.Transparent)
                            )
                        )
                        .padding(24.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Apps,
                                contentDescription = null,
                                tint = CyanNeon,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                Res.get(R.string.vy_swiss_knife),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = OnSurfaceLight
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            Res.get(R.string.canivete_suico_pro_cybernetic),
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceMuted
                        )
                    }
                }

                HorizontalDivider(color = DarkBorder)
                Spacer(Modifier.height(8.dp))

                // Drawer Actions
                NavigationDrawerItem(
                    label = { Text(if (anyExpanded) Res.get(R.string.colapsar_todas_categorias) else Res.get(R.string.expandir_todas_categorias)) },
                    icon = {
                        Icon(
                            if (anyExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = CyanNeon
                        )
                    },
                    selected = false,
                    onClick = {
                        collapsedCategories = if (anyExpanded) allFilteredCategories.map { it.name }.toSet() else emptySet()
                        scope.launch { drawerState.close() }
                    },
                    colors = NavigationDrawerItemDefaults.colors(unselectedTextColor = OnSurfaceLight),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )

                NavigationDrawerItem(
                    label = { Text(Res.get(R.string.central_de_plugins_catalogo)) },
                    icon = { Icon(Icons.Default.Extension, contentDescription = null, tint = CyanNeon) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate(Screen.PluginManager.route)
                    },
                    colors = NavigationDrawerItemDefaults.colors(unselectedTextColor = OnSurfaceLight),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )

                NavigationDrawerItem(
                    label = { Text(Res.get(R.string.loja_de_mini_apps)) },
                    icon = { Icon(Icons.Default.Apps, contentDescription = null, tint = ElectricViolet) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate(Screen.MiniAppStore.route)
                    },
                    colors = NavigationDrawerItemDefaults.colors(unselectedTextColor = OnSurfaceLight),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )

                NavigationDrawerItem(
                    label = { Text(Res.get(R.string.criar_atalho_na_tela_inicial)) },
                    icon = { Icon(Icons.AutoMirrored.Filled.AddToHomeScreen, contentDescription = null, tint = AmberGold) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        createToolShortcut(context, Res.get(R.string.vy_swiss_knife), Screen.Dashboard.route)
                    },
                    colors = NavigationDrawerItemDefaults.colors(unselectedTextColor = OnSurfaceLight),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )

                Spacer(Modifier.weight(1f))
                HorizontalDivider(color = DarkBorder)
                Spacer(Modifier.height(8.dp))

                NavigationDrawerItem(
                    label = { Text(Res.get(R.string.configuracoes)) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null, tint = OnSurfaceMuted) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate(Screen.Settings.route)
                    },
                    colors = NavigationDrawerItemDefaults.colors(unselectedTextColor = OnSurfaceLight),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )

                NavigationDrawerItem(
                    label = { Text("Sobre") },
                    icon = { Icon(Icons.Default.Info, contentDescription = null, tint = OnSurfaceMuted) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate(Screen.About.route)
                    },
                    colors = NavigationDrawerItemDefaults.colors(unselectedTextColor = OnSurfaceLight),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )

                Spacer(Modifier.height(16.dp))
            }
        }
    ) {
        Scaffold(
            containerColor = DeepDark,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            Res.get(R.string.vy_swiss_knife),
                            style      = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color      = OnSurfaceLight,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = Res.get(R.string.menu_principal),
                                tint = CyanNeon
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            collapsedCategories = if (anyExpanded) allFilteredCategories.map { it.name }.toSet() else emptySet()
                        }) {
                            Icon(
                                imageVector = if (anyExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (anyExpanded) Res.get(R.string.colapsar_todos) else Res.get(R.string.expandir_todos),
                                tint = CyanNeon,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepDark),
                )
            }
        ) { padding ->
        val allTools = categories.flatMap { it.tools }
        val topTools = remember(refreshTrigger, allTools) {
            allTools.map { it to com.example.utils.UsageTracker.getUsageCount(context, it.route) }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
                .take(12)
                .map { it.first }
        }

        LazyVerticalGrid(
            columns              = GridCells.Fixed(6),
            contentPadding       = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            modifier             = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(DeepDark),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement   = Arrangement.spacedBy(10.dp),
        ) {
            // Search bar
            item(span = { GridItemSpan(maxLineSpan) }) {
                SearchBar(
                    query    = searchQuery,
                    onQuery  = { searchQuery = it },
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
            }

            // Plugins Hub & Catalog Banner
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .border(1.dp, CyanNeon.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .clickable { navController.navigate(Screen.PluginManager.route) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Extension, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(Res.get(R.string.loja_central_de_plugins), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = OnSurfaceLight)
                                Text(Res.get(R.string.knifeswiss_yotserlab_com_catalogo_url_ou), style = MaterialTheme.typography.bodySmall, color = OnSurfaceMuted)
                            }
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(18.dp))
                    }
                }
            }

            if (topTools.isNotEmpty() && searchQuery.isBlank()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    CategoryHeader(
                        label = Res.get(R.string.mais_usados),
                        accentColor = AmberGold,
                        horizontalPadding = 0.dp,
                        expanded = true,
                        onClick = {}
                    )
                }
                items(topTools, span = { GridItemSpan(1) }) { tool ->
                    MiniToolCard(
                        tool = tool,
                        categoryColor = categories.find { it.tools.contains(tool) }?.accentColor ?: CyanNeon,
                        onClick = { 
                            com.example.utils.UsageTracker.trackUsage(context, tool.route)
                            navController.navigate(tool.route) 
                        }
                    )
                }
                val remTop = topTools.size % 6
                if (remTop != 0) {
                    val fillTop = 6 - remTop
                    items(fillTop, span = { GridItemSpan(1) }) { Box(modifier = Modifier.aspectRatio(1f)) }
                }
            }

            allFilteredCategories.forEach { category ->
                val expanded = searchQuery.isNotBlank() || !collapsedCategories.contains(category.name)
                item(span = { GridItemSpan(maxLineSpan) }) {
                    CategoryHeader(
                        label = category.name,
                        accentColor = category.accentColor,
                        horizontalPadding = 0.dp,
                        expanded = expanded,
                        onClick = {
                            collapsedCategories = if (collapsedCategories.contains(category.name)) {
                                collapsedCategories - category.name
                            } else {
                                collapsedCategories + category.name
                            }
                        },
                    )
                }
                if (expanded) {
                    items(category.tools, span = { GridItemSpan(2) }) { tool ->
                        ToolCard(
                            tool          = tool,
                            categoryColor = category.accentColor,
                            onClick       = { 
                                com.example.utils.UsageTracker.trackUsage(context, tool.route)
                                navController.navigate(tool.route) 
                            },
                            onLongClick   = { createToolShortcut(context, tool.title, tool.route) },
                        )
                    }
                    // Fill remaining cells in last row for alignment
                    val rem = category.tools.size % 3
                    if (rem != 0) {
                        val fill = 3 - rem
                        items(fill, span = { GridItemSpan(2) }) {
                            Box(modifier = Modifier.aspectRatio(1f))
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(DarkNavy)
            .border(1.dp, DarkBorder, RoundedCornerShape(23.dp))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, contentDescription = null, tint = OnSurfaceMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value        = query,
                onValueChange = onQuery,
                singleLine   = true,
                textStyle    = MaterialTheme.typography.bodyMedium.copy(color = OnSurfaceLight),
                modifier     = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(Res.get(R.string.buscar_ferramentas), style = MaterialTheme.typography.bodyMedium, color = OnSurfaceDim)
                    }
                    inner()
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ToolCard(
    tool: ToolItem,
    categoryColor: Color = CyanNeon,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val glowColor = categoryColor.copy(alpha = 0.15f)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .drawBehind {
                drawRoundRect(color = glowColor, cornerRadius = CornerRadius(16.dp.toPx()))
            }
            .background(DarkNavy, RoundedCornerShape(16.dp))
            .border(1.dp, categoryColor.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier             = Modifier.padding(8.dp),
            horizontalAlignment  = Alignment.CenterHorizontally,
            verticalArrangement  = Arrangement.Center,
        ) {
            Icon(
                imageVector     = tool.icon,
                contentDescription = tool.title,
                modifier        = Modifier.size(38.dp),
                tint            = categoryColor,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text      = tool.title,
                style     = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp),
                color     = OnSurfaceLight,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium,
                maxLines  = 2,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun MiniToolCard(
    tool: ToolItem,
    categoryColor: Color,
    onClick: () -> Unit
) {
    val glowColor = categoryColor.copy(alpha = 0.15f)
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .drawBehind {
                drawRoundRect(color = glowColor, cornerRadius = CornerRadius(12.dp.toPx()))
            }
            .background(DarkNavy, RoundedCornerShape(12.dp))
            .border(1.dp, categoryColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier             = Modifier.padding(2.dp),
            horizontalAlignment  = Alignment.CenterHorizontally,
            verticalArrangement  = Arrangement.Center,
        ) {
            Icon(
                imageVector     = tool.icon,
                contentDescription = tool.title,
                modifier        = Modifier.size(20.dp),
                tint            = categoryColor,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text      = tool.title,
                style     = MaterialTheme.typography.labelMedium.copy(fontSize = 9.sp),
                color     = OnSurfaceLight,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium,
                maxLines  = 2,
                lineHeight = 10.sp
            )
        }
    }
}

