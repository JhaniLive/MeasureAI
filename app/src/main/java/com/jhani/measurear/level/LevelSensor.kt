package com.jhani.measurear.level

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * How the phone is being used as a level.
 */
enum class LevelMode {
    /** Lying on its back on a surface: 2-axis bubble. */
    FLAT,

    /** Standing on an edge (against a wall, frame, shelf): 1-axis horizon. */
    EDGE
}

/**
 * One level reading, in degrees.
 *
 * @param tiltX FLAT: left/right tilt. EDGE: rotation of the horizon line.
 * @param tiltY FLAT: forward/back tilt. EDGE: unused (0).
 */
data class LevelReading(
    val mode: LevelMode,
    val tiltX: Float,
    val tiltY: Float
) {
    /** Largest deviation from level. */
    val magnitude: Float
        get() = if (mode == LevelMode.FLAT) sqrt(tiltX * tiltX + tiltY * tiltY) else abs(tiltX)
}

/**
 * Reads the gravity sensor (falling back to the accelerometer) and turns it into level angles.
 * Readings are low-pass filtered so the bubble moves smoothly.
 */
class LevelSensor(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val gravity = FloatArray(3)
    private var hasReading = false
    private var listener: ((LevelReading) -> Unit)? = null

    val isAvailable: Boolean
        get() = sensor != null

    companion object {
        private const val SMOOTHING = 0.15f

        // Switch between FLAT and EDGE with hysteresis so it doesn't flicker near 45°
        private const val FLAT_ENTER_DEG = 35f
        private const val FLAT_EXIT_DEG = 55f

        private const val RAD_TO_DEG = (180.0 / Math.PI).toFloat()
    }

    private var mode = LevelMode.FLAT

    fun start(onReading: (LevelReading) -> Unit) {
        listener = onReading
        hasReading = false
        sensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        listener = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!hasReading) {
            event.values.copyInto(gravity, endIndex = 3)
            hasReading = true
        } else {
            for (i in 0..2) gravity[i] += SMOOTHING * (event.values[i] - gravity[i])
        }

        val gx = gravity[0]
        val gy = gravity[1]
        val gz = gravity[2]
        val g = sqrt(gx * gx + gy * gy + gz * gz)
        if (g < 1e-3f) return

        // Angle between the screen normal (device Z) and vertical
        val screenTilt = Math.toDegrees(Math.acos((abs(gz) / g).toDouble().coerceIn(0.0, 1.0))).toFloat()
        mode = when (mode) {
            LevelMode.FLAT -> if (screenTilt > FLAT_EXIT_DEG) LevelMode.EDGE else LevelMode.FLAT
            LevelMode.EDGE -> if (screenTilt < FLAT_ENTER_DEG) LevelMode.FLAT else LevelMode.EDGE
        }

        val reading = when (mode) {
            LevelMode.FLAT -> LevelReading(
                mode = LevelMode.FLAT,
                // Signs follow the bubble: +X means the left edge is lower (bubble drifts right),
                // +Y means the top edge is lower (bubble drifts down the screen)
                tiltX = atan2(gx, abs(gz)) * RAD_TO_DEG,
                tiltY = atan2(-gy, abs(gz)) * RAD_TO_DEG
            )
            LevelMode.EDGE -> {
                // Rotation of gravity within the screen plane, relative to the nearest edge
                var angle = atan2(gx, gy) * RAD_TO_DEG
                while (angle > 45f) angle -= 90f
                while (angle < -45f) angle += 90f
                LevelReading(mode = LevelMode.EDGE, tiltX = angle, tiltY = 0f)
            }
        }
        listener?.invoke(reading)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
