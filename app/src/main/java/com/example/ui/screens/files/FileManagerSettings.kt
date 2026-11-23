package com.example.ui.screens.files

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.automirrored.filled.*
import com.example.R
import com.example.Res

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerSettingsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Res.get(R.string.configuracoes), color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF2C2C2C),
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF9F9F9))
        ) {
            item { SettingsSectionHeader(Res.get(R.string.configuracoes_gerais)) }
            item { SettingsItem(title = Res.get(R.string.apps_padrao)) }
            item { SettingsItem(title = Res.get(R.string.aplicativos_incorporados)) }
            
            item { SettingsSectionHeader(Res.get(R.string.arquivos_temporarios)) }
            item { SettingsItem(title = Res.get(R.string.arquivos_temporarios)) }
            
            item { SettingsSectionHeader(Res.get(R.string.configuracoes_da_lixeira)) }
            item { SettingsCheckboxItem(title = Res.get(R.string.usar_a_lixeira_como_padrao), checked = true) }
            item { SettingsCheckboxItem(title = Res.get(R.string.exibir_confirmacao_da_lixeira), checked = true) }
            
            item { SettingsSectionHeader(Res.get(R.string.configuracoes_de_notificacoes)) }
            item { SettingsItem(title = Res.get(R.string.o_armazenamento_esta_cheio), subtitle = Res.get(R.string.mostrar_quando_o_armazenamento_exceder_9)) }
            
            item { SettingsSectionHeader(Res.get(R.string.configuracoes_avancadas)) }
            item { SettingsCheckboxItem(title = Res.get(R.string.sempre_mostrar_o_icone_de_navegacao), checked = true) }
            item { SettingsCheckboxItem(title = Res.get(R.string.detectar_conexao_usb), checked = true) }
            item { SettingsCheckboxItem(title = Res.get(R.string.exibir_historico), checked = true) }
            item { SettingsCheckboxItem(title = Res.get(R.string.exibir_armazenamento_do_sistema), checked = false) }
            item { SettingsCheckboxItem(title = Res.get(R.string.mostrar_armazenamento_duplo), checked = false) }
            item { SettingsItem(title = Res.get(R.string.unidade_de_tamanho_de_arquivo), subtitle = "GB") }
            
            item { SettingsSectionHeader(Res.get(R.string.mostrar_menus_avancados)) }
            item { SettingsCheckboxItem(title = Res.get(R.string.adicionar_marcador), checked = true) }
            item { SettingsCheckboxItem(title = Res.get(R.string.adicionar_a_tela_inicial), checked = false) }
            item { SettingsCheckboxItem(title = Res.get(R.string.mostrar_ocultar), checked = false) }
            item { SettingsCheckboxItem(title = Res.get(R.string.abrir_como), checked = false) }
            
            item { SettingsSectionHeader("Sobre") }
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        color = Color(0xFF00897B),
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsItem(title: String, subtitle: String? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(text = title, fontSize = 16.sp, color = Color.Black)
        if (subtitle != null) {
            Text(text = subtitle, fontSize = 14.sp, color = Color.Gray)
        }
    }
}

@Composable
fun SettingsCheckboxItem(title: String, checked: Boolean) {
    var isChecked by remember { mutableStateOf(checked) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isChecked = !isChecked }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, fontSize = 16.sp, color = Color.Black, modifier = Modifier.weight(1f))
        Checkbox(
            checked = isChecked,
            onCheckedChange = { isChecked = it },
            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF00897B), uncheckedColor = Color.Gray)
        )
    }
}
