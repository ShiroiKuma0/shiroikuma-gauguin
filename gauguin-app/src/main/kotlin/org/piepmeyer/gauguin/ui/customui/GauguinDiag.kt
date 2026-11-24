package org.piepmeyer.gauguin.ui.customui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import org.piepmeyer.gauguin.MainApplication
import org.piepmeyer.gauguin.R
import org.piepmeyer.gauguin.ui.customui.chrome.SkToast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TreeMap

/**
 * UI → Diagnostics. Every diagnostic the fork writes sits behind its own switch, all OFF by
 * default — off means the line is never written. The same contract as 白い熊 地図's `ChizuDiag`
 * and 白い熊 電話's `Diag`, which this follows.
 *
 * Everything goes to the app's own diag directory, `Android/data/<app id>/files/diag/` (readable
 * with adb, gone with the app): one file per kind per day, `gauguin-<kind>_<yyyy-MM-dd>.txt`.
 * Never `.log`: EMUI's nightly clean-up deletes every `*.log` under `Android/data` as junk. Each
 * line is appended with the file opened and closed around it, so a file deleted under us is simply
 * started again.
 *
 * On 白い熊's phone it is the only way to see what the app did: its logcat keeps error-level lines
 * only.
 */
object GauguinDiag {
    enum class Kind(
        val key: String,
        @StringRes val titleRes: Int,
        @StringRes val descrRes: Int,
    ) {
        TOUCH("touch", R.string.diag_touch, R.string.diag_touch_descr),
    }

    /** What is stored: [first]/[last] in millis; [days] maps a day to its span, [kinds] a kind to files, bytes, first, last. */
    class Summary {
        var files = 0
        var bytes = 0L
        var first = Long.MAX_VALUE
        var last = 0L
        val days = TreeMap<String, LongArray>()
        val kinds = TreeMap<String, LongArray>()
    }

    const val ACTION_MARK = "shiroikuma.gauguin.action.DIAG_MARK"
    const val ACTION_ALL_OFF = "shiroikuma.gauguin.action.DIAG_ALL_OFF"
    const val MAX_KEEP_DAYS = 60

    private const val PREFS = "gauguin_diag"
    private const val DIR = "diag"
    private const val PREFIX = "gauguin-"
    private const val EXT = ".txt"
    private const val KEY_KEEP_DAYS = "keep_days"
    private const val KEY_EXPORT_TREE = "export_tree"
    private const val CHANNEL = "gauguin_diag"
    private const val NOTIFICATION_ID = 0xD1A6
    private const val MAX_DAY_BYTES = 8L * 1024 * 1024
    private const val DAY_LENGTH = 10
    private const val CLOCK_LENGTH = 8

    // The always-on probe of build +008, written to the files root before this section existed.
    private val OLD_TOUCH_FILES = listOf("ui-open-trace.txt")

    private val on = BooleanArray(Kind.entries.size)

    @Volatile
    private var anyOn = false

    @Volatile
    private var app: Context? = null
    private var marks = 0

    /** Load the switches and move the old probe file in. Safe to call again: only the first call works. */
    fun init(context: Context) {
        if (app != null) return
        val c = context.applicationContext ?: return
        synchronized(this) {
            if (app != null) return
            app = c
        }
        val prefs = prefs(c)
        Kind.entries.forEach { on[it.ordinal] = prefs.getBoolean(it.key, false) }
        anyOn = on.any { it }
        updateNotification(c)
        Thread({
            runCatching { migrate(c) }
            runCatching { cleanup(c) }
        }, "gauguin-diag-migrate").apply { isDaemon = true }.start()
    }

    fun isOn(kind: Kind) = on[kind.ordinal]

    fun set(
        context: Context,
        kind: Kind,
        value: Boolean,
    ) {
        init(context)
        on[kind.ordinal] = value
        anyOn = on.any { it }
        prefs(context).edit().putBoolean(kind.key, value).apply()
        updateNotification(context)
        if (value) write(kind, "diag ${kind.key} on, ${MainApplication.versionName(context)}")
    }

    /** One line into [kind]'s file of today — when its switch is on, and never in the way of the caller. */
    fun log(
        kind: Kind,
        line: String,
    ) {
        if (on[kind.ordinal]) write(kind, line)
    }

