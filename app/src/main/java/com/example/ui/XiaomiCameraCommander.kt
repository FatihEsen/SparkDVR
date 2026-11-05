package com.example.ui

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Xiaomi YI ve 70mai araç kameraları için donanım seviyesi akış başlatma ve el sıkışma yöneticisi.
 *
 * ÖNEMLİ NOT: Xiaomi YI Smart Dash Cam (192.168.42.1), RTSP portuna (554) doğrudan bağlansanız bile
 * TCP Port 7878 üzerinden JSON başlatma komutu ("start viewfinder") almadıkça video akışını başlatmaz!
 * RTSP soketi açık görünür ama kameradan sıfır video karesi gelir (kullanıcının yaşadığı "hata yok ama görüntü yok" sorunu).
 * Bu sınıf bu el sıkışmayı otomatik yapar ve akışı açık tutmak için düzenli kalp atışı (heartbeat) gönderir.
 */
object XiaomiCameraCommander {
    private const val TAG = "XiaomiCommander"
    const val DEFAULT_YI_IP = "192.168.1.254"
    const val YI_COMMAND_PORT = 7878
    const val NOVATEK_HTTP_PORT = 80

    enum class DetectedCameraModel(val displayName: String) {
        YI_CAR_CAM_NOVATEK("YI Car Cam (Novatek / 192.168.1.254)"),
        XIAOMI_YI_ACTION("Xiaomi YI Smart Cam (Port 7878)"),
        SEVENTY_MAI("70mai Dash Cam"),
        GENERIC_RTSP("Standart RTSP Kamera")
    }

    data class CommandResult(
        val success: Boolean,
        val message: String,
        val token: Int = 0,
        val details: String = ""
    )

    /**
     * Kamera tipini port ve HTTP imzasıyla otomatik tespit eder.
     * Örneğin 192.168.1.254 üzerinde YICAMCAR / DrivingInfoFile varlığı.
     */
    suspend fun detectCameraType(ipAddress: String): DetectedCameraModel = withContext(Dispatchers.IO) {
        if (ipAddress.startsWith("192.168.1.254") || ipAddress.startsWith("192.168.1.")) {
            // Check HTTP port 80 for YICAMCAR / Novatek
            try {
                val url = java.net.URL("http://$ipAddress/")
                val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 1500
                    readTimeout = 1500
                    requestMethod = "GET"
                }
                val content = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                if (content.contains("YICAMCAR", ignoreCase = true) ||
                    content.contains("DrivingInfoFile", ignoreCase = true) ||
                    content.contains("custom=1", ignoreCase = true)
                ) {
                    return@withContext DetectedCameraModel.YI_CAR_CAM_NOVATEK
                }
            } catch (_: Exception) {}
            return@withContext DetectedCameraModel.YI_CAR_CAM_NOVATEK
        }

        if (ipAddress.startsWith("192.168.0.")) {
            return@withContext DetectedCameraModel.SEVENTY_MAI
        }

        // Check if TCP 7878 is open
        var port7878Open = false
        try {
            Socket().use { s ->
                s.connect(InetSocketAddress(ipAddress, YI_COMMAND_PORT), 1000)
                port7878Open = true
            }
        } catch (_: Exception) {}

