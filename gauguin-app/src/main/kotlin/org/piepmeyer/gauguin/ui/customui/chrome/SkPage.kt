package org.piepmeyer.gauguin.ui.customui.chrome

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.edit
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceViewHolder
import org.piepmeyer.gauguin.R

/**
 * Fork: the arrangement of the 白い熊 GNU Gauguin UI page — the order of its sections and which of
 * them are open. Two small lists in the fork's own preferences file, so they travel in the export
 * with the colours and fonts. Every section is folded by default; a section a
 * later build adds appears at its built place without disturbing a saved order.
 */
object SkPageState {
    const val ORDER_KEY = "gauguin_ui_section_order"
    const val OPEN_KEY = "gauguin_ui_sections_open"

    private fun list(
        context: Context,
        key: String,
    ): List<String> =
        context
            .getSharedPreferences(SkRoles.PREFS, Context.MODE_PRIVATE)
            .getString(key, null)
            ?.split(",")
            ?.filter { it.isNotEmpty() }
            .orEmpty()

    fun order(
        context: Context,
        built: List<String>,
    ): List<String> = merge(list(context, ORDER_KEY), built)

    /** Stored keys still present, in their saved order; every other built key at its built place */
    fun merge(
        saved: List<String>,
        built: List<String>,
    ): List<String> {
        val kept = saved.filter { it in built }.distinct()
        if (kept.isEmpty()) return built
        val result = kept.toMutableList()
        built.forEachIndexed { i, k ->
            if (k !in result) {
                val before = built.take(i).lastOrNull { it in result }
                result.add(if (before == null) 0 else result.indexOf(before) + 1, k)
            }
        }
        return result
    }

    fun setOrder(
        context: Context,
        order: List<String>,
    ) = context.getSharedPreferences(SkRoles.PREFS, Context.MODE_PRIVATE).edit { putString(ORDER_KEY, order.joinToString(",")) }

    fun resetOrder(context: Context) = context.getSharedPreferences(SkRoles.PREFS, Context.MODE_PRIVATE).edit { remove(ORDER_KEY) }

    fun isOpen(
        context: Context,
        key: String,
    ): Boolean = key in list(context, OPEN_KEY)

    fun setOpen(
        context: Context,
        key: String,
        open: Boolean,
    ) {
        val set = list(context, OPEN_KEY).toMutableSet()
        if (open) set.add(key) else set.remove(key)
        context.getSharedPreferences(SkRoles.PREFS, Context.MODE_PRIVATE).edit { putString(OPEN_KEY, set.joinToString(",")) }
    }
}

/**
 * A top-level section of the UI page: its heading folds and unfolds its rows. Its key is
 * `sk_sec_<section key>`.
 */
class SkSectionCategory(
    context: Context,
    attrs: AttributeSet? = null,
) : PreferenceCategory(context, attrs) {
    /** Set by the page: a tap on the heading */
    var onToggle: ((SkSectionCategory) -> Unit)? = null

    /** The section this heading stands for: its icon and colour are shared with its chip */
    var section: SkSection = SkSection.FOUNDATION

    val sectionKey: String get() = key.removePrefix(PREFIX)

    init {
        layoutResource = R.layout.sk_preference_section
    }

    fun refresh() = notifyChanged()

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val open = SkPageState.isOpen(context, sectionKey)
        val heading = SkRoles[SkRole.HEADING]
        val title = holder.findViewById(android.R.id.title) as? TextView
        title?.setTextColor(heading)
        holder.findViewById(R.id.sk_section_underline)?.setBackgroundColor(heading)
        holder.findViewById(R.id.sk_section_divider)?.setBackgroundColor(SkRoles.mix(SkRoles[SkRole.BACKGROUND], SkRoles[SkRole.ACCENT], 0.7f))
        val size = maxOf(Pills.dp(context, 18f), ((title?.textSize ?: 0f) * 1.25f).toInt())
        (holder.findViewById(R.id.sk_section_icon) as? ImageView)?.apply {
            setImageDrawable(SkIcons.drawable(context, section.icon, SkRoles[section.color], 24f))
            layoutParams =
                layoutParams.apply {
                    width = size
                    height = size
                }
        }
        (holder.findViewById(R.id.sk_section_chevron) as? ImageView)?.apply {
            setImageDrawable(SkIcons.drawable(context, SkIcons.Icon.CHEVRON, heading, 20f))
            rotation = if (open) 90f else 0f
        }
        holder.itemView.isClickable = true
        holder.itemView.setOnClickListener { onToggle?.invoke(this) }
    }

    companion object {
        const val PREFIX = "sk_sec_"
    }
}

/** The chip index under the settings link: one chip per section, in the sections' order */
class SkIndexPreference(
    context: Context,
    private val items: () -> List<SectionIndex.Item>,
    private val listener: SectionIndex.Listener,
) : Preference(context) {
    init {
        layoutResource = R.layout.sk_preference_index
        isPersistent = false
        isSelectable = false
        key = "sk_ui_index"
    }

    fun refresh() = notifyChanged()

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val frame = holder.itemView as? ViewGroup ?: return
        frame.removeAllViews()
        frame.addView(SectionIndex.build(context, items(), listener))
    }
}

/** One colour role: its label, its hex value beneath, and its swatch at the right */
class SkRolePreference(
    context: Context,
    val role: SkRole,
) : Preference(context) {
    init {
        layoutResource = R.layout.sk_preference_row
        widgetLayoutResource = R.layout.sk_preference_widget_swatch
        isPersistent = false
        key = "sk_ui_role_" + role.key
        title = context.getString(role.labelRes)
        icon = SkIcons.drawable(context, SkIcons.Icon.PALETTE, SkRoles[SkRole.SETTINGS_ICON], 22f)
        extras.putBoolean(SkPreferences.EXTRA_CONTROL, true)
        refresh()
    }

    fun refresh() {
        val color = SkRoles[role]
        summary = String.format("#%08X", color) + if (SkRoles.isSet(role)) "" else "  ·  " + context.getString(R.string.sk_default)
        notifyChanged()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.findViewById(R.id.sk_swatch)?.background =
            GradientDrawable().apply {
                cornerRadius = Pills.dp(context, 6f).toFloat()
                color = ColorStateList.valueOf(SkRoles[role])
                setStroke(Pills.dp(context, 1.25f), SkRoles[SkRole.OUTLINE_VARIANT])
            }
    }
}
