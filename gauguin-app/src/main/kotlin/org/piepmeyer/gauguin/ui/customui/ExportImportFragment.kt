package org.piepmeyer.gauguin.ui.customui

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.piepmeyer.gauguin.R
import java.io.File

/**
 * The Export / Import panel, in the Kōjiki sheet format with the ArcaneChat button bar: the whole
 * page sits in one bordered rounded box — centred bold title, dim intro, a bordered tappable folder
 * box (red while unset, house yellow once set), the last-backup line, thin dividers around the
 * checkbox list, then Cancel alone on the left with Import and Export grouped on the right, all as
 * fully round pills.
 *
 * The close chain is 白い熊's: a **successful** export or import shows a bordered black-yellow result
 * dialog, and acknowledging it closes the dialog, this panel and the UI settings page behind it. A
 * failure ("Export failed…", "No categories selected.") leaves everything open so the folder or the
 * selection can be corrected on the spot.
 */
class ExportImportFragment : Fragment() {
    private lateinit var config: GauguinUiConfig
    private lateinit var root: LinearLayout

    private var exportDir: String? = null
    private val selectedParts: MutableSet<GauguinBackup.Cat> =
        GauguinBackup.Cat.entries.filter { it.defaultSelected }.toMutableSet()

    private val yellow = GauguinUiConfig.YELLOW
    private val dim = 0xFFCCCC66.toInt()
    private val red = 0xFFFF5555.toInt()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        config = GauguinUiConfig(requireContext())
        val scroll =
            ScrollView(requireContext()).apply {
                setBackgroundColor(config.backgroundColor)
                isFillViewport = true
                clipToPadding = false
            }
        root =
            LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), dp(10), dp(10), dp(24))
                clipToPadding = false
                clipChildren = false
            }
        scroll.addView(root)
        return scroll
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        rebuild()
    }

    override fun onResume() {
        super.onResume()
        // Coming back from the all-files-access screen: re-render so the grant prompt clears.
        rebuild()
    }

    private fun rebuild() {
        exportDir = config.exportDirectory
        root.removeAllViews()

        val box =
            LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(16), dp(20), dp(20))
                clipToPadding = false
                clipChildren = false
                background =
                    GradientDrawable().apply {
                        setColor(config.backgroundColor)
                        setStroke(dp(2), yellow)
                        cornerRadius = dp(16).toFloat()
                    }
            }
        root.addView(
            box,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )

        box.addView(heading(getString(R.string.export_import_title)))
        box.addView(
            caption(getString(R.string.export_import_intro)).apply {
                alpha = 0.85f
                setPadding(0, 0, 0, dp(10))
            },
        )

        if (!hasAllFilesAccess()) {
            box.addView(caption(getString(R.string.export_import_need_access), color = red))
            box.addView(pillButton(getString(R.string.export_import_grant_access)) { requestAllFilesAccess() })
            box.addView(spacer(6))
        }

        box.addView(dirRow(exportDir))
        box.addView(statusLine())

        box.addView(divider())
        box.addView(selectAllRow())
        GauguinBackup.Cat.entries.forEach { part -> box.addView(partRow(part)) }

        box.addView(divider(topGap = 8))
        box.addView(actionRow())
    }

    // ---- rows -----------------------------------------------------------------------------------

    /** The folder box: small label over the bold path, red while nothing is set. */
    private fun dirRow(path: String?): View =
        LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusable = true
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background =
                GradientDrawable().apply {
                    setColor(config.backgroundColor)
                    setStroke(dp(2), if (path == null) red else yellow)
                    cornerRadius = dp(10).toFloat()
                }
            setOnClickListener { editDir(path) }
            addView(
                TextView(requireContext()).apply {
                    text = getString(R.string.export_import_dir_label)
                    setTextColor(if (path == null) red else yellow)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                },
            )
            addView(
                TextView(requireContext()).apply {
                    text = path ?: getString(R.string.export_import_dir_unset)
                    setTextColor(if (path == null) red else yellow)
                    setTypeface(typeface, Typeface.BOLD)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                },
            )
            layoutParams =
                LinearLayout
                    .LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply {
                        topMargin = dp(6)
                        bottomMargin = dp(6)
                    }
        }

    private fun statusLine(): View {
        val (text, warn) = lastBackupStatus()
        return TextView(requireContext()).apply {
            this.text = text
            setTextColor(if (warn) red else dim)
            alpha = if (warn) 1f else 0.8f
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dp(2), 0, 0, dp(8))
        }
    }

    private fun selectAllRow(): View =
        checkbox(getString(R.string.export_import_select_all), bold = true).apply {
            isChecked = selectedParts.size == GauguinBackup.Cat.entries.size
            setOnClickListener {
                if (isChecked) {
                    selectedParts.addAll(GauguinBackup.Cat.entries)
                } else {
                    selectedParts.clear()
                }
                rebuild()
            }
        }

    private fun partRow(part: GauguinBackup.Cat): View =
        checkbox(getString(part.labelRes)).apply {
            isChecked = part in selectedParts
            setPadding(dp(24), dp(7), 0, dp(7))
            setOnCheckedChangeListener { _, checked ->
                if (checked) selectedParts.add(part) else selectedParts.remove(part)
            }
        }

    private fun divider(topGap: Int = 0): View =
        View(requireContext()).apply {
            layoutParams =
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                    topMargin = dp(topGap)
                }
            setBackgroundColor(yellow)
            alpha = 0.4f
        }

    /** The ArcaneChat bar: Cancel alone on the left, Import + Export grouped on the right. */
    private fun actionRow(): View =
        LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            setPadding(0, dp(14), 0, 0)
            addView(
                pillButton(getString(R.string.export_import_cancel)) {
                    (activity as? GauguinUiActivity)?.closeExportPanel()
                },
            )
            addView(View(requireContext()).also { it.layoutParams = LinearLayout.LayoutParams(0, 0, 1f) })
            addView(
                pillButton(getString(R.string.export_import_import)) { onImport() }.also {
                    (it.layoutParams as LinearLayout.LayoutParams).marginEnd = dp(8)
                },
            )
            addView(pillButton(getString(R.string.export_import_export)) { onExport() })
        }

    // ---- export ---------------------------------------------------------------------------------

    private fun onExport() {
        if (!ensureReady()) return
        if (selectedParts.isEmpty()) {
            flash(getString(R.string.export_import_none_selected))
            return
        }
        val directory = File(exportDir!!)
        val parts = selectedParts.toSet()
        val name = GauguinBackup.exportFileName()
        flash(getString(R.string.export_import_exporting))
        lifecycleScope.launch {
            val result =
                runCatching {
                    withContext(Dispatchers.IO) {
                        GauguinBackup.exportToDirectory(requireContext(), parts, directory, name)
                    }
                }
            result
                .onSuccess { file ->
                    GauguinDialogs.info(
                        requireContext(),
                        getString(R.string.export_import_export_done_title),
                        getString(
                            R.string.export_import_export_ok,
                            file.name,
                            GauguinBackup.humanSize(file.length()),
                        ),
                    ) {
                        // 白い熊's close chain: OK closes the dialog, this panel, and the page.
                        (activity as? GauguinUiActivity)?.closeAfterSuccess()
                    }
                }.onFailure {
                    // A failure leaves the panel open so the folder or selection can be fixed here.
                    GauguinDialogs.info(
                        requireContext(),
                        getString(R.string.export_import_export_done_title),
                        getString(R.string.export_import_export_failed, it.message ?: ""),
                    )
                }
        }
    }

    // ---- import ---------------------------------------------------------------------------------

    private fun onImport() {
        if (!ensureReady()) return
        if (selectedParts.isEmpty()) {
            flash(getString(R.string.export_import_none_selected))
            return
        }
        val backups = listBackups()
        if (backups.isEmpty()) {
            flash(getString(R.string.export_import_no_backups))
            return
        }
        val names = backups.map { it.name }.toTypedArray()
        val dialog =
            AlertDialog
                .Builder(requireContext(), R.style.Theme_Gauguin_ShiroikumaDialog)
                .setTitle(R.string.export_import_pick_backup)
                .setItems(names) { _, which -> runImport(backups[which]) }
                .setNegativeButton(R.string.export_import_cancel, null)
                .create()
        GauguinDialogs.style(dialog)
        dialog.show()
    }

    private fun runImport(file: File) {
        val parts = selectedParts.toSet()
        flash(getString(R.string.export_import_importing))
        lifecycleScope.launch {
            val result =
                runCatching {
                    withContext(Dispatchers.IO) {
                        file.inputStream().use { GauguinBackup.import(requireContext(), parts, it) }
                    }
                }
            result
                .onSuccess { showImportResult(it) }
                .onFailure {
                    GauguinDialogs.info(
                        requireContext(),
                        getString(R.string.export_import_import_done_title),
                        getString(R.string.export_import_import_failed, it.message ?: ""),
                    )
                }
        }
    }

    private fun showImportResult(result: GauguinBackup.ImportResult) {
        val body =
            buildString {
                result.lines.forEach { appendLine(it) }
                if (result.errors.isNotEmpty()) {
                    appendLine()
                    appendLine("⚠ " + result.errors.joinToString(", "))
                }
                appendLine()
                append(getString(R.string.export_import_restart_hint))
            }.trim()

        GauguinDialogs.choice(
            requireContext(),
            getString(R.string.export_import_import_done_title),
            body,
            positiveText = getString(R.string.export_import_restart_now),
            negativeText = getString(R.string.export_import_restart_later),
            onPositive = { restartApp() },
            // "Later" unwinds the whole chain: dialog, panel, and the settings page.
            onNegative = { (activity as? GauguinUiActivity)?.closeAfterSuccess() },
        )
    }

    private fun restartApp() {
        val context = requireContext().applicationContext
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        context.startActivity(Intent.makeRestartActivityTask(launch.component))
        Runtime.getRuntime().exit(0)
    }

    // ---- folder ---------------------------------------------------------------------------------

    private fun listBackups(): List<File> {
        val directory = exportDir?.let { File(it) } ?: return emptyList()
        if (!directory.isDirectory) return emptyList()
        return directory
            .listFiles { file -> file.isFile && GauguinBackup.isBackupFileName(file.name) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    private fun lastBackupStatus(): Pair<String, Boolean> {
        if (exportDir.isNullOrBlank()) return getString(R.string.export_import_last_nodir) to true
        val newest = listBackups().firstOrNull() ?: return getString(R.string.export_import_last_none) to true
        val stamp =
            DateFormat.getDateFormat(requireContext()).format(newest.lastModified()) + " " +
                DateFormat.getTimeFormat(requireContext()).format(newest.lastModified())
        return getString(R.string.export_import_last, stamp) to false
    }

    private fun ensureReady(): Boolean {
        if (!hasAllFilesAccess()) {
            requestAllFilesAccess()
            return false
        }
        if (exportDir.isNullOrBlank()) {
            editDir(null)
            return false
        }
        return true
    }

    private fun editDir(current: String?) {
        val input =
            EditText(requireContext()).apply {
                setText(current ?: "")
                hint = getString(R.string.export_import_dir_hint)
                setSingleLine()
                setTextColor(yellow)
                setHintTextColor(dim)
            }
        val box = FrameLayout(requireContext()).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(input) }
        val dialog =
            AlertDialog
                .Builder(requireContext(), R.style.Theme_Gauguin_ShiroikumaDialog)
                .setTitle(R.string.export_import_dir_dialog_title)
                .setMessage(R.string.export_import_dir_dialog_message)
                .setView(box)
                .setPositiveButton(R.string.export_import_save) { _, _ -> saveDir(input.text.toString()) }
                .setNeutralButton(R.string.export_import_browse) { _, _ ->
                    val start =
                        current?.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.isDirectory }
                            ?: Environment.getExternalStorageDirectory()
                    browseForFolder(start) { picked -> saveDir(picked.absolutePath) }
                }.setNegativeButton(R.string.export_import_cancel, null)
                .create()
        GauguinDialogs.style(dialog)
        dialog.show()
    }

    private fun saveDir(path: String) {
        config.exportDirectory = path.takeIf { it.isNotBlank() }
        rebuild()
    }

    private fun browseForFolder(
        directory: File,
        onPick: (File) -> Unit,
    ) {
        if (!hasAllFilesAccess()) {
            requestAllFilesAccess()
            return
        }
        val subDirectories = directory.listFiles { file -> file.isDirectory }?.sortedBy { it.name.lowercase() } ?: emptyList()
        val labels = mutableListOf<String>()
        val targets = mutableListOf<File?>()
        labels.add("✓ ${getString(R.string.export_import_pick_here)}")
        targets.add(null)
        directory.parentFile?.let {
            labels.add(".. (${it.name.ifBlank { "/" }})")
            targets.add(it)
        }
        subDirectories.forEach {
            labels.add("📁 ${it.name}")
            targets.add(it)
        }
        val dialog =
            AlertDialog
                .Builder(requireContext(), R.style.Theme_Gauguin_ShiroikumaDialog)
                .setTitle(directory.absolutePath)
                .setItems(labels.toTypedArray()) { _, which ->
                    val target = targets[which]
                    if (target == null) onPick(directory) else browseForFolder(target, onPick)
                }.setNegativeButton(R.string.export_import_cancel, null)
                .create()
        GauguinDialogs.style(dialog)
        dialog.show()
    }

    private fun hasAllFilesAccess(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    private fun requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val target = Uri.parse("package:" + requireContext().packageName)
        runCatching { startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, target)) }
            .recoverCatching { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
            .onFailure { flash(getString(R.string.export_import_need_access)) }
    }

    // ---- view builders --------------------------------------------------------------------------

    private fun heading(text: String) =
        TextView(requireContext()).apply {
            this.text = text
            setTextColor(yellow)
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(6))
        }

    private fun caption(
        text: String,
        color: Int = dim,
    ) = TextView(requireContext()).apply {
        this.text = text
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
    }

    private fun checkbox(
        labelText: String,
        bold: Boolean = false,
    ): CheckBox =
        CheckBox(requireContext()).apply {
            text = labelText
            setTextColor(yellow)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            buttonTintList = ColorStateList.valueOf(yellow)
            setPadding(dp(8), dp(7), 0, dp(7))
        }

    private fun spacer(height: Int) =
        View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(height))
        }

    /** A fully round ArcaneChat pill: black fill, thin yellow stroke, yellow text, yellow ripple. */
    private fun pillButton(
        text: String,
        onClick: () -> Unit,
    ): Button =
        Button(requireContext()).apply {
            this.text = text
            isAllCaps = false
            setTextColor(yellow)
            background =
                RippleDrawable(
                    ColorStateList.valueOf((yellow and 0x00FFFFFF) or 0x33000000),
                    GradientDrawable().apply {
                        setColor(config.backgroundColor)
                        setStroke((1.5f * resources.displayMetrics.density).toInt(), yellow)
                        cornerRadius = dp(50).toFloat()
                    },
                    null,
                )
            // Explicit padding + zeroed minimums so the rounded stroke is never clipped at the edge.
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(20), dp(6), dp(20), dp(6))
            stateListAnimator = null
            setOnClickListener { onClick() }
            layoutParams =
                LinearLayout
                    .LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply {
                        topMargin = dp(8)
                        gravity = Gravity.CENTER
                    }
        }

    private fun flash(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
