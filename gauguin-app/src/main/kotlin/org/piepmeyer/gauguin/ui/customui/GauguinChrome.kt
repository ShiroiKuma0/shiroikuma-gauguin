package org.piepmeyer.gauguin.ui.customui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.view.iterator
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.card.MaterialCardView
import com.mikepenz.materialdrawer.model.BaseDrawerItem
import com.mikepenz.materialdrawer.widget.MaterialDrawerSliderView
import org.piepmeyer.gauguin.R

/**
 * The chrome around the game — the top panel, the navigation drawer and the bottom bar — painted
 * from the knobs of their own categories.
 *
 * [GauguinUi]'s single walk over the activity tree cannot do this on its own:
 * - the top panel and the drawer carry their **own** colours in the config, not the global ones;
 * - a `MaterialCardView` and the drawer panel are tinted, not backgrounded, so painting a text
 *   colour on their children leaves the surface underneath untouched;
 * - the drawer repaints its rows from the item model on every bind, so a colour written onto the
 *   row view is gone as soon as the list scrolls;
 * - the bottom bar's overflow lives in a window of its own that no view walk can reach — that one
 *   is themed in XML, see `ThemeOverlay.Gauguin.ShiroikumaPopup`.
 *
 * The drawer entry points post their work: the global pass is posted from the activity's
 * `onCreate`, so posting again from a later point in the same `onCreate` puts these behind it and
 * the more specific colours win.
 */
object GauguinChrome {
    /**
     * The panel above the grid: its own background and foreground, and the logo toggle.
     *
     * Called from the fragment that inflates it rather than left to the global pass, because that
     * pass deliberately steps over this subtree.
     */
    fun applyToTopPanel(root: View) {
        val config = GauguinUiConfig(root.context)
        if (!config.customUiActive) return

        val card = root.findViewById<MaterialCardView>(R.id.mainTopArea) ?: return
        val background = config.int(GauguinUiConfig.TOP_BACKGROUND)

        // The layout tints the card rather than colouring it, and a tint wins over the fill: set
        // both, or the theme's tertiary container keeps showing through.
        card.backgroundTintList = ColorStateList.valueOf(background)
        card.setCardBackgroundColor(background)
        card.strokeWidth = 0
        card.cardElevation = 0f

        paintTopPanel(
            card,
            config.int(GauguinUiConfig.TOP_FOREGROUND),
            GauguinFonts.typeface(
                card.context,
                config.string(GauguinUiConfig.TOP_FONT).ifEmpty { config.fontFamily },
                config.fontWeight,
                config.fontItalic,
            ),
            config.int(GauguinUiConfig.TOP_TEXT_SIZE),
        )

        root.findViewById<View>(R.id.appicon)?.isVisible = config.bool(GauguinUiConfig.TOP_SHOW_LOGO)
    }

    /** The drawer panel itself: the configured fill, and an accent border drawn over the rows. */
    fun applyToDrawer(slider: MaterialDrawerSliderView) {
        val config = GauguinUiConfig(slider.context)
        if (!config.customUiActive) return

        slider.post {
            val density = slider.resources.displayMetrics.density
            val radius = config.cornerRadiusDp * density
            // Rounded on the open side only — the panel sits flush against the screen edge.
            val corners = floatArrayOf(0f, 0f, radius, radius, radius, radius, 0f, 0f)

            slider.background =
                GradientDrawable().apply {
                    setColor(config.int(GauguinUiConfig.DRAWER_BACKGROUND))
                    cornerRadii = corners
                }
            // As a foreground: a stroke on the background would be covered by the row list.
            slider.foreground =
                GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    setStroke((config.borderWidthDp * density).toInt(), config.borderColor)
                    cornerRadii = corners
                }
        }
    }

    /**
     * The drawer header: the fork's mark and name on the drawer's own background.
     *
     * Upstream fills this with one of Gauguin's paintings. The global pass tints every image with
     * the accent colour, which turns that photo into a single solid yellow block, so the fork drops
     * the image and keeps the space it reserved.
     */
    fun applyToDrawerHeader(header: View) {
        val config = GauguinUiConfig(header.context)
        if (!config.customUiActive) return

        header.post {
            val background = config.int(GauguinUiConfig.DRAWER_BACKGROUND)
            val text = config.int(GauguinUiConfig.DRAWER_TEXT_COLOR)

            header.setBackgroundColor(background)
            header.findViewById<ImageView>(R.id.navigation_drawer_picture)?.apply {
                setImageDrawable(null)
                imageTintList = null
                setBackgroundColor(background)
            }
            // The fork's own mark is already black-and-yellow; an accent tint would flatten it.
            header.findViewById<ImageView>(R.id.navigation_drawer_app_icon)?.imageTintList = null
            listOf(R.id.navigation_drawer_app_title, R.id.navigation_drawer_app_version).forEach {
                header.findViewById<TextView>(it)?.setTextColor(text)
            }
        }
    }

    /** Row colours and font, set on the item so they survive every rebind of the drawer list. */
    fun applyToDrawerItem(
        item: BaseDrawerItem<*, *>,
        context: Context,
    ) {
        val config = GauguinUiConfig(context)
        if (!config.customUiActive) return

        item.textColor = ColorStateList.valueOf(config.int(GauguinUiConfig.DRAWER_TEXT_COLOR))
        item.iconColor = ColorStateList.valueOf(config.accentColor)
        item.typeface =
            GauguinFonts.typeface(
                context,
                config.string(GauguinUiConfig.DRAWER_FONT).ifEmpty { config.fontFamily },
                config.fontWeight,
                config.fontItalic,
            )
    }

    /** The bottom bar: the house surface, with navigation, action and overflow icons in accent. */
    fun applyToBottomBar(bar: BottomAppBar) {
        val config = GauguinUiConfig(bar.context)
        if (!config.customUiActive) return

        val accent = config.accentColor

        bar.backgroundTint = ColorStateList.valueOf(config.surfaceColor)
        bar.navigationIcon = bar.navigationIcon?.mutate()?.apply { setTint(accent) }
        bar.overflowIcon = bar.overflowIcon?.mutate()?.apply { setTint(accent) }
        bar.menu.iterator().forEach { item ->
            item.icon = item.icon?.mutate()?.apply { setTint(accent) }
        }
    }

    private fun paintTopPanel(
        view: View,
        foreground: Int,
        typeface: Typeface,
        scalePct: Int,
    ) {
        when (view) {
            is TextView -> {
                view.setTextColor(foreground)
                view.typeface = typeface
                // The scale is relative, so it may only ever be applied once per view.
                if (scalePct != 100 && view.getTag(SCALED_TAG) != true) {
                    view.setTag(SCALED_TAG, true)
                    view.setTextSize(TypedValue.COMPLEX_UNIT_PX, view.textSize * scalePct / 100f)
                }
            }

            is ImageView -> view.imageTintList = ColorStateList.valueOf(foreground)
        }

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) paintTopPanel(view.getChildAt(index), foreground, typeface, scalePct)
        }
    }

    private val SCALED_TAG = "gauguin-chrome-scaled".hashCode()
}
