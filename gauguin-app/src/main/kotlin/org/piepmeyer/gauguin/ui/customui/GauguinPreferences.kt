package org.piepmeyer.gauguin.ui.customui

import androidx.preference.CheckBoxPreference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.SeekBarPreference
import org.piepmeyer.gauguin.R
import org.piepmeyer.gauguin.ui.customui.chrome.SkPreferences

/**
 * Upstream's settings tree, re-laid out in the house style.
 *
 * The fork's own UI page builds its rows from these layouts to begin with; upstream's page declares
 * its tree in XML, so this walks that tree and swaps the layout of every row it finds. Both pages
 * then read as one: a bold heading with a word-width underline over each group, tight rows under
 * it, each with an icon coloured by what the row is about.
 *
 * Upstream's tick boxes are on/off settings, not entries chosen from a list, so each wears the
 * outlined switch. The layouts carry no colour: `SkPreferences.styleRow` paints every row from the
 * roles as it is attached (see `SettingsActivity`).
 */
object GauguinPreferences {
    /** Upstream stops the cell size at 96 dp, which leaves a large unfolded screen half empty. */
    const val MAXIMUM_CELL_SIZE_DP = 240

    fun applyHouseLayouts(group: PreferenceGroup) {
        group.findPreference<SeekBarPreference>("maximumCellSize")?.max = MAXIMUM_CELL_SIZE_DP

        if (!GauguinUiConfig(group.context).customUiActive) return

        fun walk(parent: PreferenceGroup) {
            for (index in 0 until parent.preferenceCount) {
                val preference = parent.getPreference(index)

                preference.layoutResource =
                    when (preference) {
                        is PreferenceCategory -> R.layout.sk_preference_section
                        // Its layout has to keep the androidx seekbar ids, so it takes the slider row.
                        is SeekBarPreference -> R.layout.sk_preference_seekbar
                        else -> R.layout.sk_preference_row
                    }
                preference.isIconSpaceReserved = false

                if (preference is CheckBoxPreference) {
                    preference.widgetLayoutResource = R.layout.sk_preference_widget_switch_check
                }

                if (preference is PreferenceGroup) walk(preference)
            }
        }

        walk(group)

        // the link to the fork's UI page: the main action of its group, and it opens something
        group.findPreference<androidx.preference.Preference>("shiroikumaUi")?.extras?.apply {
            putBoolean(SkPreferences.EXTRA_MAIN, true)
            putBoolean(SkPreferences.EXTRA_OPENS, true)
        }
        SkPreferences.decorate(group)
    }
}
