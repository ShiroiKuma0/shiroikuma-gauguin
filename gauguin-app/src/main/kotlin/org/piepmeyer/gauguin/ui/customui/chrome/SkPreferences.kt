package org.piepmeyer.gauguin.ui.customui.chrome

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.preference.DialogPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceGroupAdapter
import androidx.preference.SeekBarPreference
import androidx.preference.TwoStatePreference
import androidx.recyclerview.widget.RecyclerView
import org.piepmeyer.gauguin.R
import org.piepmeyer.gauguin.ui.customui.chrome.SkIcons.Does
import org.piepmeyer.gauguin.ui.customui.chrome.SkIcons.Icon

/**
 * Fork: the rows of every settings screen — upstream's and the 白い熊 GNU Gauguin UI page alike.
 *
 *  - [decorate] gives every row an icon coloured by what the row does, told from its key and,
 *    failing that, from the kind of row it is. A row that brings an icon keeps it.
 *  - [styleRow] makes a row that opens or runs something an outlined pill hugging its label
 *    (with a › when it opens something), leaves a control row plain, sizes the icon by the
 *    label, strips the 48 dp minimums, and gives an explained row its dim-gold left rule.
 */
object SkPreferences {
    private val byKeyword: List<Pair<Regex, Pair<Icon, Does>>> =
        listOf(
            Regex("delete|clear|reset|erase|forget|remove|purge|regenerate") to (Icon.DELETE to Does.DELETE),
            Regex("export|import|backup|restore|share|eximport") to (Icon.EXPORT to Does.SHARE),
            Regex("privacy|block|password|secure|lock|dangerous|permission|token") to (Icon.PRIVACY to Does.BLOCK),
            Regex("sync|server|network|metered|automation|api|thirdparty|plugin") to (Icon.GLOBE to Does.CONNECT),
            Regex("pencil|duplicates|badmaths|fastfinishing|singlecages|confetti|cage|grid|cell|value|possibles") to
                (Icon.GRID to Does.CONTACT),
            Regex("directory") to (Icon.DIRECTORY to Does.SHARE),
            Regex("clipboard|copy|paste") to (Icon.COPY to Does.SHARE),
            Regex("voice|tts|audio|sound|mic|record") to (Icon.MIC to Does.MESSAGE),
            Regex("language|locale") to (Icon.GLOBE to Does.CONTACT),
            Regex("theme|colou?r|appearance|wallpaper|background|frame|ground|shiroikumaui|icon") to (Icon.PALETTE to Does.VIEW),
            Regex("gesture|swipe|tap|touch|control|shortcut|keyboard|binding|button|key_") to (Icon.KEYCAP to Does.VIEW),
            Regex("font|text|title|label|heading|typing|newline|field|edit") to (Icon.TEXT to Does.MESSAGE),
            Regex("size|zoom|scale|height|width|padding|weight|gap|corner|border") to (Icon.SIZE to Does.VIEW),
            Regex("notif|remind|alarm|timer|minute|timeout|delay|speed|sensitiv|keepscreen") to (Icon.GAUGE to Does.VIEW),
            Regex("about|licen|version|help|debug|developer|changelog|website|warn") to (Icon.INFO to Does.VIEW),
            Regex("show|display|hide|preview|fullscreen|safe|header|visible") to (Icon.EYE to Does.VIEW),
            Regex("setting") to (Icon.SLIDERS to Does.VIEW),
        )

