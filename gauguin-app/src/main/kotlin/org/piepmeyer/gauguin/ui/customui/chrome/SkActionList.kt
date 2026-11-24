package org.piepmeyer.gauguin.ui.customui.chrome

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ListAdapter

/**
 * Fork: the rows of a dialog's action list (pick a backup, walk the directories): each a pill as
 * wide as the list with its icon inside at the left, coloured by what the row does — the same row
 * a popup menu has.
 */
object SkActionList {
    data class Row(
        val label: CharSequence,
        val icon: SkIcons.Icon,
        val does: SkIcons.Does,
    )

    fun adapter(
        context: Context,
        rows: List<Row>,
    ): ListAdapter =
        object : BaseAdapter() {
            override fun getCount(): Int = rows.size

            override fun getItem(position: Int): Row = rows[position]

            override fun getItemId(position: Int): Long = position.toLong()

            override fun getView(
                position: Int,
                convertView: View?,
                parent: ViewGroup,
            ): View {
                val row = rows[position]
                val pill =
                    Pills.iconPill(
                        context,
                        row.label,
                        row.icon,
                        iconColor = SkIcons.color(row.does),
                        color = SkRoles[SkRole.MENU_ROW_FRAME],
                        fullWidth = true,
                        onClick = {},
                    )
                // the list handles the tap; the pill only draws
                pill.isClickable = false
                pill.isFocusable = false
                pill.gravity = Gravity.CENTER_VERTICAL
                val h = Pills.dp(context, 20f)
                val v = Pills.dp(context, 3f)
                return FrameLayout(context).apply {
                    setPadding(h, v, h, v)
                    addView(pill, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                    // drawn from its own roles: the dialog pass leaves it alone
                    SkDialogs.keep(this)
                }
            }
        }
}
