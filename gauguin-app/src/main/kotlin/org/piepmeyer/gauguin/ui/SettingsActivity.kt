package org.piepmeyer.gauguin.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import org.koin.android.ext.android.inject
import org.piepmeyer.gauguin.R
import org.piepmeyer.gauguin.ui.customui.GauguinPreferences
import org.piepmeyer.gauguin.ui.customui.GauguinUiActivity
import org.piepmeyer.gauguin.ui.customui.GauguinUiConfig

class SettingsActivity : AppCompatActivity() {
    private val activityUtils: ActivityUtils by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        activityUtils.configureTheme(this)
        setContentView(R.layout.activity_settings)
        activityUtils.configureMainContainerBackground(findViewById(R.id.rootSettings))
        activityUtils.configureRootView(findViewById(R.id.rootSettings))

        if (savedInstanceState == null) {
            val settings = SettingsFragment()

            supportFragmentManager.commit {
                replace(R.id.settings, settings)
            }
        }
        val actionBar = supportActionBar

        activityUtils.configureFullscreen(this)

        actionBar?.setDisplayHomeAsUpEnabled(true)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settings)) { v, insets ->
            val innerPadding =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout(),
                )
            v.setPadding(
                innerPadding.left,
                0,
                innerPadding.right,
                innerPadding.bottom,
            )

            WindowInsetsCompat.CONSUMED
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(
            savedInstanceState: Bundle?,
            rootKey: String?,
        ) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            // 白い熊 fork: open the house UI page. Wired here rather than via an <intent> in the XML
            // because applicationId (shiroikuma.gauguin) differs from the namespace, so the target
            // package cannot be written literally in the resource without hard-coding it.
            findPreference<Preference>("shiroikumaUi")?.setOnPreferenceClickListener {
                startActivity(Intent(requireContext(), GauguinUiActivity::class.java))
                true
            }

            // 白い熊 fork: upstream's tree, in the house rows — the same layouts the fork's own UI
            // page is built from.
            GauguinPreferences.applyHouseLayouts(preferenceScreen)
        }

        override fun onViewCreated(
            view: View,
            savedInstanceState: Bundle?,
        ) {
            super.onViewCreated(view, savedInstanceState)

            // The headings draw their own hairline; the list's dividers would double it.
            if (GauguinUiConfig(requireContext()).customUiActive) {
                setDivider(null)
                setDividerHeight(0)
            }
        }
    }
}
