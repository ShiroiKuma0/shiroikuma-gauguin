package org.piepmeyer.gauguin.ui.customui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.piepmeyer.gauguin.R
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs the headless 保存復元 export off the broadcast window.
 *
 * A manifest receiver must reach `finish()` inside Android's broadcast window (~10 s foreground,
 * ~60 s otherwise) — `goAsync()` does not extend it — so the whole export lives here instead, behind
 * a foreground notification started within 5 s of the service starting.
 *
 * Exactly **one** terminal reply is sent per request, guarded by an [AtomicBoolean] so an async
 * success and a synchronous error can never both fire. The cancel flag is process-local and released
 * in a `finally`: persisting it would wedge the app for good after a single crash.
 */
class StateExportService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        // Where to reply is read BEFORE going foreground, and going foreground is guarded.
        //
        // Two rules meet here and the order satisfies both. Once the receiver called
        // startForegroundService the platform demands startForeground whatever this service then
        // concludes, so it must precede every early return — including the return that does
        // nothing. But startForeground can itself be refused, and a refusal raised before we know
        // reply_action/reply_package/reply_id has nothing to answer with: it dies silently and the
        // caller waits out its whole timeout for a reply that was never possible. Reading three
        // extras is instant, so it costs nothing against the 5 s window and buys us somewhere to
        // send the refusal.
        val replyAction = intent?.getStringExtra("reply_action")
        val replyPackage = intent?.getStringExtra("reply_package")
        val replyId = intent?.getStringExtra("reply_id")

        val started = runCatching { startForeground(NOTIFICATION_ID, buildNotification()) }

        if (replyAction == null || replyPackage == null || replyId == null) return stop()

        val replied = AtomicBoolean(false)
        fun reply(result: String) {
            if (!replied.compareAndSet(false, true)) return
            StateExportReceiver.reply(applicationContext, replyAction, replyPackage, replyId, result)
        }

        started.exceptionOrNull()?.let {
            // The service never ran, so nothing else can fire: this refusal is safely the one
            // terminal reply this request is owed.
            reply(StateExportReceiver.refusal(applicationContext, it))
            return stop()
        }

        if (!running.compareAndSet(false, true)) {
            reply("ERROR:export already running")
            return stop()
        }
        cancelled = false

        val progressAction = intent?.getStringExtra("progress_action")
        val pathOverride = intent?.getStringExtra("path")
        val items = intent?.getStringExtra("items")

        scope.launch {
            try {
                val categories = resolveCategories(items) ?: run {
                    reply("ERROR:unknown category in items: $items")
                    return@launch
                }
                val directory = resolveDirectory(pathOverride) ?: return@launch run {
                    reply(directoryError(pathOverride))
                }

                val file =
                    GauguinBackup.exportToDirectory(
                        context = applicationContext,
                        categories = categories,
                        directory = directory,
                        onProgress = { progress ->
                            if (progressAction != null) {
                                sendProgress(progressAction, replyPackage, replyId, progress)
                            }
                        },
                        isCancelled = { cancelled },
                    )

                val bytes = file.length()
                reply(
                    "OK:${file.absolutePath}|$bytes|${GauguinBackup.humanSize(bytes)}|${categories.size} categories",
                )
            } catch (e: InterruptedException) {
                reply("ERROR:cancelled")
            } catch (e: Throwable) {
                reply("ERROR:${e.message ?: e.javaClass.simpleName}")
            } finally {
                running.set(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    /** `items` absent/empty = our default set, per the contract — not "everything we have". */
    private fun resolveCategories(items: String?): Set<GauguinBackup.Cat>? {
        if (items.isNullOrBlank()) {
            return GauguinBackup.Cat.entries.filter { it.defaultSelected }.toSet()
        }
        val requested = items.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val resolved = requested.map { GauguinBackup.Cat.byId(it) }
        return if (resolved.any { it == null }) null else resolved.filterNotNull().toSet()
    }

    /** Precedence: the `path` extra → the app's configured folder → an error. */
    private fun resolveDirectory(pathOverride: String?): File? {
        if (!pathOverride.isNullOrBlank()) {
            // Check the grant rather than discovering it by failing: 自由作業盤 keys on the exact
            // "no-storage-access" string to offer its 全ファイルアクセスを許可 repair button.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                return null
            }
            return File(pathOverride).also { if (!it.exists()) it.mkdirs() }
        }
        val configured = GauguinUiConfig(applicationContext).exportDirectory ?: return null
        return File(configured).also { if (!it.exists()) it.mkdirs() }
    }

    private fun directoryError(pathOverride: String?): String =
        if (!pathOverride.isNullOrBlank() &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            !Environment.isExternalStorageManager()
        ) {
            "ERROR:no-storage-access"
        } else {
            "ERROR:no-directory"
        }

    /**
     * Real numbers, never a percentage. `item` is the category id being written right now, and
     * `current` is its 1-based POSITION — that is what moves the panel's highlight.
     */
    private fun sendProgress(
        progressAction: String,
        replyPackage: String,
        replyId: String,
        progress: GauguinBackup.Progress,
    ) {
        val label = getString(progress.category.labelRes)
        sendBroadcast(
            Intent(progressAction).apply {
                setPackage(replyPackage)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                putExtra("reply_id", replyId)
                putExtra("app", getString(R.string.app_name))
                putExtra("item", progress.category.id)
                putExtra("text", "区分 ${progress.position}/${progress.total} — $label")
                putExtra("current", progress.position.toLong())
                putExtra("total", progress.total.toLong())
                putExtra("unit", "区分")
            },
        )
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.gauguin_ui_automation_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.gauguin_ui_automation_notification))
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setOngoing(true)
            .build()
    }

    private fun stop(): Int {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        running.set(false)
    }

    companion object {
        private const val CHANNEL_ID = "gauguin-automation-export"
        private const val NOTIFICATION_ID = 4711

        private val running = AtomicBoolean(false)

        @Volatile
        private var cancelled = false

        /**
         * Signal the running export to unwind at the next category boundary. Safe at any time — a
         * cancel that arrives with nothing running is a silent no-op.
         */
        fun requestCancel() {
            if (running.get()) cancelled = true
        }
    }
}
