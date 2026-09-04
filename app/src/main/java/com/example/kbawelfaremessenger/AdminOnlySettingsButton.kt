package com.example.kbawelfaremessenger

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatButton

/** Settings is an administrator-only entry point. */
class AdminOnlySettingsButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.buttonStyle
) : AppCompatButton(context, attrs, defStyleAttr) {
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        visibility = if (SecurityManager.isAdmin(context)) VISIBLE else GONE
    }
}
