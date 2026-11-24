package org.piepmeyer.gauguin.ui.customui

import android.content.Context
import androidx.appcompat.app.AlertDialog

/**
 * The two dialogs the fork raises by itself.
 *
 * Their look is not set here: like every dialog of the app they are drawn by `chrome/SkDialogs` as
 * they are built — the framed panel in the dialog roles, the positive button as the main pill, every
 * other button a regular pill — because the theme names the fork's view inflater.
 */
object GauguinDialogs {
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
        AlertDialog
            .Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(buttonText) { _, _ -> fire() }
            .setOnDismissListener { fire() }
            .show()
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
        AlertDialog
            .Builder(context)
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
            }.show()
    }
}
