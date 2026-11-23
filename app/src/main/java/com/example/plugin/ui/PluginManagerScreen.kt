package com.example.plugin.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.plugin.core.ToolPlugin
import com.example.plugin.manager.PluginManager
import com.example.plugin.model.CatalogPluginItem
import com.example.plugin.model.PluginCategory
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import com.example.R
import com.example.Res

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginManagerScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf(Res.get(R.string.instalados), Res.get(R.string.catalogo_online))

    // State for local plugins
    var installedPlugins by remember { mutableStateOf(PluginManager.getAllPlugins()) }
    var installedFilter by remember { mutableStateOf(Res.get(R.string.todos)) }

    // State for online catalog
    var catalogItems by remember { mutableStateOf<List<CatalogPluginItem>>(emptyList()) }
    var isCatalogLoading by remember { mutableStateOf(false) }
    var catalogError by remember { mutableStateOf<String?>(null) }
    var catalogSearchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf<PluginCategory?>(null) }

    // Dialogs
    var showUrlInstallDialog by remember { mutableStateOf(false) }
    var urlToInstall by remember { mutableStateOf("") }
    var isInstallingFromUrl by remember { mutableStateOf(false) }

    var showRepoConfigDialog by remember { mutableStateOf(false) }
    var repoUrlInput by remember { mutableStateOf(PluginManager.getCatalogUrl(context)) }

    fun refreshInstalled() {
        installedPlugins = PluginManager.getAllPlugins()
    }

    fun loadCatalog() {
        coroutineScope.launch {
            isCatalogLoading = true
            catalogError = null
            val result = PluginManager.fetchOnlineCatalog(context)
            isCatalogLoading = false
            result.onSuccess { items ->
                catalogItems = items
            }.onFailure { err ->
                catalogError = err.message ?: Res.get(R.string.erro_ao_carregar_o_catalogo_de_plugins)
            }
        }
    }

    LaunchedEffect(Unit) {
        loadCatalog()
    }

    // ZIP file picker launcher
    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                Toast.makeText(context, Res.get(R.string.instalando_plugin_do_arquivo_zip), Toast.LENGTH_SHORT).show()
                val res = PluginManager.installFromUri(context, uri)
                res.onSuccess { manifest ->
                    Toast.makeText(context, Res.get(R.string.plugin_x_instalado_com_sucesso, (manifest.name)), Toast.LENGTH_LONG).show()
                    refreshInstalled()
                    loadCatalog()
                }.onFailure { err ->
                    Toast.makeText(context, Res.get(R.string.erro_na_instalacao_x, (err.message)), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        containerColor = DeepDark,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            Res.get(R.string.central_de_plugins),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = OnSurfaceLight
                        )
                        Text(
                            "knifeswiss.yotserlab.com",
                            style = MaterialTheme.typography.bodySmall,
                            color = CyanNeon
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = Res.get(R.string.voltar),
                            tint = CyanNeon
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { zipPickerLauncher.launch("application/zip") }) {
                        Icon(Icons.Default.FileOpen, contentDescription = Res.get(R.string.instalar_zip), tint = CyanNeon)
                    }
                    IconButton(onClick = { showUrlInstallDialog = true }) {
                        Icon(Icons.Default.Link, contentDescription = Res.get(R.string.instalar_via_url), tint = CyanNeon)
                    }
                    IconButton(onClick = { showRepoConfigDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = Res.get(R.string.configurar_repositorio), tint = OnSurfaceMuted)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepDark)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DeepDark)
        ) {
            // Tab row
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = SurfaceDark,
                contentColor = CyanNeon,
                indicator = {}
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                title,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTabIndex == index) CyanNeon else OnSurfaceMuted
                            )
                        }
                    )
                }
            }

            when (selectedTabIndex) {
                0 -> {
                    // TAB: INSTALADOS
                    InstalledPluginsTab(
                        plugins = installedPlugins,
                        filter = installedFilter,
                        onFilterChange = { installedFilter = it },
                        onToggle = { plugin, enabled ->
                            PluginManager.setPluginEnabled(context, plugin.id, enabled)
                            refreshInstalled()
                        },
                        onUninstall = { plugin ->
                            PluginManager.uninstallPlugin(context, plugin.id)
                            refreshInstalled()
                            loadCatalog()
                            Toast.makeText(context, Res.get(R.string.plugin_removido), Toast.LENGTH_SHORT).show()
                        },
                        onOpen = { plugin ->
                            navController.navigate(plugin.route)
                        }
                    )
                }
                1 -> {
                    // TAB: ONLINE CATALOG
                    OnlineCatalogTab(
                        catalogItems = catalogItems,
                        isLoading = isCatalogLoading,
                        errorMessage = catalogError,
                        searchQuery = catalogSearchQuery,
                        onSearchChange = { catalogSearchQuery = it },
                        selectedCategory = selectedCategoryFilter,
                        onCategorySelect = { selectedCategoryFilter = it },
                        onRefresh = { loadCatalog() },
                        onInstall = { item ->
                            val downloadUrl = item.manifest.downloadUrl
                            if (downloadUrl.isNullOrBlank()) {
                                Toast.makeText(context, Res.get(R.string.url_de_download_indisponivel_para_este_p), Toast.LENGTH_SHORT).show()
                            } else {
                                coroutineScope.launch {
                                    Toast.makeText(context, Res.get(R.string.baixando_x, (item.manifest.name)), Toast.LENGTH_SHORT).show()
                                    val res = PluginManager.installFromUrl(context, downloadUrl)
                                    res.onSuccess {
                                        Toast.makeText(context, Res.get(R.string.x_instalado, (item.manifest.name)), Toast.LENGTH_LONG).show()
                                        refreshInstalled()
                                        loadCatalog()
                                    }.onFailure { err ->
                                        Toast.makeText(context, Res.get(R.string.falha_ao_instalar_x, (err.message)), Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    // Dialog: Instalar via URL
    if (showUrlInstallDialog) {
        AlertDialog(
            onDismissRequest = { if (!isInstallingFromUrl) showUrlInstallDialog = false },
            title = { Text(Res.get(R.string.instalar_plugin_via_url)) },
            text = {
                Column {
                    Text(
                        Res.get(R.string.digite_a_url_direta_do_pacote_zip_do_plu),
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceMuted
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = urlToInstall,
                        onValueChange = { urlToInstall = it },
                        placeholder = { Text("https://knifeswiss.yotserlab.com/plugins/meu-plugin.zip") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (urlToInstall.isNotBlank()) {
                            isInstallingFromUrl = true
                            coroutineScope.launch {
                                val res = PluginManager.installFromUrl(context, urlToInstall.trim())
                                isInstallingFromUrl = false
                                showUrlInstallDialog = false
                                res.onSuccess { manifest ->
                                    Toast.makeText(context, Res.get(R.string.plugin_x_instalado, (manifest.name)), Toast.LENGTH_LONG).show()
                                    refreshInstalled()
                                    loadCatalog()
                                }.onFailure { err ->
                                    Toast.makeText(context, Res.get(R.string.erro_x_2, (err.message)), Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    enabled = !isInstallingFromUrl && urlToInstall.isNotBlank()
                ) {
                    Text(if (isInstallingFromUrl) Res.get(R.string.instalando) else Res.get(R.string.baixar_instalar))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showUrlInstallDialog = false },
                    enabled = !isInstallingFromUrl
                ) {
                    Text(Res.get(R.string.cancelar))
                }
            }
        )
    }

    // Dialog: Configure Repository URL
    if (showRepoConfigDialog) {
        AlertDialog(
            onDismissRequest = { showRepoConfigDialog = false },
            title = { Text(Res.get(R.string.url_do_catalogo_de_plugins)) },
            text = {
                Column {
                    Text(
                        Res.get(R.string.defina_o_endereco_do_repositorio_json_on),
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceMuted
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = repoUrlInput,
                        onValueChange = { repoUrlInput = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { repoUrlInput = PluginManager.DEFAULT_CATALOG_URL }) {
                        Text(Res.get(R.string.restaurar_padrao_knifeswiss_yotserlab_co), color = CyanNeon)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    PluginManager.setCatalogUrl(context, repoUrlInput)
                    showRepoConfigDialog = false
                    loadCatalog()
                }) {
                    Text(Res.get(R.string.salvar))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRepoConfigDialog = false }) {
                    Text(Res.get(R.string.cancelar))
                }
            }
        )
    }
}

@Composable
private fun InstalledPluginsTab(
    plugins: List<ToolPlugin>,
    filter: String,
    onFilterChange: (String) -> Unit,
    onToggle: (ToolPlugin, Boolean) -> Unit,
    onUninstall: (ToolPlugin) -> Unit,
    onOpen: (ToolPlugin) -> Unit
) {
    val context = LocalContext.current
    val filterOptions = listOf(Res.get(R.string.todos), Res.get(R.string.nativos), Res.get(R.string.dinamicos_web))

    val filteredList = when (filter) {
        Res.get(R.string.nativos) -> plugins.filter { it.isBuiltIn }
        Res.get(R.string.dinamicos_web) -> plugins.filter { !it.isBuiltIn }
        else -> plugins
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Filter pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filterOptions.forEach { opt ->
                val selected = filter == opt
                FilterChip(
                    selected = selected,
                    onClick = { onFilterChange(opt) },
                    label = { Text(opt) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyanNeon.copy(alpha = 0.2f),
                        selectedLabelColor = CyanNeon
                    )
                )
            }
        }

        if (filteredList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(Res.get(R.string.nenhum_plugin_encontrado_nesta_categoria), color = OnSurfaceMuted)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredList, key = { it.id }) { plugin ->
                    var isEnabled by remember(plugin.id, plugins) { mutableStateOf(PluginManager.isPluginEnabled(context, plugin.id)) }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, if (isEnabled) plugin.category.color.copy(alpha = 0.3f) else Color.Transparent, RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(plugin.category.color.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = plugin.icon,
                                        contentDescription = null,
                                        tint = plugin.category.color,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = plugin.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = OnSurfaceLight
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Surface(
                                            color = if (plugin.isBuiltIn) ElectricViolet.copy(alpha = 0.2f) else CyanNeon.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = if (plugin.isBuiltIn) "NATIVO" else "WEB",
                                                color = if (plugin.isBuiltIn) ElectricViolet else CyanNeon,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = Res.get(R.string.x_vx, (plugin.category.displayName), (plugin.manifest.version)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OnSurfaceMuted
                                    )
                                }
                                Switch(
                                    checked = isEnabled,
                                    onCheckedChange = {
                                        isEnabled = it
                                        onToggle(plugin, it)
                                    }
                                )
                            }

                            if (plugin.description.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = plugin.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceMuted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!plugin.isBuiltIn) {
                                    TextButton(
                                        onClick = { onUninstall(plugin) },
                                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Desinstalar")
                                    }
                                    Spacer(Modifier.width(8.dp))
                                }
                                Button(
                                    onClick = { onOpen(plugin) },
                                    enabled = isEnabled,
                                    colors = ButtonDefaults.buttonColors(containerColor = CyanNeon.copy(alpha = 0.2f), contentColor = CyanNeon)
                                ) {
                                    Text(Res.get(R.string.abrir))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineCatalogTab(
    catalogItems: List<CatalogPluginItem>,
    isLoading: Boolean,
    errorMessage: String?,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedCategory: PluginCategory?,
    onCategorySelect: (PluginCategory?) -> Unit,
    onRefresh: () -> Unit,
    onInstall: (CatalogPluginItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text(Res.get(R.string.buscar_no_catalogo_online), color = OnSurfaceMuted) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CyanNeon) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = Res.get(R.string.limpar), tint = OnSurfaceMuted)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Categories horizontal list
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { onCategorySelect(null) },
                    label = { Text(Res.get(R.string.todos)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyanNeon.copy(alpha = 0.2f),
                        selectedLabelColor = CyanNeon
                    )
                )
            }
            items(PluginCategory.entries) { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { onCategorySelect(cat) },
                    label = { Text(cat.displayName) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = cat.color.copy(alpha = 0.2f),
                        selectedLabelColor = cat.color
                    )
                )
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = CyanNeon)
                    Spacer(Modifier.height(12.dp))
                    Text(Res.get(R.string.conectando_a_knifeswiss_yotserlab_com), color = OnSurfaceMuted)
                }
            }
        } else if (errorMessage != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Icon(Icons.Default.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(Res.get(R.string.nao_foi_possivel_carregar_o_catalogo), fontWeight = FontWeight.Bold, color = OnSurfaceLight)
                    Spacer(Modifier.height(4.dp))
                    Text(errorMessage, color = OnSurfaceMuted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRefresh) {
                        Text(Res.get(R.string.tentar_novamente))
                    }
                }
            }
        } else {
            val filtered = catalogItems.filter { item ->
                val matchesQuery = searchQuery.isBlank() ||
                        item.manifest.name.contains(searchQuery, ignoreCase = true) ||
                        item.manifest.description.contains(searchQuery, ignoreCase = true)
                val matchesCat = selectedCategory == null || item.manifest.category == selectedCategory
                matchesQuery && matchesCat
            }

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(Res.get(R.string.nenhum_plugin_disponivel_no_momento), color = OnSurfaceMuted)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filtered, key = { it.manifest.id }) { item ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, item.manifest.category.color.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(item.manifest.category.color.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Extension,
                                            contentDescription = null,
                                            tint = item.manifest.category.color,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.manifest.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = OnSurfaceLight
                                        )
                                        Text(
                                            text = Res.get(R.string.por_x_vx, (item.manifest.author), (item.manifest.version)),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = OnSurfaceMuted
                                        )
                                    }
                                }

                                if (item.manifest.description.isNotBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = item.manifest.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OnSurfaceMuted
                                    )
                                }

                                Spacer(Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (item.manifest.fileSize > 0) {
                                        Text(
                                            text = Res.get(R.string.x_1024_kb, (item.manifest.fileSize / 1024)),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = OnSurfaceMuted
                                        )
                                    } else {
                                        Spacer(Modifier.width(1.dp))
                                    }

                                    if (item.isUpdateAvailable) {
                                        Button(
                                            onClick = { onInstall(item) },
                                            colors = ButtonDefaults.buttonColors(containerColor = AmberGold)
                                        ) {
                                            Icon(Icons.Default.Update, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(Res.get(R.string.atualizar_vx, (item.manifest.version)))
                                        }
                                    } else if (item.isInstalled) {
                                        Surface(
                                            color = SuccessGreen.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(14.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Instalado", color = SuccessGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    } else {
                                        Button(
                                            onClick = { onInstall(item) },
                                            colors = ButtonDefaults.buttonColors(containerColor = CyanNeon)
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = DeepDark)
                                            Spacer(Modifier.width(4.dp))
                                            Text("Instalar", color = DeepDark, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
