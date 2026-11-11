package com.flipdeck.launcher

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.graphics.PorterDuff
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Big toggle tiles. Android doesn't let normal apps flip Wi-Fi, mobile data, hotspot or airplane
 * mode directly, so those tiles show the live state and open the one-tap system screen for it.
 * Bluetooth is toggled directly (falls back to its settings screen if the phone refuses).
 */
class QuickSettingsActivity : BaseActivity() {

    override val lightScreen = true

    private companion object {
        const val MUSIC = 5
    }

    // Same order as the tiles: Wi-Fi, Bluetooth, mobile data, hotspot, airplane mode, music.
    private val names = listOf("Wi-Fi", "Bluetooth", "Mobile data", "Hotspot", "Airplane mode", "Music")
    private val icons = listOf(
        R.drawable.ic_qs_wifi,
        R.drawable.ic_qs_bluetooth,
        R.drawable.ic_qs_data,
        R.drawable.ic_qs_hotspot,
        R.drawable.ic_qs_airplane,
        R.drawable.ic_qs_playpause
    )
    private val handler = Handler(Looper.getMainLooper())

    // Last drawn on/off state of each tile; tiles are only redrawn when one of these changes.
    private var shownStates: List<Boolean?> = emptyList()

    private val ticker = object : Runnable {
        override fun run() {
            val now = names.indices.map { stateOf(it) }
            if (now != shownStates) {
                shownStates = now
                tileAdapter.notifyDataSetChanged()
            }
            handler.postDelayed(this, 800)
        }
    }

    private val tileAdapter = object : BaseAdapter() {
        override fun getCount() = names.size
        override fun getItem(position: Int): Any = names[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val ctx = this@QuickSettingsActivity
            val tile = (convertView as? LinearLayout) ?: LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                minimumHeight = dp(84)
                setPadding(dp(4), dp(8), dp(4), dp(8))
                addView(ImageView(ctx).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
                })
                addView(TextView(ctx).apply {
                    gravity = Gravity.CENTER
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setPadding(0, dp(4), 0, 0)
                })
            }
            val state = shownStates.getOrNull(position) ?: stateOf(position)
            // Icon tinted by state: green = on, grey = off, dark = can't tell.
            (tile.getChildAt(0) as ImageView).apply {
                setImageResource(icons[position])
                setColorFilter(
                    when (state) {
                        true -> Ui.L_ON
                        false -> Ui.L_SUB
                        null -> Ui.L_TEXT
                    },
                    PorterDuff.Mode.SRC_IN
                )
            }
            (tile.getChildAt(1) as TextView).apply {
                text = if (position == MUSIC) {
                    // Tapping OK sends play/pause to whatever is playing.
                    if (state == true) "Playing" else "Paused"
                } else when (state) {
                    true -> "ON"
                    false -> "OFF"
                    null -> "Open"
                }
                setTextColor(if (state == true) Ui.L_ON else Ui.L_SUB)
            }
            return tile
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val grid = GridView(this).apply {
            Ui.tuneList(this)
            numColumns = 2
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            verticalSpacing = dp(6)
            horizontalSpacing = dp(6)
            setPadding(dp(6), dp(8), dp(6), dp(8))
            clipToPadding = false
            selector = Ui.selector(this@QuickSettingsActivity, light = true)
            setDrawSelectorOnTop(false)
            adapter = tileAdapter
            setOnItemClickListener { _, _, position, _ -> toggle(position) }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.L_BG)
        }
        root.addView(Ui.header(this, "Quick settings", light = true))
        root.addView(grid, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        setContentView(root)
        grid.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    // --- State ------------------------------------------------------------

    /** true = on, false = off, null = can't tell. */
    private fun stateOf(index: Int): Boolean? = try {
        when (index) {
            0 -> (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled
            1 -> BluetoothAdapter.getDefaultAdapter()?.isEnabled
            2 -> (getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).isDataEnabled
            3 -> hotspotOn()
            4 -> Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
            MUSIC -> (getSystemService(Context.AUDIO_SERVICE) as AudioManager).isMusicActive
            else -> null
        }
    } catch (e: Exception) {
        null
    }

    private fun hotspotOn(): Boolean? = try {
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val m = WifiManager::class.java.getDeclaredMethod("isWifiApEnabled")
        m.isAccessible = true
        m.invoke(wm) as Boolean
    } catch (e: Throwable) {
        null // hidden API, not available on every build
    }

    // --- Actions ----------------------------------------------------------

    private fun toggle(index: Int) {
        when (index) {
            0 -> openFirst(
                Intent("android.settings.panel.action.WIFI"),
                Intent(Settings.ACTION_WIFI_SETTINGS)
            )
            1 -> toggleBluetooth()
            2 -> openFirst(
                Intent("android.settings.panel.action.INTERNET_CONNECTIVITY"),
                Intent(Settings.ACTION_DATA_ROAMING_SETTINGS),
                Intent(Settings.ACTION_WIRELESS_SETTINGS)
            )
            3 -> openFirst(
                Intent().setClassName("com.android.settings", "com.android.settings.TetherSettings"),
                Intent(Settings.ACTION_WIRELESS_SETTINGS)
            )
            4 -> openFirst(
                Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS),
                Intent(Settings.ACTION_WIRELESS_SETTINGS)
            )
            MUSIC -> Actions.run(this, Actions.PLAYPAUSE)
        }
    }

    @Suppress("DEPRECATION")
    private fun toggleBluetooth() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        val done = try {
            when {
                adapter == null -> false
                adapter.isEnabled -> adapter.disable()
                else -> adapter.enable()
            }
        } catch (e: SecurityException) {
            false
        }
        if (!done) openFirst(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    }

    /** Opens the first intent the phone can actually handle. */
    private fun openFirst(vararg intents: Intent) {
        for (i in intents) {
            try {
                startActivity(i)
                return
            } catch (e: Exception) {
                // try the next one
            }
        }
        Actions.toast(this, "Can't open that")
    }
}
