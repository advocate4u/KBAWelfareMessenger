package com.example.kbawelfaremessenger

import android.content.Context
import android.util.AttributeSet
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Keeps screen content below the system status bar / punch-hole camera area.
 * This is important for targetSdk 35 where edge-to-edge is enforced.
 */
class SafeInsetScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : androidx.core.widget.NestedScrollView(context, attrs, defStyleAttr) {

    private var baseTop = 0
    private var baseBottom = 0

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        baseTop = paddingTop
        baseBottom = paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or
                    WindowInsetsCompat.Type.displayCutout()
            )
            view.updatePadding(
                top = baseTop + systemBars.top,
                bottom = baseBottom + insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(this)
    }
}
