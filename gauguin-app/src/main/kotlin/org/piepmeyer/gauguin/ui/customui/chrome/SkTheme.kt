package org.piepmeyer.gauguin.ui.customui.chrome

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import org.piepmeyer.gauguin.R
import org.piepmeyer.gauguin.ui.customui.GauguinUiConfig
import java.util.WeakHashMap

/**
 * Fork: puts the black-yellow theme on a context. Called for every activity right after upstream's
 * theme is set (`ActivityUtils.configureTheme`), while the "Use 白い熊 GNU Gauguin UI" switch is on;
 * the UI page itself always wears it.
 *  - the stored role colours are laid over the role colour resources ([SkPalette]);
 *  - `ThemeOverlay.Shiroikuma` points every colour attribute at a role and names the fork's view
 *    inflater, which draws buttons, switches, marks and dialogs.
 *
 * An activity built from older colours recreates itself when it is next resumed.
 */
object SkTheme {
    private val builtWith = WeakHashMap<Activity, Int>()

    @Volatile
    private var watching = false

    /** Whether the house look is switched on */
    fun active(context: Context): Boolean = GauguinUiConfig(context).customUiActive

    /** Whether the overlay is on [context]: our own views draw the stock look when it is not */
    fun isThemed(context: Context): Boolean {
        val value = TypedValue()
        return context.theme.resolveAttribute(R.attr.skThemed, value, true) && value.data != 0
    }

    fun install(
        context: Context,
        always: Boolean = false,
    ) {
        SkRoles.attach(context)
        if (context is Activity) {
            synchronized(builtWith) { builtWith[context] = stamp(context) }
            watch(context.application)
        }
        if (!always && !active(context)) return
        SkPalette.apply(context)
        context.theme.applyStyle(R.style.ThemeOverlay_Shiroikuma, true)
    }

    // the switch itself is part of what an activity was built with
    private fun stamp(context: Context): Int = if (active(context)) SkRoles.generation else -SkRoles.generation

    private fun watch(application: Application) {
        if (watching) return
        watching = true
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    val built = synchronized(builtWith) { builtWith[activity] } ?: return
                    if (built != stamp(activity)) activity.recreate()
                }

                override fun onActivityCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?,
                ) = Unit

                override fun onActivityStarted(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivityStopped(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(
                    activity: Activity,
                    outState: Bundle,
                ) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }

    /** The activity showing a change itself: it already redrew, so it is up to date */
    fun markCurrent(activity: Activity) {
        synchronized(builtWith) { builtWith[activity] = stamp(activity) }
    }
}
