package org.piepmeyer.gauguin.ui.customui

import android.content.Context
import android.content.SharedPreferences

/**
 * Every knob of the 白い熊 GNU Gauguin UI, backed by one SharedPreferences file.
 *
 * The knobs are declared once in [SPECS] — id, kind, default, and the category they belong to — and
 * the rest of the fork reads them through the typed accessors below. Declaring them in one place is
 * what lets the settings page, the backup export and "reset to defaults" all be generated instead of
 * hand-maintained: add a row to [SPECS] and it shows up in all three.
 *
 * Defaults are the house look: pure black background, pure yellow `#FFFF00` text and borders — NOT
 * Material's amber. A fresh install is black-and-yellow with no user action.
 */
class GauguinUiConfig(
    context: Context,
) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---- typed access -------------------------------------------------------------------------

    fun int(key: String): Int = prefs.getInt(key, defaultInt(key))

    fun bool(key: String): Boolean = prefs.getBoolean(key, defaultBool(key))

    fun string(key: String): String = prefs.getString(key, defaultString(key)) ?: defaultString(key)

    fun setInt(
        key: String,
        value: Int,
    ) = prefs.edit().putInt(key, value).apply()

    fun setBool(
        key: String,
        value: Boolean,
    ) = prefs.edit().putBoolean(key, value).apply()

    fun setString(
        key: String,
        value: String,
    ) = prefs.edit().putString(key, value).apply()

    /** Drop every knob of [category] (or all of them when null) back to its declared default. */
    fun resetToDefaults(category: UiCategory? = null) {
        val editor = prefs.edit()
        SPECS.filter { category == null || it.category == category }.forEach { editor.remove(it.key) }
        editor.apply()
    }

    /** The whole config as a flat map, for the backup export. Only knobs that were actually set. */
    fun toMap(): Map<String, Any?> =
        SPECS
            .filter { prefs.contains(it.key) }
            .associate { spec ->
                spec.key to
                    when (spec.kind) {
                        Kind.COLOR, Kind.SLIDER -> prefs.getInt(spec.key, 0)
                        Kind.TOGGLE -> prefs.getBoolean(spec.key, false)
                        Kind.FONT -> prefs.getString(spec.key, null)
                    }
            }

    /** Merge a backup map back in, key by key. Unknown or wrongly-typed keys are skipped. */
    fun applyMap(values: Map<String, Any?>): Int {
        val editor = prefs.edit()
        var applied = 0
        values.forEach { (key, value) ->
            val spec = SPECS.firstOrNull { it.key == key } ?: return@forEach
            when (spec.kind) {
                Kind.COLOR, Kind.SLIDER -> (value as? Number)?.let { editor.putInt(key, it.toInt()); applied++ }
                Kind.TOGGLE -> (value as? Boolean)?.let { editor.putBoolean(key, it); applied++ }
                Kind.FONT -> (value as? String)?.let { editor.putString(key, it); applied++ }
            }
        }
        editor.apply()
        return applied
    }

    // ---- the knobs most of the fork actually reads --------------------------------------------

    val customUiActive: Boolean get() = bool(ENABLED)

    val backgroundColor: Int get() = int(BACKGROUND)
    val surfaceColor: Int get() = int(SURFACE)
    val accentColor: Int get() = int(ACCENT)
    val textColor: Int get() = int(TEXT)
    val borderColor: Int get() = int(BORDER_COLOR)
    val borderWidthDp: Int get() = int(BORDER_WIDTH)
    val cornerRadiusDp: Int get() = int(CORNER)

    val fontFamily: String get() = string(FONT_FAMILY)
    val fontWeight: Int get() = int(FONT_WEIGHT)
    val fontScalePct: Int get() = int(FONT_SIZE)
    val fontItalic: Boolean get() = bool(FONT_ITALIC)

    val dialogBorderColor: Int get() = int(DIALOG_BORDER_COLOR)
    val dialogBorderWidthDp: Int get() = int(DIALOG_BORDER_WIDTH)
    val dialogCornerDp: Int get() = int(DIALOG_CORNER)
    val dialogBackgroundColor: Int get() = int(DIALOG_BACKGROUND)

    /** Where the Export/Import page writes backups. Device-local: never itself exported. */
    var exportDirectory: String?
        get() = prefs.getString(EXPORT_DIR, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().putString(EXPORT_DIR, value.orEmpty()).apply()
        }

    private fun defaultInt(key: String): Int = SPECS.first { it.key == key }.default as Int

    private fun defaultBool(key: String): Boolean = SPECS.first { it.key == key }.default as Boolean

    private fun defaultString(key: String): String = SPECS.first { it.key == key }.default as String

    enum class Kind { COLOR, SLIDER, TOGGLE, FONT }

    /**
     * One knob. [min]/[max] apply to [Kind.SLIDER] only; every slider that controls a thickness or a
     * radius bottoms out at 0, per 白い熊's rule that borders must be able to go away entirely.
     */
    data class Spec(
        val key: String,
        val kind: Kind,
        val default: Any,
        val category: UiCategory,
        val labelRes: Int,
        val min: Int = 0,
        val max: Int = 100,
        val sub: Boolean = false,
    )

    companion object {
        const val PREFS = "gauguin_ui"

        private const val EXPORT_DIR = "gauguin_ui_export_dir"

        // global
        const val ENABLED = "gauguin_ui_enabled"
        const val BACKGROUND = "gauguin_ui_background"
        const val SURFACE = "gauguin_ui_surface"
        const val ACCENT = "gauguin_ui_accent"
        const val TEXT = "gauguin_ui_text"
        const val BORDER_COLOR = "gauguin_ui_border_color"
        const val BORDER_WIDTH = "gauguin_ui_border_width"
        const val CORNER = "gauguin_ui_corner"
        const val FONT_FAMILY = "gauguin_ui_font_family"
        const val FONT_WEIGHT = "gauguin_ui_font_weight"
        const val FONT_SIZE = "gauguin_ui_font_size"
        const val FONT_ITALIC = "gauguin_ui_font_italic"

        // grid — surface
        const val GRID_BACKGROUND = "gauguin_ui_grid_background"
        const val GRID_CELL_BACKGROUND = "gauguin_ui_grid_cell_background"
        const val GRID_SELECTED_BACKGROUND = "gauguin_ui_grid_selected_background"
        const val GRID_CORNER = "gauguin_ui_grid_corner"

        // grid — lines
        const val CAGE_BORDER_COLOR = "gauguin_ui_cage_border_color"
        const val CAGE_BORDER_WIDTH = "gauguin_ui_cage_border_width"
        const val CELL_BORDER_COLOR = "gauguin_ui_cell_border_color"
        const val CELL_BORDER_WIDTH = "gauguin_ui_cell_border_width"

        // grid — text
        const val VALUE_COLOR = "gauguin_ui_value_color"
        const val VALUE_SELECTED_COLOR = "gauguin_ui_value_selected_color"
        const val VALUE_SIZE = "gauguin_ui_value_size"
        const val VALUE_FONT = "gauguin_ui_value_font"
        const val VALUE_WEIGHT = "gauguin_ui_value_weight"
        const val CAGE_TEXT_COLOR = "gauguin_ui_cage_text_color"
        const val CAGE_TEXT_SIZE = "gauguin_ui_cage_text_size"
        const val CAGE_TEXT_FONT = "gauguin_ui_cage_text_font"
        const val POSSIBLES_COLOR = "gauguin_ui_possibles_color"
        const val POSSIBLES_SIZE = "gauguin_ui_possibles_size"
        const val POSSIBLES_FONT = "gauguin_ui_possibles_font"

        // grid — states
        const val ERROR_COLOR = "gauguin_ui_error_color"
        const val WARNING_COLOR = "gauguin_ui_warning_color"
        const val CHEATED_COLOR = "gauguin_ui_cheated_color"
        const val LAST_MODIFIED_COLOR = "gauguin_ui_last_modified_color"

        // keypad
        const val KEY_BACKGROUND = "gauguin_ui_key_background"
        const val KEY_TEXT_COLOR = "gauguin_ui_key_text_color"
        const val KEY_TEXT_SIZE = "gauguin_ui_key_text_size"
        const val KEY_FONT = "gauguin_ui_key_font"
        const val KEY_WEIGHT = "gauguin_ui_key_weight"
        const val KEY_BORDER_COLOR = "gauguin_ui_key_border_color"
        const val KEY_BORDER_WIDTH = "gauguin_ui_key_border_width"
        const val KEY_CORNER = "gauguin_ui_key_corner"

        // top panel
        const val TOP_BACKGROUND = "gauguin_ui_top_background"
        const val TOP_FOREGROUND = "gauguin_ui_top_foreground"
        const val TOP_TEXT_SIZE = "gauguin_ui_top_text_size"
        const val TOP_FONT = "gauguin_ui_top_font"
        const val TOP_SHOW_LOGO = "gauguin_ui_top_show_logo"

        // navigation drawer
        const val DRAWER_BACKGROUND = "gauguin_ui_drawer_background"
        const val DRAWER_TEXT_COLOR = "gauguin_ui_drawer_text_color"
        const val DRAWER_TEXT_SIZE = "gauguin_ui_drawer_text_size"
        const val DRAWER_FONT = "gauguin_ui_drawer_font"
        const val DRAWER_ICON_SIZE = "gauguin_ui_drawer_icon_size"
        const val DRAWER_SHOW_HEADER = "gauguin_ui_drawer_show_header"

        // dialogs
        const val DIALOG_BACKGROUND = "gauguin_ui_dialog_background"
        const val DIALOG_TEXT_COLOR = "gauguin_ui_dialog_text_color"
        const val DIALOG_BORDER_COLOR = "gauguin_ui_dialog_border_color"
        const val DIALOG_BORDER_WIDTH = "gauguin_ui_dialog_border_width"
        const val DIALOG_CORNER = "gauguin_ui_dialog_corner"

        // lists & statistics
        const val LIST_TEXT_COLOR = "gauguin_ui_list_text_color"
        const val LIST_TEXT_SIZE = "gauguin_ui_list_text_size"
        const val LIST_FONT = "gauguin_ui_list_font"
        const val LIST_DIVIDER_COLOR = "gauguin_ui_list_divider_color"
        const val LIST_DIVIDER_WIDTH = "gauguin_ui_list_divider_width"
        const val LIST_ROW_PADDING = "gauguin_ui_list_row_padding"

        const val BLACK = 0xFF000000.toInt()
        const val YELLOW = 0xFFFFFF00.toInt()
        private const val YELLOW_DIM = 0xFFCCCC66.toInt()
        private const val RED = 0xFFFF5555.toInt()
        private const val ORANGE = 0xFFFFAA00.toInt()

        /**
         * Every knob, in page order. The settings page walks this list, so the order here IS the order
         * on screen; [Spec.sub] marks a row that belongs under the preceding sub-heading.
         */
        val SPECS: List<Spec> =
            listOf(
                // --- global -------------------------------------------------------------------
                Spec(ENABLED, Kind.TOGGLE, true, UiCategory.GLOBAL, org.piepmeyer.gauguin.R.string.gauguin_ui_enabled),
                Spec(BACKGROUND, Kind.COLOR, BLACK, UiCategory.GLOBAL, org.piepmeyer.gauguin.R.string.gauguin_ui_background, sub = true),
                Spec(SURFACE, Kind.COLOR, BLACK, UiCategory.GLOBAL, org.piepmeyer.gauguin.R.string.gauguin_ui_surface, sub = true),
                Spec(ACCENT, Kind.COLOR, YELLOW, UiCategory.GLOBAL, org.piepmeyer.gauguin.R.string.gauguin_ui_accent, sub = true),
                Spec(TEXT, Kind.COLOR, YELLOW, UiCategory.GLOBAL, org.piepmeyer.gauguin.R.string.gauguin_ui_text, sub = true),
                Spec(BORDER_COLOR, Kind.COLOR, YELLOW, UiCategory.GLOBAL, org.piepmeyer.gauguin.R.string.gauguin_ui_border_color, sub = true),
                Spec(BORDER_WIDTH, Kind.SLIDER, 2, UiCategory.GLOBAL, org.piepmeyer.gauguin.R.string.gauguin_ui_border_width, 0, 16, sub = true),
                Spec(CORNER, Kind.SLIDER, 12, UiCategory.GLOBAL, org.piepmeyer.gauguin.R.string.gauguin_ui_corner, 0, 48, sub = true),
                Spec(FONT_FAMILY, Kind.FONT, "", UiCategory.GLOBAL, org.piepmeyer.gauguin.R.string.gauguin_ui_font_family, sub = true),
                Spec(FONT_WEIGHT, Kind.SLIDER, 0, UiCategory.GLOBAL, org.piepmeyer.gauguin.R.string.gauguin_ui_font_weight, 0, 900, sub = true),
                Spec(FONT_SIZE, Kind.SLIDER, 100, UiCategory.GLOBAL, org.piepmeyer.gauguin.R.string.gauguin_ui_font_size, 50, 250, sub = true),
                Spec(FONT_ITALIC, Kind.TOGGLE, false, UiCategory.GLOBAL, org.piepmeyer.gauguin.R.string.gauguin_ui_font_italic, sub = true),
                // --- grid ---------------------------------------------------------------------
                Spec(GRID_BACKGROUND, Kind.COLOR, BLACK, UiCategory.GRID, org.piepmeyer.gauguin.R.string.gauguin_ui_grid_background, sub = true),
                Spec(GRID_CELL_BACKGROUND, Kind.COLOR, BLACK, UiCategory.GRID, org.piepmeyer.gauguin.R.string.gauguin_ui_grid_cell_background, sub = true),
                Spec(GRID_SELECTED_BACKGROUND, Kind.COLOR, 0xFF332F00.toInt(), UiCategory.GRID, org.piepmeyer.gauguin.R.string.gauguin_ui_grid_selected_background, sub = true),
                Spec(GRID_CORNER, Kind.SLIDER, 0, UiCategory.GRID, org.piepmeyer.gauguin.R.string.gauguin_ui_grid_corner, 0, 32, sub = true),
                Spec(CAGE_BORDER_COLOR, Kind.COLOR, YELLOW, UiCategory.GRID, org.piepmeyer.gauguin.R.string.gauguin_ui_cage_border_color, sub = true),
                Spec(CAGE_BORDER_WIDTH, Kind.SLIDER, 4, UiCategory.GRID, org.piepmeyer.gauguin.R.string.gauguin_ui_cage_border_width, 0, 16, sub = true),
                Spec(CELL_BORDER_COLOR, Kind.COLOR, YELLOW_DIM, UiCategory.GRID, org.piepmeyer.gauguin.R.string.gauguin_ui_cell_border_color, sub = true),
                Spec(CELL_BORDER_WIDTH, Kind.SLIDER, 1, UiCategory.GRID, org.piepmeyer.gauguin.R.string.gauguin_ui_cell_border_width, 0, 12, sub = true),
                Spec(VALUE_COLOR, Kind.COLOR, YELLOW, UiCategory.GRID, org.piepmeyer.gauguin.R.string.gauguin_ui_value_color, sub = true),
                Spec(VALUE_SELECTED_COLOR, Kind.COLOR, YELLOW, UiCategory.GRID, org.piepmeyer.gauguin.R.string.gauguin_ui_value_selected_color, sub = true),
                Spec(VALUE_SIZE, Kind.SLIDER, 100, UiCategory.GRID, org.piepmeyer.gauguin.R.string.gauguin_ui_value_size, 40, 250, sub = true),
                Spec(VALUE_FONT, Kind.FONT, "", UiCategory.GRID, org.piepmeyer.gauguin.R.string.gauguin_ui_value_font, sub = true),
                Spec(VALUE_WEIGHT, Kind.SLIDER, 0, UiCategory.GRID, org.piepmeyer.gauguin.R.string.gauguin_ui_value_weight, 0, 900, sub = true),
                Spec(CAGE_TEXT_COLOR, Kind.COLOR, YELLOW, UiCategory.GRID, org.piepmeyer.gauguin.R.string.gauguin_ui_cage_text_color, sub = true),
                Spec(CAGE_TEXT_SIZE, Kind.SLIDER, 100, UiCategory.GRID, org.piepmeyer.gauguin.R.string.gauguin_ui_cage_text_size, 40, 250, sub = true),
                Spec(CAGE_TEXT_FONT, Kind.FONT, "", UiCategory.GRID, org.piepmeyer.gauguin.R.string.gauguin_ui_cage_text_font, sub = true),
                Spec(POSSIBLES_COLOR, Kind.COLOR, YELLOW_DIM, UiCategory.GRID, org.piepmeyer.gauguin.R.string.gauguin_ui_possibles_color, sub = true),
                Spec(POSSIBLES_SIZE, Kind.SLIDER, 100, UiCategory.GRID, org.piepmeyer.gauguin.R.string.gauguin_ui_possibles_size, 40, 250, sub = true),
                Spec(POSSIBLES_FONT, Kind.FONT, "", UiCategory.GRID, org.piepmeyer.gauguin.R.string.gauguin_ui_possibles_font, sub = true),
                Spec(ERROR_COLOR, Kind.COLOR, RED, UiCategory.GRID, org.piepmeyer.gauguin.R.string.gauguin_ui_error_color, sub = true),
                Spec(WARNING_COLOR, Kind.COLOR, ORANGE, UiCategory.GRID, org.piepmeyer.gauguin.R.string.gauguin_ui_warning_color, sub = true),
                Spec(CHEATED_COLOR, Kind.COLOR, 0xFF806000.toInt(), UiCategory.GRID, org.piepmeyer.gauguin.R.string.gauguin_ui_cheated_color, sub = true),
                Spec(LAST_MODIFIED_COLOR, Kind.COLOR, 0xFF1A1A00.toInt(), UiCategory.GRID, org.piepmeyer.gauguin.R.string.gauguin_ui_last_modified_color, sub = true),
                // --- keypad -------------------------------------------------------------------
                Spec(KEY_BACKGROUND, Kind.COLOR, BLACK, UiCategory.KEYPAD, org.piepmeyer.gauguin.R.string.gauguin_ui_key_background, sub = true),
                Spec(KEY_TEXT_COLOR, Kind.COLOR, YELLOW, UiCategory.KEYPAD, org.piepmeyer.gauguin.R.string.gauguin_ui_key_text_color, sub = true),
                Spec(KEY_TEXT_SIZE, Kind.SLIDER, 100, UiCategory.KEYPAD, org.piepmeyer.gauguin.R.string.gauguin_ui_key_text_size, 50, 250, sub = true),
                Spec(KEY_FONT, Kind.FONT, "", UiCategory.KEYPAD, org.piepmeyer.gauguin.R.string.gauguin_ui_key_font, sub = true),
                Spec(KEY_WEIGHT, Kind.SLIDER, 0, UiCategory.KEYPAD, org.piepmeyer.gauguin.R.string.gauguin_ui_key_weight, 0, 900, sub = true),
                Spec(KEY_BORDER_COLOR, Kind.COLOR, YELLOW, UiCategory.KEYPAD, org.piepmeyer.gauguin.R.string.gauguin_ui_key_border_color, sub = true),
                Spec(KEY_BORDER_WIDTH, Kind.SLIDER, 2, UiCategory.KEYPAD, org.piepmeyer.gauguin.R.string.gauguin_ui_key_border_width, 0, 12, sub = true),
                Spec(KEY_CORNER, Kind.SLIDER, 10, UiCategory.KEYPAD, org.piepmeyer.gauguin.R.string.gauguin_ui_key_corner, 0, 40, sub = true),
                // --- top panel ----------------------------------------------------------------
                Spec(TOP_BACKGROUND, Kind.COLOR, BLACK, UiCategory.TOP_PANEL, org.piepmeyer.gauguin.R.string.gauguin_ui_top_background, sub = true),
                Spec(TOP_FOREGROUND, Kind.COLOR, YELLOW, UiCategory.TOP_PANEL, org.piepmeyer.gauguin.R.string.gauguin_ui_top_foreground, sub = true),
                Spec(TOP_TEXT_SIZE, Kind.SLIDER, 100, UiCategory.TOP_PANEL, org.piepmeyer.gauguin.R.string.gauguin_ui_top_text_size, 50, 250, sub = true),
                Spec(TOP_FONT, Kind.FONT, "", UiCategory.TOP_PANEL, org.piepmeyer.gauguin.R.string.gauguin_ui_top_font, sub = true),
                Spec(TOP_SHOW_LOGO, Kind.TOGGLE, true, UiCategory.TOP_PANEL, org.piepmeyer.gauguin.R.string.gauguin_ui_top_show_logo, sub = true),
                // --- drawer -------------------------------------------------------------------
                Spec(DRAWER_BACKGROUND, Kind.COLOR, BLACK, UiCategory.DRAWER, org.piepmeyer.gauguin.R.string.gauguin_ui_drawer_background, sub = true),
                Spec(DRAWER_TEXT_COLOR, Kind.COLOR, YELLOW, UiCategory.DRAWER, org.piepmeyer.gauguin.R.string.gauguin_ui_drawer_text_color, sub = true),
                Spec(DRAWER_TEXT_SIZE, Kind.SLIDER, 100, UiCategory.DRAWER, org.piepmeyer.gauguin.R.string.gauguin_ui_drawer_text_size, 50, 250, sub = true),
                Spec(DRAWER_FONT, Kind.FONT, "", UiCategory.DRAWER, org.piepmeyer.gauguin.R.string.gauguin_ui_drawer_font, sub = true),
                Spec(DRAWER_ICON_SIZE, Kind.SLIDER, 24, UiCategory.DRAWER, org.piepmeyer.gauguin.R.string.gauguin_ui_drawer_icon_size, 0, 64, sub = true),
                Spec(DRAWER_SHOW_HEADER, Kind.TOGGLE, true, UiCategory.DRAWER, org.piepmeyer.gauguin.R.string.gauguin_ui_drawer_show_header, sub = true),
                // --- dialogs ------------------------------------------------------------------
                Spec(DIALOG_BACKGROUND, Kind.COLOR, BLACK, UiCategory.DIALOG, org.piepmeyer.gauguin.R.string.gauguin_ui_dialog_background, sub = true),
                Spec(DIALOG_TEXT_COLOR, Kind.COLOR, YELLOW, UiCategory.DIALOG, org.piepmeyer.gauguin.R.string.gauguin_ui_dialog_text_color, sub = true),
                Spec(DIALOG_BORDER_COLOR, Kind.COLOR, YELLOW, UiCategory.DIALOG, org.piepmeyer.gauguin.R.string.gauguin_ui_dialog_border_color, sub = true),
                Spec(DIALOG_BORDER_WIDTH, Kind.SLIDER, 2, UiCategory.DIALOG, org.piepmeyer.gauguin.R.string.gauguin_ui_dialog_border_width, 0, 16, sub = true),
                Spec(DIALOG_CORNER, Kind.SLIDER, 16, UiCategory.DIALOG, org.piepmeyer.gauguin.R.string.gauguin_ui_dialog_corner, 0, 48, sub = true),
                // --- lists --------------------------------------------------------------------
                Spec(LIST_TEXT_COLOR, Kind.COLOR, YELLOW, UiCategory.LISTS, org.piepmeyer.gauguin.R.string.gauguin_ui_list_text_color, sub = true),
                Spec(LIST_TEXT_SIZE, Kind.SLIDER, 100, UiCategory.LISTS, org.piepmeyer.gauguin.R.string.gauguin_ui_list_text_size, 50, 250, sub = true),
                Spec(LIST_FONT, Kind.FONT, "", UiCategory.LISTS, org.piepmeyer.gauguin.R.string.gauguin_ui_list_font, sub = true),
                Spec(LIST_DIVIDER_COLOR, Kind.COLOR, YELLOW_DIM, UiCategory.LISTS, org.piepmeyer.gauguin.R.string.gauguin_ui_list_divider_color, sub = true),
                Spec(LIST_DIVIDER_WIDTH, Kind.SLIDER, 1, UiCategory.LISTS, org.piepmeyer.gauguin.R.string.gauguin_ui_list_divider_width, 0, 12, sub = true),
                Spec(LIST_ROW_PADDING, Kind.SLIDER, 6, UiCategory.LISTS, org.piepmeyer.gauguin.R.string.gauguin_ui_list_row_padding, 0, 32, sub = true),
            )
    }
}

