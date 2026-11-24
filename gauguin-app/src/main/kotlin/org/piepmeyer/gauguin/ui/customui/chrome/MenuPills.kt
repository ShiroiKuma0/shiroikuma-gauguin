package org.piepmeyer.gauguin.ui.customui.chrome

import android.content.Context
import android.view.MenuItem
import org.piepmeyer.gauguin.ui.customui.chrome.SkIcons.Does
import org.piepmeyer.gauguin.ui.customui.chrome.SkIcons.Icon

/**
 * Fork: what a menu item does. A menu item carries no "opens something" or "deletes" flag, so this
 * is told from the item's resource name rather than from a table of ids — a row upstream adds later
 * is covered without a change here: the icon a row shows when it brings none, the colour that icon
 * takes, and whether the row is red.
 */
object MenuPills {
    data class What(
        val icon: Icon,
        val does: Does,
        val opens: Boolean = false,
        val destructive: Boolean = false,
    )

    private val byKeyword: List<Pair<Regex, What>> =
        listOf(
            Regex("delete|remove|eraser|erase|clear|discard") to What(Icon.DELETE, Does.DELETE, destructive = true),
            // giving the answer away costs the streak: the colour of what is kept secret
            Regex("reveal|solution|cheat") to What(Icon.EYE, Does.BLOCK),
            Regex("mistake|check|verify") to What(Icon.CHECK, Does.CONNECT),
            Regex("export|import|share|copy|backup|restore|save|load") to What(Icon.EXPORT, Does.SHARE),
            Regex("simulate|debug|solve|recalculate|start|restart|replay|new|create") to What(Icon.PLAY, Does.CONNECT),
            Regex("comment|report|bug|issue|edit|rename") to What(Icon.EDIT, Does.MESSAGE),
            Regex("statistic|stats|game|challenge") to What(Icon.LAYERS, Does.CONTACT),
            Regex("undo|redo|history") to What(Icon.HISTORY, Does.VIEW),
            Regex("setting|option|preference|theme") to What(Icon.SLIDERS, Does.VIEW),
            Regex("help|about|info") to What(Icon.INFO, Does.VIEW),
        )

    private val plain = What(Icon.RIGHT, Does.VIEW)

    fun of(
        context: Context,
        item: MenuItem,
    ): What = of(context, item.itemId)

    fun of(
        context: Context,
        id: Int,
    ): What {
        if (id == 0 || id == -1) return plain
        val name = runCatching { context.resources.getResourceEntryName(id) }.getOrNull()?.lowercase() ?: return plain
        return byKeyword.firstOrNull { it.first.containsMatchIn(name) }?.second ?: plain
    }
}
