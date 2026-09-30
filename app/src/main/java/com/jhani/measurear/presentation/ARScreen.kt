package com.jhani.measurear.presentation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
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
    // Where the last screen tap was (and when), so a point placed by it pops out there
    var lastTap by remember { mutableStateOf<Pair<Offset, Long>?>(null) }
    val view = androidx.compose.ui.platform.LocalView.current
    // Step-by-step guide for the current screen (the ? button); opens by itself the first time
    var helpTopic by remember { mutableStateOf<String?>(null) }
    var showLanguage by remember { mutableStateOf(false) }
    val language = remember { AppLanguage.saved(context) }
    // Will it fit?: chosen box size, and the size picker
    var fitSpec by remember { mutableStateOf(com.jhani.measurear.measurement.BoxSpec.PRESETS[0]) }
    LaunchedEffect(fitSpec) { sessionManager.fitSpec = fitSpec }
    var showFitSize by remember { mutableStateOf(false) }
    // Hang pictures: arrangement and its editor
    var hangSpec by remember { mutableStateOf(com.jhani.measurear.measurement.HangSpec()) }
    LaunchedEffect(hangSpec) { sessionManager.hangSpec = hangSpec }
    var showHang by remember { mutableStateOf(false) }
    LaunchedEffect(mode) { if (mode == MeasureMode.HANG) showHang = true }
    // Home tools for a finished area: materials calculator and floor plan
    var materialsFor by remember { mutableStateOf<Float?>(null) }
    var planBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var planArea by remember { mutableStateOf(0f) }
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
                hint = context.getString(R.string.hint_measure_first)
                return@launch
            }
            val bitmap = HistoryStore.captureScreen(host, cameraView)
            if (bitmap == null) hint = context.getString(R.string.hint_saved_no_photo)
            lastCapture = HistoryStore.add(context, context.getString(R.string.snapshot_name, items.size), items, bitmap)
        }
    }

    // Only auto-prompt for the camera once; the permission dialog itself pauses/resumes the
    // activity, so prompting on every ON_RESUME would loop forever after a denial.
    var permissionPrompted by remember { mutableStateOf(false) }
    // First launch: the welcome pages come before the camera permission prompt
    var showWelcome by remember { mutableStateOf(!WelcomePrefs.isDone(context)) }
    val welcomeShowing by rememberUpdatedState(showWelcome)

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
                    if (activity != null && !welcomeShowing) {
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

    if (showWelcome) {
        WelcomeScreen(onDone = {
            WelcomePrefs.markDone(context)
            showWelcome = false
            if (activity != null) {
                if (sessionManager.hasCameraPermission()) {
                    sessionManager.onResume(activity)
                } else {
                    permissionPrompted = true
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
        })
        return
    }

    val screenTopic = if (tool == Tool.MEASURE) HelpTopic.of(mode) else tool.name
    // Level and Compass don't wait for the camera
    val screenReady = sessionState is ARSessionState.SessionReady && (tool != Tool.MEASURE || !showLoader) &&
        !showModes && !showHistory
    LaunchedEffect(screenTopic, screenReady) {
        if (screenReady && helpTopic == null && !HelpSeen.isSeen(context, screenTopic)) helpTopic = screenTopic
    }
    helpTopic?.let { topic ->
        val name = when (topic) {
            HelpTopic.LEVEL -> stringResource(R.string.tab_level)
            HelpTopic.COMPASS -> stringResource(R.string.tab_compass)
            else -> MeasureMode.values().firstOrNull { it.name == topic }?.let { context.modeTitle(it) } ?: ""
        }
        HelpDialog(topic = topic, name = name, onClose = {
            HelpSeen.markSeen(context, topic)
            helpTopic = null
        })
    }

    if (showHistory) {
        HistoryScreen(unit = unit, onClose = { showHistory = false }, modifier = modifier)
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (val state = sessionState) {
            is ARSessionState.SessionReady -> if (tool != Tool.MEASURE) {
                if (tool == Tool.LEVEL) LevelScreen() else com.jhani.measurear.level.CompassScreen()
                HelpButton(
                    onClick = { helpTopic = tool.name },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        // Below the Level's instruction line, which spans the top
                        .padding(top = 64.dp, end = 20.dp)
                )
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
                    onScreenTap = { x, y ->
                        lastTap = Offset(x, y) to System.currentTimeMillis()
                        sessionManager.requestAction(MeasureAction.AddPointAt(x, y))
                    },
                    onDragStart = { x, y -> sessionManager.requestAction(MeasureAction.DragStart(x, y)) },
                    onDrag = { x, y -> sessionManager.requestAction(MeasureAction.DragMove(x, y)) },
                    onDragEnd = { sessionManager.requestAction(MeasureAction.DragEnd) }
                )

                // A point placed: a short vibration and a teal ring popping out where it landed
                // (the tap, or the crosshair in the middle for STAMP); a light tick on a snap
                val placedKey = Triple(ui.summaries.size, ui.draftCount, ui.hasPendingPoint)
                var lastPlacedKey by remember { mutableStateOf(placedKey) }
                val pop = remember { androidx.compose.animation.core.Animatable(1f) }
                var popAt by remember { mutableStateOf<Offset?>(null) }
                LaunchedEffect(placedKey) {
                    val (done, draft, pending) = placedKey
                    val (lastDone, lastDraft, lastPending) = lastPlacedKey
                    lastPlacedKey = placedKey
                    val added = done > lastDone || draft > lastDraft || (pending && !lastPending)
                    if (!added) return@LaunchedEffect
                    view.performHapticFeedback(
                        if (android.os.Build.VERSION.SDK_INT >= 30) android.view.HapticFeedbackConstants.CONFIRM
                        else android.view.HapticFeedbackConstants.VIRTUAL_KEY
                    )
                    popAt = lastTap?.takeIf { System.currentTimeMillis() - it.second < 600 }?.first
                    pop.snapTo(0f)
                    pop.animateTo(1f, androidx.compose.animation.core.tween(450))
                }
                LaunchedEffect(ui.reticle) {
                    if (ui.reticle == ReticleState.SNAPPED) view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                }
                if (pop.value < 1f) {
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        val at = popAt ?: center
                        val v = pop.value
                        drawCircle(
                            HudTeal.copy(alpha = (1f - v) * 0.9f),
                            radius = (10f + 30f * v) * density,
                            center = at,
                            style = androidx.compose.ui.graphics.drawscope.Stroke((3f - 2f * v) * density)
                        )
                    }
                }

                // Height of the bottom controls, so the transient hint can sit above them
                var controlsHeight by remember { mutableStateOf(0) }

                // Scanning guide whenever ARCore has no surface to measure on
                val scanning = ui.surfaceCount == 0 && !ui.hasPendingPoint && ui.draftCount == 0
                if (scanning) {
                    OnboardingHint(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(top = 230.dp)
                    )
                }

                val guidanceText = when {
                    !ui.isTracking -> ui.trackingMessage ?: context.getString(R.string.guide_start)
                    ui.mode == MeasureMode.FAR && ui.draftCount == 0 ->
                        context.getString(R.string.guide_far_base)
                    ui.mode == MeasureMode.FAR -> context.getString(R.string.guide_far_top)
                    ui.surfaceCount == 0 -> context.getString(R.string.guide_scanning)
                    ui.reticle == ReticleState.SEARCHING -> context.getString(R.string.guide_nothing)
                    ui.mode != MeasureMode.LINE && ui.reticle == ReticleState.ESTIMATE ->
                        context.getString(R.string.guide_approx)
                    ui.mode != MeasureMode.LINE -> context.modeHowTo(ui.mode)
                    (ui.targetMeters ?: 1f) < 0.2f -> context.getString(R.string.guide_too_close)
                    ui.snapAxis == SnapAxis.VERTICAL && ui.pendingIsTop ->
                        context.getString(R.string.guide_vertical_base)
                    ui.snapAxis == SnapAxis.VERTICAL -> context.getString(R.string.guide_vertical_top)
                    ui.reticle == ReticleState.ESTIMATE && ui.reticleAmbiguous -> context.getString(R.string.guide_edge)
                    ui.reticle == ReticleState.ESTIMATE && !ui.reticleReliable ->
                        context.getString(R.string.guide_approx)
                    ui.hasPendingPoint -> context.getString(R.string.guide_end_point)
                    ui.reticle == ReticleState.SNAPPED -> context.getString(R.string.guide_continue)
                    else -> context.getString(R.string.guide_ready)
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
                        hint = context.getString(if (debugOn) R.string.hint_debug_on else R.string.hint_debug_off)
                    },
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
                        hint = context.getString(if (magnifierOn) R.string.hint_magnifier_on else R.string.hint_magnifier_off)
                    },
                    gridOcclusion = gridOcclusion,
                    onToggleOcclusion = {
                        gridOcclusion = !gridOcclusion
                        hint = context.getString(if (gridOcclusion) R.string.hint_occlusion_on else R.string.hint_occlusion_off)
                    },
                    onCapture = ::capture,
                    onHistory = { showHistory = true },
                    onHelp = { helpTopic = HelpTopic.of(mode) },
                    modifier = Modifier.align(Alignment.TopCenter)
                )

                // The one place instructions appear, just above the bottom controls: what to do
                // now, or briefly a tip (e.g. + pressed while not aiming at a surface)
                val coachText = hint ?: if (scanning) context.getString(R.string.onboarding).replace("\n", " ") else shownGuidance
                CoachLine(
                    text = coachText,
                    isTip = hint != null,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = (with(LocalDensity.current) { controlsHeight.toDp() } - 12.dp).coerceAtLeast(0.dp))
                        .padding(horizontal = 24.dp)
                )

                MeasureControls(
                    hangSpec = hangSpec,
                    onEditHang = { showHang = true },
                    onMaterials = { area -> materialsFor = area },
                    onPlan = { r ->
                        val outline = r.outline
                        val area = r.area
                        if (outline != null && area != null && outline.size >= 3) {
                            planArea = area
                            planBitmap = FloorPlanRenderer.render(outline, r.outlineNormal, unit, context.getString(R.string.plan_title, context.modeTitle(r.mode)), area, context.getString(R.string.plan_footer))
                        }
                    },
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
                    scanning = scanning,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onSizeChanged { controlsHeight = it.height }
                )

                selectedLine?.let { index ->
                    val summary = ui.summaries.filter { !it.isArea }.getOrNull(index)
                    if (summary != null) {
                        LineActionSheet(
                            title = context.getString(R.string.line_n, index + 1),
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
                                hint = context.getString(R.string.hint_copied, formatLength(summary.value, unit))
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
                    BrandedLoader(status = context.getString(R.string.loader_starting))
                }

                materialsFor?.let { area ->
                    MaterialsDialog(area = area, unit = unit, onDismiss = { materialsFor = null })
                }
                planBitmap?.let { bmp ->
                    fun save(then: (HistoryRecord) -> Unit) = scope.launch {
                        val summary = com.jhani.measurear.measurement.MeasurementSummary(planArea, isArea = true, isEstimate = false, label = "Floor plan")
                        val record = HistoryStore.add(context, context.getString(R.string.plan_record_name, formatArea(planArea, false, unit)), listOf(summary), bmp)
                        then(record)
                    }
                    FloorPlanDialog(
                        plan = bmp,
                        onSave = { save { hint = context.getString(R.string.hint_plan_saved); planBitmap = null } },
                        onShare = { save { HistoryStore.share(context, it, unit); planBitmap = null } },
                        onDismiss = { planBitmap = null }
                    )
                }

                if (showHang && helpTopic == null) {
                    HangDialog(
                        current = hangSpec,
                        unit = unit,
                        onApply = {
                            hangSpec = it
                            showHang = false
                            sessionManager.hangSpec = it
                            sessionManager.requestAction(MeasureAction.UpdateHang)
                            hint = context.getString(R.string.hint_tap_wall)
                        },
                        onDismiss = { showHang = false }
                    )
                }

                if (showFitSize && helpTopic == null) {
                    FitSizeDialog(
                        current = fitSpec,
                        unit = unit,
                        onPick = {
                            fitSpec = it
                            showFitSize = false
                            // Also resize a box that's already placed
                            sessionManager.fitSpec = it
                            sessionManager.requestAction(MeasureAction.ResizeBox)
                            hint = context.getString(R.string.hint_place_box, context.boxName(it))
                        },
                        onDismiss = { showFitSize = false }
                    )
                }

                if (showLanguage) {
                    LanguageDialog(
                        current = language,
                        onPick = { picked ->
                            showLanguage = false
                            if (picked != language) {
                                AppLanguage.save(context, picked)
                                // Re-create so every screen, hint and layout direction follow
                                activity?.recreate()
                            }
                        },
                        onDismiss = { showLanguage = false }
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
                            hint = context.getString(R.string.hint_calibrated, "%+.1f%%".format((factor - 1f) * 100))
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
                    language = language,
                    onLanguage = {
                        showModes = false
                        showLanguage = true
                    },
                    onCalibrate = {
                        if (mode != MeasureMode.CALIBRATE) modeBeforeCalibration = mode
                        mode = MeasureMode.CALIBRATE
                        showModes = false
                        hint = context.modeHowTo(MeasureMode.CALIBRATE)
                    },
                    onSelect = {
                        mode = it
                        showModes = false
                        hint = context.getString(R.string.mode_hint, context.modeTitle(it), context.modeHowTo(it))
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
                    title = context.getString(R.string.perm_title),
                    description = context.getString(R.string.perm_desc),
                    buttonText = context.getString(R.string.perm_button)
                ) {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }

            is ARSessionState.CheckingAvailability, is ARSessionState.InstallingArcore -> {
                BrandedLoader(
                    status = if (state is ARSessionState.InstallingArcore) context.getString(R.string.loader_installing)
                    else context.getString(R.string.loader_starting)
                )
            }

            is ARSessionState.UnsupportedDevice -> {
                BrandStateScreen(
                    title = context.getString(R.string.state_unsupported_title),
                    description = state.message,
                    buttonText = null,
                    onButtonClick = {}
                )
            }

            is ARSessionState.Error -> {
                BrandStateScreen(
                    title = context.getString(R.string.state_error_title),
                    description = state.message,
                    buttonText = context.getString(R.string.try_again)
                ) {
                    if (activity != null) {
                        sessionManager.onResume(activity)
                    }
                }
            }

            ARSessionState.Idle -> BrandedLoader(status = context.getString(R.string.loader_starting))
        }
    }
}

/**
 * Top bar, kept light so the camera stays visible: the mode and its guide on the left; save,
 * history and a ⋯ menu (flash, grid, magnifier, units) on the right; under them the distance
 * to the aim point and how accurate a point placed now would be.
 */
@Composable
private fun HudTopBar(
    aimError: Float?,
    aimOnSurface: Boolean,
    mode: MeasureMode,
    onModeClick: () -> Unit,
    onToggleDebug: () -> Unit,
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
    gridOcclusion: Boolean,
    onToggleOcclusion: () -> Unit,
    onCapture: () -> Unit,
    onHistory: () -> Unit,
    onHelp: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)))
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                // Current mode; tap for the mode picker. Hidden developer switch: long-press
                // toggles the debug view
                Box(
                    Modifier
                        .weight(1f, fill = false)
                        .combinedClickable(onClick = onModeClick, onLongClick = onToggleDebug)
                ) {
                    ModeChip(mode = mode, onClick = onModeClick)
                }
                Spacer(modifier = Modifier.width(8.dp))
                HelpButton(onClick = onHelp)
            }
            Spacer(modifier = Modifier.width(8.dp))
            TopIconButton(R.drawable.ic_camera, stringResource(R.string.cd_save_screenshot), onClick = onCapture)
            Spacer(modifier = Modifier.width(8.dp))
            TopIconButton(R.drawable.ic_history, stringResource(R.string.saved_measurements), onClick = onHistory)
            Spacer(modifier = Modifier.width(8.dp))
            MoreMenu(
                unit = unit, onUnitChange = onUnitChange,
                hasFlash = hasFlash, torchOn = torchOn, onToggleTorch = onToggleTorch,
                showGrid = showGrid, onToggleGrid = onToggleGrid,
                magnifierOn = magnifierOn, onToggleMagnifier = onToggleMagnifier,
                gridOcclusion = gridOcclusion, onToggleOcclusion = onToggleOcclusion
            )
        }
        if (targetMeters != null) {
            Spacer(modifier = Modifier.height(8.dp))
            AimChip(
                meters = targetMeters,
                error = aimError,
                onSurface = aimOnSurface,
                unit = unit,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

/** The ⋯ menu: the less-used switches, so the top bar stays uncluttered. */
@Composable
private fun MoreMenu(
    unit: MeasureUnit,
    onUnitChange: (MeasureUnit) -> Unit,
    hasFlash: Boolean,
    torchOn: Boolean,
    onToggleTorch: () -> Unit,
    showGrid: Boolean,
    onToggleGrid: () -> Unit,
    magnifierOn: Boolean,
    onToggleMagnifier: () -> Unit,
    gridOcclusion: Boolean,
    onToggleOcclusion: () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box {
        TopIconButton(R.drawable.ic_more, stringResource(R.string.cd_more), active = open, onClick = { open = true })
        androidx.compose.material3.DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = Color(0xFF121A1A),
            shape = RoundedCornerShape(16.dp)
        ) {
            @Composable
            fun item(@DrawableRes icon: Int?, label: String, on: Boolean?, onClick: () -> Unit) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(label, color = Color.White) },
                    // Rows without an icon keep the same indent
                    leadingIcon = {
                        if (icon != null) Icon(painterResource(icon), contentDescription = null, tint = if (on == true) HudTeal else Color.White, modifier = Modifier.size(20.dp))
                        else Spacer(Modifier.size(20.dp))
                    },
                    trailingIcon = on?.let {
                        {
                            androidx.compose.material3.Switch(
                                checked = it,
                                onCheckedChange = null,
                                modifier = Modifier.scale(0.8f),
                                colors = androidx.compose.material3.SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = HudTeal,
                                    uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                                    uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                                    uncheckedBorderColor = Color.White.copy(alpha = 0.3f)
                                )
                            )
                        }
                    },
                    onClick = onClick
                )
            }
            if (hasFlash) {
                item(if (torchOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off, stringResource(R.string.flashlight), torchOn, onToggleTorch)
            }
            item(R.drawable.ic_grid, stringResource(R.string.surface_grid), showGrid, onToggleGrid)
            if (showGrid) item(null, stringResource(R.string.menu_occlusion), gridOcclusion, onToggleOcclusion)
            item(R.drawable.ic_zoom, stringResource(R.string.magnifier), magnifierOn, onToggleMagnifier)
            androidx.compose.material3.HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_units), color = Color.White) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_straighten), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                trailingIcon = { UnitToggle(unit = unit, onUnitChange = onUnitChange) },
                onClick = { onUnitChange(if (unit == MeasureUnit.METRIC) MeasureUnit.IMPERIAL else MeasureUnit.METRIC) }
            )
        }
    }
}

