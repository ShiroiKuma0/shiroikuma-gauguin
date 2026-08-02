package org.piepmeyer.gauguin.ui.customui

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject
import org.piepmeyer.gauguin.R
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The category ZIP backup, in the family format: a `manifest.json` plus one `<id>.json` (or, for
 * file corpora, one `<id>/` folder) per selected category.
 *
 * The export core is deliberately headless — [export] takes categories, an [OutputStream] and a
 * progress callback, so the Export/Import panel, the automation receiver and its foreground service
 * are all thin callers of the same code, per the 保存復元 contract.
 */
object GauguinBackup {
    const val FORMAT = "shiroikuma-gauguin-backup"
    const val VERSION = 1

    /**
     * The mandatory family name convention: `<english-app-name>_<yyyy-MM-dd_HH-mm-ss>.zip`. No
     * version, no infix, no suffix — 白い熊 keeps every sister app's backups in one directory, so
     * they must sort and read uniformly.
     */
    const val EXPORT_PREFIX = "shiroikuma-gauguin_"

    fun exportFileName(now: Date = Date()): String =
        EXPORT_PREFIX + SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(now) + ".zip"

    fun isBackupFileName(name: String): Boolean =
        name.startsWith(EXPORT_PREFIX) && name.endsWith(".zip")

    /** One exportable part of the app. [id] doubles as the ZIP entry name and the `items` id. */
    enum class Cat(
        val id: String,
        val labelRes: Int,
        val defaultSelected: Boolean = true,
    ) {
        UI("ui", R.string.backup_cat_ui),
        FONTS("fonts", R.string.backup_cat_fonts),
        SETTINGS("settings", R.string.backup_cat_settings),
        STATISTICS("statistics", R.string.backup_cat_statistics),
        SAVED_GAMES("savedgames", R.string.backup_cat_savedgames),
        ;

        companion object {
            fun byId(id: String): Cat? = entries.firstOrNull { it.id == id }
        }
    }

    data class Progress(
        val category: Cat,
        val position: Int,
        val total: Int,
    )

    data class ImportResult(
        val lines: List<String>,
        val errors: List<String>,
    )

