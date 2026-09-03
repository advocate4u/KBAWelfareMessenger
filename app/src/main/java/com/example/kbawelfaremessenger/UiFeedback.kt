package com.example.kbawelfaremessenger

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat

/**
 * Centralized, familiar feedback convention used across the app:
 * ERROR = red, SUCCESS = green, WARNING = amber/yellow, INFO = blue.
 */
object UiFeedback {
    enum class Type { ERROR, SUCCESS, WARNING, INFO }

    fun show(context: Context, message: String, type: Type = Type.INFO, long: Boolean = false) {
        val backgroundColor = when (type) {
            Type.ERROR -> ContextCompat.getColor(context, R.color.feedback_error)
            Type.SUCCESS -> ContextCompat.getColor(context, R.color.feedback_success)
            Type.WARNING -> ContextCompat.getColor(context, R.color.feedback_warning)
            Type.INFO -> ContextCompat.getColor(context, R.color.feedback_info)
        }

        val textColor = if (type == Type.WARNING) Color.BLACK else Color.WHITE
        val view = TextView(context).apply {
            text = message
            setTextColor(textColor)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(28, 16, 28, 16)
            background = GradientDrawable().apply {
                setColor(backgroundColor)
                cornerRadius = 18f
            }
        }

        Toast(context).apply {
            duration = if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
            view = view
            setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 80)
        }.show()
    }

    fun error(context: Context, message: String, long: Boolean = false) =
        show(context, message, Type.ERROR, long)

    fun success(context: Context, message: String, long: Boolean = false) =
        show(context, message, Type.SUCCESS, long)

    fun warning(context: Context, message: String, long: Boolean = false) =
        show(context, message, Type.WARNING, long)

    fun info(context: Context, message: String, long: Boolean = false) =
        show(context, message, Type.INFO, long)
}
