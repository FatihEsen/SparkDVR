package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.launch
import android.view.Surface
import android.content.Context
import android.view.WindowManager
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bluetooth.BondedObdDevice
import com.example.bluetooth.ConnectionState
import com.example.bluetooth.Obd2BluetoothManager
import com.example.ui.VehicleProfile
import com.example.ui.ObdDashboardViewModel
import com.example.ui.SpeedometerStyle
import com.example.ui.CameraOverlayScreen
import com.example.ui.SensorsMatrixScreen
import com.example.ui.CameraSourceMode
import com.example.ui.HudLayoutPosition
import com.example.ui.HudContrastMode
import com.example.ui.WifiStreamProtocol
import com.example.ui.theme.MyApplicationTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    ObdDashboardApp(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun ObdDashboardApp(
    modifier: Modifier = Modifier,
    viewModel: ObdDashboardViewModel = viewModel()
) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsState()
    val rpm by viewModel.rpm.collectAsState()
    val speed by viewModel.speed.collectAsState()
    val pairedDevices by viewModel.pairedDevices.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val isPedalPressed by viewModel.isPedalPressed.collectAsState()
    val isManualSliderActive by viewModel.isManualSliderActive.collectAsState()
    val manualRpmValue by viewModel.manualRpmSliderValue.collectAsState()
    val consoleLogs by viewModel.terminalConsoleLogs.collectAsState()

    // Collect advanced diagnostics telemetry states
    val coolantTemp by viewModel.coolantTemp.collectAsState()
    val throttle by viewModel.throttle.collectAsState()
    val engineLoad by viewModel.engineLoad.collectAsState()
    val intakeTemp by viewModel.intakeTemp.collectAsState()
    val manifoldPressure by viewModel.manifoldPressure.collectAsState()
    val fuelLevel by viewModel.fuelLevel.collectAsState()
    val dtcCodes by viewModel.dtcCodes.collectAsState()
    val ecuProtocol by viewModel.ecuProtocol.collectAsState()
    val ecuVin by viewModel.ecuVin.collectAsState()
    val ecuMilStatus by viewModel.ecuMilStatus.collectAsState()
    val isDtcScanning by viewModel.isDtcScanning.collectAsState()
    val dtcScanProgress by viewModel.dtcScanProgress.collectAsState()
    val connectedDeviceAddress by viewModel.connectedDeviceAddress.collectAsState()

    val rpmHistory by viewModel.rpmHistory.collectAsState()
    val loadHistory by viewModel.loadHistory.collectAsState()
    val boostHistory by viewModel.boostHistory.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val recordingSecs by viewModel.recordingSecs.collectAsState()

    val pagerState = rememberPagerState(initialPage = 0) { 5 }
    val coroutineScope = rememberCoroutineScope()

    // Theme assets
    val carbonBg = Color(0xFF0F1115)       // Deep space background
    val cardBg = Color(0xFF1C1E26)         // Cozy card surface
    val sportAccentRed = Color(0xFFD13438) // Vibrant Luxury Sport Red
    val amberWarning = Color(0xFFFF9500)   // Warning Orange
    val electricCyan = Color(0xFF4ADE80)   // Vibrant active connection glowing green
    val terminalBg = Color(0xFF13151D)     // Terminal/console background
    val labelGray = Color(0xFF919196)      // Modern metadata gray

    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.CAMERA)
    } else {
        arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.CAMERA)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.all { it }
        if (granted) {
            viewModel.refreshPairedDevices()
        }
    }

    // Auto-check permissions on launch
    LaunchedEffect(Unit) {
        if (!viewModel.hasBluetoothPermissions()) {
            launcher.launch(permissionsToRequest)
        } else {
            viewModel.refreshPairedDevices()
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .background(carbonBg)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // App Header Status Strip (Minimalist & Sleek HUD)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Smart Spark",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                letterSpacing = 1.sp
                            )
                        )
                        if (pagerState.currentPage == 2) {
                            CameraTopBarIndicators(viewModel, electricCyan, sportAccentRed)
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Kamera Aktif Kayıt Göstergesi (Kırmızı Nokta + REC)
                        if (isRecording) {
                            Surface(
                                color = sportAccentRed.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, sportAccentRed.copy(alpha = 0.6f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    val recTransition = rememberInfiniteTransition(label = "recBlinkLandscape")
                                    val recAlpha by recTransition.animateFloat(
                                        initialValue = 1f,
                                        targetValue = 0.25f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(650, easing = LinearEasing),
                                            repeatMode = RepeatMode.Reverse
                                        ),
                                        label = "recAlphaLandscape"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .background(sportAccentRed.copy(alpha = recAlpha), CircleShape)
                                    )
                                    Text(
                                        text = "REC",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    )
                                }
                            }
                        }

                        // OBD Bağlantı Durumu
                        Surface(
                            color = Color.White.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(
                                            color = when (connectionState) {
                                                is ConnectionState.Connected -> electricCyan
                                                is ConnectionState.Initializing -> Color.Yellow
                                                is ConnectionState.Error -> sportAccentRed
                                                else -> Color.Gray.copy(alpha = 0.6f)
                                            },
                                            shape = CircleShape
                                        )
                                )
                                Text(
                                    text = when (connectionState) {
                                        is ConnectionState.Disconnected -> "Çevrim dışı"
                                        is ConnectionState.Connecting -> "Bağlanıyor..."
                                        is ConnectionState.Initializing -> "Protokol..."
                                        is ConnectionState.Connected -> {
                                            if (connectedDeviceAddress == "00:BB:DD:32:00:11") {
                                                "Sanal OBD"
                                            } else {
                                                val device = pairedDevices.find { it.address == connectedDeviceAddress }
                                                val name = device?.name?.replace("🚗 ", "")?.replace(" (Simülatör)", "") ?: "OBD"
                                                "Bağlı ($name)"
                                            }
                                        }
                                        is ConnectionState.Error -> "OBD Bekleniyor"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color(0xFFC7C7CC),
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 11.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                // Swipeable Page Views (HorizontalPager)
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { page ->
                    when (page) {
                        0 -> CockpitScreen(
                            rpm = rpm,
                            speed = speed,
                            coolantTemp = coolantTemp,
                            throttle = throttle,
                            engineLoad = engineLoad,
                            manifoldPressure = manifoldPressure,
                            fuelLevel = fuelLevel,
                            intakeTemp = intakeTemp,
                            activeProfile = activeProfile,
                            viewModel = viewModel,
                            cardBg = cardBg,
                            electricCyan = electricCyan,
                            sportAccentRed = sportAccentRed
                        )
                        1 -> SensorsMatrixScreen(
                            viewModel = viewModel,
                            cardBg = cardBg,
                            electricCyan = electricCyan,
                            sportAccentRed = sportAccentRed
                        )
                        2 -> CameraOverlayScreen(
                            viewModel = viewModel,
                            cardBg = cardBg,
                            electricCyan = electricCyan,
                            sportAccentRed = sportAccentRed
                        )
                        3 -> DiagnosticsScreen(
                            isDtcScanning = isDtcScanning,
                            dtcScanProgress = dtcScanProgress,
                            dtcCodes = dtcCodes,
                            ecuProtocol = ecuProtocol,
                            ecuVin = ecuVin,
                            ecuMilStatus = ecuMilStatus,
                            viewModel = viewModel,
                            cardBg = cardBg,
                            electricCyan = electricCyan,
                            sportAccentRed = sportAccentRed,
                            amberWarning = amberWarning
                        )
                        4 -> SettingsScreen(
                            viewModel = viewModel,
                            cardBg = cardBg,
                            electricCyan = electricCyan,
                            sportAccentRed = sportAccentRed
                        )
                    }
                }
            }

            VerticalDivider(color = Color.White.copy(alpha = 0.08f))

            // Right-Side Navigation Rail (Vertical Navigation for Landscape)
            NavigationRail(
                containerColor = Color(0xFF13151D),
                modifier = Modifier
                    .fillMaxHeight()
                    .width(72.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    NavigationRailItem(
                        selected = pagerState.currentPage == 0,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                        icon = { Icon(Icons.Default.Speed, contentDescription = "Gösterge") },
                        label = { Text("Gösterge", fontSize = 9.sp, maxLines = 1) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = electricCyan,
                            selectedTextColor = Color.White,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = Color.Transparent
                        )
                    )
                    NavigationRailItem(
                        selected = pagerState.currentPage == 1,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                        icon = { Icon(Icons.Default.Tune, contentDescription = "Sensörler") },
                        label = { Text("Sensörler", fontSize = 9.sp, maxLines = 1) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = electricCyan,
                            selectedTextColor = Color.White,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = Color.Transparent
                        )
                    )
                    NavigationRailItem(
                        selected = pagerState.currentPage == 2,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                        icon = { Icon(Icons.Default.Videocam, contentDescription = "Kamera") },
                        label = { Text("Kamera", fontSize = 9.sp, maxLines = 1) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = electricCyan,
                            selectedTextColor = Color.White,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = Color.Transparent
                        )
                    )
                    NavigationRailItem(
                        selected = pagerState.currentPage == 3,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(3) } },
                        icon = { Icon(Icons.Default.Build, contentDescription = "Teşhis") },
                        label = { Text("Teşhis", fontSize = 9.sp, maxLines = 1) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = electricCyan,
                            selectedTextColor = Color.White,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = Color.Transparent
                        )
                    )
                    NavigationRailItem(
                        selected = pagerState.currentPage == 4,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(4) } },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Ayarlar") },
                        label = { Text("Ayarlar", fontSize = 9.sp, maxLines = 1) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = electricCyan,
                            selectedTextColor = Color.White,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = Color.Transparent
                        )
                    )
                }
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(carbonBg)
        ) {
            // App Header Status Strip (Minimalist & Sleek HUD)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Smart Spark",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                    )
                    if (pagerState.currentPage == 2) {
                        CameraTopBarIndicators(viewModel, electricCyan, sportAccentRed)
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Kamera Aktif Kayıt Göstergesi (Kırmızı Nokta + REC)
                    if (isRecording) {
                        Surface(
                            color = sportAccentRed.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, sportAccentRed.copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                val recTransition = rememberInfiniteTransition(label = "recBlinkPortrait")
                                val recAlpha by recTransition.animateFloat(
                                    initialValue = 1f,
                                    targetValue = 0.25f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(650, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "recAlphaPortrait"
                                )
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(sportAccentRed.copy(alpha = recAlpha), CircleShape)
                                )
                                Text(
                                    text = "REC",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                            }
                        }
                    }

                    // OBD Bağlantı Durumu
                    Surface(
                        color = Color.White.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(
                                        color = when (connectionState) {
                                            is ConnectionState.Connected -> electricCyan
                                            is ConnectionState.Initializing -> Color.Yellow
                                            is ConnectionState.Error -> sportAccentRed
                                            else -> Color.Gray.copy(alpha = 0.6f)
                                        },
                                        shape = CircleShape
                                    )
                            )
                            Text(
                                text = when (connectionState) {
                                    is ConnectionState.Disconnected -> "Çevrim dışı"
                                    is ConnectionState.Connecting -> "Bağlanıyor..."
                                    is ConnectionState.Initializing -> "Protokol..."
                                    is ConnectionState.Connected -> {
                                        if (connectedDeviceAddress == "00:BB:DD:32:00:11") {
                                            "Sanal OBD"
                                        } else {
                                            val device = pairedDevices.find { it.address == connectedDeviceAddress }
                                            val name = device?.name?.replace("🚗 ", "")?.replace(" (Simülatör)", "") ?: "OBD"
                                            "Bağlı ($name)"
                                        }
                                    }
                                    is ConnectionState.Error -> "OBD Bekleniyor"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFFC7C7CC),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            // Swipeable Page Views (HorizontalPager)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                when (page) {
                    0 -> CockpitScreen(
                        rpm = rpm,
                        speed = speed,
                        coolantTemp = coolantTemp,
                        throttle = throttle,
                        engineLoad = engineLoad,
                        manifoldPressure = manifoldPressure,
                        fuelLevel = fuelLevel,
                        intakeTemp = intakeTemp,
                        activeProfile = activeProfile,
                        viewModel = viewModel,
                        cardBg = cardBg,
                        electricCyan = electricCyan,
                        sportAccentRed = sportAccentRed
                    )
                    1 -> SensorsMatrixScreen(
                        viewModel = viewModel,
                        cardBg = cardBg,
                        electricCyan = electricCyan,
                        sportAccentRed = sportAccentRed
                    )
                    2 -> CameraOverlayScreen(
                        viewModel = viewModel,
                        cardBg = cardBg,
                        electricCyan = electricCyan,
                        sportAccentRed = sportAccentRed
                    )
                    3 -> DiagnosticsScreen(
                        isDtcScanning = isDtcScanning,
                        dtcScanProgress = dtcScanProgress,
                        dtcCodes = dtcCodes,
                        ecuProtocol = ecuProtocol,
                        ecuVin = ecuVin,
                        ecuMilStatus = ecuMilStatus,
                        viewModel = viewModel,
                        cardBg = cardBg,
                        electricCyan = electricCyan,
                        sportAccentRed = sportAccentRed,
                        amberWarning = amberWarning
                    )
                    4 -> SettingsScreen(
                        viewModel = viewModel,
                        cardBg = cardBg,
                        electricCyan = electricCyan,
                        sportAccentRed = sportAccentRed
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            NavigationBar(
                containerColor = Color(0xFF13151D),
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
            ) {
                NavigationBarItem(
                    selected = pagerState.currentPage == 0,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                    icon = { Icon(Icons.Default.Speed, contentDescription = "Gösterge") },
                    label = { Text("Gösterge", fontSize = 10.sp, maxLines = 1) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = electricCyan,
                        selectedTextColor = Color.White,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = electricCyan.copy(alpha = 0.15f)
                    )
                )
                NavigationBarItem(
                    selected = pagerState.currentPage == 1,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                    icon = { Icon(Icons.Default.Tune, contentDescription = "Sensörler") },
                    label = { Text("Sensörler", fontSize = 10.sp, maxLines = 1) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = electricCyan,
                        selectedTextColor = Color.White,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = electricCyan.copy(alpha = 0.15f)
                    )
                )
                NavigationBarItem(
                    selected = pagerState.currentPage == 2,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                    icon = { Icon(Icons.Default.Videocam, contentDescription = "Kamera") },
                    label = { Text("Kamera", fontSize = 10.sp, maxLines = 1) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = electricCyan,
                        selectedTextColor = Color.White,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = electricCyan.copy(alpha = 0.15f)
                    )
                )
                NavigationBarItem(
                    selected = pagerState.currentPage == 3,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(3) } },
                    icon = { Icon(Icons.Default.Build, contentDescription = "Teşhis") },
                    label = { Text("Teşhis", fontSize = 10.sp, maxLines = 1) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = electricCyan,
                        selectedTextColor = Color.White,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = electricCyan.copy(alpha = 0.15f)
                    )
                )
                NavigationBarItem(
                    selected = pagerState.currentPage == 4,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(4) } },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Ayarlar") },
                    label = { Text("Ayarlar", fontSize = 10.sp, maxLines = 1) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = electricCyan,
                        selectedTextColor = Color.White,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = electricCyan.copy(alpha = 0.15f)
                    )
                )
            }
        }
    }
}