    /**
     * Write [categories] into [target]. [onProgress] is called once per category as it starts, with
     * the 1-based position of the category being written (what the 自由作業盤 panel highlights).
     * [isCancelled] is polled between categories so an outside cancel unwinds at a clean boundary.
     */
    fun export(
        context: Context,
        categories: Set<Cat>,
        target: OutputStream,
        onProgress: (Progress) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ) {
        val ordered = Cat.entries.filter { it in categories }
        ZipOutputStream(target.buffered()).use { zip ->
            val manifest =
                JSONObject().apply {
                    put("format", FORMAT)
                    put("version", VERSION)
                    put("app", "shiroikuma-gauguin")
                    put("appVersion", appVersion(context))
                    put("createdTs", System.currentTimeMillis())
                    put("categories", JSONArray(ordered.map { it.id }))
                }
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toString(2).toByteArray())
            zip.closeEntry()

            ordered.forEachIndexed { index, category ->
                if (isCancelled()) throw InterruptedException("cancelled")
                onProgress(Progress(category, index + 1, ordered.size))
                when (category) {
                    Cat.UI -> writeJson(zip, category, prefsToJson(uiPrefs(context)))
                    Cat.SETTINGS -> writeJson(zip, category, prefsToJson(defaultPrefs(context)))
                    Cat.STATISTICS -> writeJson(zip, category, prefsToJson(statsPrefs(context)))
                    Cat.FONTS -> writeFiles(zip, category, GauguinFonts.fontsDir(context).listFiles()?.toList().orEmpty())
                    Cat.SAVED_GAMES -> writeFiles(zip, category, savedGameFiles(context))
                }
            }
        }
    }

    /** Merge a backup back in, key by key. Absent categories are skipped, not treated as errors. */
    fun import(
        context: Context,
        categories: Set<Cat>,
        source: InputStream,
    ): ImportResult {
        val lines = mutableListOf<String>()
        val errors = mutableListOf<String>()
        var sawManifest = false

        ZipInputStream(source.buffered()).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                runCatching {
                    when {
                        name == "manifest.json" -> {
                            sawManifest = true
                            zip.readBytes()
                        }

                        name.endsWith(".json") -> {
                            val category = Cat.byId(name.removeSuffix(".json"))
                            if (category != null && category in categories) {
                                val count =
                                    applyJsonToPrefs(
                                        JSONObject(String(zip.readBytes())),
                                        when (category) {
                                            Cat.UI -> uiPrefs(context)
                                            Cat.SETTINGS -> defaultPrefs(context)
                                            Cat.STATISTICS -> statsPrefs(context)
                                            else -> uiPrefs(context)
                                        },
                                    )
                                lines += "${context.getString(category.labelRes)}: $count"
                            } else {
                                zip.readBytes()
                            }
                        }

                        name.contains('/') -> {
                            val category = Cat.byId(name.substringBefore('/'))
                            if (category != null && category in categories) {
                                val fileName = name.substringAfter('/')
                                if (fileName.isNotBlank()) {
                                    val directory =
                                        when (category) {
                                            Cat.FONTS -> GauguinFonts.fontsDir(context)
                                            else -> context.filesDir
                                        }
                                    File(directory, File(fileName).name).writeBytes(zip.readBytes())
                                }
                            } else {
                                zip.readBytes()
                            }
                        }

                        else -> zip.readBytes()
                    }
                }.onFailure { errors += "$name: ${it.message ?: it.javaClass.simpleName}" }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        if (!sawManifest) errors += "no manifest.json — is this a 白い熊 GNU Gauguin backup?"
        // File corpora are summarised after the walk, so a folder shows one line, not one per file.
        listOf(Cat.FONTS, Cat.SAVED_GAMES).filter { it in categories }.forEach { category ->
            val label = context.getString(category.labelRes)
            if (lines.none { it.startsWith(label) }) lines += "$label: ok"
        }
        return ImportResult(lines, errors)
    }

    // ---- category storage -----------------------------------------------------------------------

    private fun uiPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(GauguinUiConfig.PREFS, Context.MODE_PRIVATE)

    private fun defaultPrefs(context: Context): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    private fun statsPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences("stats", Context.MODE_PRIVATE)

    private fun savedGameFiles(context: Context): List<File> =
        context.filesDir
            .listFiles { file -> file.isFile && file.name.startsWith("game_") }
            ?.toList()
            .orEmpty()

    // ---- zip helpers ----------------------------------------------------------------------------

    private fun writeJson(
        zip: ZipOutputStream,
        category: Cat,
        json: JSONObject,
    ) {
        zip.putNextEntry(ZipEntry("${category.id}.json"))
        zip.write(json.toString(2).toByteArray())
        zip.closeEntry()
    }

    private fun writeFiles(
        zip: ZipOutputStream,
        category: Cat,
        files: List<File>,
    ) {
        files.filter { it.isFile }.forEach { file ->
            zip.putNextEntry(ZipEntry("${category.id}/${file.name}"))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }

    /** Type-tagged dump, so an import can put every value back as the type it was stored as. */
    private fun prefsToJson(prefs: SharedPreferences): JSONObject {
        val root = JSONObject()
        prefs.all.forEach { (key, value) ->
            val entry =
                when (value) {
                    is Boolean -> JSONObject().put("t", "b").put("v", value)
                    is Int -> JSONObject().put("t", "i").put("v", value)
                    is Long -> JSONObject().put("t", "l").put("v", value)
                    is Float -> JSONObject().put("t", "f").put("v", value.toDouble())
                    is String -> JSONObject().put("t", "s").put("v", value)
                    is Set<*> -> JSONObject().put("t", "ss").put("v", JSONArray(value.map { it.toString() }))
                    else -> null
                }
            if (entry != null) root.put(key, entry)
        }
        return root
    }

    private fun applyJsonToPrefs(
        json: JSONObject,
        prefs: SharedPreferences,
    ): Int {
        val editor = prefs.edit()
        var applied = 0
        json.keys().forEach { key ->
            val entry = json.optJSONObject(key) ?: return@forEach
            when (entry.optString("t")) {
                "b" -> editor.putBoolean(key, entry.optBoolean("v"))
                "i" -> editor.putInt(key, entry.optInt("v"))
                "l" -> editor.putLong(key, entry.optLong("v"))
                "f" -> editor.putFloat(key, entry.optDouble("v").toFloat())
                "s" -> editor.putString(key, entry.optString("v"))
                "ss" -> {
                    val array = entry.optJSONArray("v") ?: return@forEach
                    editor.putStringSet(key, (0 until array.length()).map { array.optString(it) }.toSet())
                }

                else -> return@forEach
            }
            applied++
        }
        editor.apply()
        return applied
    }

    private fun appVersion(context: Context): String =
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
        }.getOrDefault("0")

    fun humanSize(bytes: Long): String {
        val units = listOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        return if (unit == 0) "$bytes B" else String.format(Locale.US, "%.1f %s", value, units[unit])
    }

    /**
     * Write a backup into [directory] **atomically**: everything goes to `<name>.part` and is renamed
     * only once the archive is closed and complete, and the partial is deleted on any failure. A
     * killed export must never leave a file that looks like a real backup.
     */
    fun exportToDirectory(
        context: Context,
        categories: Set<Cat>,
        directory: File,
        fileName: String = exportFileName(),
        onProgress: (Progress) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): File {
        if (!directory.exists()) directory.mkdirs()
        val target = File(directory, fileName)
        val partial = File(directory, "$fileName.part")
        try {
            partial.outputStream().use { export(context, categories, it, onProgress, isCancelled) }
            if (isCancelled()) throw InterruptedException("cancelled")
            if (!partial.renameTo(target)) throw IllegalStateException("could not rename ${partial.name}")
            return target
        } catch (e: Throwable) {
            partial.delete()
            throw e
        }
    }
}
