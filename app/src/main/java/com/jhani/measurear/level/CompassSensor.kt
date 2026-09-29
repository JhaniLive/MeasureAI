package com.jhani.measurear.level

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs

/** One compass reading: heading in degrees clockwise from magnetic north (0..360). */
data class CompassReading(
    val heading: Float,
    /** The magnetometer needs a figure-8 calibration (low accuracy reported). */
    val needsCalibration: Boolean
)

/** Pure compass math, unit tested. */
object CompassMath {
    private val POINTS = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

    /** 8-point direction name for a heading: 0 = N, 45 = NE, … (each spans ±22.5°). */
    fun cardinal(heading: Float): String {
        val h = normalize(heading)
        return POINTS[(((h + 22.5f) / 45f).toInt()) % 8]
    }

    fun normalize(degrees: Float): Float = ((degrees % 360f) + 360f) % 360f

    /** Signed shortest turn from [from] to [to], in degrees (-180..180]. */
    fun delta(from: Float, to: Float): Float {
        var d = normalize(to) - normalize(from)
        if (d > 180f) d -= 360f
        if (d <= -180f) d += 360f
        return d
    }

    /** Low-pass filter on the circle (so 359° → 1° doesn't swing through 180°). */
    fun smooth(previous: Float, next: Float, alpha: Float): Float =
        normalize(previous + delta(previous, next) * alpha)
}

/**
 * Heading from the fused rotation-vector sensor (magnetometer + gyro + accelerometer). Held
 * flat, it's where the top of the phone points; held upright, where the camera points.
 */
class CompassSensor(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val rotation = FloatArray(9)
    private val remapped = FloatArray(9)
    private val orientation = FloatArray(3)
    private var heading: Float? = null
    private var lowAccuracy = false
    private var listener: ((CompassReading) -> Unit)? = null

    val isAvailable: Boolean get() = sensor != null

    fun start(onReading: (CompassReading) -> Unit) {
        listener = onReading
        sensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        listener = null
        heading = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        // rotation[8] = device Z · world up: near ±1 when lying flat
        val flat = abs(rotation[8]) > 0.7f
        val matrix = if (flat) {
            rotation
        } else {
            // Upright: use the camera's viewing direction (device -Z) as the pointer
            SensorManager.remapCoordinateSystem(rotation, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapped)
            remapped
        }
        SensorManager.getOrientation(matrix, orientation)
        val raw = CompassMath.normalize(Math.toDegrees(orientation[0].toDouble()).toFloat())
        val smoothed = heading?.let { CompassMath.smooth(it, raw, SMOOTHING) } ?: raw
        heading = smoothed
        listener?.invoke(CompassReading(smoothed, lowAccuracy))
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        lowAccuracy = accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW
    }

    companion object {
        private const val SMOOTHING = 0.18f
    }
}