@Composable
fun MinimalistSpeedometerGauge(
    speed: Int,
    rpm: Float,
    maxRpm: Float,
    electricCyan: Color,
    sportAccentRed: Color,
    modifier: Modifier = Modifier
) {
    val rpmPercent = (rpm / maxRpm).coerceIn(0f, 1f)
    
    // Dynamic border animation based on RPM
    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (rpm > 0f) (3000 - (rpmPercent * 2500)).toInt().coerceAtLeast(300) else 5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Box(
        modifier = modifier
            .size(240.dp)
            .padding(8.dp)
            .drawBehind {
                rotate(rotation) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color.Transparent, 
                                if (rpmPercent > 0.8f) sportAccentRed else electricCyan, 
                                Color.Transparent
                            )
                        ),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 4.dp.toPx())
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f - 12.dp.toPx()

            // Outer subtle track ring
            drawArc(
                color = Color.White.copy(alpha = 0.08f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            )

            // Active RPM arc
            val sweepAngle = rpmPercent * 270f
            val activeColor = if (rpmPercent > 0.82f) sportAccentRed else electricCyan

            drawArc(
                color = activeColor,
                startAngle = 135f,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "$speed",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    letterSpacing = (-2).sp
                )
            )

            Text(
                text = "KM / H",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = electricCyan,
                    letterSpacing = 2.sp
                )
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "%.0f RPM".format(rpm),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.LightGray,
                    fontFamily = FontFamily.Monospace,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    letterSpacing = 1.sp
                )
            )
        }
    }
}

