package org.piepmeyer.gauguin.ui.customui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import org.piepmeyer.gauguin.R

/**
 * A settings row that carries a bordered colour swatch on the right, so the current colour is
 * readable at a glance without opening the picker. The summary shows the ARGB hex.
 */
class ColorSwatchPreference(
    context: Context,
) : Preference(context) {
    var color: Int = GauguinUiConfig.BLACK
        set(value) {
            field = value
            summary = String.format("#%08X", value)
            notifyChanged()
        }

    init {
        layoutResource = R.layout.preference_item_gauguin
        widgetLayoutResource = R.layout.preference_color_swatch_gauguin
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val swatch = holder.findViewById(R.id.color_swatch) ?: return
        val density = context.resources.displayMetrics.density
        // Capture before apply{}, where `color` would otherwise resolve to GradientDrawable.color.
        val fillColor = color
        swatch.background =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(fillColor)
                setStroke(
                    (1.5f * density).toInt(),
                    ContextCompat.getColor(context, R.color.shiroikuma_yellow),
                )
                cornerRadius = 4f * density
            }
    }
}
