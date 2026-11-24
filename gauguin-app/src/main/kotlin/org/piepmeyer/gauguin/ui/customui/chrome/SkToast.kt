package org.piepmeyer.gauguin.ui.customui.chrome

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast

/**
 * Fork: the short message of the fork's own screens — the ground showing through a thin frame, in
 * the message roles (section "Toasts and hints"). The platform draws a plain text toast itself and
 * takes no colour from an app, so the fork shows its own view; that works while the app is in the
 * foreground, which is the only time the fork raises one.
 */
object SkToast {
    fun show(
        context: Context,
        message: CharSequence,
        long: Boolean = false,
    ) {
        val text =
            TextView(context).apply {
                this.text = message
                setTextColor(SkRoles[SkRole.SNACK_TEXT])
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                gravity = Gravity.CENTER
                setPadding(Pills.dp(context, 18f), Pills.dp(context, 10f), Pills.dp(context, 18f), Pills.dp(context, 10f))
                background =
                    GradientDrawable().apply {
                        setColor(SkRoles[SkRole.SNACK_GROUND])
                        cornerRadius = Pills.dp(context, 100f).toFloat()
                        setStroke(maxOf(1, Pills.dp(context, Pills.REGULAR_DP)), SkRoles[SkRole.SNACK_FRAME])
                    }
            }
        @Suppress("DEPRECATION")
        Toast(context.applicationContext)
            .apply {
                duration = if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                view = text
            }.show()
    }
}
