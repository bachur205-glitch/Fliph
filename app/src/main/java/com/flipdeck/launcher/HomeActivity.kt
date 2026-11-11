package com.flipdeck.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView

/**
 * Home screen, drawn over the system wallpaper.
 *   OK            -> app menu (hold OK for launcher settings)
 *   D-pad up      -> quick settings
 *   D-pad others  -> whatever you bound them to (nothing is shown on screen for them)
 *   Left softkey  -> notices
 *   Right softkey -> contacts by default (change it in launcher settings)
 */
class HomeActivity : BaseActivity() {

    private lateinit var prefs: Prefs
    private lateinit var noticesLabel: TextView
    private lateinit var hebrewLabel: TextView
    private lateinit var rightLabel: TextView

    // Refreshes the Hebrew date at midnight and when the clock/timezone changes.
    private val dateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refreshLabels()
    }

    // Apps installed / removed / updated: reload the menu's app list in the background.
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = AppList.invalidate(this@HomeActivity)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        setContentView(buildUi())
        // Load the app list now so the menu opens instantly the first time.
        AppList.get(this) { }
        registerReceiver(packageReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        })
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(packageReceiver)
        } catch (e: IllegalArgumentException) {
            // not registered
        }
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        refreshLabels()
        NoticeStore.listener = { runOnUiThread { refreshLabels() } }
        registerReceiver(dateReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        })
    }

    override fun onPause() {
        NoticeStore.listener = null
        try {
            unregisterReceiver(dateReceiver)
        } catch (e: IllegalArgumentException) {
            // not registered
        }
        super.onPause()
    }

    /** Soft shadow so text stays readable on any wallpaper. */
    private fun <T : TextView> T.shade(): T {
        setShadowLayer(5f, 0f, 1f, Color.BLACK)
        return this
    }

    private fun label(size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            setTextColor(color)
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }.shade()

    private fun buildUi(): View {
        val root = FrameLayout(this)
        // Light dark tint over the wallpaper; the wallpaper itself comes from the window.
        root.setBackgroundColor(Color.parseColor("#33000000"))

        // Clock + dates in the middle.
        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val clock = TextClock(this).apply {
            format12Hour = "h:mm"
            format24Hour = "HH:mm"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 56f)
            setTextColor(Ui.TEXT)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            gravity = Gravity.CENTER
        }.shade()
        val date = TextClock(this).apply {
            format12Hour = "EEE, MMM d"
            format24Hour = "EEE, MMM d"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(Color.parseColor("#E3EBF3"))
            gravity = Gravity.CENTER
        }.shade()
        hebrewLabel = label(16f, Color.WHITE).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, 0)
        }
        center.addView(clock)
        center.addView(date)
        center.addView(hebrewLabel)
        center.setPadding(0, dp(6), 0, 0)
        root.addView(center, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.TOP))

        // Bottom: tagline above the softkey bar.
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#B31A222C"))
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        noticesLabel = label(16f, Ui.TEXT, true).apply { gravity = Gravity.START }
        val menu = label(16f, Ui.ACCENT, true).apply {
            text = "MENU"
            gravity = Gravity.CENTER
        }
        rightLabel = label(16f, Ui.TEXT, true).apply {
            gravity = Gravity.END
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        for (v in listOf(noticesLabel, menu, rightLabel)) {
            bar.addView(v, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }
        val tagline = label(20f, Color.WHITE, true).apply {
            typeface = Typeface.create("cursive", Typeface.BOLD)
            text = "We want Moshiach now!"
            gravity = Gravity.CENTER
            setPadding(dp(4), 0, dp(4), dp(8))
        }
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(tagline)
            addView(bar)
        }
        root.addView(bottom, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM))
        return root
    }

    private fun refreshLabels() {
        hebrewLabel.text = HebrewDate.today(prefs.hebrewScript)
        rightLabel.text = Actions.shortLabel(this, prefs.getBinding(Prefs.SOFT_RIGHT))
        val n = NoticeStore.active().size
        noticesLabel.text = if (n > 0) "Notices ($n)" else "Notices"
    }

    // --- Keys -------------------------------------------------------------

    private fun slotFor(code: Int): String? = when (code) {
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> "menu"
        KeyEvent.KEYCODE_DPAD_UP -> "up"
        KeyEvent.KEYCODE_DPAD_DOWN -> Prefs.DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> Prefs.LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> Prefs.RIGHT
        prefs.softLeftKey -> "softleft"
        prefs.softRightKey -> "softright"
        else -> null
    }

    /** The character a keypad key types, or null for any other key. */
    private fun dialCharFor(code: Int): String? = when (code) {
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> (code - KeyEvent.KEYCODE_0).toString()
        in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 ->
            (code - KeyEvent.KEYCODE_NUMPAD_0).toString()
        KeyEvent.KEYCODE_STAR -> "*"
        KeyEvent.KEYCODE_POUND -> "#"
        else -> null
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Typing a number on the home screen opens the dialer with those digits already entered.
        val dialChar = dialCharFor(keyCode)
        if (dialChar != null) {
            if (event.repeatCount == 0) Actions.dial(this, dialChar)
            return true
        }
        if (slotFor(keyCode) != null || keyCode == KeyEvent.KEYCODE_BACK) {
            event.startTracking()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (slotFor(keyCode) == "menu") {
            startActivity(Intent(this, LauncherSettingsActivity::class.java))
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        // Back does nothing on the home screen.
        if (keyCode == KeyEvent.KEYCODE_BACK) return true
        val slot = slotFor(keyCode) ?: return super.onKeyUp(keyCode, event)
        if (event.isCanceled) return true // a long press already handled it
        when (slot) {
            "menu" -> Actions.run(this, Actions.MENU)
            "up" -> Actions.run(this, Actions.QUICK)
            "softleft" -> Actions.run(this, Actions.NOTICES)
            "softright" -> Actions.run(this, prefs.getBinding(Prefs.SOFT_RIGHT))
            else -> Actions.run(this, prefs.getBinding(slot))
        }
        return true
    }
}