/** Distance to the aim point, with the accuracy of a point placed there now. */
@Composable
private fun AimChip(meters: Float, error: Float?, onSurface: Boolean, unit: MeasureUnit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = formatRange(meters, unit),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
        if (error != null) {
            Spacer(modifier = Modifier.width(10.dp))
            AccuracyMeter(error = error, onSurface = onSurface, unit = unit)
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
            .size(44.dp)
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
            modifier = Modifier.size(20.dp)
        )
    }
}

/** The single instruction line: dark pill so it reads on any background; amber edge for a tip. */
@Composable
private fun CoachLine(text: String, isTip: Boolean, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = text to isTip,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "coach",
        modifier = modifier
    ) { (message, tip) ->
        Text(
            text = message,
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black.copy(alpha = 0.62f))
                .border(1.dp, if (tip) HudAmber.copy(alpha = 0.8f) else HudTeal.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 9.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            textAlign = TextAlign.Center
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
    hangSpec: com.jhani.measurear.measurement.HangSpec,
    onEditHang: () -> Unit,
    onMaterials: (Float) -> Unit,
    onPlan: (ShapeResultUi) -> Unit,
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
    scanning: Boolean,
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
        if (mode == MeasureMode.LINE && scanning) {
            // Room for the scanning guide
        } else if (mode == MeasureMode.LINE) {
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
        } else if (scanning && mode != MeasureMode.FAR) {
            // The scanning guide has this space until there's a surface; the mode's own
            // controls and instructions follow once there is
        } else {
            if (mode == MeasureMode.FAR) {
                GroundChip(groundDetected, phoneHeight, unit, onPhoneHeightClick)
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (mode == MeasureMode.HANG) {
                HangControls(hangSpec, unit, onEdit = onEditHang)
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (mode == MeasureMode.FIT) {
                FitControls(fitSpec, unit, onSize = onFitSize, onRotate = onRotateBox)
                Spacer(modifier = Modifier.height(8.dp))
            }
            ResultCard(result = result, mode = mode, draftCount = draftCount, unit = unit)
            // Finished area: what to buy, and a floor plan
            val area = result?.area
            if (result != null && !result.isLive && area != null && draftCount == 0 && mode != MeasureMode.FIT) {
                Spacer(modifier = Modifier.height(8.dp))
                AreaActions(onMaterials = { onMaterials(area) }, onPlan = { onPlan(result) })
            }
            // Open-ended shapes (Path, Area) finish with Done
            if (mode.points == null && draftCount >= mode.minPoints) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "✓  " + stringResource(R.string.done),
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
                HudIconButton(R.drawable.ic_undo, stringResource(R.string.undo), enabled = canUndo, onClick = onUndo)
            }
            StampButton(enabled = canAdd, onClick = onAdd)
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                HudIconButton(R.drawable.ic_delete_sweep, stringResource(R.string.clear), enabled = canUndo, onClick = onClear)
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
            Triple(Tool.MEASURE, R.drawable.ic_straighten, stringResource(R.string.tab_measure)),
            Triple(Tool.LEVEL, R.drawable.ic_level, stringResource(R.string.tab_level)),
            Triple(Tool.COMPASS, R.drawable.ic_compass, stringResource(R.string.tab_compass))
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
                text = stringResource(R.string.stamp),
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
            TopIconButton(R.drawable.ic_close, stringResource(R.string.close), onClick = onDismiss)
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Breakdown of the 3D length into flat and height components
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip(stringResource(R.string.horizontal), horizontal, Modifier.weight(1f))
            StatChip(stringResource(R.string.vertical), vertical, Modifier.weight(1f))
            StatChip(stringResource(R.string.label_angle), angle, Modifier.weight(0.7f))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SheetButton(R.drawable.ic_straighten, stringResource(R.string.make_level), HudAmber, onMakeLevel, Modifier.weight(1f))
            SheetButton(R.drawable.ic_level, stringResource(R.string.make_vertical), HudAmber, onMakeVertical, Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SheetButton(R.drawable.ic_copy, stringResource(R.string.copy), HudTeal, onCopy, Modifier.weight(1f))
            SheetButton(R.drawable.ic_delete, stringResource(R.string.delete), Color(0xFFFF6B6B), onDelete, Modifier.weight(1f))
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
        Text(text = stringResource(R.string.saved), color = Color.White, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.width(12.dp))
        TextButton(onClick = onShare) { Text(stringResource(R.string.share), color = HudTeal) }
        TextButton(onClick = onOpenHistory) { Text(stringResource(R.string.history), color = HudTeal) }
    }
}

