package com.ahnaf.cricketdrs

import android.util.Log
import android.view.ViewGroup
import android.hardware.camera2.CaptureRequest
import android.util.Range
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Delivery state machine ────────────────────────────────────────────────────
enum class DeliveryState {
    IDLE,        // waiting for ball to appear
    TRACKING,    // ball detected — accumulate trajectory
    PAD_IMPACT,  // ball disappeared near batsman — extrapolate + predict
    RESULT       // show LBW decision
}

// Normalised stump dimensions (width relative to screen)
private const val STUMP_WIDTH_NORM  = 0.08f
private const val STUMP_HEIGHT_NORM = 0.10f

// Ball must disappear in this y-band (middle of screen) to count as pad impact
private const val BATSMAN_ZONE_Y_UPPER = 0.25f
private const val BATSMAN_ZONE_Y_LOWER = 0.60f

// Consecutive missed frames in batsman zone = pad impact
private const val PAD_IMPACT_FRAMES = 3

@Composable
fun CameraScreen() {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope          = rememberCoroutineScope()

    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val ballDetector   = remember { BallDetector(context) }
    val ballTracker    = remember { BallTracker() }

    // ── State ──────────────────────────────────────────────────────────────────
    var latestDetection     by remember { mutableStateOf<DetectionResult?>(null) }
    var observedTrajectory  by remember { mutableStateOf<List<FloatArray>>(emptyList()) }
    var displayTrajectory   by remember { mutableStateOf<List<FloatArray>>(emptyList()) }
    var predictionResult    by remember { mutableStateOf<PredictionResult?>(null) }
    var deliveryState       by remember { mutableStateOf(DeliveryState.IDLE) }
    var missedInBatsmanZone by remember { mutableStateOf(0) }
    var lastTrackedY        by remember { mutableStateOf(1f) }

    // ── Stump placement ────────────────────────────────────────────────────────
    var stumpCentreNorm by remember { mutableStateOf<Offset?>(null) }
    var isPlacingStumps by remember { mutableStateOf(false) }

    fun buildStumpRegion(normCentre: Offset?) = StumpRegion(
        x      = normCentre?.x ?: 0.47f,
        y      = (normCentre?.y ?: 0.38f) - STUMP_HEIGHT_NORM / 2f,
        width  = STUMP_WIDTH_NORM,
        height = STUMP_HEIGHT_NORM
    )

    val stumpPredictor = remember { StumpPredictor(buildStumpRegion(null)) }

    // ── Helpers ────────────────────────────────────────────────────────────────
    fun resetDelivery() {
        ballTracker.reset()
        latestDetection     = null
        observedTrajectory  = emptyList()
        displayTrajectory   = emptyList()
        predictionResult    = null
        deliveryState       = DeliveryState.IDLE
        missedInBatsmanZone = 0
        lastTrackedY        = 1f
    }

    fun triggerPrediction() {
        deliveryState = DeliveryState.PAD_IMPACT
        val arc      = ballTracker.extrapolate(40)
        val fullTraj = observedTrajectory + arc
        displayTrajectory = fullTraj

        val region = buildStumpRegion(stumpCentreNorm)
        val result = StumpPredictor(region).predict(fullTraj)
        predictionResult  = result
        deliveryState     = DeliveryState.RESULT
        Log.d("CricketDRS", "LBW Decision: ${result.decision}, conf=${result.confidence}")
    }

    DisposableEffect(Unit) {
        onDispose {
            ballDetector.close()
            cameraExecutor.shutdown()
        }
    }

    // ── UI ─────────────────────────────────────────────────────────────────────
    Column(modifier = Modifier.fillMaxSize()) {

        // Buttons row
        Row(modifier = Modifier.padding(8.dp)) {

            Button(
                onClick = { isPlacingStumps = !isPlacingStumps },
                modifier = Modifier.padding(end = 6.dp)
            ) {
                Text(if (isPlacingStumps) "✋ Tap screen to place" else "📍 Place Stumps")
            }

            Button(
                onClick = { resetDelivery() },
                modifier = Modifier.padding(end = 6.dp)
            ) { Text("🔄 Reset") }

            Button(onClick = {
                resetDelivery()
                scope.launch {
                    deliveryState = DeliveryState.TRACKING

                    for (frame in 0..20) {
                        val x = 0.47f + frame * 0.001f
                        val y = 0.05f + frame * 0.042f
                        val det = DetectionResult(
                            x = x - 0.025f, y = y - 0.025f,
                            width = 0.05f, height = 0.05f, confidence = 0.9f
                        )
                        val smoothed = ballTracker.update(det)
                        if (smoothed != null) {
                            observedTrajectory = observedTrajectory + floatArrayOf(smoothed[0], smoothed[1])
                            lastTrackedY = smoothed[1]
                        }
                        latestDetection   = det
                        displayTrajectory = ballTracker.extrapolate(30)
                        delay(50L)
                    }

                    for (frame in 0..20) {
                        val x = 0.47f + frame * 0.001f
                        val y = 0.90f - frame * 0.027f
                        val det = DetectionResult(
                            x = x - 0.025f, y = y - 0.025f,
                            width = 0.05f, height = 0.05f, confidence = 0.9f
                        )
                        val smoothed = ballTracker.update(det)
                        if (smoothed != null) {
                            observedTrajectory = observedTrajectory + floatArrayOf(smoothed[0], smoothed[1])
                            lastTrackedY = smoothed[1]
                        }
                        latestDetection   = det
                        displayTrajectory = ballTracker.extrapolate(30)
                        delay(50L)
                    }

                    latestDetection = null
                    triggerPrediction()
                }
            }) { Text("🎳 Simulate") }
        }

        Box(modifier = Modifier.weight(1f)) {

            // ── Camera feed ──────────────────────────────────────────────────
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also { analysis ->
                                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                        try {
                                            if (deliveryState == DeliveryState.RESULT ||
                                                deliveryState == DeliveryState.PAD_IMPACT) {
                                                imageProxy.close()
                                                return@setAnalyzer
                                            }

                                            val bitmap    = imageProxy.toBitmap()
                                            val detection = ballDetector.detectBall(bitmap)

                                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                                if (detection != null) {
                                                    missedInBatsmanZone = 0
                                                    val smoothed = ballTracker.update(detection)

                                                    if (smoothed != null) {
                                                        lastTrackedY = smoothed[1]
                                                        if (deliveryState == DeliveryState.IDLE)
                                                            deliveryState = DeliveryState.TRACKING

                                                        observedTrajectory = observedTrajectory +
                                                                floatArrayOf(smoothed[0], smoothed[1])
                                                    }

                                                    latestDetection   = detection
                                                    displayTrajectory = ballTracker.extrapolate(30)

                                                } else {
                                                    ballTracker.update(null)
                                                    latestDetection = null

                                                    if (deliveryState == DeliveryState.TRACKING &&
                                                        lastTrackedY in BATSMAN_ZONE_Y_UPPER..BATSMAN_ZONE_Y_LOWER) {
                                                        missedInBatsmanZone++
                                                        if (missedInBatsmanZone >= PAD_IMPACT_FRAMES) {
                                                            triggerPrediction()
                                                        }
                                                    } else {
                                                        displayTrajectory = ballTracker.extrapolate(30)
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        } finally {
                                            imageProxy.close()
                                        }
                                    }
                                }

                            cameraProvider.unbindAll()

                            // ── Bind camera ───────────────────────────────────────────────
                            @Suppress("UnsafeOptInUsageError")
                            val camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis
                            )

                            // ── Lock to fast shutter to reduce motion blur ─────────────────
                            @Suppress("UnsafeOptInUsageError")
                            val camera2Control = Camera2CameraControl.from(camera.cameraControl)
                            @Suppress("UnsafeOptInUsageError")
                            camera2Control.addCaptureRequestOptions(
                                CaptureRequestOptions.Builder()
                                    .setCaptureRequestOption(
                                        CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                                        Range(30, 60)
                                    )
                                    .setCaptureRequestOption(
                                        CaptureRequest.SENSOR_SENSITIVITY,
                                        800
                                    )
                                    .build()
                            )

                        } catch (e: Exception) { e.printStackTrace() }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // ── Tap-to-place stumps overlay ──────────────────────────────────
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(isPlacingStumps) {
                        if (isPlacingStumps) {
                            detectTapGestures { tapOffset ->
                                stumpCentreNorm = Offset(
                                    tapOffset.x / size.width,
                                    tapOffset.y / size.height
                                )
                                isPlacingStumps = false
                                Log.d("CricketDRS", "Stumps placed at $stumpCentreNorm")
                            }
                        }
                    }
            ) {
                val sc = stumpCentreNorm
                val stumpPx = if (sc != null) {
                    Offset(sc.x * size.width, sc.y * size.height)
                } else {
                    Offset(0.47f * size.width, 0.38f * size.height)
                }

                val sw = STUMP_WIDTH_NORM  * size.width
                val sh = STUMP_HEIGHT_NORM * size.height
                val stumpColor = if (isPlacingStumps) Color.Cyan else Color.White

                drawRect(
                    color    = stumpColor,
                    topLeft  = Offset(stumpPx.x - sw / 2f, stumpPx.y - sh / 2f),
                    size     = Size(sw, sh),
                    style    = Stroke(width = 6f)
                )

                latestDetection?.let { drawBox(it) }

                val obsSize = observedTrajectory.size
                displayTrajectory.forEachIndexed { index, point ->
                    val isExtrapolated = index >= obsSize
                    drawCircle(
                        color  = if (isExtrapolated) Color.Cyan else Color.Yellow,
                        radius = if (isExtrapolated) 5f else 7f,
                        center = Offset(point[0] * size.width, point[1] * size.height)
                    )
                }

                predictionResult?.let { result ->
                    if (result.impactX != null && result.impactY != null) {
                        drawCircle(
                            color = when (result.decision) {
                                LBWDecision.HIT               -> Color.Red
                                LBWDecision.UMPIRES_CALL      -> Color.Yellow
                                LBWDecision.MISS              -> Color.Green
                                LBWDecision.INSUFFICIENT_DATA -> Color.Gray
                            },
                            radius = 20f,
                            center = Offset(result.impactX * size.width, result.impactY * size.height)
                        )
                    }
                }
            }

            // ── State label ───────────────────────────────────────────────────
            Text(
                text = when {
                    isPlacingStumps                           -> "👆 Tap to place stumps"
                    stumpCentreNorm == null                   -> "⚠️ Stumps not placed — using default"
                    deliveryState == DeliveryState.IDLE       -> "⚪ Waiting for delivery..."
                    deliveryState == DeliveryState.TRACKING   -> "🟡 Tracking ball"
                    deliveryState == DeliveryState.PAD_IMPACT -> "🟠 Analysing..."
                    else -> ""
                },
                color    = Color.White,
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
            )

            // ── LBW decision ──────────────────────────────────────────────────
            predictionResult?.let { result ->
                val label = when (result.decision) {
                    LBWDecision.HIT               -> "🔴 OUT — HITTING STUMPS"
                    LBWDecision.UMPIRES_CALL      -> "🟡 UMPIRE'S CALL"
                    LBWDecision.MISS              -> "🟢 NOT OUT — MISSING STUMPS"
                    LBWDecision.INSUFFICIENT_DATA -> ""
                }
                if (label.isNotEmpty()) {
                    Text(
                        text     = label,
                        color    = Color.White,
                        fontSize = 20.sp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp)
                    )
                }
            }
        }
    }
}

fun DrawScope.drawBox(detection: DetectionResult) {
    drawRect(
        color    = Color.Red,
        topLeft  = Offset(detection.x * size.width, detection.y * size.height),
        size     = Size(detection.width * size.width, detection.height * size.height),
        style    = Stroke(width = 8f)
    )
}