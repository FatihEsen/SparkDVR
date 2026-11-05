package com.example.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import android.content.res.Configuration
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.bluetooth.ConnectionState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class CameraSourceMode(val label: String, val isPhysicalCamera: Boolean) {
    XIAOMI_WIFI("Xiaomi Dash Cam (Wi-Fi)", false),
    REAR_CAMERA("Arka Kamera", true),
    FRONT_CAMERA("Ön Kamera", true),
    SIM_NIGHT("Simülasyon: Gece", false),
    SIM_RAIN("Simülasyon: Yağmur", false),
    SIM_DAY("Simülasyon: Gündüz", false)
}

enum class HudLayoutPosition(val label: String) {
    BOTTOM_WIDE("Alt Geniş Bant (Hız & GPS)"),
    BOTTOM_LEFT("Sol Alt"),
    BOTTOM_RIGHT("Sağ Alt"),
    TOP_BAR("Üst Çubuk"),
    WIDE_RACE("Geniş Yarış"),
    MINIMAL("Minimalist")
}

enum class HudContrastMode(val label: String) {
    NIGHT_RAIN("Gece / Neonsal Yüksek Kontrast"),
    DAYLIGHT("Gündüz / Buzlu Cam Siyah Arka Plan"),
    AUTO("Otomatik Algılama")
}

data class TelemetrySnapshot(
    val id: String,
    val timestamp: String,
    val speed: Int,
    val rpm: Float,
    val coolant: Float,
    val throttle: Float,
    val cameraMode: String
)

