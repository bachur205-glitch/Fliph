package com.flipdeck.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.AbsListView
import android.widget.TextView

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

object Ui {
    // Dark palette: home screen, app menu, launcher settings.
    val BG: Int = Color.parseColor("#0B0F14")
    val PANEL: Int = Color.parseColor("#1A222C")
    val TEXT: Int = Color.WHITE
    val SUB: Int = Color.parseColor("#9FB3C8")
    val ACCENT: Int = Color.parseColor("#4FC3F7")
    val ON: Int = Color.parseColor("#66E08A")

    // Light palette: Notices and Quick settings (white background).
    val L_BG: Int = Color.WHITE
    val L_PANEL: Int = Color.parseColor("#F1F3F6")
    val L_TEXT: Int = Color.parseColor("#111418")
    val L_SUB: Int = Color.parseColor("#5F6B78")
    val L_ACCENT: Int = Color.parseColor("#0277BD")
    val L_ON: Int = Color.parseColor("#1E9E4A")
    val L_HIGHLIGHT: Int = Color.parseColor("#E1F0FA")

    /** Package that must never appear anywhere in the launcher (menu, pickers, notices). */
    const val BLOCKED_PACKAGE = "com.moshe.flipmdm"

    /** Highlight drawn behind the currently selected D-pad item. */
    fun selector(ctx: Context, light: Boolean = false): Drawable = GradientDrawable().apply {
        setColor(if (light) L_HIGHLIGHT else Color.parseColor("#223244"))
        cornerRadius = ctx.dp(12).toFloat()
        setStroke(ctx.dp(2), if (light) L_ACCENT else ACCENT)
    }

    fun header(ctx: Context, title: String, light: Boolean = false): TextView = TextView(ctx).apply {
        text = title
        setTextColor(if (light) L_TEXT else TEXT)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setBackgroundColor(if (light) L_PANEL else PANEL)
        setPadding(ctx.dp(8), ctx.dp(8), ctx.dp(8), ctx.dp(8))
    }

    /** Settings shared by every list/grid so D-pad scrolling stays smooth. */
    fun tuneList(list: AbsListView) {
        list.isScrollingCacheEnabled = true
        list.isSmoothScrollbarEnabled = true
        list.cacheColorHint = Color.TRANSPARENT
        list.overScrollMode = View.OVER_SCROLL_NEVER
        list.isVerticalScrollBarEnabled = false
        list.isFocusable = true
    }
}
