package org.piepmeyer.gauguin.ui.customui.chrome

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import androidx.appcompat.widget.SwitchCompat
import androidx.core.widget.CompoundButtonCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.materialswitch.MaterialSwitch
import org.piepmeyer.gauguin.R
import org.piepmeyer.gauguin.ui.customui.GauguinFonts
import org.piepmeyer.gauguin.ui.customui.GauguinUiConfig

/**
 * Fork: the look of the stock widgets every screen of the app is built from, applied by
 * [SkViewInflater] as each one is inflated — so upstream's
 * layouts and call sites stay untouched.
 */
object SkWidgets {
    /**
     * Every button is an outlined pill. A button upstream drew filled is the main action of its
     * block (3 dp, bold); a label that deletes is red; a button that already carries a colour
     * of its own (an ease, a flag) lends it to frame and label. A button showing only an icon
     * stays a glyph.
     */
    fun button(
        button: Button,
        wasFilled: Boolean,
    ) {
        if (button.getTag(R.id.tag_sk_dialog_keep) != null) return
        if (button is MaterialButton && isKey(button)) {
            key(button)
            return
        }
        if (button is ExtendedFloatingActionButton) {
            button.backgroundTintList = ColorStateList.valueOf(SkRoles[SkRole.FAB_GROUND])
            button.strokeColor = ColorStateList.valueOf(SkRoles[SkRole.FAB_FRAME])
            button.strokeWidth = Pills.dp(button.context, Pills.MAIN_DP)
            button.setTextColor(SkRoles[SkRole.FAB_ICON])
            button.iconTint = ColorStateList.valueOf(SkRoles[SkRole.FAB_ICON])
            button.elevation = 0f
            return
        }
        if (button is MaterialButton && button.isCheckable) {
            segment(button)
            return
        }
        if (button is MaterialButton && button.text.isNullOrEmpty() && button.icon != null) {
            button.iconTint = ColorStateList.valueOf(SkRoles[SkRole.ICON])
            return
        }
        val context = button.context
        val current = button.currentTextColor
        val ink =
            when {
                isDeleteLabel(button) -> SkRoles[SkRole.DELETE]
                current in THEME_INKS -> SkRoles[SkRole.PILL_TEXT]
                else -> current or 0xFF000000.toInt()
            }
        val frame = if (ink == SkRoles[SkRole.PILL_TEXT]) SkRoles[SkRole.PILL_FRAME] else ink
        if (button is MaterialButton) {
            button.backgroundTintList = ColorStateList.valueOf(SkRoles[SkRole.PILL_GROUND])
            button.strokeColor = stateful(frame)
            button.strokeWidth = maxOf(1, Pills.dp(context, if (wasFilled) Pills.MAIN_DP else Pills.REGULAR_DP))
            button.cornerRadius = Pills.dp(context, 100f)
            button.rippleColor = ColorStateList.valueOf(SkRoles.ripple(ink))
            button.setTextColor(stateful(ink))
            button.iconTint = stateful(ink)
            button.typeface = if (wasFilled) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            button.isAllCaps = false
            button.stateListAnimator = null
            button.elevation = 0f
        } else {
            val padding = intArrayOf(button.paddingLeft, button.paddingTop, button.paddingRight, button.paddingBottom)
            button.background = Pills.background(context, frame, wasFilled)
            button.setTextColor(stateful(ink))
            button.typeface = if (wasFilled) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            button.isAllCaps = false
            button.stateListAnimator = null
            val side = maxOf(padding[0], Pills.dp(context, 14f))
            button.setPadding(side, padding[1], side, padding[3])
        }
    }

    private fun isKey(button: View): Boolean {
        var parent = button.parent
        while (parent is View) {
            if (parent.id == R.id.keypadFrame) return true
            parent = parent.parent
        }
        return false
    }

