package org.piepmeyer.gauguin.ui.customui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.shape.MaterialShapeDrawable
import org.piepmeyer.gauguin.R

/**
 * Paints a live activity with the configured look.
 *
 * Every Gauguin activity already routes its content view through `ActivityUtils.configureRootView`,
 * so that is where this hooks in: one pass over the tree after layout, applying the background,
 * text colour, font, size scale and — for buttons — the border width, colour and corner radius.
 *
 * The grid itself is NOT painted here: it is a custom-drawn view whose colours come from
 * `GridPaintHolder` and whose fonts come from `GridFontHolder`, both of which read the config
 * directly.
 */
object GauguinUi {
    /** Style [root] and everything under it. No-op when the house look is switched off. */
    fun applyTo(root: View) {
        val config = GauguinUiConfig(root.context)
        if (!config.customUiActive) return
        // Post it: children of a just-inflated tree are not measured yet, and Material re-applies
        // its own tints during the first layout pass.
        root.post { paint(root, config, isRoot = true) }
    }

    private fun paint(
        view: View,
        config: GauguinUiConfig,
        isRoot: Boolean = false,
    ) {
        // The top panel has colours of its own in the config; GauguinChrome paints it from the
        // fragment that inflates it, so the global pass steps over the whole subtree.
        if (view.id == R.id.mainTopArea) return

        // The settings list is styled by the house preference layouts (see GauguinPreferences).
        // One global text colour and font here would flatten the headings' bold and the summaries'
        // dim — and it would only ever reach the rows that happen to be bound at this moment.
        if (view.id == R.id.settings) return

        val density = view.resources.displayMetrics.density
        val typeface = GauguinFonts.typeface(view.context, config.fontFamily, config.fontWeight, config.fontItalic)

        if (isRoot) view.background = config.backgroundColor.toDrawable()

        when (view) {
            is MaterialButton -> {
                view.setBackgroundColor(config.surfaceColor)
                // A tint from the layout wins over the fill, and the extended FAB carries one.
                view.backgroundTintList = ColorStateList.valueOf(config.surfaceColor)
                view.setTextColor(config.accentColor)
                view.typeface = typeface
                view.isAllCaps = false
                view.strokeWidth = (config.borderWidthDp * density).toInt()
                view.strokeColor = ColorStateList.valueOf(config.borderColor)
                view.cornerRadius = (config.cornerRadiusDp * density).toInt()
                view.iconTint = ColorStateList.valueOf(config.accentColor)
                scale(view, config)
            }

            // Before the ImageView branch: a FAB is one. It has no stroke of its own, so the border
            // is drawn as a ring over it.
            is FloatingActionButton -> {
                val surface = ColorStateList.valueOf(config.surfaceColor)
                view.backgroundTintList = surface
                // The FAB overrides setBackgroundTintList to recolour its own shape and nothing
                // else, so the View-level tint the layout sets keeps painting over that shape. The
                // background drawable has to be told separately.
                view.background?.setTintList(surface)
                view.imageTintList = ColorStateList.valueOf(config.accentColor)
                // Built from the FAB's own shape, so the border follows its rounded square instead
                // of boxing it in a circle.
                view.foreground =
                    MaterialShapeDrawable(view.shapeAppearanceModel).apply {
                        fillColor = ColorStateList.valueOf(Color.TRANSPARENT)
                        setStroke(config.borderWidthDp * density, config.borderColor)
                    }
            }

            // Before the Button branch: a tick box is a Button, and giving it a button's filled,
            // bordered background boxes the tick in. It gets the accent on its own mark instead.
            is CompoundButton -> {
                view.setTextColor(config.textColor)
                view.typeface = typeface
                view.buttonTintList = ColorStateList.valueOf(config.accentColor)
                scale(view, config)
            }

            is Button -> {
                view.setTextColor(config.accentColor)
                view.typeface = typeface
                view.isAllCaps = false
                view.background =
                    GradientDrawable().apply {
                        setColor(config.surfaceColor)
                        setStroke((config.borderWidthDp * density).toInt(), config.borderColor)
                        cornerRadius = config.cornerRadiusDp * density
                    }
                scale(view, config)
            }

            is EditText -> {
                view.setTextColor(config.textColor)
                view.setHintTextColor(config.int(GauguinUiConfig.LIST_DIVIDER_COLOR))
                view.typeface = typeface
                scale(view, config)
            }

            is TextView -> {
                view.setTextColor(config.textColor)
                view.typeface = typeface
                scale(view, config)
            }

            is ImageView -> view.imageTintList = ColorStateList.valueOf(config.accentColor)
        }

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) paint(view.getChildAt(index), config)
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
        if (view.getTag(SCALED_TAG) == true) return
        view.setTag(SCALED_TAG, true)
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, view.textSize * config.fontScalePct / 100f)
    }

    private val SCALED_TAG = "gauguin-ui-scaled".hashCode()
}
