package com.example.ui.screens.network

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.AppDatabase
import com.example.data.HistoryLog
import com.example.data.HistoryRepository
import com.example.utils.net.NetworkUtils
import kotlinx.coroutines.launch

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.navigation.NavController
import com.example.R
import com.example.Res

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PingScreen(initialHost: String? = null, navController: NavController? = null) {
    var host by remember(initialHost) { mutableStateOf(initialHost ?: "google.com") }
    var isRunning by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf("") }
    
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val repository = remember { HistoryRepository(AppDatabase.getDatabase(context).historyDao()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Res.get(R.string.network_tests_ping)) },
                navigationIcon = {
                    if (navController != null) {
                        IconButton(onClick = {
                            if (!navController.popBackStack()) {
                                navController.navigate(com.example.ui.navigation.Screen.Dashboard.route)
                            }
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = Res.get(R.string.voltar)
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text(Res.get(R.string.target_host_ip)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isRunning
            )
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    isRunning = true
                    output = Res.get(R.string.pinging_x_n, host)
                    coroutineScope.launch {
                        val result = NetworkUtils.ping(host) // RED-R07
                        output += result
                        isRunning = false
                        
                        repository.insert(HistoryLog(
                            toolName = Res.get(R.string.ping_network_test),
                            resultSummary = Res.get(R.string.pinged_x_result_length_x, host, (result.length))
                        ))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isRunning && host.isNotBlank()
            ) {
                Text(if (isRunning) Res.get(R.string.running) else Res.get(R.string.run_ping_test))
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = output,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                textStyle = MaterialTheme.typography.bodySmall
            )
        }
    }
}