@Composable
fun CockpitScreen(
    rpm: Float,
    speed: Int,
    coolantTemp: Float,
    throttle: Float,
    engineLoad: Float,
    manifoldPressure: Int,
    fuelLevel: Float,
    intakeTemp: Int,
    activeProfile: VehicleProfile,
    viewModel: ObdDashboardViewModel,
    cardBg: Color,
    electricCyan: Color,
    sportAccentRed: Color
) {
    val scrollState = rememberScrollState()
    val speedometerStyle by viewModel.speedometerStyle.collectAsState()
    
    // Auto-animate gauge updates smoothly
    val animatedRpm by animateFloatAsState(
        targetValue = rpm,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "RpmAnimation"
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val isWide = maxWidth > maxHeight

        if (isWide) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Side (Gauge)
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (speedometerStyle != SpeedometerStyle.MINIMALIST) {
                        ShiftLightsIndicator(
                            currentRpm = rpm,
                            maxRpm = activeProfile.maxRpm
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (speedometerStyle == SpeedometerStyle.MINIMALIST) {
                        MinimalistSpeedometerGauge(
                            speed = speed,
                            rpm = animatedRpm,
                            maxRpm = activeProfile.maxRpm,
                            electricCyan = electricCyan,
                            sportAccentRed = sportAccentRed
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(240.dp)
                                .padding(8.dp)
                                .drawBehind {
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(sportAccentRed.copy(alpha = 0.18f), Color.Transparent),
                                            center = center,
                                            radius = size.width / 1.4f
                                        )
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            TachometerGauge(
                                rpm = animatedRpm,
                                maxRpm = activeProfile.maxRpm,
                                modifier = Modifier.fillMaxSize(),
                                cyanColor = electricCyan,
                                redColor = sportAccentRed
                            )

                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                DigitalSevenSegmentDisplay(
                                    text = String.format("%.0f", rpm),
                                    color = if (rpm >= activeProfile.maxRpm * 0.82f) sportAccentRed else Color.White,
                                    digitWidth = 24.dp,
                                    digitHeight = 44.dp,
                                    thickness = 3.5.dp,
                                    spacing = 1.2.dp,
                                    gap = 4.dp
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .offset(y = 48.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Hız",
                                    tint = electricCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                DigitalSevenSegmentDisplay(
                                    text = "$speed",
                                    color = electricCyan,
                                    digitWidth = 14.dp,
                                    digitHeight = 24.dp,
                                    thickness = 2.dp,
                                    spacing = 0.8.dp,
                                    gap = 2.dp
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "km/h",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color(0xFF919196)
                                    )
                                )
                            }
                        }
                    }
                }

                // Right Side (Telemetry)
                Column(
                    modifier = Modifier.weight(1.2f).verticalScroll(scrollState),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CockpitTelemetryData(
                        rpm = rpm,
                        speed = speed,
                        coolantTemp = coolantTemp,
                        throttle = throttle,
                        engineLoad = engineLoad,
                        manifoldPressure = manifoldPressure,
                        fuelLevel = fuelLevel,
                        intakeTemp = intakeTemp,
                        activeProfile = activeProfile,
                        viewModel = viewModel,
                        cardBg = cardBg,
                        electricCyan = electricCyan,
                        sportAccentRed = sportAccentRed
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Redline indicator LED panel (only when not minimalist)
                if (speedometerStyle != SpeedometerStyle.MINIMALIST) {
                    ShiftLightsIndicator(
                        currentRpm = rpm,
                        maxRpm = activeProfile.maxRpm
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Gauge rendering based on selected speedometer style (Minimalist as default)
                if (speedometerStyle == SpeedometerStyle.MINIMALIST) {
                    MinimalistSpeedometerGauge(
                        speed = speed,
                        rpm = animatedRpm,
                        maxRpm = activeProfile.maxRpm,
                        electricCyan = electricCyan,
                        sportAccentRed = sportAccentRed
                    )
                } else {
                    // Radial Tachometer Gauge with Immersive Background Glow
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .padding(8.dp)
                            .drawBehind {
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(sportAccentRed.copy(alpha = 0.18f), Color.Transparent),
                                        center = center,
                                        radius = size.width / 1.4f
                                    )
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        TachometerGauge(
                            rpm = animatedRpm,
                            maxRpm = activeProfile.maxRpm,
                            modifier = Modifier.fillMaxSize(),
                            cyanColor = electricCyan,
                            redColor = sportAccentRed
                        )

                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            DigitalSevenSegmentDisplay(
                                text = String.format("%.0f", rpm),
                                color = if (rpm >= activeProfile.maxRpm * 0.82f) sportAccentRed else Color.White,
                                digitWidth = 24.dp,
                                digitHeight = 44.dp,
                                thickness = 3.5.dp,
                                spacing = 1.2.dp,
                                gap = 4.dp
                            )
                        }

                        Row(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .offset(y = 48.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Hız",
                                tint = electricCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            DigitalSevenSegmentDisplay(
                                text = "$speed",
                                color = electricCyan,
                                digitWidth = 14.dp,
                                digitHeight = 24.dp,
                                thickness = 2.dp,
                                spacing = 0.8.dp,
                                gap = 2.dp
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "km/h",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFF919196)
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                CockpitTelemetryData(
                    rpm = rpm,
                    speed = speed,
                    coolantTemp = coolantTemp,
                    throttle = throttle,
                    engineLoad = engineLoad,
                    manifoldPressure = manifoldPressure,
                    fuelLevel = fuelLevel,
                    intakeTemp = intakeTemp,
                    activeProfile = activeProfile,
                    viewModel = viewModel,
                    cardBg = cardBg,
                    electricCyan = electricCyan,
                    sportAccentRed = sportAccentRed
                )
            }
        }
    }
}

@Composable
fun SimplifiedTelemetryItem(
    title: String,
    value: String,
    unit: String = "",
    cardBg: Color,
    accentColor: Color = Color(0xFF4ADE80),
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color(0xFF8E8E93),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 9.sp,
                    letterSpacing = 0.5.sp
                )
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 18.sp
                    )
                )
                if (unit.isNotEmpty()) {
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = accentColor,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp
                        ),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CockpitTelemetryData(
    rpm: Float,
    speed: Int,
    coolantTemp: Float,
    throttle: Float,
    engineLoad: Float,
    manifoldPressure: Int,
    fuelLevel: Float,
    intakeTemp: Int,
    activeProfile: VehicleProfile,
    viewModel: ObdDashboardViewModel,
    cardBg: Color,
    electricCyan: Color,
    sportAccentRed: Color
) {
    val pedalPosition by viewModel.pedalPosition.collectAsState()
    val timerMillis by viewModel.timerMillis.collectAsState()
    val sprint60 by viewModel.sprint60Time.collectAsState()
    val sprint100 by viewModel.sprint100Time.collectAsState()
    val gForceX by viewModel.gForceX.collectAsState()
    val gForceY by viewModel.gForceY.collectAsState()

    val batteryVoltage = if (rpm > 0f) {
        13.8f + (rpm / 8000f) * 0.4f + (Math.sin(System.currentTimeMillis() / 2000.0) * 0.05).toFloat()
    } else if (fuelLevel > 0f) {
        12.4f
    } else {
        0.0f
    }

    val maxProfileHp = when (activeProfile.id) {
        "symbol_d4f" -> 75f
        "lfa" -> 560f
        "v12_skyline" -> 500f
        "rally" -> 300f
        else -> 150f
    }
    val maxProfileTorque = when (activeProfile.id) {
        "symbol_d4f" -> 105f
        "lfa" -> 480f
        "v12_skyline" -> 600f
        "rally" -> 400f
        else -> 220f
    }

    val calculatedHp = if (rpm > 0f) {
        ((engineLoad / 100f) * (rpm / activeProfile.maxRpm) * maxProfileHp).coerceIn(0f, maxProfileHp + 30f) + (throttle * 0.1f)
    } else {
        0.0f
    }

    val calculatedTorque = if (rpm > 0f) {
        val rpmPeak = when (activeProfile.id) {
            "symbol_d4f" -> 4250f
            "lfa" -> 6800f
            "v12_skyline" -> 4500f
            "rally" -> 3500f
            else -> 2500f
        }
        val rpmDiffFraction = (kotlin.math.abs(rpm - rpmPeak) / activeProfile.maxRpm)
        ((engineLoad / 100f) * maxProfileTorque * (1.0f - 0.4f * rpmDiffFraction)).coerceIn(0f, maxProfileTorque)
    } else {
        0.0f
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Satır 1: Motor Sıcaklığı & Akü Voltajı
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SimplifiedTelemetryItem(
                title = "MOTOR SICAKLIĞI",
                value = "${coolantTemp.toInt()}",
                unit = "°C",
                accentColor = if (coolantTemp > 105) sportAccentRed else electricCyan,
                cardBg = cardBg,
                modifier = Modifier.weight(1f)
            )
            SimplifiedTelemetryItem(
                title = "AKÜ VOLTAJI",
                value = if (batteryVoltage > 0f) String.format(java.util.Locale.US, "%.1f", batteryVoltage) else "12.4",
                unit = "V",
                accentColor = electricCyan,
                cardBg = cardBg,
                modifier = Modifier.weight(1f)
            )
        }

        // Satır 2: Gaz Kelebeği (TPS) & Gaz Pedalı (APP)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SimplifiedTelemetryItem(
                title = "GAZ KELEBEĞİ (TPS)",
                value = "${throttle.toInt()}",
                unit = "%",
                accentColor = if (throttle > 70f) sportAccentRed else Color(0xFF38BDF8),
                cardBg = cardBg,
                modifier = Modifier.weight(1f)
            )
            SimplifiedTelemetryItem(
                title = "GAZ PEDALI (APP)",
                value = "${pedalPosition.toInt()}",
                unit = "%",
                accentColor = if (pedalPosition > 70f) sportAccentRed else electricCyan,
                cardBg = cardBg,
                modifier = Modifier.weight(1f)
            )
        }

        // Satır 3: Motor Yükü & Anlık Güç
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SimplifiedTelemetryItem(
                title = "MOTOR YÜKÜ",
                value = "${engineLoad.toInt()}",
                unit = "%",
                accentColor = Color(0xFFFF9500),
                cardBg = cardBg,
                modifier = Modifier.weight(1f)
            )
            SimplifiedTelemetryItem(
                title = "MOTOR GÜCÜ",
                value = "${calculatedHp.toInt()}",
                unit = "HP",
                accentColor = Color(0xFFFFCC00),
                cardBg = cardBg,
                modifier = Modifier.weight(1f)
            )
        }

        // Satır 4: 0-100 Hızlanma & G-Metre
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 0-100 Süresi Kartı
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                onClick = { viewModel.resetPerformanceTimer() }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "0-100 SÜRESİ",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF8E8E93),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 9.sp,
                                letterSpacing = 0.5.sp
                            )
                        )
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sıfırla",
                            tint = Color.Gray,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val sprintDisplay = when {
                            sprint100 > 0f -> String.format(java.util.Locale.US, "%.2f", sprint100)
                            timerMillis > 0 -> String.format(java.util.Locale.US, "%02d.%01d", timerMillis / 1000, (timerMillis % 1000) / 100)
                            else -> "--"
                        }
                        Text(
                            text = sprintDisplay,
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = if (sprint100 > 0f) Color(0xFF34D399) else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 19.sp
                            )
                        )
                        Text(
                            text = "sn",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = sportAccentRed,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp
                            ),
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                        if (sprint60 > 0f) {
                            Text(
                                text = String.format(java.util.Locale.US, "(60: %.1fs)", sprint60),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.Gray,
                                    fontSize = 9.sp
                                ),
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                    }
                }
            }

            // G-Metre Kartı (Yatay & Dikey Uyumlu)
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "G-METRE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFF8E8E93),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 9.sp,
                            letterSpacing = 0.5.sp
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Mini Hedef Dairesi & Canlı G Noktası
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.2f), modifier = Modifier.align(Alignment.Center))
                            VerticalDivider(color = Color.White.copy(alpha = 0.2f), modifier = Modifier.align(Alignment.Center))

                            val maxG = 1.5f
                            val limitedX = gForceX.coerceIn(-maxG, maxG)
                            val limitedY = gForceY.coerceIn(-maxG, maxG)
                            val offX = (limitedX / maxG) * 9f
                            val offY = (-limitedY / maxG) * 9f

                            Box(
                                modifier = Modifier
                                    .offset(x = offX.dp, y = offY.dp)
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(electricCyan)
                            )
                        }

                        val totalG = kotlin.math.sqrt(gForceX * gForceX + gForceY * gForceY)
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = String.format(java.util.Locale.US, "%.2f", totalG),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 19.sp
                                )
                            )
                            Text(
                                text = "G",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = electricCyan,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.sp
                                ),
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
fun ShiftLightsIndicator(
    currentRpm: Float,
    maxRpm: Float,
    modifier: Modifier = Modifier
) {
    // 10-LED high indicators. Under 55%: off. 55%-85%: green to amber. 85%+: flashing red.
    val percent = currentRpm / maxRpm
    val flashingTransition = rememberInfiniteTransition(label = "FlashingLeds")
    val isFlashingZone = percent >= 0.85f
    
    val activeCount = when {
        currentRpm < 600f -> 0
        currentRpm in 600f..850f -> 1
        else -> {
            val ratio = (currentRpm - 850f) / (maxRpm - 850f)
            val scaledLeds = 1 + (ratio * 9f)
            scaledLeds.toInt().coerceIn(1, 10)
        }
    }

    val flashAlpha by flashingTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(120, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AlphaFlash"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.4f))
            .padding(vertical = 10.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(10) { i ->
            val isLit = i < activeCount

            val ledColor = when {
                !isLit -> Color(0xFF22262F) // Dim/Off
                i < 4 -> Color(0xFF34C759) // Green (First 4)
                i < 8 -> Color(0xFFFF9500) // Amber (Middle 4)
                else -> Color(0xFFFF3B30)   // Redline (Last 2)
            }

            Box(
                modifier = Modifier
                    .size(width = 18.dp, height = 14.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        color = if (isFlashingZone && isLit) {
                            ledColor.copy(alpha = flashAlpha)
                        } else {
                            ledColor
                        }
                    )
                    .border(
                        1.dp,
                        if (isLit) Color.White.copy(alpha = 0.3f) else Color.Transparent,
                        RoundedCornerShape(3.dp)
                    )
            )
        }
    }
}

@Composable
fun TachometerGauge(
    rpm: Float,
    maxRpm: Float,
    modifier: Modifier = Modifier,
    cyanColor: Color,
    redColor: Color
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val center = Offset(width / 2f, height / 2f)
        val radius = size.minDimension / 2f - 16.dp.toPx()

        // 1. Draw dial semi-transparent back track arc (270 degrees total sweep, from 135 to 405)
        drawArc(
            color = Color(0xFF202534),
            startAngle = 135f,
            sweepAngle = 270f,
            useCenter = false,
            style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
        )

        // Calculate dynamic dial sweep based on RPM
        val rpmPercent = (rpm / maxRpm).coerceIn(0f, 1f)
        val sweepAngle = rpmPercent * 270f

        // 2. Draw active glowing sweep line representing real devir speed
        val activeBrush = Brush.linearGradient(
            colors = listOf(cyanColor, Color.Yellow, redColor),
            start = Offset(0f, height),
            end = Offset(width, 0f)
        )
        
        drawArc(
            brush = activeBrush,
            startAngle = 135f,
            sweepAngle = sweepAngle,
            useCenter = false,
            style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
        )

        // 3. Draw redzone danger zone arc marking
        // Starts at 82% of maximum devir threshold
        val redzoneStartOffset = 0.82f * 270f
        val redzoneSweep = (1.0f - 0.82f) * 270f
        drawArc(
            color = redColor.copy(alpha = 0.4f),
            startAngle = 135f + redzoneStartOffset,
            sweepAngle = redzoneSweep,
            useCenter = false,
            style = Stroke(width = 14.dp.toPx())
        )

        // 4. Draw Dial ticks and numbers around the gauge face
        val tickCount = 10
        for (i in 0..tickCount) {
            val angleDeg = 135f + (i * (270f / tickCount))
            val angleRad = Math.toRadians(angleDeg.toDouble())
            
            val isRedZone = i >= tickCount * 0.82
            val tickCol = if (isRedZone) redColor else Color.White.copy(alpha = 0.5f)
            val tickLen = if (i % 2 == 0) 12.dp.toPx() else 6.dp.toPx()
            val strokeW = if (i % 2 == 0) 3.dp.toPx() else 1.5.dp.toPx()

            val outerPoint = Offset(
                x = center.x + (radius - 1.dp.toPx()) * cos(angleRad).toFloat(),
                y = center.y + (radius - 1.dp.toPx()) * sin(angleRad).toFloat()
            )
            val innerPoint = Offset(
                x = center.x + (radius - tickLen) * cos(angleRad).toFloat(),
                y = center.y + (radius - tickLen) * sin(angleRad).toFloat()
            )

            drawLine(
                color = tickCol,
                start = innerPoint,
                end = outerPoint,
                strokeWidth = strokeW
            )
        }

        // 5. Draw pointer needle segment (outer part only, floating)
        val needleAngleDeg = 135f + sweepAngle
        val needleAngleRad = Math.toRadians(needleAngleDeg.toDouble())
        
        val needleEndPoint = Offset(
            x = center.x + (radius + 2.dp.toPx()) * cos(needleAngleRad).toFloat(),
            y = center.y + (radius + 2.dp.toPx()) * sin(needleAngleRad).toFloat()
        )
        
        val needleInnerPoint = Offset(
            x = center.x + (radius - 20.dp.toPx()) * cos(needleAngleRad).toFloat(),
            y = center.y + (radius - 20.dp.toPx()) * sin(needleAngleRad).toFloat()
        )

        drawLine(
            color = redColor,
            start = needleInnerPoint,
            end = needleEndPoint,
            strokeWidth = 6.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}



@Composable
fun AiDrivingScreen(
    viewModel: ObdDashboardViewModel,
    cardBg: Color,
    electricCyan: Color,
    sportAccentRed: Color
) {
    val isGeminiActive by viewModel.isGeminiActive.collectAsState()
    val geminiPersonality by viewModel.geminiPersonality.collectAsState()
    val geminiLog by viewModel.geminiLog.collectAsState()

    val aiDtcReport by viewModel.aiDtcReport.collectAsState()
    val isAiDtcLoading by viewModel.isAiDtcLoading.collectAsState()

    val aiCoachReport by viewModel.aiCoachReport.collectAsState()
    val isAiCoachLoading by viewModel.isAiCoachLoading.collectAsState()

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "YAPAY ZEKA (AI) KOKPİT ASİSTANI",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                letterSpacing = 1.sp
            )
        )

        // 1. --- Gemini AI Sürüş Otopilot Paneli ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                width = 1.dp,
                color = if (isGeminiActive) sportAccentRed.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.05f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "🤖 GEMINI OTOPİLOT SÜRÜCÜ",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isGeminiActive) sportAccentRed else Color.LightGray
                            )
                        )
                        Text(
                            text = "Yapay zeka gaz pedalını otomatik yönetir",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                        )
                    }

                    Switch(
                        checked = isGeminiActive,
                        onCheckedChange = { viewModel.setGeminiActive(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = sportAccentRed,
                            checkedTrackColor = sportAccentRed.copy(alpha = 0.5f),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.LightGray.copy(alpha = 0.2f)
                        )
                    )
                }

                val geminiApiKey by viewModel.geminiApiKey.collectAsState()
                val geminiApiBase by viewModel.geminiApiBase.collectAsState()
                val geminiModelName by viewModel.geminiModelName.collectAsState()

                OutlinedTextField(
                    value = geminiApiKey,
                    onValueChange = { viewModel.setGeminiApiKey(it) },
                    label = { Text("API Anahtarı (Opsiyonel / .env)", fontSize = 11.sp, color = Color.Gray) },
                    placeholder = { Text("API anahtarınızı buraya girin...", fontSize = 12.sp, color = Color.DarkGray) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = sportAccentRed,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                        focusedContainerColor = Color.Black.copy(alpha = 0.15f),
                        unfocusedContainerColor = Color.Black.copy(alpha = 0.15f)
                    )
                )

                GeminiProviderAndModelSelector(
                    viewModel = viewModel,
                    sportAccentRed = sportAccentRed,
                    electricCyan = electricCyan
                )

                // Slider to adjust the interval dynamically and avoid 429
                val geminiInterval by viewModel.geminiInterval.collectAsState()
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🔄 AI Sürüş Komut Sıklığı:",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray, fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "$geminiInterval saniye",
                            style = MaterialTheme.typography.bodySmall.copy(color = electricCyan, fontWeight = FontWeight.ExtraBold)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Slider(
                        value = geminiInterval.toFloat(),
                        onValueChange = { viewModel.setGeminiInterval(it.toInt()) },
                        valueRange = 10f..120f,
                        colors = SliderDefaults.colors(
                            thumbColor = electricCyan,
                            activeTrackColor = electricCyan,
                            inactiveTrackColor = Color.White.copy(alpha = 0.12f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                    )
                    Text(
                        text = "İpucu: Ücretsiz anahtar kullanıyorsanız 429 API sınırı hatasını engellemek için bu süreyi 45-60 saniye veya üzerine getirin.",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray, fontSize = 10.sp)
                    )
                }

                Text(
                    text = "Sürüş Tarzı (Karakter):",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray, fontWeight = FontWeight.Bold)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("sport" to "⚡ SPOR", "commuter" to "🚇 ŞEHİR", "peaceful" to "🍃 CHILL").forEach { (id, label) ->
                        val isSelected = geminiPersonality == id
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) sportAccentRed.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f))
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) sportAccentRed else Color.White.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { viewModel.setGeminiPersonality(id) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else Color.Gray
                                )
                            )
                        }
                    }
                }

                // Console feedback box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    color = if (isGeminiActive) electricCyan else Color.Gray,
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = geminiLog,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (isGeminiActive) electricCyan else Color.LightGray,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }

        // 2. --- AI OBD Teşhis Raporu Bölümü ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🔍 AI OTO TEŞHİS ASİSTANI",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Text(
                            text = "OBD2 telemetrilerini ve arıza kodlarını analiz edin",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Button(
                        onClick = { viewModel.generateAiDtcReport() },
                        enabled = !isAiDtcLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = electricCyan),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        if (isAiDtcLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                        } else {
                            Text("Teşhis Et", color = Color.Black, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }

                if (aiDtcReport.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.3f))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = aiDtcReport,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.LightGray,
                                lineHeight = 18.sp
                            )
                        )
                    }
                } else {
                    Text(
                        text = "Henüz bir teşhis raporu oluşturulmadı. Başlatmak için 'Teşhis Et' butonuna tıklayın.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }

        // 3. --- AI Eco / Performans Sürüş Koç Paneli ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🍃 AI SURUŞ & ECO KOÇU",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Text(
                            text = "Sürüş stilinizi değerlendirin ve tasarruf önerileri alın",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Button(
                        onClick = { viewModel.generateAiCoachReport() },
                        enabled = !isAiCoachLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE2A000)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        if (isAiCoachLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                        } else {
                            Text("Puanla", color = Color.Black, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }

                if (aiCoachReport.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.3f))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = aiCoachReport,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.LightGray,
                                lineHeight = 18.sp
                            )
                        )
                    }
                } else {
                    Text(
                        text = "Sürüş davranışınızı değerlendirmek ve yakıt tasarrufu seviyenizi öğrenmek için 'Puanla' butonuna tıklayın.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceItemCard(
    device: BondedObdDevice,
    isActiveConnection: Boolean,
    isAnyOtherConnected: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    cardBg: Color,
    electricCyan: Color,
    sportAccentRed: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = if (device.isMock) Color.Yellow.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.05f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (device.isMock) Icons.Default.PlayArrow else Icons.Default.Check,
                        contentDescription = "Bağlantı Türü",
                        tint = if (device.isMock) Color.Yellow else electricCyan
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = device.address,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.Gray,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
            }

            // Connection button (show only if this is the active connection OR no other device is connected)
            if (isActiveConnection || !isAnyOtherConnected) {
                Button(
                    onClick = {
                        if (isActiveConnection) {
                            onDisconnect()
                        } else {
                            onConnect()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isActiveConnection) sportAccentRed else if (device.isMock) Color(0xFFFF9500) else electricCyan
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isActiveConnection) "Kes" else "Bağlan",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    viewModel: ObdDashboardViewModel,
    cardBg: Color,
    electricCyan: Color,
    sportAccentRed: Color
) {
    val scrollState = rememberScrollState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Connection states collected from ViewModel for inline Connection Area
    val pairedDevices by viewModel.pairedDevices.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val connectedDeviceAddress by viewModel.connectedDeviceAddress.collectAsState()
    val consoleLogs by viewModel.terminalConsoleLogs.collectAsState()
    val terminalBg = Color(0xFF13151D)

    // Camera and HUD Overlay states
    val cameraSourceMode by viewModel.cameraSourceMode.collectAsState()
    val hudPosition by viewModel.hudPosition.collectAsState()
    val hudContrastMode by viewModel.hudContrastMode.collectAsState()

    // Gauge style state
    val speedometerStyle by viewModel.speedometerStyle.collectAsState()

    // Xiaomi Smart Dash Cam states
    val xiaomiModelName by viewModel.xiaomiModelName.collectAsState()
    val xiaomiSerialNumber by viewModel.xiaomiSerialNumber.collectAsState()
    val xiaomiFirmwareVersion by viewModel.xiaomiFirmwareVersion.collectAsState()
    val xiaomiSsid by viewModel.xiaomiSsid.collectAsState()
    val xiaomiPassword by viewModel.xiaomiPassword.collectAsState()
    val xiaomiIpAddress by viewModel.xiaomiIpAddress.collectAsState()
    val xiaomiPort by viewModel.xiaomiPort.collectAsState()
    val xiaomiStreamProtocol by viewModel.xiaomiStreamProtocol.collectAsState()
    val xiaomiStreamPath by viewModel.xiaomiStreamPath.collectAsState()
    val xiaomiCustomUrl by viewModel.xiaomiCustomUrl.collectAsState()
    val xiaomiUseSimulationFallback by viewModel.xiaomiUseSimulationFallback.collectAsState()
    val isXiaomiConnected by viewModel.isXiaomiConnected.collectAsState()
    val isXiaomiConnecting by viewModel.isXiaomiConnecting.collectAsState()
    val xiaomiSignalDbm by viewModel.xiaomiSignalDbm.collectAsState()
    val xiaomiBatteryVolt by viewModel.xiaomiBatteryVolt.collectAsState()
    val xiaomiSdCardFree by viewModel.xiaomiSdCardFree.collectAsState()
    val xiaomiEmergencyLocked by viewModel.xiaomiEmergencyLocked.collectAsState()
    val xiaomiOrange = Color(0xFFFF6900)

    // Category navigation tab (0: Kamera & Wi-Fi, 1: OBD2 & Bluetooth, 2: Yapay Zeka, 3: Gösterge & Araç, 4: Tümü)
    var selectedCategoryTab by remember { mutableIntStateOf(0) }

    // Gemini AI states
    val isGeminiActive by viewModel.isGeminiActive.collectAsState()
    val geminiApiKey by viewModel.geminiApiKey.collectAsState()
    val geminiInterval by viewModel.geminiInterval.collectAsState()
    val geminiPersonality by viewModel.geminiPersonality.collectAsState()
    val geminiLog by viewModel.geminiLog.collectAsState()
    var isApiKeyVisible by remember { mutableStateOf(false) }

    // --- COMPOSE SECTION BUILDERS ---
    val cameraAndHudSection: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // CARD 1: WI-FI CAR DASH CAM (192.168.1.254 RTSP)
            Text(
                text = "WI-FI ARAÇ KAMERASI (192.168.1.254 RTSP)",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    letterSpacing = 1.sp
                )
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, if (isXiaomiConnected) Color(0xFF34D399).copy(alpha = 0.5f) else xiaomiOrange.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Minimalist Header with Live Status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Videocam, contentDescription = null, tint = if (isXiaomiConnected) Color(0xFF34D399) else xiaomiOrange)
                            Column {
                                Text(
                                    text = "YI Car Dash Cam",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                                )
                                Text(
                                    text = "rtsp://$xiaomiIpAddress:$xiaomiPort$xiaomiStreamPath",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isXiaomiConnected) Color(0xFF34D399).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, if (isXiaomiConnected) Color(0xFF34D399) else Color.Gray)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(
                                            if (isXiaomiConnected) Color(0xFF34D399) else if (isXiaomiConnecting) Color(0xFFFBBF24) else Color.Gray,
                                            CircleShape
                                        )
                                    )
                                Text(
                                    text = if (isXiaomiConnected) "BAĞLI" else if (isXiaomiConnecting) "BAĞLANIYOR..." else "ÇEVRİMDIŞI",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isXiaomiConnected) Color(0xFF34D399) else Color.LightGray
                                )
                            }
                        }
                    }

                    // Minimal Connection Parameters (SSID & Password)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = xiaomiSsid,
                            onValueChange = { viewModel.updateXiaomiConfig(ssid = it) },
                            label = { Text("Kamera Wi-Fi Adı", fontSize = 10.sp, color = Color.Gray) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontFamily = FontFamily.Monospace),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = xiaomiPassword,
                            onValueChange = { viewModel.updateXiaomiConfig(password = it) },
                            label = { Text("Wi-Fi Şifresi", fontSize = 10.sp, color = Color.Gray) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontFamily = FontFamily.Monospace),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Network IP & Port
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = xiaomiIpAddress,
                            onValueChange = { viewModel.updateXiaomiConfig(ipAddress = it) },
                            label = { Text("Kamera IP Adresi", fontSize = 10.sp, color = Color.Gray) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontFamily = FontFamily.Monospace),
                            modifier = Modifier.weight(1.5f)
                        )
                        OutlinedTextField(
                            value = xiaomiPort,
                            onValueChange = { viewModel.updateXiaomiConfig(port = it) },
                            label = { Text("Port", fontSize = 10.sp, color = Color.Gray) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontFamily = FontFamily.Monospace),
                            modifier = Modifier.weight(0.9f)
                        )
                    }

                    // Varsayılan Değerleri Sıfırla (192.168.1.254 RTSP)
                    OutlinedButton(
                        onClick = { viewModel.resetXiaomiDefaults() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF34D399)),
                        border = BorderStroke(1.dp, Color(0xFF34D399).copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Varsayılan Ayarlara Sıfırla (192.168.1.254 RTSP)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Simulation fallback switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Simülasyon Yedeklemesi",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Kamera sinyali yokken HUD test videosu oynat",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray, fontSize = 10.sp)
                            )
                        }
                        Switch(
                            checked = xiaomiUseSimulationFallback,
                            onCheckedChange = { viewModel.setXiaomiUseSimulationFallback(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = xiaomiOrange,
                                checkedTrackColor = xiaomiOrange.copy(alpha = 0.4f)
                            )
                        )
                    }

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.toggleXiaomiConnection() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isXiaomiConnected) sportAccentRed else xiaomiOrange
                            ),
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isXiaomiConnecting) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Bağlanıyor...", fontSize = 12.sp)
                            } else {
                                Icon(
                                    imageVector = if (isXiaomiConnected) Icons.Default.Close else Icons.Default.Wifi,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isXiaomiConnected) "Bağlantıyı Kes" else "YI Dash Cam'e Bağlan",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Button(
                            onClick = { viewModel.toggleXiaomiEmergencyLock() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (xiaomiEmergencyLocked) sportAccentRed else Color.White.copy(alpha = 0.12f)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = if (xiaomiEmergencyLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = if (xiaomiEmergencyLocked) Color.White else Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (xiaomiEmergencyLocked) "KİLİTLİ" else "Acil Kilit",
                                color = if (xiaomiEmergencyLocked) Color.White else Color.LightGray,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Quick Reset to YI Defaults
                    TextButton(
                        onClick = {
                            viewModel.updateXiaomiConfig(
                                ipAddress = "192.168.42.1",
                                port = "554",
                                streamPath = "/live",
                                protocol = WifiStreamProtocol.RTSP,
                                customUrl = "",
                                ssid = "YI-Symbol",
                                password = "1234567890"
                            )
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("YI Dash Cam Varsayılanlarını Geri Yükle", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }

            // CARD 2: CAMERA SOURCE & HUD OVERLAY
            Text(
                text = "KAMERA KAYNAĞI VE TELEMETRİ (HUD)",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    letterSpacing = 1.sp
                )
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, electricCyan.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = null, tint = electricCyan)
                        Column {
                            Text(
                                text = "Kamera Kaynağı",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                            )
                            Text(
                                text = "Ekranda gösterilecek video kaynağını seçin",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                            )
                        }
                    }

                    // Camera Source Mode Selection Chips
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        CameraSourceMode.values().forEach { mode ->
                            val isSelected = cameraSourceMode == mode
                            Surface(
                                onClick = { viewModel.setCameraSourceMode(mode) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) electricCyan.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.25f),
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) electricCyan else Color.White.copy(alpha = 0.08f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { viewModel.setCameraSourceMode(mode) },
                                        colors = RadioButtonDefaults.colors(selectedColor = electricCyan)
                                    )
                                    Column {
                                        Text(
                                            text = mode.label,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) Color.White else Color.LightGray
                                            )
                                        )
                                        Text(
                                            text = if (mode.isPhysicalCamera) "Cihaz içi donanım kamerası" else "Wi-Fi stream veya video simülasyonu",
                                            style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray, fontSize = 10.sp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                    // HUD Position Setting
                    Text(
                        text = "HUD Telemetri Konumu",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HudLayoutPosition.values().take(3).forEach { pos ->
                            val isSelected = hudPosition == pos
                            OutlinedButton(
                                onClick = { viewModel.setHudPosition(pos) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) electricCyan.copy(alpha = 0.2f) else Color.Transparent
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) electricCyan else Color.White.copy(alpha = 0.15f)
                                )
                            ) {
                                Text(
                                    text = pos.label.split(" ").first(),
                                    fontSize = 11.sp,
                                    color = if (isSelected) Color.White else Color.Gray,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // HUD Contrast Mode Setting
                    Text(
                        text = "HUD Kontrast ve Tema Modu",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HudContrastMode.values().forEach { mode ->
                            val isSelected = hudContrastMode == mode
                            OutlinedButton(
                                onClick = { viewModel.setHudContrastMode(mode) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) sportAccentRed.copy(alpha = 0.2f) else Color.Transparent
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) sportAccentRed else Color.White.copy(alpha = 0.15f)
                                )
                            ) {
                                Text(
                                    text = mode.label.split(" ").first(),
                                    fontSize = 11.sp,
                                    color = if (isSelected) Color.White else Color.Gray,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val speedometerSection: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "HIZ GÖSTERGESİ VE KOKPİT TEMASI",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    letterSpacing = 1.sp
                )
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = null, tint = electricCyan)
                        Column {
                            Text(
                                text = "Hız Göstergesi Stili",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                            )
                            Text(
                                text = "Ana kokpit ekranındaki gösterge tasarımını seçin",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SpeedometerStyle.values().forEach { style ->
                            val isSelected = speedometerStyle == style

                            Surface(
                                onClick = { viewModel.setSpeedometerStyle(style) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) electricCyan.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.25f),
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) electricCyan else Color.White.copy(alpha = 0.08f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { viewModel.setSpeedometerStyle(style) },
                                            colors = RadioButtonDefaults.colors(selectedColor = electricCyan)
                                        )
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(
                                                    text = style.label,
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isSelected) Color.White else Color.LightGray
                                                    )
                                                )
                                                if (style == SpeedometerStyle.MINIMALIST) {
                                                    Surface(
                                                        color = electricCyan.copy(alpha = 0.2f),
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(
                                                            text = "VARSAYILAN (MİNİMALİST)",
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = electricCyan,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                text = style.description,
                                                style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray, fontSize = 11.sp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2010 Symbol D4F 1.2 16V Motor Bilgi Kartı
            Text(
                text = "ARAÇ VE MOTOR PROFİLİ",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    letterSpacing = 1.sp
                )
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = Color(0xFF60A5FA))
                        Column {
                            Text(
                                text = "2010 Renault Symbol 1.2 16V",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                            )
                            Text(
                                text = "Motor Kodu: D4F 732 (Atmosferik Benzin / LPG)",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF93C5FD), fontSize = 11.sp)
                            )
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "Silindir Hacmi" to "1149 cc",
                            "Maks Güç" to "75 HP",
                            "Maks Tork" to "107 Nm",
                            "Maks Devir" to "6000 RPM"
                        ).forEach { (lbl, valStr) ->
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(lbl, fontSize = 9.sp, color = Color.Gray, maxLines = 1)
                                Text(valStr, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }

                    Text(
                        text = "Sensör Donanımı: Elektronik Gaz Kelebeği (TPS), Gaz Pedalı Pozisyon Sensörü (APP), MAP Manifold Mutlak Basınç, IAT Emme Havası Sıcaklığı, Soğutma Suyu Sıcaklığı (ECT), Ön O2 Lambda Sensörü.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray, fontSize = 10.sp, lineHeight = 14.sp)
                    )
                }
            }
        }
    }


    val geminiSection: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "YAPAY ZEKA (GEMINI AI) VE MODEL AYARLARI",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    letterSpacing = 1.sp
                )
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isGeminiActive) sportAccentRed.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.05f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "🤖 GEMINI AI OTOPİLOT VE ANALİZ",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isGeminiActive) sportAccentRed else Color.White
                                )
                            )
                            Text(
                                text = "Canlı araç verilerini ve telematiği yapay zeka ile işle",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                            )
                        }

                        Switch(
                            checked = isGeminiActive,
                            onCheckedChange = { viewModel.setGeminiActive(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = sportAccentRed,
                                checkedTrackColor = sportAccentRed.copy(alpha = 0.5f),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.LightGray.copy(alpha = 0.2f)
                            )
                        )
                    }

                    // API Key Field with Password Visibility Toggle
                    OutlinedTextField(
                        value = geminiApiKey,
                        onValueChange = { viewModel.setGeminiApiKey(it) },
                        label = { Text("Gemini / LLM API Anahtarı", fontSize = 11.sp, color = Color.Gray) },
                        placeholder = { Text("AI Studio veya OpenAI/Groq API anahtarı...", fontSize = 12.sp, color = Color.DarkGray) },
                        singleLine = true,
                        visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                Icon(
                                    imageVector = if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isApiKeyVisible) "API Anahtarını Gizle" else "API Anahtarını Göster",
                                    tint = electricCyan
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = sportAccentRed,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            focusedContainerColor = Color.Black.copy(alpha = 0.15f),
                            unfocusedContainerColor = Color.Black.copy(alpha = 0.15f)
                        )
                    )

                    // Provider and Model Selector
                    GeminiProviderAndModelSelector(
                        viewModel = viewModel,
                        sportAccentRed = sportAccentRed,
                        electricCyan = electricCyan
                    )

                    // Slider to adjust interval dynamically
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🔄 AI Sorgu Sıklığı:",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "$geminiInterval saniye",
                                style = MaterialTheme.typography.bodySmall.copy(color = electricCyan, fontWeight = FontWeight.ExtraBold)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = geminiInterval.toFloat(),
                            onValueChange = { viewModel.setGeminiInterval(it.toInt()) },
                            valueRange = 10f..120f,
                            colors = SliderDefaults.colors(
                                thumbColor = electricCyan,
                                activeTrackColor = electricCyan,
                                inactiveTrackColor = Color.White.copy(alpha = 0.12f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp)
                        )
                    }

                    // Personality / Style picker
                    Text(
                        text = "Yapay Zeka Sürüş Karakteri:",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray, fontWeight = FontWeight.Bold)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("sport" to "⚡ SPOR", "commuter" to "🚇 ŞEHİR", "peaceful" to "🍃 CHILL").forEach { (id, label) ->
                            val isSelected = geminiPersonality == id
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) sportAccentRed.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f))
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) sportAccentRed else Color.White.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { viewModel.setGeminiPersonality(id) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else Color.Gray
                                    )
                                )
                            }
                        }
                    }

                    // AI Terminal feedback box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.35f))
                            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        color = if (isGeminiActive) electricCyan else Color.Gray,
                                        shape = CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = geminiLog,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (isGeminiActive) electricCyan else Color.LightGray,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    val obdConnectionSection: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Live Diagnostic Terminal logs
            Text(
                text = "CANLI OBD2 SERİ TERMİNAL GÜNLÜĞÜ",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    letterSpacing = 1.sp
                )
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = terminalBg),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    if (consoleLogs.isEmpty()) {
                        Text(
                            text = "Terminal beklemede...\nOBD bağlantısını başlattığınızda ham paket alışverişleri buraya dökülür.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.Gray,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 16.sp
                            ),
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        val terminalScrollState = rememberScrollState()
                        LaunchedEffect(consoleLogs.size) {
                            terminalScrollState.animateScrollTo(terminalScrollState.maxValue)
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(terminalScrollState)
                        ) {
                            consoleLogs.takeLast(30).forEach { log ->
                                val color = when {
                                    log.startsWith("❌") || log.contains("Hata") -> sportAccentRed
                                    log.contains("TX:") -> Color.Yellow
                                    log.contains("RX:") -> electricCyan
                                    log.contains("✅") -> Color.Green
                                    else -> Color.LightGray
                                }
                                Text(
                                    text = log,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = color,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 16.sp
                                    ),
                                    modifier = Modifier.padding(bottom = 3.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // OBD2 Connection settings & Pairing
            Text(
                text = "CANLI OBD2 BAĞLANTI & PORT AYARLARI",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    letterSpacing = 1.sp
                )
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Bilgi",
                        tint = electricCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Lütfen aracınızın OBD2 portuna Bluetooth cihazını (ELM327) bağlayın, telefon ayarlardan eşleştirin ardından listeden seçip bağlanın.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "EŞLEŞMİŞ OBD BLUETOOTH CİHAZLARI",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        letterSpacing = 1.sp
                    )
                )

                IconButton(
                    onClick = { viewModel.refreshPairedDevices() }
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Yenile", tint = electricCyan)
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (pairedDevices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(cardBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Bluetooth cihazı bulunamadı. Eşleşen cihazları yenileyin.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray)
                        )
                    }
                } else {
                    val sortedDevices = remember(pairedDevices, connectedDeviceAddress) {
                        if (connectedDeviceAddress != null) {
                            pairedDevices.sortedWith(compareBy { it.address != connectedDeviceAddress })
                        } else {
                            pairedDevices
                        }
                    }
                    val isAnyConnectionActive = connectionState is ConnectionState.Connected || connectionState is ConnectionState.Connecting || connectionState is ConnectionState.Initializing
                    sortedDevices.forEach { device ->
                        val isThisActive = isAnyConnectionActive && device.address == connectedDeviceAddress
                        val isAnyOtherConnected = isAnyConnectionActive && !isThisActive
                        DeviceItemCard(
                            device = device,
                            isActiveConnection = isThisActive,
                            isAnyOtherConnected = isAnyOtherConnected,
                            onConnect = { viewModel.connectToObd(device.address) },
                            onDisconnect = { viewModel.disconnectObd() },
                            cardBg = cardBg,
                            electricCyan = electricCyan,
                            sportAccentRed = sportAccentRed
                        )
                    }
                }
            }
        }
    }

    // --- CATEGORY TABS & MAIN LAYOUT ---
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Modern Category Tab Bar
        ScrollableTabRow(
            selectedTabIndex = selectedCategoryTab,
            containerColor = Color(0xFF131722),
            contentColor = electricCyan,
            edgePadding = 12.dp,
            indicator = { tabPositions ->
                if (selectedCategoryTab < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedCategoryTab]),
                        color = electricCyan,
                        height = 3.dp
                    )
                }
            },
            divider = {
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            }
        ) {
            val tabs = listOf(
                Triple(0, "Kamera & Wi-Fi", Icons.Default.Wifi),
                Triple(1, "OBD2 & Cihazlar", Icons.Default.Bluetooth),
                Triple(2, "Yapay Zeka (AI)", Icons.Default.SmartToy),
                Triple(3, "Gösterge & Araç", Icons.Default.Speed),
                Triple(4, "Tümü", Icons.Default.Tune)
            )
            tabs.forEach { (index, title, icon) ->
                val isSelected = selectedCategoryTab == index
                Tab(
                    selected = isSelected,
                    onClick = { selectedCategoryTab = index },
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp,
                            color = if (isSelected) electricCyan else Color.Gray
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected) electricCyan else Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
        }

        // Active Category Content (Scrollable)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (selectedCategoryTab) {
                0 -> {
                    cameraAndHudSection()
                }
                1 -> {
                    obdConnectionSection()
                }
                2 -> {
                    geminiSection()
                }
                3 -> {
                    speedometerSection()
                }
                4 -> {
                    // Show All View
                    if (isLandscape) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                cameraAndHudSection()
                                speedometerSection()
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                geminiSection()
                                obdConnectionSection()
                            }
                        }
                    } else {
                        cameraAndHudSection()
                        speedometerSection()
                        geminiSection()
                        obdConnectionSection()
                    }
                }
            }
        }
    }
}


