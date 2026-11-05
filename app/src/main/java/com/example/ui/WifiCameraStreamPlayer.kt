package com.example.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

enum class WifiStreamProtocol(val displayName: String, val defaultPort: String, val defaultPath: String) {
    RTSP("RTSP Akışı (Novatek / YI)", "554", "/"),
    HTTP_MJPEG("HTTP Akışı (Port 8192 MJPEG)", "8192", ""),
    HTTP_SNAPSHOT("HTTP Anlık Kare (Snapshot)", "80", "/?custom=1&cmd=1001"),
    CUSTOM("Özel URL Akışı", "554", "/")
}

@Composable
fun WifiCameraStreamPlayer(
    ipAddress: String,
    port: String,
    streamPath: String,
    protocol: WifiStreamProtocol,
    customUrl: String,
    speed: Int,
    useSimulationFallback: Boolean,
    electricCyan: Color,
    sportAccentRed: Color,
    onProtocolChange: (WifiStreamProtocol) -> Unit,
    onClose: (() -> Unit)? = null,
    onDiagnosticsVisibilityChanged: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isStreamLive by remember { mutableStateOf(false) }
    var isCheckingConnection by remember { mutableStateOf(false) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    var streamStatusText by remember { mutableStateOf("Kamera Akışı Hazırlanıyor...") }
    var resolutionText by remember { mutableStateOf("") }
    var mjpegBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var connectionRetryCount by remember { mutableIntStateOf(0) }
    var wifiSsidName by remember { mutableStateOf<String?>(null) }
    var isConnectionDismissed by remember { mutableStateOf(false) }
    var showDiagnosisManually by remember { mutableStateOf(false) }
    var forceRtpTcp by remember { mutableStateOf(true) }
    var activeIpOverride by remember { mutableStateOf<String?>(null) }
    var activePortOverride by remember { mutableStateOf<String?>(null) }
    var activePathOverride by remember { mutableStateOf<String?>(null) }
    var yiCommanderLog by remember { mutableStateOf<String?>(null) }
    var isScanningCandidates by remember { mutableStateOf(false) }
    var candidateResults by remember { mutableStateOf<List<Pair<String, Boolean>>>(emptyList()) }
    var snapshotBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isCapturingSnapshot by remember { mutableStateOf(false) }
    var showSnapshotDialog by remember { mutableStateOf(false) }
    var snapshotStatusMsg by remember { mutableStateOf<String?>(null) }
    var isCameraHardwareConnected by remember { mutableStateOf(false) }
    var hardwareStatus by remember { mutableStateOf<XiaomiCameraCommander.HardwareConnectivityStatus?>(null) }
    var isTestingHardware by remember { mutableStateOf(false) }

    val effectiveIp = activeIpOverride ?: ipAddress
    val effectivePort = activePortOverride ?: port
    val effectivePath = activePathOverride ?: streamPath

    val isDiagnosisShowing = (!isStreamLive && !isConnectionDismissed) || showDiagnosisManually
    val dismissDiagnostics: () -> Unit = {
        isConnectionDismissed = true
        showDiagnosisManually = false
    }

    LaunchedEffect(isDiagnosisShowing) {
        onDiagnosticsVisibilityChanged?.invoke(isDiagnosisShowing)
    }

    DisposableEffect(Unit) {
        onDispose {
            onDiagnosticsVisibilityChanged?.invoke(false)
        }
    }

    // Kamera Donanım Portları ve IP Canlılık Kontrolü + Novatek Otomatik Müzakere
    LaunchedEffect(effectiveIp, connectionRetryCount) {
        isTestingHardware = true
        val status = XiaomiCameraCommander.checkHardwareConnectivity(effectiveIp)
        hardwareStatus = status
        isTestingHardware = false

        // Eğer Port 80 veya 3333 açıksa, Novatek video modunu ve RTSP servisini başlat
        if (status.port80HttpOpen || status.port3333KeepaliveOpen) {
            val res = XiaomiCameraCommander.startNovatekStream(effectiveIp)
            yiCommanderLog = res.details

            if (res.port554OpenNow || res.movieLiveViewLink != null) {
                activePortOverride = "554"
                if (!res.movieLiveViewLink.isNullOrBlank()) {
                    val path = try { java.net.URI(res.movieLiveViewLink).path } catch (_: Exception) { "/" }
                    activePathOverride = if (path.isNullOrBlank()) "/" else path
                } else {
                    activePathOverride = "/"
                }
                if (protocol != WifiStreamProtocol.RTSP) {
                    onProtocolChange(WifiStreamProtocol.RTSP)
                }
            } else if (res.port8192OpenNow) {
                activePortOverride = "8192"
                activePathOverride = ""
                if (protocol != WifiStreamProtocol.HTTP_MJPEG) {
                    onProtocolChange(WifiStreamProtocol.HTTP_MJPEG)
                }
            }
        }
    }

    // Novatek / YI Smart Dashcam RTSP Aday Akış Listesi
    val candidateStreams = remember(effectiveIp) {
        listOf(
            Triple("rtsp://$effectiveIp/", true, "Novatek Standart RTSP (rtsp://$effectiveIp/)"),
            Triple("rtsp://$effectiveIp/live.mov", true, "Novatek Canlı Akış (/live.mov)"),
            Triple("rtsp://$effectiveIp/xxx.mov", true, "YI Car Cam Standart RTSP (TCP - /xxx.mov)"),
            Triple("rtsp://$effectiveIp:554/xxx.mov", true, "YI RTSP Port 554 (TCP - /xxx.mov)"),
            Triple("rtsp://$effectiveIp/live", true, "RTSP Canlı Akış (/live)"),
            Triple("rtsp://$effectiveIp/sjcam.mov", true, "Novatek RTSP (TCP - /sjcam.mov)"),
            Triple("rtsp://$effectiveIp/ch0_0.h264", true, "H.264 Doğrudan Akış (/ch0_0.h264)")
        )
    }
    var activeCandidateIndex by remember { mutableIntStateOf(0) }

    // Re-open card if user explicitly clicks retry
    LaunchedEffect(connectionRetryCount) {
        if (connectionRetryCount > 0) {
            isConnectionDismissed = false
            activeCandidateIndex = 0
        }
    }

    // Bağlantı sağlandığında ayar ve teşhis penceresini otomatik olarak kaldır
    LaunchedEffect(isStreamLive) {
        if (isStreamLive) {
            delay(350)
            isConnectionDismissed = true
        }
    }

    // Resolve exact stream URL
    val streamUrl = remember(customUrl, protocol, effectiveIp, effectivePort, effectivePath, activeCandidateIndex) {
        if (customUrl.isNotBlank()) {
            customUrl.trim()
        } else if (protocol == WifiStreamProtocol.RTSP && activePathOverride == null) {
            candidateStreams.getOrNull(activeCandidateIndex % candidateStreams.size)?.first
                ?: "rtsp://$effectiveIp/xxx.mov"
        } else {
            val cleanPath = if (effectivePath.isBlank()) "/xxx.mov" else if (effectivePath.startsWith("/")) effectivePath else "/$effectivePath"
            when (protocol) {
                WifiStreamProtocol.RTSP, WifiStreamProtocol.CUSTOM -> {
                    if (effectivePort == "554" || effectivePort.isBlank()) "rtsp://$effectiveIp$cleanPath"
                    else "rtsp://$effectiveIp:$effectivePort$cleanPath"
                }
                WifiStreamProtocol.HTTP_MJPEG, WifiStreamProtocol.HTTP_SNAPSHOT -> "http://$effectiveIp:$effectivePort$cleanPath"
            }
        }
    }

    // 1. Wi-Fi Ağ Bağlama (Android Network Binding)
    // İnterneti olmayan araç kamerası Wi-Fi ağına bağlandığında soketlerin Mobil Veriye kaçmasını engeller.
    DisposableEffect(connectionRetryCount) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        var networkCallback: ConnectivityManager.NetworkCallback? = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && connectivityManager != null) {
                // Mevcut Wi-Fi ağını derhal bağla
                for (network in connectivityManager.allNetworks) {
                    val caps = connectivityManager.getNetworkCapabilities(network)
                    if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        connectivityManager.bindProcessToNetwork(network)
                        Log.d("WifiStreamPlayer", "Bound process to existing Wi-Fi network immediately")
                        break
                    }
                }

                // Dinamik takip için callback kaydet
                val request = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build()

                networkCallback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        connectivityManager.bindProcessToNetwork(network)
                        Log.d("WifiStreamPlayer", "Wi-Fi onAvailable -> Bound process")
                    }
                }
                connectivityManager.registerNetworkCallback(request, networkCallback)
            }

            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val info = wifiManager?.connectionInfo
            val ssid = info?.ssid?.replace("\"", "")
            wifiSsidName = if (!ssid.isNullOrBlank() && ssid != "<unknown ssid>") ssid else "Wi-Fi Ağı Bağlı"
        } catch (e: Exception) {
            Log.w("WifiStreamPlayer", "Wi-Fi query failed: ${e.message}")
        }

        onDispose {
            try {
                if (networkCallback != null && connectivityManager != null) {
                    connectivityManager.unregisterNetworkCallback(networkCallback)
                }
            } catch (_: Exception) {}
        }
    }

    // 2. Donanımsal Akış Tetikleyici (192.168.1.254 Novatek HTTP RPC veya TCP 7878 Xiaomi YI)
    // Novatek/YI Car Cam (192.168.1.254): cmd=8001 ve cmd=3001 ile video akışı açılır ve kalp atışı gerekir!
    // Xiaomi YI kameralar (192.168.42.1): Port 7878 üzerinden JSON komutu almadan video aktarmaz!
    LaunchedEffect(effectiveIp, connectionRetryCount) {
        if (effectiveIp.startsWith("192.168.1")) {
            yiCommanderLog = "YI Car Cam (Novatek 192.168.1.254) tespit edildi. HTTP komutları (cmd=8001, cmd=3001) gönderiliyor..."
            val result = XiaomiCameraCommander.startNovatekStream(effectiveIp)
            if (result.success) {
                yiCommanderLog = "✓ YI Car Cam Video Sensörü & RTSP Aktif (cmd=8001 & cmd=3001 OK)"
                // Heartbeat canlı tutma başlat (Akış 10 sn sonra kesilmesin diye)
                coroutineScope.launch {
                    XiaomiCameraCommander.maintainNovatekKeepAlive(effectiveIp) {
                        Log.d("WifiStreamPlayer", "Novatek Heartbeat: $it")
                    }
                }
            } else {
                yiCommanderLog = "YI Car Cam (192.168.1.254) yanıtı: ${result.message}"
            }
        } else if (protocol == WifiStreamProtocol.RTSP || protocol == WifiStreamProtocol.CUSTOM) {
            val isLikelyYiOrXiaomi = effectiveIp.startsWith("192.168.42") || effectiveIp.startsWith("192.168.0")
            if (isLikelyYiOrXiaomi) {
                yiCommanderLog = "Xiaomi/YI donanım komutu gönderiliyor (Port 7878)..."
                val result = XiaomiCameraCommander.startYiVideoStream(effectiveIp)
                if (result.success) {
                    yiCommanderLog = "✓ Kamera video sensörü aktif (Token: ${result.token})"
                    // Canlı tutma (Keep-alive) başlat
                    coroutineScope.launch {
                        XiaomiCameraCommander.maintainStreamKeepAlive(effectiveIp, result.token) {
                            Log.d("WifiStreamPlayer", "Heartbeat status: $it")
                        }
                    }
                } else {
                    yiCommanderLog = "Port 7878 yanıtı: ${result.message}"
                }
            }
        }
    }

    // 3. Port & Ping Reachability Test
    LaunchedEffect(streamUrl, connectionRetryCount) {
        isCheckingConnection = true
        connectionError = null
        withContext(Dispatchers.IO) {
            try {
                val targetPort = effectivePort.toIntOrNull() ?: 554
                Socket().use { socket ->
                    socket.soTimeout = 2500
                    socket.connect(InetSocketAddress(effectiveIp, targetPort), 2500)
                }
                Log.d("WifiStreamPlayer", "Port $targetPort on $effectiveIp is reachable!")
            } catch (e: Exception) {
                Log.d("WifiStreamPlayer", "Port check: ${e.message}")
            }
        }
        isCheckingConnection = false
    }

    // 4. ExoPlayer RTSP Oynatıcı Oluşturma (TCP Interleaved & Aday Geçişi)
    val isRtsp = protocol == WifiStreamProtocol.RTSP || (protocol == WifiStreamProtocol.CUSTOM && streamUrl.startsWith("rtsp", ignoreCase = true))

    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    val currentCandidate = candidateStreams.getOrNull(activeCandidateIndex % candidateStreams.size)
    val effectiveTcpSetting = if (customUrl.isNotBlank() || activePathOverride != null) forceRtpTcp else (currentCandidate?.second ?: forceRtpTcp)

    // Watchdog: Aday akıştan 4 sn içinde kare gelmezse sıradaki aday RTSP akışını dene
    LaunchedEffect(activeCandidateIndex, connectionRetryCount) {
        if (!isStreamLive && customUrl.isBlank() && isRtsp) {
            delay(4000)
            if (!isStreamLive) {
                if (activeCandidateIndex < candidateStreams.size - 1) {
                    Log.d("WifiStreamPlayer", "Candidate $activeCandidateIndex timed out, switching to next")
                    activeCandidateIndex++
                } else if (hardwareStatus?.port8192MjpegOpen == true) {
                    Log.d("WifiStreamPlayer", "Tüm RTSP adayları denendi, Port 8192 MJPEG deneniyor")
                    activePortOverride = "8192"
                    activePathOverride = ""
                    onProtocolChange(WifiStreamProtocol.HTTP_MJPEG)
                }
            }
        }
    }

    DisposableEffect(streamUrl, isRtsp, effectiveTcpSetting, connectionRetryCount) {
        if (isRtsp) {
            isStreamLive = false
            connectionError = null
            val candDesc = currentCandidate?.third ?: streamUrl
            streamStatusText = "RTSP Akışı Bağlanıyor: $candDesc..."

            val lowLatencyLoadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    250,
                    1000,
                    100,
                    250
                )
                .build()

            val player = ExoPlayer.Builder(context)
                .setLoadControl(lowLatencyLoadControl)
                .build().apply {
                val mediaItem = MediaItem.fromUri(Uri.parse(streamUrl))
                val rtspMediaSource = RtspMediaSource.Factory()
                    .setForceUseRtpTcp(effectiveTcpSetting) // TCP interleaved: Paket kaybını önler
                    .setDebugLoggingEnabled(true)
                    .setTimeoutMs(4000)
                    .createMediaSource(mediaItem)

                setMediaSource(rtspMediaSource)
                prepare()
                playWhenReady = true

                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_READY -> {
                                isStreamLive = true
                                isCameraHardwareConnected = true
                                connectionError = null
                                isConnectionDismissed = true
                                streamStatusText = "Canlı Video Akışı Aktif"
                            }
                            Player.STATE_BUFFERING -> {
                                streamStatusText = "Video Ara Belleğe Alınıyor..."
                            }
                            Player.STATE_ENDED -> {
                                streamStatusText = "Akış Bitti"
                            }
                            Player.STATE_IDLE -> {}
                        }
                    }

                    override fun onRenderedFirstFrame() {
                        isStreamLive = true
                        isCameraHardwareConnected = true
                        connectionError = null
                        isConnectionDismissed = true
                        streamStatusText = "✓ Canlı Görüntü Alındı"
                    }

                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        if (videoSize.width > 0 && videoSize.height > 0) {
                            resolutionText = "${videoSize.width}x${videoSize.height}"
                            isStreamLive = true
                            isCameraHardwareConnected = true
                            isConnectionDismissed = true
                        }
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        val msg = error.message ?: error.errorCodeName
                        Log.w("WifiStreamPlayer", "ExoPlayer error on $streamUrl: $msg", error)
                        if (customUrl.isBlank() && activePathOverride == null && activeCandidateIndex < candidateStreams.size - 1) {
                            activeCandidateIndex++
                            val next = candidateStreams[activeCandidateIndex]
                            streamStatusText = "Alternatif deneniyor (${activeCandidateIndex + 1}/${candidateStreams.size}): ${next.third}"
                            connectionError = null
                        } else {
                            connectionError = "RTSP Bağlantı Hatası: $msg"
                            isStreamLive = false
                        }
                    }
                })
            }

            exoPlayer = player

            onDispose {
                player.release()
                exoPlayer = null
            }
        } else {
            exoPlayer?.release()
            exoPlayer = null
            onDispose {}
        }
    }

    // 5. Port 3333 Donanım El Sıkışması ve Video Akış Motoru
    // A) Port 3333 Arka Plan Soketi: Kamera ekranında telefon bağlantı ikonunu sürekli açık tutar
    LaunchedEffect(effectiveIp, connectionRetryCount) {
        if (effectiveIp.startsWith("192.168.1")) {
            withContext(Dispatchers.IO) {
                while (isActive) {
                    try {
                        Socket().use { socket ->
                            socket.soTimeout = 15000
                            socket.connect(InetSocketAddress(effectiveIp, 3333), 3000)
                            isCameraHardwareConnected = true
                            connectionError = null
                            streamStatusText = "Kamera Donanımı Bağlandı (Port 3333 Aktif)"
                            Log.d("WifiStreamPlayer", "Port 3333 connected successfully to $effectiveIp")
                            val buf = ByteArray(512)
                            val inputStream = socket.getInputStream()
                            while (isActive && !socket.isClosed) {
                                val len = inputStream.read(buf)
                                if (len == -1) break
                            }
                        }
                    } catch (e: Exception) {
                        Log.d("WifiStreamPlayer", "Port 3333 keepalive: ${e.message}")
                        delay(3500)
                    }
                }
            }
        }
    }

    // B) Video Akış & Kare Alıcı (Port 8192 Novatek MJPEG / Port 80 HTTP Snapshots)
    LaunchedEffect(streamUrl, protocol, effectiveIp, effectivePort, connectionRetryCount) {
        if (protocol == WifiStreamProtocol.HTTP_MJPEG) {
            isStreamLive = false
            withContext(Dispatchers.IO) {
                // Novatek fotoğraf / canlı akış modunu tetikle (cmd=3001&par=0)
                try {
                    val triggerUrl = URL("http://$effectiveIp/?custom=1&cmd=3001&par=0")
                    val c = (triggerUrl.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 1500
                        readTimeout = 1500
                        setRequestProperty("User-Agent", "YICarCam-Android")
                    }
                    c.responseCode
                    c.disconnect()
                } catch (_: Exception) {}

                val mjpegUrl = if (streamUrl.startsWith("http")) streamUrl else "http://$effectiveIp:8192"
                while (isActive) {
                    streamStatusText = "Port 8192 MJPEG Akışı Başlatılıyor..."
                    XiaomiCameraCommander.streamMjpegFrames(
                        streamUrl = mjpegUrl,
                        onFrame = { bmp ->
                            mjpegBitmap = bmp
                            isStreamLive = true
                            isCameraHardwareConnected = true
                            connectionError = null
                            streamStatusText = "Canlı Kamera Akışı Aktif (${bmp.width}x${bmp.height})"
                            resolutionText = "${bmp.width}x${bmp.height}"
                        },
                        onError = { err ->
                            connectionError = err
                            streamStatusText = "MJPEG Yeniden Bağlanılıyor..."
                        }
                    )
                    delay(1200)
                }
            }
        } else if (protocol == WifiStreamProtocol.HTTP_SNAPSHOT) {
            isStreamLive = false
            withContext(Dispatchers.IO) {
                val candidateUrls = listOf(
                    "http://$effectiveIp/?custom=1&cmd=1001",
                    "http://$effectiveIp/?custom=1&cmd=4001",
                    "http://$effectiveIp/snapshot.jpg",
                    "http://$effectiveIp/cgi-bin/Config.cgi?action=snapshot",
                    "http://$effectiveIp/web/snapshot.jpg"
                )

                while (isActive) {
                    var frameDecoded = false
                    for (candidate in candidateUrls) {
                        if (!isActive || frameDecoded) break
                        var connection: HttpURLConnection? = null
                        try {
                            val url = URL(candidate)
                            connection = (url.openConnection() as HttpURLConnection).apply {
                                connectTimeout = 1500
                                readTimeout = 2000
                                setRequestProperty("User-Agent", "YICarCam-Android")
                            }
                            val responseCode = connection.responseCode
                            if (responseCode in 200..299) {
                                val bytes = connection.inputStream.use { it.readBytes() }
                                if (bytes.size > 1000 && (bytes[0].toInt() and 0xFF == 0xFF) && (bytes[1].toInt() and 0xFF == 0xD8)) {
                                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                    if (bmp != null) {
                                        mjpegBitmap = bmp
                                        isStreamLive = true
                                        isCameraHardwareConnected = true
                                        connectionError = null
                                        streamStatusText = "Canlı Anlık Kare Aktif (${bmp.width}x${bmp.height})"
                                        resolutionText = "${bmp.width}x${bmp.height}"
                                        frameDecoded = true
                                    }
                                }
                            }
                        } catch (_: Exception) {
                        } finally {
                            connection?.disconnect()
                        }
                    }

                    if (frameDecoded) {
                        delay(80) // ~12 FPS
                    } else {
                        if (isCameraHardwareConnected) {
                            connectionError = null
                            streamStatusText = "Kamera Donanımı Bağlı • Akış Bekleniyor..."
                        } else {
                            connectionError = "Kamera Yanıtı Bekleniyor ($effectiveIp)"
                            streamStatusText = "Kameraya Bağlanılıyor..."
                        }
                        delay(1200)
                    }
                }
            }
        }
    }


    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // KATMAN A: Video Görüntü Katmanı
        if (isRtsp) {
            // Modern Media3 PlayerView (TextureView modu ile Compose z-order ve render sorunsuz)
            exoPlayer?.let { player ->
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            this.player = player
                        }
                    },
                    update = { playerView ->
                        playerView.player = player
                    }
                )
            }
        } else {
            mjpegBitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Wi-Fi Kamera Canlı Görüntüsü",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        // KATMAN B: Simülasyon Yedek Katmanı (Kameradan henüz görüntü gelmediğinde)
        if (!isStreamLive && useSimulationFallback) {
            DashcamSimulatorCanvas(
                speed = speed,
                environment = CameraEnvironment.NIGHT_HIGHWAY,
                modifier = Modifier.fillMaxSize()
            )
        }

        // KATMAN C: Teşhis & Canlı Bağlantı Kılavuz Paneli (Akış beklenirken veya hata durumunda)
        // KATMAN C: TAM EKRAN WI-FI KAMERA TEŞHİS VE CANLI AKIŞ KONTROL MERKEZİ
        // Blok / kutu şeklinde değil, ekranı tamamen kaplayan profesyonel teşhis ekranı
        if (isDiagnosisShowing) {
            val configuration = LocalConfiguration.current
            val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

            // Android donanımsal geri tuşuna basıldığında teşhis ekranını kapat ve HUD'a dön
            BackHandler(enabled = true) {
                dismissDiagnostics()
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0C0F17))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = if (isLandscape) 14.dp else 12.dp, vertical = 8.dp)
                ) {
                    // 1. TAM EKRAN ÜST ÇUBUK: GERİ OKU, BAŞLIK VE KAPAT BUTONU
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            IconButton(
                                onClick = { dismissDiagnostics() },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.White.copy(alpha = 0.12f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Geri / Göstergeye Dön",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = "WI-FI KAMERA TEŞHİS & CANLI AKIŞ",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        fontSize = if (isLandscape) 14.sp else 13.sp,
                                        letterSpacing = 0.8.sp
                                    )
                                )
                                Text(
                                    text = "YI & Novatek Araç Kamerası (192.168.1.254) Donanım ve Akış Merkezi",
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                            }
                        }

                        // Sağ Üst Hızlı Kapat Butonu
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1E293B),
                            border = BorderStroke(1.dp, electricCyan.copy(alpha = 0.6f)),
                            onClick = { dismissDiagnostics() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color.White, modifier = Modifier.size(14.dp))
                                Text(
                                    text = "GÖSTERGEYE DÖN",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

                    // 2. İÇERİK BÖLÜMÜ (Yatay Ekranda 2 Kolon, Dikey Ekranda Tek Kolon)
                    if (isLandscape) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // SOL SÜTUN: Port Teşhisleri, Ping, Wi-Fi Ağı ve Web Klasörleri
                            Column(
                                modifier = Modifier
                                    .weight(1.1f)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // DONANIM PORT DOĞRULAMA KARTI
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF131722),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "DONANIM PORT TESTİ:",
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = electricCyan
                                            )
                                            if (isTestingHardware) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    CircularProgressIndicator(color = electricCyan, modifier = Modifier.size(11.dp), strokeWidth = 1.5.dp)
                                                    Text("Test ediliyor...", fontSize = 9.sp, color = Color.Gray)
                                                }
                                            } else {
                                                Text(
                                                    text = if (hardwareStatus?.ipReachable == true) "Kamera Erişilebilir (Ping: ${hardwareStatus?.pingTimeMs}ms)" else "Kamera Çevrimdışı",
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (hardwareStatus?.ipReachable == true) Color(0xFF34D399) else Color(0xFFFF5252)
                                                )
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            val hs = hardwareStatus
                                            val p554Open = hs?.port554RtspOpen == true
                                            val p3333Open = hs?.port3333KeepaliveOpen == true
                                            val p80Open = hs?.port80HttpOpen == true

                                            Surface(
                                                shape = RoundedCornerShape(5.dp),
                                                color = if (p554Open) Color(0xFF00E676).copy(alpha = 0.18f) else Color.Red.copy(alpha = 0.12f),
                                                border = BorderStroke(0.5.dp, if (p554Open) Color(0xFF00E676) else Color.Red.copy(alpha = 0.4f)),
                                                modifier = Modifier.weight(1.2f)
                                            ) {
                                                Text(
                                                    text = if (p554Open) "554 RTSP: Açık ✓" else "554 RTSP: Kapalı",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (p554Open) Color(0xFF00E676) else Color(0xFFFF5252),
                                                    modifier = Modifier.padding(vertical = 3.dp, horizontal = 4.dp),
                                                    textAlign = TextAlign.Center
                                                )
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(5.dp),
                                                color = if (p3333Open) Color(0xFF38BDF8).copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f),
                                                border = BorderStroke(0.5.dp, if (p3333Open) Color(0xFF38BDF8) else Color.Gray.copy(alpha = 0.4f)),
                                                modifier = Modifier.weight(1.1f)
                                            ) {
                                                Text(
                                                    text = if (p3333Open) "3333: Bağlı ✓" else "3333: Bekliyor",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (p3333Open) Color(0xFF38BDF8) else Color.LightGray,
                                                    modifier = Modifier.padding(vertical = 3.dp, horizontal = 4.dp),
                                                    textAlign = TextAlign.Center
                                                )
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(5.dp),
                                                color = if (p80Open) Color(0xFFFBBF24).copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f),
                                                border = BorderStroke(0.5.dp, if (p80Open) Color(0xFFFBBF24) else Color.Gray.copy(alpha = 0.4f)),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    text = if (p80Open) "80 Web: Açık ✓" else "80 Web: Kapalı",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (p80Open) Color(0xFFFBBF24) else Color.Gray,
                                                    modifier = Modifier.padding(vertical = 3.dp, horizontal = 4.dp),
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }

                                        // 1-Dokunuşla Otomatik Çözümle Butonu
                                        Button(
                                            onClick = {
                                                coroutineScope.launch {
                                                    isTestingHardware = true
                                                    yiCommanderLog = "Novatek el sıkışması başlatılıyor (cmd=2001&par=0, cmd=3001&par=1, cmd=2019)..."
                                                    val res = XiaomiCameraCommander.startNovatekStream(effectiveIp)
                                                    val status = XiaomiCameraCommander.checkHardwareConnectivity(effectiveIp)
                                                    hardwareStatus = status
                                                    isTestingHardware = false
                                                    yiCommanderLog = res.details

                                                    if (status.port554RtspOpen || res.port554OpenNow) {
                                                        activePortOverride = "554"
                                                        if (!res.movieLiveViewLink.isNullOrBlank()) {
                                                            val path = try { java.net.URI(res.movieLiveViewLink).path } catch (_: Exception) { "/" }
                                                            activePathOverride = if (path.isNullOrBlank()) "/" else path
                                                        } else {
                                                            activePathOverride = "/"
                                                        }
                                                        onProtocolChange(WifiStreamProtocol.RTSP)
                                                    } else if (status.port8192MjpegOpen || res.port8192OpenNow) {
                                                        activePortOverride = "8192"
                                                        activePathOverride = ""
                                                        onProtocolChange(WifiStreamProtocol.HTTP_MJPEG)
                                                    }
                                                    connectionRetryCount++
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                            border = BorderStroke(1.dp, electricCyan.copy(alpha = 0.6f)),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(vertical = 6.dp, horizontal = 8.dp)
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, tint = electricCyan, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Portları Test Et & Otomatik Bağlan", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                // Wi-Fi Ağı & Durum Kutusu
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color.Black.copy(alpha = 0.45f),
                                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Wi-Fi Ağı:", style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray, fontSize = 11.sp))
                                            Text(
                                                text = wifiSsidName ?: "Wi-Fi Aranıyor...",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = if (wifiSsidName != null) Color(0xFF34D399) else Color(0xFFFBBF24),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp
                                                )
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Hedef URL:", style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray, fontSize = 11.sp))
                                            Text(
                                                text = streamUrl,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = electricCyan,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 10.5.sp
                                                )
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Protokol:", style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray, fontSize = 11.sp))
                                            Text(
                                                text = protocol.displayName,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = Color(0xFF38BDF8),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.5.sp
                                                )
                                            )
                                        }

                                        yiCommanderLog?.let { log ->
                                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 2.dp))
                                            Text(
                                                text = "Donanım: $log",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = if (log.startsWith("✓")) Color(0xFF34D399) else Color(0xFFF59E0B),
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 9.5.sp
                                                )
                                            )
                                        }
                                    }
                                }

                                // Dahili Web & Klasör Kısayolları (YICARCAM / DrivingInfoFile)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF0F172A),
                                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "KAMERA DOSYALARI (192.168.1.254):",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF38BDF8)
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    try {
                                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://$effectiveIp/"))
                                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        context.startActivity(intent)
                                                    } catch (_: Exception) {}
                                                },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(6.dp),
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Text("Web Arayüzü", fontSize = 9.5.sp, maxLines = 1)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    try {
                                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://$effectiveIp/YICARCAM"))
                                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        context.startActivity(intent)
                                                    } catch (_: Exception) {}
                                                },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(6.dp),
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Text("Videolar (SD)", fontSize = 9.5.sp, maxLines = 1)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    try {
                                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://$effectiveIp/DrivingInfoFile"))
                                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        context.startActivity(intent)
                                                    } catch (_: Exception) {}
                                                },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(6.dp),
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Text("Sürüş Logları", fontSize = 9.5.sp, maxLines = 1)
                                            }
                                        }
                                    }
                                }
                            }

                            // SAĞ SÜTUN: Canlı Önizleme Kutusu, Protokol Seçimi ve Aksiyonlar
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // CANLI KAMERA ÖNİZLEME PENCERESİ
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color.Black,
                                    border = BorderStroke(1.dp, if (mjpegBitmap != null) Color(0xFF00E676) else electricCyan.copy(alpha = 0.4f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(130.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        if (mjpegBitmap != null) {
                                            Image(
                                                bitmap = mjpegBitmap!!.asImageBitmap(),
                                                contentDescription = "Canlı Kamera Görüntüsü",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color.Black.copy(alpha = 0.7f),
                                                modifier = Modifier
                                                    .align(Alignment.BottomStart)
                                                    .padding(6.dp)
                                            ) {
                                                Text(
                                                    text = "CANLI GÖRÜNTÜ AKTİF ($resolutionText)",
                                                    color = Color(0xFF00E676),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        } else {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                CircularProgressIndicator(color = electricCyan, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                                Text(
                                                    text = connectionError ?: streamStatusText,
                                                    color = Color.LightGray,
                                                    fontSize = 10.sp,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.padding(horizontal = 8.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // AKIŞ PROTOKOLÜ SEÇİMİ
                                Text(
                                    text = "AKIŞ PROTOKOLÜ SEÇİN:",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = electricCyan
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    FilterChip(
                                        selected = protocol == WifiStreamProtocol.RTSP,
                                        onClick = {
                                            activePortOverride = "554"
                                            activePathOverride = "/"
                                            onProtocolChange(WifiStreamProtocol.RTSP)
                                            connectionRetryCount++
                                        },
                                        label = { Text("RTSP 554 (Önerilen)", fontSize = 9.sp, fontWeight = FontWeight.Bold) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF00E676),
                                            selectedLabelColor = Color.Black
                                        ),
                                        modifier = Modifier.weight(1.3f)
                                    )
                                    FilterChip(
                                        selected = protocol == WifiStreamProtocol.HTTP_MJPEG,
                                        onClick = {
                                            activePortOverride = "8192"
                                            activePathOverride = ""
                                            onProtocolChange(WifiStreamProtocol.HTTP_MJPEG)
                                            connectionRetryCount++
                                        },
                                        label = { Text("MJPEG 8192", fontSize = 9.sp) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilterChip(
                                        selected = protocol == WifiStreamProtocol.HTTP_SNAPSHOT,
                                        onClick = {
                                            activePortOverride = "80"
                                            activePathOverride = "/?custom=1&cmd=1001"
                                            onProtocolChange(WifiStreamProtocol.HTTP_SNAPSHOT)
                                            connectionRetryCount++
                                        },
                                        label = { Text("Port 80 Foto", fontSize = 9.sp) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                // AKSİYON BUTONLARI: Tetikle & Bağlan
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                yiCommanderLog = "Novatek RTSP ve MJPEG akışı uyandırılıyor..."
                                                val res = XiaomiCameraCommander.startNovatekStream(effectiveIp)
                                                yiCommanderLog = res.details
                                                val status = XiaomiCameraCommander.checkHardwareConnectivity(effectiveIp)
                                                hardwareStatus = status
                                                if (status.port554RtspOpen || res.port554OpenNow) {
                                                    activePortOverride = "554"
                                                    if (!res.movieLiveViewLink.isNullOrBlank()) {
                                                        val path = try { java.net.URI(res.movieLiveViewLink).path } catch (_: Exception) { "/" }
                                                        activePathOverride = if (path.isNullOrBlank()) "/" else path
                                                    } else {
                                                        activePathOverride = "/"
                                                    }
                                                    onProtocolChange(WifiStreamProtocol.RTSP)
                                                } else if (status.port8192MjpegOpen || res.port8192OpenNow) {
                                                    activePortOverride = "8192"
                                                    activePathOverride = ""
                                                    onProtocolChange(WifiStreamProtocol.HTTP_MJPEG)
                                                }
                                                connectionRetryCount++
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E676)),
                                        border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.5f)),
                                        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp)
                                    ) {
                                        Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Akışı Tetikle", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = { connectionRetryCount++ },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = electricCyan),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Yeniden Bağlan", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 9.5.sp)
                                    }
                                }

                                // BÜYÜK TAM EKRAN KAPAT BUTONU
                                Button(
                                    onClick = { dismissDiagnostics() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("✓ GÖSTERGEYE DÖN (TAM EKRAN GÖRÜNTÜLE)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }
                    } else {
                        // PORTRAIT (Dikey Ekran Düzeni)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Canlı Önizleme Kutusu
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.Black,
                                border = BorderStroke(1.dp, if (mjpegBitmap != null) Color(0xFF00E676) else electricCyan.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    if (mjpegBitmap != null) {
                                        Image(
                                            bitmap = mjpegBitmap!!.asImageBitmap(),
                                            contentDescription = "Canlı Kamera Görüntüsü",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color.Black.copy(alpha = 0.7f),
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(8.dp)
                                        ) {
                                            Text(
                                                text = "CANLI GÖRÜNTÜ AKTİF ($resolutionText)",
                                                color = Color(0xFF00E676),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    } else {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            CircularProgressIndicator(color = electricCyan, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                                            Text(
                                                text = connectionError ?: streamStatusText,
                                                color = Color.LightGray,
                                                fontSize = 11.sp,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(horizontal = 12.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Donanım Port Doğrulama
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF131722),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "DONANIM PORT TESTİ:",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = electricCyan
                                        )
                                        Text(
                                            text = if (hardwareStatus?.ipReachable == true) "Ping: ${hardwareStatus?.pingTimeMs}ms" else "Kamera Çevrimdışı",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (hardwareStatus?.ipReachable == true) Color(0xFF34D399) else Color(0xFFFF5252)
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        val hs = hardwareStatus
                                        val p554Open = hs?.port554RtspOpen == true
                                        val p3333Open = hs?.port3333KeepaliveOpen == true
                                        val p80Open = hs?.port80HttpOpen == true

                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (p554Open) Color(0xFF00E676).copy(alpha = 0.18f) else Color.Red.copy(alpha = 0.12f),
                                            border = BorderStroke(0.5.dp, if (p554Open) Color(0xFF00E676) else Color.Red.copy(alpha = 0.4f)),
                                            modifier = Modifier.weight(1.2f)
                                        ) {
                                            Text(
                                                text = if (p554Open) "554 RTSP: Açık ✓" else "554 RTSP: Kapalı",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (p554Open) Color(0xFF00E676) else Color(0xFFFF5252),
                                                modifier = Modifier.padding(vertical = 4.dp),
                                                textAlign = TextAlign.Center
                                            )
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (p3333Open) Color(0xFF38BDF8).copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f),
                                            border = BorderStroke(0.5.dp, if (p3333Open) Color(0xFF38BDF8) else Color.Gray.copy(alpha = 0.4f)),
                                            modifier = Modifier.weight(1.1f)
                                        ) {
                                            Text(
                                                text = if (p3333Open) "3333: Bağlı ✓" else "3333: Bekliyor",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (p3333Open) Color(0xFF38BDF8) else Color.LightGray,
                                                modifier = Modifier.padding(vertical = 4.dp),
                                                textAlign = TextAlign.Center
                                            )
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (p80Open) Color(0xFFFBBF24).copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f),
                                            border = BorderStroke(0.5.dp, if (p80Open) Color(0xFFFBBF24) else Color.Gray.copy(alpha = 0.4f)),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = if (p80Open) "80 Web: Açık ✓" else "80 Web: Kapalı",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (p80Open) Color(0xFFFBBF24) else Color.Gray,
                                                modifier = Modifier.padding(vertical = 4.dp),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                isTestingHardware = true
                                                hardwareStatus = XiaomiCameraCommander.checkHardwareConnectivity(effectiveIp)
                                                isTestingHardware = false
                                                yiCommanderLog = "Novatek el sıkışması başlatılıyor..."
                                                val res = XiaomiCameraCommander.startNovatekStream(effectiveIp)
                                                val status = XiaomiCameraCommander.checkHardwareConnectivity(effectiveIp)
                                                hardwareStatus = status
                                                isTestingHardware = false
                                                yiCommanderLog = res.details

                                                if (status.port554RtspOpen || res.port554OpenNow) {
                                                    activePortOverride = "554"
                                                    if (!res.movieLiveViewLink.isNullOrBlank()) {
                                                        val path = try { java.net.URI(res.movieLiveViewLink).path } catch (_: Exception) { "/" }
                                                        activePathOverride = if (path.isNullOrBlank()) "/" else path
                                                    } else {
                                                        activePathOverride = "/"
                                                    }
                                                    onProtocolChange(WifiStreamProtocol.RTSP)
                                                } else if (status.port8192MjpegOpen || res.port8192OpenNow) {
                                                    activePortOverride = "8192"
                                                    activePathOverride = ""
                                                    onProtocolChange(WifiStreamProtocol.HTTP_MJPEG)
                                                }
                                                connectionRetryCount++
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                        border = BorderStroke(1.dp, electricCyan.copy(alpha = 0.6f)),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, tint = electricCyan, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Donanım Portlarını Test Et & Otomatik Bağlan", fontSize = 11.5.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // Protokol Seçimi
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                FilterChip(
                                    selected = protocol == WifiStreamProtocol.RTSP,
                                    onClick = {
                                        activePortOverride = "554"
                                        activePathOverride = "/"
                                        onProtocolChange(WifiStreamProtocol.RTSP)
                                        connectionRetryCount++
                                    },
                                    label = { Text("RTSP 554 (Önerilen)", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF00E676),
                                        selectedLabelColor = Color.Black
                                    ),
                                    modifier = Modifier.weight(1.3f)
                                )
                                FilterChip(
                                    selected = protocol == WifiStreamProtocol.HTTP_MJPEG,
                                    onClick = {
                                        activePortOverride = "8192"
                                        activePathOverride = ""
                                        onProtocolChange(WifiStreamProtocol.HTTP_MJPEG)
                                        connectionRetryCount++
                                    },
                                    label = { Text("MJPEG 8192", fontSize = 10.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = protocol == WifiStreamProtocol.HTTP_SNAPSHOT,
                                    onClick = {
                                        activePortOverride = "80"
                                        activePathOverride = "/?custom=1&cmd=1001"
                                        onProtocolChange(WifiStreamProtocol.HTTP_SNAPSHOT)
                                        connectionRetryCount++
                                    },
                                    label = { Text("Port 80 Foto", fontSize = 10.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Aksiyon Butonları
                            Button(
                                onClick = { connectionRetryCount++ },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = electricCyan),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Yeniden Bağlan & Akışı Başlat", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            Button(
                                onClick = { dismissDiagnostics() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pencereyi Kapat ve Göstergeye (HUD) Geç", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }
                }
            }
        }

        // Kapatıldığında Sağ Üstte Yeniden Açma Butonu (HUD görünürken teşhise dönüş imkanı sağlar)
        if (!isStreamLive && isConnectionDismissed) {
            Surface(
                onClick = {
                    isConnectionDismissed = false
                    showDiagnosisManually = true
                },
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF131722).copy(alpha = 0.9f),
                border = BorderStroke(1.dp, electricCyan.copy(alpha = 0.7f)),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = "Wi-Fi Ayarları",
                        tint = electricCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Kamera Teşhis",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        } else if (isStreamLive) {
            // Akış Aktifken Sağ Üst Durum Rozeti (Dokunulduğunda teşhis & port ayar panelini açar)
            Surface(
                onClick = {
                    showDiagnosisManually = true
                },
                shape = RoundedCornerShape(6.dp),
                color = Color.Black.copy(alpha = 0.65f),
                border = BorderStroke(1.dp, Color(0xFF34D399)),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 40.dp, end = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFF34D399), CircleShape)
                    )
                    Text(
                        text = if (resolutionText.isNotBlank()) "CANLI AKIŞ (${protocol.name}) • $resolutionText" else "CANLI AKIŞ (${protocol.name})",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF34D399)
                    )
                }
            }
        }

        // Kamera Anlık Fotoğraf Önizleme Dialogu
        if (showSnapshotDialog && snapshotBitmap != null) {
            AlertDialog(
                onDismissRequest = { showSnapshotDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = null, tint = Color(0xFF00E676))
                        Text("Kameradan Alınan Anlık Kare", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            bitmap = snapshotBitmap!!.asImageBitmap(),
                            contentDescription = "Kamera Snapshot",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .background(Color.Black, RoundedCornerShape(8.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        )
                        Text(
                            text = "Kamera lensinden canlı görüntü başarıyla doğrulandı (${snapshotBitmap!!.width}x${snapshotBitmap!!.height}).",
                            fontSize = 12.sp,
                            color = Color.LightGray
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSnapshotDialog = false }) {
                        Text("Kapat", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}
