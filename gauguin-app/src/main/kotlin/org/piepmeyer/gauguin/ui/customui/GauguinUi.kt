package org.piepmeyer.gauguin.ui.customui

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.materialdrawer.widget.MaterialDrawerSliderView
import org.piepmeyer.gauguin.R
import org.piepmeyer.gauguin.ui.customui.chrome.SkRole
import org.piepmeyer.gauguin.ui.customui.chrome.SkRoles

/**
 * The part of the configured look a theme cannot carry: the root's ground, the font and the size
 * scale.
 *
 * Colours are NOT painted here. Every colour reaches upstream's layouts through the theme overlay
 * and the role resources (chrome/SkTheme, chrome/SkPalette), and every button, switch, mark, card
 * and dialog is drawn as it is inflated (chrome/SkViewInflater). One walk over the tree painting a
 * single text colour, as this object used to do, would flatten exactly what the roles tell apart.
 *
 * Every Gauguin activity routes its content view through `ActivityUtils.configureRootView`, so that
 * is where this hooks in. The grid is custom-drawn: its colours come from `GridPaintHolder` and its
 * fonts from `GridFontHolder`, both of which read the config directly.
 */
object GauguinUi {
    /** Ground and fonts for [root] and everything under it. No-op when the house look is off. */
    fun applyTo(root: View) {
        val config = GauguinUiConfig(root.context)
        if (!config.customUiActive) return
        val background = SkRoles[SkRole.BACKGROUND]
        root.background = background.toDrawable()
        // The system draws its bar icons dark or light by upstream's day/night theme; they have to
        // follow the ground the fork actually paints under them.
        (root.context as? Activity)?.window?.let { window ->
            val light = ColorUtils.calculateLuminance(background) > 0.5
            WindowCompat.getInsetsController(window, root).apply {
                isAppearanceLightStatusBars = light
                isAppearanceLightNavigationBars = light
            }
        }
        // Post it: children of a just-inflated tree are not measured yet.
        root.post { fonts(root) }
    }

    /** The global font and size scale on [root] and everything under it, rows of lists included. */
    fun fonts(root: View) {
        val config = GauguinUiConfig(root.context)
        val plainFont = config.fontFamily.isEmpty() && config.fontWeight <= 0 && !config.fontItalic
        if (plainFont && config.fontScalePct == 100) return
        val typeface = GauguinFonts.typeface(root.context, config.fontFamily, config.fontWeight, config.fontItalic)
        walk(root, typeface, plainFont, config)
    }

    private fun walk(
        view: View,
        typeface: Typeface,
        plainFont: Boolean,
        config: GauguinUiConfig,
    ) {
        // The top panel and the number pad have fonts of their own in the config.
        if (view.id == R.id.mainTopArea || view.id == R.id.keypadFrame) return
        // A view that draws itself from its own settings (a font sample, a colour preview).
        if (view.getTag(R.id.tag_sk_dialog_keep) != null) return

        if (view is TextView) {
            if (!plainFont) {
                // a set weight is the weight; otherwise a bold label stays bold in the new family
                view.typeface =
                    if (config.fontWeight > 0) typeface else Typeface.create(typeface, view.typeface?.style ?: Typeface.NORMAL)
            }
            scale(view, config)
        }

        if (view is RecyclerView && view.getTag(R.id.tag_sk_font) == null) {
            // rows are bound long after this pass: each takes the font as it arrives
            view.setTag(R.id.tag_sk_font, true)
            val plainList = isPlainList(view)
            if (plainList) view.addItemDecoration(ListDividers(config))
            view.addOnChildAttachStateChangeListener(
                object : RecyclerView.OnChildAttachStateChangeListener {
                    override fun onChildViewAttachedToWindow(child: View) {
                        fonts(child)
                        if (plainList) listRow(child, config)
                    }

                    override fun onChildViewDetachedFromWindow(child: View) = Unit
                },
            )
        }

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) walk(view.getChildAt(index), typeface, plainFont, config)
        }
    }

    /**
     * A list of the app's own (the saved games), as opposed to a settings list or the drawer, which
     * have sections of their own: its rows follow the "Lists and cards" section.
     */
    private fun isPlainList(list: RecyclerView): Boolean {
        if (list.id == androidx.preference.R.id.recycler_view) return false
        var parent = list.parent
        while (parent is View) {
            if (parent is MaterialDrawerSliderView) return false
            parent = parent.parent
        }
        return true
    }

    /** One row of such a list: the list's text colour, font, size and row padding. */
    private fun listRow(
        row: View,
        config: GauguinUiConfig,
    ) {
        if (row.getTag(R.id.tag_sk_list) == null) {
            row.setTag(R.id.tag_sk_list, true)
            val pad = (config.int(GauguinUiConfig.LIST_ROW_PADDING) * row.resources.displayMetrics.density).toInt()
            (row.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                it.topMargin = pad
                it.bottomMargin = pad
                row.layoutParams = it
            }
        }
        val family = config.string(GauguinUiConfig.LIST_FONT)
        val typeface = if (family.isEmpty()) null else GauguinFonts.typeface(row.context, family, config.fontWeight, config.fontItalic)
        val scalePct = config.int(GauguinUiConfig.LIST_TEXT_SIZE)

        fun paint(view: View) {
            if (view is Button) return
            if (view is TextView) {
                view.setTextColor(SkRoles[SkRole.LIST_TEXT])
                if (typeface != null) view.typeface = typeface
                if (scalePct != 100 && view.getTag(R.id.tag_sk_list) == null) {
                    // the scale is relative: once per view
                    view.setTag(R.id.tag_sk_list, true)
                    view.setTextSize(TypedValue.COMPLEX_UNIT_PX, view.textSize * scalePct / 100f)
                }
            }
            if (view is ViewGroup) for (index in 0 until view.childCount) paint(view.getChildAt(index))
        }
        paint(row)
    }

    /** The line between two rows of such a list, in the divider role; the width slider goes to 0. */
    private class ListDividers(
        private val config: GauguinUiConfig,
    ) : RecyclerView.ItemDecoration() {
        private val paint = Paint()

        override fun onDrawOver(
            canvas: Canvas,
            parent: RecyclerView,
            state: RecyclerView.State,
        ) {
            val width = config.int(GauguinUiConfig.LIST_DIVIDER_WIDTH) * parent.resources.displayMetrics.density
            if (width <= 0f) return
            paint.color = SkRoles[SkRole.LIST_DIVIDER]
            for (index in 0 until parent.childCount - 1) {
                val row = parent.getChildAt(index)
                val next = parent.getChildAt(index + 1)
                val y = (row.bottom + next.top) / 2f
                canvas.drawRect(row.left.toFloat(), y - width / 2f, row.right.toFloat(), y + width / 2f, paint)
            }
        }
    }

    /**
     * Apply the global size scale once per view. The scale is relative, so re-running the pass would
     * compound it — the tag records that this view has already been scaled.
     */
    private fun scale(
        view: TextView,
        config: GauguinUiConfig,
    ) {
        if (config.fontScalePct == 100) return
        if (view.getTag(R.id.tag_sk_font) == true) return
        view.setTag(R.id.tag_sk_font, true)
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, view.textSize * config.fontScalePct / 100f)
    }
}
