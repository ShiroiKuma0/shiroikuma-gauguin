package org.piepmeyer.gauguin.ui.customui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * The 保存復元 automation entry point: 白い熊's 自由作業盤 fires these actions to make this app export
 * itself headlessly and report back.
 *
 * The receiver itself does **no work** beyond the token gate — `goAsync()` does not extend the
 * broadcast window, and overrunning it gets the process ANR'd mid-export. `EXPORT_STATE` therefore
 * hands straight off to [StateExportService] and returns; only `LIST_CATEGORIES`, which is instant,
 * answers inline.
 */
class StateExportReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val app = context.applicationContext
        val pkg = app.packageName
        val token = intent.getStringExtra("token")

        when (intent.action) {
            "$pkg.action.EXPORT_STATE" -> {
                val replyAction = intent.getStringExtra("reply_action")
                val replyPackage = intent.getStringExtra("reply_package")
                val replyId = intent.getStringExtra("reply_id")
                if (replyAction == null || replyPackage == null || replyId == null) return

                // "automation disabled" and "bad token" are distinct on purpose — they debug
                // differently, and 自由作業盤 shows the line verbatim.
                if (!AutomationAuth.enabled(app)) {
                    reply(app, replyAction, replyPackage, replyId, "ERROR:automation disabled")
                    return
                }
                if (!AutomationAuth.isTokenValid(app, token)) {
                    reply(app, replyAction, replyPackage, replyId, "ERROR:bad token")
                    return
                }

                val service =
                    Intent(app, StateExportService::class.java).apply {
                        putExtra("path", intent.getStringExtra("path"))
                        putExtra("items", intent.getStringExtra("items"))
                        putExtra("progress_action", intent.getStringExtra("progress_action"))
                        putExtra("reply_action", replyAction)
                        putExtra("reply_package", replyPackage)
                        putExtra("reply_id", replyId)
                    }
                ContextCompat.startForegroundService(app, service)
            }

            "$pkg.action.LIST_CATEGORIES" -> {
                val replyAction = intent.getStringExtra("reply_action") ?: return
                val replyPackage = intent.getStringExtra("reply_package") ?: return
                val replyId = intent.getStringExtra("reply_id") ?: return
                if (!AutomationAuth.enabled(app)) {
                    reply(app, replyAction, replyPackage, replyId, "ERROR:automation disabled")
                    return
                }
                if (!AutomationAuth.isTokenValid(app, token)) {
                    reply(app, replyAction, replyPackage, replyId, "ERROR:bad token")
                    return
                }
                reply(app, replyAction, replyPackage, replyId, categoryLines(app))
            }

            "$pkg.action.CANCEL_EXPORT" -> {
                // Fire and forget: no reply of its own, and a silent no-op when nothing is running.
                if (!AutomationAuth.enabled(app)) return
                if (!AutomationAuth.isTokenValid(app, token)) return
                StateExportService.requestCancel()
            }
        }
    }

    /** `OK:` + one `id<TAB>label` line per category. Everything here is authored, so all default on. */
    private fun categoryLines(context: Context): String =
        "OK:" +
            GauguinBackup.Cat.entries.joinToString("\n") { category ->
                "${category.id}\t${context.getString(category.labelRes)}"
            }

    companion object {
        /**
         * The reply is a **fresh broadcast** — never a Binder. EMUI will not reliably carry a live
         * ResultReceiver/PendingIntent into another app's manifest receiver, and it severs the
         * ordered-broadcast result channel between third-party apps.
         * `FLAG_INCLUDE_STOPPED_PACKAGES` is what lets a backgrounded caller still hear us.
         */
        fun reply(
            context: Context,
            replyAction: String,
            replyPackage: String,
            replyId: String,
            result: String,
        ) {
            context.sendBroadcast(
                Intent(replyAction).apply {
                    setPackage(replyPackage)
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    putExtra("reply_id", replyId)
                    putExtra("result", result)
                },
            )
        }
    }
}
