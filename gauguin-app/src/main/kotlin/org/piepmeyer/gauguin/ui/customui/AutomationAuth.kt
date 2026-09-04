package org.piepmeyer.gauguin.ui.customui

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The gate for the 保存復元 automation contract: 白い熊's 自由作業盤 drives this app's headless
 * export, and 応用管理 backs its data up and puts it back.
 *
 * ## Contract v2 — the switch is ON and the token is OFF
 *
 * v1 shipped closed: [KEY_ENABLED] defaulted to false and a caller had also to present a secret
 * 白い熊 had pasted from this app's settings into the caller's. **A pasted secret cannot survive a
 * wipe**, and the case this family now exists to serve is 応用管理 restoring apps *and their data*
 * onto a clean phone, where nothing has been configured and nobody has pasted anything. A gate that
 * only works once the phone is already set up is no gate for setting the phone up.
 *
 * So the switch defaults ON, and [KEY_REQUIRE_TOKEN] — new in v2, default **off** — is what turns
 * the token back into a requirement. The switch stays because it is the only way to close this one
 * app off, and a feature that can be turned on but never off is one 白い熊 cannot retreat from.
 *
 * The data door ([org.piepmeyer.gauguin.automation.AutomationProvider]) does not rely on any of
 * this for identity: it checks the caller's package name, uid and signing certificate regardless.
 *
 * These prefs live in their own file, which the backup export never touches: a token must never
 * travel inside a backup ZIP.
 */
object AutomationAuth {
    private const val PREFS = "gauguin_automation"
    private const val KEY_ENABLED = "automation_enabled"
    private const val KEY_REQUIRE_TOKEN = "automation_require_token"
    private const val KEY_TOKEN = "automation_token"
    private const val TOKEN_BYTES = 24

    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(
        context: Context,
        enabled: Boolean,
    ) = prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()

    fun requireToken(context: Context): Boolean = prefs(context).getBoolean(KEY_REQUIRE_TOKEN, false)

    fun setRequireToken(
        context: Context,
        require: Boolean,
    ) = prefs(context).edit().putBoolean(KEY_REQUIRE_TOKEN, require).apply()

    /**
     * The one place both checks live. `null` means proceed; anything else is the exact `ERROR:`
     * string to answer with.
     *
     * Written out at each entry point instead, "disabled" and "bad token" drift apart across
     * forty-two apps — so every caller of the contract goes through here.
     *
     * **A token handed to an app that does not require one is IGNORED, never refused.** Tokens live
     * in task arguments and workspace variables that outlive the setting they were pasted for, and
     * a caller still sending one — because it was configured last year, or because another app on
     * the batch does want one — must be served. Refusing it would turn "白い熊 turned a switch off"
     * into "half the batch mysteriously fails", which is precisely the friction the switch exists
     * to remove.
     */
    fun refuse(
        context: Context,
        candidate: String?,
    ): String? =
        when {
            !enabled(context) -> "ERROR:automation disabled"
            requireToken(context) && !isTokenValid(context, candidate) -> "ERROR:bad token"
            else -> null
        }

    /** The stored token, minted on first read so the row is never empty. */
    fun token(context: Context): String {
        val stored = prefs(context).getString(KEY_TOKEN, null)
        if (!stored.isNullOrBlank()) return stored
        return regenerate(context)
    }

    fun regenerate(context: Context): String {
        val bytes = ByteArray(TOKEN_BYTES).also { SecureRandom().nextBytes(it) }
        val token = bytes.joinToString("") { "%02x".format(it) }
        prefs(context).edit().putString(KEY_TOKEN, token).apply()
        return token
    }

    /** Constant-time comparison — never `==` on a secret. Still used when a token *is* required. */
    fun isTokenValid(
        context: Context,
        candidate: String?,
    ): Boolean {
        if (candidate.isNullOrEmpty()) return false
        return MessageDigest.isEqual(candidate.toByteArray(), token(context).toByteArray())
    }

    /** `80922d8c…4c49a87c` — what the settings row shows. */
    fun abbreviate(token: String): String = if (token.length <= 20) token else token.take(8) + "…" + token.takeLast(8)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
