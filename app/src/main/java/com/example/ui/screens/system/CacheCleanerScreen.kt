package com.example.ui.screens.system

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.ui.components.AppTopAppBar
import com.example.ui.theme.DeepDark
import com.example.R
import com.example.Res

@Composable
fun CacheCleanerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    
    Scaffold(
        containerColor = DeepDark,
        topBar = {
            AppTopAppBar(
                title = Res.get(R.string.gerenciador_de_cache),
                onBack = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(Res.get(R.string.gerenciador_de_cache), style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(24.dp))
            Text(Res.get(R.string.por_seguranca_o_android_restringe_a_limp), style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { 
                context.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) // ARQ-R10 
            }) {
                Text(Res.get(R.string.abrir_configuracoes_de_armazenamento))
            }
        }
    }
}
