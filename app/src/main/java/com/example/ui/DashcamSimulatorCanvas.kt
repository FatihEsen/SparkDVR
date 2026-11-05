package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.sin
import kotlin.math.cos

enum class CameraEnvironment(val label: String, val icon: String) {
    NIGHT_HIGHWAY("Gece Otoyol", "🌙"),
    RAINY_CITY("Yağmurlu Şehir", "🌧️"),
    BRIGHT_DAYLIGHT("Gündüz Sahil", "☀️")
}

@Composable
fun DashcamSimulatorCanvas(
    speed: Int,
    environment: CameraEnvironment,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "DashcamAnimation")
    
    // Road line movement animation tied to vehicle speed
    val roadAnimProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (20000 / (speed.coerceAtLeast(15))).coerceIn(200, 3000),
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "RoadSpeed"
    )

    // Windshield wiper animation for rain mode
    val wiperAngle by infiniteTransition.animateFloat(
        initialValue = -50f,
        targetValue = 50f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "WiperSwing"
    )

    // Oncoming traffic movement
    val trafficAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Traffic"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val horizonY = h * 0.42f
        val vanishingX = w * 0.5f

        when (environment) {
            CameraEnvironment.NIGHT_HIGHWAY -> {
                drawNightHighway(
                    w = w,
                    h = h,
                    horizonY = horizonY,
                    vanishingX = vanishingX,
                    roadProgress = roadAnimProgress,
                    trafficProgress = trafficAnim
                )
            }
            CameraEnvironment.RAINY_CITY -> {
                drawRainyCity(
                    w = w,
                    h = h,
                    horizonY = horizonY,
                    vanishingX = vanishingX,
                    roadProgress = roadAnimProgress,
                    wiperAngle = wiperAngle,
                    trafficProgress = trafficAnim
                )
            }
            CameraEnvironment.BRIGHT_DAYLIGHT -> {
                drawBrightDaylight(
                    w = w,
                    h = h,
                    horizonY = horizonY,
                    vanishingX = vanishingX,
                    roadProgress = roadAnimProgress,
                    trafficProgress = trafficAnim
                )
            }
        }
    }
}

private fun DrawScope.drawNightHighway(
    w: Float,
    h: Float,
    horizonY: Float,
    vanishingX: Float,
    roadProgress: Float,
    trafficProgress: Float
) {
    // 1. Sky & Horizon Gradient
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF07090E),
                Color(0xFF0F1420),
                Color(0xFF181F33)
            ),
            startY = 0f,
            endY = horizonY
        ),
        size = Size(w, horizonY)
    )

    // Stars / City lights in background
    for (i in 0..15) {
        val starX = (w * 0.05f) + (i * w * 0.06f)
        val starY = horizonY - 12f - (sin(i.toDouble()) * 30).toFloat()
        drawCircle(
            color = Color.Yellow.copy(alpha = 0.4f + (i % 3) * 0.2f),
            radius = 1.5f + (i % 2),
            center = Offset(starX, starY)
        )
    }

    // 2. Asphalt Ground
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF12151D),
                Color(0xFF0B0D12),
                Color(0xFF050608)
            ),
            startY = horizonY,
            endY = h
        ),
        topLeft = Offset(0f, horizonY),
        size = Size(w, h - horizonY)
    )

    // 3. Road Surface (Perspective Trapezoid)
    val roadPath = Path().apply {
        moveTo(vanishingX - 25f, horizonY)
        lineTo(vanishingX + 25f, horizonY)
        lineTo(w * 0.95f, h)
        lineTo(w * 0.05f, h)
        close()
    }
    drawPath(
        path = roadPath,
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF1E222D), Color(0xFF151821), Color(0xFF0E1016)),
            startY = horizonY,
            endY = h
        )
    )

    // Guardrails / Edge Yellow Lines
    val leftEdgePath = Path().apply {
        moveTo(vanishingX - 25f, horizonY)
        lineTo(w * 0.05f, h)
    }
    val rightEdgePath = Path().apply {
        moveTo(vanishingX + 25f, horizonY)
        lineTo(w * 0.95f, h)
    }
    drawPath(leftEdgePath, color = Color(0xFFFFCC00), style = Stroke(width = 4f))
    drawPath(rightEdgePath, color = Color.White.copy(alpha = 0.7f), style = Stroke(width = 4f))

    // 4. Moving Lane Dash Lines
    val numDashes = 7
    for (i in 0 until numDashes) {
        val t = ((i.toFloat() / numDashes) + roadProgress) % 1.0f
        val lineY = horizonY + (t * t) * (h - horizonY)
        val dashH = (3f + t * 28f)
        val leftX = vanishingX - (10f + t * (w * 0.28f))
        val rightX = vanishingX + (10f + t * (w * 0.28f))

        // Left Center Dash Line
        drawLine(
            color = Color.White.copy(alpha = 0.2f + t * 0.7f),
            start = Offset(leftX, lineY),
            end = Offset(leftX - (t * 8f), lineY + dashH),
            strokeWidth = 2f + t * 5f
        )
        // Right Center Dash Line
        drawLine(
            color = Color.White.copy(alpha = 0.2f + t * 0.7f),
            start = Offset(rightX, lineY),
            end = Offset(rightX + (t * 8f), lineY + dashH),
            strokeWidth = 2f + t * 5f
        )
    }

    // 5. Oncoming Traffic Headlights (Left Lane)
    val tOncoming = (trafficProgress + 0.3f) % 1.0f
    val oncomingY = horizonY + (tOncoming * tOncoming) * (h - horizonY)
    val oncomingX = vanishingX - (15f + tOncoming * (w * 0.32f))
    val lightSize = 4f + tOncoming * 18f

    drawCircle(
        color = Color(0xFFFFF2A1),
        radius = lightSize,
        center = Offset(oncomingX - lightSize * 1.2f, oncomingY)
    )
    drawCircle(
        color = Color(0xFFFFF2A1),
        radius = lightSize,
        center = Offset(oncomingX + lightSize * 1.2f, oncomingY)
    )
    // Headlight Beam Cone
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.4f * tOncoming), Color.Transparent),
            center = Offset(oncomingX, oncomingY),
            radius = lightSize * 4f
        ),
        radius = lightSize * 4f,
        center = Offset(oncomingX, oncomingY)
    )

    // 6. Own Vehicle Hood Rim Glow (Bottom edge of dashcam view)
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f), Color.Black),
            startY = h - 60f,
            endY = h
        ),
        topLeft = Offset(0f, h - 60f),
        size = Size(w, 60f)
    )
}

