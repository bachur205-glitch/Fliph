package com.flipdeck.launcher

import android.app.Activity
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

/**
 * Notification list. D-pad up/down to move, OK opens the notice and clears it.
 * Erase just the selected notice with the right softkey (or the clear / delete key, or by holding OK).
 */
class NoticesActivity : BaseActivity() {

    override val lightScreen = true

    private class Row(
        val kind: Int,
        val sbn: StatusBarNotification?,
        val text: String,
        val music: NowPlaying.Info? = null
    )

    private companion object {
        const val NOTE = 0
        const val CLEAR = 1
        const val GRANT = 2
        const val EMPTY = 3
        const val MUSIC = 4
    }

    private var rows: List<Row> = emptyList()
    private lateinit var list: ListView
    private val handler = Handler(Looper.getMainLooper())
    private var lastMusic = ""
    private lateinit var prefs: Prefs
    private lateinit var footerLeft: TextView
    private lateinit var footerRight: TextView

    // Notices erased one by one. Android removes them a moment later, so hide them right away.
    private val erased = HashSet<String>()

    // Keeps the music widget current (track changes, play/pause from elsewhere).
    private val musicTicker = object : Runnable {
        override fun run() {
            val now = if (NoticeStore.isEnabled(this@NoticesActivity)) {
                NowPlaying.current(this@NoticesActivity)?.signature ?: ""
            } else ""
            if (now != lastMusic) refresh()
            handler.postDelayed(this, 1000)
        }
    }

    private fun tv(text: CharSequence, size: Float, color: Int, bold: Boolean = false, lines: Int = 1) =
        TextView(this).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            setTextColor(color)
            maxLines = lines
            ellipsize = TextUtils.TruncateAt.END
            if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

