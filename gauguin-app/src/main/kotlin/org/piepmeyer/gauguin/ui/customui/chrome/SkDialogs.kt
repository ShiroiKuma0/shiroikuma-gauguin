package org.piepmeyer.gauguin.ui.customui.chrome

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AbsListView
import android.widget.Button
import android.widget.CheckedTextView
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import org.piepmeyer.gauguin.R
import org.piepmeyer.gauguin.ui.customui.GauguinUi
import org.piepmeyer.gauguin.ui.customui.GauguinUiConfig

/**
 * Fork: the black-yellow look of every dialog in the app.
 *
 * Upstream builds its dialogs at its own call sites, so nothing here is applied per
 * call site. The theme names [SkViewInflater] (see chrome/SkTheme); a view it creates
 * for a dialog (a floating window) is styled at once and, when it reaches its window,
 * triggers one pass over the whole dialog: the panel becomes a framed black box, every
 * button an outlined pill, and all text takes the dialog's colour roles. Rows created
 * later (lists) are styled as they are added.
 *
 * Every colour is a role set on the 白い熊 GNU Gauguin UI page, section "Dialogs"; the frame's
 * width and the corner are that section's two sliders.
 */
object SkDialogs {
    /** The dialog colour roles, read once per styled view tree */
    data class Roles(
        val ground: Int,
        val frame: Int,
        val title: Int,
        val text: Int,
        val buttonText: Int,
        val buttonFrame: Int,
        val mark: Int,
        val delete: Int,
    )

    @Suppress("UNUSED_PARAMETER")
    fun roles(context: Context): Roles =
        Roles(
            ground = SkRoles[SkRole.DIALOG_GROUND],
            frame = SkRoles[SkRole.DIALOG_FRAME],
            title = SkRoles[SkRole.DIALOG_TITLE],
            text = SkRoles[SkRole.DIALOG_TEXT],
            buttonText = SkRoles[SkRole.DIALOG_BUTTON_TEXT],
            buttonFrame = SkRoles[SkRole.DIALOG_BUTTON_FRAME],
            mark = SkRoles[SkRole.DIALOG_MARK],
            delete = SkRoles[SkRole.DIALOG_DELETE],
        )

    /** A dialog that draws its own panel (the Export / Import boxes) opts out of the window pass */
    fun keepOwnChrome(dialog: Dialog) {
        dialog.window?.decorView?.setTag(R.id.tag_sk_dialog_styled, true)
    }

    /** A view (and everything under it) that keeps the colours its owner gave it */
    fun keep(view: View) = view.setTag(R.id.tag_sk_dialog_keep, true)

    private fun isDialogContext(context: Context): Boolean {
        val tv = TypedValue()
        return context.theme.resolveAttribute(android.R.attr.windowIsFloating, tv, true) && tv.data != 0
    }

    /** Entry point of [SkViewInflater]: [view] was just created from a layout */
    fun onViewCreated(view: View): Boolean {
        if (!isDialogContext(view.context)) return false
        styleView(view, roles(view.context))
        view.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    styleWindowOnce(v.rootView)
                    // a bar button's label is only known once the dialog is built
                    if (v is Button) styleView(v, roles(v.context))
                }