@Composable
fun OldSoundCustomizerScreen() {
    // Deprecated
}


@Composable
fun TelemetryHUDItem(
    title: String,
    value: String,
    progress: Float,
    progressColor: Color,
    cardBg: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFF919196),
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.sp
                    ),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                )
            }
            
            // Progress Bar representing intensity level
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp)),
                color = progressColor,
                trackColor = Color.White.copy(alpha = 0.05f)
            )
        }
    }
}

@Composable
fun DiagnosticsScreen(
    isDtcScanning: Boolean,
    dtcScanProgress: Float,
    dtcCodes: List<String>,
    ecuProtocol: String,
    ecuVin: String,
    ecuMilStatus: Boolean,
    viewModel: ObdDashboardViewModel,
    cardBg: Color,
    electricCyan: Color,
    sportAccentRed: Color,
    amberWarning: Color
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "CANLI OBD2 ARIZA TEŞHİS MERKEZİ",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                letterSpacing = 1.sp
            )
        )

        // Yellow Dashboard Glow Alert Check-Engine representation
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(
                width = 1.dp,
                color = if (ecuMilStatus) sportAccentRed.copy(alpha = 0.5f) else electricCyan.copy(alpha = 0.2f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val blinkTransition = rememberInfiniteTransition(label = "CheckEngineBlink")
                val alphaMultiplier by if (ecuMilStatus) {
                    blinkTransition.animateFloat(
                        initialValue = 1.0f,
                        targetValue = 0.4f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "MILBlinkAlpha"
                    )
                } else {
                    remember { mutableStateOf(1.0f) }
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = if (ecuMilStatus) sportAccentRed.copy(alpha = 0.12f) else electricCyan.copy(alpha = 0.1f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Check Engine MIL Light",
                        tint = if (ecuMilStatus) amberWarning.copy(alpha = alphaMultiplier) else electricCyan,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = if (ecuMilStatus) "MOTOR İKAZ LAMBASI (MIL) AKTİF" else "KONTROL SİSTEMLERİ SAĞLIKLI",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (ecuMilStatus) amberWarning else Color.White
                        )
                    )
                    Text(
                        text = if (ecuMilStatus) "Araç şanzıman veya motor sensörlerinde arıza saptandı." else "ECU hafızasında bekleyen aktif bir hata saptanmadı.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                    )
                }
            }
        }

        // ECU System Metadata Identity row
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "ECU KİMLİK VERİLERİ",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                )
                
                Divider(color = Color.White.copy(alpha = 0.05f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "İletişim Standardı:", style = MaterialTheme.typography.bodyMedium.copy(color = Color.LightGray))
                    Text(text = ecuProtocol, style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Şasi Numarası (VIN):", style = MaterialTheme.typography.bodyMedium.copy(color = Color.LightGray))
                    Text(text = ecuVin, style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Aktif Emisyon Sınıfı:", style = MaterialTheme.typography.bodyMedium.copy(color = Color.LightGray))
                    Text(text = "EURO 6D-Temp / OBD-II", style = MaterialTheme.typography.bodyMedium.copy(color = electricCyan, fontWeight = FontWeight.Bold))
                }
            }
        }

        // Active Diagnostics Control console
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "DİAGNOSTİK SORGUSU VE TARAMA",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
                Text(
                    text = "ECU hata hafızasını okuyarak ateşleme, silindir tekleme, lambda ve turbo basınç emniyet limit aşımı kontrolü yapar.",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                )

                if (isDtcScanning) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            color = sportAccentRed,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = "ECU Belleği Taranıyor: %" + (dtcScanProgress * 100).toInt(),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                        LinearProgressIndicator(
                            progress = { dtcScanProgress },
                            color = sportAccentRed,
                            trackColor = Color.White.copy(alpha = 0.05f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { viewModel.scanFaultCodes() },
                            colors = ButtonDefaults.buttonColors(containerColor = electricCyan),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Search, contentDescription = "Taramayı Başlat", tint = Color.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SİSTEMİ TARA", fontWeight = FontWeight.Bold, color = Color.Black)
                        }

                        if (dtcCodes.isNotEmpty()) {
                            Button(
                                onClick = { viewModel.clearFaultCodes() },
                                colors = ButtonDefaults.buttonColors(containerColor = sportAccentRed),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Kodları Sıfırla", tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("BELLEĞİ SİL", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // Active codes list segment
        Text(
            text = "KAYDEDİLEN AKTİF OBD HATA KODLARI",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                letterSpacing = 1.sp
            )
        )

        if (dtcCodes.isEmpty() && !isDtcScanning) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Hata Yok",
                        tint = electricCyan,
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Arıza Kaydı Bulunamadı",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    Text(
                        text = "Tüm ECU sensör gerilimleri ve emisyon normları sağlıklı limitlerde.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else if (dtcCodes.isNotEmpty() && !isDtcScanning) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                dtcCodes.forEach { code ->
                    DtcItemCard(
                        code = code,
                        cardBg = cardBg,
                        accentRed = sportAccentRed,
                        orangeWarning = amberWarning
                    )
                }
            }
        }

        // AI OTO TEŞHİS ASİSTANI CARD
        val aiDtcReport by viewModel.aiDtcReport.collectAsState()
        val isAiDtcLoading by viewModel.isAiDtcLoading.collectAsState()

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🤖 AI OTO TEŞHİS ASİSTANI",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Text(
                            text = "OBD2 telemetrilerini ve arıza kodlarını yapay zeka ile derinlemesine analiz edin",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Button(
                        onClick = { viewModel.generateAiDtcReport() },
                        enabled = !isAiDtcLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = electricCyan),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        if (isAiDtcLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                        } else {
                            Text("Teşhis Et", color = Color.Black, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }

                if (aiDtcReport.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.3f))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = aiDtcReport,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.LightGray,
                                lineHeight = 18.sp
                            )
                        )
                    }
                } else {
                    Text(
                        text = "Henüz bir teşhis raporu oluşturulmadı. Başlatmak için 'Teşhis Et' butonuna tıklayın.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DtcItemCard(
    code: String,
    cardBg: Color,
    accentRed: Color,
    orangeWarning: Color
) {
    val (label, explanation, risk) = when (code) {
        "P0300" -> Triple(
            "P0300 - Çoklu/Rastgele Silindir Tekleme Saptandı",
            "Random/Multiple Cylinder Misfire Detected. ECU, silindirlere giden yakıtın düzensiz patladığını veya ateşleme bobinlerinde anlık voltaj çökmesi oluştuğunu rapor etmiştir.",
            "Buji aşınması, bobin çatlaması veya kalitesiz yakıt karışımı."
        )
        "P0171" -> Triple(
            "P0171 - Hava/Yakıt Karışımı Fakir (Sıra 1)",
            "System Too Lean (Bank 1). Motor emme manifolduna giren hava miktarına oranla silindirlere püskürtülen yakıt debisi yetersiz kalmaktadır (fakir karışım).",
            "Emme hattı vakum sızıntıları, MAF/MAP sensör kirliliği veya tıkalı enjektörler."
        )
        else -> Triple(
            "$code - OBD-II Standart Hata Bildirimi",
            "Genel aktarma organı kontrol ünitesi arızası kaydedilmiştir.",
            "Canlı terminal log listesindeki detaylı HEX verilerini inceleyin."
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, accentRed.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Black,
                        color = accentRed
                    )
                )
                Box(
                    modifier = Modifier
                        .background(accentRed.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "AKTİF",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = accentRed
                        )
                    )
                }
            }

            Text(
                text = explanation,
                style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray)
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Olası Sorun",
                    tint = orangeWarning,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Teşhis: Olası neden -> $risk",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }
}

