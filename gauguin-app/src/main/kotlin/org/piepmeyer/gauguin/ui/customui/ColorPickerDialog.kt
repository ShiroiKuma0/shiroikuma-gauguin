package org.piepmeyer.gauguin.ui.customui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.piepmeyer.gauguin.ui.customui.chrome.SkDialogs
import org.piepmeyer.gauguin.ui.customui.chrome.SkRole
import org.piepmeyer.gauguin.ui.customui.chrome.SkRoles

/**
 * The house colour picker: a row of one-click swatches prefilled with previously chosen colours, a
 * live preview showing the ARGB hex, and four A/R/G/B sliders.
 *
 * Every change applies LIVE through [onColor] so the page's preview updates as the slider moves;
 * Cancel puts back the colour the dialog opened with, OK keeps it and remembers it as a swatch,
 * and Default — offered for a colour role — puts the role's own default back.
 */
object ColorPickerDialog {
    private const val PREFS = "gauguin_color_picker"
    private const val KEY_RECENT = "recent"
    private const val MAX_RECENT = 8

    fun show(
        context: Context,
        titleRes: Int,
        initial: Int,
        onDefault: (() -> Unit)? = null,
        onDone: () -> Unit = {},
        onColor: (Int) -> Unit,
    ) {
        val density = context.resources.displayMetrics.density
        val accent = SkRoles[SkRole.DIALOG_FRAME]
        val ink = SkRoles[SkRole.DIALOG_TEXT]

        var a = Color.alpha(initial)
        var r = Color.red(initial)
        var g = Color.green(initial)
        var b = Color.blue(initial)

        val sliders = mutableListOf<SeekBar>()
        val preview =
            TextView(context).apply {
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                minHeight = (56 * density).toInt()
                // it shows the colour being picked, in an ink chosen against it: not the dialog's
                SkDialogs.keep(this)
            }

        fun current() = Color.argb(a, r, g, b)

        fun refresh(apply: Boolean) {
            val color = current()
            preview.background =
                GradientDrawable().apply {
                    setColor(color)
                    setStroke((1.5f * density).toInt(), accent)
                    cornerRadius = 6f * density
                }
            // Pick a legible label colour for whatever is behind it — a dark or very transparent
            // swatch takes white, a light one black.
            val luminance = 0.299 * r + 0.587 * g + 0.114 * b
            preview.setTextColor(if (luminance < 128 || a < 128) Color.WHITE else Color.BLACK)
            preview.text = String.format("#%02X%02X%02X%02X", a, r, g, b)
            if (apply) onColor(color)
        }

        fun setFrom(color: Int) {
            a = Color.alpha(color)
            r = Color.red(color)
            g = Color.green(color)
            b = Color.blue(color)
            sliders.getOrNull(0)?.progress = a
            sliders.getOrNull(1)?.progress = r
            sliders.getOrNull(2)?.progress = g
            sliders.getOrNull(3)?.progress = b
            refresh(apply = true)
        }

        fun channelRow(
            label: String,
            value: Int,
            onChange: (Int) -> Unit,
        ): View {
            val row =
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
            row.addView(
                TextView(context).apply {
                    text = label
                    setTextColor(ink)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    width = (24 * density).toInt()
                },
            )
            val seek =
                SeekBar(context).apply {
                    max = 255
                    progress = value
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
            sliders.add(seek)
            row.addView(seek)
            val readout =
                TextView(context).apply {
                    setTextColor(ink)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    minWidth = (36 * density).toInt()
                    gravity = Gravity.END
                    text = value.toString()
                }
            seek.setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean,
                    ) {
                        onChange(progress)
                        readout.text = progress.toString()
                        refresh(apply = true)
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                    override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                },
            )
            row.addView(readout)
            return row
        }

        // One-click prefilled swatches: what was picked before, seeded with the house staples.
        val swatchRow =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
        val swatchSize = (34 * density).toInt()
        val gap = (6 * density).toInt()
        recent(context).forEach { swatch ->
            swatchRow.addView(
                View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(swatchSize, swatchSize).apply { marginEnd = gap }
                    background =
                        GradientDrawable().apply {
                            setColor(swatch)
                            setStroke((1.5f * density).toInt(), accent)
                            cornerRadius = 4f * density
                        }
                    setOnClickListener { setFrom(swatch) }
                },
            )
        }

        val pad = (20 * density).toInt()

        fun spaced(
            view: View,
            bottomDp: Int,
        ): View {
            val params =
                view.layoutParams as? LinearLayout.LayoutParams
                    ?: LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    )
            params.bottomMargin = (bottomDp * density).toInt()
            view.layoutParams = params
            return view
        }

        val layout =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(pad, pad / 2, pad, pad / 2)
                if (swatchRow.childCount > 0) addView(spaced(swatchRow, 16))
                addView(spaced(preview, 16))
                addView(spaced(channelRow("A", a) { a = it }, 6))
                addView(spaced(channelRow("R", r) { r = it }, 6))
                addView(spaced(channelRow("G", g) { g = it }, 6))
                addView(channelRow("B", b) { b = it })
            }
        refresh(apply = false)

        val builder =
            AlertDialog
                .Builder(context)
                .setTitle(titleRes)
                .setView(layout)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val color = current()
                    onColor(color)
                    remember(context, color)
                    onDone()
                }.setNegativeButton(android.R.string.cancel) { _, _ ->
                    onColor(initial)
                    onDone()
                }.setOnCancelListener {
                    onColor(initial)
                    onDone()
                }
        if (onDefault != null) {
            builder.setNeutralButton(org.piepmeyer.gauguin.R.string.sk_default) { _, _ ->
                onDefault()
                onDone()
            }
        }
        builder.show()
    }

    private fun recent(context: Context): List<Int> {
        val stored =
            context
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_RECENT, null)
                ?.split(",")
                ?.mapNotNull { it.toIntOrNull() }
                ?: emptyList()
        val seeds =
            listOf(
                // the foundation colours as they stand, then two presets that are not roles
                SkRoles[SkRole.BACKGROUND],
                SkRoles[SkRole.ACCENT],
                SkRoles[SkRole.NOTE],
                SkRoles[SkRole.DELETE],
                Color.WHITE,
                Color.TRANSPARENT,
            )
        return (stored + seeds).distinct().take(MAX_RECENT)
    }

    private fun remember(
        context: Context,
        color: Int,
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current =
            prefs.getString(KEY_RECENT, null)?.split(",")?.mapNotNull { it.toIntOrNull() } ?: emptyList()
        val updated = (listOf(color) + current).distinct().take(MAX_RECENT)
        prefs.edit().putString(KEY_RECENT, updated.joinToString(",")).apply()
    }
}
