package org.piepmeyer.gauguin.ui.customui

import android.app.Dialog
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.piepmeyer.gauguin.R

/**
 * Black-yellow dialogs: every dialog the fork raises gets the house look — black fill, an accent
 * border of the configured width, the configured corner radius, and accent text throughout.
 *
 * The border has to be applied once the window exists, so [style] hangs itself on the dialog's
 * show listener rather than running at build time.
 */
object GauguinDialogs {
    /** Give [dialog] the house border/background, now and again after it lays out. */
    fun style(dialog: Dialog) {
        dialog.setOnShowListener {
            applyBorder(dialog)
            recolour(dialog.window?.decorView, GauguinUiConfig(dialog.context))
            dialog.window?.decorView?.post { applyBorder(dialog) }
        }
    }

    /**
     * The house info dialog: title, body, and a single acknowledging button.
     *
     * [onAcknowledge] runs when the button is pressed **or** the dialog is dismissed any other way,
     * exactly once — that is what lets a successful export close the panel and the settings page
     * behind it without the caller having to wire up two separate paths.
     */
    fun info(
        context: Context,
        title: CharSequence,
        message: CharSequence,
        buttonText: CharSequence = context.getString(android.R.string.ok),
        onAcknowledge: (() -> Unit)? = null,
    ) {
        var fired = false
        val fire = {
            if (!fired) {
                fired = true
                onAcknowledge?.invoke()
            }
        }
        val dialog =
            AlertDialog
                .Builder(context, R.style.Theme_Gauguin_ShiroikumaDialog)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(buttonText) { _, _ -> fire() }
                .setOnDismissListener { fire() }
                .create()
        style(dialog)
        dialog.show()
    }

    /**
     * A two-button house dialog — used by the import result, where the positive button restarts the
     * app and the negative one ("Later") just closes the chain.
     */
    fun choice(
        context: Context,
        title: CharSequence,
        message: CharSequence,
        positiveText: CharSequence,
        negativeText: CharSequence,
        onPositive: () -> Unit,
        onNegative: () -> Unit,
    ) {
        var fired = false
        val dialog =
            AlertDialog
                .Builder(context, R.style.Theme_Gauguin_ShiroikumaDialog)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(positiveText) { _, _ ->
                    fired = true
                    onPositive()
                }.setNegativeButton(negativeText) { _, _ ->
                    fired = true
                    onNegative()
                }.setOnDismissListener {
                    // Dismissed by back / outside tap: treat it as the soft option, so the chain
                    // still unwinds instead of leaving the panel stranded behind a gone dialog.
                    if (!fired) onNegative()
                }.create()
        style(dialog)
        dialog.show()
    }

    private fun applyBorder(dialog: Dialog) {
        val window = dialog.window ?: return
        val config = GauguinUiConfig(dialog.context)
        val density = dialog.context.resources.displayMetrics.density
        val inset = (16 * density).toInt()
        val background =
            GradientDrawable().apply {
                cornerRadius = config.dialogCornerDp * density
                setColor(config.dialogBackgroundColor)
                setStroke((config.dialogBorderWidthDp * density).toInt(), config.dialogBorderColor)
            }
        window.setBackgroundDrawable(InsetDrawable(background, inset, inset, inset, inset))
    }

    /** Paint every text view and button in the dialog tree with the configured colours and font. */
    private fun recolour(
        view: View?,
        config: GauguinUiConfig,
    ) {
        if (view == null) return
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) recolour(view.getChildAt(index), config)
            return
        }
        val typeface: Typeface =
            GauguinFonts.typeface(view.context, config.fontFamily, config.fontWeight, config.fontItalic)
        when (view) {
            is Button -> {
                view.setTextColor(config.accentColor)
                view.typeface = typeface
                view.isAllCaps = false
            }

            is TextView -> {
                view.setTextColor(config.int(GauguinUiConfig.DIALOG_TEXT_COLOR))
                view.typeface = typeface
                view.setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    view.textSize * config.fontScalePct / 100f,
                )
            }
        }
    }
}
