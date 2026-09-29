package com.jhani.measurear.presentation

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import androidx.annotation.DrawableRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jhani.measurear.R
import com.jhani.measurear.ar.ARSessionManager
import com.jhani.measurear.capture.HistoryRecord
import com.jhani.measurear.capture.HistoryStore
import com.jhani.measurear.capture.HistoryScreen
import com.jhani.measurear.ar.ARSessionState
import com.jhani.measurear.ar.ARSurfaceView
import com.jhani.measurear.ar.MeasureAction
import com.jhani.measurear.level.LevelScreen
import com.jhani.measurear.measurement.MeasureUnit
import com.jhani.measurear.measurement.MeasureMode
import com.jhani.measurear.measurement.ShapeResultUi
import com.jhani.measurear.measurement.ResultValue
import com.jhani.measurear.measurement.formatValue
import com.jhani.measurear.measurement.ReticleState
import com.jhani.measurear.measurement.SnapAxis
import com.jhani.measurear.measurement.StraightenMode
import com.jhani.measurear.measurement.formatArea
import com.jhani.measurear.measurement.formatDistance
import com.jhani.measurear.measurement.formatLength
import com.jhani.measurear.measurement.formatSummaries
import com.jhani.measurear.measurement.formatRange
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ARScreen(
    modifier: Modifier = Modifier,
    /** Mode requested by a launcher shortcut (null = none). */
    requestedMode: MeasureMode? = null,
    onModeRequestHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    val sessionManager = remember { ARSessionManager(context) }
    val sessionState by sessionManager.sessionState.collectAsState()
    val ui by sessionManager.uiState.collectAsState()

    // The branded loader stays up until the camera delivers frames (and at least briefly, so
    // it never just flashes), then fades out over the live camera
    var loaderMinShown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1200)
        loaderMinShown = true
    }
    val showLoader = !(ui.cameraReady && loaderMinShown)
    var unit by rememberSaveable { mutableStateOf(MeasureUnit.METRIC) }
    LaunchedEffect(unit) { sessionManager.imperial = unit == MeasureUnit.IMPERIAL }
    var tool by rememberSaveable { mutableStateOf(Tool.MEASURE) }
    var showGrid by rememberSaveable { mutableStateOf(true) }

    // The AR camera view, for photos (a window copy alone leaves the camera area black)
    var cameraView by remember { mutableStateOf<ARSurfaceView?>(null) }
    LaunchedEffect(showGrid) { sessionManager.showGrid = showGrid }
    var debugOn by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(debugOn) { sessionManager.debugView = debugOn }
    var mode by rememberSaveable { mutableStateOf(MeasureMode.LINE) }
    LaunchedEffect(mode) { sessionManager.mode = mode }
    var showModes by remember { mutableStateOf(false) }
    LaunchedEffect(requestedMode) {
        if (requestedMode != null) {
            mode = requestedMode
            onModeRequestHandled()
        }
    }
    var showPhoneHeight by remember { mutableStateOf(false) }
    // Will it fit?: chosen box size, and the size picker
    var fitSpec by remember { mutableStateOf(com.jhani.measurear.measurement.BoxSpec.PRESETS[0]) }
    LaunchedEffect(fitSpec) { sessionManager.fitSpec = fitSpec }
    var showFitSize by remember { mutableStateOf(false) }
    LaunchedEffect(mode) { if (mode == MeasureMode.FIT) showFitSize = true }
    // Calibration factor for this session, and a finished calibration measurement to confirm
    var scale by rememberSaveable { mutableStateOf(1f) }
    LaunchedEffect(scale) { sessionManager.scale = scale }
    var calibrationSample by remember { mutableStateOf<ARSessionManager.CalibrationSample?>(null) }
    var modeBeforeCalibration by rememberSaveable { mutableStateOf(MeasureMode.LINE) }
    LaunchedEffect(sessionManager) {
        sessionManager.calibration.collect { calibrationSample = it }
    }
    var farPhoneHeight by rememberSaveable { mutableStateOf(1.45f) }
    LaunchedEffect(farPhoneHeight) { sessionManager.farPhoneHeight = farPhoneHeight }
    var magnifierOn by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(magnifierOn) { sessionManager.magnifierEnabled = magnifierOn }
    // Off by default: depth noise on this class of phone hid grid dots on the surface itself
    var gridOcclusion by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(gridOcclusion) { sessionManager.gridOcclusion = gridOcclusion }
    val torchOn by sessionManager.torchOn.collectAsState()
    var showHistory by rememberSaveable { mutableStateOf(false) }

    // Auto-save every finished line / area to History, with a photo of that moment
    val currentUnit by rememberUpdatedState(unit)
    LaunchedEffect(sessionManager) {
        sessionManager.completed.collect { done ->
            delay(250) // let the new line and label draw before the photo
            val bitmap = activity?.let { HistoryStore.captureScreen(it, cameraView) }
            val value = done.summary.let {
                formatValue(ResultValue(it.label ?: "", it.value, it.kind), it.isEstimate, currentUnit)
            }
            HistoryStore.add(context, "${done.label} · $value", listOf(done.summary), bitmap)
        }
    }

    // Volume keys stamp points while measuring
    DisposableEffect(tool, showHistory) {
        StampKeys.enabled = tool == Tool.MEASURE && !showHistory
        onDispose { StampKeys.enabled = false }
    }
    LaunchedEffect(sessionManager) {
        StampKeys.presses.collect { pressedAt ->
            sessionManager.requestAction(MeasureAction.AddPoint(pressedAt))
        }
    }
    var selectedLine by remember { mutableStateOf<Int?>(null) }
    var lastCapture by remember { mutableStateOf<HistoryRecord?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(lastCapture) {
        if (lastCapture != null) {
            delay(4000)
            lastCapture = null
        }
    }
    // A deleted line (undo, clear…) closes its action sheet
    LaunchedEffect(ui.lineCount) {
        if ((selectedLine ?: -1) >= ui.lineCount) selectedLine = null
    }

    val currentTool by rememberUpdatedState(tool)

    // The Level tool doesn't use the camera: pause AR while it's open, resume on return
    var toolInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(tool) {
        if (!toolInitialized) {
            toolInitialized = true
            return@LaunchedEffect
        }
        if (tool == Tool.LEVEL) {
            sessionManager.onPause()
        } else if (activity != null && sessionManager.hasCameraPermission()) {
            sessionManager.onResume(activity)
        }
    }

    var hint by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(sessionManager) {
        sessionManager.hints.collect { message ->
            hint = message
        }
    }
    LaunchedEffect(hint) {
        if (hint != null) {
            delay(2500)
            hint = null
        }
    }

    fun capture() {
        val host = activity ?: return
        selectedLine = null
        scope.launch {
            // Let the sheet disappear before copying the window
            delay(100)
            val items = ui.summaries
            if (items.isEmpty()) {
                hint = "Measure something first, then save"
                return@launch
            }
            val bitmap = HistoryStore.captureScreen(host, cameraView)
            if (bitmap == null) hint = "Saved without a photo (screen capture failed)"
            lastCapture = HistoryStore.add(context, "Snapshot · ${items.size} measurements", items, bitmap)
        }
    }

    // Only auto-prompt for the camera once; the permission dialog itself pauses/resumes the
    // activity, so prompting on every ON_RESUME would loop forever after a denial.
    var permissionPrompted by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        if (activity != null) {
            sessionManager.onResume(activity)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (activity != null) {
                        if (currentTool == Tool.LEVEL && sessionManager.hasCameraPermission()) {
                            // Camera stays off while the Level tool is open
                        } else if (sessionManager.hasCameraPermission() || permissionPrompted) {
                            sessionManager.onResume(activity)
                        } else {
                            permissionPrompted = true
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    sessionManager.onPause()
                }
                Lifecycle.Event.ON_DESTROY -> {
                    sessionManager.onDestroy()
                }
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            sessionManager.onDestroy()
        }
    }

    if (showHistory) {
        HistoryScreen(unit = unit, onClose = { showHistory = false }, modifier = modifier)
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (val state = sessionState) {
            is ARSessionState.SessionReady -> if (tool != Tool.MEASURE) {
                if (tool == Tool.LEVEL) LevelScreen() else com.jhani.measurear.level.CompassScreen()
                ToolTabs(
                    selected = tool,
                    onSelect = { tool = it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 20.dp)
                )
            } else {
                // Camera feed (GL); measuring runs on its render thread
                AndroidView(
                    factory = { ctx ->
                        ARSurfaceView(ctx, sessionManager).also { cameraView = it }
                    },
                    modifier = Modifier.fillMaxSize(),
                    onRelease = { surfaceView ->
                        surfaceView.onPause()
                        if (cameraView === surfaceView) cameraView = null
                    }
                )

                // Lines, points, labels and reticle
                MeasureOverlay(
                    ui = ui,
                    unit = unit,
                    onLineTap = { selectedLine = it },
                    onScreenTap = { x, y -> sessionManager.requestAction(MeasureAction.AddPointAt(x, y)) },
                    onDragStart = { x, y -> sessionManager.requestAction(MeasureAction.DragStart(x, y)) },
                    onDrag = { x, y -> sessionManager.requestAction(MeasureAction.DragMove(x, y)) },
                    onDragEnd = { sessionManager.requestAction(MeasureAction.DragEnd) }
                )

                // Scanning guide whenever ARCore has no surface to measure on
                if (ui.surfaceCount == 0 && !ui.hasPendingPoint) {
                    OnboardingHint(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(top = 260.dp)
                    )
                }

                val guidanceText = when {
                    !ui.isTracking -> ui.trackingMessage ?: "Let's get started — move the phone slowly"
                    ui.mode == MeasureMode.FAR && ui.draftCount == 0 ->
                        "Aim at the base of the building, where it meets the ground"
                    ui.mode == MeasureMode.FAR -> "Now aim at the very top and stamp"
                    ui.surfaceCount == 0 -> "Scanning with you — sweep slowly over a table or floor"
                    ui.reticle == ReticleState.SEARCHING -> "Nothing under the crosshair — step back ~50 cm and aim at the teal dots"
                    ui.mode != MeasureMode.LINE && ui.reticle == ReticleState.ESTIMATE ->
                        "Approximate here (≈) — on the teal dots it's exact"
                    ui.mode != MeasureMode.LINE -> ui.mode.howTo
                    (ui.targetMeters ?: 1f) < 0.2f -> "Too close — step back about 50 cm"
                    ui.snapAxis == SnapAxis.VERTICAL && ui.pendingIsTop ->
                        "Locked vertical — stamp exactly where it meets the table"
                    ui.snapAxis == SnapAxis.VERTICAL -> "Locked vertical — stamp the top point"
                    ui.reticle == ReticleState.ESTIMATE && ui.reticleAmbiguous -> "Edge — aim slightly inside the object"
                    ui.reticle == ReticleState.ESTIMATE && !ui.reticleReliable ->
                        "Approximate here (≈) — on the teal dots it's exact"
                    ui.hasPendingPoint -> "Nice — now stamp or tap the end point"
                    ui.reticle == ReticleState.SNAPPED -> "Stamp to continue from this point"
                    else -> "Ready — stamp, or tap a spot on the teal dots"
                }

                // Hold each message briefly so rapid state flicker doesn't overlap cross-fades
                var shownGuidance by remember { mutableStateOf(guidanceText) }
                LaunchedEffect(guidanceText) {
                    delay(400)
                    shownGuidance = guidanceText
                }

                // Debug stats panel (long-press the title to toggle)
                ui.debugText?.let { text ->
                    Text(
                        text = text,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 12.dp, top = 40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(10.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFFD60A),
                        fontFamily = FontFamily.Monospace
                    )
                }

                HudTopBar(
                    aimError = if (ui.mode == MeasureMode.FAR) null else ui.targetMeters?.let {
                        com.jhani.measurear.measurement.Uncertainty.pointError(it, ui.reticleReliable)
                    },
                    aimOnSurface = ui.reticleReliable,
                    mode = mode,
                    onModeClick = { showModes = true },
                    onToggleDebug = {
                        debugOn = !debugOn
                        hint = if (debugOn) "Debug view on — feature points, surfaces, stats" else "Debug view off"
                    },
                    guidance = shownGuidance,
                    targetMeters = ui.targetMeters,
                    unit = unit,
                    onUnitChange = { unit = it },
                    hasFlash = sessionManager.hasFlash,
                    torchOn = torchOn,
                    onToggleTorch = { sessionManager.setTorch(!torchOn) },
                    showGrid = showGrid,
                    onToggleGrid = { showGrid = !showGrid },
                    magnifierOn = magnifierOn,
                    onToggleMagnifier = {
                        magnifierOn = !magnifierOn
                        hint = if (magnifierOn) "Magnifier on — hold steady to zoom" else "Magnifier off"
                    },
                    onToggleOcclusion = {
                        gridOcclusion = !gridOcclusion
                        hint = if (gridOcclusion) "Grid hides behind objects" else "Grid drawn over everything"
                    },
                    onCapture = ::capture,
                    onHistory = { showHistory = true },
                    modifier = Modifier.align(Alignment.TopCenter)
                )

                // Transient hint (e.g. + pressed while not aiming at a surface)
                hint?.let { message ->
                    // Just above the bottom controls, clear of the crosshair and measurements
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 250.dp)
                            .padding(horizontal = 32.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Black.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                MeasureControls(
                    fitSpec = fitSpec,
                    onFitSize = { showFitSize = true },
                    onRotateBox = { sessionManager.requestAction(MeasureAction.RotateBox(it)) },
                    groundDetected = ui.groundDetected,
                    phoneHeight = ui.phoneHeight,
                    onPhoneHeightClick = { showPhoneHeight = true },
                    mode = ui.mode,
                    result = ui.result,
                    draftCount = ui.draftCount,
                    onDone = { sessionManager.requestAction(MeasureAction.FinishShape) },
                    liveMeters = ui.liveMeters,
                    liveIsEstimate = ui.liveIsEstimate,
                    snapAxis = ui.snapAxis,
                    unit = unit,
                    canAdd = ui.isTracking && (ui.reticle != ReticleState.SEARCHING || ui.mode == MeasureMode.FAR),
                    canUndo = ui.hasPendingPoint || ui.lineCount > 0 || ui.draftCount > 0 || ui.summaries.isNotEmpty(),
                    onUndo = { sessionManager.requestAction(MeasureAction.Undo) },
                    onAdd = { pressedAt -> sessionManager.requestAction(MeasureAction.AddPoint(pressedAt)) },
                    onClear = { sessionManager.requestAction(MeasureAction.Clear) },
                    onSelectTool = { tool = it },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )

                selectedLine?.let { index ->
                    val summary = ui.summaries.filter { !it.isArea }.getOrNull(index)
                    if (summary != null) {
                        LineActionSheet(
                            title = "Line ${index + 1}",
                            value = formatDistance(summary.value, summary.isEstimate, unit),
                            horizontal = formatLength(summary.horizontal, unit),
                            vertical = formatLength(summary.vertical, unit),
                            angle = "%.0f°".format(summary.angleDegrees),
                            onMakeLevel = {
                                sessionManager.requestAction(MeasureAction.StraightenLine(index, StraightenMode.LEVEL))
                            },
                            onMakeVertical = {
                                sessionManager.requestAction(MeasureAction.StraightenLine(index, StraightenMode.VERTICAL))
                            },
                            onCopy = {
                                val clipboard = context.getSystemService(ClipboardManager::class.java)
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText("Measurement", formatLength(summary.value, unit))
                                )
                                hint = "Copied ${formatLength(summary.value, unit)}"
                                selectedLine = null
                            },
                            onDelete = {
                                sessionManager.requestAction(MeasureAction.DeleteLine(index))
                                selectedLine = null
                            },
                            onDismiss = { selectedLine = null },
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = showLoader,
                    enter = androidx.compose.animation.EnterTransition.None,
                    exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(500))
                ) {
                    BrandedLoader(status = "Starting the camera")
                }

                if (showFitSize) {
                    FitSizeDialog(
                        current = fitSpec,
                        unit = unit,
                        onPick = {
                            fitSpec = it
                            showFitSize = false
                            // Also resize a box that's already placed
                            sessionManager.fitSpec = it
                            sessionManager.requestAction(MeasureAction.ResizeBox)
                            hint = "Tap the teal floor to place the ${it.name.lowercase()}"
                        },
                        onDismiss = { showFitSize = false }
                    )
                }

                if (showPhoneHeight) {
                    PhoneHeightDialog(
                        height = farPhoneHeight,
                        unit = unit,
                        onChange = { farPhoneHeight = it },
                        onDismiss = { showPhoneHeight = false }
                    )
                }

                calibrationSample?.let { sample ->
                    CalibrationDialog(
                        measuredMeters = sample.meters,
                        onSurface = sample.onSurface,
                        unit = unit,
                        onApply = { factor ->
                            scale = factor
                            calibrationSample = null
                            mode = modeBeforeCalibration
                            hint = "Calibrated — readings corrected by ${"%+.1f".format((factor - 1f) * 100)}%"
                        },
                        onRetry = { calibrationSample = null },
                        onDismiss = {
                            calibrationSample = null
                            mode = modeBeforeCalibration
                        }
                    )
                }

                ModePickerSheet(
                    visible = showModes,
                    current = mode,
                    scale = scale,
                    onCalibrate = {
                        if (mode != MeasureMode.CALIBRATE) modeBeforeCalibration = mode
                        mode = MeasureMode.CALIBRATE
                        showModes = false
                        hint = MeasureMode.CALIBRATE.howTo
                    },
                    onSelect = {
                        mode = it
                        showModes = false
                        hint = "${it.title} — ${it.howTo}"
                    },
                    onDismiss = { showModes = false }
                )

                lastCapture?.let { saved ->
                    SavedToast(
                        onShare = { HistoryStore.share(context, saved, unit) },
                        onOpenHistory = { lastCapture = null; showHistory = true },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(top = 120.dp)
                    )
                }
            }

            is ARSessionState.PermissionRequired -> {
                BrandStateScreen(
                    title = "Let's use your camera",
                    description = "MeasureAR sees surfaces through the camera to measure them. Nothing is recorded or uploaded — measurements stay on this phone.",
                    buttonText = "Allow camera"
                ) {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }

            is ARSessionState.CheckingAvailability, is ARSessionState.InstallingArcore -> {
                BrandedLoader(
                    status = if (state is ARSessionState.InstallingArcore) "Setting up Google Play Services for AR"
                    else "Starting the camera"
                )
            }

            is ARSessionState.UnsupportedDevice -> {
                BrandStateScreen(
                    title = "This phone can't run AR",
                    description = state.message,
                    buttonText = null,
                    onButtonClick = {}
                )
            }

            is ARSessionState.Error -> {
                BrandStateScreen(
                    title = "Something went wrong",
                    description = state.message,
                    buttonText = "Try again"
                ) {
                    if (activity != null) {
                        sessionManager.onResume(activity)
                    }
                }
            }

            ARSessionState.Idle -> BrandedLoader(status = "Starting the camera")
        }
    }
}

/**
 * Top HUD: app title with live guidance on the left, camera-to-target distance and the unit
 * toggle on the right.
 */
@Composable
private fun HudTopBar(
    aimError: Float?,
    aimOnSurface: Boolean,
    mode: MeasureMode,
    onModeClick: () -> Unit,
    onToggleDebug: () -> Unit,
    guidance: String,
    targetMeters: Float?,
    unit: MeasureUnit,
    onUnitChange: (MeasureUnit) -> Unit,
    hasFlash: Boolean,
    torchOn: Boolean,
    onToggleTorch: () -> Unit,
    showGrid: Boolean,
    onToggleGrid: () -> Unit,
    magnifierOn: Boolean,
    onToggleMagnifier: () -> Unit,
    onToggleOcclusion: () -> Unit,
    onCapture: () -> Unit,
    onHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                // Current mode; tap for the mode picker. Hidden developer switch: long-press
                // toggles the debug view
                Box(Modifier.combinedClickable(onClick = onModeClick, onLongClick = onToggleDebug)) {
                    ModeChip(mode = mode, onClick = onModeClick)
                }
                Spacer(modifier = Modifier.height(6.dp))
                AnimatedContent(
                    targetState = guidance,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "guidance_animation"
                ) { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = HudTeal
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "TARGET DIST",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Text(
                    text = targetMeters?.let { formatRange(it, unit) } ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                // How precise a point placed right now would be
                if (targetMeters != null && aimError != null) {
                    AccuracyMeter(error = aimError, onSurface = aimOnSurface, unit = unit)
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.align(Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (hasFlash) {
                TopIconButton(
                    icon = if (torchOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off,
                    description = if (torchOn) "Turn flashlight off" else "Turn flashlight on",
                    active = torchOn,
                    onClick = onToggleTorch
                )
            }
            TopIconButton(
                icon = R.drawable.ic_grid,
                description = if (showGrid) "Hide surface grid" else "Show surface grid",
                active = showGrid,
                onClick = onToggleGrid,
                onLongClick = onToggleOcclusion
            )
            TopIconButton(
                icon = R.drawable.ic_zoom,
                description = if (magnifierOn) "Turn magnifier off" else "Turn magnifier on",
                active = magnifierOn,
                onClick = onToggleMagnifier
            )
            TopIconButton(R.drawable.ic_camera, "Save screenshot", onClick = onCapture)
            TopIconButton(R.drawable.ic_history, "Saved measurements", onClick = onHistory)
            Spacer(modifier = Modifier.width(4.dp))
            UnitToggle(unit = unit, onUnitChange = onUnitChange)
        }
    }
}

/** Compact round icon button for the top HUD. Teal when [active]. */
@Composable
private fun TopIconButton(
    @DrawableRes icon: Int,
    description: String,
    active: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (active) HudTeal else Color.Black.copy(alpha = 0.55f))
            .border(1.dp, HudTeal.copy(alpha = 0.5f), CircleShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = if (active) Color.Black else Color.White,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** Segmented m/cm ⇄ ft/in switch. */
@Composable
private fun UnitToggle(unit: MeasureUnit, onUnitChange: (MeasureUnit) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .border(1.dp, HudTeal.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
    ) {
        listOf(MeasureUnit.IMPERIAL to "ft/in", MeasureUnit.METRIC to "m/cm").forEach { (option, label) ->
            val selected = option == unit
            Text(
                text = label,
                modifier = Modifier
                    .background(if (selected) HudTeal else Color.Transparent)
                    .clickable { onUnitChange(option) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) Color.Black else Color.White
            )
        }
    }
}

/**
 * Bottom controls: alignment badge and live distance, then Undo, the teal Stamp button and
 * Clear.
 */
@Composable
private fun MeasureControls(
    fitSpec: com.jhani.measurear.measurement.BoxSpec,
    onFitSize: () -> Unit,
    onRotateBox: (Float) -> Unit,
    groundDetected: Boolean,
    phoneHeight: Float,
    onPhoneHeightClick: () -> Unit,
    mode: MeasureMode,
    result: ShapeResultUi?,
    draftCount: Int,
    onDone: () -> Unit,
    liveMeters: Float?,
    liveIsEstimate: Boolean,
    snapAxis: SnapAxis?,
    unit: MeasureUnit,
    canAdd: Boolean,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onAdd: (pressedAtNanos: Long) -> Unit,
    onClear: () -> Unit,
    onSelectTool: (Tool) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))
            )
            .navigationBarsPadding()
            .padding(top = 32.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (mode == MeasureMode.LINE) {
            // Alignment badge, e.g. "VERTICAL" while measuring a height
            Text(
                text = snapAxis?.label?.uppercase() ?: " ",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp,
                color = HudAmber
            )
            Text(
                text = liveMeters?.let { formatDistance(it, liveIsEstimate, unit) } ?: " ",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        } else {
            if (mode == MeasureMode.FAR) {
                GroundChip(groundDetected, phoneHeight, unit, onPhoneHeightClick)
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (mode == MeasureMode.FIT) {
                FitControls(fitSpec, unit, onSize = onFitSize, onRotate = onRotateBox)
                Spacer(modifier = Modifier.height(8.dp))
            }
            ResultCard(result = result, mode = mode, draftCount = draftCount, unit = unit)
            // Open-ended shapes (Path, Area) finish with Done
            if (mode.points == null && draftCount >= mode.minPoints) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "✓  Done",
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(HudTeal)
                        .clickable(onClick = onDone)
                        .padding(horizontal = 22.dp, vertical = 8.dp),
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                HudIconButton(R.drawable.ic_undo, "Undo", enabled = canUndo, onClick = onUndo)
            }
            StampButton(enabled = canAdd, onClick = onAdd)
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                HudIconButton(R.drawable.ic_delete_sweep, "Clear", enabled = canUndo, onClick = onClear)
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        ToolTabs(selected = Tool.MEASURE, onSelect = onSelectTool)
    }
}

/** Screens reachable from the bottom tabs. */
enum class Tool { MEASURE, LEVEL, COMPASS }

/** Round translucent icon button with a small caption, used in the HUD. */
@Composable
private fun HudIconButton(
    @DrawableRes icon: Int,
    label: String,
    enabled: Boolean = true,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.35f
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .border(1.dp, Color.White.copy(alpha = 0.2f * alpha), CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = label,
                tint = tint.copy(alpha = alpha),
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.8f * alpha)
        )
    }
}

/** Measure | Level switch at the bottom of the screen. */
@Composable
fun ToolTabs(selected: Tool, onSelect: (Tool) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(4.dp)
    ) {
        listOf(
            Triple(Tool.MEASURE, R.drawable.ic_straighten, "Measure"),
            Triple(Tool.LEVEL, R.drawable.ic_level, "Level"),
            Triple(Tool.COMPASS, R.drawable.ic_compass, "Compass")
        ).forEach { (tool, icon, label) ->
            val isSelected = tool == selected
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) HudTeal else Color.Transparent)
                    .clickable { onSelect(tool) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = if (isSelected) Color.Black else Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) Color.Black else Color.White
                )
            }
        }
    }
}