    /** As [log], with the line built only when the switch is on. */
    inline fun log(
        kind: Kind,
        line: () -> String,
    ) {
        if (isOn(kind)) log(kind, line())
    }

    @Synchronized
    private fun write(
        kind: Kind,
        line: String,
    ) {
        try {
            val dir = dir(app) ?: return
            val now = Date()
            val file = File(dir, "$PREFIX${kind.key}_${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now)}$EXT")
            if (file.length() > MAX_DAY_BYTES) return
            file.appendText("${SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(now)}  $line\n")
        } catch (ignored: Exception) {
            // diagnostics must never be the reason anything else fails
        }
    }

    fun dir(context: Context?): File? {
        val base = (context ?: app)?.getExternalFilesDir(null) ?: return null
        return File(base, DIR).takeIf { it.isDirectory || it.mkdirs() }
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---- moving the old probe file in ----

    // ui-open-trace.txt from the files root becomes gauguin-touch_<day it was last written>.txt,
    // in front of anything already recorded for that day.
    private fun migrate(context: Context) {
        val root = context.getExternalFilesDir(null) ?: return
        val target = dir(context) ?: return
        OLD_TOUCH_FILES.map { File(root, it) }.filter { it.isFile }.forEach { old ->
            val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(old.lastModified()))
            val to = File(target, "$PREFIX${Kind.TOUCH.key}_$day$EXT")
            if (to.exists()) {
                val newer = to.readText()
                to.writeText(old.readText() + newer)
                old.delete()
            } else if (!old.renameTo(to)) {
                to.writeText(old.readText())
                old.delete()
            }
        }
    }

    // ---- what is stored ----

    /** All files in the diag directory, oldest name first. */
    fun files(context: Context): List<File> = dir(context)?.listFiles { f -> f.isFile }?.sortedBy { it.name }.orEmpty()

    fun summarize(context: Context): Summary {
        val summary = Summary()
        files(context).forEach { file ->
            val end = file.lastModified()
            val start = firstTime(file).takeIf { it in 1..end } ?: end
            summary.files++
            summary.bytes += file.length()
            summary.first = minOf(summary.first, start)
            summary.last = maxOf(summary.last, end)
            dayOf(file.name)?.let { day ->
                val span = summary.days.getOrPut(day) { longArrayOf(start, end) }
                span[0] = minOf(span[0], start)
                span[1] = maxOf(span[1], end)
            }
            val kind = file.name.removePrefix(PREFIX).substringBefore('_')
            val k = summary.kinds.getOrPut(kind) { longArrayOf(0, 0, start, end) }
            k[0]++
            k[1] += file.length()
            k[2] = minOf(k[2], start)
            k[3] = maxOf(k[3], end)
        }
        return summary
    }

    private fun dayOf(name: String): String? {
        val at = name.indexOf('_')
        return if (at >= 0 && name.length >= at + 1 + DAY_LENGTH) name.substring(at + 1, at + 1 + DAY_LENGTH) else null
    }

    // The time of a file's first line: the day from its name, the clock from the line.
    private fun firstTime(file: File): Long =
        runCatching {
            val day = dayOf(file.name) ?: return 0
            val clock = file.bufferedReader().use { it.readLine() }?.take(CLOCK_LENGTH) ?: return 0
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse("$day $clock")?.time ?: 0
        }.getOrDefault(0)

    // ---- keeping, purging ----

    /** Days of data kept; 0 keeps everything (the default). */
    fun getKeepDays(context: Context) = prefs(context).getInt(KEY_KEEP_DAYS, 0)

    fun setKeepDays(
        context: Context,
        days: Int,
    ) {
        prefs(context).edit().putInt(KEY_KEEP_DAYS, days.coerceAtLeast(0)).apply()
    }

