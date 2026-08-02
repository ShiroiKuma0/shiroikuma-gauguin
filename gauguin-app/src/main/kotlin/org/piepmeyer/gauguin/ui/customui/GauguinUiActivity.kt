package org.piepmeyer.gauguin.ui.customui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.piepmeyer.gauguin.R

/**
 * Host for the 白い熊 GNU Gauguin UI page. The live preview is pinned at the top of the layout and
 * the settings list scrolls beneath it; the Export / Import panel replaces the list in the same
 * container, which is what makes the close chain natural — popping the back stack closes the panel,
 * finishing the activity closes the whole page.
 */
class GauguinUiActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Gauguin_ShiroikumaUi)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gauguin_ui)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.gauguinUiContainer, GauguinUiPreferenceFragment())
                .commit()
        }
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
     * folder or the selection can be corrected without navigating back.
     */
    fun closeAfterSuccess() {
        closeExportPanel()
        finish()
    }

    companion object {
        private const val EXPORT_PANEL = "gauguin-export-panel"
    }
}
