package org.piepmeyer.gauguin.ui.customui

import androidx.preference.CheckBoxPreference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.SeekBarPreference
import org.piepmeyer.gauguin.R

/**
 * Upstream's settings tree, re-laid out in the house style.
 *
 * The fork's own UI page builds its rows from these layouts to begin with; upstream's page declares
 * its tree in XML, so this walks that tree and swaps the layout of every row it finds. Both pages
 * then read as one: a big bold yellow heading with a word-width underline over each group, tight
 * yellow rows under it, and tick boxes carrying nothing but their own yellow mark.
 *
 * The colours live in the layouts rather than in the config, exactly as on the fork's own page —
 * and [GauguinUi]'s pass steps over this list, since one global text colour and font would flatten
 * the headings' bold and the summaries' dim, and would only ever reach the rows bound at the moment
 * it runs.
 */
object GauguinPreferences {
    fun applyHouseLayouts(group: PreferenceGroup) {
        if (!GauguinUiConfig(group.context).customUiActive) return

        var firstHeading = true

        fun walk(parent: PreferenceGroup) {
            for (index in 0 until parent.preferenceCount) {
                val preference = parent.getPreference(index)

                when (preference) {
                    is PreferenceCategory -> {
                        // Only the first heading goes without the hairline that marks a group break.
                        preference.layoutResource =
                            if (firstHeading) {
                                R.layout.preference_category_gauguin_first
                            } else {
                                R.layout.preference_category_gauguin
                            }
                        firstHeading = false
                    }

                    // Its layout has to keep the androidx seekbar ids, so it takes the slider row.
                    is SeekBarPreference -> preference.layoutResource = R.layout.preference_seekbar_gauguin

                    else -> preference.layoutResource = R.layout.preference_item_gauguin
                }

                preference.isIconSpaceReserved = false

                if (preference is CheckBoxPreference) {
                    preference.widgetLayoutResource = R.layout.preference_widget_checkbox_gauguin
                }

                if (preference is PreferenceGroup) walk(preference)
            }
        }

        walk(group)
    }
}
