package com.jhani.measurear.presentation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import com.jhani.measurear.measurement.FloorPlan
import com.jhani.measurear.measurement.MeasureUnit
import com.jhani.measurear.measurement.Vec3
import com.jhani.measurear.measurement.formatArea
import com.jhani.measurear.measurement.formatLength
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Draws a measured outline as a clean floor-plan image: white paper, walls in deep teal,
 * each wall's length outside it, the area in the middle, a scale bar and a title block.
 */
object FloorPlanRenderer {

    private const val W = 1600
    private const val H = 1200
    private const val INK = 0xFF0B3B38.toInt()
    private const val ACCENT = 0xFF12A594.toInt()
    private const val MUTED = 0xFF6B7F7C.toInt()

    fun render(outline: List<Vec3>, normal: Vec3?, unit: MeasureUnit, title: String, area: Float, footer: String): Bitmap {
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        c.drawColor(0xFFFFFFFF.toInt())

        // Faint 1 m grid is added after fitting (needs the scale)
        val plan = FloorPlan.flatten(outline, normal)
        // Margin leaves room for wall lengths beside the side walls
        val fitted = FloorPlan.fit(plan, W.toFloat(), H - 180f, 250f)
        val pts = fitted.points.map { FloorPlan.P(it.x, it.y + 20f) }
        if (pts.size < 3) return bitmap

        val grid = Paint().apply { color = 0xFFE9F1F0.toInt(); strokeWidth = 1.5f }
        val step = fitted.pxPerMeter * (if (unit == MeasureUnit.METRIC) 1f else 0.3048f)
        if (step > 12f) {
            var x = 0f
            while (x < W) { c.drawLine(x, 0f, x, H - 160f, grid); x += step }
            var y = 0f
            while (y < H - 160f) { c.drawLine(0f, y, W.toFloat(), y, grid); y += step }
        }

        // Room: soft fill + thick walls
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        }
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x1A1DE9D0; style = Paint.Style.FILL })
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK; style = Paint.Style.STROKE; strokeWidth = 12f; strokeJoin = Paint.Join.MITER
        })

        // Wall lengths, outside each wall (skip the many tiny edges of a circle)
        // Winding measured on the image itself (its y axis points down, unlike the plan's)
        val ccw = FloorPlan.signedArea(pts) > 0
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK; textSize = 34f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textAlign = Paint.Align.CENTER
        }
        val dim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ACCENT; strokeWidth = 2.5f; pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
        }
        if (pts.size <= 12) {
            for (i in pts.indices) {
                val a = pts[i]
                val b = pts[(i + 1) % pts.size]
                val dx = b.x - a.x
                val dy = b.y - a.y
                val len = hypot(dx, dy)
                if (len < 40f) continue
                // Outward normal in image space
                var nx = dy / len
                var ny = -dx / len
                if (!ccw) { nx = -nx; ny = -ny }
                val off = 42f
                c.drawLine(a.x + nx * off, a.y + ny * off, b.x + nx * off, b.y + ny * off, dim)
                val text = formatLength(len / fitted.pxPerMeter, unit)
                // Beside a side wall the text's half width also has to clear the wall
                val clear = off + 30f + abs(nx) * label.measureText(text) / 2f
                val mx = (a.x + b.x) / 2f + nx * clear
                val my = (a.y + b.y) / 2f + ny * clear + 12f
                c.drawText(text, mx, my, label)
            }
        }

        // Area in the middle
        val cx = pts.map { it.x }.average().toFloat()
        val cy = pts.map { it.y }.average().toFloat()
        c.drawText(formatArea(area, false, unit), cx, cy + 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK; textSize = 64f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textAlign = Paint.Align.CENTER
        })

        // Title block
        val footerTop = H - 150f
        c.drawRect(0f, footerTop, W.toFloat(), H.toFloat(), Paint().apply { color = 0xFFF3F8F7.toInt() })
        c.drawLine(0f, footerTop, W.toFloat(), footerTop, Paint().apply { color = 0xFFD5E3E1.toInt(); strokeWidth = 2f })
        c.drawText(title, 60f, footerTop + 64f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK; textSize = 46f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        })
        c.drawText(
            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date()) + "  ·  " + footer,
            60f, footerTop + 112f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MUTED; textSize = 28f }
        )

        // Scale bar
        val barMeters = FloorPlan.niceLength(360f / fitted.pxPerMeter)
        val barPx = barMeters * fitted.pxPerMeter
        val bx = W - 60f - barPx
        val by = footerTop + 80f
        val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; strokeWidth = 6f }
        c.drawLine(bx, by, bx + barPx, by, bar)
        c.drawLine(bx, by - 14f, bx, by + 14f, bar)
        c.drawLine(bx + barPx, by - 14f, bx + barPx, by + 14f, bar)
        c.drawText(formatLength(barMeters, unit), bx + barPx / 2f, by - 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK; textSize = 28f; textAlign = Paint.Align.CENTER
        })
        return bitmap
    }
}