@Composable
fun DrivingKitScreen(
    viewModel: ObdDashboardViewModel,
    rpmHistory: List<Float>,
    loadHistory: List<Float>,
    boostHistory: List<Float>,
    currentRpm: Float,
    currentLoad: Float,
    currentBoost: Int,
    maxProductRpm: Float,
    cardBg: Color,
    electricCyan: Color,
    sportAccentRed: Color
) {
    val scrollState = rememberScrollState()

    // 1. GPS Map State
    val routePoints by viewModel.gpsRoutePoints.collectAsState()
    val bearing by viewModel.gpsBearing.collectAsState()
    val speed by viewModel.speed.collectAsState() // Using calculated OBD speed
    val isRouteSim by viewModel.isRouteSimulationActive.collectAsState()

    // 2. Performance Timer State
    val timerMillis by viewModel.timerMillis.collectAsState()
    val sprint60 by viewModel.sprint60Time.collectAsState()
    val sprint100 by viewModel.sprint100Time.collectAsState()
    val statusText by viewModel.timerStatusText.collectAsState()
    val pastRuns by viewModel.pastRuns.collectAsState()

    // 4. G-Force & Inclinometer
    val gForceX by viewModel.gForceX.collectAsState()
    val gForceY by viewModel.gForceY.collectAsState()
    val pitch by viewModel.pitch.collectAsState()
    val roll by viewModel.roll.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        
        // ============================
        // 1. 0-100 SPRINT TIMER (MOVED UP)
        // ============================
        Text(
            text = "PERFORMANS SÜRESİ (0-100) & KRONOMETRE",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                letterSpacing = 1.sp
            )
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium.copy(color = if (sprint100 > 0f) Color.Green else sportAccentRed, fontWeight = FontWeight.Bold)
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                val seconds = timerMillis / 1000
                val fractions = (timerMillis % 1000) / 10

                Text(
                    text = String.format("%02d.%02d s", seconds, fractions),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("0-60 km/h", style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray))
                        Text(
                            text = if (sprint60 > 0f) String.format("%.2f s", sprint60) else "--",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = electricCyan)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("0-100 km/h", style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray))
                        Text(
                            text = if (sprint100 > 0f) String.format("%.2f s", sprint100) else "--",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = sportAccentRed)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = { viewModel.resetPerformanceTimer() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Sıfırla / Tekrar Dene", color = Color.White)
                }

                if (pastRuns.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Önceki Dereceler", style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray))
                    pastRuns.reversed().take(3).forEach { runMsg ->
                        Text(
                            text = runMsg,
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray, fontFamily = FontFamily.Monospace),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        // ============================
        // 2. G-FORCE & INCLINOMETER (MOVED UP)
        // ============================
        Text(
            text = "G-KUVVETİ & EĞİM ÖLÇER",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                letterSpacing = 1.sp
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // G-Force Vector Ball
            Card(
                modifier = Modifier.weight(1f).aspectRatio(1f),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(12.dp).fillMaxSize()
                ) {
                    Text("G-Kuvveti", style = MaterialTheme.typography.labelMedium.copy(color = Color.LightGray))
                    Spacer(modifier = Modifier.weight(1f))
                    
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        // Crosshair
                        HorizontalDivider(color = Color.White.copy(alpha = 0.2f), modifier = Modifier.align(Alignment.Center))
                        VerticalDivider(color = Color.White.copy(alpha = 0.2f), modifier = Modifier.align(Alignment.Center))

                        // Max G limits
                        val maxG = 1.5f
                        val limitedX = gForceX.coerceIn(-maxG, maxG)
                        val limitedY = gForceY.coerceIn(-maxG, maxG)
                        val offX = (limitedX / maxG) * 50.dp.value
                        val offY = (-limitedY / maxG) * 50.dp.value // Y axis inverted for UI

                        Box(
                            modifier = Modifier
                                .offset(x = offX.dp, y = offY.dp)
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(electricCyan)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "X: %.2f Y: %.2f".format(gForceX, gForceY),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = Color.Gray)
                    )
                }
            }

            // Pitch & Roll
            Card(
                modifier = Modifier.weight(1f).aspectRatio(1f),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(12.dp).fillMaxSize()
                ) {
                    Text("Eğim (Inclinometer)", style = MaterialTheme.typography.labelMedium.copy(color = Color.LightGray))
                    Spacer(modifier = Modifier.weight(1f))

                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .graphicsLayer(rotationZ = -roll), // Rotate chassis based on roll
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Draw an abstract car chassis back view
                            val w = size.width
                            val h = size.height
                            drawRoundRect(
                                color = sportAccentRed.copy(alpha = 0.5f),
                                topLeft = Offset(w*0.2f, h*0.4f),
                                size = Size(w*0.6f, h*0.3f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
                            )
                            // Wheels
                            drawRoundRect(color = Color.DarkGray, topLeft = Offset(w*0.1f, h*0.45f), size = Size(w*0.15f, h*0.4f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                            drawRoundRect(color = Color.DarkGray, topLeft = Offset(w*0.75f, h*0.45f), size = Size(w*0.15f, h*0.4f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                            
                            // Pitch baseline
                            val pitchOffset = (pitch / 45f) * (h / 2)
                            drawLine(
                                color = electricCyan,
                                start = Offset(0f, (h/2) + pitchOffset),
                                end = Offset(w, (h/2) + pitchOffset),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "Eğim: %.0f° Yatış: %.0f°".format(pitch, roll),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = Color.Gray)
                    )
                }
            }
        }

    }
}

@Composable
fun SevenSegmentDigit(
    digit: Char,
    color: Color,
    modifier: Modifier = Modifier,
    thickness: androidx.compose.ui.unit.Dp = 4.dp,
    spacing: androidx.compose.ui.unit.Dp = 2.dp
) {
    val active = when (digit) {
        '0' -> booleanArrayOf(true, true, true, true, true, true, false)
        '1' -> booleanArrayOf(false, true, true, false, false, false, false)
        '2' -> booleanArrayOf(true, true, false, true, true, false, true)
        '3' -> booleanArrayOf(true, true, true, true, false, false, true)
        '4' -> booleanArrayOf(false, true, true, false, false, true, true)
        '5' -> booleanArrayOf(true, false, true, true, false, true, true)
        '6' -> booleanArrayOf(true, false, true, true, true, true, true)
        '7' -> booleanArrayOf(true, true, true, false, false, false, false)
        '8' -> booleanArrayOf(true, true, true, true, true, true, true)
        '9' -> booleanArrayOf(true, true, true, true, false, true, true)
        '-' -> booleanArrayOf(false, false, false, false, false, false, true)
        else -> booleanArrayOf(false, false, false, false, false, false, false)
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val t = thickness.toPx()
        val s = spacing.toPx()

        val onColor = color
        val offColor = color.copy(alpha = 0.05f)

        // A (Top Horizontal)
        drawRoundRect(
            color = if (active[0]) onColor else offColor,
            topLeft = Offset(t + s, 0f),
            size = Size(w - 2 * (t + s), t),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(t / 2, t / 2)
        )

        // B (Top Right)
        drawRoundRect(
            color = if (active[1]) onColor else offColor,
            topLeft = Offset(w - t, t + s),
            size = Size(t, h / 2 - t - 1.5f * s),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(t / 2, t / 2)
        )

        // C (Bottom Right)
        drawRoundRect(
            color = if (active[2]) onColor else offColor,
            topLeft = Offset(w - t, h / 2 + 0.5f * s),
            size = Size(t, h / 2 - t - 1.5f * s),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(t / 2, t / 2)
        )

        // D (Bottom Horizontal)
        drawRoundRect(
            color = if (active[3]) onColor else offColor,
            topLeft = Offset(t + s, h - t),
            size = Size(w - 2 * (t + s), t),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(t / 2, t / 2)
        )

        // E (Bottom Left)
        drawRoundRect(
            color = if (active[4]) onColor else offColor,
            topLeft = Offset(0f, h / 2 + 0.5f * s),
            size = Size(t, h / 2 - t - 1.5f * s),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(t / 2, t / 2)
        )

        // F (Top Left)
        drawRoundRect(
            color = if (active[5]) onColor else offColor,
            topLeft = Offset(0f, t + s),
            size = Size(t, h / 2 - t - 1.5f * s),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(t / 2, t / 2)
        )

        // G (Middle Horizontal)
        drawRoundRect(
            color = if (active[6]) onColor else offColor,
            topLeft = Offset(t + s, h / 2 - t / 2),
            size = Size(w - 2 * (t + s), t),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(t / 2, t / 2)
        )
    }
}

@Composable
fun DigitalSevenSegmentDisplay(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    digitWidth: androidx.compose.ui.unit.Dp = 26.dp,
    digitHeight: androidx.compose.ui.unit.Dp = 48.dp,
    thickness: androidx.compose.ui.unit.Dp = 3.5.dp,
    spacing: androidx.compose.ui.unit.Dp = 1.5.dp,
    gap: androidx.compose.ui.unit.Dp = 6.dp
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        text.forEach { char ->
            if (char == '.' || char == ',') {
                Box(
                    modifier = Modifier
                        .align(Alignment.Bottom)
                        .padding(bottom = 2.dp)
                        .size(thickness)
                        .background(color, shape = CircleShape)
                )
            } else {
                SevenSegmentDigit(
                    digit = char,
                    color = color,
                    modifier = Modifier.size(width = digitWidth, height = digitHeight),
                    thickness = thickness,
                    spacing = spacing
                )
            }
        }
    }
}

