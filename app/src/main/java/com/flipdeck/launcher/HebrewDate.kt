package com.flipdeck.launcher

import android.icu.text.DateFormat
import android.icu.util.ULocale
import java.util.Date

/** Today's Hebrew-calendar date, from the phone's built-in ICU data (no network needed). */
object HebrewDate {
    fun today(hebrewScript: Boolean): String = try {
        val locale = ULocale(if (hebrewScript) "he@calendar=hebrew" else "en@calendar=hebrew")
        DateFormat.getDateInstance(DateFormat.LONG, locale).format(Date())
    } catch (e: Exception) {
        ""
    }
}
