package org.piepmeyer.gauguin.automation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.piepmeyer.gauguin.R
import org.piepmeyer.gauguin.ui.customui.GauguinBackup
import org.piepmeyer.gauguin.ui.customui.StateExportReceiver
import java.io.File
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Where a data export or import actually runs.
 *
 * ## Why a foreground service and not the provider call
 *
 * The call returns in milliseconds; this can run for minutes. Two hard reasons it cannot be done
 * anywhere cheaper:
 *
 * - **A binder call holds the caller.** 応用管理 is drawing a list; a multi-minute synchronous call
 *   would freeze its UI, report no progress, and refuse cancellation.
 * - **A backgrounded app writing for minutes is frozen mid-stream on this phone**, which yields a
 *   truncated archive underneath a success reply — the worst possible failure, because it is
 *   indistinguishable from a good backup until the day it is restored (応用管理, 2026-09-04).
 *
 * ## The descriptor
 *
 * Already duplicated by [AutomationProvider] before it got here, because the original belongs to
 * the binder transaction and is closed the moment `call()` returns. This service owns the copy from
 * the instant it drains [HANDOVER], and closes it on **every** path out — a leaked descriptor holds
 * the caller's file open, and the caller cannot checksum or encrypt a file that is still open.
 */
class AutomationDataService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        // Extras first, then the guarded foreground start, then every decision.
        val importing = intent?.getBooleanExtra(EXTRA_IMPORTING, false) == true
        val jobId = intent?.getStringExtra(EXTRA_JOB)
        val replyAction = intent?.getStringExtra(AutomationProvider.KEY_REPLY_ACTION)
        val replyPackage = intent?.getStringExtra(AutomationProvider.KEY_REPLY_PACKAGE)
        val progressAction = intent?.getStringExtra(AutomationProvider.KEY_PROGRESS_ACTION)

        // Going foreground precedes every decision, INCLUDING the decision to do nothing: once a
        // caller invoked startForegroundService the platform demands startForeground inside its
        // window whatever this service concludes, and enforces that by killing the process. A
        // caller retrying a stale job id must therefore be quietly ignored, not fatal.
        //
        // But the extras are read first, because the start can itself be REFUSED and a refusal
        // raised before we know where to reply has nothing to answer with. And it is answered
        // rather than swallowed: closing the descriptor and stopping quietly is not enough, since
        // the provider has already handed the caller `OK:<job_id>` — a silent stop leaves it
        // waiting out its timeout on a job that no longer exists, which reads as an app that never
        // implemented the contract rather than one that was refused.
        val started = runCatching { startForeground(NOTIFICATION_ID, notification(importing)) }
        started.exceptionOrNull()?.let { failure ->
            jobId?.let { id ->
                HANDOVER.remove(id)?.let { orphan -> runCatching { orphan.close() } }
                AutomationJobs.finish(id)
                if (!replyAction.isNullOrEmpty() && !replyPackage.isNullOrEmpty()) {
                    sendBroadcast(
                        Intent(replyAction).apply {
                            setPackage(replyPackage)
                            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                            putExtra(AutomationProvider.KEY_JOB_ID, id)
                            putExtra(
                                AutomationProvider.KEY_RESULT,
                                StateExportReceiver.refusal(applicationContext, failure),
                            )
                        },
                    )
                }
            }
            return stop(startId)
        }

        if (jobId == null) return stop(startId)
        val fd = HANDOVER.remove(jobId) ?: return stop(startId)

        // From here the descriptor is ours and nothing else will ever close it: it is out of the
        // handover map, and the caller closed its own copy when `call()` returned. Reaching the
        // coroutine is what transfers that duty onwards, so every path that does not reach it has
        // to close the descriptor here. `startForeground` throwing is only the likeliest of those
        // paths, not the only one, which is why this is a flag and not a guard per failure.
        var handedOff = false
        try {
            val replyAction = intent.getStringExtra(AutomationProvider.KEY_REPLY_ACTION)
            val replyPackage = intent.getStringExtra(AutomationProvider.KEY_REPLY_PACKAGE)
            val progressAction = intent.getStringExtra(AutomationProvider.KEY_PROGRESS_ACTION)

            val replied = AtomicBoolean(false)
            // A `val` lambda rather than a local `fun`: a local function alongside a local `var`
            // captured by an anonymous object crashes AGP's lint analysis ("FirDeclaration was not
            // found for class KtProperty"), and it does so after Kotlin, Java and dex have all
            // succeeded, so it surfaces late in a release build looking like anything but source.
            val reply: (String) -> Unit = { result ->
                // Exactly one terminal answer per job, whatever path got here — a synchronous
                // failure and an asynchronous success must never both fire. The same guard the
                // broadcast contract has carried since the first sister app.
                if (replied.compareAndSet(false, true)) {
                    AutomationJobs.finish(jobId)
                    if (!replyAction.isNullOrEmpty() && !replyPackage.isNullOrEmpty()) {
                        sendBroadcast(
                            Intent(replyAction).apply {
                                setPackage(replyPackage)
                                // Without this a caller that has been backgrounded never hears the
                                // answer, and on a clean phone it may not have been launched at all.
                                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                                putExtra(AutomationProvider.KEY_JOB_ID, jobId)
                                putExtra(AutomationProvider.KEY_RESULT, result)
                            },
                        )
                    }
                }
            }

            scope.launch {
                try {
                    fd.use { open ->
                        if (importing) {
                            runImport(open, reply)
                        } else {
                            runExport(
                                jobId = jobId,
                                fd = open,
                                items = intent.getStringExtra(AutomationProvider.KEY_ITEMS),
                                progressAction = progressAction,
                                replyPackage = replyPackage,
                                reply = reply,
                            )
                        }
                    }
                } catch (e: InterruptedException) {
                    reply("ERROR:cancelled")
                } catch (t: Throwable) {
                    reply("ERROR:${t.message ?: t.javaClass.simpleName}")
                } finally {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                }
            }
            handedOff = true
        } finally {
            if (!handedOff) {
                runCatching { fd.close() }
                AutomationJobs.finish(jobId)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun runExport(
        jobId: String,
        fd: ParcelFileDescriptor,
        items: String?,
        progressAction: String?,
        replyPackage: String?,
        reply: (String) -> Unit,
    ) {
        val categories =
            resolve(items) ?: run {
                reply("ERROR:unknown category in items: $items")
                return
            }
        var written = 0L
        ParcelFileDescriptor.AutoCloseOutputStream(fd).use { out ->
            val counting = CountingOutputStream(out)
            GauguinBackup.export(
                context = this,
                categories = categories,
                target = counting,
                onProgress = { progress ->
                    if (progressAction != null && !replyPackage.isNullOrEmpty()) {
                        sendProgress(progressAction, replyPackage, jobId, progress)
                    }
                },
                isCancelled = { AutomationJobs.isCancelled(jobId) },
            )
            written = counting.written
        }
        if (AutomationJobs.isCancelled(jobId)) {
            reply("ERROR:cancelled")
        } else {
            reply("OK:$written|${GauguinBackup.humanSize(written)}|${categories.size} categories")
        }
    }

    private fun runImport(
        fd: ParcelFileDescriptor,
        reply: (String) -> Unit,
    ) {
        // Spooled to a file rather than read into a ByteArray. The whole archive still lands before
        // anything is touched — a partial read that failed halfway would import half an archive,
        // and a half-restored app is worse than one that refused — but it lands on disk, because
        // this app's backup carries 白い熊's imported font files and nothing bounds how many there
        // are. Reading the archive must not be the thing that kills the process.
        val spool = File.createTempFile("automation-import", ".zip", cacheDir)
        try {
            runImportFrom(spool, fd, reply)
        } finally {
            spool.delete()
        }
    }

    private fun runImportFrom(
        spool: File,
        fd: ParcelFileDescriptor,
        reply: (String) -> Unit,
    ) {
        ParcelFileDescriptor.AutoCloseInputStream(fd).use { source ->
            spool.outputStream().use { source.copyTo(it) }
        }
        if (spool.length() == 0L) {
            reply("ERROR:empty archive")
            return
        }
        // Every category the archive actually carries, not every category we know about: asking
        // for one the archive lacks is how a restore ends up reporting success over nothing.
        val present = spool.inputStream().use { GauguinBackup.categoriesIn(it) }
        if (present.isEmpty()) {
            reply("ERROR:archive carries no categories")
            return
        }
        val result = spool.inputStream().use { GauguinBackup.import(this, present, it) }
        if (result.errors.isNotEmpty()) {
            reply("ERROR:${result.errors.first()}")
            return
        }
        // Everything the import wrote is on disk by now — GauguinBackup commits its preference
        // edits synchronously rather than posting them. The caller SIGKILLs this process the
        // instant it reads the line below, so an edit still in flight has no orderly shutdown left
        // to flush it: the kill that protects the import would otherwise truncate it, and the
        // restore would report success over data that is gone.
        reply("OK:${result.lines.size} restored")
    }

    /**
     * Real numbers, never a percentage. `item` is the category id being written right now, and
     * `current` is its 1-based POSITION — that is what moves the caller's highlight. The correlation
     * id goes out under both names because the data door answers on `job_id` while the family's
     * progress shape is written around `reply_id`.
     */
    private fun sendProgress(
        progressAction: String,
        replyPackage: String,
        jobId: String,
        progress: GauguinBackup.Progress,
    ) {
        val label = getString(progress.category.labelRes)
        sendBroadcast(
            Intent(progressAction).apply {
                setPackage(replyPackage)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                putExtra(AutomationProvider.KEY_JOB_ID, jobId)
                putExtra("reply_id", jobId)
                putExtra("app", getString(R.string.app_name))
                putExtra("item", progress.category.id)
                putExtra("text", "区分 ${progress.position}/${progress.total} — $label")
                putExtra("current", progress.position.toLong())
                putExtra("total", progress.total.toLong())
                putExtra("unit", "区分")
            },
        )
    }

    private fun resolve(items: String?): Set<GauguinBackup.Cat>? {
        if (items.isNullOrBlank()) return GauguinBackup.Cat.entries.filter { it.defaultSelected }.toSet()
        val wanted = items.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val found = wanted.mapNotNull { GauguinBackup.Cat.byId(it) }
        return if (found.size == wanted.size) found.toSet() else null
    }

    private fun notification(importing: Boolean): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    getString(R.string.gauguin_ui_automation_data_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        return NotificationCompat
            .Builder(this, CHANNEL)
            .setContentTitle(
                getString(
                    if (importing) {
                        R.string.gauguin_ui_automation_data_importing
                    } else {
                        R.string.gauguin_ui_automation_data_exporting
                    },
                ),
            ).setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setOngoing(true)
            .build()
    }

    private fun stop(startId: Int): Int {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "gauguin-automation-data"
        private const val NOTIFICATION_ID = 4712
        private const val EXTRA_JOB = "job"
        private const val EXTRA_IMPORTING = "importing"

        /**
         * The descriptor's way across, because an Intent is the wrong vehicle for one.
         *
         * A `ParcelFileDescriptor` in an Intent extra is duplicated by the system on delivery and
         * the copy's lifetime stops being ours to reason about. Handing it through a map keyed by
         * the job id keeps exactly one open descriptor with exactly one owner — the service, which
         * closes it on every path out of [onStartCommand].
         */
        private val HANDOVER = ConcurrentHashMap<String, ParcelFileDescriptor>()

        /**
         * Start the job, or say why it could not start. `null` means running.
         *
         * A start from inside a binder call is a **background start**, and API 31+ may refuse it
         * outright unless this app is exempt from battery optimisation. On that path the service
         * never runs, so nothing else would ever take the descriptor back out of [HANDOVER] or
         * close it — and a leaked descriptor holds the caller's file open, which is exactly what
         * stops 応用管理 checksumming and encrypting it. So the failure is caught here, the
         * descriptor closed, and the caller told, rather than an `OK:` being returned for a job
         * that will never run.
         */
        fun start(
            context: Context,
            jobId: String,
            fd: ParcelFileDescriptor,
            importing: Boolean,
            extras: Bundle?,
        ): String? {
            HANDOVER[jobId] = fd
            return runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, AutomationDataService::class.java).apply {
                        putExtra(EXTRA_JOB, jobId)
                        putExtra(EXTRA_IMPORTING, importing)
                        putExtra(AutomationProvider.KEY_ITEMS, extras?.getString(AutomationProvider.KEY_ITEMS))
                        putExtra(
                            AutomationProvider.KEY_REPLY_ACTION,
                            extras?.getString(AutomationProvider.KEY_REPLY_ACTION),
                        )
                        putExtra(
                            AutomationProvider.KEY_REPLY_PACKAGE,
                            extras?.getString(AutomationProvider.KEY_REPLY_PACKAGE),
                        )
                        putExtra(
                            AutomationProvider.KEY_PROGRESS_ACTION,
                            extras?.getString(AutomationProvider.KEY_PROGRESS_ACTION),
                        )
                    },
                )
            }.fold(
                onSuccess = { null },
                onFailure = { failure ->
                    HANDOVER.remove(jobId)?.let { leaked -> runCatching { leaked.close() } }
                    "ERROR:${failure.message ?: failure.javaClass.simpleName}"
                },
            )
        }
    }
}

/**
 * Counts what it passes through, because the caller owns the file and we may not be able to stat it
 * at all — it can be an anonymous pipe, or a descriptor into a directory this app cannot list.
 *
 * A named class rather than an anonymous `object : OutputStream()` capturing a local `var`: that
 * shape, in a method that also declares a local `fun`, crashes AGP's lint analysis with
 * "FirDeclaration was not found for class KtProperty, fir is null" — and does so only after Kotlin,
 * Java and dex have all succeeded, so it surfaces very late in a release build and looks like
 * anything but a source problem.
 */
private class CountingOutputStream(
    private val out: OutputStream,
) : OutputStream() {
    var written = 0L
        private set

    override fun write(b: Int) {
        out.write(b)
        written++
    }

    override fun write(
        b: ByteArray,
        off: Int,
        len: Int,
    ) {
        out.write(b, off, len)
        written += len
    }

    override fun flush() = out.flush()
}