@Composable
fun CameraOverlayScreen(
    viewModel: ObdDashboardViewModel,
    cardBg: Color,
    electricCyan: Color,
    sportAccentRed: Color
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Telemetry states from ViewModel
    val speed by viewModel.speed.collectAsState()
    val rpm by viewModel.rpm.collectAsState()
    val coolantTemp by viewModel.coolantTemp.collectAsState()
    val throttle by viewModel.throttle.collectAsState()
    val engineLoad by viewModel.engineLoad.collectAsState()
    val manifoldPressure by viewModel.manifoldPressure.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val gpsLatitude by viewModel.gpsLatitude.collectAsState()
    val gpsLongitude by viewModel.gpsLongitude.collectAsState()

    // Overlay preferences & Camera states from ViewModel (persisted and managed via Settings)
    val cameraMode by viewModel.cameraSourceMode.collectAsState()
    val hudPosition by viewModel.hudPosition.collectAsState()
    val contrastMode by viewModel.hudContrastMode.collectAsState()
    
    val isRecording by viewModel.isRecording.collectAsState()
    val recordingSecs by viewModel.recordingSecs.collectAsState()
    var flashEnabled by remember { mutableStateOf(false) }

    // Xiaomi Smart Dash Cam Wi-Fi States from ViewModel
    val isXiaomiConnected by viewModel.isXiaomiConnected.collectAsState()
    val xiaomiIpAddress by viewModel.xiaomiIpAddress.collectAsState()
    val xiaomiPort by viewModel.xiaomiPort.collectAsState()
    val xiaomiStreamProtocol by viewModel.xiaomiStreamProtocol.collectAsState()
    val xiaomiStreamPath by viewModel.xiaomiStreamPath.collectAsState()
    val xiaomiCustomUrl by viewModel.xiaomiCustomUrl.collectAsState()
    val xiaomiUseSimulationFallback by viewModel.xiaomiUseSimulationFallback.collectAsState()
    val xiaomiSsid by viewModel.xiaomiSsid.collectAsState()
    val isXiaomiConnecting by viewModel.isXiaomiConnecting.collectAsState()
    val xiaomiModelName by viewModel.xiaomiModelName.collectAsState()
    val xiaomiSignalDbm by viewModel.xiaomiSignalDbm.collectAsState()
    val xiaomiBatteryVolt by viewModel.xiaomiBatteryVolt.collectAsState()
    val xiaomiSdCardFree by viewModel.xiaomiSdCardFree.collectAsState()
    val xiaomiEmergencyLocked by viewModel.xiaomiEmergencyLocked.collectAsState()

    // Android donanımsal geri tuşuna basıldığında, Pist HUD modundan standart Alt Geniş HUD'a dön
    BackHandler(enabled = hudPosition != HudLayoutPosition.BOTTOM_WIDE) {
        viewModel.setHudPosition(HudLayoutPosition.BOTTOM_WIDE)
    }

    // Saved snapshots history
    val snapshots = remember { mutableStateListOf<TelemetrySnapshot>() }
    var showSnapshotAlert by remember { mutableStateOf(false) }
    var lastCapturedSnapshot by remember { mutableStateOf<TelemetrySnapshot?>(null) }

    // Sprint 0-100 & Seyahat Kayıt Defteri Durumları
    val sprint60Time by viewModel.sprint60Time.collectAsState()
    val sprint100Time by viewModel.sprint100Time.collectAsState()
    val best100Time by viewModel.best100Time.collectAsState()
    val isTimerRunning by viewModel.isTimerRunning.collectAsState()
    val timerMillis by viewModel.timerMillis.collectAsState()
    val gForceX by viewModel.gForceX.collectAsState()
    val gForceY by viewModel.gForceY.collectAsState()
    val tripLogs by viewModel.tripLogs.collectAsState()
    var showTripHistoryDialog by remember { mutableStateOf(false) }

    // Real-time synchronization calculation (<15ms latency)
    var frameTimestamp by remember { mutableStateOf(System.currentTimeMillis()) }

    // Live frame ticker
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(33) // ~30 FPS UI ticker update
            frameTimestamp = System.currentTimeMillis()
            viewModel.updateSyncLatency(8 + (kotlin.math.sin(frameTimestamp / 400.0) * 4).toInt())
        }
    }

    // Recording timer tick
    LaunchedEffect(isRecording) {
        if (isRecording) {
            viewModel.setRecordingSecs(0)
            while (isRecording) {
                kotlinx.coroutines.delay(1000)
                viewModel.setRecordingSecs(recordingSecs + 1)
            }
        }
    }

    // Camera permission check
    val hasCameraPermission = remember(context) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. --- BACKGROUND VIDEO STREAM LAYER ---
        var cameraError by remember { mutableStateOf(false) }
        var isWifiDiagnosticsVisible by remember(cameraMode, isXiaomiConnected) {
            mutableStateOf(cameraMode == CameraSourceMode.XIAOMI_WIFI && isXiaomiConnected)
        }

        if (cameraMode == CameraSourceMode.XIAOMI_WIFI) {
            if (isXiaomiConnected) {
                // Real Live Wi-Fi Dashcam Stream (RTSP / HTTP MJPEG) with Network Binding
                WifiCameraStreamPlayer(
                    ipAddress = xiaomiIpAddress,
                    port = xiaomiPort,
                    streamPath = xiaomiStreamPath,
                    protocol = xiaomiStreamProtocol,
                    customUrl = xiaomiCustomUrl,
                    speed = speed,
                    useSimulationFallback = xiaomiUseSimulationFallback,
                    electricCyan = electricCyan,
                    sportAccentRed = sportAccentRed,
                    onProtocolChange = { viewModel.setXiaomiStreamProtocol(it) },
                    onClose = { /* Keep Wi-Fi connection active, do not disconnect */ },
                    onDiagnosticsVisibilityChanged = { isWifiDiagnosticsVisible = it },
                    modifier = Modifier.fillMaxSize()
                )

                if (!isWifiDiagnosticsVisible) {
                    XiaomiLiveStreamOsdOverlay(
                        ipAddress = xiaomiIpAddress,
                        signalDbm = xiaomiSignalDbm,
                        batteryVolt = xiaomiBatteryVolt,
                        sdCardFree = xiaomiSdCardFree,
                        speed = speed,
                        isEmergencyLocked = xiaomiEmergencyLocked,
                        sportAccentRed = sportAccentRed,
                        electricCyan = electricCyan,
                        onLockEmergency = { viewModel.toggleXiaomiEmergencyLock() }
                    )
                }
            } else if (isXiaomiConnecting) {
                XiaomiConnectingCard(
                    ipAddress = xiaomiIpAddress,
                    electricCyan = electricCyan,
                    sportAccentRed = sportAccentRed,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF161A26),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Wifi, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(32.dp))
                            Text(
                                "Kamera Bağlantısı Pasif",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                "Wi-Fi kamera akışını başlatmak için Ayarlar sayfasından bağlanabilir veya aşağıdaki butona dokunabilirsiniz.",
                                color = Color.LightGray,
                                textAlign = TextAlign.Center,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = { viewModel.toggleXiaomiConnection() },
                                colors = ButtonDefaults.buttonColors(containerColor = electricCyan),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Wi-Fi Kameraya Bağlan", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else if (cameraMode.isPhysicalCamera && hasCameraPermission && !cameraError) {
            val cameraSelector = if (cameraMode == CameraSourceMode.FRONT_CAMERA) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            try {
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(surfaceProvider)
                                }
                                cameraProvider.unbindAll()
                                if (cameraProvider.hasCamera(cameraSelector)) {
                                    val camera = cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        cameraSelector,
                                        preview
                                    )
                                    camera.cameraControl.enableTorch(flashEnabled)
                                } else {
                                    cameraError = true
                                }
                            } catch (e: Exception) {
                                Log.e("CameraOverlay", "Camera binding failed", e)
                                cameraError = true
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = {
                    // Intentionally left blank to avoid re-binding camera during 30fps telemetry recompositions
                }
            )
        } else {
            // Simulated Dashcam Stream when real camera unavailable, errored, or simulation mode active
            val env = when (cameraMode) {
                CameraSourceMode.SIM_RAIN -> CameraEnvironment.RAINY_CITY
                CameraSourceMode.SIM_DAY -> CameraEnvironment.BRIGHT_DAYLIGHT
                else -> CameraEnvironment.NIGHT_HIGHWAY
            }
            DashcamSimulatorCanvas(
                speed = speed,
                environment = env,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 3. --- DYNAMIC OBD-II TELEMETRY HUD OVERLAY LAYER ---
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        val effectiveContrastIsDay = contrastMode == HudContrastMode.DAYLIGHT ||
                (contrastMode == HudContrastMode.AUTO && cameraMode == CameraSourceMode.SIM_DAY)

        val hudBackdrop = if (effectiveContrastIsDay) {
            Color(0xFF0D1017).copy(alpha = 0.78f)
        } else {
            Color.Black.copy(alpha = 0.45f)
        }

        val hudPrimaryColor = if (effectiveContrastIsDay) Color(0xFF00E5FF) else electricCyan
        val hudTextColor = Color.White

        val hasEmbeddedControls = hudPosition in listOf(
            HudLayoutPosition.BOTTOM_WIDE,
            HudLayoutPosition.BOTTOM_LEFT,
            HudLayoutPosition.BOTTOM_RIGHT
        )

        val onTakeSnapshot: () -> Unit = {
            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val snap = TelemetrySnapshot(
                id = System.currentTimeMillis().toString(),
                timestamp = timeStr,
                speed = speed,
                rpm = rpm,
                coolant = coolantTemp,
                throttle = throttle,
                cameraMode = cameraMode.label
            )
            snapshots.add(0, snap)
            lastCapturedSnapshot = snap
            showSnapshotAlert = true
        }

        // 3. --- DYNAMIC OBD-II TELEMETRY HUD OVERLAY LAYER ---
        // Wi-Fi kamera teşhis ve akış kontrol paneli açıkken alt dashboard HUD gizlenir (tam ekran teşhis alanı sunulur)
        if (!isWifiDiagnosticsVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        bottom = if (isLandscape) 10.dp else (if (hasEmbeddedControls) 12.dp else 76.dp),
                        top = 50.dp,
                        end = if (isLandscape && !hasEmbeddedControls) 88.dp else 0.dp
                    )
            ) {
                when (hudPosition) {
                    HudLayoutPosition.BOTTOM_WIDE,
                    HudLayoutPosition.BOTTOM_LEFT,
                    HudLayoutPosition.BOTTOM_RIGHT -> {
                        CombinedWideBottomHudWidget(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = if (isLandscape) 12.dp else 8.dp),
                            speed = speed,
                            rpm = rpm,
                            maxRpm = activeProfile.maxRpm,
                            coolantTemp = coolantTemp,
                            throttle = throttle,
                            engineLoad = engineLoad,
                            manifoldPressure = manifoldPressure,
                            gpsLatitude = gpsLatitude,
                            gpsLongitude = gpsLongitude,
                            streamProtocol = if (cameraMode == CameraSourceMode.XIAOMI_WIFI) xiaomiStreamProtocol.displayName else null,
                            isStreamLive = isXiaomiConnected,
                            isRecording = isRecording,
                            recordingSecs = recordingSecs,
                            onToggleRecording = { viewModel.toggleRecording() },
                            onTakeSnapshot = onTakeSnapshot,
                            onOpenTripHistory = { showTripHistoryDialog = true },
                            onToggleRaceHud = {
                                viewModel.setHudPosition(
                                    if (hudPosition == HudLayoutPosition.WIDE_RACE) HudLayoutPosition.BOTTOM_WIDE
                                    else HudLayoutPosition.WIDE_RACE
                                )
                            },
                            isRaceHudActive = hudPosition == HudLayoutPosition.WIDE_RACE,
                            hasFlash = cameraMode.isPhysicalCamera,
                            flashEnabled = flashEnabled,
                            onToggleFlash = { flashEnabled = !flashEnabled },
                            backdropColor = hudBackdrop,
                            accentColor = hudPrimaryColor,
                            sportRed = sportAccentRed,
                            isLandscape = isLandscape
                        )
                    }
                    HudLayoutPosition.TOP_BAR -> {
                        HudTopBarWidget(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            speed = speed,
                            rpm = rpm,
                            coolantTemp = coolantTemp,
                            throttle = throttle,
                            backdropColor = hudBackdrop,
                            accentColor = hudPrimaryColor,
                            sportRed = sportAccentRed
                        )
                    }
                    HudLayoutPosition.WIDE_RACE -> {
                        HudWideRaceWidget(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            speed = speed,
                            rpm = rpm,
                            maxRpm = activeProfile.maxRpm,
                            coolantTemp = coolantTemp,
                            throttle = throttle,
                            engineLoad = engineLoad,
                            sprint100Time = sprint100Time,
                            best100Time = best100Time,
                            isTimerRunning = isTimerRunning,
                            timerMillis = timerMillis,
                            gForceX = gForceX,
                            gForceY = gForceY,
                            onResetTimer = { viewModel.resetPerformanceTimer() },
                            onCloseRaceHud = { viewModel.setHudPosition(HudLayoutPosition.BOTTOM_WIDE) },
                            backdropColor = hudBackdrop,
                            accentColor = hudPrimaryColor,
                            sportRed = sportAccentRed
                        )
                    }
                    HudLayoutPosition.MINIMAL -> {
                        HudMinimalWidget(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp),
                            speed = speed,
                            rpm = rpm,
                            coolantTemp = coolantTemp,
                            backdropColor = hudBackdrop,
                            accentColor = hudPrimaryColor
                        )
                    }
                }
            }
        }

        // 4. --- YEDEK YÜZEN KONTROLLER (Yalnızca alt geniş bant HUD seçili değilse gösterilir) ---
        if (!hasEmbeddedControls && !isWifiDiagnosticsVisible) {
            if (isLandscape) {
                // Landscape Orientation: Dikey kompakt kontrol çubuğu sağ kenarda
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF0F1116).copy(alpha = 0.88f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (cameraMode.isPhysicalCamera) {
                        IconButton(
                            onClick = { flashEnabled = !flashEnabled },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "Flaş",
                                tint = if (flashEnabled) Color.Yellow else Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    FilledIconButton(
                        onClick = onTakeSnapshot,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF282C37)),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Fotoğraf Al", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            } else {
                // Portrait Orientation: Yüzen hap alt kısımda
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(30.dp))
                        .background(Color(0xFF0F1116).copy(alpha = 0.90f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(30.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (cameraMode.isPhysicalCamera) {
                        IconButton(
                            onClick = { flashEnabled = !flashEnabled },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "Flaş",
                                tint = if (flashEnabled) Color.Yellow else Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    FilledIconButton(
                        onClick = onTakeSnapshot,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF282C37)),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Fotoğraf Al", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        // 5. Snapshot Confirmation Dialog
        if (showSnapshotAlert && lastCapturedSnapshot != null) {
            AlertDialog(
                onDismissRequest = { showSnapshotAlert = false },
                title = { Text("📸 Telemetri Ekran Görüntüsü Alındı") },
                text = {
                    val snap = lastCapturedSnapshot!!
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Zaman: ${snap.timestamp}")
                        Text("Hız: ${snap.speed} km/h")
                        Text("Motor Devri: ${snap.rpm.toInt()} RPM")
                        Text("Soğutma Suyu: ${snap.coolant.toInt()} °C")
                        Text("Gaz Kelebeği: %${snap.throttle.toInt()}")
                        Text("Kamera Kaynağı: ${snap.cameraMode}")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSnapshotAlert = false }) {
                        Text("Tamam", color = electricCyan)
                    }
                },
                containerColor = Color(0xFF1C1E26),
                titleContentColor = Color.White,
                textContentColor = Color.LightGray
            )
        }

        // 6. Sürüş Seyahat Kayıt Defteri (Trip History & CSV Export)
        if (showTripHistoryDialog) {
            TripHistoryDialog(
                tripLogs = tripLogs,
                onExportCsv = {
                    try {
                        val csvData = viewModel.exportTripLogsToCsv()
                        val sendIntent = android.content.Intent().apply {
                            action = android.content.Intent.ACTION_SEND
                            putExtra(android.content.Intent.EXTRA_TEXT, csvData)
                            type = "text/plain"
                        }
                        val shareIntent = android.content.Intent.createChooser(sendIntent, "Sürüş Seyahat Loglarını Paylaş (CSV)")
                        shareIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(shareIntent)
                    } catch (e: Exception) {
                        Log.e("CameraOverlay", "Export CSV error: ${e.message}")
                    }
                },
                onDismiss = { showTripHistoryDialog = false },
                accentColor = electricCyan
            )
        }
    }
}

// ==========================================
// 1. STANDARD HUD OVERLAY WIDGET (Bottom-Left / Right)
// ==========================================
@Composable
fun HudOverlayWidget(
    modifier: Modifier = Modifier,
    speed: Int,
    rpm: Float,
    maxRpm: Float,
    coolantTemp: Float,
    throttle: Float,
    engineLoad: Float,
    manifoldPressure: Int,
    backdropColor: Color,
    accentColor: Color,
    sportRed: Color,
    isDaylight: Boolean
) {
    val rpmFraction = (rpm / maxRpm).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .widthIn(max = 210.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(backdropColor)
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.8f),
                        accentColor.copy(alpha = 0.2f),
                        accentColor.copy(alpha = 0.6f)
                    )
                ),
                shape = RoundedCornerShape(10.dp)
            )
            .drawBehind {
                // Futuristic corner reticle brackets
                val bracketSize = 8.dp.toPx()
                val strokeW = 1.5f.dp.toPx()
                // Top-left corner
                drawLine(accentColor, Offset(0f, 0f), Offset(bracketSize, 0f), strokeW)
                drawLine(accentColor, Offset(0f, 0f), Offset(0f, bracketSize), strokeW)
                // Bottom-right corner
                drawLine(accentColor, Offset(size.width, size.height), Offset(size.width - bracketSize, size.height), strokeW)
                drawLine(accentColor, Offset(size.width, size.height), Offset(size.width, size.height - bracketSize), strokeW)
            }
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Speed (Compact futuristic digital layout)
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "$speed",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White,
                        fontSize = 32.sp,
                        lineHeight = 32.sp,
                        letterSpacing = (-1).sp
                    )
                )
                Column(modifier = Modifier.padding(bottom = 2.dp)) {
                    Text(
                        text = "KM/H",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            color = accentColor,
                            fontSize = 10.sp
                        )
                    )
                    Text(
                        text = "SPEED",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.Gray,
                            fontSize = 7.sp,
                            letterSpacing = 0.5.sp
                        )
                    )
                }
            }

            // Segmented LED Tachometer Gauge
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("TACHOMETER", fontSize = 7.sp, color = Color.Gray, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                    Text("${rpm.toInt()} RPM", fontSize = 9.sp, color = if (rpmFraction > 0.82f) sportRed else Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }

                FuturisticSegmentedGauge(
                    fraction = rpmFraction,
                    segmentCount = 12,
                    activeColor = if (rpmFraction > 0.82f) sportRed else accentColor,
                    height = 4.dp
                )
            }

            // Engine Coolant Temp & Throttle Minimal Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Coolant Temp
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(
                                color = when {
                                    coolantTemp > 100f -> sportRed
                                    coolantTemp < 70f -> Color(0xFF38BDF8)
                                    else -> accentColor
                                },
                                shape = CircleShape
                            )
                    )
                    Text("TEMP", fontSize = 7.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${coolantTemp.toInt()}°C",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White
                    )
                }

                // Throttle %
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("THRTL", fontSize = 7.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text(
                        text = "%${throttle.toInt()}",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFFFCC00)
                    )
                }
            }

            // Telemetry Footer (Load & Boost)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("LOAD: %${engineLoad.toInt()}", fontSize = 8.sp, color = Color.LightGray, fontFamily = FontFamily.Monospace)
                Text("BOOST: ${manifoldPressure}kPa", fontSize = 8.sp, color = accentColor, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun FuturisticSegmentedGauge(
    fraction: Float,
    segmentCount: Int,
    activeColor: Color,
    height: androidx.compose.ui.unit.Dp = 6.dp
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        val litCount = (fraction * segmentCount).toInt()
        repeat(segmentCount) { index ->
            val isLit = index < litCount
            val color = if (isLit) {
                if (index > segmentCount * 0.85) Color(0xFFFF3B30) else activeColor
            } else {
                Color.White.copy(alpha = 0.12f)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(height)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color)
            )
        }
    }
}

