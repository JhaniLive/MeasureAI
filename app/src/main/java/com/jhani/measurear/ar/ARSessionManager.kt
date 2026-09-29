package com.jhani.measurear.ar

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.ar.core.ArCoreApk
import com.google.ar.core.CameraConfig
import com.google.ar.core.CameraConfigFilter
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import com.jhani.measurear.measurement.MeasureUiState
import com.jhani.measurear.measurement.MeasuredArea
import com.jhani.measurear.measurement.MeasuredLine
import com.jhani.measurear.measurement.PlacedPoint
import java.util.EnumSet
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * User actions from the measuring controls. They are queued and applied on the GL thread,
 * because adding a point needs the current frame's reticle hit.
 */
sealed interface MeasureAction {
    /**
     * Stamp a point. [pressedAtNanos] ([System.nanoTime]) is when the user pressed, so the
     * point can use where the reticle was *before* the press nudged the phone.
     */
    data class AddPoint(val pressedAtNanos: Long = System.nanoTime()) : MeasureAction

    /** Place a point where the user tapped, at view pixel ([x], [y]). */
    data class AddPointAt(val x: Float, val y: Float) : MeasureAction

    /** Finish an open-ended shape (Path, Area) with the points placed so far. */
    data object FinishShape : MeasureAction

    /** Finger drag of a placed point, in view pixels. */
    data class DragStart(val x: Float, val y: Float) : MeasureAction
    data class DragMove(val x: Float, val y: Float) : MeasureAction
    data object DragEnd : MeasureAction
    data object Undo : MeasureAction
    data object Clear : MeasureAction

    /** Removes one completed line (and any area that uses it). */
    data class DeleteLine(val index: Int) : MeasureAction

    /** Moves a line's end so it is exactly level or vertical with its start. */
    data class StraightenLine(
        val index: Int,
        val mode: com.jhani.measurear.measurement.StraightenMode
    ) : MeasureAction
}

/**
 * Manages ARCore Session creation, lifecycle, permission checks, installation requests,
 * and the measurement anchors.
 */
class ARSessionManager(private val context: Context) {

    companion object {
        private const val TAG = "ARSessionManager"
    }

    private val _sessionState = MutableStateFlow<ARSessionState>(ARSessionState.Idle)
    val sessionState: StateFlow<ARSessionState> = _sessionState.asStateFlow()

    private val _uiState = MutableStateFlow(MeasureUiState())
    val uiState: StateFlow<MeasureUiState> = _uiState.asStateFlow()

    /** A finished measurement (line, area or straightened line), for auto-saving to History. */
    data class Completed(val label: String, val summary: com.jhani.measurear.measurement.MeasurementSummary)

    private val _completed = MutableSharedFlow<Completed>(extraBufferCapacity = 8)
    val completed: SharedFlow<Completed> = _completed.asSharedFlow()

    fun emitCompleted(label: String, summary: com.jhani.measurear.measurement.MeasurementSummary) {
        _completed.tryEmit(Completed(label, summary))
    }

    // One-shot hints shown briefly to the user (e.g. + pressed while not aiming at a surface)
    private val _hints = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val hints: SharedFlow<String> = _hints.asSharedFlow()

    var session: Session? = null
        private set

    private var installRequested = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val pendingActions = ConcurrentLinkedQueue<MeasureAction>()

    /** Completed measurements. Only touched on the GL thread (and after it stops, in onDestroy). */
    val lines = mutableListOf<MeasuredLine>()

    /** Start point of the measurement in progress, waiting for the end point. GL thread only. */
    var pendingStart: PlacedPoint? = null

    /** Closed shapes. GL thread only. */
    val areas = mutableListOf<MeasuredArea>()

    /**
     * Lines stamped one after another, each starting where the previous one ended. When a
     * line ends back on the chain's first point, the chain becomes an area. GL thread only.
     */
    val chain = mutableListOf<com.jhani.measurear.measurement.MeasuredLine>()

    /** Whether the pending start continues the current chain. GL thread only. */
    var pendingContinuesChain = false

    /** Selected measuring mode. UI thread writes, GL reads. */
    @Volatile
    var mode: com.jhani.measurear.measurement.MeasureMode = com.jhani.measurear.measurement.MeasureMode.LINE

    /** Far mode: phone height above the ground when no ground is detected. UI writes, GL reads. */
    @Volatile
    var farPhoneHeight: Float = 1.45f

    /** Finished shapes of every mode except Line (which uses [lines]). GL thread only. */
    val shapes = mutableListOf<com.jhani.measurear.measurement.MeasuredShape>()