/** The page's top-level sections. Order here is the order on the settings page and in the backup. */
enum class UiCategory(
    val id: String,
    val headingRes: Int,
    val subHeadingRes: Int,
) {
    GLOBAL(
        "ui.global",
        org.piepmeyer.gauguin.R.string.gauguin_ui_cat_global,
        org.piepmeyer.gauguin.R.string.gauguin_ui_sub_global,
    ),
    GRID(
        "ui.grid",
        org.piepmeyer.gauguin.R.string.gauguin_ui_cat_grid,
        org.piepmeyer.gauguin.R.string.gauguin_ui_sub_grid,
    ),
    KEYPAD(
        "ui.keypad",
        org.piepmeyer.gauguin.R.string.gauguin_ui_cat_keypad,
        org.piepmeyer.gauguin.R.string.gauguin_ui_sub_keypad,
    ),
    TOP_PANEL(
        "ui.toppanel",
        org.piepmeyer.gauguin.R.string.gauguin_ui_cat_top,
        org.piepmeyer.gauguin.R.string.gauguin_ui_sub_top,
    ),
    DRAWER(
        "ui.drawer",
        org.piepmeyer.gauguin.R.string.gauguin_ui_cat_drawer,
        org.piepmeyer.gauguin.R.string.gauguin_ui_sub_drawer,
    ),
    DIALOG(
        "ui.dialog",
        org.piepmeyer.gauguin.R.string.gauguin_ui_cat_dialog,
        org.piepmeyer.gauguin.R.string.gauguin_ui_sub_dialog,
    ),
    LISTS(
        "ui.lists",
        org.piepmeyer.gauguin.R.string.gauguin_ui_cat_lists,
        org.piepmeyer.gauguin.R.string.gauguin_ui_sub_lists,
    ),
}
