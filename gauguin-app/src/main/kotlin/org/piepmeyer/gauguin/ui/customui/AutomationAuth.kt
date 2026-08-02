package org.piepmeyer.gauguin.ui.customui

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The token gate for the 保存復元 automation contract: 白い熊's 自由作業盤 fires a token-carrying
 * intent at this app to make it export itself headlessly.
 *
 * The switch is **off by default** — nothing is reachable until 白い熊 turns it on. The token is
 * generated lazily on first read so the settings row always has a value to show, and is compared in
 * constant time. These live in their own prefs file, which the backup export never touches: a token
 * must never travel inside a backup ZIP.
 */
object AutomationAuth {
    private const val PREFS = "gauguin_automation"
    private const val KEY_ENABLED = "automation_enabled"
    private const val KEY_TOKEN = "automation_token"
    private const val TOKEN_BYTES = 24

    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(
        context: Context,
        enabled: Boolean,
    ) = prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()

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

    /** Constant-time comparison — never `==` on a secret. */
    fun isTokenValid(
        context: Context,
        candidate: String?,
    ): Boolean {
        if (candidate.isNullOrEmpty()) return false
        return MessageDigest.isEqual(candidate.toByteArray(), token(context).toByteArray())
    }

    /** `80922d8c…4c49a87c` — what the settings row shows. */
    fun abbreviate(token: String): String =
        if (token.length <= 20) token else token.take(8) + "…" + token.takeLast(8)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
