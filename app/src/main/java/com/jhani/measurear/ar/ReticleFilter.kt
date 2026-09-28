package com.jhani.measurear.ar

import com.google.ar.core.Pose
import kotlin.math.sqrt

/**
 * Steadies the reticle position: keeps the last few hit positions from the same source and
 * returns their per-axis median, which rejects single-frame outliers from hand shake and
 * noisy depth estimates. The window restarts when the source changes (another plane, or
 * plane ↔ estimate) or the hit jumps, so aiming at a new spot responds immediately.
 */
class ReticleFilter(
    private val windowSize: Int = 8,
    private val jumpThresholdMeters: Float = 0.05f
) {
    private val xs = ArrayDeque<Float>()
    private val ys = ArrayDeque<Float>()
    private val zs = ArrayDeque<Float>()
    private var sourceKey: Any? = null
    private var lastMedian: FloatArray? = null

    fun reset() {
        xs.clear()
        ys.clear()
        zs.clear()
        sourceKey = null
        lastMedian = null
    }

    /**
     * Adds a new hit and returns the filtered pose (median position, latest orientation).
     */
    fun add(pose: Pose, sourceKey: Any): Pose {
        val previous = lastMedian
        if (sourceKey != this.sourceKey || (previous != null && distance(previous, pose) > jumpThresholdMeters)) {
            reset()
            this.sourceKey = sourceKey
        }

        xs.addLast(pose.tx())
        ys.addLast(pose.ty())
        zs.addLast(pose.tz())
        if (xs.size > windowSize) {
            xs.removeFirst()
            ys.removeFirst()
            zs.removeFirst()
        }

        val median = floatArrayOf(median(xs), median(ys), median(zs))
        lastMedian = median
        return Pose(median, pose.rotationQuaternion)
    }

    private fun distance(a: FloatArray, pose: Pose): Float {
        val dx = a[0] - pose.tx()
        val dy = a[1] - pose.ty()
        val dz = a[2] - pose.tz()
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun median(values: Collection<Float>): Float {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2f else sorted[mid]
    }
}