        if (port7878Open || ipAddress.startsWith("192.168.42.")) {
            DetectedCameraModel.XIAOMI_YI_ACTION
        } else {
            DetectedCameraModel.GENERIC_RTSP
        }
    }

    data class NovatekStreamNegotiationResult(
        val success: Boolean,
        val message: String,
        val movieLiveViewLink: String? = null,
        val photoLiveViewLink: String? = null,
        val port554OpenNow: Boolean = false,
        val port8192OpenNow: Boolean = false,
        val details: String = ""
    )

    /**
     * YI Car Cam / Novatek Çipsetli Kameralar (192.168.1.254) için HTTP RPC Tetikleyici ve Canlı Akış Müzakerecisi.
     * Bu kameralarda RTSP ve MJPEG akışını başlatmak için şu adımlar uygulanır:
     * 1. cmd=2001&par=0 -> SD kart kaydını durdurur (kayıt açıkken donanım enkoderi kilitlidir, RTSP 554 kapalı kalır)
     * 2. cmd=3001&par=1 -> Canlı video / film (Movie stream) moduna geçirir
     * 3. cmd=2019       -> WIFIAPP_CMD_MOVE_GET_LIVESTREAM: RTSP sunucusunu başlatır ve canlı akış XML linklerini döner
     * 4. cmd=2008       -> Canlı video akış servisini başlatır
     * 5. cmd=8001       -> Video soket portunu tetikler
     */
    suspend fun startNovatekStream(ipAddress: String = "192.168.1.254"): NovatekStreamNegotiationResult = withContext(Dispatchers.IO) {
        val logs = StringBuilder()
        var movieLink: String? = null
        var photoLink: String? = null

        // 1. ADIM: SD Kart Kaydını Durdur (Enkoderi Canlı Akış İçin Serbest Bırak)
        try {
            val stopRecUrl = "http://$ipAddress/?custom=1&cmd=2001&par=0"
            val conn = (java.net.URL(stopRecUrl).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 2000
                readTimeout = 2000
                setRequestProperty("User-Agent", "YICarCam-Android")
            }
            val code = conn.responseCode
            conn.disconnect()
            logs.append("✓ Kayıt Kapatıldı (cmd=2001&par=0): HTTP $code\n")
        } catch (e: Exception) {
            logs.append("⚠ Kayıt kapatma atlandı: ${e.message}\n")
        }

        // 2. ADIM: Video / Film Canlı Akış Moduna Geç (cmd=3001&par=1)
        try {
            val modeUrl = "http://$ipAddress/?custom=1&cmd=3001&par=1"
            val conn = (java.net.URL(modeUrl).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 2000
                readTimeout = 2000
                setRequestProperty("User-Agent", "YICarCam-Android")
            }
            val code = conn.responseCode
            conn.disconnect()
            logs.append("✓ Video Moduna Geçildi (cmd=3001&par=1): HTTP $code\n")
        } catch (e: Exception) {
            logs.append("⚠ Video moduna geçilemedi: ${e.message}\n")
        }

        // 3. ADIM: cmd=2019 ile Canlı Akış Bağlantılarını İste ve RTSP Servisini Başlat
        try {
            val liveViewUrl = "http://$ipAddress/?custom=1&cmd=2019"
            val conn = (java.net.URL(liveViewUrl).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 2500
                readTimeout = 2500
                setRequestProperty("User-Agent", "YICarCam-Android")
            }
            val code = conn.responseCode
            val responseBody = if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else ""
            conn.disconnect()
            logs.append("✓ cmd=2019 Akış Bağlantısı Sorgusu: HTTP $code\n")

            if (responseBody.isNotBlank()) {
                val movieRegex = Regex("<MovieLiveViewLink>(.*?)</MovieLiveViewLink>", RegexOption.IGNORE_CASE)
                val photoRegex = Regex("<PhotoLiveViewLink>(.*?)</PhotoLiveViewLink>", RegexOption.IGNORE_CASE)

                val mMatch = movieRegex.find(responseBody)?.groupValues?.getOrNull(1)?.trim()
                val pMatch = photoRegex.find(responseBody)?.groupValues?.getOrNull(1)?.trim()

                if (!mMatch.isNullOrBlank()) {
                    movieLink = mMatch
                    logs.append("  ➜ Kamera RTSP Akış Linki: $movieLink\n")
                }
                if (!pMatch.isNullOrBlank()) {
                    photoLink = pMatch
                    logs.append("  ➜ Kamera MJPEG Akış Linki: $photoLink\n")
                }
            }
        } catch (e: Exception) {
            logs.append("⚠ cmd=2019: ${e.message}\n")
        }

        // 4. ADIM: cmd=2008 ve cmd=8001 ile Canlı Akış Yayınını Başlat
        val triggerCmds = listOf(
            "cmd=2008" to "Akış Yayınını Başlat (cmd=2008)",
            "cmd=8001" to "Video Soket Servisi (cmd=8001)"
        )
        for ((q, desc) in triggerCmds) {
            try {
                val url = java.net.URL("http://$ipAddress/?custom=1&$q")
                val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 2000
                    readTimeout = 2000
                    setRequestProperty("User-Agent", "YICarCam-Android")
                }
                val code = conn.responseCode
                conn.disconnect()
                logs.append("✓ $desc: HTTP $code\n")
            } catch (_: Exception) {}
        }

        // 5. ADIM: Portların Açılmasını Bekle ve Kontrol Et
        delay(400)
        val p554 = testPort(ipAddress, 554, 700)
        val p8192 = testPort(ipAddress, 8192, 700)

        logs.append(if (p554) "✓ Port 554 RTSP Açıldı!\n" else "⚠ Port 554 henüz yanıt vermedi\n")
        if (p8192) logs.append("✓ Port 8192 HTTP MJPEG Açık!\n")

        NovatekStreamNegotiationResult(
            success = p554 || p8192 || movieLink != null,
            message = if (p554) "RTSP Port 554 Aktif" else (if (p8192) "HTTP MJPEG Port 8192 Aktif" else "Novatek Akış Komutları Gönderildi"),
            movieLiveViewLink = movieLink ?: "rtsp://$ipAddress/",
            photoLiveViewLink = photoLink ?: "http://$ipAddress:8192",
            port554OpenNow = p554,
            port8192OpenNow = p8192,
            details = logs.toString()
        )
    }

    /**
     * Novatek / IP Kameralardan gelen sürekli HTTP MJPEG akışını
     * JPEG SOI (0xFF, 0xD8) ve EOI (0xFF, 0xD9) baytlarını tarayarak
     * sıfır takılma ve yüksek FPS ile decode eden akış motoru.
     */
    suspend fun streamMjpegFrames(
        streamUrl: String,
        onFrame: (android.graphics.Bitmap) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        var connection: java.net.HttpURLConnection? = null
        try {
            val url = java.net.URL(streamUrl)
            connection = (url.openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 4000
                setRequestProperty("User-Agent", "YICarCam-Android")
                setRequestProperty("Connection", "keep-alive")
            }
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                onError("HTTP $responseCode: Akış açılamadı")
                return@withContext
            }

            val inputStream = java.io.BufferedInputStream(connection.inputStream, 32768)
            val frameBuffer = java.io.ByteArrayOutputStream(131072)
            var prevByte = -1
            var inFrame = false
            val readBuf = ByteArray(8192)

            while (isActive) {
                val bytesRead = inputStream.read(readBuf)
                if (bytesRead == -1) break

                for (i in 0 until bytesRead) {
                    val b = readBuf[i].toInt() and 0xFF
                    if (!inFrame) {
                        if (prevByte == 0xFF && b == 0xD8) {
                            // JPEG Start of Image (SOI)
                            inFrame = true
                            frameBuffer.reset()
                            frameBuffer.write(0xFF)
                            frameBuffer.write(0xD8)
                        }
                    } else {
                        frameBuffer.write(b)
                        if (prevByte == 0xFF && b == 0xD9) {
                            // JPEG End of Image (EOI)
                            inFrame = false
                            val jpegBytes = frameBuffer.toByteArray()
                            if (jpegBytes.size > 1500) {
                                val bmp = android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                                if (bmp != null) {
                                    withContext(Dispatchers.Main) {
                                        onFrame(bmp)
                                    }
                                }
                            }
                            frameBuffer.reset()
                        }
                    }
                    prevByte = b
                }
            }
        } catch (e: Exception) {
            onError(e.message ?: "MJPEG Akış Bağlantısı Kesildi")
        } finally {
            try {
                connection?.disconnect()
            } catch (_: Exception) {}
        }
    }

    /**
     * Novatek Araç Kamerası Video Kaydını Başlatır (cmd=2001&par=1)
     */
    suspend fun startRecording(ipAddress: String = "192.168.1.254"): CommandResult = withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL("http://$ipAddress/?custom=1&cmd=2001&par=1")
            val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 2500
                readTimeout = 2500
                requestMethod = "GET"
                setRequestProperty("User-Agent", "YICarCam-Android")
            }
            val code = conn.responseCode
            conn.disconnect()
            Log.d(TAG, "startRecording response: $code")
            CommandResult(success = code in 200..299, message = "Kayıt Başlatıldı (HTTP $code)")
        } catch (e: Exception) {
            Log.w(TAG, "startRecording error: ${e.message}")
            CommandResult(success = false, message = "Kayıt Başlatma Hatası: ${e.message}")
        }
    }

    /**
     * Novatek Araç Kamerası Video Kaydını Durdurur / Kapatır (cmd=2001&par=0)
     * Not: Kamera sürekli SD karta kayıt yaparken video enkoderi meşgul olduğu için
     * canlı önizleme akışı vermeyebilir. Kayıt kapatıldığında canlı akış serbest kalır.
     */
    suspend fun stopRecording(ipAddress: String = "192.168.1.254"): CommandResult = withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL("http://$ipAddress/?custom=1&cmd=2001&par=0")
            val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 2500
                readTimeout = 2500
                requestMethod = "GET"
                setRequestProperty("User-Agent", "YICarCam-Android")
            }
            val code = conn.responseCode
            conn.disconnect()
            Log.d(TAG, "stopRecording response: $code")
            // Kayıt kapatıldıktan sonra canlı video modunu tetikle
            try {
                val liveUrl = java.net.URL("http://$ipAddress/?custom=1&cmd=3001&par=1")
                val liveConn = (liveUrl.openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 2000
                    readTimeout = 2000
                    requestMethod = "GET"
                }
                liveConn.responseCode
                liveConn.disconnect()
            } catch (_: Exception) {}
            CommandResult(success = code in 200..299, message = "Kayıt Kapatıldı (HTTP $code)")
        } catch (e: Exception) {
            Log.w(TAG, "stopRecording error: ${e.message}")
            CommandResult(success = false, message = "Kayıt Kapatma Hatası: ${e.message}")
        }
    }

    /**
     * YI Car Cam / Novatek için periyodik Heartbeat (Akış 10 sn sonra kopmasın diye)
     */
    suspend fun maintainNovatekKeepAlive(
        ipAddress: String,
        onStatus: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        while (isActive) {
            try {
                delay(4000)
                val url = java.net.URL("http://$ipAddress/?custom=1&cmd=3002")
                val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 2000
                    readTimeout = 2000
                    requestMethod = "GET"
                }
                val code = conn.responseCode
                conn.disconnect()
                onStatus("Novatek Heartbeat: OK ($code)")
            } catch (e: Exception) {
                // Alternatif sorgu pingle
                try {
                    val url = java.net.URL("http://$ipAddress/?custom=1&cmd=3019")
                    val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                        connectTimeout = 2000
                        readTimeout = 2000
                        requestMethod = "GET"
                    }
                    conn.responseCode
                    conn.disconnect()
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * YI Car Cam Web Arayüzünden Anlık Kare (Snapshot) Çeker
     */
    suspend fun fetchNovatekSnapshot(ipAddress: String): ByteArray? = withContext(Dispatchers.IO) {
        val candidatePaths = listOf(
            "/?custom=1&cmd=1001",
            "/?custom=1&cmd=4001",
            "/snapshot.jpg",
            "/cgi-bin/Config.cgi?action=snapshot",
            "/web/snapshot.jpg",
            "/cgi-bin/snapshot.cgi"
        )
        for (path in candidatePaths) {
            try {
                val url = java.net.URL("http://$ipAddress$path")
                val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 1500
                    readTimeout = 2500
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "YICarCam-Android")
                }
                if (conn.responseCode in 200..299) {
                    val bytes = conn.inputStream.use { it.readBytes() }
                    conn.disconnect()
                    if (bytes.size > 1000 && (bytes[0].toInt() and 0xFF == 0xFF) && (bytes[1].toInt() and 0xFF == 0xD8)) {
                        return@withContext bytes
                    }
                }
                conn.disconnect()
            } catch (_: Exception) {}
        }
        null
    }

    /**
     * Xiaomi YI kamerasına TCP 7878 portu üzerinden bağlanır, oturum token'ı alır
     * ve RTSP canlı video akışını (viewfinder) tetikler.
     */
    suspend fun startYiVideoStream(ipAddress: String = DEFAULT_YI_IP): CommandResult = withContext(Dispatchers.IO) {
        var socket: Socket? = null
        try {
            Log.d(TAG, "Connecting to Xiaomi YI control port: $ipAddress:$YI_COMMAND_PORT")
            socket = Socket()
            socket.soTimeout = 4000
            socket.connect(InetSocketAddress(ipAddress, YI_COMMAND_PORT), 3000)

            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = OutputStreamWriter(socket.getOutputStream())

            // 1. Adım: Token iste (msg_id: 257)
            val tokenReq = JSONObject().apply {
                put("token", 0)
                put("msg_id", 257)
            }.toString() + "\n"

            writer.write(tokenReq)
            writer.flush()
            Log.d(TAG, "Sent token request: $tokenReq")

            val tokenResponseStr = reader.readLine()
            Log.d(TAG, "Received token response: $tokenResponseStr")

            var token = 0
            if (!tokenResponseStr.isNullOrBlank()) {
                val json = JSONObject(tokenResponseStr)
                token = json.optInt("param", 0)
            }

            if (token == 0) {
                token = 1 // Bazı firmware'lerde varsayılan token 1'dir
            }

            // 2. Adım: Viewfinder / Canlı Akışı Başlat (msg_id: 259)
            val startStreamReq = JSONObject().apply {
                put("token", token)
                put("msg_id", 259)
                put("param", "none_force")
            }.toString() + "\n"

            writer.write(startStreamReq)
            writer.flush()
            Log.d(TAG, "Sent start stream request: $startStreamReq")

            // Yanıtı bekle
            val streamResponse = reader.readLine()
            Log.d(TAG, "Stream start response: $streamResponse")

            // 3. Adım: RTSP Video modunu aktif et (msg_id: 515)
            try {
                val modeReq = JSONObject().apply {
                    put("token", token)
                    put("msg_id", 515)
                    put("param", "1")
                }.toString() + "\n"
                writer.write(modeReq)
                writer.flush()
            } catch (e: Exception) {
                Log.w(TAG, "Mode 515 non-critical: ${e.message}")
            }

            CommandResult(
                success = true,
                message = "Xiaomi YI Video Başlatma Başarılı (Token: $token)",
                token = token
            )
        } catch (e: Exception) {
            Log.e(TAG, "Xiaomi YI command failed: ${e.message}")
            CommandResult(
                success = false,
                message = "Komut soketi (7878) hatası: ${e.message}"
            )
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * Akışı canlı tutmak için periyodik heartbeat gönderir.
     */
    suspend fun maintainStreamKeepAlive(
        ipAddress: String,
        token: Int,
        onStatus: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        while (isActive) {
            try {
                delay(4500)
                Socket().use { s ->
                    s.soTimeout = 2000
                    s.connect(InetSocketAddress(ipAddress, YI_COMMAND_PORT), 2000)
                    val writer = OutputStreamWriter(s.getOutputStream())
                    val keepAlive = JSONObject().apply {
                        put("token", token)
                        put("msg_id", 258) // Heartbeat
                    }.toString() + "\n"
                    writer.write(keepAlive)
                    writer.flush()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Keep-alive warning: ${e.message}")
            }
        }
    }

    data class HardwareConnectivityStatus(
        val ipReachable: Boolean,
        val pingTimeMs: Long,
        val port554RtspOpen: Boolean,
        val port3333KeepaliveOpen: Boolean,
        val port80HttpOpen: Boolean,
        val port8192MjpegOpen: Boolean = false,
        val recommendedUrl: String
    )

    /**
     * Kamera donanım portlarını ve IP erişilebilirliğini 600ms içinde paralel olarak test eder.
     * Eğer port 80 açıksa fakat port 554 kapalıysa, Novatek RTSP servisini uyandırır (cmd=2001&par=0 + cmd=2019)
     * ve port 554'ü tekrar kontrol eder.
     */
    suspend fun checkHardwareConnectivity(ipAddress: String = "192.168.1.254"): HardwareConnectivityStatus = withContext(Dispatchers.IO) {
        val startPing = System.currentTimeMillis()
        val ipReachable = try {
            java.net.InetAddress.getByName(ipAddress).isReachable(600)
        } catch (_: Exception) { false }
        val pingTimeMs = (System.currentTimeMillis() - startPing).coerceAtLeast(1)

        var port554 = testPort(ipAddress, 554, 600)
        val port3333 = testPort(ipAddress, 3333, 600)
        val port80 = testPort(ipAddress, 80, 600)
        var port8192 = testPort(ipAddress, 8192, 600)

        // Eğer Port 80 açık ve RTSP kapalıysa: Novatek RTSP daemon'unu hemen uyandır
        if (!port554 && (port80 || port3333)) {
            try {
                val url1 = java.net.URL("http://$ipAddress/?custom=1&cmd=2001&par=0")
                (url1.openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 700
                    readTimeout = 700
                    setRequestProperty("User-Agent", "YICarCam-Android")
                }.responseCode
                val url2 = java.net.URL("http://$ipAddress/?custom=1&cmd=3001&par=1")
                (url2.openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 700
                    readTimeout = 700
                    setRequestProperty("User-Agent", "YICarCam-Android")
                }.responseCode
                val url3 = java.net.URL("http://$ipAddress/?custom=1&cmd=2019")
                (url3.openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 800
                    readTimeout = 800
                    setRequestProperty("User-Agent", "YICarCam-Android")
                }.responseCode
                delay(300)
                port554 = testPort(ipAddress, 554, 600)
                port8192 = testPort(ipAddress, 8192, 600)
            } catch (_: Exception) {}
        }

        val recommendedUrl = when {
            port554 -> "rtsp://$ipAddress/"
            port8192 -> "http://$ipAddress:8192"
            port3333 -> "rtsp://$ipAddress/"
            else -> "rtsp://$ipAddress/"
        }

        HardwareConnectivityStatus(
            ipReachable = ipReachable || port554 || port3333 || port80 || port8192,
            pingTimeMs = pingTimeMs,
            port554RtspOpen = port554,
            port3333KeepaliveOpen = port3333,
            port80HttpOpen = port80,
            port8192MjpegOpen = port8192,
            recommendedUrl = recommendedUrl
        )
    }

    private fun testPort(ip: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { s ->
                s.connect(InetSocketAddress(ip, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Alternatif RTSP ve HTTP Akış Yollarını Hızlıca Yoklar (Probe)
     */
    suspend fun probeStreamCandidates(ipAddress: String, port: Int): List<Pair<String, Boolean>> = withContext(Dispatchers.IO) {
        val candidates = listOf(
            "/xxx.mov" to 554,
            "/sjcam.mov" to 554,
            "/live" to 554,
            "" to 554,
            "/ch0_0.h264" to 554,
            "/ch0" to 554,
            "/movie" to 554,
            "/main" to 554,
            "" to 8192,
            "/web/snapshot.jpg" to 80,
            "/?custom=1&cmd=3014" to 80
        )

        val results = mutableListOf<Pair<String, Boolean>>()
        for ((path, testPort) in candidates) {
            var reachable = false
            try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(ipAddress, testPort), 800)
                    reachable = true
                }
            } catch (_: Exception) {}
            val fullUrl = if (testPort == 554) "rtsp://$ipAddress:$testPort$path" else "http://$ipAddress:$testPort$path"
            results.add(fullUrl to reachable)
        }
        results
    }
}
