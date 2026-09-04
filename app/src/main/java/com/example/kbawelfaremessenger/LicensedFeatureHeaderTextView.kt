package com.example.kbawelfaremessenger

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.TextView

/** License-gated collapsible-section header. Used for Range Selection. */
class LicensedFeatureHeaderTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : androidx.appcompat.widget.AppCompatTextView(context, attrs, defStyleAttr) {
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (id != R.id.txtRangeHeader) return
        if (!LicenseManager.isFeatureEnabled(context, "range")) {
            visibility = GONE
            (parent as? android.view.ViewGroup)?.let { group ->
                val index = group.indexOfChild(this)
                if (index >= 0 && index + 1 < group.childCount) {
                    group.getChildAt(index + 1).visibility = View.GONE
                }
            }
        }
    }
}
