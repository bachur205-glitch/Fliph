package com.flipdeck.launcher

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.view.View

/**
 * Base for every Flipdeck screen. Applies the chosen screen density (default 120 dpi) to the
 * launcher's own screens only. Other apps and the rest of the system are not affected.
 */
open class BaseActivity : Activity() {

    private var appliedDensity = 0

    /** White screens set this so the status bar icons are drawn dark (readable on white). */
    protected open val lightScreen = false

    override fun attachBaseContext(newBase: Context) {
        appliedDensity = Prefs(newBase).density
        if (appliedDensity > 0) {
            val config = Configuration(newBase.resources.configuration)
            config.densityDpi = appliedDensity
            super.attachBaseContext(newBase.createConfigurationContext(config))
        } else {
            super.attachBaseContext(newBase)
        }
    }

    /**
     * Hides the on-screen navigation bar. This phone has physical keys, and the bar was leaving a
     * strip at the bottom of the home screen that the softkey bar couldn't reach.
     */
    @Suppress("DEPRECATION")
    private fun hideNavigationBar() {
        var flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        if (lightScreen) flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        val decor = window.decorView
        // Only touch the flags when they actually change; re-applying them causes a relayout.
        if (decor.systemUiVisibility != flags) decor.systemUiVisibility = flags
    }

    /**
     * Screens now draw behind the (transparent) status bar, so no black strip is left at the top.
     * Push the content down by the status bar's height so the status icons don't sit on top of it.
     */
    @Suppress("DEPRECATION")
    override fun setContentView(view: View) {
        super.setContentView(view)
        findViewById<View>(android.R.id.content).setOnApplyWindowInsetsListener { v, insets ->
            val top = insets.systemWindowInsetTop
            if (v.paddingTop != top) v.setPadding(0, top, 0, 0)
            insets
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideNavigationBar()
    }

    override fun onResume() {
        super.onResume()
        hideNavigationBar()
        // The density was changed in settings while this screen was in the background.
        if (appliedDensity != Prefs(this).density) recreate()
    }
}
