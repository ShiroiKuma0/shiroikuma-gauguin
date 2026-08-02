package org.piepmeyer.gauguin.ui.grid

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.text.TextPaint
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors
import org.piepmeyer.gauguin.R
import org.piepmeyer.gauguin.grid.GridCage
import org.piepmeyer.gauguin.grid.GridCell
import org.piepmeyer.gauguin.ui.customui.GauguinFonts
import org.piepmeyer.gauguin.ui.customui.GauguinUiConfig

class GridPaintHolder(
    gridUI: GridUI,
    private val context: Context,
    usePlainBlackBackground: Boolean? = false,
) {
    private val backgroundPaint: Paint = Paint()

    private val valuePaint: Paint = Paint()
    private val valueSelectedPaint: Paint = Paint()

    private val gridPaint: Paint = Paint()
    private val selectedGridPaint: Paint
    val warningGridPaint: Paint
    private val innerGridPaint: Paint = Paint()

    private val cageTextPaint: Paint = Paint()
    private val cageTextSelectedPaint: Paint = Paint()
    private val cageTextSelectedFastFinishModePaint: Paint = Paint()
    private val cageTextPreviewModePaint: Paint = Paint()

    private val possiblesPaint: TextPaint = TextPaint()
    private val possiblesSelectedPaint: TextPaint = TextPaint()
    private val possiblesSelectedFastFinishModePaint: TextPaint = TextPaint()
    private val possiblesInvalidTextPaint: TextPaint = TextPaint()
    private val possiblesInvalidBackgroundPaint: TextPaint = TextPaint()
    private val possiblesInvalidFramePaint: TextPaint = TextPaint()

    private val warningTextPaint: Paint = Paint()
    private val cheatedPaint: Paint = Paint()
    private val errorBackgroundPaint: Paint = Paint()

    private val selectedPaint: Paint = Paint()
    private val selectedFastFinishModePaint: Paint = Paint()
    private val textOnSelectedFastFinishModePaint: Paint = Paint()
    private val lastModifiedPaint: Paint = Paint()

    private val previewPaint: Paint = Paint()
    private val previewTextPaint: Paint = Paint()

    /** 白い熊 fork: the board's share of the house UI settings. Also read by [GridLayoutDetails]. */
    val uiConfig = GauguinUiConfig(context)

    init {
        val fontHolder = GridFontHolder(context)
        val semiTransparentErrorBackgroundColor =
            MaterialColors.compositeARGBWithAlpha(
                getColor(
                    com.google.android.material.R.attr.colorErrorContainer,
                ),
                128,
            )

        val surfaceColor =
            if (usePlainBlackBackground == true) {
                Color.BLACK
            } else {
                getColor(com.google.android.material.R.attr.colorSurface)
            }

        backgroundPaint.color = surfaceColor
        backgroundPaint.style = Paint.Style.FILL

        gridPaint.flags = Paint.ANTI_ALIAS_FLAG
        gridPaint.color =
            ColorUtils.blendARGB(
                getColor(R.attr.colorGridCage),
                surfaceColor,
                if (gridUI.isInEditMode) {
                    0.0f
                } else {
                    1.0f - gridUI.resources.getFraction(R.fraction.gradCageOpacity, 1, 1)
                },
            )
        gridPaint.strokeJoin = Paint.Join.ROUND
        gridPaint.style = Paint.Style.STROKE

        selectedGridPaint = Paint(gridPaint)
        selectedGridPaint.color =
            ColorUtils.blendARGB(
                getColor(com.google.android.material.R.attr.colorSecondary),
                surfaceColor,
                0.1f,
            )

        warningGridPaint = Paint(gridPaint)
        warningGridPaint.color = getColor(com.google.android.material.R.attr.colorOnErrorContainer)
        warningGridPaint.flags = Paint.ANTI_ALIAS_FLAG

        innerGridPaint.flags = Paint.ANTI_ALIAS_FLAG
        innerGridPaint.color = gridPaint.color

        cageTextPaint.flags = Paint.ANTI_ALIAS_FLAG
        cageTextPaint.color = getColor(R.attr.colorGridCageText)
        cageTextPaint.typeface = fontHolder.fontCageText

        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(cageTextPaint.color, hsl)
        hsl[1] = hsl[1] * 0.35f
        cageTextPreviewModePaint.flags = Paint.ANTI_ALIAS_FLAG
        cageTextPreviewModePaint.color = ColorUtils.HSLToColor(hsl)
        cageTextPreviewModePaint.typeface = fontHolder.fontCageText

        cageTextSelectedPaint.flags = Paint.ANTI_ALIAS_FLAG
        cageTextSelectedPaint.color = getColor(R.attr.colorGridCageText)
        cageTextSelectedPaint.typeface = fontHolder.fontCageText
        cageTextSelectedFastFinishModePaint.flags = Paint.ANTI_ALIAS_FLAG
        cageTextSelectedFastFinishModePaint.color = surfaceColor
        cageTextSelectedFastFinishModePaint.typeface = fontHolder.fontCageText

        valuePaint.flags = Paint.ANTI_ALIAS_FLAG
        valuePaint.color = getColor(R.attr.colorGridValue)
        valuePaint.typeface = fontHolder.fontValue

        valueSelectedPaint.flags = Paint.ANTI_ALIAS_FLAG
        valueSelectedPaint.color = getColor(R.attr.colorGridSelected)
        valueSelectedPaint.typeface = fontHolder.fontValue

        possiblesPaint.flags = Paint.ANTI_ALIAS_FLAG
        possiblesPaint.color = getColor(com.google.android.material.R.attr.colorOnSurface)
        possiblesPaint.typeface = fontHolder.fontPossibles
        possiblesSelectedPaint.flags = Paint.ANTI_ALIAS_FLAG
        possiblesSelectedPaint.color = getColor(R.attr.colorGridSelected)
        possiblesSelectedPaint.typeface = fontHolder.fontPossibles
        possiblesSelectedFastFinishModePaint.flags = Paint.ANTI_ALIAS_FLAG
        possiblesSelectedFastFinishModePaint.color = getColor(R.attr.colorGridSelectedText)
        possiblesSelectedFastFinishModePaint.typeface = fontHolder.fontPossibles
        possiblesInvalidTextPaint.color = getColor(com.google.android.material.R.attr.colorOnErrorContainer)
        possiblesInvalidTextPaint.flags = Paint.ANTI_ALIAS_FLAG
        possiblesInvalidBackgroundPaint.color = semiTransparentErrorBackgroundColor
        possiblesInvalidBackgroundPaint.flags = Paint.ANTI_ALIAS_FLAG
        possiblesInvalidBackgroundPaint.style = Paint.Style.FILL
        possiblesInvalidFramePaint.color = getColor(com.google.android.material.R.attr.colorOnErrorContainer)
        possiblesInvalidFramePaint.flags = Paint.ANTI_ALIAS_FLAG
        possiblesInvalidFramePaint.style = Paint.Style.STROKE

        previewTextPaint.flags = Paint.ANTI_ALIAS_FLAG
        previewTextPaint.textSize = 6f
        previewTextPaint.color = getColor(com.google.android.material.R.attr.colorOnTertiaryContainer)
        previewTextPaint.typeface = fontHolder.fontPossibles

        previewPaint.color = getColor(com.google.android.material.R.attr.colorTertiaryContainer)
        previewPaint.style = Paint.Style.FILL_AND_STROKE

        selectedPaint.flags = Paint.ANTI_ALIAS_FLAG
        selectedPaint.color = getColor(R.attr.colorGridSelected)
        selectedPaint.style = Paint.Style.STROKE

        selectedFastFinishModePaint.flags = Paint.ANTI_ALIAS_FLAG
        selectedFastFinishModePaint.color = getColor(R.attr.colorGridSelected)
        selectedFastFinishModePaint.style = Paint.Style.FILL_AND_STROKE
        textOnSelectedFastFinishModePaint.flags = Paint.ANTI_ALIAS_FLAG
        textOnSelectedFastFinishModePaint.color = getColor(R.attr.colorGridSelectedText)

        lastModifiedPaint.color =
            ColorUtils.blendARGB(
                getColor(R.attr.colorGridSelected),
                surfaceColor,
                if (gridUI.isInEditMode) {
                    0.7f
                } else {
                    gridUI.resources.getFraction(R.fraction.lastModifiedOpacity, 1, 1)
                },
            )
        lastModifiedPaint.style = Paint.Style.FILL_AND_STROKE
        lastModifiedPaint.flags = Paint.ANTI_ALIAS_FLAG

        warningTextPaint.color = getColor(com.google.android.material.R.attr.colorOnErrorContainer)
        warningTextPaint.typeface = fontHolder.fontValue

        cheatedPaint.color = getColor(com.google.android.material.R.attr.colorSurfaceVariant)

        errorBackgroundPaint.color = semiTransparentErrorBackgroundColor

        applyShiroikumaLook(context)
    }

    /**
     * 白い熊 fork: override the board's colours and fonts from the 白い熊 GNU Gauguin UI settings.
     *
     * Applied as a final pass over the paints upstream has just built, rather than woven into their
     * construction — that keeps the fork's diff to one block, so an upstream rebase that reworks the
     * theming above still leaves this intact and re-appliable.
     */
    private fun applyShiroikumaLook(context: android.content.Context) {
        if (!uiConfig.customUiActive) return

        val valueFont =
            GauguinFonts.typeface(
                context,
                uiConfig.string(GauguinUiConfig.VALUE_FONT).ifEmpty { uiConfig.fontFamily },
                uiConfig.int(GauguinUiConfig.VALUE_WEIGHT),
                uiConfig.fontItalic,
            )
        val cageFont =
            GauguinFonts.typeface(
                context,
                uiConfig.string(GauguinUiConfig.CAGE_TEXT_FONT).ifEmpty { uiConfig.fontFamily },
                uiConfig.fontWeight,
                uiConfig.fontItalic,
            )
        val possiblesFont =
            GauguinFonts.typeface(
                context,
                uiConfig.string(GauguinUiConfig.POSSIBLES_FONT).ifEmpty { uiConfig.fontFamily },
                uiConfig.fontWeight,
                uiConfig.fontItalic,
            )

        backgroundPaint.color = uiConfig.int(GauguinUiConfig.GRID_BACKGROUND)

        gridPaint.color = uiConfig.int(GauguinUiConfig.CAGE_BORDER_COLOR)
        selectedGridPaint.color = uiConfig.accentColor
        innerGridPaint.color = uiConfig.int(GauguinUiConfig.CELL_BORDER_COLOR)
        warningGridPaint.color = uiConfig.int(GauguinUiConfig.WARNING_COLOR)

        listOf(cageTextPaint, cageTextSelectedPaint, cageTextPreviewModePaint).forEach {
            it.color = uiConfig.int(GauguinUiConfig.CAGE_TEXT_COLOR)
            it.typeface = cageFont
        }
        cageTextSelectedFastFinishModePaint.color = uiConfig.int(GauguinUiConfig.GRID_CELL_BACKGROUND)
        cageTextSelectedFastFinishModePaint.typeface = cageFont

        valuePaint.color = uiConfig.int(GauguinUiConfig.VALUE_COLOR)
        valuePaint.typeface = valueFont
        valueSelectedPaint.color = uiConfig.int(GauguinUiConfig.VALUE_SELECTED_COLOR)
        valueSelectedPaint.typeface = valueFont
        textOnSelectedFastFinishModePaint.color = uiConfig.int(GauguinUiConfig.VALUE_SELECTED_COLOR)

        listOf(possiblesPaint, possiblesSelectedPaint, possiblesSelectedFastFinishModePaint).forEach {
            it.color = uiConfig.int(GauguinUiConfig.POSSIBLES_COLOR)
            it.typeface = possiblesFont
        }
        previewTextPaint.typeface = possiblesFont

        val errorColor = uiConfig.int(GauguinUiConfig.ERROR_COLOR)
        possiblesInvalidTextPaint.color = errorColor
        possiblesInvalidFramePaint.color = errorColor
        warningTextPaint.color = errorColor
        warningTextPaint.typeface = valueFont
        possiblesInvalidBackgroundPaint.color = withAlpha(errorColor, 128)
        errorBackgroundPaint.color = withAlpha(errorColor, 128)

        selectedPaint.color = uiConfig.accentColor
        selectedFastFinishModePaint.color = uiConfig.int(GauguinUiConfig.GRID_SELECTED_BACKGROUND)
        lastModifiedPaint.color = uiConfig.int(GauguinUiConfig.LAST_MODIFIED_COLOR)
        cheatedPaint.color = uiConfig.int(GauguinUiConfig.CHEATED_COLOR)
    }

    private fun withAlpha(
        color: Int,
        alpha: Int,
    ): Int = (color and 0x00FFFFFF) or (alpha shl 24)

    fun possiblesPaint(
        cell: GridCell,
        fastFinishMode: Boolean,
    ) = when {
        cell.isSelected && fastFinishMode -> possiblesSelectedFastFinishModePaint
        cell.isSelected -> possiblesSelectedPaint
        else -> possiblesPaint
    }

    fun cellValuePaint(
        cell: GridCell,
        fastFinishMode: Boolean,
    ) = when {
        cell.isInvalidHighlight -> warningTextPaint
        cell.isSelected && fastFinishMode -> textOnSelectedFastFinishModePaint
        cell.isSelected -> valueSelectedPaint
        else -> valuePaint
    }

    fun cellBackgroundPaint(
        cell: GridCell,
        badMathInCage: Boolean,
        markDuplicatedInRowOrColumn: Boolean,
        fastFinishMode: Boolean,
    ) = when {
        cell.isSelected && fastFinishMode -> selectedFastFinishModePaint
        cell.isLastModified -> lastModifiedPaint
        cell.isCheated -> cheatedPaint
        (markDuplicatedInRowOrColumn && cell.duplicatedInRowOrColumn) || badMathInCage || cell.isInvalidHighlight -> errorBackgroundPaint
        else -> null
    }

    fun cellForegroundPaint(cell: GridCell) =
        when {
            cell.isSelected -> selectedPaint
            else -> null
        }

    fun cageTextPaint(
        cage: GridCage,
        previewMode: Boolean,
        fastFinishMode: Boolean,
    ): Paint =
        when {
            previewMode -> cageTextPreviewModePaint
            cage.getCell(0).isSelected && fastFinishMode -> cageTextSelectedFastFinishModePaint
            cage.getCell(0).isSelected -> cageTextSelectedPaint
            else -> cageTextPaint
        }

    fun previewBannerTextPaint(): Paint = previewTextPaint

    fun previewBannerBackgroundPaint(): Paint = previewPaint

    fun backgroundPaint(): Paint = backgroundPaint

    fun innerGridPaint(): Paint = innerGridPaint

    fun gridPaint(): Paint = gridPaint

    fun selectedGridPaint(): Paint = selectedGridPaint

    private fun getColor(colorId: Int): Int = MaterialColors.getColor(context, colorId, "ups")

    fun invalidPossiblePaint(): Paint = possiblesInvalidTextPaint

    fun invalidPossibleBackgroundPaint(): Paint = possiblesInvalidBackgroundPaint

    fun invalidPossiblesFramePaint(): Paint = possiblesInvalidFramePaint
}
