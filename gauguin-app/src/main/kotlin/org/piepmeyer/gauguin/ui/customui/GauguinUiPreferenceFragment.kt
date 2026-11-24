package org.piepmeyer.gauguin.ui.customui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroupAdapter
import androidx.preference.PreferenceScreen
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import androidx.recyclerview.widget.LinearLayoutManager
import org.piepmeyer.gauguin.R
import org.piepmeyer.gauguin.ui.SettingsActivity
import org.piepmeyer.gauguin.ui.customui.chrome.SectionIndex
import org.piepmeyer.gauguin.ui.customui.chrome.SkIndexPreference
import org.piepmeyer.gauguin.ui.customui.chrome.SkPageState
import org.piepmeyer.gauguin.ui.customui.chrome.SkPreferences
import org.piepmeyer.gauguin.ui.customui.chrome.SkRole
import org.piepmeyer.gauguin.ui.customui.chrome.SkRolePreference
import org.piepmeyer.gauguin.ui.customui.chrome.SkRoles
import org.piepmeyer.gauguin.ui.customui.chrome.SkSection
import org.piepmeyer.gauguin.ui.customui.chrome.SkSectionCategory
import org.piepmeyer.gauguin.ui.customui.chrome.SkTheme
import org.piepmeyer.gauguin.ui.customui.chrome.SkToast

/**
 * The 白い熊 GNU Gauguin UI page: a folding accordion with a draggable chip index on top.
 *
 *  - The first element is the thick pill that opens the app's own settings.
 *  - Under it the index: one small chip per section, each with its section's icon in the section's
 *    colour. A tap unfolds the section and scrolls to it; a long press drags the chip, the chips
 *    reorder under the finger and the sections follow on drop.
 *  - Every section is folded by default. Its heading carries the same icon and colour as its chip.
 *    Fold state and order are kept in the fork's preferences file and travel in the export.
 *
 * The sections come from [SkSection], their colour rows from the role table ([SkRole]), their
 * sliders, switches and fonts from [GauguinUiConfig.SPECS]. A section's rows are built when it is
 * first opened. Every row is styled by [SkPreferences.styleRow]: an action is a pill that hugs its
 * label, a control stays plain, each has an icon sized by its label.
 */
class GauguinUiPreferenceFragment : PreferenceFragmentCompat() {
    private lateinit var config: GauguinUiConfig

    private val sections = mutableListOf<SkSectionCategory>()
    private val builtSections = mutableSetOf<String>()

    /** Rows hidden for a reason of their own (the automation token), whatever the fold state */
    private val hiddenRows = mutableSetOf<String>()

    private var linkPref: Preference? = null
    private var indexPref: SkIndexPreference? = null
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
        preferenceScreen = screen

        sections.clear()
        builtSections.clear()
        hiddenRows.clear()

        // the link to the app's own settings: the first, thick pill of the page
        linkPref =
            actionRow("gauguin_ui_app_settings", R.string.sk_app_settings, R.string.sk_app_settings_summary, main = true, opens = true) {
                startActivity(Intent(requireContext(), SettingsActivity::class.java))
            }
        indexPref =
            SkIndexPreference(
                context,
                {
                    orderedSections().map {
                        SectionIndex.Item(it.sectionKey, it.title ?: "", it.section.icon, SkRoles[it.section.color])
                    }
                },
                object : SectionIndex.Listener {
                    override fun onJump(key: String) {
                        sections.firstOrNull { it.sectionKey == key }?.let(::jumpToSection)
                    }

                    override fun onReordered(order: List<String>) {
                        // posted: the drag is still being dispatched to the chips this rebuild replaces
                        view?.post {
                            SkPageState.setOrder(requireContext(), order)
                            applySectionOrder()
                        }
                    }
                },
            )
        screen.addPreference(linkPref!!)
        screen.addPreference(indexPref!!)

