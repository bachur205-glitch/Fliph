package com.flipdeck.launcher

import android.content.Context
import android.content.SharedPreferences
import android.view.KeyEvent

class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("flipdeck", Context.MODE_PRIVATE)

    /** Action bound to a customizable key: a D-pad direction (DOWN, LEFT, RIGHT) or SOFT_RIGHT. */
    fun getBinding(slot: String): String =
        sp.getString("bind_$slot", null) ?: DEFAULTS[slot] ?: Actions.NONE

    fun setBinding(slot: String, action: String) {
        sp.edit().putString("bind_$slot", action).apply()
    }

    var softLeftKey: Int
        get() = sp.getInt("soft_left", KeyEvent.KEYCODE_SOFT_LEFT)
        set(value) { sp.edit().putInt("soft_left", value).apply() }

    var softRightKey: Int
        get() = sp.getInt("soft_right", KeyEvent.KEYCODE_SOFT_RIGHT)
        set(value) { sp.edit().putInt("soft_right", value).apply() }

    /** Packages hidden from the app menu (still launchable through D-pad key bindings). */
    var hiddenApps: Set<String>
        get() = sp.getStringSet("hidden", null)?.toSet() ?: emptySet()
        set(value) { sp.edit().putStringSet("hidden", HashSet(value)).apply() }

    /** Launcher screen density in dpi. 0 = use the system's own density. */
    var density: Int
        get() = sp.getInt("density", 120)
        set(value) { sp.edit().putInt("density", value).apply() }

    /** true = Hebrew letters, false = English spelling (e.g. "Cheshvan"). */
    var hebrewScript: Boolean
        get() = sp.getBoolean("hebrew_script", true)
        set(value) { sp.edit().putBoolean("hebrew_script", value).apply() }

    /** 3 = large icons, 2 = huge icons. */
    var gridColumns: Int
        get() = sp.getInt("columns", 3)
        set(value) { sp.edit().putInt("columns", value).apply() }

    /** App menu look: false = icon grid, true = list (icon + name per row). */
    var menuList: Boolean
        get() = sp.getBoolean("menu_list", false)
        set(value) { sp.edit().putBoolean("menu_list", value).apply() }

    companion object {
        const val DOWN = "down"
        const val LEFT = "left"
        const val RIGHT = "right"
        const val SOFT_RIGHT = "softright"

        private val DEFAULTS = mapOf(
            DOWN to Actions.DIALER,
            LEFT to Actions.MESSAGES,
            RIGHT to Actions.CAMERA,
            SOFT_RIGHT to Actions.CONTACTS
        )
    }
}
