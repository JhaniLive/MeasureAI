package com.jhani.measurear.ar

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import com.google.ar.core.Camera
import com.google.ar.core.Coordinates2d
import com.google.ar.core.DepthPoint
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.SessionPausedException
import com.jhani.measurear.measurement.MeasureUiState
import com.jhani.measurear.measurement.MeasuredArea
import com.jhani.measurear.measurement.MeasurementSummary
import com.jhani.measurear.measurement.ScreenArea
import com.jhani.measurear.measurement.polygonArea
import com.jhani.measurear.measurement.MeasuredLine
import com.jhani.measurear.measurement.PlacedPoint
import com.jhani.measurear.measurement.ReticleState
import com.jhani.measurear.measurement.ScreenSegment
import com.jhani.measurear.measurement.SnapAxis
import com.jhani.measurear.measurement.StraightenMode
import com.jhani.measurear.measurement.distanceBetween
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/**
 * GLSurfaceView that renders the ARCore camera feed and runs the measuring logic on the GL
 * thread: it aims a reticle from the screen center, filters its position over several frames,
 * applies queued add/undo/clear actions, and publishes every segment projected to view pixels
 * so the Compose overlay can draw crisp lines, points and labels.
 */
class ARSurfaceView(
    context: Context,
    private val sessionManager: ARSessionManager
) : GLSurfaceView(context), GLSurfaceView.Renderer {

    private val backgroundRenderer = BackgroundRenderer()
    private val planeDotRenderer = PlaneDotRenderer()
    private val depthTexture = DepthTexture()
    private val pointCloudRenderer = PointCloudRenderer()

    /** Latest debug stats (GL thread), and what the crosshair hit this frame. */
    private var debugStats: String? = null
    private var lastHitKind = "none"
    private var lastHitDistance = 0f

    // View-normalized -> depth texture coordinates (origin, U axis, V axis)
    private val viewCorners = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f)
    private val textureCorners = FloatArray(6)
    private val viewToUv = FloatArray(6)

    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)

    private var viewportWidth = 0
    private var viewportHeight = 0

    private val density = resources.displayMetrics.density
    private val reticleFilter = ReticleFilter()
    private var lastReticleState = ReticleState.SEARCHING
    private var lastSnapAxis: SnapAxis? = null

    /** Recent reticle targets (frame time in ns → target) for press-time lookup. GL thread. */
    private val reticleHistory = ArrayDeque<Pair<Long, ReticleTarget?>>()

    /** Magnifier state (GL thread): shown, and the camera pose it was shown at. */
    private var loupeVisible = false
    private var loupeAnchor: Pose? = null

    /**
     * Line of sight each estimated point was stamped along, and whether it was on an edge.
     * Lets a measurement fix whichever end got a wrong depth. GL thread.
     */
    private class PointRay(val origin: FloatArray, val dir: FloatArray, val ambiguous: Boolean)
    private val pointRays = HashMap<com.google.ar.core.Anchor, PointRay>()

    /** Recent camera poses (frame time in ns → pose) for steadiness checks. GL thread. */
    private val cameraHistory = ArrayDeque<Pair<Long, Pose>>()
    private val inverseViewProjection = FloatArray(16)

    companion object {
        private const val TAG = "ARSurfaceView"

        private const val NEAR_PLANE = 0.05f
        private const val FAR_PLANE = 100f

        // Keep this much reticle history, and stamp from this long before the press (ns)
        private const val HISTORY_NANOS = 600_000_000L
        private const val PRESS_LEAD_NANOS = 80_000_000L

        // Reticle snaps onto an existing endpoint within this on-screen radius
        private const val SNAP_RADIUS_DP = 28f

        // Planes smaller than this (per side, meters) are too young to trust for placement
        private const val MIN_PLANE_EXTENT = 0.15f

        // ARCore is most accurate within ~0.5-5 m; ignore hits further away
        private const val MAX_HIT_DISTANCE = 5f

        // A plane hit this much farther than the nearest depth hit is behind an object (meters)
        private const val OCCLUSION_TOLERANCE = 0.03f

        // Edge-robust estimates: center + two rings (dp offsets) of hit-test samples
        private val EDGE_SAMPLE_OFFSETS: List<FloatArray> = buildList {
            add(floatArrayOf(0f, 0f))
            for (radius in listOf(8f, 16f)) {
                for (i in 0 until 8) {
                    val a = (Math.PI * 2 * i / 8).toFloat()
                    add(floatArrayOf(kotlin.math.cos(a) * radius, kotlin.math.sin(a) * radius))
                }
            }
        }
        private const val MIN_EDGE_SAMPLES = 5

        // Height from a base: max gap between A's line of sight and the base's vertical line
        private const val BASE_VERTICAL_GAP_MIN = 0.08f
        private const val BASE_VERTICAL_GAP_RATIO = 0.15f

        private const val EDGE_TOLERANCE_RATIO = 0.08f   // center may be 8% beyond foreground
        private const val EDGE_SPREAD_MIN = 0.06f        // meters
        private const val EDGE_SPREAD_RATIO = 0.12f

        // Alignment assists for the live segment
        private const val VERTICAL_SNAP_DP = 30f
        private const val LEVEL_SNAP_SIN = 0.07f        // ~4 degrees
        private const val PARALLEL_SNAP_COS = 0.996f    // ~5 degrees
        private const val PERPENDICULAR_SNAP_SIN = 0.087f // ~5 degrees
        private const val GUIDE_EXTENSION = 0.5f
    }

    /**
     * Where the reticle currently lands.
     *
     * @param onSurface whether a point placed here counts as accurate (not an estimate)
     * @param axis alignment the position was snapped to, with its world-space guide line
     */
    private class ReticleTarget(
        val pose: Pose,
        val state: ReticleState,
        val plane: Plane?,
        val snappedTo: PlacedPoint?,
        val onSurface: Boolean,
        val axis: SnapAxis? = null,
        val guideStart: Pose? = null,
        val guideEnd: Pose? = null,
        val ambiguous: Boolean = false,
        /** Corrected position for point A (height measured up from a reliable base). */
        val replaceStart: Pose? = null
    )

    init {
        preserveEGLContextOnPause = true
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        setRenderer(this)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        backgroundRenderer.createOnGlThread()
        planeDotRenderer.createOnGlThread()
        depthTexture.createOnGlThread()
        pointCloudRenderer.createOnGlThread()
        sessionManager.session?.setCameraTextureName(backgroundRenderer.textureId)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        viewportWidth = width
        viewportHeight = height
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayRotation = @Suppress("DEPRECATION") windowManager.defaultDisplay.rotation
        sessionManager.session?.setDisplayGeometry(displayRotation, width, height)
    }

    // TEMP perf diagnostics: per-stage time (ns), summed and logged every ~2 s
    private val perfTotals = LongArray(6)
    private var perfFrames = 0
    private var perfWindowStart = 0L
    private var perfLastFrame = 0L
    private var perfMaxGap = 0L

    private fun perfLog(session: Session) {
        val now = System.nanoTime()
        if (perfLastFrame != 0L) perfMaxGap = max(perfMaxGap, now - perfLastFrame)
        perfLastFrame = now
        if (perfWindowStart == 0L) perfWindowStart = now
        perfFrames++
        val elapsed = now - perfWindowStart
        if (elapsed < 2_000_000_000L) return
        val fps = perfFrames * 1e9 / elapsed
        fun ms(i: Int) = perfTotals[i] / 1e6 / perfFrames
        val planes = session.getAllTrackables(Plane::class.java)
            .filter { it.trackingState == TrackingState.TRACKING && it.subsumedBy == null }
        val line = "fps=%.1f maxGap=%.0fms | update=%.1f reticle=%.1f grid=%.1f loupe=%.1f publish=%.1f total=%.1f ms | planes=%d %s".format(
            fps, perfMaxGap / 1e6, ms(0), ms(1), ms(2), ms(3), ms(4), ms(5), planes.size,
            planes.joinToString { "%s %.2fx%.2f".format(it.type.name.take(5), it.extentX, it.extentZ) }
        )
        Log.i("Perf", line)
        debugStats = buildString {
            appendLine("FPS  %.0f   (worst gap %.0f ms)".format(fps, perfMaxGap / 1e6))
            appendLine("Frame %.1f ms: update %.1f · aim %.1f · grid %.1f".format(ms(5), ms(0), ms(1), ms(2)))
            appendLine("Surfaces %d".format(planes.size))
            planes.take(3).forEach { appendLine("  %s %.2f × %.2f m".format(it.type.name.take(10), it.extentX, it.extentZ)) }
            appendLine("Feature points %d".format(pointCloudRenderer.lastPointCount))
            append("Depth %s".format(if (sessionManager.depthEnabled) "on" else "off"))
        }
        perfTotals.fill(0)
        perfFrames = 0
        perfWindowStart = now
        perfMaxGap = 0L
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val tFrame = System.nanoTime()

        val session = sessionManager.session ?: return

        try {
            // Re-bind texture ID if session was recreated or reset
            session.setCameraTextureName(backgroundRenderer.textureId)

            val frame = session.update()
            backgroundRenderer.draw(frame)
            val tUpdate = System.nanoTime()

            val camera = frame.camera
            if (camera.trackingState != TrackingState.TRACKING) {
                reticleFilter.reset()
                applyActions(session, frame, reticle = null)
                publishNotTracking(camera)
                return
            }

            camera.getProjectionMatrix(projectionMatrix, 0, NEAR_PLANE, FAR_PLANE)
            camera.getViewMatrix(viewMatrix, 0)

            val reticle = applyAlignment(findReticleTarget(frame))
            val tReticle = System.nanoTime()
            if ((reticle?.state == ReticleState.SNAPPED && lastReticleState != ReticleState.SNAPPED) ||
                (reticle?.axis != null && reticle.axis != lastSnapAxis)
            ) {
                post { performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
            }
            lastReticleState = reticle?.state ?: ReticleState.SEARCHING
            lastSnapAxis = reticle?.axis

            val now = System.nanoTime()
            reticleHistory.addLast(now to reticle)
            while (reticleHistory.isNotEmpty() && now - reticleHistory.first().first > HISTORY_NANOS) {
                reticleHistory.removeFirst()
            }

            // Teal dot grid on detected surfaces, highlighted around the reticle
            val allPlanes = session.getAllTrackables(Plane::class.java)
            if (sessionManager.showGrid) {
                val occlusion = if (sessionManager.depthEnabled && sessionManager.gridOcclusion) {
                    depthTexture.update(frame)
                    updateViewToUv(frame)
                    depthTexture
                } else {
                    null
                }
                planeDotRenderer.drawPlanes(
                    allPlanes,
                    reticle?.pose,
                    projectionMatrix,
                    viewMatrix,
                    occlusion,
                    viewToUv,
                    viewportWidth,
                    viewportHeight
                )
            }

            if (sessionManager.debugView) {
                pointCloudRenderer.draw(frame, allPlanes, projectionMatrix, viewMatrix)
            }
            val tGrid = System.nanoTime()

            // Magnifier next to the crosshair, only while the aim is steady and close
            loupeVisible = updateLoupeVisibility(reticle, camera.pose, now)
            if (loupeVisible) {
                backgroundRenderer.drawLoupe(
                    frame,
                    centerX = LoupeSpec.centerX(viewportWidth.toFloat(), density),
                    centerY = LoupeSpec.centerY(viewportHeight.toFloat(), density),
                    radius = LoupeSpec.RADIUS_DP * density,
                    zoom = LoupeSpec.ZOOM,
                    viewportWidth = viewportWidth,
                    viewportHeight = viewportHeight
                )
            }

            val tLoupe = System.nanoTime()

            applyActions(session, frame, reticle)
            publishTracking(reticle, camera.pose)

            val tEnd = System.nanoTime()
            perfTotals[0] += tUpdate - tFrame
            perfTotals[1] += tReticle - tUpdate
            perfTotals[2] += tGrid - tReticle
            perfTotals[3] += tLoupe - tGrid
            perfTotals[4] += tEnd - tLoupe
            perfTotals[5] += tEnd - tFrame
            perfLog(session)

        } catch (_: SessionPausedException) {
            // Expected briefly while the activity is paused
        } catch (e: Exception) {
            Log.e(TAG, "Frame update failed", e)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Reticle
    // ---------------------------------------------------------------------------------------

    /**
     * Hit tests the screen center. A hit inside a tracked, reasonably sized plane is accurate;
     * depth and oriented feature points are accepted as estimates. The position is filtered
     * over recent frames, and snaps onto an existing endpoint near the screen center.
     */
    private fun findReticleTarget(frame: Frame): ReticleTarget? {
        if (viewportWidth == 0 || viewportHeight == 0) return null

        val cx = viewportWidth / 2f
        val cy = viewportHeight / 2f

        snapTarget(cx, cy)?.let { point ->
            reticleFilter.reset()
            return ReticleTarget(
                point.anchor.pose, ReticleState.SNAPPED,
                plane = null, snappedTo = point, onSurface = point.onSurface
            )
        }

        val cameraPose = frame.camera.pose
        // Hits are sorted nearest first. A plane hit is only used when nothing is in front of
        // it; otherwise an object (e.g. a cup) is occluding the plane and the nearer hit wins.
        val hits = frame.hitTest(cx, cy).filter { it.distance <= MAX_HIT_DISTANCE }
        val planeHit = hits.firstOrNull { it.isOnStablePlane(cameraPose) }
        val estimateHit = hits.firstOrNull { it.isEstimate() }
        val usePlane = planeHit != null &&
            (estimateHit == null || planeHit.distance <= estimateHit.distance + OCCLUSION_TOLERANCE)
        val hit = if (usePlane) planeHit else estimateHit
        lastHitKind = when (val t = hit?.trackable) {
            null -> "none"
            is Plane -> "PLANE"
            is DepthPoint -> "depth"
            is Point -> "feature"
            else -> t.javaClass.simpleName
        }
        lastHitDistance = hit?.distance ?: 0f
        if (hit == null) {
            reticleFilter.reset()
            return null
        }

        val plane = if (usePlane) planeHit?.trackable as? Plane else null

        // Estimates at object edges can grab the background's depth: refine from neighbors
        var rawPose = hit.hitPose
        var ambiguous = false
        if (plane == null) {
            robustEstimate(frame, cx, cy, hit)?.let { (pose, isAmbiguous) ->
                rawPose = pose
                ambiguous = isAmbiguous
            }
        }

        val filtered = reticleFilter.add(rawPose, sourceKey = plane ?: "estimate")
        return ReticleTarget(
            pose = filtered,
            state = if (plane != null) ReticleState.SURFACE else ReticleState.ESTIMATE,
            plane = plane,
            snappedTo = null,
            onSurface = plane != null,
            ambiguous = ambiguous
        )
    }

    private fun normalize(v: FloatArray) {
        val len = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        if (len > 1e-6f) for (k in 0..2) v[k] /= len
    }

    /**
     * Depth estimates are least reliable exactly at object edges: the center pixel can sit on
     * the background behind the object being aimed at. Samples estimate hits in two small
     * rings around the center; if the center is clearly farther than the nearby foreground,
     * the point is pulled forward along the same line of sight onto the foreground surface.
     *
     * @return refined pose and whether the neighborhood depth is ambiguous, or null to keep
     *   the center hit as is (too few samples)
     */
    private fun robustEstimate(frame: Frame, cx: Float, cy: Float, center: HitResult): Pair<Pose, Boolean>? {
        val distances = ArrayList<Float>(EDGE_SAMPLE_OFFSETS.size)
        for (offset in EDGE_SAMPLE_OFFSETS) {
            val x = cx + offset[0] * density
            val y = cy + offset[1] * density
            frame.hitTest(x, y)
                .firstOrNull { it.distance <= MAX_HIT_DISTANCE && it.isEstimate() }
                ?.let { distances.add(it.distance) }
        }
        if (distances.size < MIN_EDGE_SAMPLES) return null

        distances.sort()
        val foreground = distances[distances.size / 4]
        val upper = distances[distances.size * 3 / 4]
        val ambiguous = upper - foreground > max(EDGE_SPREAD_MIN, foreground * EDGE_SPREAD_RATIO)

        // Center consistent with the foreground: keep it exactly
        if (center.distance <= foreground * (1f + EDGE_TOLERANCE_RATIO)) return center.hitPose to ambiguous

        // Center disagrees with its neighbors (object edge): keep the hit but flag it.
        // Pulling it onto a nearer neighbor proved worse — that can be a different object.
        return center.hitPose to true
    }

    // ---------------------------------------------------------------------------------------
    // Alignment assists
    // ---------------------------------------------------------------------------------------

    /**
     * While a measurement is in progress, snaps the end point so straight lines are easy:
     * vertical through the start point (heights), level, or parallel / perpendicular to the
     * previous line. Endpoint snapping takes priority.
     */
    private fun applyAlignment(base: ReticleTarget?): ReticleTarget? {
        val pending = sessionManager.pendingStart ?: return base
        if (pending.anchor.trackingState != TrackingState.TRACKING) return base
        if (base?.state == ReticleState.SNAPPED) return base

        val a = pending.anchor.pose
        if (pending.onSurface) {
            // A is on a detected surface: its vertical line is trustworthy
            verticalTarget(a, pending, base)?.let { return it }
        } else if (base != null && base.plane != null) {
            // A is an estimate (e.g. top of a bottle, depth may be wrong) but the aim is on a
            // detected surface: measure the height up from this reliable base instead
            baseVerticalTarget(pending, base)?.let { return it }
        }
        base ?: return null

        var end = floatArrayOf(base.pose.tx(), base.pose.ty(), base.pose.tz())
        val dx = end[0] - a.tx()
        val dy = end[1] - a.ty()
        val dz = end[2] - a.tz()
        val horizontal = sqrt(dx * dx + dz * dz)
        val length = sqrt(dx * dx + dy * dy + dz * dz)
        if (length < 0.02f || abs(dy) / length > LEVEL_SNAP_SIN) return base

        // Nearly level: flatten onto the start point's height
        end[1] = a.ty()
        var axis = SnapAxis.LEVEL
        var dir = floatArrayOf(dx / horizontal, 0f, dz / horizontal)

        // Nearly parallel / perpendicular to the previous (level) line
        previousLevelDirection()?.let { u ->
            val cos = dir[0] * u[0] + dir[2] * u[2]
            val perp = floatArrayOf(-u[2], 0f, u[0])
            val snapDir = when {
                abs(cos) >= PARALLEL_SNAP_COS -> { axis = SnapAxis.PARALLEL; u }
                abs(cos) <= PERPENDICULAR_SNAP_SIN -> { axis = SnapAxis.PERPENDICULAR; perp }
                else -> null
            }
            if (snapDir != null) {
                val t = dx * snapDir[0] + dz * snapDir[2]
                end = floatArrayOf(a.tx() + snapDir[0] * t, a.ty(), a.tz() + snapDir[2] * t)
                dir = if (t >= 0) snapDir else floatArrayOf(-snapDir[0], 0f, -snapDir[2])
            }
        }

        val endPose = Pose(end, base.pose.rotationQuaternion)
        return ReticleTarget(
            pose = endPose,
            state = base.state,
            plane = base.plane,
            snappedTo = null,
            onSurface = base.onSurface,
            axis = axis,
            guideStart = offsetPose(a, dir, -GUIDE_EXTENSION),
            guideEnd = offsetPose(endPose, dir, GUIDE_EXTENSION)
        )
    }

    /**
     * When the screen center is close to the vertical line through the start point, returns
     * the point on that line closest to the camera's center ray. Needs no surface hit, so it
     * measures heights of objects like cups and bottles from a start point on the table.
     */
    private fun verticalTarget(a: Pose, pending: PlacedPoint, base: ReticleTarget?): ReticleTarget? {
        // Aiming at the same horizontal surface as the start: the user is measuring along it
        // (a line running away from the camera also looks vertical on screen)
        val basePlane = base?.plane
        if (basePlane != null && basePlane.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
            abs(base.pose.ty() - a.ty()) < 0.02f
        ) {
            return null
        }

        val top = Pose(floatArrayOf(a.tx(), a.ty() + 0.3f, a.tz()), a.rotationQuaternion)
        val sa = projectToScreen(a) ?: return null
        val sb = projectToScreen(top) ?: return null
        val cx = viewportWidth / 2f
        val cy = viewportHeight / 2f

        // Distance from the screen center to the projected vertical line
        val lx = sb[0] - sa[0]
        val ly = sb[1] - sa[1]
        val lineLength = hypot(lx, ly)
        if (lineLength < 1f) return null // looking straight down the vertical line
        val screenDistance = abs(lx * (sa[1] - cy) - ly * (sa[0] - cx)) / lineLength
        if (screenDistance > VERTICAL_SNAP_DP * density) return null

        val ray = centerRay() ?: return null
        val origin = ray[0]
        val d = ray[1]
        // Closest point between line A + s*(0,1,0) and ray origin + t*d
        val w0 = floatArrayOf(a.tx() - origin[0], a.ty() - origin[1], a.tz() - origin[2])
        val b = d[1]
        val c = d[0] * d[0] + d[1] * d[1] + d[2] * d[2]
        val dd = w0[1]
        val e = d[0] * w0[0] + d[1] * w0[1] + d[2] * w0[2]
        val denom = c - b * b
        if (denom < 1e-4f) return null
        val height = (b * e - c * dd) / denom
        if (abs(height) < 0.01f || abs(height) > 3f) return null

        val endPose = Pose(floatArrayOf(a.tx(), a.ty() + height, a.tz()), a.rotationQuaternion)
        val up = floatArrayOf(0f, if (height >= 0) 1f else -1f, 0f)
        return ReticleTarget(
            pose = endPose,
            state = base?.state?.takeIf { it != ReticleState.SNAPPED } ?: ReticleState.SURFACE,
            plane = null,
            snappedTo = null,
            // Height accuracy depends on the start point; the end comes from geometry, not a hit
            onSurface = pending.onSurface,
            axis = SnapAxis.VERTICAL,
            guideStart = offsetPose(a, up, -GUIDE_EXTENSION),
            guideEnd = offsetPose(endPose, up, GUIDE_EXTENSION)
        )
    }

    /**
     * Height measured up from a reliable base. The vertical line through the base point (on a
     * detected surface) is compared with the line of sight A was stamped along; if the two
     * nearly meet, this is a height: A is corrected to the point on the vertical line closest
     * to its line of sight, so A's own (possibly wrong) depth is never used.
     */
    private fun baseVerticalTarget(pending: PlacedPoint, base: ReticleTarget): ReticleTarget? {
        val ray = pointRays[pending.anchor] ?: return null
        val p = base.pose
        val o = ray.origin
        val d = ray.dir

        // Closest points between vertical line P + s*(0,1,0) and ray O + t*d
        val w0x = p.tx() - o[0]
        val w0y = p.ty() - o[1]
        val w0z = p.tz() - o[2]
        val b = d[1]
        val e = d[0] * w0x + d[1] * w0y + d[2] * w0z
        val denom = 1f - b * b // |d| = 1
        if (denom < 1e-3f) return null // line of sight is itself vertical
        val s = (b * e - w0y) / denom
        val t = (e - b * w0y) / denom
        if (t <= 0f || s < 0.01f || s > 3f) return null

        // The two lines must nearly meet for this to be a height of the same object
        val topX = p.tx()
        val topY = p.ty() + s
        val topZ = p.tz()
        val gapX = topX - (o[0] + d[0] * t)
        val gapY = topY - (o[1] + d[1] * t)
        val gapZ = topZ - (o[2] + d[2] * t)
        val gap = sqrt(gapX * gapX + gapY * gapY + gapZ * gapZ)
        if (gap > max(BASE_VERTICAL_GAP_MIN, t * BASE_VERTICAL_GAP_RATIO)) return null

        val top = Pose(floatArrayOf(topX, topY, topZ), pending.anchor.pose.rotationQuaternion)
        val up = floatArrayOf(0f, 1f, 0f)
        return ReticleTarget(
            pose = p,
            state = ReticleState.SURFACE,
            plane = base.plane,
            snappedTo = null,
            onSurface = true,
            axis = SnapAxis.VERTICAL,
            guideStart = offsetPose(p, up, -GUIDE_EXTENSION),
            guideEnd = offsetPose(top, up, GUIDE_EXTENSION),
            replaceStart = top
        )
    }

    /** Horizontal unit direction of the most recent completed line, if it is roughly level. */
    private fun previousLevelDirection(): FloatArray? {
        val line = sessionManager.lines.lastOrNull() ?: return null
        val s0 = line.start.anchor.pose
        val s1 = line.end.anchor.pose
        val dx = s1.tx() - s0.tx()
        val dy = s1.ty() - s0.ty()
        val dz = s1.tz() - s0.tz()
        val horizontal = sqrt(dx * dx + dz * dz)
        if (horizontal < 0.02f || abs(dy) / sqrt(dx * dx + dy * dy + dz * dz) > LEVEL_SNAP_SIN * 2) return null
        return floatArrayOf(dx / horizontal, 0f, dz / horizontal)
    }

    /** World-space origin and direction of the ray through the screen center. */
    private fun centerRay(): Array<FloatArray>? {
        val viewProjection = FloatArray(16)
        Matrix.multiplyMM(viewProjection, 0, projectionMatrix, 0, viewMatrix, 0)
        if (!Matrix.invertM(inverseViewProjection, 0, viewProjection, 0)) return null
        val near = unproject(0f, 0f, -1f)
        val far = unproject(0f, 0f, 1f)
        val dir = floatArrayOf(far[0] - near[0], far[1] - near[1], far[2] - near[2])
        val len = sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2])
        if (len < 1e-6f) return null
        return arrayOf(near, floatArrayOf(dir[0] / len, dir[1] / len, dir[2] / len))
    }

    private fun unproject(ndcX: Float, ndcY: Float, ndcZ: Float): FloatArray {
        val out = FloatArray(4)
        Matrix.multiplyMV(out, 0, inverseViewProjection, 0, floatArrayOf(ndcX, ndcY, ndcZ, 1f), 0)
        return floatArrayOf(out[0] / out[3], out[1] / out[3], out[2] / out[3])
    }

    private fun offsetPose(pose: Pose, dir: FloatArray, distance: Float): Pose =
        Pose(
            floatArrayOf(pose.tx() + dir[0] * distance, pose.ty() + dir[1] * distance, pose.tz() + dir[2] * distance),
            pose.rotationQuaternion
        )

    /** Closest tracked endpoint whose on-screen position is within the snap radius of (x, y). */
    private fun snapTarget(x: Float, y: Float): PlacedPoint? {
        val radius = SNAP_RADIUS_DP * density
        return allEndpoints()
            .filter { it.anchor.trackingState == TrackingState.TRACKING }
            .mapNotNull { point ->
                val screen = projectToScreen(point.anchor.pose) ?: return@mapNotNull null
                val distance = hypot(screen[0] - x, screen[1] - y)
                if (distance <= radius) point to distance else null
            }
            .minByOrNull { it.second }
            ?.first
    }

    /** Endpoints of completed lines. The pending start is excluded so B can't snap onto A. */
    private fun allEndpoints(): List<PlacedPoint> {
        val points = ArrayList<PlacedPoint>()
        sessionManager.lines.forEach {
            points.add(it.start)
            points.add(it.end)
        }
        return points
    }

    private fun HitResult.isOnStablePlane(cameraPose: Pose): Boolean {
        val plane = trackable as? Plane ?: return false
        return plane.trackingState == TrackingState.TRACKING &&
            isCameraInFrontOf(plane, cameraPose) &&
            plane.subsumedBy == null &&
            plane.type != Plane.Type.HORIZONTAL_DOWNWARD_FACING &&
            plane.extentX >= MIN_PLANE_EXTENT &&
            plane.extentZ >= MIN_PLANE_EXTENT &&
            plane.isPoseInPolygon(hitPose)
    }

    /** Camera is on the front side of the plane (from the ARCore hello_ar sample). */
    private fun HitResult.isCameraInFrontOf(plane: Plane, cameraPose: Pose): Boolean {
        val normal = FloatArray(3)
        hitPose.getTransformedAxis(1, 1.0f, normal, 0)
        val toCamera = floatArrayOf(
            cameraPose.tx() - hitPose.tx(),
            cameraPose.ty() - hitPose.ty(),
            cameraPose.tz() - hitPose.tz()
        )
        return normal[0] * toCamera[0] + normal[1] * toCamera[1] + normal[2] * toCamera[2] > 0f
    }

    private fun HitResult.isEstimate(): Boolean {
        val t = trackable
        return t is DepthPoint ||
            (t is Point && t.orientationMode == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL)
    }

    // ---------------------------------------------------------------------------------------
    // Actions
    // ---------------------------------------------------------------------------------------

    private fun applyActions(session: Session, frame: Frame, reticle: ReticleTarget?) {
        while (true) {
            when (val action = sessionManager.pollAction() ?: return) {
                is MeasureAction.AddPoint -> addPoint(session, frame, reticleAt(action.pressedAtNanos, reticle), action.force)
                MeasureAction.Undo -> sessionManager.undo()
                MeasureAction.Clear -> {
                    sessionManager.clearAll()
                    pointRays.clear()
                }
                is MeasureAction.DeleteLine -> sessionManager.deleteLine(action.index)
                is MeasureAction.StraightenLine -> straightenLine(session, action.index, action.mode)
            }
        }
    }

    /**
     * Replaces a line's end point so the line is exactly level (end moved to the start's
     * height) or exactly vertical (end moved directly above/below the start). Any area built
     * from the line no longer matches its shape, so it is removed.
     */
    private fun straightenLine(session: Session, index: Int, mode: StraightenMode) {
        val lines = sessionManager.lines
        val line = lines.getOrNull(index) ?: return
        val start = line.start.anchor.pose
        val end = line.end.anchor.pose
        val target = when (mode) {
            StraightenMode.LEVEL -> floatArrayOf(end.tx(), start.ty(), end.tz())
            StraightenMode.VERTICAL -> floatArrayOf(start.tx(), end.ty(), start.tz())
        }
        val newEnd = PlacedPoint(
            session.createAnchor(Pose(target, end.rotationQuaternion)),
            line.end.onSurface
        )
        line.end.anchor.detach()
        val straightened = MeasuredLine(line.start, newEnd)
        lines[index] = straightened
        sessionManager.emitCompleted(
            if (mode == StraightenMode.LEVEL) "Level line" else "Vertical line",
            lineSummary(straightened)
        )
        sessionManager.areas.removeAll { area -> area.lines.any { it === line } }
        sessionManager.chain.clear()
        sessionManager.pendingContinuesChain = false
        post { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
    }

    private fun lineSummary(line: MeasuredLine): MeasurementSummary {
        val s = line.start.anchor.pose
        val e = line.end.anchor.pose
        val dx = e.tx() - s.tx()
        val dz = e.tz() - s.tz()
        return MeasurementSummary(
            distanceBetween(s, e),
            isArea = false,
            isEstimate = line.isEstimate,
            horizontal = sqrt(dx * dx + dz * dz),
            vertical = abs(e.ty() - s.ty())
        )
    }

    /**
     * Shows the magnifier once the aim has been steady for [LoupeSpec.STEADY_NANOS] on a target
     * within [LoupeSpec.MAX_DISTANCE_METERS]; hides it again after a clear move.
     */
    private fun updateLoupeVisibility(reticle: ReticleTarget?, cameraPose: Pose, now: Long): Boolean {
        cameraHistory.addLast(now to cameraPose)
        while (cameraHistory.isNotEmpty() && now - cameraHistory.first().first > HISTORY_NANOS) {
            cameraHistory.removeFirst()
        }

        if (!sessionManager.magnifierEnabled || reticle == null ||
            distanceBetween(cameraPose, reticle.pose) > LoupeSpec.MAX_DISTANCE_METERS
        ) {
            loupeAnchor = null
            return false
        }

        val anchor = loupeAnchor
        if (loupeVisible && anchor != null) {
            if (rotationDegrees(anchor, cameraPose) <= LoupeSpec.HIDE_DEGREES &&
                distanceBetween(anchor, cameraPose) <= LoupeSpec.HIDE_METERS
            ) {
                return true
            }
            loupeAnchor = null
            return false
        }

        // Steady: the phone barely moved over the whole recent window
        val spanned = cameraHistory.firstOrNull()?.let { now - it.first >= LoupeSpec.STEADY_NANOS } == true
        val steady = spanned && cameraHistory
            .filter { now - it.first <= LoupeSpec.STEADY_NANOS }
            .all { (_, pose) ->
                rotationDegrees(pose, cameraPose) <= LoupeSpec.STEADY_DEGREES &&
                    distanceBetween(pose, cameraPose) <= LoupeSpec.STEADY_METERS
            }
        if (steady) loupeAnchor = cameraPose
        return steady
    }

    /** Angle in degrees between two poses' orientations. */
    private fun rotationDegrees(a: Pose, b: Pose): Float {
        val qa = a.rotationQuaternion
        val qb = b.rotationQuaternion
        val dot = abs(qa[0] * qb[0] + qa[1] * qb[1] + qa[2] * qb[2] + qa[3] * qb[3]).coerceAtMost(1f)
        return Math.toDegrees(2.0 * kotlin.math.acos(dot.toDouble())).toFloat()
    }

    /**
     * The reticle as it was just before [pressedAtNanos]. Pressing the screen tips the phone,
     * so using the pre-press target keeps the point exactly where the user was aiming.
     */
    private fun reticleAt(pressedAtNanos: Long, current: ReticleTarget?): ReticleTarget? {
        val cutoff = pressedAtNanos - PRESS_LEAD_NANOS
        val before = reticleHistory.lastOrNull { it.first <= cutoff }
        // Only trust history that still aims at something; otherwise fall back to now
        return before?.second ?: current
    }

    private fun addPoint(session: Session, frame: Frame, reticle: ReticleTarget?, force: Boolean) {
        if (reticle == null) {
            sessionManager.showHint("Aim the circle at a surface first")
            return
        }
        // Without a depth sensor, estimates can be off by tens of centimeters: only measure
        // on detected surfaces unless the user explicitly forces it
        if (!reticle.onSurface && !force) {
            sessionManager.showHint("Aim at a detected surface (teal) — or long-press Stamp to place an estimate")
            return
        }

        val point = if (reticle.plane != null && reticle.axis == null) {
            // Attached to the plane so the point follows it as ARCore refines the surface
            PlacedPoint(reticle.plane.createAnchor(reticle.pose), onSurface = true)
        } else {
            PlacedPoint(session.createAnchor(reticle.pose), reticle.onSurface)
        }

        // Remember the line of sight of freely aimed estimates (not snapped or axis-locked points)
        if (!point.onSurface && reticle.snappedTo == null && reticle.axis == null) {
            val cam = frame.camera.pose
            val p = point.anchor.pose
            val dir = floatArrayOf(p.tx() - cam.tx(), p.ty() - cam.ty(), p.tz() - cam.tz())
            normalize(dir)
            pointRays[point.anchor] = PointRay(floatArrayOf(cam.tx(), cam.ty(), cam.tz()), dir, reticle.ambiguous)
        }

        // Height from a reliable base: swap A for its corrected position first
        val replaceWith = reticle.replaceStart
        val current = sessionManager.pendingStart
        if (replaceWith != null && current != null) {
            val corrected = PlacedPoint(session.createAnchor(replaceWith), onSurface = true)
            pointRays.remove(current.anchor)
            current.anchor.detach()
            sessionManager.pendingStart = corrected
        }

        val chain = sessionManager.chain
        val pendingStart = sessionManager.pendingStart
        val start = pendingStart
        val endPoint = point
        if (start == null) {
            sessionManager.pendingStart = point
            // Starting from the end of the chain's last line continues that chain
            sessionManager.pendingContinuesChain =
                reticle.snappedTo != null && chain.isNotEmpty() && reticle.snappedTo === chain.last().end
        } else {
            val line = MeasuredLine(start, endPoint)
            sessionManager.lines.add(line)
            sessionManager.emitCompleted("Line", lineSummary(line))
            sessionManager.pendingStart = null
            if (!sessionManager.pendingContinuesChain) chain.clear()
            chain.add(line)
            sessionManager.pendingContinuesChain = false

            // Ending on the chain's first point closes the shape
            if (chain.size >= 3 && reticle.snappedTo != null && reticle.snappedTo === chain.first().start) {
                val area = MeasuredArea(chain.toList())
                sessionManager.areas.add(area)
                sessionManager.emitCompleted(
                    "Area",
                    MeasurementSummary(
                        polygonArea(area.lines.map { it.start.anchor.pose }),
                        isArea = true,
                        isEstimate = area.isEstimate
                    )
                )
                chain.clear()
                sessionManager.showHint("Shape closed — area measured")
            }
        }
        if (reticle.ambiguous) {
            sessionManager.showHint("Placed on an edge — depth is uncertain here. Undo and aim slightly inside if it looks off")
        } else if (!point.onSurface) {
            sessionManager.showHint("Not on a detected flat surface — this point is an estimate")
        }
        post { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
    }

    // ---------------------------------------------------------------------------------------
    // Publishing
    // ---------------------------------------------------------------------------------------

    private fun publishNotTracking(camera: Camera) {
        sessionManager.publishUiState(
            MeasureUiState(
                isTracking = false,
                trackingMessage = trackingMessage(camera.trackingFailureReason),
                hasPendingPoint = sessionManager.pendingStart != null,
                lineCount = sessionManager.lines.size
            )
        )
    }

    private fun publishTracking(reticle: ReticleTarget?, cameraPose: Pose) {
        val segments = ArrayList<ScreenSegment>()

        val summaries = ArrayList<MeasurementSummary>()

        sessionManager.lines.forEachIndexed { index, line ->
            val s = line.start.anchor.pose
            val e = line.end.anchor.pose
            val dx = e.tx() - s.tx()
            val dz = e.tz() - s.tz()
            summaries.add(
                MeasurementSummary(
                    distanceBetween(s, e),
                    isArea = false,
                    isEstimate = line.isEstimate,
                    horizontal = sqrt(dx * dx + dz * dz),
                    vertical = abs(e.ty() - s.ty())
                )
            )
            if (line.start.anchor.trackingState != TrackingState.TRACKING ||
                line.end.anchor.trackingState != TrackingState.TRACKING
            ) {
                return@forEachIndexed
            }
            projectSegment(line.start.anchor.pose, line.end.anchor.pose, line.isEstimate, isLive = false)
                ?.copy(lineIndex = index)
                ?.let(segments::add)
        }

        val areas = ArrayList<ScreenArea>()
        for (area in sessionManager.areas) {
            val poses = area.lines.map { it.start.anchor.pose }
            val squareMeters = polygonArea(poses)
            summaries.add(MeasurementSummary(squareMeters, isArea = true, isEstimate = area.isEstimate))
            if (area.lines.any { it.start.anchor.trackingState != TrackingState.TRACKING }) continue
            val screen = poses.map { projectToScreen(it) }
            if (screen.any { it == null }) continue
            val xs = screen.map { it!![0] }
            val ys = screen.map { it!![1] }
            areas.add(ScreenArea(xs, ys, xs.average().toFloat(), ys.average().toFloat(), squareMeters, area.isEstimate))
        }

        var liveMeters: Float? = null
        var liveIsEstimate = false
        var pendingScreen: FloatArray? = null
        var offscreenDir: FloatArray? = null
        var pendingDistance: Float? = null
        val pending = sessionManager.pendingStart
        if (pending != null && pending.anchor.trackingState == TrackingState.TRACKING) {
            val startPose = pending.anchor.pose
            pendingScreen = projectToScreen(startPose)
            pendingDistance = distanceBetween(cameraPose, startPose)
            offscreenDir = offscreenDirection(startPose, pendingScreen)
            if (reticle != null) {
                val liveStart = reticle.replaceStart ?: startPose
                liveIsEstimate = (reticle.replaceStart == null && !pending.onSurface) || !reticle.onSurface
                liveMeters = distanceBetween(liveStart, reticle.pose)
                projectSegment(liveStart, reticle.pose, liveIsEstimate, isLive = true)?.let(segments::add)
            }
        }

        val guide = if (reticle?.guideStart != null && reticle.guideEnd != null) {
            projectSegment(reticle.guideStart, reticle.guideEnd, isEstimate = false, isLive = false)
        } else {
            null
        }

        sessionManager.publishUiState(
            MeasureUiState(
                isTracking = true,
                reticle = reticle?.state ?: ReticleState.SEARCHING,
                hasPendingPoint = pending != null,
                pendingX = pendingScreen?.get(0) ?: 0f,
                pendingY = pendingScreen?.get(1) ?: 0f,
                pendingVisible = pendingScreen != null,
                pendingOffscreenDirX = offscreenDir?.get(0),
                pendingOffscreenDirY = offscreenDir?.get(1),
                pendingDistance = pendingDistance,
                lineCount = sessionManager.lines.size,
                liveMeters = liveMeters,
                liveIsEstimate = liveIsEstimate,
                snapAxis = reticle?.axis,
                guide = guide,
                reticleAmbiguous = reticle?.ambiguous == true,
                reticleReliable = reticle?.onSurface == true,
                debugText = if (sessionManager.debugView) {
                    (debugStats ?: "Collecting stats…") +
                        "\nAim: $lastHitKind" + (if (lastHitKind != "none") " at %.2f m".format(lastHitDistance) else "")
                } else {
                    null
                },
                loupeVisible = loupeVisible,
                targetMeters = reticle?.let { distanceBetween(cameraPose, it.pose) },
                segments = segments,
                areas = areas,
                summaries = summaries
            )
        )
    }

    private fun trackingMessage(reason: TrackingFailureReason): String = when (reason) {
        TrackingFailureReason.INSUFFICIENT_LIGHT -> "Too dark — move to a well-lit area"
        TrackingFailureReason.EXCESSIVE_MOTION -> "Moving too fast — slow down"
        TrackingFailureReason.INSUFFICIENT_FEATURES -> "Can't find anything — aim at a surface with more texture or color"
        TrackingFailureReason.CAMERA_UNAVAILABLE -> "Another app is using the camera"
        TrackingFailureReason.BAD_STATE -> "Tracking lost — please restart the app"
        else -> "Move phone slowly to get started"
    }

    // ---------------------------------------------------------------------------------------
    // Projection
    // ---------------------------------------------------------------------------------------

    /**
     * The depth image shares the camera texture's coordinates, which are rotated/cropped
     * relative to the screen. Map three view corners through ARCore to get an affine
     * view -> texture transform for the shader.
     */
    private fun updateViewToUv(frame: Frame) {
        frame.transformCoordinates2d(
            Coordinates2d.VIEW_NORMALIZED,
            viewCorners,
            Coordinates2d.TEXTURE_NORMALIZED,
            textureCorners
        )
        viewToUv[0] = textureCorners[0]
        viewToUv[1] = textureCorners[1]
        viewToUv[2] = textureCorners[2] - textureCorners[0]
        viewToUv[3] = textureCorners[3] - textureCorners[1]
        viewToUv[4] = textureCorners[4] - textureCorners[0]
        viewToUv[5] = textureCorners[5] - textureCorners[1]
    }

    /**
     * Unit screen direction (x right, y down) from the screen center toward [pose] when it is
     * outside the viewport or behind the camera; null when it is on screen.
     */
    private fun offscreenDirection(pose: Pose, screen: FloatArray?): FloatArray? {
        val margin = 24f * density
        if (screen != null &&
            screen[0] in margin..(viewportWidth - margin) &&
            screen[1] in margin..(viewportHeight - margin)
        ) {
            return null
        }
        // View space x is right and y is up; flip y for screen coordinates
        val view = toViewSpace(pose)
        var dx = view[0]
        var dy = -view[1]
        if (screen != null) {
            dx = screen[0] - viewportWidth / 2f
            dy = screen[1] - viewportHeight / 2f
        }
        val len = hypot(dx, dy)
        if (len < 1e-4f) return floatArrayOf(0f, 1f) // directly behind: point down (turn around)
        return floatArrayOf(dx / len, dy / len)
    }

    /** World pose → view space (camera looks down -Z). */
    private fun toViewSpace(pose: Pose): FloatArray {
        val world = floatArrayOf(pose.tx(), pose.ty(), pose.tz(), 1f)
        val view = FloatArray(4)
        Matrix.multiplyMV(view, 0, viewMatrix, 0, world, 0)
        return view
    }

    /** View-space point → view pixels. Assumes the point is in front of the camera. */
    private fun viewToScreen(view: FloatArray): FloatArray {
        val clip = FloatArray(4)
        Matrix.multiplyMV(clip, 0, projectionMatrix, 0, view, 0)
        val w = clip[3]
        return floatArrayOf(
            (clip[0] / w + 1f) / 2f * viewportWidth,
            (1f - clip[1] / w) / 2f * viewportHeight
        )
    }

    private fun projectToScreen(pose: Pose): FloatArray? {
        val view = toViewSpace(pose)
        if (view[2] > -NEAR_PLANE) return null
        return viewToScreen(view)
    }

    /**
     * Projects a world segment, clipping it at the near plane so lines that pass behind the
     * camera still draw correctly. Null when the whole segment is behind the camera.
     */
    private fun projectSegment(start: Pose, end: Pose, isEstimate: Boolean, isLive: Boolean): ScreenSegment? {
        var a = toViewSpace(start)
        var b = toViewSpace(end)
        val aVisible = a[2] <= -NEAR_PLANE
        val bVisible = b[2] <= -NEAR_PLANE
        if (!aVisible && !bVisible) return null

        if (!aVisible) a = clipToNear(b, a)
        if (!bVisible) b = clipToNear(a, b)

        val sa = viewToScreen(a)
        val sb = viewToScreen(b)
        return ScreenSegment(
            startX = sa[0], startY = sa[1],
            endX = sb[0], endY = sb[1],
            meters = distanceBetween(start, end),
            isEstimate = isEstimate,
            isLive = isLive,
            startVisible = aVisible,
            endVisible = bVisible
        )
    }

    /** Point on segment inside→outside where it crosses the near plane. */
    private fun clipToNear(inside: FloatArray, outside: FloatArray): FloatArray {
        val t = (-NEAR_PLANE - inside[2]) / (outside[2] - inside[2])
        return floatArrayOf(
            inside[0] + (outside[0] - inside[0]) * t,
            inside[1] + (outside[1] - inside[1]) * t,
            -NEAR_PLANE,
            1f
        )
    }
}
