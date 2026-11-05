package com.example.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class D4fSensorData(
    val pid: String,
    val name: String,
    val description: String,
    val valueStr: String,
    val unit: String,
    val icon: ImageVector,
    val statusText: String,
    val statusColor: Color,
    val progress: Float, // 0f..1f
    val normalRange: String,
    val category: String
)

@Composable
fun SensorsMatrixScreen(
    viewModel: ObdDashboardViewModel,
    cardBg: Color,
    electricCyan: Color,
    sportAccentRed: Color,
    modifier: Modifier = Modifier
) {
    val rpm by viewModel.rpm.collectAsState()
    val speed by viewModel.speed.collectAsState()
    val coolantTemp by viewModel.coolantTemp.collectAsState()
    val throttle by viewModel.throttle.collectAsState()
    val engineLoad by viewModel.engineLoad.collectAsState()
    val intakeTemp by viewModel.intakeTemp.collectAsState()
    val manifoldPressure by viewModel.manifoldPressure.collectAsState()
    val fuelLevel by viewModel.fuelLevel.collectAsState()
    val batteryVoltage by viewModel.batteryVoltage.collectAsState()
    val timingAdvance by viewModel.timingAdvance.collectAsState()
    val stft by viewModel.stft.collectAsState()
    val ltft by viewModel.ltft.collectAsState()
    val o2Voltage by viewModel.o2Voltage.collectAsState()
    val o2VoltageSensor2 by viewModel.o2VoltageSensor2.collectAsState()
    val pedalPosition by viewModel.pedalPosition.collectAsState()
    val baroPressure by viewModel.baroPressure.collectAsState()
    val fuelSystemStatus by viewModel.fuelSystemStatus.collectAsState()
    val engineRunTime by viewModel.engineRunTime.collectAsState()
    val ecuMilStatus by viewModel.ecuMilStatus.collectAsState()

    // Build D4F Sensor Model
    val sensorsList = remember(
        rpm, speed, coolantTemp, throttle, engineLoad, intakeTemp,
        manifoldPressure, fuelLevel, batteryVoltage, timingAdvance, stft, ltft, o2Voltage,
        o2VoltageSensor2, pedalPosition, baroPressure, fuelSystemStatus, engineRunTime, ecuMilStatus
    ) {
        val list = mutableListOf<D4fSensorData>()

        // 1. Devir (RPM)
        val rpmProgress = (rpm / 6200f).coerceIn(0f, 1f)
        val rpmStatus = when {
            rpm == 0f -> "Motor Kapalı"
            rpm < 900f -> "Rölanti (Normal)"
            rpm < 4000f -> "Normal Çalışma"
            rpm < 5500f -> "Yüksek Devir"
            else -> "Kırmızı Alan (Dikkat)"
        }
        val rpmColor = when {
            rpm > 5500f -> sportAccentRed
            rpm > 4000f -> Color(0xFFFBBF24)
            else -> Color(0xFF34D399)
        }
        list.add(
            D4fSensorData(
                pid = "PID 010C",
                name = "Motor Devri (RPM)",
                description = "Krank Mili Pozisyon Sensörü",
                valueStr = String.format("%.0f", rpm),
                unit = "RPM",
                icon = Icons.Default.Speed,
                statusText = rpmStatus,
                statusColor = rpmColor,
                progress = rpmProgress,
                normalRange = "750 - 5800 RPM",
                category = "MOTOR & BASINÇ"
            )
        )

        // 2. Hız (Speed)
        list.add(
            D4fSensorData(
                pid = "PID 010D",
                name = "Araç Hızı (VSS)",
                description = "Şanzıman / ABS Hız Sensörü",
                valueStr = "$speed",
                unit = "km/h",
                icon = Icons.Default.DirectionsCar,
                statusText = if (speed == 0) "Durgun" else "$speed km/h seyir",
                statusColor = electricCyan,
                progress = (speed / 180f).coerceIn(0f, 1f),
                normalRange = "0 - 170 km/h",
                category = "MOTOR & BASINÇ"
            )
        )

        // 3. Gaz Kelebeği (TPS) - Gaz Pedalı İle Yan Yana
        val tpsProgress = (throttle / 100f).coerceIn(0f, 1f)
        val tpsStatus = when {
            throttle <= 15f -> "Rölanti Açıklığı (%10-15)"
            throttle <= 50f -> "Kısmi Kelebek Açık"
            else -> "Tam Basış / Hızlanma"
        }
        list.add(
            D4fSensorData(
                pid = "PID 0111",
                name = "Gaz Kelebeği (TPS)",
                description = "Elektronik Kelebek Potansiyometresi",
                valueStr = String.format("%.1f", throttle),
                unit = "%",
                icon = Icons.Default.Tune,
                statusText = tpsStatus,
                statusColor = if (throttle > 70f) Color(0xFFFBBF24) else Color(0xFF38BDF8),
                progress = tpsProgress,
                normalRange = "%10 - %88 Kelebek Açısı",
                category = "MOTOR & BASINÇ"
            )
        )

        // 4. Gaz Pedal Sensörü (APP) - Gaz Kelebeğinin Yanında
        val pedalProgress = (pedalPosition / 100f).coerceIn(0f, 1f)
        val pedalStatus = when {
            pedalPosition <= 2f -> "Ayak Pedaldan Çekik (%0)"
            pedalPosition <= 30f -> "Kısmi Gaz Basılı"
            pedalPosition <= 75f -> "Hızlanma Basışı"
            else -> "Dip Gaz / WOT (%100)"
        }
        list.add(
            D4fSensorData(
                pid = "PID 0149 / 015A",
                name = "Gaz Pedal Sensörü (APP)",
                description = "Sürücü Ayak Pedalı Potansiyometresi",
                valueStr = String.format("%.1f", pedalPosition),
                unit = "%",
                icon = Icons.Default.TouchApp,
                statusText = pedalStatus,
                statusColor = if (pedalPosition > 75f) sportAccentRed else if (pedalPosition > 20f) Color(0xFFFBBF24) else Color(0xFF34D399),
                progress = pedalProgress,
                normalRange = "%0 - %100 Pedal Basışı",
                category = "MOTOR & BASINÇ"
            )
        )

        // 5. MAP Sensörü (Emme Manifoldu Mutlak Basıncı)
        val mapProgress = (manifoldPressure / 120f).coerceIn(0f, 1f)
        val mapStatus = when {
            manifoldPressure == 0 -> "Veri Bekleniyor"
            manifoldPressure in 28..40 -> "İdeal Rölanti Vakumu"
            manifoldPressure in 41..60 -> "Kısmi Yük"
            manifoldPressure in 61..95 -> "Yüksek Gaz Açıklığı"
            manifoldPressure > 95 -> "WOT (Tam Gaz Açık)"
            else -> "Düşük Vakum"
        }
        val mapColor = when {
            manifoldPressure in 28..40 -> Color(0xFF34D399)
            manifoldPressure in 41..95 -> electricCyan
            manifoldPressure > 95 -> Color(0xFFFBBF24)
            else -> Color(0xFF94A3B8)
        }
        list.add(
            D4fSensorData(
                pid = "PID 010B",
                name = "Manifold Mutlak Basıncı (MAP)",
                description = "Emme Manifoldu Vakum & Basınç",
                valueStr = "$manifoldPressure",
                unit = "kPa",
                icon = Icons.Default.Compress,
                statusText = mapStatus,
                statusColor = mapColor,
                progress = mapProgress,
                normalRange = "30 - 38 kPa (Rölanti)",
                category = "MOTOR & BASINÇ"
            )
        )

        // 6. Barometrik Basınç (BARO) - MAP ile Karşılaştırmalı Yan Yana
        val baroProgress = (baroPressure / 110f).coerceIn(0f, 1f)
        val baroStatus = when {
            baroPressure >= 100 -> "Deniz Seviyesi (~101 kPa)"
            baroPressure >= 92 -> "Orta Rakım (~95 kPa)"
            else -> "Yüksek Rakım / Dağlık"
        }
        list.add(
            D4fSensorData(
                pid = "PID 0133",
                name = "Barometrik Basınç (BARO)",
                description = "Dış Atmosfer & Rakım Referansı",
                valueStr = "$baroPressure",
                unit = "kPa",
                icon = Icons.Default.Cloud,
                statusText = baroStatus,
                statusColor = electricCyan,
                progress = baroProgress,
                normalRange = "90 - 103 kPa (Rakıma Bağlı)",
                category = "MOTOR & BASINÇ"
            )
        )

        // 7. Motor Yükü (Calc Load)
        val loadProgress = (engineLoad / 100f).coerceIn(0f, 1f)
        list.add(
            D4fSensorData(
                pid = "PID 0104",
                name = "Hesaplanan Motor Yükü",
                description = "ECU Hava/Yakıt Tüketim Yükü",
                valueStr = String.format("%.1f", engineLoad),
                unit = "%",
                icon = Icons.Default.FitnessCenter,
                statusText = if (engineLoad < 30f) "Düşük Yük" else if (engineLoad < 70f) "Orta Yük" else "Ağır Yük",
                statusColor = if (engineLoad > 80f) sportAccentRed else Color(0xFF34D399),
                progress = loadProgress,
                normalRange = "%18 - %26 (Rölanti)",
                category = "MOTOR & BASINÇ"
            )
        )

        // 8. Ateşleme Avansı (Timing Advance)
        val timingProgress = ((timingAdvance + 10f) / 50f).coerceIn(0f, 1f)
        list.add(
            D4fSensorData(
                pid = "PID 010E",
                name = "Ateşleme Avansı",
                description = "1. Silindir Buji Ateşleme Açısı",
                valueStr = String.format("%+.1f", timingAdvance),
                unit = "°",
                icon = Icons.Default.Bolt,
                statusText = if (timingAdvance < 15f) "Rölanti Avansı" else "Yüksek Devir Avansı",
                statusColor = Color(0xFFA78BFA),
                progress = timingProgress,
                normalRange = "+6° ile +35°",
                category = "MOTOR & BASINÇ"
            )
        )

        // 9. Motor Soğutma Suyu (ECT / Hararet)
        val tempProgress = (coolantTemp / 120f).coerceIn(0f, 1f)
        val tempStatus = when {
            coolantTemp < 65f -> "Soğuk Motor (Isınıyor)"
            coolantTemp < 82f -> "Isınma Devresi"
            coolantTemp <= 96f -> "İdeal Termostat Açık (Normal)"
            coolantTemp <= 102f -> "Fan 1. Kademe Devrede"
            else -> "YÜKSEK HARARET! (Dikkat)"
        }
        val tempColor = when {
            coolantTemp > 102f -> sportAccentRed
            coolantTemp > 96f -> Color(0xFFFBBF24)
            coolantTemp < 70f -> Color(0xFF60A5FA)
            else -> Color(0xFF34D399)
        }
        list.add(
            D4fSensorData(
                pid = "PID 0105",
                name = "Soğutma Suyu (ECT Hararet)",
                description = "D4F Çift Tırnaklı Hararet Müşürü",
                valueStr = String.format("%.1f", coolantTemp),
                unit = "°C",
                icon = Icons.Default.Thermostat,
                statusText = tempStatus,
                statusColor = tempColor,
                progress = tempProgress,
                normalRange = "85°C - 96°C",
                category = "TERMAL & ELEKTRİK"
            )
        )

        // 10. Emme Havası Sıcaklığı (IAT)
        val iatProgress = (intakeTemp / 80f).coerceIn(0f, 1f)
        val iatStatus = if (intakeTemp < 45) "Serin Emme Havası" else "Sıcak Manifold Havası"
        list.add(
            D4fSensorData(
                pid = "PID 010F",
                name = "Emme Havası Sıcaklığı (IAT)",
                description = "Manifold Entegre Hava Isı Sensörü",
                valueStr = "$intakeTemp",
                unit = "°C",
                icon = Icons.Default.Air,
                statusText = iatStatus,
                statusColor = if (intakeTemp > 50) Color(0xFFFBBF24) else Color(0xFF38BDF8),
                progress = iatProgress,
                normalRange = "15°C - 50°C",
                category = "TERMAL & ELEKTRİK"
            )
        )

        // 11. Ön Oksijen / Lambda Sensörü (O2 Bank 1 Sensor 1)
        val o2Progress = (o2Voltage / 1.0f).coerceIn(0f, 1f)
        val o2Status = if (o2Voltage < 0.45f) "Fakir Karışım (<0.45V)" else "Zengin Karışım (>0.45V)"
        list.add(
            D4fSensorData(
                pid = "PID 0114",
                name = "Ön Lambda (Katalizör Öncesi)",
                description = "Zirkonya Egzoz Giriş Lambda",
                valueStr = String.format("%.2f", o2Voltage),
                unit = "V",
                icon = Icons.Default.Sensors,
                statusText = o2Status,
                statusColor = electricCyan,
                progress = o2Progress,
                normalRange = "0.1V - 0.9V Dalgalanma",
                category = "YAKIT & LPG"
            )
        )

        // 12. Arka Oksijen Sensörü (O2 Bank 1 Sensor 2) - Ön Lambda ile Yan Yana
        val o2Sensor2Progress = (o2VoltageSensor2 / 1.0f).coerceIn(0f, 1f)
        val o2Sensor2Status = when {
            o2VoltageSensor2 in 0.50f..0.75f -> "Katalizör Sağlam & Verimli"
            o2VoltageSensor2 < 0.35f -> "Fakir Egzoz Gazı"
            else -> "Zengin Egzoz Gazı"
        }
        list.add(
            D4fSensorData(
                pid = "PID 0115",
                name = "Arka Lambda (Katalizör Sonrası)",
                description = "Katalitik Konvertör Sağlık Kontrolü",
                valueStr = String.format("%.2f", o2VoltageSensor2),
                unit = "V",
                icon = Icons.Default.CheckCircle,
                statusText = o2Sensor2Status,
                statusColor = if (o2VoltageSensor2 in 0.45f..0.78f) Color(0xFF34D399) else Color(0xFFFBBF24),
                progress = o2Sensor2Progress,
                normalRange = "0.50V - 0.70V (Stabil Çizgi)",
                category = "YAKIT & LPG"
            )
        )

        // 13. Kısa Vadeli Yakıt Düzeltmesi (STFT)
        val stftProgress = ((stft + 25f) / 50f).coerceIn(0f, 1f)
        val stftStatus = when {
            kotlin.math.abs(stft) <= 4f -> "Mükemmel Karışım (İdeal)"
            kotlin.math.abs(stft) <= 10f -> "Normal Karışım Düzeltmesi"
            stft > 10f -> "Fakir Karışım (Yakıt Ekleniyor)"
            else -> "Zengin Karışım (Yakıt Kısılıyor)"
        }
        val stftColor = if (kotlin.math.abs(stft) > 12f) sportAccentRed else if (kotlin.math.abs(stft) > 6f) Color(0xFFFBBF24) else Color(0xFF34D399)
        list.add(
            D4fSensorData(
                pid = "PID 0106",
                name = "Kısa Vadeli Düzeltme (STFT)",
                description = "Bank 1 Anlık Lambda Regülasyonu",
                valueStr = String.format("%+.1f", stft),
                unit = "%",
                icon = Icons.Default.LocalGasStation,
                statusText = stftStatus,
                statusColor = stftColor,
                progress = stftProgress,
                normalRange = "±3% (Mükemmel), ±10% (Tolerans)",
                category = "YAKIT & LPG"
            )
        )

        // 14. Uzun Vadeli Yakıt Düzeltmesi (LTFT - LPG Kontrolü)
        val ltftProgress = ((ltft + 25f) / 50f).coerceIn(0f, 1f)
        val ltftStatus = when {
            kotlin.math.abs(ltft) <= 5f -> "ECU Haritası & LPG Ayarı İdeal"
            kotlin.math.abs(ltft) <= 12f -> "Kabul Edilebilir Sapma"
            ltft > 12f -> "Sürekli Fakir (LPG/Enjektör Ayarı Gerek)"
            else -> "Sürekli Zengin (Hava/Enjektör Fazlası)"
        }
        val ltftColor = if (kotlin.math.abs(ltft) > 12f) sportAccentRed else if (kotlin.math.abs(ltft) > 6f) Color(0xFFFBBF24) else Color(0xFF34D399)
        list.add(
            D4fSensorData(
                pid = "PID 0107",
                name = "Uzun Vadeli Düzeltme (LTFT)",
                description = "Kalıcı Adaptasyon (LPG Ayar Sağlığı)",
                valueStr = String.format("%+.1f", ltft),
                unit = "%",
                icon = Icons.Default.Memory,
                statusText = ltftStatus,
                statusColor = ltftColor,
                progress = ltftProgress,
                normalRange = "-8% ile +8% arası",
                category = "YAKIT & LPG"
            )
        )

        // 15. Yakıt Döngü Durumu (Fuel System Status)
        val isClosedLoop = fuelSystemStatus.contains("Kapalı", ignoreCase = true)
        list.add(
            D4fSensorData(
                pid = "PID 0103",
                name = "Yakıt Döngü Durumu",
                description = "ECU Lambda Kapalı/Açık Döngü",
                valueStr = if (isClosedLoop) "Kapalı" else "Açık",
                unit = "Döngü",
                icon = Icons.Default.Refresh,
                statusText = fuelSystemStatus,
                statusColor = if (isClosedLoop) Color(0xFF34D399) else Color(0xFFFBBF24),
                progress = if (isClosedLoop) 1.0f else 0.4f,
                normalRange = "Isınmış Motorda: Kapalı Döngü",
                category = "YAKIT & LPG"
            )
        )

        // 16. Akü / Alternatör Voltajı (Control Module)
        val voltProgress = ((batteryVoltage - 10f) / 6f).coerceIn(0f, 1f)
        val voltStatus = when {
            batteryVoltage < 12.0f -> "Düşük Akü (Şarj Gerekli)"
            batteryVoltage < 13.0f -> "Kontak Açık / Dinamo Pasif"
            batteryVoltage in 13.6f..14.6f -> "Şarj Dinamosu Sağlam (İdeal)"
            else -> "Yüksek Şarj Voltajı (>14.6V)"
        }
        val voltColor = when {
            batteryVoltage in 13.5f..14.6f -> Color(0xFF34D399)
            batteryVoltage < 12.2f -> sportAccentRed
            else -> Color(0xFFFBBF24)
        }
        list.add(
            D4fSensorData(
                pid = "PID 0142 / ATRV",
                name = "ECU & Akü Gerilimi",
                description = "Alternatör Şarj & Besleme Voltajı",
                valueStr = String.format("%.2f", batteryVoltage),
                unit = "V",
                icon = Icons.Default.BatteryChargingFull,
                statusText = voltStatus,
                statusColor = voltColor,
                progress = voltProgress,
                normalRange = "13.6V - 14.4V (Çalışırken)",
                category = "TERMAL & ELEKTRİK"
            )
        )

        // 17. Yakıt Seviyesi
        list.add(
            D4fSensorData(
                pid = "PID 012F",
                name = "Depo Yakıt Seviyesi",
                description = "Dirençli Şamandıra Telemetrisi",
                valueStr = String.format("%.0f", fuelLevel),
                unit = "%",
                icon = Icons.Default.Opacity,
                statusText = if (fuelLevel < 15f) "Yakıt Az (Rezerv)" else "Yeterli Seviye",
                statusColor = if (fuelLevel < 15f) Color(0xFFFBBF24) else Color(0xFF34D399),
                progress = (fuelLevel / 100f).coerceIn(0f, 1f),
                normalRange = "%15 - %100",
                category = "YAKIT & LPG"
            )
        )

        // 18. Motor Çalışma Süresi (Run Time Since Engine Start)
        val runMinutes = engineRunTime / 60
        val runSeconds = engineRunTime % 60
        list.add(
            D4fSensorData(
                pid = "PID 011F",
                name = "Motor Çalışma Süresi",
                description = "Kontağın Açılmasından Bu Yana",
                valueStr = String.format("%02d:%02d", runMinutes, runSeconds),
                unit = "dk:sn",
                icon = Icons.Default.Timer,
                statusText = "$runMinutes dakika devrede",
                statusColor = Color(0xFFA78BFA),
                progress = (engineRunTime / 3600f).coerceIn(0f, 1f),
                normalRange = "Start Sonrası Sürekli Sayar",
                category = "TERMAL & ELEKTRİK"
            )
        )

        list
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 165.dp),
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0C0E14))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Sensor Grid Cards directly
        items(sensorsList.size) { index ->
            val sensor = sensorsList[index]
            SensorMatrixCard(
                sensor = sensor,
                cardBg = cardBg,
                electricCyan = electricCyan
            )
        }

        // Bottom Spacer for clean edge-to-edge layout
        item(span = { GridItemSpan(maxLineSpan) }) {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun SensorMatrixCard(
    sensor: D4fSensorData,
    cardBg: Color,
    electricCyan: Color,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = sensor.progress,
        animationSpec = tween(durationMillis = 350),
        label = "progress"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header: PID Code & Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.White.copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Text(
                        text = sensor.pid,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFF94A3B8),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(sensor.statusColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = sensor.icon,
                        contentDescription = null,
                        tint = sensor.statusColor,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            // Sensor Title & Description
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = sensor.name,
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = (-0.2).sp
                    ),
                    maxLines = 1
                )
                Text(
                    text = sensor.description,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFF64748B),
                        fontSize = 9.sp
                    ),
                    maxLines = 1
                )
            }

            // Big Value & Unit Display
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = sensor.valueStr,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        fontStyle = FontStyle.Italic,
                        fontSize = 24.sp
                    )
                )
                Text(
                    text = sensor.unit,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = electricCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }

            // Mini Linear Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.08f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress)
                        .background(sensor.statusColor)
                )
            }

            // Status Text & Normal Range Reference
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = sensor.statusText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = sensor.statusColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        ),
                        maxLines = 1
                    )
                }
                Text(
                    text = "Ref: ${sensor.normalRange}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFF64748B),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp
                    ),
                    maxLines = 1
                )
            }
        }
    }
}
