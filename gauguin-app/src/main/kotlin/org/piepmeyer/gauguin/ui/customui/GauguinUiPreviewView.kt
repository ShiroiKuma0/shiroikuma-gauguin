package org.piepmeyer.gauguin.ui.customui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * The live preview that sits at the top of the settings page: a miniature Gauguin board (cages,
 * cell lines, a filled number, a cage clue, pencil marks, a selected cell) plus a row of keypad
 * buttons and a line of sample text — all drawn straight from [GauguinUiConfig].
 *
 * Every knob on the page calls [refresh] as it changes, so a slider drag or a colour slide is
 * visible immediately without leaving the page.
 */
class GauguinUiPreviewView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : View(context, attrs, defStyleAttr) {
        private var config = GauguinUiConfig(context)

        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val text = Paint(Paint.ANTI_ALIAS_FLAG)

        private val density = context.resources.displayMetrics.density

        /** Re-read the config and redraw. Called by the page on every change. */
        fun refresh() {
            config = GauguinUiConfig(context)
            invalidate()
        }

        override fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
        ) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            setMeasuredDimension(width, (190 * density).toInt())
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()

            fill.color = config.int(GauguinUiConfig.GRID_BACKGROUND)
            canvas.drawRect(0f, 0f, w, h, fill)

            val pad = 10 * density
            val boardSize = minOf(h - 60 * density, w * 0.45f)
            val cell = boardSize / 3f
            val left = pad
            val top = pad

            drawBoard(canvas, left, top, cell)
            drawKeypad(canvas, left + boardSize + 14 * density, top, w - pad, cell)
            drawSampleText(canvas, pad, h - 12 * density)
        }

        private fun drawBoard(
            canvas: Canvas,
            left: Float,
            top: Float,
            cell: Float,
        ) {
            val cellBg = config.int(GauguinUiConfig.GRID_CELL_BACKGROUND)
            val selectedBg = config.int(GauguinUiConfig.GRID_SELECTED_BACKGROUND)
            val radius = config.int(GauguinUiConfig.GRID_CORNER) * density

            // cell fills — the middle cell stands in for the selected one
            for (row in 0 until 3) {
                for (col in 0 until 3) {
                    fill.color = if (row == 1 && col == 1) selectedBg else cellBg
                    val rect =
                        RectF(
                            left + col * cell,
                            top + row * cell,
                            left + (col + 1) * cell,
                            top + (row + 1) * cell,
                        )
                    canvas.drawRoundRect(rect, radius, radius, fill)
                }
            }

            // inner cell lines
            val cellWidth = config.int(GauguinUiConfig.CELL_BORDER_WIDTH) * density
            if (cellWidth > 0f) {
                stroke.color = config.int(GauguinUiConfig.CELL_BORDER_COLOR)
                stroke.strokeWidth = cellWidth
                for (index in 1 until 3) {
                    canvas.drawLine(left + index * cell, top, left + index * cell, top + 3 * cell, stroke)
                    canvas.drawLine(left, top + index * cell, left + 3 * cell, top + index * cell, stroke)
                }
            }

            // cage outlines — the board edge plus one interior cage, so both weights are visible
            val cageWidth = config.int(GauguinUiConfig.CAGE_BORDER_WIDTH) * density
            if (cageWidth > 0f) {
                stroke.color = config.int(GauguinUiConfig.CAGE_BORDER_COLOR)
                stroke.strokeWidth = cageWidth
                val board = RectF(left, top, left + 3 * cell, top + 3 * cell)
                canvas.drawRoundRect(board, radius, radius, stroke)
                val cage = RectF(left, top, left + 2 * cell, top + cell)
                canvas.drawRoundRect(cage, radius, radius, stroke)
            }

            // a filled number in the selected cell
            text.color = config.int(GauguinUiConfig.VALUE_SELECTED_COLOR)
            text.typeface =
                GauguinFonts.typeface(
                    context,
                    config.string(GauguinUiConfig.VALUE_FONT).ifEmpty { config.fontFamily },
                    config.int(GauguinUiConfig.VALUE_WEIGHT),
                    config.fontItalic,
                )
            text.textSize = cell * 0.5f * config.int(GauguinUiConfig.VALUE_SIZE) / 100f
            text.textAlign = Paint.Align.CENTER
            canvas.drawText("5", left + 1.5f * cell, top + 1.5f * cell + text.textSize / 3f, text)

            // the cage clue in the top-left corner
            text.color = config.int(GauguinUiConfig.CAGE_TEXT_COLOR)
            text.typeface =
                GauguinFonts.typeface(
                    context,
                    config.string(GauguinUiConfig.CAGE_TEXT_FONT).ifEmpty { config.fontFamily },
                    config.fontWeight,
                    config.fontItalic,
                )
            text.textSize = cell * 0.26f * config.int(GauguinUiConfig.CAGE_TEXT_SIZE) / 100f
            text.textAlign = Paint.Align.LEFT
            canvas.drawText("12+", left + 3 * density, top + text.textSize + 2 * density, text)

            // pencil marks in the bottom-left cell
            text.color = config.int(GauguinUiConfig.POSSIBLES_COLOR)
            text.typeface =
                GauguinFonts.typeface(
                    context,
                    config.string(GauguinUiConfig.POSSIBLES_FONT).ifEmpty { config.fontFamily },
                    config.fontWeight,
                    config.fontItalic,
                )
            text.textSize = cell * 0.22f * config.int(GauguinUiConfig.POSSIBLES_SIZE) / 100f
            canvas.drawText("1 2 3", left + 3 * density, top + 2 * cell + text.textSize + 3 * density, text)
        }

        private fun drawKeypad(
            canvas: Canvas,
            left: Float,
            top: Float,
            right: Float,
            cell: Float,
        ) {
            val keyRadius = config.int(GauguinUiConfig.KEY_CORNER) * density
            val keyBorder = config.int(GauguinUiConfig.KEY_BORDER_WIDTH) * density
            val gap = 6 * density
            val keyWidth = ((right - left) - 2 * gap) / 3f
            val keyHeight = cell * 0.85f

            text.color = config.int(GauguinUiConfig.KEY_TEXT_COLOR)
            text.typeface =
                GauguinFonts.typeface(
                    context,
                    config.string(GauguinUiConfig.KEY_FONT).ifEmpty { config.fontFamily },
                    config.int(GauguinUiConfig.KEY_WEIGHT),
                    config.fontItalic,
                )
            text.textSize = keyHeight * 0.45f * config.int(GauguinUiConfig.KEY_TEXT_SIZE) / 100f
            text.textAlign = Paint.Align.CENTER

            for (row in 0 until 2) {
                for (col in 0 until 3) {
                    val rect =
                        RectF(
                            left + col * (keyWidth + gap),
                            top + row * (keyHeight + gap),
                            left + col * (keyWidth + gap) + keyWidth,
                            top + row * (keyHeight + gap) + keyHeight,
                        )
                    fill.color = config.int(GauguinUiConfig.KEY_BACKGROUND)
                    canvas.drawRoundRect(rect, keyRadius, keyRadius, fill)
                    if (keyBorder > 0f) {
                        stroke.color = config.int(GauguinUiConfig.KEY_BORDER_COLOR)
                        stroke.strokeWidth = keyBorder
                        canvas.drawRoundRect(rect, keyRadius, keyRadius, stroke)
                    }
                    val label = (row * 3 + col + 1).toString()
                    canvas.drawText(label, rect.centerX(), rect.centerY() + text.textSize / 3f, text)
                }
            }
        }

        private fun drawSampleText(
            canvas: Canvas,
            left: Float,
            baseline: Float,
        ) {
            text.color = config.textColor
            text.typeface =
                GauguinFonts.typeface(context, config.fontFamily, config.fontWeight, config.fontItalic)
            text.textSize = 14 * density * config.fontScalePct / 100f
            text.textAlign = Paint.Align.LEFT
            canvas.drawText(context.getString(org.piepmeyer.gauguin.R.string.gauguin_ui_preview_sample), left, baseline, text)
        }
    }
