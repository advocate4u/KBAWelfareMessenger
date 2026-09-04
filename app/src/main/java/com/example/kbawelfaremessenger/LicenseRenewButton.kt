package com.example.kbawelfaremessenger

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatImageButton
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class LicenseRenewButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageButton(context, attrs, defStyleAttr) {

    init {
        contentDescription = "Renew license"
        setOnClickListener {
            context.startActivity(Intent(context, RenewLicenseActivity::class.java))
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshVisibility()
    }

    fun refreshVisibility() {
        val license = LicenseManager.getInstalledLicense(context)
        val days = license?.let { ChronoUnit.DAYS.between(LocalDate.now(), it.expiryDate) }
        visibility = if (days != null && days >= 0 && days < 7) View.VISIBLE else View.GONE
    }
}
