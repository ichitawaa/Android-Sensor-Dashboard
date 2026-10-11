package com.example.testsensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MultiSensorApp()
            }
        }
    }
}

/**
 * Aplikasi Multi-Sensor Utama:
 * Memanfaatkan 3 kategori sensor Android:
 * 1. Environment: Light Sensor (Sensor.TYPE_LIGHT)
 * 2. Position/Motion: Gravity Sensor (Sensor.TYPE_GRAVITY) / Accelerometer (Sensor.TYPE_ACCELEROMETER)
 * 3. Heading/Orientation: Magnetic Field Sensor (Sensor.TYPE_MAGNETIC_FIELD)
 */
@Composable
fun MultiSensorApp() {
    val context = LocalContext.current
    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 1. State untuk Light Sensor (Kecerahan dalam lux)
    var luxValue by remember { mutableFloatStateOf(100f) }
    var hasLightSensor by remember { mutableStateOf(true) }

    // 2. State untuk Motion/Gravity Sensor (Kemiringan X & Y)
    var tiltX by remember { mutableFloatStateOf(0f) }
    var tiltY by remember { mutableFloatStateOf(0f) }
    var hasMotionSensor by remember { mutableStateOf(true) }
    var motionSensorName by remember { mutableStateOf("Menunggu Sensor...") }

    // 3. State untuk Magnetic Field Sensor (Kompas & Orientasi)
    var azimuthDegrees by remember { mutableFloatStateOf(0f) }
    var hasMagneticSensor by remember { mutableStateOf(true) }

    // Array sementara untuk perhitungan rotasi matriks kompas
    val gravityValues = remember { FloatArray(3) }
    val magneticValues = remember { FloatArray(3) }
    var hasGravityData by remember { mutableStateOf(false) }
    var hasMagneticData by remember { mutableStateOf(false) }

    // State untuk Posisi Bola 2D di Canvas
    val ballSize = 44.dp
    val ballRadiusPx = with(density) { (ballSize / 2).toPx() }
    var ballX by remember { mutableFloatStateOf(0f) }
    var ballY by remember { mutableFloatStateOf(0f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // Penentuan Tema Berdasarkan Sensor Cahaya:
    // Mode Malam jika kecerahan < 20 lux, Mode Siang jika >= 20 lux
    val isDarkMode = luxValue < 20f

    // Animasi perubahan warna latar belakang UI agar responsif dan halus
    val backgroundColor by animateColorAsState(
        targetValue = if (isDarkMode) Color(0xFF12131C) else Color(0xFFF5F7FA),
        animationSpec = tween(durationMillis = 500),
        label = "backgroundColorAnim"
    )

    val cardBackgroundColor by animateColorAsState(
        targetValue = if (isDarkMode) Color(0xFF1E202E) else Color(0xFFFFFFFF),
        animationSpec = tween(durationMillis = 500),
        label = "cardBackgroundColorAnim"
    )

    val cardBorderColor by animateColorAsState(
        targetValue = if (isDarkMode) Color(0xFF2C2F45) else Color(0xFFE2E8F0),
        animationSpec = tween(durationMillis = 500),
        label = "cardBorderColorAnim"
    )

    val primaryTextColor by animateColorAsState(
        targetValue = if (isDarkMode) Color(0xFFF1F5F9) else Color(0xFF0F172A),
        animationSpec = tween(durationMillis = 500),
        label = "primaryTextColorAnim"
    )

    val secondaryTextColor by animateColorAsState(
        targetValue = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B),
        animationSpec = tween(durationMillis = 500),
        label = "secondaryTextColorAnim"
    )

    // --------------------------------------------------------------------------
    // MANAJEMEN LIFECYCLE 3 SENSOR (LIGHT, MOTION, MAGNETIC FIELD)
    // Mendaftarkan sensor di ON_RESUME dan melepasnya di ON_PAUSE / onDispose
    // --------------------------------------------------------------------------
    DisposableEffect(lifecycleOwner) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // 1. Environment Sensor: Light Sensor
        val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        hasLightSensor = (lightSensor != null)

        // 2. Position/Motion Sensor: Utamakan Gravity Sensor, Fallback ke Accelerometer
        val gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        val accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val motionSensor = gravitySensor ?: accelerometerSensor

        hasMotionSensor = (motionSensor != null)
        motionSensorName = when {
            gravitySensor != null -> "Gravity Sensor"
            accelerometerSensor != null -> "Accelerometer (Fallback)"
            else -> "Tidak Tersedia"
        }

        // 3. Orientation Sensor: Magnetic Field Sensor
        val magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        hasMagneticSensor = (magneticSensor != null)

        // Listener 3 Sensor
        val sensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let { e ->
                    when (e.sensor.type) {
                        Sensor.TYPE_LIGHT -> {
                            // Update nilai kecerahan cahaya (lux)
                            luxValue = e.values[0]
                        }
                        Sensor.TYPE_GRAVITY, Sensor.TYPE_ACCELEROMETER -> {
                            // Update nilai kemiringan sumbu X dan Y
                            tiltX = e.values[0]
                            tiltY = e.values[1]

                            // Simpan data gravitasi/akselerometer untuk perhitungan azimuth kompas
                            System.arraycopy(e.values, 0, gravityValues, 0, 3)
                            hasGravityData = true

                            // Sensitivitas pergerakan bola
                            val sensitivitas = 3.2f

                            // Hitung posisi bola berikutnya berdasarkan kemiringan HP
                            val nextX = ballX - (tiltX * sensitivitas)
                            val nextY = ballY + (tiltY * sensitivitas)

                            // Pastikan bola tidak keluar dari batas area Canvas (clamping)
                            if (containerSize.width > 0 && containerSize.height > 0) {
                                val minX = ballRadiusPx
                                val maxX = containerSize.width.toFloat() - ballRadiusPx
                                val minY = ballRadiusPx
                                val maxY = containerSize.height.toFloat() - ballRadiusPx

                                ballX = nextX.coerceIn(minX, maxX)
                                ballY = nextY.coerceIn(minY, maxY)
                            } else {
                                ballX = nextX
                                ballY = nextY
                            }
                        }
                        Sensor.TYPE_MAGNETIC_FIELD -> {
                            // Simpan data medan magnet untuk perhitungan azimuth kompas
                            System.arraycopy(e.values, 0, magneticValues, 0, 3)
                            hasMagneticData = true
                        }
                    }

                    // Hitung Orientasi / Azimuth Kompas jika data gravitasi dan magnetik sudah lengkap
                    if (hasGravityData && hasMagneticData) {
                        val rotationMatrix = FloatArray(9)
                        val orientationValues = FloatArray(3)

                        val success = SensorManager.getRotationMatrix(
                            rotationMatrix,
                            null,
                            gravityValues,
                            magneticValues
                        )

                        if (success) {
                            SensorManager.getOrientation(rotationMatrix, orientationValues)
                            val azimuthInRadians = orientationValues[0]
                            var deg = Math.toDegrees(azimuthInRadians.toDouble()).toFloat()
                            if (deg < 0) {
                                deg += 360f
                            }
                            azimuthDegrees = deg
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        // Observer Lifecycle: Register saat ON_RESUME, Unregister saat ON_PAUSE
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    lightSensor?.let {
                        sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI)
                    }
                    motionSensor?.let {
                        sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME)
                    }
                    magneticSensor?.let {
                        sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI)
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    sensorManager.unregisterListener(sensorListener)
                }
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            sensorManager.unregisterListener(sensorListener)
        }
    }

    // --------------------------------------------------------------------------
    // TAMPILAN ANTARMUKA (UI)
    // --------------------------------------------------------------------------
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            // Header Utama & Status Sensor
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Triple Sensor Dashboard",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Light, Motion & Compass Interactive App",
                        fontSize = 11.sp,
                        color = secondaryTextColor
                    )
                }

                // Indikator Mode Tema (Malam / Siang)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (isDarkMode) Color(0xFF312E81) else Color(0xFFFEF3C7))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isDarkMode) "🌙 Malam" else "☀️ Siang",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDarkMode) Color(0xFFA5B4FC) else Color(0xFFD97706)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ------------------------------------------------------------------
            // BARIS KARTU 1 & 2 (LIGHT & MOTION)
            // ------------------------------------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Kartu 1: Environment (Light Sensor)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(cardBackgroundColor)
                        .border(1.dp, cardBorderColor, RoundedCornerShape(18.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(if (isDarkMode) Color(0xFF3730A3) else Color(0xFFFEF3C7)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isDarkMode) "🌙" else "💡",
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Light Sensor",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = secondaryTextColor
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (hasLightSensor) {
                            Text(
                                text = "${luxValue.toInt()} lx",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryTextColor
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isDarkMode) "Dark (< 20lx)" else "Light (≥ 20lx)",
                                fontSize = 10.sp,
                                color = secondaryTextColor
                            )
                        } else {
                            Text(
                                text = "Tidak Ada Sensor",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444)
                            )
                        }
                    }
                }

                // Kartu 2: Position/Motion (Gravity / Accelerometer)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(cardBackgroundColor)
                        .border(1.dp, cardBorderColor, RoundedCornerShape(18.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFDBEAFE)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "🎯",
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Motion Sensor",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = secondaryTextColor
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (hasMotionSensor) {
                            Text(
                                text = "X: %.1f | Y: %.1f".format(tiltX, tiltY),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryTextColor
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = motionSensorName,
                                fontSize = 10.sp,
                                color = secondaryTextColor
                            )
                        } else {
                            Text(
                                text = "Tidak Ada Sensor",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ------------------------------------------------------------------
            // KARTU 3: COMPASS & HEADING (MAGNETIC FIELD SENSOR)
            // ------------------------------------------------------------------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(cardBackgroundColor)
                    .border(1.dp, cardBorderColor, RoundedCornerShape(18.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Indikator Kompas Visual (Jarum Kompas Berputar Sesuai Azimuth)
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(if (isDarkMode) Color(0xFF2E1065) else Color(0xFFF3E8FF))
                            .border(1.dp, if (isDarkMode) Color(0xFF7C3AED) else Color(0xFFA855F7), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "▲",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEF4444),
                            modifier = Modifier.graphicsLayer {
                                rotationZ = -azimuthDegrees
                            }
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Compass & Heading",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = secondaryTextColor
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "🧭 Magnetic Field",
                                fontSize = 10.sp,
                                color = Color(0xFF8B5CF6)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        if (hasMagneticSensor) {
                            Text(
                                text = "${azimuthDegrees.toInt()}°  ${getCardinalDirection(azimuthDegrees)}",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryTextColor
                            )
                        } else {
                            Text(
                                text = "Magnetic Sensor Tidak Tersedia",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ------------------------------------------------------------------
            // PLAYGROUND CANVAS INTERAKTIF BOLA 2D
            // ------------------------------------------------------------------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(cardBackgroundColor)
                    .border(1.dp, cardBorderColor, RoundedCornerShape(22.dp))
                    .onGloballyPositioned { coordinates ->
                        val newSize = coordinates.size
                        if (containerSize != newSize) {
                            containerSize = newSize
                            // Posisikan bola di tengah-tengah canvas saat pertama kali dimuat
                            if (ballX == 0f && ballY == 0f) {
                                ballX = newSize.width / 2f
                                ballY = newSize.height / 2f
                            }
                        }
                    }
            ) {
                // Gambar Garis Target / Lingkaran Konsentris di Canvas Latar Belakang
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f

                    val circleColor1 = if (isDarkMode) Color(0xFF25283B) else Color(0xFFE2E8F0)
                    val circleColor2 = if (isDarkMode) Color(0xFF1E2132) else Color(0xFFF1F5F9)

                    drawCircle(
                        color = circleColor1,
                        radius = 70.dp.toPx(),
                        center = Offset(centerX, centerY)
                    )
                    drawCircle(
                        color = circleColor2,
                        radius = 40.dp.toPx(),
                        center = Offset(centerX, centerY)
                    )
                    drawCircle(
                        color = if (isDarkMode) Color(0xFF4F46E5) else Color(0xFF3B82F6),
                        radius = 4.dp.toPx(),
                        center = Offset(centerX, centerY)
                    )
                }

                // Objek Bola 2D yang Digerakkan secara Halus Menggunakan graphicsLayer
                Box(
                    modifier = Modifier
                        .size(ballSize)
                        .graphicsLayer {
                            translationX = ballX - ballRadiusPx
                            translationY = ballY - ballRadiusPx
                        }
                ) {
                    // Efek Glow / Shadow pada Bola
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(8.dp)
                            .background(
                                color = if (isDarkMode) Color(0xFF818CF8).copy(alpha = 0.4f)
                                else Color(0xFF3B82F6).copy(alpha = 0.3f),
                                shape = CircleShape
                            )
                    )

                    // Bola Utama dengan Gradien Radial
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = if (isDarkMode) listOf(
                                        Color(0xFFC7D2FE),
                                        Color(0xFF818CF8),
                                        Color(0xFF4338CA)
                                    ) else listOf(
                                        Color(0xFFFFFFFF),
                                        Color(0xFF60A5FA),
                                        Color(0xFF2563EB)
                                    )
                                )
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                    )
                }

                // Teks Panduan & Info Posisi Real-Time Bola di Bagian Bawah Canvas
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Posisi Bola: (X: ${ballX.toInt()} px, Y: ${ballY.toInt()} px)",
                        color = primaryTextColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Miringkan HP untuk menggerakkan bola",
                        color = secondaryTextColor,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ------------------------------------------------------------------
            // FOOTER/INFO PENJELASAN INTEGRASI SENSOR & LIFECYCLE
            // ------------------------------------------------------------------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(cardBackgroundColor)
                    .border(1.dp, cardBorderColor, RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "⚡",
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Status 3 Sensor & Lifecycle",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryTextColor
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = "Light, Motion & Magnetic sensors aktif di onResume dan di-pause di onPause.",
                            fontSize = 10.sp,
                            color = secondaryTextColor
                        )
                    }
                }
            }
        }
    }
}

/**
 * Konversi Sudut Azimuth (0° - 360°) ke Arah Mata Angin (Cardinal Direction)
 */
private fun getCardinalDirection(azimuth: Float): String {
    return when (azimuth) {
        in 22.5f..67.5f -> "Timur Laut (NE)"
        in 67.5f..112.5f -> "Timur (E)"
        in 112.5f..157.5f -> "Tenggara (SE)"
        in 157.5f..202.5f -> "Selatan (S)"
        in 202.5f..247.5f -> "Barat Daya (SW)"
        in 247.5f..292.5f -> "Barat (W)"
        in 292.5f..337.5f -> "Barat Laut (NW)"
        else -> "Utara (N)"
    }
}