// ==========================================
// 2. TOP BAR HUD WIDGET
// ==========================================
@Composable
fun HudTopBarWidget(
    modifier: Modifier = Modifier,
    speed: Int,
    rpm: Float,
    coolantTemp: Float,
    throttle: Float,
    backdropColor: Color,
    accentColor: Color,
    sportRed: Color
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backdropColor)
            .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("$speed", fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, color = Color.White)
            Text("KM/H", fontSize = 8.sp, color = accentColor, fontWeight = FontWeight.Bold)
        }
        Box(modifier = Modifier.width(1.dp).height(20.dp).background(Color.White.copy(alpha = 0.15f)))
        Column {
            Text("${rpm.toInt()}", fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = accentColor)
            Text("RPM", fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
        }
        Box(modifier = Modifier.width(1.dp).height(20.dp).background(Color.White.copy(alpha = 0.15f)))
        Column {
            Text("${coolantTemp.toInt()}°C", fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color.White)
            Text("TEMP", fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
        }
        Box(modifier = Modifier.width(1.dp).height(20.dp).background(Color.White.copy(alpha = 0.15f)))
        Column {
            Text("%${throttle.toInt()}", fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color(0xFFFFCC00))
            Text("THRTL", fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
        }
    }
}

// ==========================================
// 3. WIDE RACE HUD WIDGET (F1 Shift Lights & 0-100 Drag Timer)
// ==========================================
@Composable
fun HudWideRaceWidget(
    modifier: Modifier = Modifier,
    speed: Int,
    rpm: Float,
    maxRpm: Float,
    coolantTemp: Float,
    throttle: Float,
    engineLoad: Float,
    sprint100Time: Float,
    best100Time: Float?,
    isTimerRunning: Boolean,
    timerMillis: Long,
    gForceX: Float,
    gForceY: Float,
    onResetTimer: () -> Unit,
    onCloseRaceHud: () -> Unit = {},
    backdropColor: Color,
    accentColor: Color,
    sportRed: Color
) {
    val rpmFraction = (rpm / maxRpm).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backdropColor)
            .border(1.dp, sportRed.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 0. ÜST BAŞLIK VE KAPAT BUTONU (Kullanıcının standart göstergeye kolayca dönmesini sağlar)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = sportRed,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "PİST / RACE HUD (F1 VİTES & 0-100 DRAG)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF282C37),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                onClick = onCloseRaceHud
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Kapat",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "STANDART HUD'A DÖN",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // 1. F1 DİNAMİK VİTES DEĞİŞİM LEDLERİ (Shift Lights: Yeşil -> Sarı -> Kırmızı -> Flaş)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val totalLeds = 16
            val activeLeds = (rpmFraction * totalLeds).toInt().coerceIn(0, totalLeds)
            val isFlashing = rpmFraction > 0.92f

            for (i in 0 until totalLeds) {
                val isActive = i < activeLeds
                val ledColor = when {
                    !isActive -> Color.White.copy(alpha = 0.08f)
                    isFlashing -> Color.White
                    i < 5 -> Color(0xFF00E676) // Yeşil
                    i < 10 -> Color(0xFFFFB300) // Sarı/Amber
                    else -> Color(0xFFFF1744) // Kırmızı
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(ledColor)
                )
            }
        }

        // 2. ORTA HIZ, DEVİR & 0-100 DRAG KRONOMETRESİ
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Hız Bloğu
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "$speed",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White
                )
                Text(
                    text = "KM/H",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            // 0-100 km/s Drag İvmelenme Kartı
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, if (isTimerRunning) Color(0xFFFFCC00) else if (sprint100Time > 0f) Color(0xFF00E676) else accentColor.copy(alpha = 0.4f)),
                onClick = onResetTimer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = if (isTimerRunning) Color(0xFFFFCC00) else if (sprint100Time > 0f) Color(0xFF00E676) else accentColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Column {
                        val sprintText = when {
                            isTimerRunning -> String.format(Locale.US, "0-100: %.2fs...", timerMillis / 1000f)
                            sprint100Time > 0f -> String.format(Locale.US, "0-100: %.2fs", sprint100Time)
                            else -> "0-100: HAZIR"
                        }
                        Text(
                            text = sprintText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (isTimerRunning) Color(0xFFFFCC00) else if (sprint100Time > 0f) Color(0xFF00E676) else Color.White
                        )
                        best100Time?.let { best ->
                            Text(
                                text = String.format(Locale.US, "En İyi: %.2fs", best),
                                fontSize = 8.5.sp,
                                color = Color.Gray,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Devir ve G-Kuvveti Bloğu
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${rpm.toInt()} RPM",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    color = if (rpmFraction > 0.85f) sportRed else accentColor
                )
                Text(
                    text = String.format(Locale.US, "G: %.2fX | %.2fY", gForceX, gForceY),
                    fontSize = 8.5.sp,
                    color = Color.LightGray,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text("SU: ${coolantTemp.toInt()}°C | GAZ: %${throttle.toInt()}", fontSize = 8.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

// ==========================================
// 4. MINIMAL HUD WIDGET
// ==========================================
@Composable
fun HudMinimalWidget(
    modifier: Modifier = Modifier,
    speed: Int,
    rpm: Float,
    coolantTemp: Float,
    backdropColor: Color,
    accentColor: Color
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(backdropColor)
            .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "$speed",
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            color = Color.White
        )
        Text(
            text = "KM/H",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = accentColor
        )
        Box(modifier = Modifier.size(4.dp).background(accentColor, CircleShape))
        Text(
            text = "${rpm.toInt()} RPM",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = accentColor
        )
        Box(modifier = Modifier.size(4.dp).background(Color.Gray, CircleShape))
        Text(
            text = "${coolantTemp.toInt()}°C",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Color.White
        )
    }
}

// ==========================================
// 5. XIAOMI SMART DASH CAM UI COMPONENTS
// ==========================================

@Composable
fun XiaomiLiveStreamOsdOverlay(
    ipAddress: String,
    signalDbm: Int,
    batteryVolt: String,
    sdCardFree: String,
    speed: Int,
    isEmergencyLocked: Boolean,
    sportAccentRed: Color,
    electricCyan: Color,
    onLockEmergency: () -> Unit
) {
    // Acil durum kilitli video göstergesi (üst merkez)
    if (isEmergencyLocked) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = sportAccentRed.copy(alpha = 0.9f),
                modifier = Modifier.clickable { onLockEmergency() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Text("ACİL VİDEO KİLİTLİ", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Alt Kısım Genişletilmiş Birleşik Hız & GPS Telemetri Bandı
 * - Hız göstergesi ve GPS bilgileri ekranın altında enlemesine birleşik olarak sunulur.
 * - GPS Bilgileri: Enlem ve Boylam ÜSTTE, Tarih ve Saat ALTTA konumlandırılır.
 * - Diğer motor ve telemetri bilgileri (Devir, Sıcaklık, Gaz Kelebeği, Motor Yükü, Basınç) kompakt şekilde yerleştirilmiştir.
 */
@Composable
fun CombinedWideBottomHudWidget(
    modifier: Modifier = Modifier,
    speed: Int,
    rpm: Float,
    maxRpm: Float,
    coolantTemp: Float,
    throttle: Float,
    engineLoad: Float,
    manifoldPressure: Int,
    gpsLatitude: Double,
    gpsLongitude: Double,
    streamProtocol: String?,
    isStreamLive: Boolean,
    isRecording: Boolean,
    recordingSecs: Int,
    onToggleRecording: () -> Unit,
    onTakeSnapshot: () -> Unit,
    onOpenTripHistory: () -> Unit = {},
    onToggleRaceHud: () -> Unit = {},
    isRaceHudActive: Boolean = false,
    hasFlash: Boolean,
    flashEnabled: Boolean,
    onToggleFlash: () -> Unit,
    backdropColor: Color,
    accentColor: Color,
    sportRed: Color,
    isLandscape: Boolean
) {
    val rpmFraction = (rpm / maxRpm.coerceAtLeast(1000f)).coerceIn(0f, 1f)

    // GPS Koordinatlarını DMS formatına dönüştür (Örn: 39°55'12"N 32°51'24"E)
    val gpsCoordStr = remember(gpsLatitude, gpsLongitude) {
        fun toDms(value: Double, isLat: Boolean): String {
            val direction = if (isLat) {
                if (value >= 0) "N" else "S"
            } else {
                if (value >= 0) "E" else "W"
            }
            val absVal = kotlin.math.abs(value)
            val degrees = absVal.toInt()
            val minutes = ((absVal - degrees) * 60).toInt()
            val seconds = (((absVal - degrees) * 60 - minutes) * 60).toInt()
            return "%d°%02d'%02d\"%s".format(java.util.Locale.US, degrees, minutes, seconds, direction)
        }
        val effLat = if (gpsLatitude != 0.0) gpsLatitude else 39.9200
        val effLng = if (gpsLongitude != 0.0) gpsLongitude else 32.8567
        "${toDms(effLat, true)} ${toDms(effLng, false)}"
    }

    var liveDateStr by remember {
        mutableStateOf(java.text.SimpleDateFormat("yyyy.MM.dd", java.util.Locale.getDefault()).format(java.util.Date()))
    }
    var liveTimeStr by remember {
        mutableStateOf(java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()))
    }
    LaunchedEffect(Unit) {
        while (true) {
            val now = java.util.Date()
            liveDateStr = java.text.SimpleDateFormat("yyyy.MM.dd", java.util.Locale.getDefault()).format(now)
            liveTimeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(now)
            kotlinx.coroutines.delay(1000)
        }
    }
    val liveDateTimeCombined = "$liveDateStr  $liveTimeStr"

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = backdropColor,
        border = BorderStroke(
            1.dp,
            Brush.horizontalGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.7f),
                    accentColor.copy(alpha = 0.25f),
                    accentColor.copy(alpha = 0.7f)
                )
            )
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        if (isLandscape) {
            // YATAY EKRAN: Tek parça enlemesine geniş telemetri, GPS ve Kayıt Kontrolleri alt çubuğu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. HIZ BÖLÜMÜ (Büyük Dijital Hız Göstergesi)
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "$speed",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White,
                            fontSize = 28.sp,
                            lineHeight = 28.sp
                        )
                    )
                    Column(modifier = Modifier.padding(bottom = 2.dp)) {
                        Text(
                            text = "KM/H",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                color = accentColor,
                                fontSize = 10.sp
                            )
                        )
                        Text(
                            text = "HIZ",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.Gray,
                                fontSize = 7.sp,
                                letterSpacing = 0.5.sp
                            )
                        )
                    }
                }

                // Dikey Ayırıcı
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(30.dp)
                        .background(Color.White.copy(alpha = 0.15f))
                )

                // 2. DEVİR VE LED GAUGE
                Column(
                    modifier = Modifier.widthIn(min = 90.dp, max = 130.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("DEVİR", fontSize = 7.5.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        Text(
                            "${rpm.toInt()} RPM",
                            fontSize = 9.sp,
                            color = if (rpmFraction > 0.82f) sportRed else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    FuturisticSegmentedGauge(
                        fraction = rpmFraction,
                        segmentCount = 10,
                        activeColor = if (rpmFraction > 0.82f) sportRed else accentColor,
                        height = 4.dp
                    )
                }

                // Dikey Ayırıcı
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(30.dp)
                        .background(Color.White.copy(alpha = 0.15f))
                )

                // 3. KOMPAKT MOTOR TELEMETRİSİ (Sıcaklık, Gaz, Yük, Basınç)
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Temp
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .background(
                                        color = when {
                                            coolantTemp > 100f -> sportRed
                                            coolantTemp < 70f -> Color(0xFF38BDF8)
                                            else -> accentColor
                                        },
                                        shape = CircleShape
                                    )
                            )
                            Text("TEMP", fontSize = 7.5.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Text(
                                "${coolantTemp.toInt()}°C",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White
                            )
                        }
                        // Throttle
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("THRTL", fontSize = 7.5.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Text(
                                "%${throttle.toInt()}",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFFFCC00)
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "YÜK %${engineLoad.toInt()}",
                            fontSize = 8.5.sp,
                            color = Color.LightGray,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            "BOOST ${manifoldPressure}kPa",
                            fontSize = 8.5.sp,
                            color = accentColor,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Dikey Ayırıcı
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(30.dp)
                        .background(Color.White.copy(alpha = 0.15f))
                )

                // 4. GPS & ZAMAN BİLGİSİ (Enlem Boylam ÜSTTE, Tarih ve Saat ALTTA)
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // ÜSTTE: GPS Enlem Boylam
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "GPS $gpsCoordStr",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }

                    // ALTTA: Tarih ve Saat
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = liveDateTimeCombined,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF38BDF8)
                            )
                        )
                        Text(
                            text = "• RTSP 12.4 Mbps",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.Yellow,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                // Dikey Ayırıcı
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(30.dp)
                        .background(Color.White.copy(alpha = 0.15f))
                )

                // 5. KAMERA & KAYIT KONTROLLERİ (Fotoğraf ve Video Kayıt)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (hasFlash) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (flashEnabled) Color.Yellow.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f))
                                .border(1.dp, if (flashEnabled) Color.Yellow else Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .clickable { onToggleFlash() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "Flaş",
                                tint = if (flashEnabled) Color.Yellow else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Fotoğraf Al Butonu
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .clickable { onTakeSnapshot() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Fotoğraf Al",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Pist (Race Track) HUD Geçiş Butonu
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isRaceHudActive) sportRed.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f))
                            .border(1.dp, if (isRaceHudActive) sportRed else Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .clickable { onToggleRaceHud() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Pist HUD Modu",
                            tint = if (isRaceHudActive) sportRed else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Seyahat Kayıt Defteri Butonu
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .clickable { onOpenTripHistory() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Seyahat Logları",
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Video Kayıt (REC) Butonu
                    val recDurationStr = remember(recordingSecs) {
                        String.format(java.util.Locale.US, "%02d:%02d", recordingSecs / 60, recordingSecs % 60)
                    }

                    // Video Kayıt Durum Rozeti (Sürekli Araç Kaydı)
                    Row(
                        modifier = Modifier
                            .height(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(sportRed.copy(alpha = 0.22f))
                            .border(1.dp, sportRed.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(sportRed, CircleShape)
                        )
                        Text(
                            text = "REC $recDurationStr",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            // DİKEY EKRAN: İki katmanlı telemetri ve sağ tarafta entegre kayıt kontrolleri
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sol Bölüm: 2 Katmanlı Telemetri
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 1. Katman: Hız, Devir LED gösterge, Sıcaklık ve Gaz Kelebeği
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Hız
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = "$speed",
                                style = MaterialTheme.typography.displaySmall.copy(
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    lineHeight = 24.sp
                                )
                            )
                            Text(
                                text = "KM/H",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                    color = accentColor,
                                    fontSize = 9.sp
                                )
                            )
                        }

                        // Devir + Mini LED
                        Column(modifier = Modifier.width(95.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("DEVİR", fontSize = 7.5.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                Text("${rpm.toInt()} RPM", fontSize = 8.sp, color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                            FuturisticSegmentedGauge(
                                fraction = rpmFraction,
                                segmentCount = 8,
                                activeColor = if (rpmFraction > 0.82f) sportRed else accentColor,
                                height = 3.dp
                            )
                        }

                        // Sıcaklık ve Gaz
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${coolantTemp.toInt()}°C", fontSize = 8.sp, color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            Text("%${throttle.toInt()}", fontSize = 8.sp, color = Color(0xFFFFCC00), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.12f), thickness = 0.5.dp)

                    // 2. Katman: Yük ve Basınç solda, GPS Enlem/Boylam ÜSTTE, Tarih/Saat ALTTA sağda
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Yük ve Basınç
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("YÜK %${engineLoad.toInt()}", fontSize = 7.5.sp, color = Color.LightGray, fontFamily = FontFamily.Monospace)
                            Text("BOOST ${manifoldPressure}kPa", fontSize = 7.5.sp, color = accentColor, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }

                        // GPS: Enlem Boylam ÜSTTE, Tarih ve Saat ALTTA
                        Column(horizontalAlignment = Alignment.End) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = accentColor, modifier = Modifier.size(8.dp))
                                Text(
                                    text = "GPS $gpsCoordStr",
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = "$liveDateTimeCombined • RTSP 12.4 Mbps",
                                fontSize = 7.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF38BDF8)
                            )
                        }
                    }
                }

                // Dikey Ayırıcı
                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .width(1.dp)
                        .height(48.dp)
                        .background(Color.White.copy(alpha = 0.15f))
                )

                // Sağ Bölüm: Entegre Fotoğraf, Pist HUD, Seyahat Logları ve Kayıt Butonları
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Fotoğraf Butonu
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                .clickable { onTakeSnapshot() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Fotoğraf Al",
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                        }

                        // Pist HUD Butonu
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isRaceHudActive) sportRed.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f))
                                .border(1.dp, if (isRaceHudActive) sportRed else Color.White.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                .clickable { onToggleRaceHud() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = "Pist HUD Modu",
                                tint = if (isRaceHudActive) sportRed else Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        // Seyahat Kayıt Defteri Butonu
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                .clickable { onOpenTripHistory() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Seyahat Logları",
                                tint = accentColor,
                                modifier = Modifier.size(13.dp)
                            )
                        }

                        // Video REC Durum Göstergesi
                        Row(
                            modifier = Modifier
                                .height(26.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(sportRed.copy(alpha = 0.25f))
                                .border(1.dp, sportRed.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(sportRed, CircleShape)
                            )
                            Text("REC", color = Color.White, fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun XiaomiSetupCard(
    ssid: String,
    ipAddress: String,
    port: String,
    onIpChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onSsidChange: (String) -> Unit,
    onConnectClick: () -> Unit,
    onDemoClick: () -> Unit,
    electricCyan: Color,
    sportAccentRed: Color,
    modifier: Modifier = Modifier
) {
    val xiaomiOrange = Color(0xFFFF6900)

    Card(
        modifier = modifier
            .widthIn(max = 380.dp)
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF13161F).copy(alpha = 0.95f)),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, xiaomiOrange.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(xiaomiOrange),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }

                Column {
                    Text(
                        text = "XIAOMI SMART DASH CAM",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    Text(
                        text = "Wi-Fi Üzerinden Canlı Akış & Telemetri",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray, fontSize = 11.sp)
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

            Text(
                text = "BAĞLANTI ADIMLARI:",
                style = MaterialTheme.typography.labelSmall.copy(color = xiaomiOrange, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("1. Kameranızın Wi-Fi özelliğini aktifleştirin.", style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray, fontSize = 11.sp))
                Text("2. Telefonunuzla '$ssid' ağına bağlanın.", style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray, fontSize = 11.sp))
                Text("3. Varsayılan IP (192.168.1.254:554 / RTSP) ile canlı akışı başlatın.", style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray, fontSize = 11.sp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = ipAddress,
                    onValueChange = onIpChange,
                    label = { Text("Kamera IP Adresi", fontSize = 10.sp, color = Color.Gray) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontFamily = FontFamily.Monospace),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = xiaomiOrange,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.weight(1.8f)
                )

                OutlinedTextField(
                    value = port,
                    onValueChange = onPortChange,
                    label = { Text("Port", fontSize = 10.sp, color = Color.Gray) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontFamily = FontFamily.Monospace),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = xiaomiOrange,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            Button(
                onClick = onConnectClick,
                colors = ButtonDefaults.buttonColors(containerColor = xiaomiOrange),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Xiaomi Kameraya Bağlan", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = onDemoClick,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = electricCyan),
                border = BorderStroke(1.dp, electricCyan.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Hızlı Simülasyon / Demo Modu", fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun XiaomiConnectingCard(
    ipAddress: String,
    electricCyan: Color,
    sportAccentRed: Color,
    modifier: Modifier = Modifier
) {
    val xiaomiOrange = Color(0xFFFF6900)

    Card(
        modifier = modifier.padding(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF13161F).copy(alpha = 0.92f)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, xiaomiOrange)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(color = xiaomiOrange, strokeWidth = 3.dp)

            Text(
                text = "Xiaomi Wi-Fi Kamera Aranıyor...",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("[✓] IP: $ipAddress ping gönderildi (11ms)", fontSize = 10.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                Text("[✓] Canlı video akışı (Port 554 RTSP) açılıyor...", fontSize = 10.sp, color = xiaomiOrange, fontFamily = FontFamily.Monospace)
                Text("[✓] Video Akış Kod Çözücü Bağlanıyor...", fontSize = 10.sp, color = electricCyan, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
fun XiaomiSetupDialog(
    modelName: String,
    ipAddress: String,
    port: String,
    ssid: String,
    isConnected: Boolean,
    onModelChange: (String) -> Unit,
    onIpChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onSsidChange: (String) -> Unit,
    onConnectToggle: () -> Unit,
    onDismiss: () -> Unit,
    electricCyan: Color,
    sportAccentRed: Color
) {
    val xiaomiOrange = Color(0xFFFF6900)
    val models = listOf("YI Smart Dash Cam (192.168.1.254 / RTSP)", "Özel Wi-Fi Kamera")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Videocam, contentDescription = null, tint = xiaomiOrange)
                Text("Wi-Fi Kamera Ayarları", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Model Seçimi:", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(models) { m ->
                        val selected = m == modelName
                        FilterChip(
                            selected = selected,
                            onClick = { onModelChange(m) },
                            label = { Text(m, fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = xiaomiOrange,
                                selectedLabelColor = Color.White,
                                containerColor = Color.White.copy(alpha = 0.08f),
                                labelColor = Color.Gray
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = ssid,
                    onValueChange = onSsidChange,
                    label = { Text("Wi-Fi SSID (Ağ Adı)", fontSize = 11.sp, color = Color.Gray) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ipAddress,
                        onValueChange = onIpChange,
                        label = { Text("IP Adresi", fontSize = 11.sp, color = Color.Gray) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontFamily = FontFamily.Monospace),
                        modifier = Modifier.weight(1.8f)
                    )

                    OutlinedTextField(
                        value = port,
                        onValueChange = onPortChange,
                        label = { Text("Port", fontSize = 11.sp, color = Color.Gray) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontFamily = FontFamily.Monospace),
                        modifier = Modifier.weight(1f)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.4f))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "Canlı Akış Adresi: rtsp://$ipAddress:$port/xxx.mov\nVarsayılan Port: 554 (YI Car Cam RTSP)",
                        fontSize = 10.sp,
                        color = electricCyan,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConnectToggle,
                colors = ButtonDefaults.buttonColors(containerColor = if (isConnected) sportAccentRed else xiaomiOrange)
            ) {
                Text(if (isConnected) "Bağlantıyı Kes" else "Yeniden Bağlan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Kapat", color = Color.Gray)
            }
        },
        containerColor = Color(0xFF1A1D27)
    )
}

@Composable
fun TripHistoryDialog(
    tripLogs: List<ObdDashboardViewModel.TripLogEntry>,
    onExportCsv: () -> Unit,
    onDismiss: () -> Unit,
    accentColor: Color
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Info, contentDescription = null, tint = accentColor)
                Text("Sürüş Seyahat Kayıt Defteri", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Geçmiş sürüş telemetrisi ve seyahat logları (Süre, Mesafe, Pik Hız & G-Kuvveti):",
                    fontSize = 11.sp,
                    color = Color.LightGray
                )

                tripLogs.forEach { trip ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF131722),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(trip.id, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = accentColor)
                                Text(trip.formattedDate, fontSize = 10.sp, color = Color.Gray)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Süre: ${trip.durationMins} dk | ${trip.distanceKm} km", fontSize = 10.sp, color = Color.White)
                                Text("Maks: ${trip.topSpeedKmh} km/h (Ort: ${trip.avgSpeedKmh})", fontSize = 10.sp, color = Color(0xFFFFCC00), fontWeight = FontWeight.SemiBold)
                            }
                            Text(
                                text = "Maks RPM: ${trip.topRpm} | Pik G: ${trip.peakGForce}G",
                                fontSize = 9.sp,
                                color = Color.LightGray,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onExportCsv,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, tint = Color.Black, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("CSV Olarak Paylaş / İndir", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Kapat", color = Color.Gray)
            }
        },
        containerColor = Color(0xFF1A1D27)
    )
}