    /**
     * The game's own settings rows, each with an icon of its own: told by keyword, most of them
     * came out as the same grid. Keys are upstream's, in lower case.
     */
    private val byKey: Map<String, Pair<Icon, Does>> =
        mapOf(
            "maximumcellsize" to (Icon.SIZE to Does.VIEW),
            "gridtakesremainingspaceifnecessary" to (Icon.MOVE to Does.CONTACT),
            "pencilatstart" to (Icon.EDIT to Does.MESSAGE),
            "pencil3x3" to (Icon.LIST to Does.MESSAGE),
            "showtimer" to (Icon.HISTORY to Does.VIEW),
            "showfullscreen" to (Icon.EYE to Does.VIEW),
            "keepscreenon" to (Icon.DEVICE to Does.CONNECT),
            "stopconfettiimmediatelywhenstartingnewgame" to (Icon.SPARK to Does.BLOCK),
            "broadercageframes" to (Icon.GRID to Does.VIEW),
            "usefastfinishingmode" to (Icon.PLAY to Does.CONNECT),
            "badmaths" to (Icon.INFO to Does.BLOCK),
            "duplicates" to (Icon.COPY to Does.BLOCK),
            "removepencils" to (Icon.RESET to Does.SHARE),
            "fillsinglecagesatstart" to (Icon.CHECK to Does.CONNECT),
            // UI → Diagnostics
            "diag_directory" to (Icon.DIRECTORY to Does.VIEW),
            "diag_touch" to (Icon.POINTER to Does.MESSAGE),
            "diag_stored" to (Icon.HISTORY to Does.VIEW),
            "diag_keep_days" to (Icon.GAUGE to Does.VIEW),
            "diag_export" to (Icon.EXPORT to Does.SHARE),
            "diag_share" to (Icon.EXPORT to Does.CONNECT),
            "diag_purge" to (Icon.DELETE to Does.DELETE),
        )

    fun decorate(group: PreferenceGroup) {
        for (i in 0 until group.preferenceCount) {
            val pref = group.getPreference(i)
            if (pref is PreferenceGroup) {
                decorate(pref)
                if (pref is PreferenceCategory) continue
            }
            if (pref.icon != null || (!pref.isSelectable && pref.key == null)) continue
            val (icon, does) = iconOf(pref)
            pref.icon = SkIcons.drawable(pref.context, icon, does, 22f)
        }
    }

    private fun iconOf(pref: Preference): Pair<Icon, Does> {
        val key = pref.key.orEmpty().lowercase().removePrefix("gauguin_ui_")
        byKey[key]?.let { return it }
        byKeyword.firstOrNull { it.first.containsMatchIn(key) }?.let { return it.second }
        return when (pref) {
            is TwoStatePreference -> Icon.TOGGLE to Does.VIEW
            is SeekBarPreference -> Icon.SLIDERS to Does.VIEW
            is DialogPreference -> Icon.LIST to Does.VIEW
            else -> Icon.RIGHT to Does.VIEW
        }
    }