    /** Deletes the files of days older than the kept ones; returns how many went. */
    fun cleanup(context: Context): Int {
        val keep = getKeepDays(context)
        if (keep <= 0) return 0
        val oldest = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -(keep - 1)) }
        val oldestKept = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(oldest.time)
        return files(context).count { file -> dayOf(file.name)?.let { it < oldestKept } == true && file.delete() }
    }

    /** Deletes everything recorded; returns how many files went. */
    fun purge(context: Context): Int = files(context).count { it.delete() }

    // ---- export, share ----

    /** Where the directory picker opens: the directory used last, else `/sdcard/tmp`. */
    fun exportStart(context: Context): Uri =
        prefs(context).getString(KEY_EXPORT_TREE, null)?.let(Uri::parse)
            ?: DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:tmp")

    /** Copies every file into a new dated subdirectory of [tree]; returns its name, null when nothing could be written. */
    fun exportTo(
        context: Context,
        tree: Uri,
    ): String? {
        prefs(context).edit().putString(KEY_EXPORT_TREE, tree.toString()).apply()
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val name = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date()) + "_gauguin-diag"
        val into = DocumentsContract.createDocument(resolver, parent, DocumentsContract.Document.MIME_TYPE_DIR, name) ?: return null
        files(context).forEach { file ->
            val out = DocumentsContract.createDocument(resolver, into, "text/plain", file.name) ?: return@forEach
            resolver.openOutputStream(out)?.use { stream -> file.inputStream().use { it.copyTo(stream) } }
        }
        return name
    }

    /** A share of every file through the app's file provider; null when nothing is recorded. */
    fun shareIntent(context: Context): Intent? {
        val uris = files(context).map { FileProvider.getUriForFile(context, "${context.packageName}.diag", it) }
        if (uris.isEmpty()) return null
        val send =
            Intent(Intent.ACTION_SEND_MULTIPLE)
                .setType("text/plain")
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, null)
    }

    // ---- marking, the recording notification ----

    /** A numbered marker in every file being recorded: "something happened here". 0 when nothing records. */
    fun mark(from: String): Int {
        if (!anyOn) return 0
        val number = ++marks
        Kind.entries.filter { on[it.ordinal] }.forEach { write(it, "mark #$number from $from") }
        return number
    }

    /** While any switch is on, an ongoing notification says so, with Mark and Turn all off. */
    fun updateNotification(context: Context) {
        // the texts in the app's chosen language, which the application context does not carry
        val c = GauguinLanguage.localized(context.applicationContext)
        try {
            val manager = NotificationManagerCompat.from(c)
            if (!anyOn) {
                manager.cancel(NOTIFICATION_ID)
                return
            }
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, c.getString(R.string.diag_channel), NotificationManager.IMPORTANCE_LOW),
            )
            val which = Kind.entries.filter { on[it.ordinal] }.joinToString(", ") { c.getString(it.titleRes) }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

            fun action(
                code: Int,
                action: String,
            ) = PendingIntent.getBroadcast(c, code, Intent(action).setClass(c, GauguinDiagReceiver::class.java), flags)
            val builder =
                NotificationCompat
                    .Builder(c, CHANNEL)
                    .setSmallIcon(R.drawable.outline_bug_report_24)
                    .setContentTitle(c.getString(R.string.diag_recording))
                    .setContentText(which)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(which))
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .addAction(0, c.getString(R.string.diag_mark), action(1, ACTION_MARK))
                    .addAction(0, c.getString(R.string.diag_all_off), action(2, ACTION_ALL_OFF))
            c.packageManager.getLaunchIntentForPackage(c.packageName)?.let {
                builder.setContentIntent(PendingIntent.getActivity(c, 3, it, flags))
            }
            manager.notify(NOTIFICATION_ID, builder.build())
        } catch (ignored: Exception) {
            // no notification permission: recording goes on regardless
        }
    }

    fun allOff(context: Context) {
        init(context)
        val edit = prefs(context).edit()
        Kind.entries.forEach { kind ->
            if (on[kind.ordinal]) write(kind, "diag ${kind.key} off from the notification")
            on[kind.ordinal] = false
            edit.putBoolean(kind.key, false)
        }
        edit.apply()
        anyOn = false
        updateNotification(context)
    }
}

/** The two buttons of the recording notification. */
class GauguinDiagReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        GauguinDiag.init(context)
        when (intent.action) {
            GauguinDiag.ACTION_MARK -> {
                val number = GauguinDiag.mark("the notification")
                if (number > 0) SkToast.show(context, GauguinLanguage.localized(context).getString(R.string.diag_mark_toast, number))
            }

            GauguinDiag.ACTION_ALL_OFF -> GauguinDiag.allOff(context)
        }
    }
}
