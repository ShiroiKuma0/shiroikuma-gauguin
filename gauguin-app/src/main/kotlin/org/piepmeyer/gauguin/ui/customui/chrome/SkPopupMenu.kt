package org.piepmeyer.gauguin.ui.customui.chrome

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.view.Menu
import android.view.View
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuPopupHelper
import androidx.appcompat.widget.PopupMenu

/**
 * Fork: AppCompat's PopupMenu — its rows are drawn by [PillMenuItemView] like those of every
 * menu — with one addition: a menu taller than the room below its anchor is shown shifted up,
 * so it uses the height of the screen before it scrolls. A stock popup is given only the larger
 * of the room above and the room below its anchor.
 */
@SuppressLint("RestrictedApi")
class SkPopupMenu(
    private val context: Context,
    private val anchor: View,
) : PopupMenu(context, anchor) {
    private var tall: MenuPopupHelper? = null

    override fun show() {
        val offset = verticalOffset(anchor, menu)
        val builder = menu as? MenuBuilder
        if (offset == null || builder == null) {
            super.show()
            return
        }
        try {
            tall = MenuPopupHelper(context, builder, anchor).also { it.show(0, offset) }
        } catch (_: Throwable) {
            tall = null
            super.show()
        }
    }

    override fun dismiss() {
        runCatching { tall?.dismiss() }
        tall = null
        super.dismiss()
    }

    companion object {
        /**
         * For a menu anchored at a point of [parent] (the mouse context menu): the y to anchor it
         * at so that it fits above the bottom of the window instead of scrolling.
         */
        fun fittedY(
            parent: View,
            y: Float,
            menu: Menu,
        ): Float {
            val frame = Rect()
            parent.getWindowVisibleDisplayFrame(frame)
            val location = IntArray(2)
            parent.getLocationOnScreen(location)
            val below = frame.bottom - (location[1] + y)
            val needed = neededHeight(parent, menu)
            return if (needed <= below) y else maxOf(0f, y - (needed - below))
        }

        private fun neededHeight(
            view: View,
            menu: Menu,
        ): Int {
            var rows = 0f
            for (i in 0 until menu.size()) {
                val item = menu.getItem(i)
                if (item.isVisible) rows += if (item.isCheckable) 36f else 54f
            }
            return ((rows + 16f) * view.resources.displayMetrics.density).toInt()
        }

        /** How far up the menu must start to fit, or null when it fits below its anchor */
        fun verticalOffset(
            anchor: View,
            menu: Menu,
        ): Int? {
            val needed = neededHeight(anchor, menu)
            val frame = Rect()
            anchor.getWindowVisibleDisplayFrame(frame)
            val location = IntArray(2)
            anchor.getLocationOnScreen(location)
            val anchorBottom = location[1] + anchor.height
            val below = frame.bottom - anchorBottom
            if (needed <= below) return null
            val shift = minOf(needed - below, anchorBottom - frame.top)
            return if (shift > 0) -shift else null
        }
    }
}