    private val rowAdapter = object : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(position: Int): Any = rows[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val ctx = this@NoticesActivity
            val r = rows[position]
            val v = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), dp(8), dp(10), dp(8))
            }
            if (r.kind == MUSIC && r.music != null) {
                val m = r.music
                v.addView(tv(if (m.playing) "NOW PLAYING" else "PAUSED", 12f, Ui.L_ACCENT, true))
                v.addView(tv(m.title, 16f, Ui.L_TEXT, true))
                if (m.artist.isNotEmpty()) v.addView(tv(m.artist, 14f, Ui.L_SUB))
                v.addView(
                    tv(
                        if (m.playing) "OK: pause    < prev   next >" else "OK: play    < prev   next >",
                        12f,
                        Ui.L_ON
                    ).apply { setPadding(0, dp(4), 0, 0) }
                )
            } else if (r.kind == NOTE && r.sbn != null) {
                val n = r.sbn
                val extras = n.notification.extras
                v.addView(tv(appName(n.packageName), 12f, Ui.L_ACCENT))
                v.addView(
                    tv(extras.getCharSequence(Notification.EXTRA_TITLE) ?: "", 16f, Ui.L_TEXT, true)
                )
                v.addView(
                    tv(extras.getCharSequence(Notification.EXTRA_TEXT) ?: "", 14f, Ui.L_SUB, false, 2)
                )
            } else {
                v.addView(
                    tv(r.text, 18f, Ui.L_TEXT, true).apply { gravity = Gravity.CENTER }
                )
            }
            return v
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        list = ListView(this).apply {
            Ui.tuneList(this)
            divider = null
            selector = Ui.selector(this@NoticesActivity, light = true)
            adapter = rowAdapter
            setOnItemClickListener { _, _, position, _ -> onRowChosen(rows[position]) }
            setOnItemLongClickListener { _, _, position, _ ->
                if (rows[position].kind == NOTE) {
                    eraseSelected()
                    true
                } else false
            }
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) =
                    updateFooter()

                override fun onNothingSelected(parent: AdapterView<*>?) = updateFooter()
            }
        }
        footerLeft = tv("", 14f, Ui.L_TEXT, true).apply { gravity = Gravity.START }
        footerRight = tv("", 14f, Ui.L_TEXT, true).apply { gravity = Gravity.END }
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Ui.L_PANEL)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            addView(footerLeft, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(footerRight, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.L_BG)
        }
        root.addView(Ui.header(this, "Notices", light = true))
        root.addView(list, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        root.addView(footer)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        NoticeStore.listener = { runOnUiThread { refresh() } }
        refresh()
        list.requestFocus()
        handler.postDelayed(musicTicker, 1000)
    }

    override fun onPause() {
        handler.removeCallbacks(musicTicker)
        NoticeStore.listener = null
        super.onPause()
    }

    private fun selectedRow(): Row? = rows.getOrNull(list.selectedItemPosition)

    /** Softkey bar at the bottom: what OK and the right softkey do on the selected row. */
    private fun updateFooter() {
        when (selectedRow()?.kind) {
            NOTE -> {
                footerLeft.text = "OK: open"
                footerRight.text = "Erase"
            }
            MUSIC -> {
                footerLeft.text = "OK: play / pause"
                footerRight.text = ""
            }
            CLEAR -> {
                footerLeft.text = "OK: clear all"
                footerRight.text = ""
            }
            GRANT -> {
                footerLeft.text = "OK: allow"
                footerRight.text = ""
            }
            else -> {
                footerLeft.text = ""
                footerRight.text = ""
            }
        }
    }

    /** Erases the selected notice only (not the whole list). */
    private fun eraseSelected() {
        val pos = list.selectedItemPosition
        val n = rows.getOrNull(pos)?.takeIf { it.kind == NOTE }?.sbn ?: return
        try {
            NoticeStore.service?.cancelNotification(n.key)
        } catch (e: Exception) {
            // ignore
        }
        erased.add(n.key)
        refresh()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Right softkey (or the clear / delete key) erases the selected notice.
        val eraseKey = event.keyCode == prefs.softRightKey ||
            event.keyCode == KeyEvent.KEYCODE_CLEAR ||
            event.keyCode == KeyEvent.KEYCODE_DEL
        if (eraseKey && selectedRow()?.kind == NOTE) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) eraseSelected()
            return true
        }
        // On the music widget, left / right skip tracks.
        val music = selectedRow()?.takeIf { it.kind == MUSIC }?.music
        if (music != null && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    music.previous()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    music.next()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun refresh() {
        val keep = list.selectedItemPosition
        val r = mutableListOf<Row>()
        lastMusic = ""
        if (!NoticeStore.isEnabled(this)) {
            r.add(Row(GRANT, null, "Press OK to allow notice access"))
        } else {
            NowPlaying.current(this)?.let {
                lastMusic = it.signature
                r.add(Row(MUSIC, null, "", it))
            }
            val all = NoticeStore.active()
            erased.retainAll(all.map { it.key }.toSet()) // forget ones Android has now removed
            val active = all.filter { it.key !in erased }
            if (active.isEmpty()) {
                r.add(Row(EMPTY, null, "No notices"))
            } else {
                active.forEach { r.add(Row(NOTE, it, "")) }
                r.add(Row(CLEAR, null, "Clear all"))
            }
        }
        rows = r
        rowAdapter.notifyDataSetChanged()
        // Stay where you were (so erasing several in a row doesn't jump back to the top).
        if (keep >= 0 && r.isNotEmpty()) list.setSelection(keep.coerceAtMost(r.size - 1))
        updateFooter()
    }

    private fun onRowChosen(r: Row) {
        when (r.kind) {
            MUSIC -> {
                r.music?.playPause()
                // Give the player a moment, then redraw so the widget shows the new state.
                handler.postDelayed({ refresh() }, 400)
            }
            GRANT -> try {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (e: Exception) {
                Actions.toast(this, "Can't open that")
            }
            CLEAR -> {
                try {
                    NoticeStore.service?.cancelAllNotifications()
                } catch (e: Exception) {
                    // ignore
                }
                refresh()
            }
            NOTE -> r.sbn?.let { n ->
                try {
                    n.notification.contentIntent?.send()
                } catch (e: PendingIntent.CanceledException) {
                    Actions.toast(this, "Can't open that")
                }
                try {
                    NoticeStore.service?.cancelNotification(n.key)
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    private fun appName(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }
}
