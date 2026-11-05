package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluetooth.BondedObdDevice
import com.example.bluetooth.ConnectionState
import com.example.bluetooth.Obd2BluetoothManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.content.Context
import android.view.WindowManager
import java.util.concurrent.atomic.AtomicBoolean

enum class SpeedometerStyle(val label: String, val description: String) {
    MINIMALIST("Minimalist HUD", "Temiz, büyük dijital hız göstergesi ve hafif minimalist gösterge"),
    DIGITAL_SPORT("Dijital Spor", "F1 vites ikaz ışıkları ve 7-segment dijital kadran"),
    ANALOG_CLASSIC("Klasik Kadran", "Geleneksel ibreli devir ve dijital hız göstergesi"),
    RALLY_TELEMETRY("Ralli Telemetri", "G-Kuvveti ve anlık yük grafikli performans paneli")
}

class ObdDashboardViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "ObdDashboardViewModel"
    }

    private val bluetoothManager = Obd2BluetoothManager(application)

    // Dashboard UI states
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    val connectedDeviceAddress: StateFlow<String?> = bluetoothManager.connectedDeviceAddress

    private val _rpm = MutableStateFlow(0f)
    val rpm: StateFlow<Float> = _rpm.asStateFlow()

    private val _speed = MutableStateFlow(0)
    val speed: StateFlow<Int> = _speed.asStateFlow()

    // Expanded Diagnostics Telemetry States
    private val _coolantTemp = MutableStateFlow(0f)
    val coolantTemp: StateFlow<Float> = _coolantTemp.asStateFlow()

    private val _throttle = MutableStateFlow(0f)
    val throttle: StateFlow<Float> = _throttle.asStateFlow()

    private val _engineLoad = MutableStateFlow(0f)
    val engineLoad: StateFlow<Float> = _engineLoad.asStateFlow()

    private val _intakeTemp = MutableStateFlow(0)
    val intakeTemp: StateFlow<Int> = _intakeTemp.asStateFlow()

    private val _manifoldPressure = MutableStateFlow(0)
    val manifoldPressure: StateFlow<Int> = _manifoldPressure.asStateFlow()

    private val _fuelLevel = MutableStateFlow(0f)
    val fuelLevel: StateFlow<Float> = _fuelLevel.asStateFlow()

    private val _batteryVoltage = MutableStateFlow(14.1f)
    val batteryVoltage: StateFlow<Float> = _batteryVoltage.asStateFlow()

    private val _timingAdvance = MutableStateFlow(10.5f)
    val timingAdvance: StateFlow<Float> = _timingAdvance.asStateFlow()

    private val _stft = MutableStateFlow(1.2f)
    val stft: StateFlow<Float> = _stft.asStateFlow()

    private val _ltft = MutableStateFlow(-1.8f)
    val ltft: StateFlow<Float> = _ltft.asStateFlow()

    private val _o2Voltage = MutableStateFlow(0.68f)
    val o2Voltage: StateFlow<Float> = _o2Voltage.asStateFlow()

    private val _o2VoltageSensor2 = MutableStateFlow(0.58f)
    val o2VoltageSensor2: StateFlow<Float> = _o2VoltageSensor2.asStateFlow()

    private val _pedalPosition = MutableStateFlow(0f)
    val pedalPosition: StateFlow<Float> = _pedalPosition.asStateFlow()

    private val _baroPressure = MutableStateFlow(101)
    val baroPressure: StateFlow<Int> = _baroPressure.asStateFlow()

    private val _fuelSystemStatus = MutableStateFlow("Kapalı Döngü (Closed Loop)")
    val fuelSystemStatus: StateFlow<String> = _fuelSystemStatus.asStateFlow()

    private val _engineRunTime = MutableStateFlow(420)
    val engineRunTime: StateFlow<Int> = _engineRunTime.asStateFlow()

    private val _dtcCodes = MutableStateFlow<List<String>>(emptyList())
    val dtcCodes: StateFlow<List<String>> = _dtcCodes.asStateFlow()

    private val _ecuProtocol = MutableStateFlow("Bağlantı Yok / Bilinmiyor")
    val ecuProtocol: StateFlow<String> = _ecuProtocol

    private val _ecuVin = MutableStateFlow("Bağlantı Yok / Bilinmiyor")
    val ecuVin: StateFlow<String> = _ecuVin

    private val _ecuMilStatus = MutableStateFlow(false)
    val ecuMilStatus: StateFlow<Boolean> = _ecuMilStatus

    // DTC scanning progress animation states
    private val _isDtcScanning = MutableStateFlow(false)
    val isDtcScanning: StateFlow<Boolean> = _isDtcScanning.asStateFlow()

    private val _dtcScanProgress = MutableStateFlow(0f)
    val dtcScanProgress: StateFlow<Float> = _dtcScanProgress.asStateFlow()

    // History data buffers for drawing real-time scrolling charts (Graph component)
    private val _rpmHistory = MutableStateFlow<List<Float>>(List(50) { 0f })
    val rpmHistory: StateFlow<List<Float>> = _rpmHistory.asStateFlow()

    private val _loadHistory = MutableStateFlow<List<Float>>(List(50) { 0f })
    val loadHistory: StateFlow<List<Float>> = _loadHistory.asStateFlow()

    private val _boostHistory = MutableStateFlow<List<Float>>(List(50) { 0f })
    val boostHistory: StateFlow<List<Float>> = _boostHistory.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<BondedObdDevice>>(emptyList())
    val pairedDevices: StateFlow<List<BondedObdDevice>> = _pairedDevices.asStateFlow()

    // Selected vehicle parameters
    private val _activeProfile = MutableStateFlow(VehicleProfile.RENAULT_SYMBOL_D4F)
    val activeProfile: StateFlow<VehicleProfile> = _activeProfile.asStateFlow()

    // Interactive pedal and manual load States
    private val _isPedalPressed = MutableStateFlow(false)
    val isPedalPressed: StateFlow<Boolean> = _isPedalPressed.asStateFlow()

    private val _isManualSliderActive = MutableStateFlow(false)
    val isManualSliderActive: StateFlow<Boolean> = _isManualSliderActive.asStateFlow()

    private val _manualRpmSliderValue = MutableStateFlow(800f)
    val manualRpmSliderValue: StateFlow<Float> = _manualRpmSliderValue.asStateFlow()

    // Diagnostics / Terminal terminal logs state
    private val _terminalConsoleLogs = MutableStateFlow<List<String>>(emptyList())
    val terminalConsoleLogs: StateFlow<List<String>> = _terminalConsoleLogs.asStateFlow()

    // ===================================
    // 1. GPS & MAP TELEMETRY STATES
    // ===================================
    private val _gpsLatitude = MutableStateFlow(39.9334)
    val gpsLatitude: StateFlow<Double> = _gpsLatitude.asStateFlow()

    private val _gpsLongitude = MutableStateFlow(32.8597)
    val gpsLongitude: StateFlow<Double> = _gpsLongitude.asStateFlow()

    private val _gpsAltitude = MutableStateFlow(938.0)
    val gpsAltitude: StateFlow<Double> = _gpsAltitude.asStateFlow()

    private val _gpsSpeed = MutableStateFlow(0f)
    val gpsSpeed: StateFlow<Float> = _gpsSpeed.asStateFlow()

    private val _gpsBearing = MutableStateFlow(0f)
    val gpsBearing: StateFlow<Float> = _gpsBearing.asStateFlow()

    private val _gpsIsActive = MutableStateFlow(false)
    val gpsIsActive: StateFlow<Boolean> = _gpsIsActive.asStateFlow()

    private val _gpsRoutePoints = MutableStateFlow<List<Pair<Double, Double>>>(listOf(39.9334 to 32.8597))
    val gpsRoutePoints: StateFlow<List<Pair<Double, Double>>> = _gpsRoutePoints.asStateFlow()

    private val _isRouteSimulationActive = MutableStateFlow(true)
    val isRouteSimulationActive: StateFlow<Boolean> = _isRouteSimulationActive.asStateFlow()

    // ===================================
    // 2. 0-100 & 0-60 ACCEL TIMER STATES
    // ===================================
    private val _isTimerRunning = MutableStateFlow(false)
    val isTimerRunning: StateFlow<Boolean> = _isTimerRunning.asStateFlow()

    private val _timerMillis = MutableStateFlow(0L)
    val timerMillis: StateFlow<Long> = _timerMillis.asStateFlow()

    private val _sprint60Time = MutableStateFlow(0f)
    val sprint60Time: StateFlow<Float> = _sprint60Time.asStateFlow()

    private val _sprint100Time = MutableStateFlow(0f)
    val sprint100Time: StateFlow<Float> = _sprint100Time.asStateFlow()

    private val _best100Time = MutableStateFlow<Float?>(null)
    val best100Time: StateFlow<Float?> = _best100Time.asStateFlow()

    private val _timerStatusText = MutableStateFlow("HAZIR (Sürüş yapın veya gaza basın)")
    val timerStatusText: StateFlow<String> = _timerStatusText.asStateFlow()

    private val _pastRuns = MutableStateFlow<List<String>>(emptyList())
    val pastRuns: StateFlow<List<String>> = _pastRuns.asStateFlow()

    private var timerStartTime = 0L
    private var timerJob: kotlinx.coroutines.Job? = null
    private var is60Logged = false
    private var is100Logged = false

    // ===================================
    // 4. G-FORCE & INCLINOMETER STATES
    // ===================================
    private val _gForceX = MutableStateFlow(0f) // Lateral force
    val gForceX: StateFlow<Float> = _gForceX.asStateFlow()

    private val _gForceY = MutableStateFlow(0f) // Longitudinal force
    val gForceY: StateFlow<Float> = _gForceY.asStateFlow()

    private val _pitch = MutableStateFlow(0f) // tilting forward/back
    val pitch: StateFlow<Float> = _pitch.asStateFlow()

    private val _roll = MutableStateFlow(0f) // tilting left/right
    val roll: StateFlow<Float> = _roll.asStateFlow()

    private val _deviceRotation = MutableStateFlow(Surface.ROTATION_0)
    val deviceRotation: StateFlow<Int> = _deviceRotation.asStateFlow()

    fun setDeviceRotation(rotation: Int) {
        _deviceRotation.value = rotation
    }

    private val initialized = AtomicBoolean(false)

    // Gemini Autopilot Driver States
    private val _isGeminiActive = MutableStateFlow(false)
    val isGeminiActive: StateFlow<Boolean> = _isGeminiActive.asStateFlow()

    private val _geminiApiKey = MutableStateFlow("")
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    private val _geminiApiBase = MutableStateFlow("https://generativelanguage.googleapis.com")
    val geminiApiBase: StateFlow<String> = _geminiApiBase.asStateFlow()

    private val _geminiModelName = MutableStateFlow("gemini-1.5-flash")
    val geminiModelName: StateFlow<String> = _geminiModelName.asStateFlow()

    private val _geminiPersonality = MutableStateFlow("sport") // "sport", "commuter", "peaceful"
    val geminiPersonality: StateFlow<String> = _geminiPersonality.asStateFlow()

    private val _geminiLog = MutableStateFlow("Yapay Zeka Sürüş Asistanı Devre Dışı.")
    val geminiLog: StateFlow<String> = _geminiLog.asStateFlow()

    private val _geminiInterval = MutableStateFlow(45) // 45 seconds default to respect 429 rate limit
    val geminiInterval: StateFlow<Int> = _geminiInterval.asStateFlow()

    // Gemini Advanced AI features States
    private val _aiDtcReport = MutableStateFlow("")
    val aiDtcReport: StateFlow<String> = _aiDtcReport.asStateFlow()

    private val _isAiDtcLoading = MutableStateFlow(false)
    val isAiDtcLoading: StateFlow<Boolean> = _isAiDtcLoading.asStateFlow()

    private val _aiCoachReport = MutableStateFlow("")
    val aiCoachReport: StateFlow<String> = _aiCoachReport.asStateFlow()

    private val _isAiCoachLoading = MutableStateFlow(false)
    val isAiCoachLoading: StateFlow<Boolean> = _isAiCoachLoading.asStateFlow()

    // Speedometer / Cockpit Gauge Style State (Default: MINIMALIST)
    private val _speedometerStyle = MutableStateFlow(SpeedometerStyle.MINIMALIST)
    val speedometerStyle: StateFlow<SpeedometerStyle> = _speedometerStyle.asStateFlow()

    fun setSpeedometerStyle(style: SpeedometerStyle) {
        _speedometerStyle.value = style
    }

    // Camera and HUD Overlay Settings (persisted across screens)
    private val _cameraSourceMode = MutableStateFlow(CameraSourceMode.XIAOMI_WIFI)
    val cameraSourceMode: StateFlow<CameraSourceMode> = _cameraSourceMode.asStateFlow()

    fun setCameraSourceMode(mode: CameraSourceMode) {
        _cameraSourceMode.value = mode
    }

    private val _hudPosition = MutableStateFlow(HudLayoutPosition.BOTTOM_WIDE)
    val hudPosition: StateFlow<HudLayoutPosition> = _hudPosition.asStateFlow()

    fun setHudPosition(position: HudLayoutPosition) {
        _hudPosition.value = position
    }

    private val _hudContrastMode = MutableStateFlow(HudContrastMode.NIGHT_RAIN)
    val hudContrastMode: StateFlow<HudContrastMode> = _hudContrastMode.asStateFlow()

    fun setHudContrastMode(mode: HudContrastMode) {
        _hudContrastMode.value = mode
    }

    // --- Recording & Sync States ---
    // Araç kamerası sürekli kayıtta olduğu için varsayılan durum aktif (true)
    private val _isRecording = MutableStateFlow(true)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingSecs = MutableStateFlow(0)
    val recordingSecs: StateFlow<Int> = _recordingSecs.asStateFlow()

    private val _syncLatencyMs = MutableStateFlow(12)
    val syncLatencyMs: StateFlow<Int> = _syncLatencyMs.asStateFlow()

    // --- Sürüş Seyahat Geçmişi & CSV Dışa Aktarma ---
    data class TripLogEntry(
        val id: String,
        val timestamp: Long,
        val formattedDate: String,
        val durationMins: Int,
        val distanceKm: Float,
        val topSpeedKmh: Int,
        val avgSpeedKmh: Int,
        val topRpm: Int,
        val peakGForce: Float
    )

    private val _tripLogs = MutableStateFlow<List<TripLogEntry>>(
        listOf(
            TripLogEntry("TRIP-01", System.currentTimeMillis() - 86400000L, "Dün 18:30", 24, 18.4f, 124, 46, 4850, 0.85f),
            TripLogEntry("TRIP-02", System.currentTimeMillis() - 172800000L, "2 gün önce", 42, 35.8f, 142, 58, 5400, 1.12f),
            TripLogEntry("TRIP-03", System.currentTimeMillis() - 259200000L, "3 gün önce", 15, 9.2f, 98, 38, 3900, 0.68f)
        )
    )
    val tripLogs: StateFlow<List<TripLogEntry>> = _tripLogs.asStateFlow()

    fun exportTripLogsToCsv(): String {
        val sb = StringBuilder()
        sb.append("ID,Tarih,Sure_Dk,Mesafe_Km,Maks_Hiz_kmh,Ort_Hiz_kmh,Maks_RPM,Pik_G\n")
        _tripLogs.value.forEach { t ->
            sb.append("${t.id},\"${t.formattedDate}\",${t.durationMins},${t.distanceKm},${t.topSpeedKmh},${t.avgSpeedKmh},${t.topRpm},${t.peakGForce}\n")
        }
        return sb.toString()
    }

    fun toggleRecording() {
        val newState = !_isRecording.value
        _isRecording.value = newState
        viewModelScope.launch(Dispatchers.IO) {
            val ip = _xiaomiIpAddress.value
            if (newState) {
                XiaomiCameraCommander.startRecording(ip)
            } else {
                XiaomiCameraCommander.stopRecording(ip)
            }
        }
    }

    fun setRecordingSecs(secs: Int) {
        _recordingSecs.value = secs
    }

    fun updateSyncLatency(latency: Int) {
        _syncLatencyMs.value = latency
    }

    // Xiaomi YI Smart Dash Cam Specific States (Default: 192.168.1.254 / RTSP)
    private val _xiaomiModelName = MutableStateFlow("YI Car Dash Cam (192.168.1.254 RTSP)")
    val xiaomiModelName: StateFlow<String> = _xiaomiModelName.asStateFlow()

    private val _xiaomiSerialNumber = MutableStateFlow("CABAAAUSC01815CA4A1C")
    val xiaomiSerialNumber: StateFlow<String> = _xiaomiSerialNumber.asStateFlow()

    private val _xiaomiFirmwareVersion = MutableStateFlow("V-1.03.026-US")
    val xiaomiFirmwareVersion: StateFlow<String> = _xiaomiFirmwareVersion.asStateFlow()

    private val _xiaomiSsid = MutableStateFlow("YI-Symbol")
    val xiaomiSsid: StateFlow<String> = _xiaomiSsid.asStateFlow()

    private val _xiaomiPassword = MutableStateFlow("1234567890")
    val xiaomiPassword: StateFlow<String> = _xiaomiPassword.asStateFlow()

    private val _xiaomiIpAddress = MutableStateFlow("192.168.1.254")
    val xiaomiIpAddress: StateFlow<String> = _xiaomiIpAddress.asStateFlow()

    private val _xiaomiPort = MutableStateFlow("554")
    val xiaomiPort: StateFlow<String> = _xiaomiPort.asStateFlow()

    private val _xiaomiStreamProtocol = MutableStateFlow(WifiStreamProtocol.RTSP)
    val xiaomiStreamProtocol: StateFlow<WifiStreamProtocol> = _xiaomiStreamProtocol.asStateFlow()

    private val _xiaomiStreamPath = MutableStateFlow("/xxx.mov")
    val xiaomiStreamPath: StateFlow<String> = _xiaomiStreamPath.asStateFlow()

    private val _xiaomiCustomUrl = MutableStateFlow("")
    val xiaomiCustomUrl: StateFlow<String> = _xiaomiCustomUrl.asStateFlow()

    private val _xiaomiUseSimulationFallback = MutableStateFlow(true)
    val xiaomiUseSimulationFallback: StateFlow<Boolean> = _xiaomiUseSimulationFallback.asStateFlow()

    private val _isXiaomiConnected = MutableStateFlow(true)
    val isXiaomiConnected: StateFlow<Boolean> = _isXiaomiConnected.asStateFlow()

    private val _isXiaomiConnecting = MutableStateFlow(false)
    val isXiaomiConnecting: StateFlow<Boolean> = _isXiaomiConnecting.asStateFlow()

    private val _xiaomiSignalDbm = MutableStateFlow(-52)
    val xiaomiSignalDbm: StateFlow<Int> = _xiaomiSignalDbm.asStateFlow()

    private val _xiaomiBatteryVolt = MutableStateFlow("13.8V")
    val xiaomiBatteryVolt: StateFlow<String> = _xiaomiBatteryVolt.asStateFlow()

    private val _xiaomiSdCardFree = MutableStateFlow("128GB (%84 Boş)")
    val xiaomiSdCardFree: StateFlow<String> = _xiaomiSdCardFree.asStateFlow()

    private val _xiaomiEmergencyLocked = MutableStateFlow(false)
    val xiaomiEmergencyLocked: StateFlow<Boolean> = _xiaomiEmergencyLocked.asStateFlow()

    fun updateXiaomiConfig(
        modelName: String = _xiaomiModelName.value,
        serialNumber: String = _xiaomiSerialNumber.value,
        firmwareVersion: String = _xiaomiFirmwareVersion.value,
        ssid: String = _xiaomiSsid.value,
        password: String = _xiaomiPassword.value,
        ipAddress: String = _xiaomiIpAddress.value,
        port: String = _xiaomiPort.value,
        streamPath: String = _xiaomiStreamPath.value,
        protocol: WifiStreamProtocol = _xiaomiStreamProtocol.value,
        customUrl: String = _xiaomiCustomUrl.value
    ) {
        _xiaomiModelName.value = modelName
        _xiaomiSerialNumber.value = serialNumber
        _xiaomiFirmwareVersion.value = firmwareVersion
        _xiaomiSsid.value = ssid
        _xiaomiPassword.value = password
        _xiaomiIpAddress.value = ipAddress
        _xiaomiPort.value = port
        _xiaomiStreamPath.value = streamPath
        _xiaomiStreamProtocol.value = protocol
        _xiaomiCustomUrl.value = customUrl
        persistXiaomiConfig()
    }

    fun setXiaomiStreamProtocol(protocol: WifiStreamProtocol) {
        _xiaomiStreamProtocol.value = protocol
        if (_xiaomiCustomUrl.value.isBlank()) {
            _xiaomiPort.value = protocol.defaultPort
            _xiaomiStreamPath.value = protocol.defaultPath
        }
        persistXiaomiConfig()
    }

    fun setXiaomiCustomUrl(url: String) {
        _xiaomiCustomUrl.value = url
        persistXiaomiConfig()
    }

    fun setXiaomiStreamPath(path: String) {
        _xiaomiStreamPath.value = path
        persistXiaomiConfig()
    }

    fun setXiaomiUseSimulationFallback(fallback: Boolean) {
        _xiaomiUseSimulationFallback.value = fallback
        persistXiaomiConfig()
    }

    private fun persistXiaomiConfig() {
        try {
            val prefs = getApplication<Application>().getSharedPreferences("obd_prefs", Application.MODE_PRIVATE)
            prefs.edit()
                .putString("xiaomi_ssid", _xiaomiSsid.value)
                .putString("xiaomi_password", _xiaomiPassword.value)
                .putString("xiaomi_ip", _xiaomiIpAddress.value)
                .putString("xiaomi_port", _xiaomiPort.value)
                .putString("xiaomi_path", _xiaomiStreamPath.value)
                .putString("xiaomi_protocol", _xiaomiStreamProtocol.value.name)
                .putString("xiaomi_custom_url", _xiaomiCustomUrl.value)
                .putBoolean("xiaomi_sim_fallback", _xiaomiUseSimulationFallback.value)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist camera config: ${e.message}")
        }
    }

    fun toggleXiaomiConnection() {
        if (_isXiaomiConnected.value) {
            _isXiaomiConnected.value = false
        } else {
            _isXiaomiConnecting.value = true
            viewModelScope.launch {
                delay(1200)
                _isXiaomiConnecting.value = false
                _isXiaomiConnected.value = true
            }
        }
    }

    fun toggleXiaomiEmergencyLock() {
        _xiaomiEmergencyLocked.value = !_xiaomiEmergencyLocked.value
    }

    fun resetXiaomiDefaults() {
        _xiaomiIpAddress.value = "192.168.1.254"
        _xiaomiPort.value = "554"
        _xiaomiStreamPath.value = "/xxx.mov"
        _xiaomiStreamProtocol.value = WifiStreamProtocol.RTSP
        _xiaomiCustomUrl.value = ""
        persistXiaomiConfig()
    }

    init {
        startDashboard()
    }

    private fun startDashboard() {
        if (initialized.getAndSet(true)) return

        // Load persisted settings
        try {
            val prefs = getApplication<Application>().getSharedPreferences("obd_prefs", Application.MODE_PRIVATE)
            _geminiApiKey.value = prefs.getString("gemini_api_key", "") ?: ""
            _geminiApiBase.value = prefs.getString("gemini_api_base", "https://generativelanguage.googleapis.com") ?: "https://generativelanguage.googleapis.com"
            _geminiModelName.value = prefs.getString("gemini_model_name", "gemini-1.5-flash") ?: "gemini-1.5-flash"
            // Reset if persisted with invalid default
            if (_geminiModelName.value == "gemini-3.5-flash") {
                _geminiModelName.value = "gemini-1.5-flash"
            }
            _geminiInterval.value = prefs.getInt("gemini_interval", 45)

            // Load Wi-Fi Camera settings (Default: 192.168.1.254:554 /xxx.mov RTSP)
            _xiaomiSsid.value = prefs.getString("xiaomi_ssid", "YI-Symbol") ?: "YI-Symbol"
            _xiaomiPassword.value = prefs.getString("xiaomi_password", "1234567890") ?: "1234567890"
            _xiaomiIpAddress.value = prefs.getString("xiaomi_ip", "192.168.1.254") ?: "192.168.1.254"
            val savedPort = prefs.getString("xiaomi_port", "554") ?: "554"
            _xiaomiPort.value = if (savedPort == "3333" || savedPort.isBlank()) "554" else savedPort
            val savedPath = prefs.getString("xiaomi_path", "/xxx.mov") ?: "/xxx.mov"
            _xiaomiStreamPath.value = if (savedPath.isBlank()) "/xxx.mov" else savedPath
            val protName = prefs.getString("xiaomi_protocol", null)
            if (protName != null) {
                try {
                    _xiaomiStreamProtocol.value = WifiStreamProtocol.valueOf(protName)
                } catch (_: Exception) {
                    _xiaomiStreamProtocol.value = WifiStreamProtocol.HTTP_SNAPSHOT
                }
            } else {
                _xiaomiStreamProtocol.value = WifiStreamProtocol.HTTP_SNAPSHOT
            }
            _xiaomiCustomUrl.value = prefs.getString("xiaomi_custom_url", "") ?: ""
            _xiaomiUseSimulationFallback.value = prefs.getBoolean("xiaomi_sim_fallback", true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load credentials: ${e.message}")
        }

        // Araç kamerası sürekli kayıt zamanlayıcısı (Varsayılan olarak aktif)
        viewModelScope.launch {
            while (isActive) {
                delay(1000)
                if (_isRecording.value) {
                    _recordingSecs.value += 1
                }
            }
        }

        // Sync with Bluetooth and parsing events
        viewModelScope.launch {
            bluetoothManager.connectionState.collect { state ->
                _connectionState.value = state
                if (state == ConnectionState.Connected) {
                    val address = bluetoothManager.connectedDeviceAddress.value
                    if (!address.isNullOrBlank() && address != Obd2BluetoothManager.MOCK_DEVICE_ADDRESS) {
                        try {
                            val prefs = getApplication<Application>().getSharedPreferences("obd_prefs", Application.MODE_PRIVATE)
                            prefs.edit().putString("last_connected_device_address", address).apply()
                            Log.d(TAG, "Successfully recorded last connected device: $address")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to persist last connected device: ${e.message}")
                        }
                    }
                }
                if (state == ConnectionState.Disconnected) {
                    _rpm.value = 0f
                    _speed.value = 0
                }
            }
        }

        // Automatic connection to the last connected device
        viewModelScope.launch {
            delay(1500) // Allow Bluetooth initialization and scan refresh to complete
            try {
                val prefs = getApplication<Application>().getSharedPreferences("obd_prefs", Application.MODE_PRIVATE)
                val lastAddress = prefs.getString("last_connected_device_address", null)
                if (!lastAddress.isNullOrBlank() && _connectionState.value == ConnectionState.Disconnected) {
                    _terminalConsoleLogs.value = listOf("🔄 Son bağlanan cihaza otomatik bağlanılıyor: $lastAddress")
                    connectToObd(lastAddress)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Auto connect attempt failed: ${e.message}")
            }
        }

        viewModelScope.launch {
            bluetoothManager.rpmFlow.collect { rawRpm ->
                _rpm.value = rawRpm

                // Update historical buffer
                val history = _rpmHistory.value.toMutableList()
                if (history.size >= 50) history.removeAt(0)
                history.add(rawRpm)
                _rpmHistory.value = history
            }
        }

        viewModelScope.launch {
            bluetoothManager.speedFlow.collect { calculatedSpeed ->
                _speed.value = calculatedSpeed
                checkPerformanceTimer(calculatedSpeed)
            }
        }

        // Collect new diagnostics streams
        viewModelScope.launch {
            bluetoothManager.coolantTempFlow.collect { value ->
                _coolantTemp.value = value
            }
        }

        viewModelScope.launch {
            bluetoothManager.throttleFlow.collect { value ->
                _throttle.value = value
            }
        }

        viewModelScope.launch {
            bluetoothManager.engineLoadFlow.collect { value ->
                _engineLoad.value = value

                // Update historical load buffer
                val history = _loadHistory.value.toMutableList()
                if (history.size >= 50) history.removeAt(0)
                history.add(value)
                _loadHistory.value = history
            }
        }

        viewModelScope.launch {
            bluetoothManager.intakeTempFlow.collect { value ->
                _intakeTemp.value = value
            }
        }

        viewModelScope.launch {
            bluetoothManager.manifoldPressureFlow.collect { value ->
                _manifoldPressure.value = value

                // Update historical boost/pressure buffer
                val history = _boostHistory.value.toMutableList()
                if (history.size >= 50) history.removeAt(0)
                history.add(value.toFloat())
                _boostHistory.value = history
            }
        }

        viewModelScope.launch {
            bluetoothManager.fuelLevelFlow.collect { value ->
                _fuelLevel.value = value
            }
        }

        viewModelScope.launch {
            bluetoothManager.batteryVoltageFlow.collect { value ->
                _batteryVoltage.value = value
            }
        }

        viewModelScope.launch {
            bluetoothManager.timingAdvanceFlow.collect { value ->
                _timingAdvance.value = value
            }
        }

        viewModelScope.launch {
            bluetoothManager.stftFlow.collect { value ->
                _stft.value = value
            }
        }

        viewModelScope.launch {
            bluetoothManager.ltftFlow.collect { value ->
                _ltft.value = value
            }
        }

        viewModelScope.launch {
            bluetoothManager.o2VoltageFlow.collect { value ->
                _o2Voltage.value = value
            }
        }

        viewModelScope.launch {
            bluetoothManager.o2VoltageSensor2Flow.collect { value ->
                _o2VoltageSensor2.value = value
            }
        }

        viewModelScope.launch {
            bluetoothManager.pedalPositionFlow.collect { value ->
                _pedalPosition.value = value
            }
        }

        viewModelScope.launch {
            bluetoothManager.baroPressureFlow.collect { value ->
                _baroPressure.value = value
            }
        }

        viewModelScope.launch {
            bluetoothManager.fuelSystemStatusFlow.collect { value ->
                _fuelSystemStatus.value = value
            }
        }

        viewModelScope.launch {
            bluetoothManager.engineRunTimeFlow.collect { value ->
                _engineRunTime.value = value
            }
        }

        viewModelScope.launch {
            bluetoothManager.dtcCodesFlow.collect { list ->
                _dtcCodes.value = list
            }
        }

        viewModelScope.launch {
            bluetoothManager.ecuProtocol.collect { value ->
                _ecuProtocol.value = value
            }
        }

        viewModelScope.launch {
            bluetoothManager.ecuVin.collect { value ->
                _ecuVin.value = value
            }
        }

        viewModelScope.launch {
            bluetoothManager.ecuMilStatus.collect { value ->
                _ecuMilStatus.value = value
            }
        }

        viewModelScope.launch {
            bluetoothManager.terminalLogs.collect { logLine ->
                val currentLogs = _terminalConsoleLogs.value.toMutableList()
                if (currentLogs.size > 80) {
                    currentLogs.removeAt(0)
                }
                currentLogs.add(logLine)
                _terminalConsoleLogs.value = currentLogs
            }
        }

        // Setup bonded (paired) list refresh
        refreshPairedDevices()
        setupGpsAndSensors()
    }

    fun refreshPairedDevices() {
        _pairedDevices.value = bluetoothManager.getPairedDevices()
    }

    fun scanFaultCodes() {
        if (_isDtcScanning.value) return
        _isDtcScanning.value = true
        _dtcScanProgress.value = 0f
        
        viewModelScope.launch {
            bluetoothManager.triggerDtcScan()
            for (i in 1..100) {
                delay(20)
                _dtcScanProgress.value = i / 100f
            }
            _isDtcScanning.value = false
        }
    }

    fun clearFaultCodes() {
        bluetoothManager.clearDtcCodes()
    }

    fun hasBluetoothPermissions(): Boolean {
        return bluetoothManager.hasPermissions()
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothManager.isBluetoothEnabled()
    }

    fun connectToObd(address: String) {
        viewModelScope.launch {
            _terminalConsoleLogs.value = listOf("🔄 Belirtilen adrese bağlanılıyor: $address")
            if (address != Obd2BluetoothManager.MOCK_DEVICE_ADDRESS) {
                _dtcCodes.value = emptyList()
                _ecuMilStatus.value = false
                _ecuProtocol.value = "Algılanıyor..."
                _ecuVin.value = "Okunuyor..."
            }
            bluetoothManager.connect(address)
        }
    }

    fun disconnectObd() {
        bluetoothManager.disconnect()
    }

    fun changeVehicleProfile(profile: VehicleProfile) {
        _activeProfile.value = profile
        
        // Reset slider ranges if needed
        val currentMax = profile.maxRpm
        if (_manualRpmSliderValue.value > currentMax) {
            _manualRpmSliderValue.value = currentMax
        }
    }

    fun setManualSliderActive(active: Boolean) {
        _isManualSliderActive.value = active
        if (active) {
            _isPedalPressed.value = false
        }
    }

    fun updateManualRpm(rpmVal: Float) {
        if (!_isManualSliderActive.value) return
        _manualRpmSliderValue.value = rpmVal
        
        // In simulate mode, let the manager know if connected to Virtual Simulator
        bluetoothManager.setMockInput(
            pedalPressed = false,
            targetRpm = rpmVal
        )
        _rpm.value = rpmVal
    }

    // Touch down Gas Pedal simulation
    fun pressGasPedal() {
        if (_connectionState.value !is ConnectionState.Connected) {
            // Auto connect to physical simulation if not in OBD connections!
            connectToObd(Obd2BluetoothManager.MOCK_DEVICE_ADDRESS)
        }
        _isPedalPressed.value = true
        _isManualSliderActive.value = false
        
        // Peak target RPM when floor gas pedal
        val targetRpm = _activeProfile.value.maxRpm - 300f
        bluetoothManager.setMockInput(
            pedalPressed = true,
            targetRpm = targetRpm
        )
    }

    // Touch up Gas Pedal simulation
    fun releaseGasPedal() {
        _isPedalPressed.value = false
        
        // Pull down to idle RPM
        bluetoothManager.setMockInput(
            pedalPressed = false,
            targetRpm = 800f
        )
    }

    // --- Gemini AI Driving Controller System ---
    private var geminiJob: kotlinx.coroutines.Job? = null

    fun setGeminiApiKey(key: String) {
        _geminiApiKey.value = key
        try {
            val prefs = getApplication<Application>().getSharedPreferences("obd_prefs", Application.MODE_PRIVATE)
            prefs.edit().putString("gemini_api_key", key).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist api key: ${e.message}")
        }
    }

    fun setGeminiApiBase(base: String) {
        _geminiApiBase.value = base
        try {
            val prefs = getApplication<Application>().getSharedPreferences("obd_prefs", Application.MODE_PRIVATE)
            prefs.edit().putString("gemini_api_base", base).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist api base: ${e.message}")
        }
    }

    fun setGeminiModelName(modelName: String) {
        _geminiModelName.value = modelName
        try {
            val prefs = getApplication<Application>().getSharedPreferences("obd_prefs", Application.MODE_PRIVATE)
            prefs.edit().putString("gemini_model_name", modelName).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist model name: ${e.message}")
        }
    }

    fun setGeminiInterval(seconds: Int) {
        val coerced = seconds.coerceIn(10, 120)
        _geminiInterval.value = coerced
        try {
            val prefs = getApplication<Application>().getSharedPreferences("obd_prefs", Application.MODE_PRIVATE)
            prefs.edit().putInt("gemini_interval", coerced).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist interval: ${e.message}")
        }
    }

    private fun extractErrorMessage(responseStr: String?, fallback: String): String {
        if (responseStr.isNullOrBlank()) return fallback
        try {
            val json = JSONObject(responseStr)
            
            // Case 1: Standard OpenAI/Gemini wrapper: {"error": {"message": "..."} }
            if (json.has("error")) {
                val errorVal = json.get("error")
                if (errorVal is JSONObject) {
                    val msg = errorVal.optString("message")
                    if (msg.isNotEmpty()) return msg
                    val status = errorVal.optString("status")
                    if (status.isNotEmpty()) return status
                    val code = errorVal.optString("code")
                    if (code.isNotEmpty()) return "Error: $code"
                    return errorVal.toString()
                } else if (errorVal is String) {
                    return errorVal
                }
            }
            
            // Case 2: Root message: {"message": "..."}
            val rootMsg = json.optString("message")
            if (rootMsg.isNotEmpty()) return rootMsg
            
            // Case 3: Root error_description: {"error_description": "..."}
            val errDesc = json.optString("error_description")
            if (errDesc.isNotEmpty()) return errDesc

            // Case 4: General error detail list
            val errorsArray = json.optJSONArray("errors")
            if (errorsArray != null && errorsArray.length() > 0) {
                val firstErr = errorsArray.optJSONObject(0)
                if (firstErr != null) {
                    val m = firstErr.optString("message")
                    if (m.isNotEmpty()) return m
                }
            }

            return responseStr.take(150)
        } catch (e: Exception) {
            try {
                // If it is a JSON array instead of object
                val array = JSONArray(responseStr)
                if (array.length() > 0) {
                    val first = array.optJSONObject(0)
                    if (first != null) {
                        return extractErrorMessage(first.toString(), fallback)
                    }
                }
            } catch (e2: Exception) {
                // Ignore
            }
        }
        return responseStr.take(150)
    }

    private fun executeAiRequest(
        promptText: String,
        isJsonResponse: Boolean = false,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val rawKey = _geminiApiKey.value.trim().ifEmpty {
            try {
                com.example.BuildConfig.GEMINI_API_KEY
            } catch (e: Throwable) {
                try {
                    val clazz = Class.forName("com.example.BuildConfig")
                    val field = clazz.getField("GEMINI_API_KEY")
                    field.get(null) as String
                } catch (e2: Exception) {
                    ""
                }
            }
        }.trim()

        // Strip quotes/whitespace from copy-paste mistakes
        val apiKey = rawKey
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .trim()

        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            onError("Hata: Geçerli bir API Anahtarınız bulunamadı! Lütfen ayarlardan bir anahtar girin.")
            return
        }

        val apiBaseRaw = _geminiApiBase.value.trim().ifEmpty { "https://generativelanguage.googleapis.com" }
        val apiBase = apiBaseRaw
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .trim()

        var modelNameRaw = _geminiModelName.value.trim().ifEmpty { "gemini-1.5-flash" }
        var modelName = modelNameRaw
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .trim()

        val isGemini = apiBase.contains("generativelanguage.googleapis.com")

        // Robust engineering: Auto-correct incorrect Gemini/Other model names to prevent 404/route errors
        if (isGemini) {
            if (modelName.isEmpty() || modelName == "gemini-3.5-flash" || modelName.contains("3.5") || modelName == "gemini-3-flash" || modelName.contains("gemini-3")) {
                modelName = "gemini-1.5-flash"
            }
        } else {
            if (modelName.isEmpty()) {
                modelName = when {
                    apiBase.contains("groq.com") -> "llama-3.3-70b-versatile"
                    apiBase.contains("openrouter.ai") -> "google/gemini-2.0-flash-exp:free"
                    apiBase.contains("openai.com") -> "gpt-4o-mini"
                    apiBase.contains("deepseek.com") -> "deepseek-chat"
                    else -> "gpt-4o-mini"
                }
            }
        }

        val url = if (isGemini) {
            // Always use v1beta for developer API keys to prevent 404 and routing errors on models like gemini-1.5-flash
            "${apiBase.removeSuffix("/")}/v1beta/models/${modelName}:generateContent?key=${apiKey}"
        } else {
            val base = apiBase.removeSuffix("/")
            if (base.endsWith("/chat/completions") || base.endsWith("/generateContent")) {
                base
            } else if (base.endsWith("/v1")) {
                "$base/chat/completions"
            } else {
                "$base/v1/chat/completions"
            }
        }

        // For safe debugging, log masked url
        val maskedUrl = if (isGemini) {
            if (url.contains("?key=")) {
                url.substringBefore("?key=") + "?key=***" + url.substringAfter("?key=").takeLast(4)
            } else {
                url
            }
        } else {
            url
        }
        Log.d(TAG, "Executing AI Request on URL: $maskedUrl with model: $modelName")

        // Increased connect and read/write timeouts to 45s for reliability under load
        val client = OkHttpClient.Builder()
            .connectTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val requestBuilder = Request.Builder()
            .url(url)
            .addHeader("Accept", "application/json")

        if (isGemini) {
            val requestJson = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", promptText)
                            })
                        })
                    })
                }
                put("contents", contentsArray)
                if (isJsonResponse) {
                    put("generationConfig", JSONObject().apply {
                        put("responseMimeType", "application/json")
                    })
                }
            }
            val mediaType = "application/json; charset=utf-8".toMediaType()
            requestBuilder.post(requestJson.toString().toRequestBody(mediaType))
        } else {
            val requestJson = JSONObject().apply {
                put("model", modelName)
                val messagesArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", promptText)
                    })
                }
                put("messages", messagesArray)
                if (isJsonResponse) {
                    put("response_format", JSONObject().apply {
                        put("type", "json_object")
                    })
                }
            }
            val mediaType = "application/json; charset=utf-8".toMediaType()
            requestBuilder.post(requestJson.toString().toRequestBody(mediaType))
            requestBuilder.addHeader("Authorization", "Bearer $apiKey")
        }

        try {
            val response = client.newCall(requestBuilder.build()).execute()
            val responseStr = response.body?.string()

            if (response.isSuccessful && !responseStr.isNullOrEmpty()) {
                val responseJson = JSONObject(responseStr)
                val text = if (isGemini) {
                    responseJson.optJSONArray("candidates")
                        ?.optJSONObject(0)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts")
                        ?.optJSONObject(0)
                        ?.optString("text")
                } else {
                    responseJson.optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("message")
                        ?.optString("content")
                }

                if (!text.isNullOrEmpty()) {
                    onSuccess(text.trim())
                } else {
                    onError("Hata: Boş yanıt alındı veya kota aşılmış olabilir.")
                }
            } else {
                val errorMsgDetail = extractErrorMessage(responseStr, response.message)
                onError("Hata Kodu: ${response.code} / $errorMsgDetail")
            }
        } catch (e: Exception) {
            onError(toUserFriendlyMessage(e))
        }
    }

    fun setGeminiPersonality(personality: String) {
        _geminiPersonality.value = personality
    }

    fun setGeminiActive(active: Boolean) {
        _isGeminiActive.value = active
        if (active) {
            _isManualSliderActive.value = false
            _isPedalPressed.value = false
            startGeminiAutopilot()
        } else {
            geminiJob?.cancel()
            _geminiLog.value = "Yapay Zeka Sürüş Asistanı Devre Dışı."
            releaseGasPedal()
        }
    }

    private fun startGeminiAutopilot() {
        geminiJob?.cancel()
        geminiJob = viewModelScope.launch(Dispatchers.IO) {
            _geminiLog.value = "AI Sürücü başlatılıyor..."
            var currentDelay = (_geminiInterval.value * 1000L).coerceIn(10000L, 120000L)
            
            while (_isGeminiActive.value) {
                _geminiLog.value = "AI'dan sürüş komutları alınıyor..."
                var isSuccess = false
                var errorMsg = ""

                val promptText = """
                    You are a simulated virtual car pilot driving with personality style: '${_geminiPersonality.value}' (sport, commuter or peaceful).
                    Current engine RPM is ${_rpm.value} RPM, current speed is ${_speed.value} km/h, engine load is ${_engineLoad.value}%.
                    Maximum engine RPM allowed is ${_activeProfile.value.maxRpm} RPM.
                    Acoustic sound profile selected is '${_activeProfile.value.label}' (id: ${_activeProfile.value.id}).
                    Provide the next target RPM and throttle load for simulation. Ensure to change RPM dynamically to mimic realistic gear changes, throttle acceleration bursts, deceleration pops and cracks (sudden lift-off), or steady cruising. 
                    If 'sport', accelerate aggressively to near max RPM, then drop significantly to emulate gear shifts, then pull up again.
                    If 'commuter', do stop-and-go driving with slow acceleration.
                    If 'peaceful', cruise smoothly around 1500-2500 RPM.
                    Output a strict JSON object with:
                    1. "targetRpm": a float/int between 800.0 and ${_activeProfile.value.maxRpm}
                    2. "throttleLoad": a float between 0.0 and 1.0
                    3. "thoughtTr": a short 1-sentence explanation of your driving action in Turkish language.
                    
                    Do NOT wrap response in markdown code blocks, do NOT write ```json, output ONLY the valid raw JSON object. Example:
                    {"targetRpm": 3400.0, "throttleLoad": 0.65, "thoughtTr": "Hızlanmak için gaza basıyorum..."}
                """.trimIndent()

                executeAiRequest(
                    promptText = promptText,
                    isJsonResponse = true,
                    onSuccess = { responseText ->
                        isSuccess = true
                        try {
                            val cleanText = responseText.trim()
                                .removePrefix("```json")
                                .removePrefix("```")
                                .removeSuffix("```")
                                .trim()

                            val parsed = JSONObject(cleanText)
                            val targetRpmResult = parsed.optDouble("targetRpm", 800.0).toFloat().coerceIn(800f, _activeProfile.value.maxRpm)
                            val throttleLoadResult = parsed.optDouble("throttleLoad", 0.0).toFloat().coerceIn(0f, 1f)
                            val thoughtTrResult = parsed.optString("thoughtTr", "AI Sürüş Asistanı sürüyor.")

                            _geminiLog.value = "🤖 AI: \"$thoughtTrResult\""

                            viewModelScope.launch(Dispatchers.Main) {
                                if (_connectionState.value !is ConnectionState.Connected) {
                                    connectToObd(Obd2BluetoothManager.MOCK_DEVICE_ADDRESS)
                                }
                                
                                _rpm.value = targetRpmResult
                                
                                bluetoothManager.setMockInput(
                                    pedalPressed = throttleLoadResult > 0.2f,
                                    targetRpm = targetRpmResult
                                )
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Parsing autopilot payload failed", e)
                            _geminiLog.value = "Ayrıştırma Hatası: ${e.message}"
                        }
                    },
                    onError = { errorText ->
                        isSuccess = false
                        errorMsg = errorText
                        _geminiLog.value = errorText
                        val randomRpm = 1200f + (Math.random() * (_activeProfile.value.maxRpm - 1500f)).toFloat()
                        viewModelScope.launch(Dispatchers.Main) {
                            _rpm.value = randomRpm
                        }
                    }
                )

                if (isSuccess) {
                    currentDelay = (_geminiInterval.value * 1000L).coerceIn(10000L, 120000L) // Başarıda tekrar kullanıcının belirlediği güvenli aralığa çekiyoruz
                } else {
                    if (errorMsg.contains("429") || errorMsg.contains("Too Many", ignoreCase = true) || errorMsg.contains("Limit", ignoreCase = true) || errorMsg.contains("Sinir", ignoreCase = true) || errorMsg.contains("Sınır", ignoreCase = true)) {
                        currentDelay = (currentDelay * 2).coerceIn(90000L, 180000L) // 429'da bekleme aralığını iki katına (en az 90, en çok 180 saniyeye) çıkarıyoruz
                        _geminiLog.value = "⚠️ API Sınırı (429) Aşıldı. Gecikme geçici olarak artırıldı: Bir sonraki sürüş komutu ${currentDelay / 1000} sn sonra istenecektir."
                    } else {
                        currentDelay = (currentDelay + 10000L).coerceIn(45000L, 120000L) // Diğer genel hatalarda hafifçe artırıyoruz
                    }
                }

                delay(currentDelay)
            }
        }
    }

    fun generateAiDtcReport() {
        if (_isAiDtcLoading.value) return
        _isAiDtcLoading.value = true
        _aiDtcReport.value = "AI Teşhis Raporu oluşturuluyor..."
        
        viewModelScope.launch(Dispatchers.IO) {
            val activeDtcList = _dtcCodes.value
            val dtcPromptPart = if (activeDtcList.isEmpty()) {
                "Araçta şu anda kayıtlı aktif hata kodu (DTC) GÖRÜNMÜYOR, ECU sistemleri temiz. Genel bir kontrol yap."
            } else {
                "Aracın ECU belleğinde kayıtlı hata kodları: ${activeDtcList.joinToString(", ")}. Bu kodların anlamlarını açıkla ve ciddi bir sorun olup olmadığını belirt."
            }

            val promptText = """
                Sen akıllı bir OBD2 Yapay Zeka Oto Teşhis Uzmanısın (Diagnostic Advisor).
                Aşağıdaki araç telemetri verilerini ve hata durumunu analiz et ve Türkçe, samimi ama son derece uzman bir dille mini bir 'Araç Sağlık Analiz Raporu' ve öneriler hazırla.
                
                TELEMETRİ VERİLERİ:
                - Motor Devri: ${_rpm.value.toInt()} RPM
                - Hız: ${_speed.value.toInt()} km/h
                - Motor Yükü: ${_engineLoad.value.toInt()}%
                - Soğutma Suyu Sıcaklığı: ${_coolantTemp.value.toInt()} °C
                - Gaz Kelebeği Açıklığı: ${_throttle.value.toInt()}%
                - Emme Manifoldu Basıncı (Turbo/Boost): ${_manifoldPressure.value.toInt()} kPa
                - Emme Havası Sıcaklığı: ${_intakeTemp.value.toInt()} °C
                - Aktif Ses Profili / Araç Karakteri: ${_activeProfile.value.label}
                
                HATA DURUMU:
                $dtcPromptPart
                
                Yanıtın tamamen Türkçe olsun. Önerilerin somut, maddeler halinde olsun ve sürücüye güven versin. Yanıtta markdown işaretlemeleri kullanabilirsin (kalın yazma, listeler vb.).
            """.trimIndent()

            executeAiRequest(
                promptText = promptText,
                isJsonResponse = false,
                onSuccess = { text ->
                    _aiDtcReport.value = text
                    _isAiDtcLoading.value = false
                },
                onError = { error ->
                    _aiDtcReport.value = error
                    _isAiDtcLoading.value = false
                }
            )
        }
    }

    fun generateAiCoachReport() {
        if (_isAiCoachLoading.value) return
        _isAiCoachLoading.value = true
        _aiCoachReport.value = "Sürüş tarzı analiz ediliyor..."
        
        viewModelScope.launch(Dispatchers.IO) {
            val promptText = """
                Sen akıllı ve biraz da esprili bir 'Yapay Zeka Sürüş ve Eco Koçu'sun (AI Eco & Performance Coach).
                Aşağıdaki anlık sürüş ve telemetri durumuna bakarak sürücüye bir sürüş tarzı puanı (100 üzerinden) ver. 
                Sürüş tarzını esprili, eğlenceli ama faydalı bir dille yorumla. Yakıt verimliliği (Eco) ile performans dengesini analiz et.
                
                SÜRÜŞ VERİLERİ:
                - Anlık Devir: ${_rpm.value.toInt()} RPM (Max devir sınırı: ${_activeProfile.value.maxRpm.toInt()} RPM)
                - Hız: ${_speed.value.toInt()} km/h
                - Motor Sıkıştırma/Yük: ${_engineLoad.value.toInt()}%
                - Gaz Pedalı Baskısı: ${_throttle.value.toInt()}%
                - Seçili Ses Profili: ${_activeProfile.value.label} (${_activeProfile.value.description})
                
                İPUÇLARI:
                - Eğer devir çok yüksekse ve araç ralli veya spor sesindeyse "Tam bir pist canavarısın ama cüzdanın buna ağlıyor olabilir!" şeklinde tatlı laf sokmalar yapabilirsin.
                - Eğer rölantiye yakın sakin sürüyorsa "Eko-Elçi gibisin, kutuplardaki pandalar sana teşekkür ediyor." tarzı espriler yap.
                
                Çıktı formatı:
                1. Sürüş Tarzı Başlığı (Örn: "Otoban Fatihi" veya "Sakin Sürücü")
                2. Sürüş Skoru: X / 100
                3. Detaylı ve esprili yorum (Türkçe)
                4. ECO / Yakıt Önerisi
                
                Yanıtta markdown biçimlendirmesi kullanabilirsin.
            """.trimIndent()

            executeAiRequest(
                promptText = promptText,
                isJsonResponse = false,
                onSuccess = { text ->
                    _aiCoachReport.value = text
                    _isAiCoachLoading.value = false
                },
                onError = { error ->
                    _aiCoachReport.value = error
                    _isAiCoachLoading.value = false
                }
            )
        }
    }

    private fun toUserFriendlyMessage(e: Exception): String {
        return when {
            e is java.net.UnknownHostException || e.localizedMessage?.contains("Unable to resolve host", ignoreCase = true) == true -> {
                "İnternet bağlantısı kurulamadı. Lütfen internet durumunuzu veya cihazınızın çevrimiçi olduğundan emin olun."
            }
            e is java.net.SocketTimeoutException || e.localizedMessage?.contains("timeout", ignoreCase = true) == true -> {
                "Sunucuyla bağlantı zaman aşımına uğradı. Lütfen internet bağlantınızı kontrol edip tekrar deneyin."
            }
            else -> {
                "Bağlantı Hatası: ${e.localizedMessage ?: "Bir hata oluştu"}"
            }
        }
    }
    
    private var gpsListener: LocationListener? = null
    private var sensorListener: SensorEventListener? = null

    fun toggleRouteSimulation(active: Boolean) {
        _isRouteSimulationActive.value = active
        if (!active) {
            _gpsRoutePoints.value = listOf(39.9334 to 32.8597)
        }
    }

    fun resetPerformanceTimer() {
        timerJob?.cancel()
        _isTimerRunning.value = false
        _timerMillis.value = 0L
        _sprint60Time.value = 0f
        _sprint100Time.value = 0f
        is60Logged = false
        is100Logged = false
        _timerStatusText.value = "HAZIR (Sürüş yapın veya gaza basın)"
    }

    fun clearPerformanceRuns() {
        _pastRuns.value = emptyList()
    }

    private fun checkPerformanceTimer(currentSpeed: Int) {
        if (currentSpeed <= 0) {
            // Auto reset if we were previously fully finished, giving user a seamless loop!
            if (!_isTimerRunning.value && (_sprint100Time.value > 0f || _sprint60Time.value > 0f)) {
                _timerStatusText.value = "YENİ KOŞU İÇİN HAZIR (Hız: 0 km/h)"
            }
            return
        }

        // Trigger start from actual zero standstill speed
        if (!_isTimerRunning.value && _sprint100Time.value == 0f && _sprint60Time.value == 0f) {
            startPerformanceTimer()
        }

        // Threshold timing detections
        if (_isTimerRunning.value) {
            val elapsedSecs = (System.currentTimeMillis() - timerStartTime) / 1000f

            if (currentSpeed >= 60 && !is60Logged) {
                _sprint60Time.value = elapsedSecs
                is60Logged = true
                _timerStatusText.value = "🔥 0-60 KM/H: ${String.format("%.2f", elapsedSecs)}s!"
            }

            if (currentSpeed >= 100 && !is100Logged) {
                _sprint100Time.value = elapsedSecs
                is100Logged = true
                _isTimerRunning.value = false
                timerJob?.cancel()

                val currentBest = _best100Time.value
                if (currentBest == null || elapsedSecs < currentBest) {
                    _best100Time.value = elapsedSecs
                }

                val totalTimeStr = String.format("%.2f", elapsedSecs)
                val time60Str = if (is60Logged) String.format("%.2f", _sprint60Time.value) else "--"
                val runSummary = "Koşu #${_pastRuns.value.size + 1}: 0-100 km/h: ${totalTimeStr}s (0-60: ${time60Str}s) | ${_activeProfile.value.label}"
                
                _pastRuns.value = _pastRuns.value + runSummary
                _timerStatusText.value = "🏁 KOŞU TAMAMLANDI! 0-100: ${totalTimeStr}s!"
            }
        }
    }

    private fun startPerformanceTimer() {
        timerJob?.cancel()
        _isTimerRunning.value = true
        _sprint60Time.value = 0f
        _sprint100Time.value = 0f
        is60Logged = false
        is100Logged = false
        timerStartTime = System.currentTimeMillis()
        _timerStatusText.value = "🚀 SPRINT BAŞLADI! TAM GAZ!"

        timerJob = viewModelScope.launch(Dispatchers.Main) {
            while (_isTimerRunning.value) {
                _timerMillis.value = System.currentTimeMillis() - timerStartTime
                delay(10) // Smooth millisecond tick rate in Compose UI
            }
        }
    }

    private fun setupGpsAndSensors() {
        // 1. Android Native Location updates tracking (LocationManager)
        try {
            val context = getApplication<Application>()
            val locManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as? LocationManager
            if (locManager != null) {
                gpsListener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (!_isRouteSimulationActive.value) {
                            _gpsLatitude.value = location.latitude
                            _gpsLongitude.value = location.longitude
                            _gpsAltitude.value = location.altitude
                            _gpsSpeed.value = location.speed * 3.6f // convert m/s to km/h
                            _gpsBearing.value = location.bearing
                            _gpsIsActive.value = true

                            // Update GPS trailing points
                            val pts = _gpsRoutePoints.value.toMutableList()
                            if (pts.size > 200) pts.removeAt(0)
                            pts.add(location.latitude to location.longitude)
                            _gpsRoutePoints.value = pts
                        }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }

                val hasFineLoc = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (hasFineLoc) {
                    locManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        1000L,
                        1.0f,
                        gpsListener!!
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Dahili GPS baslatilamadi (Guvenli atlama): ${e.localizedMessage}")
        }

        // 2. Android Device Attitude and Gravity Sensors (SensorManager)
        try {
            val context = getApplication<Application>()
            val sensManager = context.getSystemService(android.content.Context.SENSOR_SERVICE) as? SensorManager
            val accelSensor = sensManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            
            if (sensManager != null && accelSensor != null) {
                // Initialize default rotation
                try {
                    val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                    @Suppress("DEPRECATION")
                    val rot = wm?.defaultDisplay?.rotation ?: Surface.ROTATION_0
                    _deviceRotation.value = rot
                } catch (_: Exception) {}

                sensorListener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                            val rawX = event.values[0] / 9.81f // Left/Right relative to portrait hardware
                            val rawY = event.values[1] / 9.81f // Forward/Back relative to portrait hardware
                            val rawZ = event.values[2] / 9.81f

                            // Remap coordinates relative to visual screen orientation:
                            // In ROTATION_0 (Portrait): screenX = rawX, screenY = rawY
                            // In ROTATION_90 (Landscape): screenX = -rawY, screenY = rawX
                            // In ROTATION_270 (Reverse Landscape): screenX = rawY, screenY = -rawX
                            // In ROTATION_180 (Reverse Portrait): screenX = -rawX, screenY = -rawY
                            val (screenX, screenY) = when (_deviceRotation.value) {
                                Surface.ROTATION_90 -> -rawY to rawX
                                Surface.ROTATION_270 -> rawY to -rawX
                                Surface.ROTATION_180 -> -rawX to -rawY
                                else -> rawX to rawY
                            }

                            // Update live G-Force coordinates with Low Pass Filter:
                            // Lateral G: Right turn forces vehicle left (-screenX)
                            // Longitudinal G: Deceleration/braking forces forward (+screenY), acceleration pulls backward (-screenY)
                            _gForceX.value = _gForceX.value * 0.95f + (-screenX) * 0.05f
                            _gForceY.value = _gForceY.value * 0.95f + screenY * 0.05f

                            val pitchVal = Math.toDegrees(Math.atan2(screenY.toDouble(), rawZ.toDouble())).toFloat()
                            val rollVal = Math.toDegrees(Math.atan2((-screenX).toDouble(), Math.sqrt((screenY * screenY + rawZ * rawZ).toDouble()))).toFloat()

                            _pitch.value = _pitch.value * 0.96f + pitchVal * 0.04f
                            _roll.value = _roll.value * 0.96f + rollVal * 0.04f
                        }
                    }
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }

                sensManager.registerListener(
                    sensorListener,
                    accelSensor,
                    SensorManager.SENSOR_DELAY_UI
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Dahili Ivmeolcer baslatilamadi (Guvenli atlama): ${e.localizedMessage}")
        }

        // 3. Immersive Simulation updates interval loop (for offline / indoor/ emulator testing)
        var simAngle = 0.0
        viewModelScope.launch(Dispatchers.Main) {
            while (true) {
                if (_isRouteSimulationActive.value) {
                    simAngle += 0.015

                    val rIndex = simAngle % (2 * Math.PI)
                    val simulatedLat = 39.9334 + 0.0035 * Math.sin(rIndex) + 0.0008 * Math.sin(2 * rIndex)
                    val simulatedLng = 32.8597 + 0.0075 * Math.cos(rIndex)
                    val simulatedAlt = 938.0 + 45.0 * Math.sin(simAngle * 0.4)

                    val dLat = 0.0035 * Math.cos(rIndex) + 0.0016 * Math.cos(2 * rIndex)
                    val dLng = -0.0075 * Math.sin(rIndex)
                    val heading = Math.toDegrees(Math.atan2(dLng, dLat)).toFloat()

                    _gpsLatitude.value = simulatedLat
                    _gpsLongitude.value = simulatedLng
                    _gpsAltitude.value = simulatedAlt
                    _gpsBearing.value = if (heading < 0f) heading + 360f else heading
                    _gpsIsActive.value = true

                    val pts = _gpsRoutePoints.value.toMutableList()
                    if (pts.size > 140) pts.removeAt(0)
                    pts.add(simulatedLat to simulatedLng)
                    _gpsRoutePoints.value = pts

                    val currentSpeed = _speed.value
                    val simulatedLongG = (if (_isPedalPressed.value) 0.45f else -0.1f) + (Math.sin(simAngle * 1.5) * 0.08).toFloat()
                    val simulatedLatG = (Math.cos(simAngle) * 0.35f).toFloat() + (Math.sin(simAngle * 3) * 0.05).toFloat()

                    _gForceX.value = _gForceX.value * 0.95f + simulatedLatG * 0.05f
                    _gForceY.value = _gForceY.value * 0.95f + simulatedLongG * 0.05f

                    _roll.value = _roll.value * 0.94f + (-simulatedLatG * 25f) * 0.06f
                    _pitch.value = _pitch.value * 0.94f + (simulatedLongG * 15f) * 0.06f
                }
                delay(200)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        bluetoothManager.disconnect()
        timerJob?.cancel()
        try {
            if (gpsListener != null) {
                val locManager = getApplication<Application>().getSystemService(android.content.Context.LOCATION_SERVICE) as? LocationManager
                locManager?.removeUpdates(gpsListener!!)
            }
            if (sensorListener != null) {
                val sensManager = getApplication<Application>().getSystemService(android.content.Context.SENSOR_SERVICE) as? SensorManager
                sensManager?.unregisterListener(sensorListener)
            }
        } catch (e: Exception) {
            // Ignore
        }
        Log.i(TAG, "ViewModel cleared. Bluetooth released.")
    }
}
