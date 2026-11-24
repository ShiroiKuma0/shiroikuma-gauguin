package org.piepmeyer.gauguin.ui.customui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toDrawable
import org.piepmeyer.gauguin.R
import org.piepmeyer.gauguin.ui.customui.chrome.SkRole
import org.piepmeyer.gauguin.ui.customui.chrome.SkRoles
import org.piepmeyer.gauguin.ui.customui.chrome.SkTheme

/**
 * Host for the 白い熊 GNU Gauguin UI page: the title, and the settings list scrolling beneath it.
 * The Export / Import panel replaces the list in the same
 * container, which is what makes the close chain natural — popping the back stack closes the panel,
 * finishing the activity closes the whole page.
 *
 * The page always wears the black-yellow overlay, also while the house look is switched off for the
 * rest of the app: it is where the look is set.
 */
class GauguinUiActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        GauguinDiag.init(this)
        touch = intent?.getLongExtra(EXTRA_TOUCH, 0L) ?: 0L
        if (touch > 0) noWindowAnimation()
        mark("page: onCreate begins")
        setTheme(R.style.Theme_Gauguin_ShiroikumaUi)
        SkTheme.install(this, always = true)
        super.onCreate(savedInstanceState)
        mark("page: super.onCreate done")
        setContentView(R.layout.activity_gauguin_ui)
        mark("page: layout inflated")

        // The layout holds no colour: the page's own few views take their roles here. The window
        // was drawn black by the platform before this ran; now it follows the background role.
        val background = SkRoles[SkRole.BACKGROUND]
        window.setBackgroundDrawable(background.toDrawable())
        findViewById<View>(R.id.gauguinUiRoot).setBackgroundColor(background)
        findViewById<TextView>(R.id.gauguinUiTitle).setTextColor(SkRoles[SkRole.HEADING])
        findViewById<View>(R.id.gauguinUiRuleTop).setBackgroundColor(SkRoles[SkRole.BORDER])

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.gauguinUiContainer, GauguinUiPreferenceFragment())
                .commit()
        }
        mark("page: onCreate ends")
        // The marks that tell where the time goes between the long-press and the page being seen:
        // the first pass that draws, the frame the system took, the window in front, the entry over.
        val observer = window.decorView.viewTreeObserver
        observer.addOnPreDrawListener(
            object : android.view.ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    window.decorView.viewTreeObserver.removeOnPreDrawListener(this)
                    mark("page: first draw pass begins")
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        window.decorView.viewTreeObserver.registerFrameCommitCallback { mark("page: first frame handed to the system") }
                    }
                    return true
                }
            },
        )
    }

    private var touch = 0L

    /** One line of the "touch" diagnostic, with the time since the long-press's touch */
    private fun mark(text: String) {
        GauguinDiag.log(GauguinDiag.Kind.TOUCH) {
            text + if (touch > 0) ", ${android.os.SystemClock.uptimeMillis() - touch} ms after the touch" else ""
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) mark("page: window in front")
    }

    override fun onEnterAnimationComplete() {
        super.onEnterAnimationComplete()
        mark("page: entry animation over")
    }

    override fun onResume() {
        super.onResume()
        mark("page: resumed")
    }

    fun openExportPanel() {
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.gauguinUiContainer, ExportImportFragment())
            .addToBackStack(EXPORT_PANEL)
            .commit()
    }

    /** Close the Export / Import panel, leaving the settings list in place. */
    fun closeExportPanel() {
        supportFragmentManager.popBackStack(EXPORT_PANEL, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }

    /**
     * Unwind the whole chain: the panel and this page. Called after a successful export or import,
     * once 白い熊 acknowledges the result dialog — a failure leaves everything open instead, so the
     * directory or the selection can be corrected without navigating back.
     */
    fun closeAfterSuccess() {
        closeExportPanel()
        finish()
    }

    /** Opened by the long-press: the page is simply there, with no slide or fade of the window */
    @Suppress("DEPRECATION")
    private fun noWindowAnimation() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        } else {
            overridePendingTransition(0, 0)
        }
    }

    companion object {
        private const val EXTRA_TOUCH = "gauguin_ui_touch_uptime"

        /** The page from the menu button's long-press: no window animation, and [touch] for the diagnostic */
        fun openInstantly(
            context: android.content.Context,
            touch: Long,
        ) {
            val intent =
                android.content
                    .Intent(context, GauguinUiActivity::class.java)
                    .putExtra(EXTRA_TOUCH, touch)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION)
            context.startActivity(intent, android.app.ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle())
        }

        private const val EXPORT_PANEL = "gauguin-export-panel"
    }
}
