package org.piepmeyer.gauguin.automation

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONArray
import org.json.JSONObject
import org.piepmeyer.gauguin.ui.customui.AutomationAuth
import org.piepmeyer.gauguin.ui.customui.GauguinBackup

/**
 * The data door: export this app's own state, and put it back, for a caller we can identify.
 *
 * ## Why a provider and not the broadcast receiver next to it
 *
 * Two reasons, and the first is the whole point of the redesign.
 *
 * **A broadcast cannot tell you who sent it.** The old contract's answer to that was a shared
 * secret, which cannot survive the wipe that this feature exists to recover from. A provider gets
 * the caller's identity from the framework for free — see [AutomationCallers] for what is actually
 * checked and why a package-name prefix would have been worse than the token it replaced.
 *
 * **A list needs a synchronous answer.** 応用管理 draws a row per installed app before any export
 * exists; a broadcast round trip per app to fill a list is the wrong shape entirely.
 *
 * ## What does NOT happen here
 *
 * The payload. `call()` validates, starts a foreground service and returns — tens of megabytes over
 * minutes inside a binder call would block the caller, report no progress, refuse cancellation and
 * die silently if this process were killed. The bytes go through a file descriptor the caller
 * opened, and the terminal answer comes back on the broadcast the family already proved on EMUI.
 *
 * ## Why a descriptor and not a path
 *
 * Because a backup is not a stable directory while it is being assembled. 応用管理 writes into a
 * temporary path and renames on commit; it encrypts and checksums **per file it knows about**. A
 * file this app dropped into that directory itself would be renamed out from under it, would sit in
 * plaintext inside an encrypted backup, and would be unverified rather than verified-and-failing
 * (応用管理, 2026-09-04). A descriptor is also a capability that **expires when it is closed** —
 * precisely the property a URI grant failed to give us on the 地図 contract, where the revoke
 * needed a five-minute floor because 地図 might not read for three minutes.
 *
 * It also means this app no longer needs `MANAGE_EXTERNAL_STORAGE` to be backed up. That permission
 * was only ever required because the old contract handed apps an absolute path — this fork keeps it
 * for the §1 broadcast export, which is still handed one, and for its own Export/Import page.
 */
class AutomationProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    /**
     * Every method answers a [Bundle] with [KEY_RESULT] — `OK…` or `ERROR:…`, the same vocabulary
     * the broadcast contract uses, so a caller has one grammar to parse rather than two.
     *
     * A refusal is returned, never thrown: an exception across a binder reaches the caller as a
     * `RuntimeException` with our stack trace in it, which tells 白い熊 nothing and tells a
     * misbehaving caller rather more than it should.
     */
    override fun call(
        method: String,
        arg: String?,
        extras: Bundle?,
    ): Bundle {
        val ctx = context ?: return fail("ERROR:not ready")

        // WHO, before WHAT. A caller we cannot identify gets the same answer whatever it asked for.
        when (val verdict = AutomationCallers.verify(ctx, callingPackage)) {
            is AutomationCallers.Verdict.Refused -> return fail(verdict.why)
            AutomationCallers.Verdict.Allowed -> Unit
        }
        // Then the app's own switches — a token is ignored unless this app asks for one.
        AutomationAuth.refuse(ctx, extras?.getString(KEY_TOKEN))?.let { return fail(it) }

        return when (method) {
            METHOD_DESCRIBE -> ok(describe(ctx))
            METHOD_EXPORT -> start(ctx, extras, importing = false)
            METHOD_IMPORT -> start(ctx, extras, importing = true)
            METHOD_CANCEL -> {
                AutomationJobs.cancel(extras?.getString(KEY_JOB_ID))
                ok("OK:cancelled")
            }

            else -> fail("ERROR:unknown method: $method")
        }
    }

    /**
     * What this app would export, answered without exporting anything.
     *
     * Returned from the call rather than written into the archive, deliberately: 応用管理 must draw
     * a row before an export exists, and at restore must judge compatibility **before** streaming
     * tens of megabytes into an app that would reject them — which it cannot do if the header is
     * buried inside an encrypted archive (応用管理, 2026-09-04).
     *
     * Built with `org.json` rather than string concatenation because `contains` carries localised
     * labels straight out of `strings.xml`: a quote or a backslash in one of those would otherwise
     * hand the caller a header it cannot parse.
     */
    private fun describe(ctx: Context): String {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        val categories = GauguinBackup.Cat.entries.filter { it.defaultSelected }
        val header =
            JSONObject().apply {
                put("app_id", ctx.packageName)
                put("version_code", PackageInfoCompat.getLongVersionCode(info))
                put("version_name", info.versionName ?: "")
                put("format", GauguinBackup.VERSION)
                put("min_format_readable", MIN_FORMAT_READABLE)
                // Gauguin merges an import key by key into prefs it creates on demand, so it does
                // not need to have been launched once first.
                put("requires_launch_first", false)
                put("contains", JSONArray(categories.map { ctx.getString(it.labelRes) }))
            }
        return "OK:$header"
    }

    /**
     * Hand the descriptor to a foreground service and get out of the way.
     *
     * The descriptor is **duplicated** before it leaves this method. The one in [extras] belongs to
     * the binder transaction and is closed when `call()` returns; a service reading it afterwards
     * would find it shut. That is a bug you only see under load, so it is not left to the service
     * to remember.
     */
    private fun start(
        ctx: Context,
        extras: Bundle?,
        importing: Boolean,
    ): Bundle {
        @Suppress("DEPRECATION")
        val fd =
            extras?.getParcelable<ParcelFileDescriptor>(KEY_FD)
                ?: return fail("ERROR:no descriptor")
        val dup = runCatching { fd.dup() }.getOrNull() ?: return fail("ERROR:descriptor unusable")
        val jobId = AutomationJobs.begin()
        // A start from inside a binder call is a background start, which API 31+ may refuse. If it
        // is refused the descriptor has already been closed for us; what must not happen is the
        // job staying claimed, or the caller being told `OK:` for work that will never run.
        AutomationDataService.start(ctx, jobId, dup, importing, extras)?.let {
            AutomationJobs.finish(jobId)
            return fail(it)
        }
        return ok("OK:$jobId")
    }

    private fun ok(result: String) = Bundle().apply { putString(KEY_RESULT, result) }

    private fun fail(why: String) = Bundle().apply { putString(KEY_RESULT, why) }

    // A provider that is only ever `call()`ed still has to answer these. Refusing loudly beats
    // returning an empty cursor, which reads downstream as "there is no data" rather than "wrong
    // door".
    override fun query(
        u: Uri,
        p: Array<String>?,
        s: String?,
        a: Array<String>?,
        o: String?,
    ): Cursor? = throw UnsupportedOperationException("automation is call() only")

    override fun getType(uri: Uri): String? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = throw UnsupportedOperationException("automation is call() only")

    override fun delete(
        uri: Uri,
        s: String?,
        a: Array<String>?,
    ): Int = throw UnsupportedOperationException("automation is call() only")

    override fun update(
        u: Uri,
        v: ContentValues?,
        s: String?,
        a: Array<String>?,
    ): Int = throw UnsupportedOperationException("automation is call() only")

    companion object {
        const val METHOD_DESCRIBE = "describe"
        const val METHOD_EXPORT = "export"
        const val METHOD_IMPORT = "import"
        const val METHOD_CANCEL = "cancel"

        const val KEY_RESULT = "result"
        const val KEY_FD = "fd"
        const val KEY_TOKEN = "token"
        const val KEY_JOB_ID = "job_id"
        const val KEY_ITEMS = "items"
        const val KEY_REPLY_ACTION = "reply_action"
        const val KEY_REPLY_PACKAGE = "reply_package"
        const val KEY_PROGRESS_ACTION = "progress_action"

        /**
         * The oldest archive this build can still read.
         *
         * Version skew has a direction: old data into a newer app is normally fine, because an app
         * migrates its own storage; newer data into an older app is not. This field is what lets a
         * caller refuse the second case at discovery time, before anything is streamed.
         *
         * The format number itself is [GauguinBackup.VERSION] — the one the archive already
         * carries in its `manifest.json`, so the header and the file can never disagree. Both are
         * mirrored in the manifest's `shiroikuma.automation.*` meta-data, which is how a caller
         * reads them without waking a frozen app.
         */
        const val MIN_FORMAT_READABLE = 1
    }
}