    /**
     * Styles every row of [list] as it is attached: the one call a settings screen needs.
     * [fonts] gives each row the page's font as well.
     */
    fun attach(
        list: RecyclerView,
        fonts: (View) -> Unit = {},
    ) {
        list.addOnChildAttachStateChangeListener(
            object : RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(view: View) {
                    styleRow(list, view)
                    fonts(view)
                }

                override fun onChildViewDetachedFromWindow(view: View) = Unit
            },
        )
    }

    /** Whether a tap on [pref] opens or runs something, as opposed to working a control */
    private fun isAction(pref: Preference): Boolean {
        if (pref is PreferenceGroup || pref is TwoStatePreference || pref is DialogPreference || pref is SeekBarPreference) return false
        if (!pref.isSelectable) return false
        // a swatch or value row is a control: its widget on the right is the affordance
        if (pref.extras.getBoolean(EXTRA_CONTROL, false)) return false
        return true
    }

    private fun opens(pref: Preference): Boolean =
        pref.fragment != null || pref.intent != null || pref.extras.getBoolean(EXTRA_OPENS, false)

    /** Marks a plain row as a control (a colour swatch, a font value): never a pill */
    const val EXTRA_CONTROL = "sk_control"

    /** Marks an action row as opening something (a panel, a picker): it gets a › */
    const val EXTRA_OPENS = "sk_opens"

    /** Marks the main action of its section: 3 dp and bold */
    const val EXTRA_MAIN = "sk_main"

    @SuppressLint("RestrictedApi")
    fun styleRow(
        list: RecyclerView,
        row: View,
    ) {
        val position = list.getChildAdapterPosition(row)
        val pref = if (position >= 0) (list.adapter as? PreferenceGroupAdapter)?.getItem(position) else null
        val title = row.findViewById<TextView>(android.R.id.title)
        val context = row.context

        // only as tall as its content
        if (row.minimumHeight != 0) row.minimumHeight = 0
        row.findViewById<View>(androidx.preference.R.id.icon_frame)?.let { frame ->
            val pad = Pills.dp(context, 2f)
            if (frame.minimumHeight != 0) frame.minimumHeight = 0
            if (frame.paddingTop != pad) frame.setPadding(frame.paddingLeft, pad, frame.paddingRight, pad)
            (title?.parent as? View)?.let { box ->
                if (box.paddingTop != pad) box.setPadding(box.paddingLeft, pad, box.paddingRight, pad)
            }
        }

        // a sub-heading inside a section: its rule takes the heading role
        row.findViewById<View>(R.id.sk_sub_underline)?.let {
            it.setBackgroundColor(SkRoles[SkRole.HEADING])
            title?.setTextColor(SkRoles[SkRole.HEADING])
            return
        }
        // the secondary action at a row's right is a small pill; one that regenerates or deletes is red
        (row.findViewById<View>(R.id.sk_preference_action) as? TextView)?.let { action ->
            if (action.getTag(R.id.tag_sk_dialog_styled) == null) {
                action.setTag(R.id.tag_sk_dialog_styled, true)
                Pills.style(action, main = false, color = SkRoles[SkRole.DELETE], small = true)
            }
        }
        if (title == null || pref == null) return
        if (pref is PreferenceCategory) {
            // a heading of upstream's settings screen (the UI page's own headings colour themselves)
            if (pref !is SkSectionCategory) {
                title.setTextColor(SkRoles[SkRole.HEADING])
                row.findViewById<View>(R.id.sk_section_underline)?.setBackgroundColor(SkRoles[SkRole.HEADING])
                row.findViewById<View>(R.id.sk_section_divider)?.setBackgroundColor(
                    SkRoles.mix(SkRoles[SkRole.BACKGROUND], SkRoles[SkRole.ACCENT], 0.7f),
                )
                row.findViewById<View>(R.id.sk_section_chevron)?.visibility = View.GONE
                row.findViewById<View>(R.id.sk_section_icon)?.visibility = View.GONE
            }
            return
        }
        row.findViewById<TextView>(android.R.id.summary)?.setTextColor(SkRoles[SkRole.SETTINGS_SUMMARY])
        row.findViewById<android.widget.SeekBar>(androidx.preference.R.id.seekbar)?.let { bar ->
            val slider = android.content.res.ColorStateList.valueOf(SkRoles[SkRole.SETTINGS_SLIDER])
            bar.thumbTintList = slider
            bar.progressTintList = slider
            bar.progressBackgroundTintList = android.content.res.ColorStateList.valueOf(SkRoles[SkRole.SWITCH_OFF])
        }
        row.findViewById<TextView>(androidx.preference.R.id.seekbar_value)?.setTextColor(SkRoles[SkRole.SETTINGS_TITLE])

        // The icon belongs to its label: it sits right before it — inside the pill of an action
        // row, as in a menu — never in a column of its own across the row (白い熊, 2026-11-22).
        // It is as tall as the label's line, never under 18 dp, and coloured by what the row does.
        row.findViewById<View>(androidx.preference.R.id.icon_frame)?.let { if (it.visibility != View.GONE) it.visibility = View.GONE }
        (row.findViewById<View>(android.R.id.icon) as? ImageView)?.let { if (it.visibility != View.GONE) it.visibility = View.GONE }

        val action = isAction(pref)
        val wanted = if (action) (if (pref.extras.getBoolean(EXTRA_MAIN, false)) 2 else 1) else 0
        val signature = "${pref.key}|${pref.title}|$wanted|${SkRoles.generation}|${title.textSize}|${pref.icon != null}"
        if (title.getTag(R.id.tag_sk_dialog_styled) != signature) {
            title.setTag(R.id.tag_sk_dialog_styled, signature)
            val does = iconOf(pref).second
            val deletes = action && does == Does.DELETE
            val ink = if (deletes) SkRoles[SkRole.DELETE] else SkRoles[SkRole.PILL_TEXT]
            val size = maxOf(Pills.dp(context, 18f), (title.textSize * 1.25f).toInt())
            val icon = labelIcon(pref, if (deletes) ink else null, size)
            if (action) {
                val main = wanted == 2
                val frame = if (deletes) ink else SkRoles[SkRole.PILL_FRAME]
                title.background = Pills.background(context, frame, main)
                title.setPadding(Pills.dp(context, 14f), Pills.dp(context, 7f), Pills.dp(context, 14f), Pills.dp(context, 7f))
                title.setTypeface(Typeface.create(title.typeface, Typeface.NORMAL), if (main) Typeface.BOLD else Typeface.NORMAL)
                title.setTextColor(ink)
                (title.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                    it.topMargin = Pills.dp(context, 3f)
                    it.bottomMargin = Pills.dp(context, 3f)
                    title.layoutParams = it
                }
                val arrow =
                    if (opens(pref)) {
                        SkIcons.drawable(context, Icon.CHEVRON, ink, 14f).apply { setBounds(0, 0, intrinsicWidth, intrinsicHeight) }
                    } else {
                        null
                    }
                title.setCompoundDrawablesRelative(icon, null, arrow, null)
                title.isSingleLine = false
            } else {
                title.background = null
                title.setTextColor(SkRoles[SkRole.SETTINGS_TITLE])
                title.setPadding(0, 0, 0, 0)
                title.setCompoundDrawablesRelative(icon, null, null, null)
                (title.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                    if (it.topMargin != 0 || it.bottomMargin != 0) {
                        it.topMargin = 0
                        it.bottomMargin = 0
                        title.layoutParams = it
                    }
                }
            }
            title.compoundDrawablePadding = Pills.dp(context, 9f)
            title.gravity = android.view.Gravity.CENTER_VERTICAL
        }

        // an explained row: bold label, dim-gold description, a 2 dp dim-gold rule down its left
        val summary = row.findViewById<TextView>(android.R.id.summary)
        val explained = summary != null && summary.visibility == View.VISIBLE && !summary.text.isNullOrEmpty()
        (title.parent as? View)?.let { box ->
            val has = box.background is LeftRule
            if (explained && !has) {
                box.background = LeftRule(SkRoles[SkRole.SETTINGS_SUMMARY], Pills.dp(context, 2f))
                box.setPaddingRelative(Pills.dp(context, 10f), box.paddingTop, box.paddingEnd, box.paddingBottom)
                if (!action) title.setTypeface(Typeface.create(title.typeface, Typeface.NORMAL), Typeface.BOLD)
                summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            } else if (!explained && has) {
                box.background = null
                box.setPaddingRelative(0, box.paddingTop, box.paddingEnd, box.paddingBottom)
                if (!action) title.setTypeface(Typeface.create(title.typeface, Typeface.NORMAL), Typeface.NORMAL)
            }
        }
    }

    /**
     * The icon of [pref] as a drawable for the start of its label: [size] square, in [tint] if
     * given, else in the colour of what the row does. One of our own icons already has it.
     */
    private fun labelIcon(
        pref: Preference,
        tint: Int?,
        size: Int,
    ): Drawable? {
        val source = pref.icon ?: return null
        val (glyph, does) = iconOf(pref)
        val drawable =
            if (SkIcons.isOurs(source)) {
                if (tint != null) SkIcons.drawable(pref.context, glyph, tint, 22f) else source
            } else {
                val copy = source.constantState?.newDrawable(pref.context.resources)?.mutate() ?: source.mutate()
                copy.setTint(tint ?: if (does == Does.VIEW) SkRoles[SkRole.SETTINGS_ICON] else SkIcons.color(does))
                copy
            }
        drawable.setBounds(0, 0, size, size)
        return drawable
    }

    /** A thin bar down the left edge of an explained row */
    class LeftRule(
        color: Int,
        private val width: Int,
    ) : Drawable() {
        private val paint = Paint().apply { this.color = color }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val left = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) b.right - width else b.left
            canvas.drawRect(left.toFloat(), b.top.toFloat(), (left + width).toFloat(), b.bottom.toFloat(), paint)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
