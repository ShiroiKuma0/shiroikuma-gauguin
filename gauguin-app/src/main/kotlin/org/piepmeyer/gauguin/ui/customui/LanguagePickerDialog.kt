package org.piepmeyer.gauguin.ui.customui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.piepmeyer.gauguin.R
import org.piepmeyer.gauguin.ui.customui.chrome.SkDialogs
import org.piepmeyer.gauguin.ui.customui.chrome.SkRole
import org.piepmeyer.gauguin.ui.customui.chrome.SkRoles
import java.util.Locale

/**
 * The app-language list, in the look the black-yellow-theme skill settles for it (2026-11-24):
 *
 *  - rows are plain; **only the current language sits in a pill** — full width, 3 dp, its name bold;
 *  - each row: the selection ring, the language's name in its own language with the country or
 *    region beneath it in dim gold, and at the right, in a column of its own, the tag in dim-gold
 *    monospace;
 *  - the tag column is at the right on every row, also for right-to-left languages, whose name
 *    stays at the left like the others;
 *  - "System default" comes first, with no country line and no tag;
 *  - a 1 px hairline between rows, inset to the text, none next to the current row's pill;
 *  - choosing a row applies it at once and closes the list; one Cancel pill.
 *
 * Every colour is a role. The rows are drawn here, so the dialog's own styling pass leaves them alone.
 */
object LanguagePickerDialog {
    fun show(
        context: Context,
        onPick: (String) -> Unit,
    ) {
        val current = GauguinLanguage.tag(context)
        val tags = listOf("") + GauguinLanguage.shippedTags(context).sortedBy { nameOf(it).lowercase() + " " + it }
        val config = GauguinUiConfig(context)
        val face = GauguinFonts.typeface(context, config.fontFamily)
        val text = SkRoles[SkRole.DIALOG_TEXT]
        val dim = SkRoles[SkRole.NOTE]
        val line = SkRoles[SkRole.LIST_DIVIDER]

        fun dp(value: Float): Int = (value * context.resources.displayMetrics.density).toInt()

        val list =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12f), dp(4f), dp(12f), dp(4f))
                SkDialogs.keep(this)
            }
        val scroll = ScrollView(context).apply { addView(list) }

        val dialog =
            AlertDialog
                .Builder(context)
                .setTitle(R.string.sk_section_language)
                .setView(scroll)
                .setNegativeButton(android.R.string.cancel, null)
                .create()

        var currentRow: View? = null
        tags.forEachIndexed { index, tag ->
            val isCurrent = tag == current
            val beforeCurrent = tags.getOrNull(index + 1) == current

            val row =
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    // one geometry for every language: ring, name, tag — left to right
                    layoutDirection = View.LAYOUT_DIRECTION_LTR
                    setPadding(dp(10f), dp(8f), dp(12f), dp(8f))
                    minimumHeight = dp(44f)
                    isClickable = true
                    isFocusable = true
                    if (isCurrent) {
                        background =
                            GradientDrawable().apply {
                                shape = GradientDrawable.RECTANGLE
                                cornerRadius = dp(22f).toFloat()
                                setStroke(dp(3f), SkRoles[SkRole.DIALOG_BUTTON_FRAME])
                            }
                    }
                    setOnClickListener {
                        dialog.dismiss()
                        if (!isCurrent) onPick(tag)
                    }
                }

            row.addView(
                View(context).apply { background = ring(context, isCurrent) },
                LinearLayout.LayoutParams(dp(18f), dp(18f)).apply { rightMargin = dp(12f) },
            )

            val names =
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutDirection = View.LAYOUT_DIRECTION_LTR
                }
            names.addView(
                TextView(context).apply {
                    this.text = if (tag.isEmpty()) context.getString(R.string.sk_set_lang_system) else nameOf(tag)
                    setTextColor(text)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    typeface = if (isCurrent) Typeface.create(face, Typeface.BOLD) else face
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                },
            )
            val region = if (tag.isEmpty()) "" else regionOf(tag)
            if (region.isNotEmpty()) {
                names.addView(
                    TextView(context).apply {
                        this.text = region
                        setTextColor(dim)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        typeface = face
                        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                    },
                )
            }
            row.addView(names, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            if (tag.isNotEmpty()) {
                row.addView(
                    TextView(context).apply {
                        this.text = tag
                        setTextColor(dim)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        typeface = Typeface.MONOSPACE
                        gravity = Gravity.END
                        textDirection = View.TEXT_DIRECTION_LTR
                    },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        leftMargin = dp(12f)
                    },
                )
            }

            list.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            if (isCurrent) currentRow = row

            // the hairline under this row: inset to the text, and none beside the current row's pill
            if (index < tags.lastIndex && !isCurrent && !beforeCurrent) {
                list.addView(
                    View(context).apply { setBackgroundColor(line) },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
                        leftMargin = dp(40f)
                        rightMargin = dp(12f)
                    },
                )
            }
        }

        dialog.show()
        currentRow?.let { row -> scroll.post { scroll.scrollTo(0, (row.top - row.height * 2).coerceAtLeast(0)) } }
    }

    /** The selection mark: a ring, with a dot on the current row */
    private fun ring(
        context: Context,
        on: Boolean,
    ): android.graphics.drawable.Drawable {
        val density = context.resources.displayMetrics.density
        val color = if (on) SkRoles[SkRole.MARK_ON] else SkRoles[SkRole.SWITCH_OFF]
        val outline =
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke((2 * density).toInt(), color)
            }
        if (!on) return outline
        val dot =
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
        val inset = (5 * density).toInt()
        return LayerDrawable(arrayOf(outline, dot)).apply { setLayerInset(1, inset, inset, inset, inset) }
    }

    /** A language's name in its own language: "日本語", "Deutsch" */
    fun nameOf(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayLanguage(locale).replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }

    /** Its country or region, in that language; empty when the tag names none */
    private fun regionOf(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayCountry(locale)
    }
}