/**
 * Glowing teal circular button that stamps a point at the reticle. The press time is taken
 * at touch-down, so the point uses the aim from before the thumb nudged the phone.
 */
@Composable
private fun StampButton(enabled: Boolean, onClick: (pressedAtNanos: Long) -> Unit) {
    val currentOnClick by rememberUpdatedState(onClick)
    val alpha = if (enabled) 1f else 0.45f
    Box(
        modifier = Modifier
            .size(84.dp)
            .background(
                Brush.radialGradient(listOf(HudTeal.copy(alpha = 0.35f * alpha), Color.Transparent)),
                CircleShape
            )
            .padding(6.dp)
            .clip(CircleShape)
            .background(HudTealDark.copy(alpha = 0.9f))
            .border(3.dp, HudTeal.copy(alpha = alpha), CircleShape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    currentOnClick(System.nanoTime())
                    waitForUpOrCancellation()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "+",
                fontSize = 30.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Light,
                color = HudTeal.copy(alpha = alpha)
            )
            Text(
                text = "STAMP",
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.SemiBold,
                color = HudTeal.copy(alpha = alpha)
            )
        }
    }
}

/** Bottom sheet for a tapped measurement label: copy the value or delete the line. */
@Composable
private fun LineActionSheet(
    title: String,
    value: String,
    horizontal: String,
    vertical: String,
    angle: String,
    onMakeLevel: () -> Unit,
    onMakeVertical: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(Color(0xFF121A1A))
            .border(1.dp, HudTeal.copy(alpha = 0.35f), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .navigationBarsPadding()
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.6f))
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            TopIconButton(R.drawable.ic_close, "Close", onClick = onDismiss)
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Breakdown of the 3D length into flat and height components
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip("Horizontal", horizontal, Modifier.weight(1f))
            StatChip("Vertical", vertical, Modifier.weight(1f))
            StatChip("Angle", angle, Modifier.weight(0.7f))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SheetButton(R.drawable.ic_straighten, "Make level", HudAmber, onMakeLevel, Modifier.weight(1f))
            SheetButton(R.drawable.ic_level, "Make vertical", HudAmber, onMakeVertical, Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SheetButton(R.drawable.ic_copy, "Copy", HudTeal, onCopy, Modifier.weight(1f))
            SheetButton(R.drawable.ic_delete, "Delete", Color(0xFFFF6B6B), onDelete, Modifier.weight(1f))
        }
    }
}

/** Small labelled value tile in the line sheet. */
@Composable
private fun StatChip(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
        Text(text = value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

@Composable
private fun SheetButton(
    @DrawableRes icon: Int,
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .border(1.5.dp, color.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, color = color, fontWeight = FontWeight.SemiBold)
    }
}

/** Confirmation after a capture, with quick Share / History actions. */
@Composable
private fun SavedToast(onShare: () -> Unit, onOpenHistory: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.8f))
            .border(1.dp, HudTeal.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "Saved", color = Color.White, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.width(12.dp))
        TextButton(onClick = onShare) { Text("Share", color = HudTeal) }
        TextButton(onClick = onOpenHistory) { Text("History", color = HudTeal) }
    }
}

