package com.jhani.measurear.measurement

import kotlin.math.ceil
import kotlin.math.max

/**
 * Material estimates from a measured area. Pure and unit tested; the defaults are typical
 * values printed on paint cans and tile / flooring boxes, and every one can be changed.
 */
object Calculators {

    /** Typical openings to subtract from a wall. */
    const val DOOR_M2 = 1.9f   // 0.9 × 2.1 m
    const val WINDOW_M2 = 1.5f // 1.2 × 1.25 m

    data class PaintResult(val paintedArea: Float, val liters: Float, val cans: Int, val cost: Float?)

    /**
     * Paint for [area] m² minus [doors] and [windows], [coats] coats at [coveragePerLiter]
     * m²/L (≈10 for emulsion), bought in cans of [canLiters] L.
     */
    fun paint(
        area: Float,
        coats: Int = 2,
        coveragePerLiter: Float = 10f,
        doors: Int = 0,
        windows: Int = 0,
        canLiters: Float = 4f,
        pricePerCan: Float? = null
    ): PaintResult {
        val painted = max(0f, area - doors * DOOR_M2 - windows * WINDOW_M2)
        val liters = painted * coats / coveragePerLiter
        val cans = if (liters <= 0f) 0 else ceil(liters / canLiters - 1e-4f).toInt()
        return PaintResult(painted, liters, cans, pricePerCan?.let { it * cans })
    }

    data class TileResult(val tiles: Int, val boxes: Int?, val cost: Float?)

    /**
     * Tiles of [tileWidth] × [tileHeight] m for [area] m², with [wastePercent] extra for cuts
     * and breakage (10% straight, ~15% diagonal), optionally in boxes of [perBox].
     */
    fun tiles(
        area: Float,
        tileWidth: Float,
        tileHeight: Float,
        wastePercent: Float = 10f,
        perBox: Int? = null,
        pricePerTile: Float? = null
    ): TileResult {
        val tileArea = tileWidth * tileHeight
        if (tileArea <= 0f || area <= 0f) return TileResult(0, perBox?.let { 0 }, pricePerTile?.let { 0f })
        val tiles = ceil(area * (1f + wastePercent / 100f) / tileArea - 1e-4f).toInt()
        val boxes = perBox?.takeIf { it > 0 }?.let { ceil(tiles.toFloat() / it - 1e-4f).toInt() }
        return TileResult(tiles, boxes, pricePerTile?.let { it * tiles })
    }

    data class FlooringResult(val boxes: Int, val coveredArea: Float, val cost: Float?)

    /** Flooring (laminate, vinyl, planks) sold in boxes covering [boxArea] m² each. */
    fun flooring(area: Float, boxArea: Float, wastePercent: Float = 8f, pricePerBox: Float? = null): FlooringResult {
        if (boxArea <= 0f || area <= 0f) return FlooringResult(0, 0f, pricePerBox?.let { 0f })
        val boxes = ceil(area * (1f + wastePercent / 100f) / boxArea - 1e-4f).toInt()
        return FlooringResult(boxes, boxes * boxArea, pricePerBox?.let { it * boxes })
    }
}
