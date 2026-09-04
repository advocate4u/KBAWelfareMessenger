package com.example.kbawelfaremessenger

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatButton

/** Button whose availability is controlled by the installed signed license. */
class LicensedFeatureButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.buttonStyle
) : AppCompatButton(context, attrs, defStyleAttr) {
    private fun featureForId(): String? = when (id) {
        R.id.btnPreview -> "preview"
        R.id.btnTestSms -> "test_sms"
        R.id.btnSelectRange, R.id.btnUnselectRange -> "range"
        R.id.btnWhatsApp -> "whatsapp"
        else -> null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val feature = featureForId() ?: return
        val allowed = LicenseManager.isFeatureEnabled(context, feature)
        isEnabled = allowed
        alpha = if (allowed) 1f else 0.45f
    }
}