        for (section in SkSection.entries) {
            val category =
                SkSectionCategory(context).apply {
                    key = SkSectionCategory.PREFIX + section.key
                    isPersistent = false
                    title = getString(section.labelRes)
                    isIconSpaceReserved = false
                    this.section = section
                    onToggle = { setSectionOpen(it, !SkPageState.isOpen(requireContext(), it.sectionKey)) }
                }
            screen.addPreference(category)
            sections += category
            if (SkPageState.isOpen(context, section.key)) buildRows(category)
        }

        applySectionOrder()
        sections.forEach(::applyFold)
        SkPreferences.decorate(screen)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        listView.setBackgroundColor(SkRoles[SkRole.BACKGROUND])
        // The sections draw their own hairline; the list's dividers would double it.
        setDivider(null)
        setDividerHeight(0)
        listView.clipToPadding = false
        SkPreferences.attach(listView)
    }

    override fun onPause() {
        super.onPause()
        // "Keep for N days" takes effect on leaving the page
        val app = requireContext().applicationContext
        Thread { runCatching { GauguinDiag.cleanup(app) } }.start()
    }

    override fun onResume() {
        super.onResume()
        refreshStoredRow()
        refreshDirectoryRow()
        refreshTokenRow()
    }

    // ---- sections -------------------------------------------------------------------------------

    /** The rows of a section; built when the section is first opened */
    private fun buildRows(category: SkSectionCategory) {
        if (!builtSections.add(category.sectionKey)) return
        val section = category.section
        when (section) {
            SkSection.EXIMPORT -> buildExportRows(category)
            SkSection.MAINTENANCE -> buildMaintenanceRows(category)
            SkSection.DIAGNOSTICS -> buildDiagnosticsRows(category)
            else -> {
                SkRole.entries.filter { it.section == section }.forEach { category.addPreference(roleRow(it)) }
                GauguinUiConfig.SPECS.filter { it.category.section == section }.forEach { category.addPreference(rowFor(it)) }
                category.addPreference(
                    actionRow("gauguin_ui_reset_" + section.key, R.string.gauguin_ui_reset, R.string.gauguin_ui_reset_summary) {
                        confirm(getString(R.string.gauguin_ui_reset_confirm, getString(section.labelRes))) {
                            config.resetToDefaults(section)
                            toast(getString(R.string.gauguin_ui_reset_done))
                            requireActivity().recreate()
                        }
                    },
                )
            }
        }
        SkPreferences.decorate(category)
    }

    private fun buildExportRows(category: SkSectionCategory) {
        // the section's one panel is its main action; it opens something
        category.addPreference(
            actionRow(
                "gauguin_ui_eximport",
                R.string.gauguin_ui_export_entry,
                R.string.gauguin_ui_export_entry_summary,
                main = true,
                opens = true,
            ) { openExportPanel() },
        )

        // a value that opens its editor: a control row, not a pill
        dirPreference =
            Preference(preferenceManager.context).apply {
                key = "gauguin_ui_export_directory"
                layoutResource = R.layout.sk_preference_row
                isPersistent = false
                title = getString(R.string.gauguin_ui_export_dir_row)
                isSingleLineTitle = false
                extras.putBoolean(SkPreferences.EXTRA_CONTROL, true)
                setOnPreferenceClickListener {
                    openExportPanel()
                    true
                }
            }
        category.addPreference(dirPreference!!)

        // The 保存復元 automation contract's rows live here — inside Export/Import, never as a
        // section of their own, so every sister app puts them in the same place. Contract v2 makes
        // it three: the master switch (ON), the token requirement (OFF), and the token itself,
        // which is shown only when it is actually being asked for. A 48-character secret sitting
        // under an off switch only invites 白い熊 to paste it somewhere it will do nothing.
        category.addPreference(
            switchRow(
                "gauguin_ui_automation_enabled",
                R.string.gauguin_ui_automation_switch,
                R.string.gauguin_ui_automation_switch_summary,
                AutomationAuth.enabled(requireContext()),
            ) { AutomationAuth.setEnabled(requireContext(), it) },
        )
        category.addPreference(
            switchRow(
                "gauguin_ui_automation_require_token",
                R.string.gauguin_ui_automation_require_token,
                R.string.gauguin_ui_automation_require_token_summary,
                AutomationAuth.requireToken(requireContext()),
            ) {
                AutomationAuth.setRequireToken(requireContext(), it)
                refreshTokenRow()
            },
        )

        tokenPreference =
            actionRow("gauguin_ui_automation_token", R.string.gauguin_ui_automation_token, null) {
                val token = AutomationAuth.token(requireContext())
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("token", token))
                toast(getString(R.string.gauguin_ui_automation_token_copied))
            }
        category.addPreference(tokenPreference!!)

        regeneratePreference =
            actionRow(
                "gauguin_ui_automation_regenerate",
                R.string.gauguin_ui_automation_regenerate,
                R.string.gauguin_ui_automation_regenerate_summary,
            ) {
                AutomationAuth.regenerate(requireContext())
                refreshTokenRow()
                GauguinDialogs.info(
                    requireContext(),
                    getString(R.string.gauguin_ui_automation_token),
                    getString(R.string.gauguin_ui_automation_regenerate_warning),
                )
            }
        category.addPreference(regeneratePreference!!)

        refreshDirectoryRow()
        refreshTokenRow()
    }

    // ---- diagnostics ----------------------------------------------------------------------------

    private var storedPreference: Preference? = null

    private val exportDiagLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree: Uri? ->
            tree ?: return@registerForActivityResult
            val app = requireContext().applicationContext
            Thread {
                val name = runCatching { GauguinDiag.exportTo(app, tree) }.getOrNull()
                view?.post {
                    if (!isAdded) return@post
                    toast(if (name != null) getString(R.string.diag_exported, name) else getString(R.string.diag_export_failed))
                }
            }.start()
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            GauguinDiag.updateNotification(requireContext())
        }

    /**
     * UI → Diagnostics: every diagnostic behind its own switch, off by default, grouped by theme;
     * then what is stored, how long it is kept, and the ways out — export, share, purge.
     */
    private fun buildDiagnosticsRows(category: SkSectionCategory) {
        val context = requireContext()
        category.addPreference(
            plainRow("gauguin_ui_diag_directory", R.string.diag_directory, GauguinDiag.dir(context)?.absolutePath ?: "?"),
        )

        category.addPreference(subheader(R.string.diag_group_interface))
        GauguinDiag.Kind.entries.forEach { kind ->
            category.addPreference(
                switchRow("gauguin_ui_diag_" + kind.key, kind.titleRes, kind.descrRes, GauguinDiag.isOn(kind)) { checked ->
                    GauguinDiag.set(requireContext(), kind, checked)
                    if (checked) askForNotifications()
                },
            )
        }

        category.addPreference(subheader(R.string.diag_group_data))
        storedPreference = plainRow("gauguin_ui_diag_stored", R.string.diag_stored, getString(R.string.diag_counting))
        category.addPreference(storedPreference!!)
        category.addPreference(
            SeekBarPreference(preferenceManager.context).apply {
                key = "gauguin_ui_diag_keep_days"
                isPersistent = false
                layoutResource = R.layout.sk_preference_seekbar
                setTitle(R.string.diag_keep)
                setSummary(R.string.diag_keep_descr)
                isSingleLineTitle = false
                min = 0
                max = GauguinDiag.MAX_KEEP_DAYS
                value = GauguinDiag.getKeepDays(context).coerceIn(0, GauguinDiag.MAX_KEEP_DAYS)
                showSeekBarValue = true
                isAdjustable = true
                updatesContinuously = true
                setOnPreferenceChangeListener { _, newValue ->
                    GauguinDiag.setKeepDays(requireContext(), newValue as Int)
                    true
                }
            },
        )
        category.addPreference(
            actionRow("gauguin_ui_diag_export", R.string.diag_export, R.string.diag_export_descr, opens = true) {
                if (GauguinDiag.files(requireContext()).isEmpty()) {
                    toast(getString(R.string.diag_stored_none))
                } else {
                    exportDiagLauncher.launch(GauguinDiag.exportStart(requireContext()))
                }
            },
        )
        category.addPreference(
            actionRow("gauguin_ui_diag_share", R.string.diag_share, R.string.diag_share_descr, opens = true) {
                GauguinDiag.shareIntent(requireContext())?.let(::startActivity) ?: toast(getString(R.string.diag_stored_none))
            },
        )
        category.addPreference(
            actionRow("gauguin_ui_diag_purge", R.string.diag_purge, R.string.diag_purge_descr, opens = true) { confirmPurge() },
        )
        refreshStoredRow()
    }

    private fun askForNotifications() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
        val permission = android.Manifest.permission.POST_NOTIFICATIONS
        val granted =
            androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), permission) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermissionLauncher.launch(permission)
    }

    /** "Stored": files, size and span, counted off the main thread */
    private fun refreshStoredRow() {
        val row = storedPreference ?: return
        val app = requireContext().applicationContext
        Thread {
            val summary = runCatching { GauguinDiag.summarize(app) }.getOrNull() ?: return@Thread
            view?.post {
                if (!isAdded) return@post
                row.summary =
                    if (summary.files == 0) {
                        getString(R.string.diag_stored_none)
                    } else {
                        getString(R.string.diag_stored_value, summary.files, size(summary.bytes), stamp(summary.first), stamp(summary.last))
                    }
            }
        }.start()
    }

    /** The purge warning: how many files, how big, and which kinds and days go — forever */
    private fun confirmPurge() {
        val app = requireContext().applicationContext
        Thread {
            val summary = runCatching { GauguinDiag.summarize(app) }.getOrNull() ?: return@Thread
            view?.post {
                if (!isAdded) return@post
                if (summary.files == 0) {
                    toast(getString(R.string.diag_stored_none))
                    return@post
                }
                val lines = mutableListOf<String>()
                summary.kinds.forEach { (kind, k) ->
                    lines += getString(R.string.diag_purge_kind, kind, k[0].toInt(), size(k[1]), stamp(k[2]), stamp(k[3]))
                }
                lines += ""
                summary.days.forEach { (day, span) -> lines += getString(R.string.diag_purge_day, day, clock(span[0]), clock(span[1])) }
                AlertDialog
                    .Builder(requireContext())
                    .setTitle(R.string.diag_purge_title)
                    .setMessage(getString(R.string.diag_purge_message, summary.files, size(summary.bytes), lines.joinToString("\n")))
                    .setPositiveButton(R.string.diag_delete_forever) { _, _ ->
                        val gone = GauguinDiag.purge(app)
                        toast(getString(R.string.diag_purged, gone))
                        refreshStoredRow()
                    }.setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }.start()
    }

    private fun size(bytes: Long): String = android.text.format.Formatter.formatShortFileSize(requireContext(), bytes)

    private fun stamp(millis: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(millis))

    private fun clock(millis: Long): String = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(millis))

    /** A sub-heading inside a section */
    private fun subheader(titleRes: Int): Preference =
        Preference(preferenceManager.context).apply {
            isPersistent = false
            isSelectable = false
            layoutResource = R.layout.sk_preference_subheader
            setTitle(titleRes)
        }

    /** A row that only shows a value: a label and what it stands for */
    private fun plainRow(
        rowKey: String,
        titleRes: Int,
        value: String,
    ): Preference =
        Preference(preferenceManager.context).apply {
            key = rowKey
            isPersistent = false
            isSelectable = false
            layoutResource = R.layout.sk_preference_row
            setTitle(titleRes)
            summary = value
            isSingleLineTitle = false
            extras.putBoolean(SkPreferences.EXTRA_CONTROL, true)
        }

    private fun buildMaintenanceRows(category: SkSectionCategory) {
        category.addPreference(
            actionRow("gauguin_ui_reset_order", R.string.sk_reset_section_order, R.string.sk_reset_section_order_summary) {
                SkPageState.resetOrder(requireContext())
                applySectionOrder()
            },
        )
        category.addPreference(
            actionRow("gauguin_ui_reset_colours", R.string.sk_reset_all_colours, R.string.sk_reset_all_colours_summary) {
                confirm(getString(R.string.sk_reset_all_colours_confirm)) {
                    SkRoles.resetAll()
                    requireActivity().recreate()
                }
            },
        )
    }

    private fun orderedSections(): List<SkSectionCategory> {
        val order = SkPageState.order(requireContext(), sections.map { it.sectionKey })
        return order.mapNotNull { key -> sections.firstOrNull { it.sectionKey == key } }
    }

    /** The settings link and the index stay on top; the sections follow in their stored order */
    private fun applySectionOrder() {
        linkPref?.order = 0
        indexPref?.order = 1
        orderedSections().forEachIndexed { index, category -> category.order = 10 + index }
        indexPref?.refresh()
    }

    private fun applyFold(category: SkSectionCategory) {
        val open = SkPageState.isOpen(requireContext(), category.sectionKey)
        for (index in 0 until category.preferenceCount) {
            val row = category.getPreference(index)
            row.isVisible = open && row.key !in hiddenRows
        }
    }

    private fun setSectionOpen(
        category: SkSectionCategory,
        open: Boolean,
    ) {
        SkPageState.setOpen(requireContext(), category.sectionKey, open)
        if (open) buildRows(category)
        applyFold(category)
        category.refresh()
    }

    /** A tap on a chip: unfold the section and bring its heading to the top of the page */
    @SuppressLint("RestrictedApi")
    private fun jumpToSection(category: SkSectionCategory) {
        setSectionOpen(category, true)
        listView?.post {
            val list = listView ?: return@post
            val position = (list.adapter as? PreferenceGroupAdapter)?.getPreferenceAdapterPosition(category) ?: return@post
            if (position >= 0) (list.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(position, 0)
        }
    }

    // ---- rows -----------------------------------------------------------------------------------

    private fun actionRow(
        rowKey: String,
        titleRes: Int,
        summaryRes: Int?,
        main: Boolean = false,
        opens: Boolean = false,
        onClick: () -> Unit,
    ): Preference =
        Preference(preferenceManager.context).apply {
            key = rowKey
            isPersistent = false
            layoutResource = R.layout.sk_preference_row
            setTitle(titleRes)
            if (summaryRes != null) setSummary(summaryRes)
            isSingleLineTitle = false
            extras.putBoolean(SkPreferences.EXTRA_MAIN, main)
            extras.putBoolean(SkPreferences.EXTRA_OPENS, opens)
            setOnPreferenceClickListener {
                onClick()
                true
            }
        }

    private fun switchRow(
        rowKey: String,
        titleRes: Int,
        summaryRes: Int?,
        checked: Boolean,
        onChange: (Boolean) -> Unit,
    ): Preference =
        SwitchPreferenceCompat(preferenceManager.context).apply {
            key = rowKey
            isPersistent = false
            layoutResource = R.layout.sk_preference_row
            widgetLayoutResource = R.layout.sk_preference_widget_switch
            setTitle(titleRes)
            if (summaryRes != null) setSummary(summaryRes)
            isSingleLineTitle = false
            isChecked = checked
            setOnPreferenceChangeListener { _, newValue ->
                onChange(newValue as Boolean)
                true
            }
        }

    /** One colour: a swatch row; its picker applies live, and "Default" puts the role's default back */
    private fun roleRow(role: SkRole): Preference =
        SkRolePreference(preferenceManager.context, role).apply {
            setOnPreferenceClickListener {
                ColorPickerDialog.show(
                    requireContext(),
                    role.labelRes,
                    SkRoles[role],
                    onDefault = { SkRoles.set(role, null) },
                    // upstream's layouts read the colours through the theme: the page is rebuilt
                    onDone = { activity?.recreate() },
                ) { picked ->
                    SkRoles.set(role, picked)
                    refresh()
                            }
                true
            }
        }

    private fun rowFor(spec: GauguinUiConfig.Spec): Preference =
        when (spec.kind) {
            GauguinUiConfig.Kind.SLIDER -> sliderRow(spec)
            GauguinUiConfig.Kind.TOGGLE -> toggleRow(spec)
            GauguinUiConfig.Kind.FONT -> fontRow(spec)
        }

    private fun sliderRow(spec: GauguinUiConfig.Spec): Preference =
        SeekBarPreference(preferenceManager.context).apply {
            key = spec.key
            isPersistent = false
            layoutResource = R.layout.sk_preference_seekbar
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
                        true
            }
        }

    private fun toggleRow(spec: GauguinUiConfig.Spec): Preference =
        switchRow(spec.key, spec.labelRes, null, config.bool(spec.key)) { checked ->
            config.setBool(spec.key, checked)
                // the master switch decides whether any screen wears the look at all
            if (spec.key == GauguinUiConfig.ENABLED) SkTheme.markCurrent(requireActivity())
        }

    /** A value that opens a picker: a control row, not a pill */
    private fun fontRow(spec: GauguinUiConfig.Spec): Preference =
        Preference(preferenceManager.context).apply {
            key = spec.key
            isPersistent = false
            layoutResource = R.layout.sk_preference_row
            title = getString(spec.labelRes)
            isSingleLineTitle = false
            extras.putBoolean(SkPreferences.EXTRA_CONTROL, true)
            summary = GauguinFonts.displayName(requireContext(), config.string(spec.key))
            setOnPreferenceClickListener {
                FontPickerDialog.show(
                    requireContext(),
                    spec.labelRes,
                    config.string(spec.key),
                    onPick = { family ->
                        config.setString(spec.key, family)
                        summary = GauguinFonts.displayName(requireContext(), family)
                                    },
                    onImport = {
                        onFontImported = { family ->
                            config.setString(spec.key, family)
                            summary = GauguinFonts.displayName(requireContext(), family)
                                            }
                        importFontLauncher.launch(arrayOf("*/*"))
                    },
                )
                true
            }
        }

    // ---- helpers --------------------------------------------------------------------------------

    private fun openExportPanel() {
        (activity as? GauguinUiActivity)?.openExportPanel()
    }

    /** A confirmation whose positive button resets: it is drawn red, by its label */
    private fun confirm(
        message: String,
        onConfirmed: () -> Unit,
    ) {
        AlertDialog
            .Builder(requireContext())
            .setMessage(message)
            .setPositiveButton(R.string.gauguin_ui_reset_button) { _, _ -> onConfirmed() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** The directory row: the warning colour while no backup directory is set. */
    private fun refreshDirectoryRow() {
        val preference = dirPreference ?: return
        val directory = config.exportDirectory
        preference.summary =
            if (directory == null) {
                colored(getString(R.string.export_import_dir_unset), SkRoles[SkRole.ERROR])
            } else {
                directory
            }
    }

    /**
     * The token rows exist only while the token is being asked for. Reading [AutomationAuth.token]
     * mints one on first read, so this only touches it when the rows are actually shown — an app
     * that never requires a token never generates one.
     */
    private fun refreshTokenRow() {
        val shown = AutomationAuth.requireToken(requireContext())
        listOfNotNull(tokenPreference, regeneratePreference).forEach { row ->
            if (shown) hiddenRows.remove(row.key) else hiddenRows.add(row.key)
            row.isVisible = shown && SkPageState.isOpen(requireContext(), SkSection.EXIMPORT.key)
        }
        if (shown) {
            tokenPreference?.summary = AutomationAuth.abbreviate(AutomationAuth.token(requireContext()))
        }
    }

    private fun colored(
        text: String,
        color: Int,
    ): CharSequence =
        SpannableString(text).apply {
            setSpan(ForegroundColorSpan(color), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

    private fun toast(message: String) {
        SkToast.show(requireContext(), message)
    }
}
