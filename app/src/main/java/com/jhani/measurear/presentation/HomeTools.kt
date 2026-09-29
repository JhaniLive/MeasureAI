package com.jhani.measurear.presentation

import com.jhani.measurear.R
import androidx.compose.ui.res.stringResource
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jhani.measurear.measurement.Calculators
import com.jhani.measurear.measurement.MeasureUnit
import com.jhani.measurear.measurement.formatArea

private val Panel = Color(0xFF0E1614)
private val Chip = Color(0xFF16211F)

/** Small pill buttons under a finished area: Materials and Floor plan. */
@Composable
fun AreaActions(onMaterials: () -> Unit, onPlan: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionPill("🎨  " + stringResource(R.string.materials), onMaterials)
        ActionPill("🗺  " + stringResource(R.string.label_floor_plan), onPlan)
    }
}

@Composable
private fun ActionPill(text: String, onClick: () -> Unit) {
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .border(1.dp, HudTeal.copy(alpha = 0.7f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun <T> Choice(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { o ->
            val on = o == selected
            Text(
                label(o),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) HudTealDark else Chip)
                    .border(1.dp, if (on) HudTeal else Color.Transparent, RoundedCornerShape(12.dp))
                    .clickable { onSelect(o) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                color = Color.White,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, modifier = Modifier.width(80.dp))
        Text("−", color = HudTeal, fontSize = 22.sp, modifier = Modifier.clickable { onChange((value - 1).coerceAtLeast(0)) }.padding(horizontal = 12.dp))
        Text("$value", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text("+", color = HudTeal, fontSize = 22.sp, modifier = Modifier.clickable { onChange((value + 1).coerceAtMost(20)) }.padding(horizontal = 12.dp))
    }
}

@Composable
private fun BigResult(main: String, detail: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HudTealDark)
            .padding(14.dp)
    ) {
        Text(main, color = HudTeal, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(detail, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
    }
}

/** Paint, tiles and flooring needed for a measured [area] (m²). */
@Composable
fun MaterialsDialog(area: Float, unit: MeasureUnit, onDismiss: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val paintLabel = stringResource(R.string.tab_paint)
    val tilesLabel = stringResource(R.string.tab_tiles)
    val flooringLabel = stringResource(R.string.tab_flooring)
    val coatLabels = listOf(stringResource(R.string.coats_1), stringResource(R.string.coats_n, 2), stringResource(R.string.coats_n, 3))
    val canLabel = stringResource(R.string.can_size).replace("%1\$d", "%d")
    val boxLabel = stringResource(R.string.box_area).replace("%1\$s", "%s")
    var coats by rememberSaveable { mutableIntStateOf(2) }
    var doors by rememberSaveable { mutableIntStateOf(0) }
    var windows by rememberSaveable { mutableIntStateOf(0) }
    var can by rememberSaveable { mutableStateOf(4f) }
    var tile by rememberSaveable { mutableStateOf("60×60") }
    var waste by rememberSaveable { mutableStateOf(10f) }
    var boxArea by rememberSaveable { mutableStateOf(2.4f) }
    val tiles = mapOf("30×30" to (0.3f to 0.3f), "60×60" to (0.6f to 0.6f), "60×120" to (0.6f to 1.2f), "80×80" to (0.8f to 0.8f))

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = {
            Column {
                Text(stringResource(R.string.materials), color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.materials_for, formatArea(area, false, unit)), color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Choice(listOf(0, 1, 2), tab, { listOf("🎨 " + paintLabel, "🧱 " + tilesLabel, "🪵 " + flooringLabel)[it] }) { tab = it }
                when (tab) {
                    0 -> {
                        Choice(listOf(1, 2, 3), coats, { coatLabels[it - 1] }) { coats = it }
                        Stepper(stringResource(R.string.doors), doors) { doors = it }
                        Stepper(stringResource(R.string.windows), windows) { windows = it }
                        Choice(listOf(1f, 4f, 10f, 20f), can, { canLabel.format(it.toInt()) }) { can = it }
                        val r = Calculators.paint(area, coats, doors = doors, windows = windows, canLiters = can)
                        BigResult(
                            "${r.cans} × ${can.toInt()} L",
                            stringResource(R.string.paint_detail, "%.1f".format(r.liters), formatArea(r.paintedArea, false, unit))
                        )
                    }
                    1 -> {
                        Choice(tiles.keys.toList(), tile, { "$it cm" }) { tile = it }
                        Choice(listOf(5f, 10f, 15f), waste, { "+${it.toInt()}%" }) { waste = it }
                        val (tw, th) = tiles.getValue(tile)
                        val r = Calculators.tiles(area, tw, th, waste)
                        BigResult(stringResource(R.string.tiles_count, r.tiles), stringResource(R.string.tiles_detail, tile, waste.toInt()))
                    }
                    else -> {
                        Choice(listOf(1.5f, 2.0f, 2.4f, 3.0f), boxArea, { boxLabel.format("%.1f".format(it)) }) { boxArea = it }
                        val r = Calculators.flooring(area, boxArea)
                        BigResult(stringResource(R.string.boxes_count, r.boxes), stringResource(R.string.flooring_detail, "%.1f".format(r.coveredArea)))
                    }
                }
                Text(
                    stringResource(R.string.estimates_note),
                    color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            Text(
                stringResource(R.string.done),
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(HudTeal)
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                color = Color.Black,
                fontWeight = FontWeight.SemiBold
            )
        }
    )
}

/** Floor-plan preview with Save (to History) and Share. */
@Composable
fun FloorPlanDialog(plan: Bitmap, onSave: () -> Unit, onShare: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text(stringResource(R.string.label_floor_plan), color = Color.White, fontWeight = FontWeight.SemiBold) },
        text = {
            Image(
                bitmap = plan.asImageBitmap(),
                contentDescription = stringResource(R.string.label_floor_plan),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            )
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionPill(stringResource(R.string.save_to_history), onSave)
                Text(
                    stringResource(R.string.share),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(HudTeal)
                        .clickable(onClick = onShare)
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    color = Color.Black,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            Text(
                stringResource(R.string.close),
                modifier = Modifier.clickable(onClick = onDismiss).padding(horizontal = 12.dp, vertical = 8.dp),
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    )
}
