package org.piepmeyer.gauguin.ui.customui

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.piepmeyer.gauguin.R

/**
 * The font picker: a black-yellow list of the built-in families plus every imported font, **each row
 * rendered in its own glyphs** so the choice is made by looking rather than by reading a name. The
 * neutral button opens the document picker to import a new `.ttf` / `.otf`.
 */
object FontPickerDialog {
    fun show(
        context: Context,
        titleRes: Int,
        current: String,
        onPick: (String) -> Unit,
        onImport: () -> Unit,
    ) {
        val fonts = GauguinFonts.availableFonts(context)
        val config = GauguinUiConfig(context)
        val accent = config.accentColor
        val density = context.resources.displayMetrics.density

        val adapter =
            object : BaseAdapter() {
                override fun getCount(): Int = fonts.size

                override fun getItem(position: Int): FontOption = fonts[position]

                override fun getItemId(position: Int): Long = position.toLong()

                override fun getView(
                    position: Int,
                    convertView: View?,
                    parent: ViewGroup,
                ): View {
                    val option = fonts[position]
                    val view = (convertView as? TextView) ?: TextView(context)
                    val selected = option.fileName == current
                    // Sample text after the name so an imported display face shows real glyphs even
                    // when its own name is short — Gauguin is a number game, so digits matter.
                    view.text = (if (selected) "✓  " else "")+ option.displayName + "   1234567890"
                    view.typeface = GauguinFonts.typeface(context, option.fileName)
                    view.setTextColor(accent)
                    view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                    val padH = (20 * density).toInt()
                    val padV = (10 * density).toInt()
                    view.setPadding(padH, padV, padH, padV)
                    return view
                }
            }

        val dialog =
            AlertDialog
                .Builder(context, R.style.Theme_Gauguin_ShiroikumaDialog)
                .setTitle(titleRes)
                .setAdapter(adapter) { _, position -> onPick(fonts[position].fileName) }
                .setNeutralButton(R.string.gauguin_ui_font_import) { _, _ -> onImport() }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
        GauguinDialogs.style(dialog)
        dialog.show()
    }
}
