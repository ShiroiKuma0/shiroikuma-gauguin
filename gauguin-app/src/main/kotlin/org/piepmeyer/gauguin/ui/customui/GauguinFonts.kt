package org.piepmeyer.gauguin.ui.customui

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import org.piepmeyer.gauguin.R
import java.io.File

/** One pickable font: [fileName] is "" (system), a `@sentinel`, or an imported file name. */
data class FontOption(
    val displayName: String,
    val fileName: String,
)

/**
 * The fork's font registry, mirroring the sister repos: the built-in families plus any `.ttf`/`.otf`
 * 白い熊 imports through the document picker into the app's private `files/fonts` directory.
 * Typefaces are cached by file name; a missing or corrupt file falls back to the default rather than
 * crashing a screen mid-draw.
 */
object GauguinFonts {
    const val SYSTEM = ""
    const val MONOSPACE = "@monospace"
    const val SERIF = "@serif"
    const val SANS = "@sans"

    private val EXTENSIONS = setOf("ttf", "otf")
    private val cache = HashMap<String, Typeface>()

    fun fontsDir(context: Context): File = File(context.filesDir, "fonts").apply { if (!exists()) mkdirs() }

    /** Built-ins + every imported font, sorted by name. */
    fun availableFonts(context: Context): List<FontOption> {
        val options =
            mutableListOf(
                FontOption(context.getString(R.string.gauguin_ui_font_system), SYSTEM),
                FontOption(context.getString(R.string.gauguin_ui_font_sans), SANS),
                FontOption(context.getString(R.string.gauguin_ui_font_serif), SERIF),
                FontOption(context.getString(R.string.gauguin_ui_font_monospace), MONOSPACE),
            )
        fontsDir(context)
            .listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in EXTENSIONS }
            ?.sortedBy { it.name.lowercase() }
            ?.forEach { options.add(FontOption(it.nameWithoutExtension, it.name)) }
        return options
    }

    fun displayName(
        context: Context,
        family: String,
    ): String =
        when (family) {
            SYSTEM -> context.getString(R.string.gauguin_ui_font_system)
            SANS -> context.getString(R.string.gauguin_ui_font_sans)
            SERIF -> context.getString(R.string.gauguin_ui_font_serif)
            MONOSPACE -> context.getString(R.string.gauguin_ui_font_monospace)
            else -> File(family).nameWithoutExtension
        }

    /** The base typeface for a stored family value (cached). */
    fun typeface(
        context: Context,
        family: String,
    ): Typeface =
        when (family) {
            SYSTEM -> Typeface.DEFAULT
            SANS -> Typeface.SANS_SERIF
            SERIF -> Typeface.SERIF
            MONOSPACE -> Typeface.MONOSPACE
            else ->
                cache.getOrPut(family) {
                    runCatching { Typeface.createFromFile(File(fontsDir(context), family)) }
                        .getOrDefault(Typeface.DEFAULT)
                }
        }

    /**
     * The family combined with a numeric weight (100..900; 0 = the family's own) and optional italic.
     * Below API 28 there is no numeric weight, so anything from semi-bold up renders bold.
     */
    fun typeface(
        context: Context,
        family: String,
        weight: Int,
        italic: Boolean = false,
    ): Typeface {
        val base = typeface(context, family)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (weight <= 0) {
                if (italic) Typeface.create(base, Typeface.ITALIC) else base
            } else {
                Typeface.create(base, weight.coerceIn(1, 1000), italic)
            }
        } else {
            val style =
                when {
                    weight >= 600 && italic -> Typeface.BOLD_ITALIC
                    weight >= 600 -> Typeface.BOLD
                    italic -> Typeface.ITALIC
                    else -> Typeface.NORMAL
                }
            Typeface.create(base, style)
        }
    }

    /** Copy a picked font into the private fonts dir; returns its file name, or null on failure. */
    fun importFont(
        context: Context,
        uri: Uri,
    ): String? {
        val name = fileName(context, uri) ?: return null
        if (name.substringAfterLast('.', "").lowercase() !in EXTENSIONS) return null
        return runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            File(fontsDir(context), name).writeBytes(bytes)
            cache.remove(name)
            name
        }.getOrNull()
    }

    private fun fileName(
        context: Context,
        uri: Uri,
    ): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index)?.let { return it }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }
}
