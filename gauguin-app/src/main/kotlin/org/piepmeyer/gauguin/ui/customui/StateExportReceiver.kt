package org.piepmeyer.gauguin.ui.customui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.ContextCompat

/**
 * The 保存復元 automation entry point: 白い熊's 自由作業盤 fires these actions to make this app export
 * itself headlessly and report back.
 *
 * The receiver itself does **no work** beyond the gate — `goAsync()` does not extend the broadcast
 * window, and overrunning it gets the process ANR'd mid-export. `EXPORT_STATE` therefore hands
 * straight off to [StateExportService] and returns; only `LIST_CATEGORIES`, which is instant,
 * answers inline.
 *
 * This is the **unauthenticated** half of the surface, and in contract v2 that is deliberate: it
 * only ever writes where it was told to and reports what it did. `import` is not here and never
 * gets a broadcast action — an import overwrites this app's data, and this receiver is exported
 * with no permission, so an import here would let any app on the phone wipe any sister app. It
 * lives on the data door instead ([org.piepmeyer.gauguin.automation.AutomationProvider]), which
 * knows who is calling.
 */
class StateExportReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val app = context.applicationContext
        val pkg = app.packageName
        // Ignored unless this app asks for one — see AutomationAuth.refuse.
        val token = intent.getStringExtra("token")

        when (intent.action) {
            "$pkg.action.EXPORT_STATE" -> {
                val replyAction = intent.getStringExtra("reply_action")
                val replyPackage = intent.getStringExtra("reply_package")
                val replyId = intent.getStringExtra("reply_id")
                if (replyAction == null || replyPackage == null || replyId == null) return

                AutomationAuth.refuse(app, token)?.let {
                    reply(app, replyAction, replyPackage, replyId, it)
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
                // A broadcast IS a background start. Unless this app happens to hold a
                // foreground-start allowance — which the system grants only when it has been
                // interacted with recently — API 31+ refuses with
                // ForegroundServiceStartNotAllowedException, and an exception escaping onReceive
                // takes the whole process down. The exposure is exactly inverted from when it
                // matters: open the app and run a backup by hand and the allowance is there; leave
                // it cold for the unattended batch, or restore onto a clean phone, which is the
                // case this contract exists for, and it is not. So every hands-on test passes.
                //
                // Catching it silently would be no improvement from the caller's side — a
                // no-export it can only report as a timeout, indistinguishable from an app that
                // never implemented the contract. The refusal is therefore answered as this
                // request's one terminal reply, which 自由作業盤 renders verbatim.
                try {
                    ContextCompat.startForegroundService(app, service)
                } catch (e: Exception) {
                    reply(app, replyAction, replyPackage, replyId, refusal(app, e))
                }
            }

            "$pkg.action.LIST_CATEGORIES" -> {
                val replyAction = intent.getStringExtra("reply_action") ?: return
                val replyPackage = intent.getStringExtra("reply_package") ?: return
                val replyId = intent.getStringExtra("reply_id") ?: return
                AutomationAuth.refuse(app, token)?.let {
                    reply(app, replyAction, replyPackage, replyId, it)
                    return
                }
                reply(app, replyAction, replyPackage, replyId, categoryLines(app))
            }

            "$pkg.action.CANCEL_EXPORT" -> {
                // Fire and forget: no reply of its own, and a silent no-op when nothing is running.
                if (AutomationAuth.refuse(app, token) != null) return
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
         * `FLAG_INCLUDE_STOPPED_PACKAGES` is what lets a backgrounded caller still hear us, and the
         * manifest's `<queries>` is what lets `setPackage` resolve at all on Android 11+.
         */

        /**
         * The one-line refusal for a foreground start the system would not allow.
         *
         * `ERROR:no-foreground-start` is a **keyed** string: 保存中核 matches it exactly and puts a
         * 「電池最適化を除外」 button on the failed row. So it is reserved for the one case that
         * button can actually fix — this app not being exempt from battery optimisation. On EMUI
         * the identical refusal also arises from アプリ起動管理 being left on 自動管理, which no app
         * can change for itself, and a button that cannot fix the fault is worse than one that
         * simply names the exception. When we are already exempt, the cause is therefore something
         * else and the caller gets the description instead of the key.
         *
         * The message is flattened to one line: a reply is a single line, and `LIST_CATEGORIES`
         * answers are newline-delimited, so an embedded newline would be read as another category.
         */
        fun refusal(
            context: Context,
            failure: Throwable?,
        ): String {
            val power = context.getSystemService(PowerManager::class.java)
            val exempt =
                runCatching {
                    power?.isIgnoringBatteryOptimizations(context.packageName) == true
                }.getOrDefault(false)
            if (!exempt) return "ERROR:no-foreground-start"
            val text = failure?.message ?: failure?.javaClass?.simpleName ?: "foreground start refused"
            return "ERROR:" + text.replace('\n', ' ').replace('\r', ' ')
        }

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
