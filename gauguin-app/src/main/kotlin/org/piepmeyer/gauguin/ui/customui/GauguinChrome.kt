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
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.isVisible
import androidx.core.view.iterator
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.card.MaterialCardView
import com.mikepenz.materialdrawer.model.BaseDrawerItem
import com.mikepenz.materialdrawer.widget.MaterialDrawerSliderView
import org.piepmeyer.gauguin.R
import org.piepmeyer.gauguin.ui.customui.chrome.SkIcons
import org.piepmeyer.gauguin.ui.customui.chrome.SkRole
import org.piepmeyer.gauguin.ui.customui.chrome.SkRoles

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
 * - the bottom bar's overflow lives in a window of its own that no view walk can reach — its rows
 *   are drawn by `chrome/PillMenuItemView`, through the app's copies of AppCompat's row layouts.
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
            sizeDrawerRows(slider, config)
            // As a foreground: a stroke on the background would be covered by the row list.
            slider.foreground =
                GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    setStroke((config.borderWidthDp * density).toInt(), SkRoles[SkRole.DRAWER_FRAME])
                    cornerRadii = corners
                }
        }
    }

    /**
     * The drawer's two size knobs: the text size of its rows, in per cent of the library's own,
     * and the edge of their icons in dp. The library draws a row from its item model, which holds
     * neither, so each row is sized as it is attached; a row grows with the larger of the two.
     */
    private fun sizeDrawerRows(
        slider: MaterialDrawerSliderView,
        config: GauguinUiConfig,
    ) {
        val textPct = config.int(GauguinUiConfig.DRAWER_TEXT_SIZE)
        val iconDp = config.int(GauguinUiConfig.DRAWER_ICON_SIZE)
        if (textPct == 100 && iconDp == DRAWER_ICON_DEFAULT_DP) return

        val listener =
            object : RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(view: View) = sizeDrawerRow(view, textPct, iconDp)

                override fun onChildViewDetachedFromWindow(view: View) = Unit
            }
        slider.recyclerView.addOnChildAttachStateChangeListener(listener)
        for (index in 0 until slider.recyclerView.childCount) {
            sizeDrawerRow(slider.recyclerView.getChildAt(index), textPct, iconDp)
        }
    }

    private fun sizeDrawerRow(
        row: View,
        textPct: Int,
        iconDp: Int,
    ) {
        val name = row.findViewById<TextView>(com.mikepenz.materialdrawer.R.id.material_drawer_name) ?: return
        val icon = row.findViewById<ImageView>(com.mikepenz.materialdrawer.R.id.material_drawer_icon)
        val density = row.resources.displayMetrics.density

        val baseText = (name.getTag(R.id.sk_tag_base_text) as? Float) ?: name.textSize.also { name.setTag(R.id.sk_tag_base_text, it) }
        val textPx = baseText * textPct / 100f
        name.setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx)

        val params = row.layoutParams ?: return
        if (params.height <= 0) return
        val baseHeight = (row.getTag(R.id.sk_tag_base_height) as? Int) ?: params.height.also { row.setTag(R.id.sk_tag_base_height, it) }
        val iconPx = (iconDp * density).toInt()
        val height = maxOf(baseHeight, iconPx + (8 * density).toInt(), (textPx * 1.7f).toInt())
        if (params.height != height) {
            params.height = height
            row.layoutParams = params
        }

        if (icon != null) {
            val vertical = (height - iconPx) / 2
            icon.setPadding(icon.paddingLeft, vertical, icon.paddingRight, vertical)
            icon.layoutParams =
                icon.layoutParams.apply {
                    width = iconPx + icon.paddingLeft + icon.paddingRight
                    this.height = height
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

        header.isVisible = config.bool(GauguinUiConfig.DRAWER_SHOW_HEADER)

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

    /**
     * Row colours and font, set on the item so they survive every rebind of the drawer list. The
     * icon takes the colour of what the row [does]: a drawer is read by colour before it is read by
     * word, like a menu.
     */
    fun applyToDrawerItem(
        item: BaseDrawerItem<*, *>,
        context: Context,
        does: SkIcons.Does,
    ) {
        val config = GauguinUiConfig(context)
        if (!config.customUiActive) return

        item.textColor = ColorStateList.valueOf(config.int(GauguinUiConfig.DRAWER_TEXT_COLOR))
        item.iconColor = ColorStateList.valueOf(SkIcons.color(does))
        item.typeface =
            GauguinFonts.typeface(
                context,
                config.string(GauguinUiConfig.DRAWER_FONT).ifEmpty { config.fontFamily },
                config.fontWeight,
                config.fontItalic,
            )
    }

    /**
     * A long-press on the bottom bar's menu button opens the 白い熊 GNU Gauguin UI page, promptly.
     * The button is the toolbar's own child, without an id: the one image button outside the
     * action menu.
     *
     * The same fix as 白い熊 地図's drawer button (`DrawerMenuButton.java` there, commits f6bf273d,
     * e022a7f6, 04ef6bf5), for the same trouble:
     *  - the button keeps its own clock and opens the page while the finger is still down, instead
     *    of the view's long-click;
     *  - **the clock runs from the touch itself**, [LONG_PRESS_MS] after `downTime`. On 白い熊's
     *    phone a held touch at the bottom of the screen reaches the app some 320–360 ms late; a
     *    wait started only on arrival is added to that. Counted from the touch, the page opens the
     *    moment the late touch arrives;
     *  - the rest of the gesture is swallowed, so no drawer opens as well;
     *  - the page comes without a window animation, as 地図's page does, being a fragment there.
     *
     * The button also claims its touch on the way down: it sits where the drawer layout watches
     * for an edge touch. Every step goes to the "touch" diagnostic when its switch is on.
     *
     * **The button is lifted out of the bar and sits right above it** (白い熊, 2026-11-24; the bar
     * and its other buttons stay where they are). Measured on 白い熊's phone that day: the
     * system's `GestureNavBottom` window covers the bottom 40 dp of the screen, and a touch landing
     * inside it is held some 300 ms before the app is told, while one landing just above it
     * arrives within 10–27 ms. The bar is 48 dp tall at the very bottom, so its menu button lay
     * almost wholly inside that strip. The phone reports no bottom gesture area to apps.
     */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    fun liftMenuButton(
        bar: BottomAppBar,
        onClick: () -> Unit,
    ) {
        GauguinDiag.init(bar.context)
        val slop = android.view.ViewConfiguration.get(bar.context).scaledTouchSlop
        val lifted = liftedMenuButton(bar, onClick) ?: return

        fun hook() {
            run {
                val button = lifted
                var opened = false
                var downTime = 0L
                var downX = 0f
                var downY = 0f
                val open =
                    Runnable {
                        opened = true
                        GauguinDiag.log(GauguinDiag.Kind.TOUCH) {
                            "fire: long-press ${android.os.SystemClock.uptimeMillis() - downTime} ms after the touch"
                        }
                        button.cancelLongPress()
                        button.isPressed = false
                        button.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                        GauguinUiActivity.openInstantly(button.context, downTime)
                    }
                button.setOnLongClickListener(null)
                button.setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                            opened = false
                            downTime = event.downTime
                            downX = event.x
                            downY = event.y
                            // timed from the touch itself, not from its late arrival
                            val late = android.os.SystemClock.uptimeMillis() - downTime
                            GauguinDiag.log(GauguinDiag.Kind.TOUCH) {
                                "down: reached the button $late ms late, raw ${event.rawX.toInt()},${event.rawY.toInt()}"
                            }
                            view.removeCallbacks(open)
                            view.postDelayed(open, maxOf(0L, LONG_PRESS_MS - late))
                            false
                        }

                        android.view.MotionEvent.ACTION_MOVE -> {
                            if (kotlin.math.abs(event.x - downX) > slop || kotlin.math.abs(event.y - downY) > slop) {
                                if (!opened) GauguinDiag.log(GauguinDiag.Kind.TOUCH, "move: left the spot, long-press called off")
                                view.removeCallbacks(open)
                            }
                            opened
                        }

                        else -> {
                            GauguinDiag.log(GauguinDiag.Kind.TOUCH) {
                                "end: action ${event.actionMasked}, held ${event.eventTime - event.downTime} ms, opened $opened"
                            }
                            view.removeCallbacks(open)
                            // once the page is opening, the lift must not also count as a tap
                            opened
                        }
                    }
                }
            }
        }
        hook()
    }

    /** The menu button as a view of its own, above the bar's start; made once per bar */
    private fun liftedMenuButton(
        bar: BottomAppBar,
        onClick: () -> Unit,
    ): android.widget.ImageButton? {
        val parent = bar.parent as? androidx.coordinatorlayout.widget.CoordinatorLayout ?: return null
        parent.findViewById<android.widget.ImageButton>(R.id.sk_main_menu)?.let { return it }
        val icon = bar.navigationIcon ?: return null
        val description = bar.navigationContentDescription
        bar.navigationIcon = null

        val size = (48 * bar.resources.displayMetrics.density).toInt()
        val button =
            androidx.appcompat.widget.AppCompatImageButton(bar.context).apply {
                id = R.id.sk_main_menu
                setImageDrawable(icon)
                scaleType = ImageView.ScaleType.CENTER
                contentDescription = description
                val ripple = TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)
                setBackgroundResource(ripple.resourceId)
                setOnClickListener { onClick() }
            }
        val params =
            androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(size, size).apply {
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.START
            }
        parent.addView(button, params)

        // right above the bar, at the bar's own start, wherever the layout and the insets put it
        fun place() {
            if (parent.height == 0) return
            val bottom = parent.height - bar.top
            val start = if (bar.layoutDirection == View.LAYOUT_DIRECTION_RTL) parent.width - bar.right else bar.left
            val current = button.layoutParams as androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
            if (current.bottomMargin != bottom || current.marginStart != start) {
                current.bottomMargin = bottom
                current.marginStart = start
                button.layoutParams = current
            }
        }
        bar.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> bar.post { place() } }
        bar.post { place() }
        return button
    }

    /**
     * 白い熊 wants the grid as large as the screen allows (2026-11-24). Upstream caps a cell at the
     * "maximum cell size" setting, 72 dp when untouched, which leaves a large screen half empty.
     * Once per installation the fork lifts the stored value to the top of its own wider slider;
     * the setting stays 白い熊's to lower again afterwards.
     */
    fun growGridOnce(context: Context) {
        val own = context.getSharedPreferences(GauguinUiConfig.PREFS, Context.MODE_PRIVATE)
        if (own.getBoolean(GRID_GROWN, false)) return
        androidx.preference.PreferenceManager
            .getDefaultSharedPreferences(context)
            .edit()
            .putInt("maximumCellSize", GauguinPreferences.MAXIMUM_CELL_SIZE_DP)
            .apply()
        own.edit().putBoolean(GRID_GROWN, true).apply()
    }

    /**
     * On a large screen held upright (an unfolded phone, a tablet in portrait) upstream puts the
     * number pad beside the grid, which costs the grid a third of the width. The fork moves the
     * pad under the grid there, the way upstream's own smaller layouts have it, so that the grid
     * gets the whole width. Upstream's layout file stays as it is: the constraints are rewired
     * here, and only when the pad really is beside the grid.
     */
    fun keypadToBottom(root: View) {
        val layout = root.findViewById<ConstraintLayout>(R.id.mainConstraintLayout) ?: return
        val keypad = root.findViewById<View>(R.id.keypadFrame) ?: return
        val besideGrid = (keypad.layoutParams as? ConstraintLayout.LayoutParams)?.startToEnd == R.id.gridview
        val upright = root.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
        if (!besideGrid || !upright) return

        val density = root.resources.displayMetrics.density
        val gap = (16 * density).toInt()
        val parent = ConstraintSet.PARENT_ID

        ConstraintSet().apply {
            clone(layout)

            listOf(R.id.gridview, R.id.keypadFrame, R.id.ferrisWheelView, R.id.gameSolvedFrame, R.id.fastFinishingModeFrame, R.id.numeral_system)
                .forEach { id ->
                    listOf(ConstraintSet.START, ConstraintSet.END, ConstraintSet.TOP, ConstraintSet.BOTTOM).forEach { clear(id, it) }
                }

            connect(R.id.gridview, ConstraintSet.TOP, R.id.gameTopFrame, ConstraintSet.BOTTOM, gap)
            connect(R.id.gridview, ConstraintSet.BOTTOM, R.id.keypadFrame, ConstraintSet.TOP, gap)
            connect(R.id.gridview, ConstraintSet.START, parent, ConstraintSet.START, gap)
            connect(R.id.gridview, ConstraintSet.END, parent, ConstraintSet.END, gap)

            listOf(R.id.keypadFrame, R.id.fastFinishingModeFrame).forEach { id ->
                setMargin(id, ConstraintSet.START, 0)
                setMargin(id, ConstraintSet.END, 0)
            }
            connect(R.id.keypadFrame, ConstraintSet.BOTTOM, parent, ConstraintSet.BOTTOM)
            connect(R.id.keypadFrame, ConstraintSet.START, parent, ConstraintSet.START)
            connect(R.id.keypadFrame, ConstraintSet.END, parent, ConstraintSet.END)

            listOf(R.id.ferrisWheelView to R.id.gridview, R.id.fastFinishingModeFrame to R.id.keypadFrame).forEach { (id, over) ->
                connect(id, ConstraintSet.TOP, over, ConstraintSet.TOP)
                connect(id, ConstraintSet.BOTTOM, over, ConstraintSet.BOTTOM)
                connect(id, ConstraintSet.START, over, ConstraintSet.START)
                connect(id, ConstraintSet.END, over, ConstraintSet.END)
            }

            constrainWidth(R.id.gameSolvedFrame, ConstraintSet.MATCH_CONSTRAINT)
            connect(R.id.gameSolvedFrame, ConstraintSet.BOTTOM, parent, ConstraintSet.BOTTOM)
            connect(R.id.gameSolvedFrame, ConstraintSet.START, parent, ConstraintSet.START)
            connect(R.id.gameSolvedFrame, ConstraintSet.END, parent, ConstraintSet.END)

            connect(R.id.numeral_system, ConstraintSet.TOP, R.id.keypadFrame, ConstraintSet.TOP)
            connect(R.id.numeral_system, ConstraintSet.START, parent, ConstraintSet.START, gap)

            applyTo(layout)
        }

        // room for the bottom bar and its button, as in upstream's layouts with the pad below
        (layout.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
            params.bottomMargin = (72 * density).toInt()
            layout.layoutParams = params
        }
    }

    /** The bottom bar: its ground and its glyphs, in the two roles of the Bars section. */
    fun applyToBottomBar(bar: BottomAppBar) {
        val config = GauguinUiConfig(bar.context)
        if (!config.customUiActive) return

        val accent = SkRoles[SkRole.BOTTOM_BAR_ICON]

        bar.backgroundTint = ColorStateList.valueOf(SkRoles[SkRole.BOTTOM_BAR_GROUND])
        bar.navigationIcon = bar.navigationIcon?.mutate()?.apply { setTint(accent) }
        // the menu button, once it is lifted out of the bar
        (bar.parent as? View)?.findViewById<ImageView>(R.id.sk_main_menu)?.let { lifted ->
            lifted.setImageDrawable(lifted.drawable?.mutate()?.apply { setTint(accent) })
        }
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

    private const val DRAWER_ICON_DEFAULT_DP = 24

    /** The long-press of the menu button, counted from the touch: as 白い熊 地図 has it */
    private const val LONG_PRESS_MS = 200L
    private const val GRID_GROWN = "gauguin_ui_grid_grown"

    private val SCALED_TAG = "gauguin-chrome-scaled".hashCode()
}
