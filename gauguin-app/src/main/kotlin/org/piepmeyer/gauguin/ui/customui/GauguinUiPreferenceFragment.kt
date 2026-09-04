package org.piepmeyer.gauguin.ui.customui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import org.piepmeyer.gauguin.R

/**
 * The 白い熊 GNU Gauguin UI page.
 *
 * The whole screen is generated from [GauguinUiConfig.SPECS], so the knob list is declared once and
 * the page, the backup and "reset to defaults" all follow it. Visual grammar is the kxkb UI page:
 * a big bold heading with a word-width underline over each top-level group, a thin full-width
 * hairline separating groups, a sub-heading one indent in, and rows indented again under it —
 * with tight vertical padding throughout, so the indentation alone tells you where you are.
 *
 * Export / Import is the first section, per 白い熊's layout: the backup folder, the panel entry, and
 * the automation contract's two rows.
 */
class GauguinUiPreferenceFragment : PreferenceFragmentCompat() {
    private lateinit var config: GauguinUiConfig

    private var dirPreference: Preference? = null
    private var tokenPreference: Preference? = null
    private var regeneratePreference: Preference? = null

    /** Where a freshly imported font lands — set just before the picker launches. */
    private var onFontImported: (String) -> Unit = {}

    private val importFontLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri ?: return@registerForActivityResult
            val name = GauguinFonts.importFont(requireContext(), uri)
            if (name != null) {
                onFontImported(name)
                toast(getString(R.string.gauguin_ui_font_imported, name))
            } else {
                toast(getString(R.string.gauguin_ui_font_import_failed))
            }
        }

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        config = GauguinUiConfig(requireContext())
        val context = preferenceManager.context
        val screen: PreferenceScreen = preferenceManager.createPreferenceScreen(context)

        buildExportSection(screen)
        UiCategory.entries.forEach { category -> buildCategory(screen, category) }

        preferenceScreen = screen
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        listView.setBackgroundColor(config.backgroundColor)
        // The house page is a tight list: no dividers, no inter-row padding beyond the row's own.
        setDivider(null)
        setDividerHeight(0)
        listView.clipToPadding = false
    }

    override fun onResume() {
        super.onResume()
        refreshDirectoryRow()
        refreshTokenRow()
        preview()?.refresh()
    }

    // ---- sections -------------------------------------------------------------------------------

    private fun buildExportSection(screen: PreferenceScreen) {
        val category = heading(R.string.gauguin_ui_cat_export, first = true)
        screen.addPreference(category)

        dirPreference =
            Preference(preferenceManager.context).apply {
                layoutResource = R.layout.preference_item_gauguin
                isPersistent = false
                title = getString(R.string.gauguin_ui_export_dir_row)
                isSingleLineTitle = false
                setOnPreferenceClickListener {
                    openExportPanel()
                    true
                }
            }
        category.addPreference(dirPreference!!)

        category.addPreference(
            Preference(preferenceManager.context).apply {
                layoutResource = R.layout.preference_item_gauguin
                isPersistent = false
                title = getString(R.string.gauguin_ui_export_entry)
                summary = getString(R.string.gauguin_ui_export_entry_summary)
                setOnPreferenceClickListener {
                    openExportPanel()
                    true
                }
            },
        )

        // The 保存復元 automation contract's rows live here — inside Export/Import, never as a
        // section of their own, so every sister app puts them in the same place. Contract v2 makes
        // it three: the master switch (ON), the token requirement (OFF), and the token itself,
        // which is shown only when it is actually being asked for. A 48-character secret sitting
        // under an off switch only invites 白い熊 to paste it somewhere it will do nothing.
        category.addPreference(
            SwitchPreferenceCompat(preferenceManager.context).apply {
                layoutResource = R.layout.preference_item_gauguin
                isPersistent = false
                title = getString(R.string.gauguin_ui_automation_switch)
                summary = getString(R.string.gauguin_ui_automation_switch_summary)
                isChecked = AutomationAuth.enabled(requireContext())
                setOnPreferenceChangeListener { _, newValue ->
                    AutomationAuth.setEnabled(requireContext(), newValue as Boolean)
                    true
                }
            },
        )

        category.addPreference(
            SwitchPreferenceCompat(preferenceManager.context).apply {
                layoutResource = R.layout.preference_item_gauguin
                isPersistent = false
                title = getString(R.string.gauguin_ui_automation_require_token)
                summary = getString(R.string.gauguin_ui_automation_require_token_summary)
                isSingleLineTitle = false
                isChecked = AutomationAuth.requireToken(requireContext())
                setOnPreferenceChangeListener { _, newValue ->
                    AutomationAuth.setRequireToken(requireContext(), newValue as Boolean)
                    refreshTokenRow()
                    true
                }
            },
        )

        tokenPreference =
            Preference(preferenceManager.context).apply {
                layoutResource = R.layout.preference_item_gauguin
                isPersistent = false
                title = getString(R.string.gauguin_ui_automation_token)
                setOnPreferenceClickListener {
                    val token = AutomationAuth.token(requireContext())
                    val clipboard =
                        requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("token", token))
                    toast(getString(R.string.gauguin_ui_automation_token_copied))
                    true
                }
            }
        category.addPreference(tokenPreference!!)

        regeneratePreference =
            Preference(preferenceManager.context).apply {
                layoutResource = R.layout.preference_item_gauguin_l2
                isPersistent = false
                title = getString(R.string.gauguin_ui_automation_regenerate)
                setOnPreferenceClickListener {
                    AutomationAuth.regenerate(requireContext())
                    refreshTokenRow()
                    GauguinDialogs.info(
                        requireContext(),
                        getString(R.string.gauguin_ui_automation_token),
                        getString(R.string.gauguin_ui_automation_regenerate_warning),
                    )
                    true
                }
            }
        category.addPreference(regeneratePreference!!)

        refreshTokenRow()
    }

    private fun buildCategory(
        screen: PreferenceScreen,
        category: UiCategory,
    ) {
        val group = heading(category.headingRes, first = false)
        screen.addPreference(group)
        group.addPreference(subHeading(category.subHeadingRes))

        GauguinUiConfig.SPECS.filter { it.category == category }.forEach { spec ->
            group.addPreference(rowFor(spec))
        }

        group.addPreference(
            Preference(preferenceManager.context).apply {
                layoutResource = R.layout.preference_item_gauguin_l2
                isPersistent = false
                title = getString(R.string.gauguin_ui_reset)
                setOnPreferenceClickListener {
                    config.resetToDefaults(category)
                    toast(getString(R.string.gauguin_ui_reset_done))
                    rebuild()
                    true
                }
            },
        )
    }

    // ---- rows -----------------------------------------------------------------------------------

    private fun rowFor(spec: GauguinUiConfig.Spec): Preference =
        when (spec.kind) {
            GauguinUiConfig.Kind.COLOR -> colorRow(spec)
            GauguinUiConfig.Kind.SLIDER -> sliderRow(spec)
            GauguinUiConfig.Kind.TOGGLE -> toggleRow(spec)
            GauguinUiConfig.Kind.FONT -> fontRow(spec)
        }

    private fun colorRow(spec: GauguinUiConfig.Spec): Preference =
        ColorSwatchPreference(preferenceManager.context).apply {
            key = spec.key
            isPersistent = false
            layoutResource = itemLayout(spec.sub)
            title = getString(spec.labelRes)
            isSingleLineTitle = false
            color = config.int(spec.key)
            setOnPreferenceClickListener {
                ColorPickerDialog.show(
                    requireContext(),
                    spec.labelRes,
                    config.int(spec.key),
                ) { picked ->
                    config.setInt(spec.key, picked)
                    color = picked
                    preview()?.refresh()
                }
                true
            }
        }

    private fun sliderRow(spec: GauguinUiConfig.Spec): Preference =
        SeekBarPreference(preferenceManager.context).apply {
            key = spec.key
            isPersistent = false
            layoutResource = if (spec.sub) R.layout.preference_seekbar_gauguin_l2 else R.layout.preference_seekbar_gauguin
            title = getString(spec.labelRes)
            isSingleLineTitle = false
            min = spec.min
            max = spec.max
            value = config.int(spec.key).coerceIn(spec.min, spec.max)
            showSeekBarValue = true
            isAdjustable = true
            updatesContinuously = true
            setOnPreferenceChangeListener { _, newValue ->
                config.setInt(spec.key, newValue as Int)
                preview()?.refresh()
                true
            }
        }

    private fun toggleRow(spec: GauguinUiConfig.Spec): Preference =
        SwitchPreferenceCompat(preferenceManager.context).apply {
            key = spec.key
            isPersistent = false
            layoutResource = itemLayout(spec.sub)
            title = getString(spec.labelRes)
            isSingleLineTitle = false
            isChecked = config.bool(spec.key)
            setOnPreferenceChangeListener { _, newValue ->
                config.setBool(spec.key, newValue as Boolean)
                preview()?.refresh()
                true
            }
        }

    private fun fontRow(spec: GauguinUiConfig.Spec): Preference =
        Preference(preferenceManager.context).apply {
            key = spec.key
            isPersistent = false
            layoutResource = itemLayout(spec.sub)
            title = getString(spec.labelRes)
            isSingleLineTitle = false
            summary = GauguinFonts.displayName(requireContext(), config.string(spec.key))
            setOnPreferenceClickListener {
                FontPickerDialog.show(
                    requireContext(),
                    spec.labelRes,
                    config.string(spec.key),
                    onPick = { family ->
                        config.setString(spec.key, family)
                        summary = GauguinFonts.displayName(requireContext(), family)
                        preview()?.refresh()
                    },
                    onImport = {
                        onFontImported = { family ->
                            config.setString(spec.key, family)
                            summary = GauguinFonts.displayName(requireContext(), family)
                            preview()?.refresh()
                        }
                        importFontLauncher.launch(arrayOf("*/*"))
                    },
                )
                true
            }
        }

    // ---- headings -------------------------------------------------------------------------------

    private fun heading(
        titleRes: Int,
        first: Boolean,
    ): PreferenceCategory =
        PreferenceCategory(preferenceManager.context).apply {
            layoutResource =
                if (first) R.layout.preference_category_gauguin_first else R.layout.preference_category_gauguin
            isPersistent = false
            title = getString(titleRes)
            isIconSpaceReserved = false
        }

    private fun subHeading(titleRes: Int): Preference =
        Preference(preferenceManager.context).apply {
            layoutResource = R.layout.preference_subcategory_gauguin
            isPersistent = false
            isSelectable = false
            title = getString(titleRes)
        }

    private fun itemLayout(sub: Boolean) =
        if (sub) R.layout.preference_item_gauguin_l2 else R.layout.preference_item_gauguin

    // ---- helpers --------------------------------------------------------------------------------

    private fun preview(): GauguinUiPreviewView? =
        activity?.findViewById(R.id.gauguinUiPreview)

    private fun openExportPanel() {
        (activity as? GauguinUiActivity)?.openExportPanel()
    }

    /** The folder row: red while no backup folder is set, house yellow once it is. */
    private fun refreshDirectoryRow() {
        val preference = dirPreference ?: return
        val directory = config.exportDirectory
        preference.summary =
            if (directory == null) {
                colored(getString(R.string.export_import_dir_unset), R.color.shiroikuma_red)
            } else {
                colored(directory, R.color.shiroikuma_yellow)
            }
    }

    /**
     * The token rows exist only while the token is being asked for. Reading [AutomationAuth.token]
     * mints one on first read, so this only touches it when the rows are actually shown — an app
     * that never requires a token never generates one.
     */
    private fun refreshTokenRow() {
        val shown = AutomationAuth.requireToken(requireContext())
        tokenPreference?.isVisible = shown
        regeneratePreference?.isVisible = shown
        if (shown) {
            tokenPreference?.summary = AutomationAuth.abbreviate(AutomationAuth.token(requireContext()))
        }
    }

    private fun colored(
        text: String,
        colorRes: Int,
    ): CharSequence =
        SpannableString(text).apply {
            setSpan(
                ForegroundColorSpan(ContextCompat.getColor(requireContext(), colorRes)),
                0,
                length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }

    /**
     * Rebuild the whole screen after a reset. The new screen is assigned straight over the old one —
     * clearing it to null first would NPE inside androidx.preference.
     */
    private fun rebuild() {
        onCreatePreferences(null, null)
        refreshDirectoryRow()
        refreshTokenRow()
        preview()?.refresh()
    }

    private fun toast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