@Composable
fun GeminiProviderAndModelSelector(
    viewModel: ObdDashboardViewModel,
    sportAccentRed: Color = Color(0xFFD13438),
    electricCyan: Color = Color(0xFF00E5FF)
) {
    val geminiApiBase by viewModel.geminiApiBase.collectAsState()
    val geminiModelName by viewModel.geminiModelName.collectAsState()

    // Providers mapping (url to label)
    val providers = listOf(
        Pair("https://generativelanguage.googleapis.com", "Google Gemini API"),
        Pair("https://api.groq.com/openai/v1", "Groq API"),
        Pair("https://openrouter.ai/api/v1", "OpenRouter"),
        Pair("https://api.openai.com/v1", "OpenAI API"),
        Pair("https://api.deepseek.com/v1", "DeepSeek API"),
        Pair("custom", "Özel Sağlayıcı...")
    )

    // Current selected provider based on baseurl state
    val currentProvider = remember(geminiApiBase) {
        providers.find { it.first == geminiApiBase } ?: providers.last()
    }

    var baseDropdownExpanded by remember { mutableStateOf(false) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }

    val geminiApiKey by viewModel.geminiApiKey.collectAsState()
    var fetchedModels by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var isFetchingModels by remember { mutableStateOf(false) }

    LaunchedEffect(currentProvider.first, geminiApiKey) {
        if (currentProvider.first == "custom" || geminiApiKey.isBlank()) {
            fetchedModels = listOf(Pair("custom", "API Anahtarı Gerekli / Özel Girin"))
            return@LaunchedEffect
        }
        
        isFetchingModels = true
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val providerUrl = currentProvider.first
                val isGemini = providerUrl.contains("generativelanguage")
                val urlString = if (isGemini) {
                    "$providerUrl/v1beta/models?key=$geminiApiKey"
                } else {
                    val suffix = if (providerUrl.endsWith("/")) "models" else "/models"
                    "$providerUrl$suffix"
                }
                
                val url = java.net.URL(urlString)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                if (!isGemini) {
                    connection.setRequestProperty("Authorization", "Bearer $geminiApiKey")
                }
                
                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonObject = org.json.JSONObject(response)
                    val modelsList = mutableListOf<Pair<String, String>>()
                    
                    if (isGemini) {
                        val modelsArray = jsonObject.optJSONArray("models") ?: org.json.JSONArray()
                        for (i in 0 until modelsArray.length()) {
                            val model = modelsArray.getJSONObject(i)
                            val name = model.optString("name").removePrefix("models/")
                            val displayName = model.optString("displayName", name)
                            if (name.contains("gemini") || name.contains("aqa")) {
                                modelsList.add(name to displayName)
                            }
                        }
                    } else {
                        val dataArray = jsonObject.optJSONArray("data") ?: org.json.JSONArray()
                        for (i in 0 until dataArray.length()) {
                            val model = dataArray.getJSONObject(i)
                            val id = model.optString("id")
                            modelsList.add(id to id)
                        }
                    }
                    
                    val finalList = if (modelsList.isNotEmpty()) {
                        modelsList.sortedBy { it.first } + Pair("custom", "Özel Model Belirt...")
                    } else {
                        listOf(Pair("custom", "Liste Boş (Özel Model Girin)"))
                    }
                    fetchedModels = finalList
                } else {
                    val errCode = connection.responseCode
                    fetchedModels = listOf(Pair("custom", "Hata: $errCode (Manuel Girin)"))
                }
            } catch (e: Exception) {
                fetchedModels = listOf(Pair("custom", "Bağlantı Hatası (Manuel Girin)"))
            } finally {
                isFetchingModels = false
            }
        }
    }

    // Models listed per provider
    val suggestedModels = if (isFetchingModels) {
        listOf(Pair(geminiModelName, "Modeller yükleniyor..."))
    } else {
        if (fetchedModels.isEmpty()) listOf(Pair("custom", "Özel Model Belirt...")) else fetchedModels
    }

    val currentSuggestedModel = remember(geminiModelName, suggestedModels) {
        suggestedModels.find { it.first == geminiModelName } ?: suggestedModels.last()
    }

    var isCustomBaseSelected by remember { mutableStateOf(currentProvider.first == "custom") }
    var isCustomModelSelected by remember { mutableStateOf(currentSuggestedModel.first == "custom") }

    LaunchedEffect(currentProvider) {
        isCustomBaseSelected = currentProvider.first == "custom"
    }
    LaunchedEffect(currentSuggestedModel) {
        isCustomModelSelected = currentSuggestedModel.first == "custom"
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // --- PROVIDER SECTION ---
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "🌐 API Sağlayıcı / Base URL:",
                style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { baseDropdownExpanded = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentProvider.second,
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Seç",
                            tint = electricCyan
                        )
                    }
                }

                DropdownMenu(
                    expanded = baseDropdownExpanded,
                    onDismissRequest = { baseDropdownExpanded = false },
                    modifier = Modifier
                        .width(300.dp)
                        .background(Color(0xFF13151D))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                ) {
                    providers.forEach { p ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = p.second,
                                    color = if (p.first == currentProvider.first) electricCyan else Color.White,
                                    fontSize = 13.sp
                                )
                            },
                            onClick = {
                                baseDropdownExpanded = false
                                if (p.first == "custom") {
                                    isCustomBaseSelected = true
                                    viewModel.setGeminiApiBase("")
                                } else {
                                    isCustomBaseSelected = false
                                    viewModel.setGeminiApiBase(p.first)
                                    // Match default recommended model for this provider
                                    val firstModel = when (p.first) {
                                        "https://generativelanguage.googleapis.com" -> "gemini-1.5-flash"
                                        "https://api.groq.com/openai/v1" -> "llama-3.3-70b-versatile"
                                        "https://openrouter.ai/api/v1" -> "google/gemini-2.0-flash-exp:free"
                                        "https://api.openai.com/v1" -> "gpt-4o-mini"
                                        "https://api.deepseek.com/v1" -> "deepseek-chat"
                                        else -> ""
                                    }
                                    if (firstModel.isNotEmpty()) {
                                        viewModel.setGeminiModelName(firstModel)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        // Custom base URL text field if Custom selected
        if (isCustomBaseSelected) {
            OutlinedTextField(
                value = geminiApiBase,
                onValueChange = { viewModel.setGeminiApiBase(it) },
                label = { Text("Özel Base URL Girin", fontSize = 10.sp, color = Color.Gray) },
                placeholder = { Text("https://kendi-api-hizmetiniz.com/v1", fontSize = 11.sp, color = Color.DarkGray) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = sportAccentRed,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    focusedContainerColor = Color.Black.copy(alpha = 0.15f),
                    unfocusedContainerColor = Color.Black.copy(alpha = 0.15f)
                )
            )
        }

        // --- MODEL SECTION ---
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "🤖 Model Seçimi:",
                style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { modelDropdownExpanded = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isCustomModelSelected) "Özel Model Belirt..." else currentSuggestedModel.first,
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Seç",
                            tint = electricCyan
                        )
                    }
                }

                DropdownMenu(
                    expanded = modelDropdownExpanded,
                    onDismissRequest = { modelDropdownExpanded = false },
                    modifier = Modifier
                        .width(300.dp)
                        .background(Color(0xFF13151D))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                ) {
                    suggestedModels.forEach { m ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = m.first,
                                        color = if (m.first == currentSuggestedModel.first) electricCyan else Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (m.second.isNotEmpty() && m.second != m.first) {
                                        Text(
                                            text = m.second,
                                            color = Color.Gray,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            },
                            onClick = {
                                modelDropdownExpanded = false
                                if (m.first == "custom") {
                                    isCustomModelSelected = true
                                    viewModel.setGeminiModelName("")
                                } else {
                                    isCustomModelSelected = false
                                    viewModel.setGeminiModelName(m.first)
                                }
                            }
                        )
                    }
                }
            }
        }

        // Custom model name text field if Custom selected
        if (isCustomModelSelected || currentProvider.first == "custom") {
            OutlinedTextField(
                value = geminiModelName,
                onValueChange = { viewModel.setGeminiModelName(it) },
                label = { Text("Özel Model İsmi Girin", fontSize = 10.sp, color = Color.Gray) },
                placeholder = { Text("Örn: deepseek-chat", fontSize = 11.sp, color = Color.DarkGray) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = sportAccentRed,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    focusedContainerColor = Color.Black.copy(alpha = 0.15f),
                    unfocusedContainerColor = Color.Black.copy(alpha = 0.15f)
                )
            )
        }
    }
}