    /**
     * A key of the number pad: never filled, like every button, but its frame, corner, label and
     * font are the Keypad section's own (白い熊 sets the keys apart from the action pills).
     */
    private fun key(button: MaterialButton) {
        val context = button.context
        val config = GauguinUiConfig(context)
        val ink = SkRoles[SkRole.KEY_TEXT]
        button.backgroundTintList = ColorStateList.valueOf(SkRoles[SkRole.KEY_BACKGROUND])
        button.strokeColor = stateful(SkRoles[SkRole.KEY_BORDER])
        button.strokeWidth = Pills.dp(context, config.int(GauguinUiConfig.KEY_BORDER_WIDTH).toFloat())
        button.cornerRadius = Pills.dp(context, config.int(GauguinUiConfig.KEY_CORNER).toFloat())
        button.rippleColor = ColorStateList.valueOf(SkRoles.ripple(ink))
        button.setTextColor(stateful(ink))
        button.iconTint = stateful(ink)
        button.typeface =
            GauguinFonts.typeface(
                context,
                config.string(GauguinUiConfig.KEY_FONT).ifEmpty { config.fontFamily },
                config.int(GauguinUiConfig.KEY_WEIGHT),
                config.fontItalic,
            )
        val scale = config.int(GauguinUiConfig.KEY_TEXT_SIZE)
        if (scale != 100 && button.getTag(R.id.tag_sk_font) == null) {
            button.setTag(R.id.tag_sk_font, true)
            button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, button.textSize * scale / 100f)
        }
        button.stateListAnimator = null
        button.elevation = 0f
    }

    /** Whether upstream's style drew [button] filled: that is how its main actions are marked */
    fun isFilled(button: Button): Boolean {
        val material = button as? MaterialButton ?: return false
        val fill = material.backgroundTintList?.defaultColor ?: return false
        return Color.alpha(fill) > 0x80 && material.strokeWidth == 0
    }

    private fun isDeleteLabel(button: Button): Boolean {
        val text =
            button.text
                ?.toString()
                ?.trim()
                .orEmpty()
        if (text.isEmpty()) return false
        return DELETE_LABELS.any { button.context.getString(it).equals(text, ignoreCase = true) }
    }

    private val DELETE_LABELS = intArrayOf(
            R.string.load_game_screen_action_delete,
            R.string.load_game_screen_delete_all_button,
            R.string.statistics_reset_statistics_button,
            R.string.gauguin_ui_reset_button,
        )

    // text colours a button takes from the theme, as opposed to one given to it for its meaning
    private val THEME_INKS: Set<Int>
        get() =
            setOf(
                SkRoles[SkRole.ACCENT],
                SkRoles[SkRole.ON_ACCENT],
                SkRoles[SkRole.TEXT],
                SkRoles[SkRole.TEXT_SECONDARY],
                SkRoles[SkRole.BACKGROUND],
                SkRoles[SkRole.PILL_TEXT],
                SkRoles[SkRole.LIST_SELECTED_TEXT],
                Color.WHITE,
                Color.BLACK,
            )

    /**
     * A floating button: an outlined ring with its icon, the ground showing through. The ring is
     * built from the button's own shape, so it follows a rounded square instead of boxing it in a
     * circle.
     */
    fun fab(fab: FloatingActionButton) {
        val ground = ColorStateList.valueOf(SkRoles[SkRole.FAB_GROUND])
        fab.backgroundTintList = ground
        // the button recolours only its own shape; a tint set by the layout sits on the background
        fab.background?.setTintList(ground)
        fab.imageTintList = ColorStateList.valueOf(SkRoles[SkRole.FAB_ICON])
        fab.rippleColor = SkRoles.ripple(SkRoles[SkRole.FAB_ICON])
        fab.compatElevation = 0f
        fab.foreground =
            MaterialShapeDrawable(fab.shapeAppearanceModel).apply {
                fillColor = ColorStateList.valueOf(Color.TRANSPARENT)
                setStroke(Pills.dp(fab.context, Pills.MAIN_DP).toFloat(), SkRoles[SkRole.FAB_FRAME])
            }
    }

    /**
     * A segment of a selector (a button that can be checked): a selection control, not an action
     * pill. It keeps the shape its group gives it; the chosen one is told by its fill.
     */
    private fun segment(button: MaterialButton) {
        val ink = SkRoles[SkRole.PILL_TEXT]
        button.backgroundTintList =
            ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(SkRoles[SkRole.SEGMENT_SELECTED], Color.TRANSPARENT),
            )
        button.strokeColor = stateful(SkRoles[SkRole.PILL_FRAME])
        button.strokeWidth = maxOf(1, Pills.dp(button.context, Pills.REGULAR_DP))
        button.rippleColor = ColorStateList.valueOf(SkRoles.ripple(ink))
        button.setTextColor(stateful(ink))
        button.iconTint = stateful(ink)
        button.isAllCaps = false
        button.elevation = 0f
    }

    /**
     * A card: black on black needs its frame to be seen at all. The frame's width and the corner
     * are the Foundation section's two sliders; the top panel has colours of its own and is
     * painted by GauguinChrome.
     */
    fun card(card: MaterialCardView) {
        if (card.id == R.id.mainTopArea) return
        val config = GauguinUiConfig(card.context)
        card.setCardBackgroundColor(SkRoles[SkRole.CARD_GROUND])
        card.strokeColor = SkRoles[SkRole.OUTLINE]
        card.strokeWidth = Pills.dp(card.context, config.borderWidthDp.toFloat())
        card.radius = Pills.dp(card.context, config.cornerRadiusDp.toFloat()).toFloat()
        card.cardElevation = 0f
    }

    /**
     * The outlined switch: a 2 dp fully round outline with the ground showing through and a
     * solid dot inside it, right when on and left when off. Never filled; the two states differ
     * in colour only. The widget keeps its class, since view bindings name it.
     */
    fun switch(v: SwitchCompat) {
        val d = v.resources.displayMetrics.density
        val colors =
            ColorStateList(
                arrayOf(
                    intArrayOf(-android.R.attr.state_enabled, android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_enabled),
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(),
                ),
                intArrayOf(
                    SkRoles.disabled(SkRoles[SkRole.SWITCH_ON]),
                    SkRoles.disabled(SkRoles[SkRole.SWITCH_OFF]),
                    SkRoles[SkRole.SWITCH_ON],
                    SkRoles[SkRole.SWITCH_OFF],
                ),
            )
        val h = (28 * d).toInt()
        val track =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = h / 2f
                setColor(Color.TRANSPARENT)
                setStroke((2 * d).toInt().coerceAtLeast(1), Color.WHITE)
                setSize((52 * d).toInt(), h)
            }
        val dotSize = (h * 0.46f).toInt()
        val inset = (h - dotSize) / 2
        val dot =
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
                setSize(dotSize, dotSize)
            }
        if (v is MaterialSwitch) {
            v.thumbIconDrawable = null
            v.trackDecorationDrawable = null
        }
        // A LayerDrawable with layer insets, never an InsetDrawable: SwitchCompat takes an
        // InsetDrawable's insets for the thumb's optical insets and shrinks the track by them
        // (seen in 白い熊 考直, 2026-11-22).
        val boxW = (26 * d).toInt()
        val insetH = ((boxW - dotSize) / 2).coerceAtLeast(0)
        v.thumbDrawable = LayerDrawable(arrayOf(dot)).apply { setLayerInset(0, insetH, inset, insetH, inset) }
        v.trackDrawable = track
        v.thumbTintList = colors
        v.trackTintList = colors
        v.switchMinWidth = (52 * d).toInt()
        v.showText = false
        v.splitTrack = false
        v.minHeight = 0
        v.minimumHeight = 0
    }

    /** A check box or a radio button: a selection mark, tinted from its two roles */
    fun mark(v: CompoundButton) {
        if (v is SwitchCompat) return
        CompoundButtonCompat.setButtonTintList(
            v,
            ColorStateList(
                arrayOf(
                    intArrayOf(-android.R.attr.state_enabled),
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(),
                ),
                intArrayOf(SkRoles.disabled(SkRoles[SkRole.MARK_OFF]), SkRoles[SkRole.MARK_ON], SkRoles[SkRole.MARK_OFF]),
            ),
        )
    }

    /**
     * The message bar at the bottom of a screen: its ground and text are roles through the
     * theme, but a black bar on a black screen needs its frame.
     */
    fun snackbar(text: View) {
        val bar = text.parent?.parent as? View ?: return
        if (bar.getTag(R.id.tag_sk_dialog_styled) != null) return
        bar.setTag(R.id.tag_sk_dialog_styled, true)
        bar.background =
            GradientDrawable().apply {
                setColor(SkRoles[SkRole.SNACK_GROUND])
                cornerRadius = Pills.dp(bar.context, 12f).toFloat()
                setStroke(maxOf(1, Pills.dp(bar.context, Pills.REGULAR_DP)), SkRoles[SkRole.SNACK_FRAME])
            }
        bar.backgroundTintList = null
        (text as? android.widget.TextView)?.setTextColor(SkRoles[SkRole.SNACK_TEXT])
    }

    fun stateful(color: Int): ColorStateList =
        ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(SkRoles.disabled(color), color),
        )

    /** Runs [block] once [view] is in its window: a button's label and id are only final then */
    fun whenAttached(
        view: View,
        block: () -> Unit,
    ) {
        if (view.isAttachedToWindow) {
            block()
            return
        }
        view.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    v.removeOnAttachStateChangeListener(this)
                    block()
                }

                override fun onViewDetachedFromWindow(v: View) = Unit
            },
        )
    }
}
