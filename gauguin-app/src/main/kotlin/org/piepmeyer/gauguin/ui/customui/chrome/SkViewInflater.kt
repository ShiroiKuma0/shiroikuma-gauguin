package org.piepmeyer.gauguin.ui.customui.chrome

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.annotation.Keep
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.appcompat.widget.AppCompatCheckedTextView
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.appcompat.widget.AppCompatTextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.theme.MaterialComponentsViewInflater

/**
 * Fork: the theme's `viewInflaterClass` (named by `ThemeOverlay.Shiroikuma`): the one place
 * every inflated widget of the app passes through, so the button, switch and mark rules reach
 * upstream's screens with no layout or call site touched.
 *
 * It creates exactly the views Material would. Inside a dialog a view is handed to
 * [SkDialogs]; anywhere else a button becomes an outlined pill, a switch the outlined
 * switch, a check box or radio button a selection mark in its roles, and a floating button an
 * outlined ring, a card a framed box.
 *
 * AppCompat instantiates this class by name, so it must survive R8 under this name.
 */
@Keep
class SkViewInflater : MaterialComponentsViewInflater() {
    override fun createButton(
        context: Context,
        attrs: AttributeSet,
    ): AppCompatButton =
        super.createButton(context, attrs).also { button ->
            if (SkDialogs.onViewCreated(button)) return@also
            val filled = SkWidgets.isFilled(button)
            SkWidgets.whenAttached(button) { SkWidgets.button(button, filled) }
        }

    override fun createTextView(
        context: Context,
        attrs: AttributeSet?,
    ): AppCompatTextView =
        super.createTextView(context, attrs).also { text ->
            if (SkDialogs.onViewCreated(text)) return@also
            if (text.id == com.google.android.material.R.id.snackbar_text) SkWidgets.whenAttached(text) { SkWidgets.snackbar(text) }
        }

    override fun createImageView(
        context: Context,
        attrs: AttributeSet?,
    ): AppCompatImageView = super.createImageView(context, attrs).also { SkDialogs.onViewCreated(it) }

    override fun createEditText(
        context: Context,
        attrs: AttributeSet?,
    ): AppCompatEditText = super.createEditText(context, attrs).also { SkDialogs.onViewCreated(it) }

    override fun createCheckBox(
        context: Context,
        attrs: AttributeSet,
    ): AppCompatCheckBox =
        super.createCheckBox(context, attrs).also {
            if (!SkDialogs.onViewCreated(it)) SkWidgets.mark(it)
        }

    override fun createRadioButton(
        context: Context,
        attrs: AttributeSet?,
    ): AppCompatRadioButton =
        super.createRadioButton(context, attrs).also {
            if (!SkDialogs.onViewCreated(it)) SkWidgets.mark(it)
        }

    override fun createCheckedTextView(
        context: Context,
        attrs: AttributeSet?,
    ): AppCompatCheckedTextView = super.createCheckedTextView(context, attrs).also { SkDialogs.onViewCreated(it) }

    // Widgets a layout names by their full class come here; each keeps its class, because view
    // bindings and upstream's code cast to it.
    override fun createView(
        context: Context,
        name: String,
        attrs: AttributeSet,
    ): View? =
        when (name) {
            "com.google.android.material.materialswitch.MaterialSwitch" ->
                MaterialSwitch(context, attrs).also { SkWidgets.switch(it) }
            "com.google.android.material.button.MaterialButton" ->
                MaterialButton(context, attrs).also { button ->
                    if (!SkDialogs.onViewCreated(button)) {
                        val filled = SkWidgets.isFilled(button)
                        SkWidgets.whenAttached(button) { SkWidgets.button(button, filled) }
                    }
                }
            "com.google.android.material.card.MaterialCardView" ->
                MaterialCardView(context, attrs).also { card -> SkWidgets.whenAttached(card) { SkWidgets.card(card) } }
            "com.google.android.material.floatingactionbutton.FloatingActionButton" ->
                FloatingActionButton(context, attrs).also { SkWidgets.fab(it) }
            "com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton" ->
                ExtendedFloatingActionButton(context, attrs).also { SkWidgets.button(it, true) }
            else -> super.createView(context, name, attrs)
        }
}
