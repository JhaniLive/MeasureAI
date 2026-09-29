package com.jhani.measurear.presentation

import android.content.Context
import androidx.annotation.StringRes
import com.jhani.measurear.R
import com.jhani.measurear.measurement.BoxSpec
import com.jhani.measurear.measurement.MeasureMode

/*
 * The measuring model (tested on the JVM) keeps stable English keys — mode names and result
 * labels like "Height". These map them to the user's language at display time.
 */

@StringRes
fun MeasureMode.titleRes(): Int = when (this) {
    MeasureMode.LINE -> R.string.mode_line
    MeasureMode.HEIGHT -> R.string.mode_height
    MeasureMode.FAR -> R.string.mode_far
    MeasureMode.DISTANCE -> R.string.mode_distance
    MeasureMode.ANGLE -> R.string.mode_angle
    MeasureMode.PATH -> R.string.mode_path
    MeasureMode.RECTANGLE -> R.string.mode_rectangle
    MeasureMode.CIRCLE -> R.string.mode_circle
    MeasureMode.AREA -> R.string.mode_area
    MeasureMode.VOLUME -> R.string.mode_volume
    MeasureMode.HANG -> R.string.mode_hang
    MeasureMode.FIT -> R.string.mode_fit
    MeasureMode.CALIBRATE -> R.string.mode_calibrate
}

@StringRes
fun MeasureMode.howToRes(): Int = when (this) {
    MeasureMode.LINE -> R.string.howto_line
    MeasureMode.HEIGHT -> R.string.howto_height
    MeasureMode.FAR -> R.string.howto_far
    MeasureMode.DISTANCE -> R.string.howto_distance
    MeasureMode.ANGLE -> R.string.howto_angle
    MeasureMode.PATH -> R.string.howto_path
    MeasureMode.RECTANGLE -> R.string.howto_rectangle
    MeasureMode.CIRCLE -> R.string.howto_circle
    MeasureMode.AREA -> R.string.howto_area
    MeasureMode.VOLUME -> R.string.howto_volume
    MeasureMode.HANG -> R.string.howto_hang
    MeasureMode.FIT -> R.string.howto_fit
    MeasureMode.CALIBRATE -> R.string.howto_calibrate
}

fun Context.modeTitle(mode: MeasureMode) = getString(mode.titleRes())
fun Context.modeHowTo(mode: MeasureMode) = getString(mode.howToRes())

private val LABELS = mapOf(
    "Length" to R.string.label_length,
    "Height" to R.string.label_height,
    "Distance" to R.string.label_distance,
    "Distance from you" to R.string.label_distance_from_you,
    "From you" to R.string.label_from_you,
    "Angle" to R.string.label_angle,
    "Arm 1" to R.string.label_arm1,
    "Arm 2" to R.string.label_arm2,
    "Total length" to R.string.label_total_length,
    "Last segment" to R.string.label_last_segment,
    "Area" to R.string.label_area,
    "Width" to R.string.label_width,
    "Depth" to R.string.label_depth,
    "Perimeter" to R.string.label_perimeter,
    "Diameter" to R.string.label_diameter,
    "Circumference" to R.string.label_circumference,
    "Radius" to R.string.label_radius,
    "Side" to R.string.label_side,
    "Volume" to R.string.label_volume,
    "Base" to R.string.label_base,
    "Distance to base" to R.string.label_distance_to_base,
    "± Distance" to R.string.label_pm_distance,
    "± Height" to R.string.label_pm_height,
    "Footprint" to R.string.label_footprint,
    "Nail spacing" to R.string.label_nail_spacing,
    "Total width" to R.string.label_total_width,
    "Nail below frame top" to R.string.label_nail_below_top,
    "Floor plan" to R.string.label_floor_plan
)

/** A result label ("Height") in the user's language; unknown keys are shown as they are. */
fun Context.resultLabel(key: String): String = LABELS[key]?.let(::getString) ?: key

private val BOX_NAMES = mapOf(
    "3-seat sofa" to R.string.box_sofa,
    "Queen bed" to R.string.box_bed,
    "Dining table" to R.string.box_dining,
    "Desk" to R.string.box_desk,
    "Fridge" to R.string.box_fridge,
    "Washing machine" to R.string.box_washer,
    "55\" TV" to R.string.box_tv,
    "Wardrobe" to R.string.box_wardrobe,
    "Custom" to R.string.box_custom
)

/** A furniture preset's name in the user's language. */
fun Context.boxName(spec: BoxSpec): String = BOX_NAMES[spec.name]?.let(::getString) ?: spec.name

/** Calibration reference name in the user's language. */
@StringRes
fun com.jhani.measurear.measurement.Calibration.Reference.labelRes(): Int = when (this) {
    com.jhani.measurear.measurement.Calibration.Reference.CARD -> R.string.ref_card
    com.jhani.measurear.measurement.Calibration.Reference.A4_SHORT -> R.string.ref_a4_short
    com.jhani.measurear.measurement.Calibration.Reference.A4_LONG -> R.string.ref_a4_long
}