@Composable
fun CameraTopBarIndicators(viewModel: ObdDashboardViewModel, electricCyan: Color, sportAccentRed: Color) {
    val cameraMode by viewModel.cameraSourceMode.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val recordingSecs by viewModel.recordingSecs.collectAsState()
    val syncLatencyMs by viewModel.syncLatencyMs.collectAsState()
    val isXiaomiConnected by viewModel.isXiaomiConnected.collectAsState()
    val xiaomiBatteryVolt by viewModel.xiaomiBatteryVolt.collectAsState()
    val xiaomiSignalDbm by viewModel.xiaomiSignalDbm.collectAsState()
    val xiaomiSdCardFree by viewModel.xiaomiSdCardFree.collectAsState()
    val xiaomiEmergencyLocked by viewModel.xiaomiEmergencyLocked.collectAsState()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState())
    ) {
        // Live 60FPS / REC Badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        color = if (isRecording) sportAccentRed else Color(0xFF22C55E),
                        shape = CircleShape
                    )
            )
            Text(
                text = if (isRecording) {
                    val mins = recordingSecs / 60
                    val secs = recordingSecs % 60
                    String.format("REC %02d:%02d", mins, secs)
                } else {
                    "60FPS"
                },
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace
                )
            )
        }

        // Sync Latency Badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .padding(horizontal = 4.dp, vertical = 3.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Sync",
                tint = electricCyan,
                modifier = Modifier.size(8.dp)
            )
            Text(
                text = "${syncLatencyMs}ms",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = electricCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace
                )
            )
        }

        val isWifiMode = cameraMode == CameraSourceMode.XIAOMI_WIFI
        if (isWifiMode) {
            val wifiConnected = isXiaomiConnected
            val battVolt = xiaomiBatteryVolt
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Icon(
                    imageVector = if (wifiConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                    contentDescription = "Wi-Fi",
                    tint = if (wifiConnected) electricCyan else Color.Gray,
                    modifier = Modifier.size(8.dp)
                )
                Text(
                    text = if (wifiConnected) "${xiaomiSignalDbm}dBm" else "Wi-Fi",
                    fontSize = 8.sp,
                    color = if (wifiConnected) Color.LightGray else Color.Gray,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )

                Text("•", fontSize = 8.sp, color = Color.Gray)

                Icon(
                    imageVector = Icons.Default.BatteryChargingFull,
                    contentDescription = "Akü",
                    tint = Color(0xFF22C55E),
                    modifier = Modifier.size(8.dp)
                )
                Text(
                    text = battVolt,
                    fontSize = 8.sp,
                    color = Color.LightGray,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