    /** Points of the shape being placed, and the mode it was started in. GL thread only. */
    val draft = mutableListOf<PlacedPoint>()
    var draftMode: com.jhani.measurear.measurement.MeasureMode? = null
    var draftNormal: com.jhani.measurear.measurement.Vec3? = null

    /** Whether the session has the Depth API on (grid occlusion). Set at session creation. */
    @Volatile
    var depthEnabled: Boolean = false
        private set

    /** Whether detected surfaces show the dot grid. Written on the UI thread, read on GL. */
    @Volatile
    var showGrid: Boolean = true

    /** Debug view: feature points, plane outlines, live stats. UI thread writes, GL reads. */
    @Volatile
    var debugView: Boolean = false

    /** User setting: allow the magnifier to appear. UI thread writes, GL reads. */
    @Volatile
    var magnifierEnabled: Boolean = true

    /** Whether the grid is hidden behind real objects using depth. UI thread writes, GL reads. */
    @Volatile
    var gridOcclusion: Boolean = false

    private val _torchOn = MutableStateFlow(false)
    val torchOn: StateFlow<Boolean> = _torchOn.asStateFlow()

    val hasFlash: Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)

    /**
     * Checks whether Camera permission is granted.
     */
    fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** Queues a control action for the GL thread. Safe to call from the UI thread. */
    fun requestAction(action: MeasureAction) {
        pendingActions.add(action)
    }

    /** Next queued control action, or null. Called on the GL thread. */
    fun pollAction(): MeasureAction? = pendingActions.poll()

    fun publishUiState(state: MeasureUiState) {
        _uiState.value = state
    }

    fun showHint(message: String) {
        _hints.tryEmit(message)
    }

    /** Removes the pending point, or the most recent completed line / shape. GL thread only. */
    fun undo() {
        val pending = pendingStart
        if (draft.isNotEmpty() && draftMode == com.jhani.measurear.measurement.MeasureMode.FAR) {
            // Far keeps [base, where you stood]: undoing either one undoes the base
            discardDraft()
        } else if (draft.isNotEmpty()) {
            draft.removeAt(draft.lastIndex).anchor.detach()
            if (draft.isEmpty()) draftMode = null
        } else if (mode != com.jhani.measurear.measurement.MeasureMode.LINE && shapes.isNotEmpty()) {
            shapes.removeAt(shapes.lastIndex).points.forEach { it.anchor.detach() }
        } else if (pending != null) {
            pending.anchor.detach()
            pendingStart = null
            pendingContinuesChain = false
        } else if (lines.isNotEmpty()) {
            deleteLine(lines.lastIndex)
        }
    }

    /** Removes one completed line, plus any area built from it. GL thread only. */
    fun deleteLine(index: Int) {
        if (index !in lines.indices) return
        val line = lines.removeAt(index)
        line.start.anchor.detach()
        line.end.anchor.detach()
        areas.removeAll { area -> area.lines.any { it === line } }
        if (chain.any { it === line }) {
            chain.clear()
            pendingContinuesChain = false
        }
    }

    /** Detaches every measurement anchor. */
    fun clearAll() {
        pendingStart?.anchor?.detach()
        pendingStart = null
        pendingContinuesChain = false
        lines.forEach {
            it.start.anchor.detach()
            it.end.anchor.detach()
        }
        lines.clear()
        areas.clear()
        chain.clear()
        discardDraft()
        shapes.forEach { shape -> shape.points.forEach { it.anchor.detach() } }
        shapes.clear()
    }

    /** Drops the shape being placed (e.g. when switching modes). GL thread only. */
    fun discardDraft() {
        draft.forEach { it.anchor.detach() }
        draft.clear()
        draftMode = null
    }

    /**
     * Turns the torch on/off through ARCore (it owns the camera). UI thread.
     */
    fun setTorch(on: Boolean) {
        val current = session ?: return
        try {
            val config = current.config
            config.flashMode = if (on) Config.FlashMode.TORCH else Config.FlashMode.OFF
            current.configure(config)
            _torchOn.value = on
        } catch (e: Exception) {
            Log.w(TAG, "Torch unavailable", e)
            showHint("Flashlight isn't available right now")
        }
    }

    /**
     * Resumes or initializes the AR session. Should be called from Activity/Composable onResume.
     */
    fun onResume(activity: Activity) {
        if (!hasCameraPermission()) {
            _sessionState.value = ARSessionState.PermissionRequired
            return
        }

        if (session == null) {
            _sessionState.value = ARSessionState.CheckingAvailability
            val availability = ArCoreApk.getInstance().checkAvailability(context)

            if (availability.isTransient) {
                // Availability check still in progress; poll again shortly
                mainHandler.postDelayed({ onResume(activity) }, 200)
                return
            }

            if (!availability.isSupported) {
                _sessionState.value = ARSessionState.UnsupportedDevice(
                    "This device does not support Google ARCore required for 3D spatial measurement."
                )
                return
            }

            try {
                // Request ARCore installation or update if necessary
                when (ArCoreApk.getInstance().requestInstall(activity, !installRequested)) {
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                        installRequested = true
                        _sessionState.value = ARSessionState.InstallingArcore
                        return
                    }
                    ArCoreApk.InstallStatus.INSTALLED -> {
                        // ARCore is installed
                    }
                }

                // Create ARCore Session
                val newSession = Session(context)
                selectCameraConfig(newSession)

                val depthSupported = newSession.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
                Log.i(TAG, "ARCore Depth API supported: $depthSupported")

                val config = Config(newSession).apply {
                    updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                    planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                    // Autofocus keeps close tabletop objects sharp (fixed focus blurs under ~50 cm)
                    focusMode = Config.FocusMode.AUTO
                    // Measuring needs real geometry: no lighting estimation, no instant placement
                    lightEstimationMode = Config.LightEstimationMode.DISABLED
                    instantPlacementMode = Config.InstantPlacementMode.DISABLED
                    // Depth adds hit results on non-planar surfaces where the device supports it
                    depthMode = if (depthSupported) Config.DepthMode.AUTOMATIC else Config.DepthMode.DISABLED
                }
                depthEnabled = depthSupported
                newSession.configure(config)
                session = newSession

            } catch (e: UnavailableArcoreNotInstalledException) {
                _sessionState.value = ARSessionState.InstallingArcore
                return
            } catch (e: UnavailableUserDeclinedInstallationException) {
                _sessionState.value = ARSessionState.Error("ARCore installation was declined. ARCore is required to measure.")
                return
            } catch (e: UnavailableDeviceNotCompatibleException) {
                _sessionState.value = ARSessionState.UnsupportedDevice("Device is incompatible with ARCore.")
                return
            } catch (e: UnavailableSdkTooOldException) {
                _sessionState.value = ARSessionState.Error("Please update this app to support your ARCore version.")
                return
            } catch (e: UnavailableApkTooOldException) {
                _sessionState.value = ARSessionState.Error("Please update ARCore on your device via Google Play Store.")
                return
            } catch (e: SecurityException) {
                _sessionState.value = ARSessionState.PermissionRequired
                return
            } catch (e: Exception) {
                _sessionState.value = ARSessionState.Error("Failed to initialize ARCore session: ${e.localizedMessage ?: "Unknown error"}")
                return
            }
        }

        // Resume existing session
        try {
            session?.resume()
            _sessionState.value = ARSessionState.SessionReady
        } catch (e: CameraNotAvailableException) {
            _sessionState.value = ARSessionState.Error("Camera is currently unavailable or used by another application.")
        } catch (e: Exception) {
            _sessionState.value = ARSessionState.Error("Failed to resume AR session: ${e.localizedMessage}")
        }
    }

    /**
     * Picks a 30 fps back-camera config with the sharpest GPU texture for the preview.
     * Must run before the session is first resumed.
     */
    private fun selectCameraConfig(session: Session) {
        val filter = CameraConfigFilter(session)
            .setTargetFps(EnumSet.of(CameraConfig.TargetFps.TARGET_FPS_30))
            .setDepthSensorUsage(EnumSet.of(CameraConfig.DepthSensorUsage.DO_NOT_USE))
        val configs = session.getSupportedCameraConfigs(filter)
        val best = configs.maxByOrNull { it.textureSize.width * it.textureSize.height } ?: return
        session.cameraConfig = best
        Log.i(TAG, "Camera config: texture ${best.textureSize}, image ${best.imageSize}, fps ${best.fpsRange}")
    }

    /**
     * Pauses the AR session. Should be called from Activity/Composable onPause.
     */
    fun onPause() {
        mainHandler.removeCallbacksAndMessages(null)
        // Torch off when leaving, so it never comes back on unexpectedly
        if (_torchOn.value) setTorch(false)
        session?.pause()
    }

    /**
     * Destroys the AR session and detaches all anchors. Should be called when component is destroyed.
     */
    fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        pendingActions.clear()
        clearAll()
        session?.close()
        session = null
        _uiState.value = MeasureUiState()
        _sessionState.value = ARSessionState.Idle
    }
}
