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
        GauguinDiag.log(GauguinDiag.Kind.TOUCH, "page: onCreate begins")
        setTheme(R.style.Theme_Gauguin_ShiroikumaUi)
        SkTheme.install(this, always = true)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gauguin_ui)

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
        GauguinDiag.log(GauguinDiag.Kind.TOUCH, "page: onCreate ends")
        window.decorView.post { GauguinDiag.log(GauguinDiag.Kind.TOUCH, "page: first pass after layout") }
    }

    override fun onResume() {
        super.onResume()
        GauguinDiag.log(GauguinDiag.Kind.TOUCH, "page: resumed")
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

    companion object {
        private const val EXPORT_PANEL = "gauguin-export-panel"
    }
}