private fun DrawScope.drawRainyCity(
    w: Float,
    h: Float,
    horizonY: Float,
    vanishingX: Float,
    roadProgress: Float,
    wiperAngle: Float,
    trafficProgress: Float
) {
    // 1. Dark Rainy Sky & City Silhouette
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0C1017),
                Color(0xFF151D2A),
                Color(0xFF1D2636)
            ),
            startY = 0f,
            endY = horizonY
        ),
        size = Size(w, horizonY)
    )

    // City Building Blocks
    val buildingColors = listOf(Color(0xFF101520), Color(0xFF0D121C), Color(0xFF141C2B))
    for (i in 0..8) {
        val bW = w * 0.12f
        val bX = i * bW * 0.95f
        val bH = 60f + (sin(i * 1.7) * 40).toFloat()
        drawRect(
            color = buildingColors[i % 3],
            topLeft = Offset(bX, horizonY - bH),
            size = Size(bW, bH)
        )
        // Neon windows
        if (i % 2 == 0) {
            drawCircle(
                color = if (i % 4 == 0) Color(0xFF00E5FF).copy(alpha = 0.6f) else Color(0xFFFF9100).copy(alpha = 0.6f),
                radius = 3f,
                center = Offset(bX + bW * 0.5f, horizonY - bH * 0.6f)
            )
        }
    }

    // 2. Wet Reflective Asphalt Ground
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF151A24),
                Color(0xFF0E131C),
                Color(0xFF070A0F)
            ),
            startY = horizonY,
            endY = h
        ),
        topLeft = Offset(0f, horizonY),
        size = Size(w, h - horizonY)
    )

    // Road Surface
    val roadPath = Path().apply {
        moveTo(vanishingX - 25f, horizonY)
        lineTo(vanishingX + 25f, horizonY)
        lineTo(w * 0.95f, h)
        lineTo(w * 0.05f, h)
        close()
    }
    drawPath(
        path = roadPath,
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF1A212E), Color(0xFF111722), Color(0xFF0A0D14)),
            startY = horizonY,
            endY = h
        )
    )

    // Neon Reflection Strips on wet pavement
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF00E5FF).copy(alpha = 0.15f), Color.Transparent),
            startY = horizonY,
            endY = h
        ),
        topLeft = Offset(w * 0.25f, horizonY),
        size = Size(w * 0.08f, h - horizonY)
    )

    // Moving Lane Dash Lines
    for (i in 0 until 6) {
        val t = ((i.toFloat() / 6f) + roadProgress) % 1.0f
        val lineY = horizonY + (t * t) * (h - horizonY)
        val dashH = (4f + t * 24f)
        val leftX = vanishingX - (8f + t * (w * 0.25f))

        drawLine(
            color = Color(0xFF00E5FF).copy(alpha = 0.3f + t * 0.6f),
            start = Offset(leftX, lineY),
            end = Offset(leftX - (t * 6f), lineY + dashH),
            strokeWidth = 2f + t * 4f
        )
    }

    // Raindrops Streaks
    val rainCount = 40
    for (i in 0 until rainCount) {
        val rx = (i * 37f) % w
        val ry = (i * 53f) % h
        drawLine(
            color = Color.White.copy(alpha = 0.35f),
            start = Offset(rx, ry),
            end = Offset(rx - 4f, ry + 16f),
            strokeWidth = 1.2f
        )
    }

    // Windshield Wiper Blade Arc
    val wiperPivot = Offset(w * 0.5f, h + 20f)
    val wiperLength = h * 0.85f
    val rad = Math.toRadians((wiperAngle - 90).toDouble())
    val wiperEnd = Offset(
        x = wiperPivot.x + wiperLength * cos(rad).toFloat(),
        y = wiperPivot.y + wiperLength * sin(rad).toFloat()
    )

    drawLine(
        color = Color(0xFF2D323E),
        start = wiperPivot,
        end = wiperEnd,
        strokeWidth = 8f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = Color(0xFF8893A7).copy(alpha = 0.8f),
        start = wiperPivot,
        end = wiperEnd,
        strokeWidth = 3f,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawBrightDaylight(
    w: Float,
    h: Float,
    horizonY: Float,
    vanishingX: Float,
    roadProgress: Float,
    trafficProgress: Float
) {
    // 1. Sky & Bright Sun Glare
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF3880EC),
                Color(0xFF6AA7FF),
                Color(0xFFBBE0FF)
            ),
            startY = 0f,
            endY = horizonY
        ),
        size = Size(w, horizonY)
    )

    // Sun Glare
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White, Color(0xFFFFF7C2).copy(alpha = 0.6f), Color.Transparent),
            center = Offset(w * 0.75f, horizonY * 0.35f),
            radius = 120f
        ),
        radius = 120f,
        center = Offset(w * 0.75f, horizonY * 0.35f)
    )

    // Mountain silhouettes
    val mountainPath = Path().apply {
        moveTo(0f, horizonY)
        lineTo(w * 0.15f, horizonY - 45f)
        lineTo(w * 0.35f, horizonY - 20f)
        lineTo(w * 0.55f, horizonY - 60f)
        lineTo(w * 0.80f, horizonY - 30f)
        lineTo(w, horizonY - 50f)
        lineTo(w, horizonY)
        close()
    }
    drawPath(mountainPath, color = Color(0xFF4A6B82))

    // 2. Sunlit Asphalt Road Surface
    drawRect(
        color = Color(0xFF6E7882),
        topLeft = Offset(0f, horizonY),
        size = Size(w, h - horizonY)
    )

    val roadPath = Path().apply {
        moveTo(vanishingX - 30f, horizonY)
        lineTo(vanishingX + 30f, horizonY)
        lineTo(w * 0.96f, h)
        lineTo(w * 0.04f, h)
        close()
    }
    drawPath(
        path = roadPath,
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF525963), Color(0xFF3A4048), Color(0xFF2C3138)),
            startY = horizonY,
            endY = h
        )
    )

    // Yellow Center Lines & White Shoulders
    val leftEdgePath = Path().apply {
        moveTo(vanishingX - 30f, horizonY)
        lineTo(w * 0.04f, h)
    }
    val rightEdgePath = Path().apply {
        moveTo(vanishingX + 30f, horizonY)
        lineTo(w * 0.96f, h)
    }
    drawPath(leftEdgePath, color = Color(0xFFFFD600), style = Stroke(width = 5f))
    drawPath(rightEdgePath, color = Color.White, style = Stroke(width = 5f))

    // Moving Dashes
    for (i in 0 until 7) {
        val t = ((i.toFloat() / 7f) + roadProgress) % 1.0f
        val lineY = horizonY + (t * t) * (h - horizonY)
        val dashH = (5f + t * 30f)
        val centerDashX = vanishingX

        drawLine(
            color = Color(0xFFFFD600),
            start = Offset(centerDashX, lineY),
            end = Offset(centerDashX, lineY + dashH),
            strokeWidth = 3f + t * 6f
        )
    }

    // Hood reflections
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, Color(0xFF1E242C).copy(alpha = 0.8f), Color(0xFF0F1318)),
            startY = h - 50f,
            endY = h
        ),
        topLeft = Offset(0f, h - 50f),
        size = Size(w, 50f)
    )
}
