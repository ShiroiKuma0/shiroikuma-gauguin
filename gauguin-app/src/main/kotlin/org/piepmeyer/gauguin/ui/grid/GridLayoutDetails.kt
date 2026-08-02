package org.piepmeyer.gauguin.ui.grid

import android.graphics.CornerPathEffect
import android.graphics.Paint
import org.piepmeyer.gauguin.grid.Grid
import org.piepmeyer.gauguin.grid.GridCage
import kotlin.math.max

class GridLayoutDetails(
    private val cellSize: Pair<Float, Float>,
    private val painterHolder: GridPaintHolder,
    private val useBroaderCageFrames: Boolean,
) {
    fun averageLengthOfCell(): Float = (cellSize.first + cellSize.second) / 2

    fun gridPaint(
        cage: GridCage,
        grid: Grid,
        showBadMaths: Boolean,
    ): Paint {
        val cageSelected = grid.isActive && grid.selectedCell?.cage == cage

        val badMathCage = !cage.isUserMathCorrect() && showBadMaths

        val paint =
            if (badMathCage) {
                painterHolder.warningGridPaint
            } else if (cageSelected) {
                painterHolder.selectedGridPaint()
            } else {
                painterHolder.gridPaint()
            }

        paint.pathEffect = CornerPathEffect(gridPaintRadius())
        paint.strokeWidth =
            if (cageSelected || badMathCage) {
                gridSelectedPaintStrokeWidth()
            } else {
                gridPaintStrokeWidth()
            }

        return paint
    }

    fun innerGridPaint(): Paint =
        painterHolder.innerGridPaint().apply {
            strokeWidth = 0.01f * averageLengthOfCell() * cellBorderFactor()
        }

    // --- 白い熊 fork: the board's line weights and clue size follow the house UI settings. Each
    // factor is 1.0 at the slider's default, so an untouched config draws exactly as upstream does,
    // and every thickness slider bottoms out at 0 — the line goes away entirely.
    private fun uiConfig() = painterHolder.uiConfig

    private fun cageBorderFactor(): Float =
        if (!uiConfig().customUiActive) {
            1f
        } else {
            uiConfig().int(org.piepmeyer.gauguin.ui.customui.GauguinUiConfig.CAGE_BORDER_WIDTH) / 4f
        }

    private fun cellBorderFactor(): Float =
        if (!uiConfig().customUiActive) {
            1f
        } else {
            uiConfig().int(org.piepmeyer.gauguin.ui.customui.GauguinUiConfig.CELL_BORDER_WIDTH).toFloat()
        }

    private fun cageTextFactor(): Float =
        if (!uiConfig().customUiActive) {
            1f
        } else {
            uiConfig().int(org.piepmeyer.gauguin.ui.customui.GauguinUiConfig.CAGE_TEXT_SIZE) / 100f
        }

    fun gridPaintRadius(): Float = 0.21f * averageLengthOfCell()

    fun possiblesFixedGridDistanceX(): Float = 0.25f * cellSize.first

    fun possiblesFixedGridDistanceYUpToSixValues(): Float = 0.22f * cellSize.second

    fun possiblesFixedGridDistanceYFromSevenValuesOn(): Float = 0.19f * cellSize.second

    fun yOffsetUpToSixValues(): Int = (cellSize.second / 2.5).toInt() + 1

    fun yOffsetFromSevenOn(): Int = (cellSize.second / 3.9).toInt() + 1

    fun gridPaintStrokeWidth(): Float =
        max((if (useBroaderCageFrames) 0.05f else 0.02f) * averageLengthOfCell(), 1f) * cageBorderFactor()

    private fun gridSelectedPaintStrokeWidth(): Float =
        max((if (useBroaderCageFrames) 0.05f else 0.03f) * averageLengthOfCell(), 1f) * cageBorderFactor()

    fun offsetDistance(): Int = max(5f / 119f * averageLengthOfCell(), 1f).toInt()

    fun innerGridWidth(): Int = max(8f / 119f * averageLengthOfCell(), 1f).toInt()

    fun possibleNumbersMarginX(): Int = max(13f / 119f * averageLengthOfCell(), 1f).toInt()

    fun possibleNumbersMarginY(): Int = max(15f / 119f * averageLengthOfCell(), 1f).toInt()

    fun possibleNumbersInvalidStrokeWidth(): Float = gridPaintStrokeWidth()

    fun cageTextMarginX(): Int = max(12f / 119f * averageLengthOfCell(), 1f).toInt()

    fun cageTextMarginY(): Int = max(10f / 119f * averageLengthOfCell(), 1f).toInt()

    fun cageTextSize(): Float = averageLengthOfCell() / 3.5f * cageTextFactor()

    fun cageTextStrokeWidth(): Float = averageLengthOfCell() / 25f

    fun possibleNumbersInvalidCornerRadius(): Float = gridPaintRadius() * 0.3f
}
