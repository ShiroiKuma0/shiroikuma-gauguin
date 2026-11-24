package org.piepmeyer.gauguin.ui.customui

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import org.xmlpull.v1.XmlPullParser
import java.util.Locale

/**
 * The app's language, chosen in the UI page's first section (black-yellow-theme skill, "App
 * language").
 *
 * The choice is applied through AppCompat's per-app locales and **also kept in the fork's own
 * preferences file** ([KEY], a BCP-47 tag, empty for "System default"), then asserted again when
 * the process starts: EMUI does not keep the system's per-app locale across a sideload update,
 * and below Android 13 AppCompat keeps nothing by itself. Stored there, it travels in the export.
 *
 * The picker's list is read from the locale configuration the build generates from the
 * `values-*` directories, so picker and declaration cannot drift apart.
 */
object GauguinLanguage {
    const val KEY = "app_language"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(GauguinUiConfig.PREFS, Context.MODE_PRIVATE)

    /** The stored tag; empty means the system's language */
    fun tag(context: Context): String = prefs(context).getString(KEY, "").orEmpty()

    fun set(
        context: Context,
        tag: String,
    ) {
        prefs(context).edit { putString(KEY, tag) }
        apply(tag)
    }

    /** At process start: put the stored choice back, before any activity is made */
    fun reassert(context: Context) {
        val tag = tag(context)
        if (tag.isEmpty()) return
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != tag) apply(tag)
    }

    private fun apply(tag: String) {
        AppCompatDelegate.setApplicationLocales(
            if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag),
        )
    }

    /**
     * A context whose strings are in the chosen language. Below Android 13 the choice reaches
     * activity contexts only, so every string built off the application context or leaving the
     * app — a notification, a receiver's answer — is resolved through this.
     */
    fun localized(context: Context): Context {
        val tag = tag(context)
        if (tag.isEmpty()) return context
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(tag))
        return context.createConfigurationContext(configuration)
    }

    /** Every language the app ships, as the build declared them */
    fun shippedTags(context: Context): List<String> {
        val resources = context.resources
        var id = 0
        for (name in listOf("_generated_res_locale_config", "locales_config")) {
            id = resources.getIdentifier(name, "xml", context.packageName)
            if (id == 0) id = resources.getIdentifier(name, "xml", "org.piepmeyer.gauguin")
            if (id != 0) break
        }
        if (id == 0) return emptyList()
        val tags = mutableListOf<String>()
        runCatching {
            val parser = resources.getXml(id)
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "locale") {
                    parser.getAttributeValue("http://schemas.android.com/apk/res/android", "name")?.let(tags::add)
                }
            }
            parser.close()
        }
        return tags.distinct()
    }

    /** A language written in its own language, with its tag: "日本語 (ja)" */
    fun displayName(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        val name = locale.getDisplayName(locale).replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
        return "$name ($tag)"
    }
}