                override fun onViewDetachedFromWindow(v: View) = Unit
            },
        )
        return true
    }

    private fun styleWindowOnce(root: View) {
        if (root.getTag(R.id.tag_sk_dialog_styled) != null) return
        // only a dialog's own window: an activity's is TYPE_BASE_APPLICATION
        val type = (root.layoutParams as? WindowManager.LayoutParams)?.type ?: return
        if (type != WindowManager.LayoutParams.TYPE_APPLICATION) return
        root.setTag(R.id.tag_sk_dialog_styled, true)
        val r = roles(root.context)
        root.background = panel(root.context, r)
        styleTree(root, r)
        GauguinUi.fonts(root)
    }

    /** The dialog panel: the ground, a thin frame, upstream's insets from the screen edge */
    fun panel(
        context: Context,
        r: Roles,
    ): InsetDrawable {
        val config = GauguinUiConfig(context)
        val box =
            GradientDrawable().apply {
                setColor(r.ground)
                cornerRadius = dp(context, config.dialogCornerDp.toFloat())
                // the width slider goes down to nothing: a frame must be able to go away
                setStroke(dp(context, config.dialogBorderWidthDp.toFloat()).toInt(), r.frame)
            }
        val h = dp(context, 24f).toInt()
        val v = dp(context, 16f).toInt()
        return InsetDrawable(box, h, v, h, v)
    }

    private fun styleTree(
        view: View,
        r: Roles,
    ) {
        if (view.getTag(R.id.tag_sk_dialog_keep) != null) return
        styleView(view, r)
        if (view !is ViewGroup) return
        when (view) {
            is RecyclerView ->
                view.addOnChildAttachStateChangeListener(
                    object : RecyclerView.OnChildAttachStateChangeListener {
                        override fun onChildViewAttachedToWindow(child: View) = styleTree(child, r)

                        override fun onChildViewDetachedFromWindow(child: View) = Unit
                    },
                )
            is AbsListView ->
                view.setOnHierarchyChangeListener(
                    object : ViewGroup.OnHierarchyChangeListener {
                        override fun onChildViewAdded(
                            parent: View?,
                            child: View?,
                        ) {
                            child?.let { styleTree(it, r) }
                        }

                        override fun onChildViewRemoved(
                            parent: View?,
                            child: View?,
                        ) = Unit
                    },
                )
            else -> Unit
        }
        for (i in 0 until view.childCount) styleTree(view.getChildAt(i), r)
    }

    private fun styleView(
        view: View,
        r: Roles,
    ) {
        if (view.getTag(R.id.tag_sk_dialog_keep) != null) return
        when (view) {
            is TextInputLayout -> {
                val dim = ColorStateList.valueOf(dim(r.text, r.ground))
                view.defaultHintTextColor = dim
                view.hintTextColor = ColorStateList.valueOf(r.text)
                view.setBoxStrokeColorStateList(stateful(r.frame))
                view.setHelperTextColor(dim)
                view.counterTextColor = dim
                view.setEndIconTintList(ColorStateList.valueOf(r.text))
            }
            // a switch is not a selection mark; it keeps its own look
            is SwitchCompat -> {
                view.setTextColor(r.text)
                SkWidgets.switch(view)
            }
            is CompoundButton -> {
                view.setTextColor(r.text)
                view.buttonTintList = stateful(r.mark)
            }
            is Button -> styleButton(view, r)
            is CheckedTextView -> {
                view.setTextColor(r.text)
                view.checkMarkTintList = ColorStateList.valueOf(r.mark)
            }
            is EditText -> {
                view.setTextColor(r.text)
                view.setHintTextColor(dim(r.text, r.ground))
                if (view.parent?.parent !is TextInputLayout) {
                    ViewCompat.setBackgroundTintList(view, ColorStateList.valueOf(r.frame))
                }
            }
            is TextView -> {
                view.setTextColor(if (view.id == androidx.appcompat.R.id.alertTitle) r.title else r.text)
                view.setLinkTextColor(r.buttonText)
            }
            is ProgressBar -> {
                view.indeterminateTintList = ColorStateList.valueOf(r.frame)
                view.progressTintList = ColorStateList.valueOf(r.frame)
                if (view is SeekBar) view.thumbTintList = ColorStateList.valueOf(r.frame)
            }
            else -> Unit
        }
    }

    /**
     * An outlined pill: 3 dp and bold for the dialog's main action (its positive button),
     * 1.25 dp for every other; red frame and label where the label deletes. Never filled.
     */
    private fun styleButton(
        button: Button,
        r: Roles,
    ) {
        val context = button.context
        val main = button.id == android.R.id.button1
        val deletes = isDeleteLabel(context, button.text) || (main && isDeleteDialog(button))
        val label = if (deletes) r.delete else r.buttonText
        val frame = if (deletes) r.delete else r.buttonFrame
        val strokePx = dp(context, if (main) 3f else 1.25f).toInt().coerceAtLeast(1)
        val ripple = ColorStateList.valueOf(ColorUtils.setAlphaComponent(label, 0x4D))

        button.setTextColor(stateful(label))
        button.isAllCaps = false
        // weight alone tells the main action from the others
        button.typeface = if (main) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        button.stateListAnimator = null
        if (button is MaterialButton) {
            button.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            button.strokeColor = stateful(frame)
            button.strokeWidth = strokePx
            button.cornerRadius = dp(context, 100f).toInt()
            button.rippleColor = ripple
            button.iconTint = stateful(label)
            button.elevation = 0f
        } else {
            val pill =
                GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    cornerRadius = dp(context, 100f)
                    setStroke(strokePx, stateful(frame))
                }
            button.background = RippleDrawable(ripple, pill, null)
        }
        val side = dp(context, 16f).toInt()
        if (button.paddingStart < side) {
            button.setPaddingRelative(side, button.paddingTop, side, button.paddingBottom)
        }
        // the buttons of a dialog's bar touch each other
        if (button.id == android.R.id.button1 || button.id == android.R.id.button2 || button.id == android.R.id.button3) {
            (button.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                val gap = dp(context, 4f).toInt()
                if (it.marginStart < gap) {
                    it.marginStart = gap
                    it.marginEnd = gap
                    button.layoutParams = it
                }
            }
        }
    }

    private fun isDeleteLabel(
        context: Context,
        label: CharSequence?,
    ): Boolean {
        val text = label?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return false
        return DELETE_LABELS.any { context.getString(it).equals(text, ignoreCase = true) }
    }

    /**
     * Upstream's confirmations all say "OK", so a dialog that deletes is told by its title.
     */
    private fun isDeleteDialog(button: Button): Boolean {
        val title =
            button.rootView
                .findViewById<TextView>(androidx.appcompat.R.id.alertTitle)
                ?.text
                ?.toString()
                ?.trim()
                .orEmpty()
        if (title.isEmpty()) return false
        return DELETE_TITLES.any { button.context.getString(it) == title }
    }

    private val DELETE_TITLES =
        intArrayOf(
            R.string.dialog_delete_game_title,
            R.string.dialog_delete_all_games_title,
            R.string.statistics_dialog_reset_statistics_title,
        )

    private val DELETE_LABELS =
        intArrayOf(
            R.string.load_game_screen_action_delete,
            R.string.diag_delete_forever,
            R.string.load_game_screen_delete_all_button,
            R.string.statistics_reset_statistics_button,
            R.string.gauguin_ui_reset_button,
        )

    /** [color] when enabled, at 38 % when disabled */
    private fun stateful(color: Int): ColorStateList =
        ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(ColorUtils.setAlphaComponent(color, 0x61), color),
        )

    /** A quieter form of [color] for hints: 55 % of the way from the ground to it, opaque */
    private fun dim(
        color: Int,
        ground: Int,
    ): Int = ColorUtils.blendARGB(ground, color, 0.55f) or 0xFF000000.toInt()

    private fun dp(
        context: Context,
        value: Float,
    ): Float = value * context.resources.displayMetrics.density
}
