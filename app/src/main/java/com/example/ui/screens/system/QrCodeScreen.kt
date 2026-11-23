package com.example.ui.screens.system
import com.example.utils.crypto.TotpSecretCrypto

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.ui.theme.*
import com.example.ui.components.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.navigation.NavController
import com.example.data.AppDatabase
import com.example.data.TotpAccount
import com.example.ui.navigation.Screen
import com.example.utils.QrSnapshotDecoder
import com.example.utils.TotpGenerator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.pm.PackageManager
import android.Manifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.compose.ui.text.AnnotatedString
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import com.example.R
import com.example.Res

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrCodeScreen(navController: NavController? = null) {
    var textInput by remember { mutableStateOf("https://google.com") }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isScanningMode by remember { mutableStateOf(false) }
    var scannedText by remember { mutableStateOf("") }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val clipboardManager = LocalClipboardManager.current
    var clipboardText by remember { mutableStateOf("") }
    var isPixInClipboard by remember { mutableStateOf(false) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                QrSnapshotDecoder.decodeSnapshotUri(context, uri) { raw ->
                    if (!raw.isNullOrBlank()) {
                        scannedText = raw
                        isScanningMode = false
                    } else {
                        Toast.makeText(context, Res.get(R.string.nenhum_qr_code_encontrado_na_imagem), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    LaunchedEffect(isScanningMode) {
        if (isScanningMode) {
            val perm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            hasCameraPermission = perm == PackageManager.PERMISSION_GRANTED
            if (!hasCameraPermission) {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    // Camera executor
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    
    // Cleanup camera executor on dispose
    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    // Read clipboard content safely on launch
    LaunchedEffect(Unit) {
        try {
            val text = clipboardManager.getText()?.text
            if (text != null && text.isNotBlank()) {
                clipboardText = text
                // Check if it is likely a PIX Copy and Paste string (starts with "000201" or contains "pix.bcb.gov.br")
                isPixInClipboard = text.trim().startsWith("000201") || text.contains("pix.bcb.gov.br", ignoreCase = true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    LaunchedEffect(textInput) {
        if (textInput.isNotBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    val size = 512
                    val bitMatrix = QRCodeWriter().encode(textInput, BarcodeFormat.QR_CODE, size, size) // COM-R01
                    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
                    for (x in 0 until size) {
                        for (y in 0 until size) {
                            bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        qrBitmap = bitmap
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            qrBitmap = null
        }
    }

    Scaffold(
        containerColor = DeepDark,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        Res.get(R.string.ferramentas_qr),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceLight,
                    )
                },
                navigationIcon = {
                    if (navController != null) {
                        IconButton(onClick = { 
                            if (!navController.popBackStack()) {
                                navController.navigate(Screen.Dashboard.route)
                            }
                        }) {
                            Icon(
                                androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = Res.get(R.string.voltar),
                                tint = CyanNeon,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { isScanningMode = !isScanningMode }) {
                        Icon(
                            if (isScanningMode) Icons.Default.QrCode else Icons.Default.QrCodeScanner,
                            contentDescription = if (isScanningMode) Res.get(R.string.gerar_qr) else Res.get(R.string.escanear_qr),
                            tint = ElectricViolet
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepDark),
            )
        }
    ) { padding ->
        if (isScanningMode) {
            // Scanning Mode
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(DeepDark)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CategoryHeader(label = Res.get(R.string.escanear_qr_code), accentColor = ElectricViolet, modifier = Modifier.padding(0.dp))
                Spacer(modifier = Modifier.height(16.dp))
                
                // Camera Preview with Neon border
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(androidx.compose.ui.graphics.Color.Black)
                        .border(1.dp, ElectricViolet.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                ) {
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx)
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            
                            cameraProviderFuture.addListener({
                                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                                
                                val resolutionSelector = ResolutionSelector.Builder()
                                    .setResolutionStrategy(ResolutionStrategy(android.util.Size(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                                    .build()
                                
                                val preview = Preview.Builder()
                                    .setResolutionSelector(resolutionSelector)
                                    .build()
                                    .also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }
                                
                                val imageAnalyzer = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .setResolutionSelector(resolutionSelector)
                                    .build()
                                    .also {
                                        it.setAnalyzer(cameraExecutor) { imageProxy ->
                                            processImageProxy(imageProxy) { result ->
                                                if (result.isNotEmpty()) {
                                                    scannedText = result
                                                    isScanningMode = false
                                                }
                                            }
                                        }
                                    }
                                
                                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                                
                                try {
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        cameraSelector,
                                        preview,
                                        imageAnalyzer
                                    )
                                } catch (exc: Exception) {
                                    exc.printStackTrace()
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                            
                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    // Scan overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(220.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(2.dp, ElectricViolet, RoundedCornerShape(12.dp))
                                .background(ElectricViolet.copy(alpha = 0.05f))
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                if (!hasCameraPermission) {
                    NeonCard(borderColor = WarningAmber, modifier = Modifier.fillMaxWidth()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(Res.get(R.string.permissao_de_camera_necessaria), color = WarningAmber, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text(Res.get(R.string.para_escanear_qr_codes_com_a_camera_auto), color = OnSurfaceMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)
                            ) {
                                Text(Res.get(R.string.autorizar_camera), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                Text(
                    text = Res.get(R.string.aponte_a_camera_para_o_qr_code_para_ler),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceMuted,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    border = BorderStroke(1.dp, ElectricViolet.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricViolet),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Image, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(Res.get(R.string.escanear_foto_da_galeria_screenshot), fontWeight = FontWeight.Bold)
                }
            }
        } else {
            // Generation Mode
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CategoryHeader(label = Res.get(R.string.gerador_de_qr_code), accentColor = ElectricViolet, modifier = Modifier.padding(0.dp))
                Spacer(modifier = Modifier.height(6.dp))
            
                Text(
                    text = Res.get(R.string.insira_um_texto_ou_url_abaixo_para_gerar),
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceMuted,
                    modifier = Modifier
                        .align(Alignment.Start)
                        .padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    label = { Text(Res.get(R.string.texto_url_ou_chave_pix), color = OnSurfaceMuted) },
                    leadingIcon = { Icon(Icons.Default.QrCode, contentDescription = null, tint = ElectricViolet) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricViolet,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = OnSurfaceLight,
                        unfocusedTextColor = OnSurfaceLight,
                        focusedContainerColor = DarkNavy,
                        unfocusedContainerColor = DarkNavy,
                        focusedLabelColor = ElectricViolet,
                        cursorColor = ElectricViolet
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Special PIX Clipboard Card
                AnimatedVisibility(
                    visible = clipboardText.isNotBlank(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    val cardBorderColor = if (isPixInClipboard) AmberGold else DarkBorder
                    NeonCard(
                        borderColor = cardBorderColor,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = null,
                                    tint = if (isPixInClipboard) AmberGold else ElectricViolet
                                )
                                Text(
                                    text = if (isPixInClipboard) Res.get(R.string.codigo_pix_detectado_no_clipboard) else Res.get(R.string.area_de_transferencia),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurfaceLight
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = if (clipboardText.length > 120) clipboardText.take(120) + "..." else clipboardText,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = OnSurfaceMuted
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            GradientButton(
                                text = if (isPixInClipboard) Res.get(R.string.gerar_qr_code_pix) else Res.get(R.string.gerar_deste_conteudo),
                                onClick = {
                                    textInput = clipboardText
                                },
                                modifier = Modifier.fillMaxWidth(),
                                icon = {
                                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = DeepDark, modifier = Modifier.size(18.dp))
                                },
                                startColor = if (isPixInClipboard) AmberGold else ElectricViolet,
                                endColor = if (isPixInClipboard) AmberGold.copy(alpha = 0.7f) else ElectricViolet.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Main QR Code Area
                NeonCard(
                    borderColor = if (textInput.trim().startsWith("000201")) AmberGold else ElectricViolet,
                    modifier = Modifier.size(290.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        qrBitmap?.let {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = Res.get(R.string.qr_code),
                                    modifier = Modifier
                                        .size(200.dp)
                                        .background(androidx.compose.ui.graphics.Color.White)
                                        .padding(8.dp)
                                )
                                
                                if (textInput.trim().startsWith("000201")) {
                                    Text(
                                        text = Res.get(R.string.pix_br_code_ativo),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AmberGold,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                            }
                        } ?: run {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.QrCode,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = OnSurfaceDim
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = Res.get(R.string.sem_codigo_gerado),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = OnSurfaceMuted
                                )
                            }
                        }
                    }
                }
                
                // Copy/share actions for the generated QR content
                if (qrBitmap != null && textInput.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(textInput))
                                Toast.makeText(context, Res.get(R.string.texto_copiado), Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanNeon)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Copiar", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, textInput)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Compartilhar"))
                            },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanNeon)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Texto", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { shareQrBitmap(context, qrBitmap) },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, ElectricViolet.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricViolet)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = ElectricViolet, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("QR", fontSize = 12.sp)
                        }
                    }
                }

                // Scanned Result Display
                if (scannedText.isNotEmpty()) {
                    val cleanText = scannedText.trim()
                    val isOtpMigration = cleanText.startsWith("otpauth-migration://", ignoreCase = true)
                    val isOtpSingle = cleanText.startsWith("otpauth://", ignoreCase = true)

                    Spacer(modifier = Modifier.height(24.dp))

                    if (isOtpMigration) {
                        val migrationPayload = remember(cleanText) {
                            TotpGenerator.parseGoogleAuthMigrationUriFull(cleanText)
                        }
                        val migrationAccounts = migrationPayload.accounts
                        val batchSize = migrationPayload.batchSize
                        val batchIndex = migrationPayload.batchIndex

                        NeonCard(
                            borderColor = ElectricViolet,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.PhonelinkLock,
                                        contentDescription = null,
                                        tint = ElectricViolet,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = if (batchSize > 1) {
                                            Res.get(R.string.lote_2fa_parte_x_1_de_x, (batchIndex + 1), batchSize)
                                        } else {
                                            Res.get(R.string.lote_de_migracao_2fa_detectado)
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = OnSurfaceLight
                                    )
                                }

                                Text(
                                    text = if (migrationAccounts.isNotEmpty()) {
                                        Res.get(R.string.google_authenticator_x_conta_s_2fa_neste, (migrationAccounts.size))
                                    } else {
                                        Res.get(R.string.uri_de_migracao_2fa_do_google_authentica)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = VioletLight,
                                    fontWeight = FontWeight.SemiBold
                                )

                                if (batchSize > 1) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(AmberGold.copy(alpha = 0.12f))
                                            .border(1.dp, AmberGold.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                            .padding(10.dp)
                                    ) {
                                        Column {
                                            Text(
                                                text = Res.get(R.string.exportacao_dividida_pelo_google_auth_x_p, batchSize),
                                                color = AmberGold,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = Res.get(R.string.google_auth_divide_x, batchSize, if (batchIndex + 1 < batchSize) Res.get(R.string.a_parte_x_de_x, batchIndex + 2, batchSize) else Res.get(R.string.as_outras_partes)),
                                                color = OnSurfaceLight,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }

                                if (migrationAccounts.isNotEmpty()) {
                                    val accountPreview = migrationAccounts.take(6).joinToString(", ") {
                                        if (it.issuer.isNotBlank()) "${it.issuer} (${it.account})" else it.account
                                    } + if (migrationAccounts.size > 6) "..." else ""

                                    Text(
                                        text = "Contas neste QR: $accountPreview",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        color = OnSurfaceMuted
                                    )
                                }

                                Text(
                                    text = if (cleanText.length > 100) cleanText.take(100) + "..." else cleanText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = OnSurfaceDim,
                                    maxLines = 2
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                // Options: Import & Continue vs Import & Finish vs System Default
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            val db = AppDatabase.getDatabase(context)
                                            val totpDao = db.totpDao()
                                            scope.launch(Dispatchers.IO) {
                                                val existing = totpDao.getAllList()
                                                val existingSecrets = existing.map { com.example.utils.crypto.TotpSecretCrypto.decryptSecret(it.encryptedSecret).uppercase().replace(" ", "").replace("-", "") }.toSet()
                                                var insertedCount = 0
                                                var skippedCount = 0

                                                migrationAccounts.forEach { params ->
                                                    val cleanSecret = params.secret.uppercase().replace(" ", "").replace("-", "")
                                                    if (!existingSecrets.contains(cleanSecret)) {
                                                        totpDao.insert(
                                                            TotpAccount(
                                                                issuer = params.issuer,
                                                                accountName = params.account,
                                                                encryptedSecret = com.example.utils.crypto.TotpSecretCrypto.encryptSecret(params.secret),
                                                                algorithm = params.algorithm,
                                                                digits = params.digits,
                                                                period = params.period,
                                                                position = existing.size + insertedCount
                                                            )
                                                        )
                                                        insertedCount++
                                                    } else {
                                                        skippedCount++
                                                    }
                                                }

                                                withContext(Dispatchers.Main) {
                                                    val hasNextPart = batchIndex + 1 < batchSize
                                                    val msg = when {
                                                        insertedCount > 0 && skippedCount > 0 -> Res.get(R.string.x_nova_s_conta_s_importada_s_x_ignoradas, insertedCount, skippedCount)
                                                        insertedCount > 0 -> Res.get(R.string.x_conta_s_2fa_importada_s_com_sucesso, insertedCount)
                                                        else -> Res.get(R.string.i_todas_as_x_conta_s_deste_qr_ja_estavam, skippedCount)
                                                    }

                                                    if (hasNextPart) {
                                                        Toast.makeText(
                                                            context,
                                                            Res.get(R.string.x_escaneie_a_parte_x_2_de_x, msg, (batchIndex + 2), batchSize),
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                        scannedText = ""
                                                        isScanningMode = true
                                                    } else {
                                                        Toast.makeText(
                                                            context,
                                                            msg,
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                        navController?.navigate(Screen.TotpAuthenticator.route)
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)
                                    ) {
                                        Icon(Icons.Default.PhonelinkLock, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (batchIndex + 1 < batchSize) {
                                                Res.get(R.string.importar_e_escanear_parte_x_2_de_x, (batchIndex + 2), batchSize)
                                            } else {
                                                Res.get(R.string.importar_e_abrir_no_app_x_contas, (migrationAccounts.size))
                                            },
                                            color = androidx.compose.ui.graphics.Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                try {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanText))
                                                    context.startActivity(intent)
                                                } catch (e: ActivityNotFoundException) {
                                                    Toast.makeText(
                                                        context,
                                                        Res.get(R.string.nenhum_aplicativo_do_sistema_ex_google_a),
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                            border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.7f)),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanNeon)
                                        ) {
                                            Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(Res.get(R.string.padrao_do_sistema), color = CyanNeon, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Button(
                                            onClick = {
                                                navController?.navigate(Screen.TotpAuthenticator.route)
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = DarkNavy),
                                            border = BorderStroke(1.dp, DarkBorder)
                                        ) {
                                            Icon(Icons.Default.PhonelinkLock, contentDescription = null, tint = OnSurfaceMuted, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(Res.get(R.string.ver_no_autenticador), color = OnSurfaceMuted, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    } else if (isOtpSingle) {
                        val singleAccount = remember(cleanText) { TotpGenerator.parseOtpAuthUri(cleanText) }
                        NeonCard(
                            borderColor = ElectricViolet,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Key,
                                        contentDescription = null,
                                        tint = ElectricViolet,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = Res.get(R.string.chave_2fa_otpauth_detectada),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = OnSurfaceLight
                                    )
                                }

                                singleAccount?.let { acc ->
                                    Text(
                                        text = Res.get(R.string.x_x_17, (acc.issuer), (acc.account)),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = VioletLight,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            if (singleAccount != null) {
                                                val db = AppDatabase.getDatabase(context)
                                                val totpDao = db.totpDao()
                                                scope.launch(Dispatchers.IO) {
                                                    val existing = totpDao.getAllList()
                                                    val cleanSecret = singleAccount.secret.uppercase().replace(" ", "").replace("-", "")
                                                    val exists = existing.any { com.example.utils.crypto.TotpSecretCrypto.decryptSecret(it.encryptedSecret).uppercase().replace(" ", "").replace("-", "") == cleanSecret }
                                                    
                                                    if (!exists) {
                                                        totpDao.insert(
                                                            TotpAccount(
                                                                issuer = singleAccount.issuer,
                                                                accountName = singleAccount.account,
                                                                encryptedSecret = com.example.utils.crypto.TotpSecretCrypto.encryptSecret(singleAccount.secret),
                                                                algorithm = singleAccount.algorithm,
                                                                digits = singleAccount.digits,
                                                                period = singleAccount.period,
                                                                position = existing.size
                                                            )
                                                        )
                                                        withContext(Dispatchers.Main) {
                                                            Toast.makeText(
                                                                context,
                                                                Res.get(R.string.conta_2fa_x_adicionada, (singleAccount.issuer)),
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                            navController?.navigate(Screen.TotpAuthenticator.route)
                                                        }
                                                    } else {
                                                        withContext(Dispatchers.Main) {
                                                            Toast.makeText(
                                                                context,
                                                                Res.get(R.string.i_a_conta_x_ja_esta_cadastrada, (singleAccount.issuer)),
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                            navController?.navigate(Screen.TotpAuthenticator.route)
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)
                                    ) {
                                        Icon(Icons.Default.PhonelinkLock, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(Res.get(R.string.abrir_no_proprio_app_importar_2fa), color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanText))
                                                context.startActivity(intent)
                                            } catch (e: ActivityNotFoundException) {
                                                Toast.makeText(
                                                    context,
                                                    Res.get(R.string.nenhum_aplicativo_do_sistema_foi_encontr),
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.7f)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanNeon)
                                    ) {
                                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = CyanNeon)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(Res.get(R.string.abrir_no_padrao_do_sistema), color = CyanNeon, fontWeight = FontWeight.Bold)
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                textInput = scannedText
                                                scannedText = ""
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = DarkNavy),
                                            border = BorderStroke(1.dp, DarkBorder)
                                        ) {
                                            Icon(Icons.Default.QrCode, contentDescription = null, tint = OnSurfaceMuted)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(Res.get(R.string.gerar_qr), color = OnSurfaceMuted)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        NeonCard(
                            borderColor = SuccessGreen,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text(
                                    text = Res.get(R.string.qr_code_escaneado),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = SuccessGreen
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = scannedText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = OnSurfaceLight
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Check if it's a URL
                                    if (cleanText.startsWith("http://", ignoreCase = true) || cleanText.startsWith("https://", ignoreCase = true)) {
                                        Button(
                                            onClick = {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanText))
                                                context.startActivity(intent)
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = CyanNeon)
                                        ) {
                                            Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = DeepDark)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(Res.get(R.string.abrir_link), color = DeepDark, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    
                                    Button(
                                        onClick = {
                                            textInput = scannedText
                                            scannedText = ""
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)
                                    ) {
                                        Icon(Icons.Default.QrCode, contentDescription = null, tint = DeepDark)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(Res.get(R.string.gerar_qr), color = DeepDark, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(cleanText))
                                            Toast.makeText(context, Res.get(R.string.copiado), Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.weight(1f),
                                        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.6f)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SuccessGreen)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Copiar", fontSize = 12.sp)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, cleanText)
                                            }
                                            context.startActivity(Intent.createChooser(sendIntent, "Compartilhar"))
                                        },
                                        modifier = Modifier.weight(1f),
                                        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.6f)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SuccessGreen)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Compartilhar", fontSize = 12.sp)
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

private fun shareQrBitmap(context: android.content.Context, bitmap: Bitmap?) {
    if (bitmap == null) return
    try {
        val file = File(context.filesDir, "qrcode_share.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(sendIntent, Res.get(R.string.compartilhar_qr_code)))
    } catch (e: Exception) {
        Toast.makeText(context, Res.get(R.string.erro_ao_compartilhar_imagem), Toast.LENGTH_SHORT).show()
    }
}

private fun processImageProxy(imageProxy: ImageProxy, onResult: (String) -> Unit) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
            
        val scanner = BarcodeScanning.getClient(options) // COM-R02
        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    val rawValue = barcode.rawValue
                    if (rawValue != null) {
                        onResult(rawValue)
                        break
                    }
                }
            }
            .addOnFailureListener {
                it.printStackTrace()
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}
