package org.piepmeyer.gauguin.ui.customui.chrome

import android.content.Context
import android.content.res.Resources
import android.content.res.loader.ResourcesLoader
import android.os.Build
import androidx.annotation.RequiresApi
import com.google.android.material.color.SkColorLoaderBridge
import io.github.oshai.kotlinlogging.KotlinLogging
import org.piepmeyer.gauguin.R
import java.util.WeakHashMap

private val logger = KotlinLogging.logger {}

/**
 * Fork: how a colour role reaches the XML this fork does not draw itself (the "mixed way",
 * decided for 白い熊 GNU Gauguin by 白い熊 on 2026-11-23).
 *
 * The fork is rebased onto every upstream release, so upstream's layouts and themes are not edited
 * for colour. `ThemeOverlay.Shiroikuma` (sk_theme.xml) points every colour attribute they read at a
 * role colour resource, `@color/sk_role_…`, and this object lays the stored colours over those
 * resources with a ResourcesLoader (Android 11 and later), together with the few colour names
 * upstream's layouts use directly. Below Android 11 the values in sk_roles.xml show: each role's
 * default.
 *
 * [apply] must run on a context before anything is inflated from it; SkTheme.install does that.
 */
object SkPalette {
    private class Applied(
        val loader: ResourcesLoader,
        val generation: Int,
    )

    private val applied = WeakHashMap<Resources, Applied>()

    @Synchronized
    fun apply(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        // Robolectric's asset manager cannot apply a style once a loader is attached (upstream's
        // activity tests run there); the role resources then show their defaults, as they do
        // below Android 11.
        if (Build.FINGERPRINT == "robolectric") return
        applyTo(context)
        val application = context.applicationContext
        if (application != null && application !== context) applyTo(application)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun applyTo(context: Context) {
        val resources = context.resources
        val generation = SkRoles.generation
        val current = applied[resources]
        if (current != null && current.generation == generation) return
        try {
            val loader = SkColorLoaderBridge.create(context, resourceColors()) ?: return
            if (current != null) resources.removeLoaders(current.loader)
            resources.addLoaders(loader)
            applied[resources] = Applied(loader, generation)
        } catch (e: Throwable) {
            logger.warn(e) { "Could not lay the colour roles over the resources" }
        }
    }

    private fun resourceColors(): Map<Int, Int> {
        val colors = HashMap<Int, Int>()
        for (role in SkRole.entries) colors[role.colorRes] = SkRoles[role]
        // Colour names upstream's layouts use directly; none of them has a night-qualified copy,
        // which would beat the run-time value.
        for ((res, role) in UPSTREAM_NAMES) colors[res] = SkRoles[role]
        return colors
    }

    private val UPSTREAM_NAMES =
        listOf(
            R.color.md_theme_light_primary to SkRole.ACCENT,
            R.color.md_theme_dark_primary to SkRole.ACCENT,
            R.color.md_theme_dark_secondary to SkRole.ACCENT,
            R.color.md_theme_dark_primaryFixed to SkRole.SEGMENT_SELECTED,
            R.color.md_theme_dark_onSecondaryFixed to SkRole.LIST_SELECTED_TEXT,
            R.color.md_theme_light_onSecondaryFixedVariant to SkRole.LIST_SELECTED_TEXT,
        )
}
