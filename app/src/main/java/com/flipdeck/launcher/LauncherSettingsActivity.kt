package com.flipdeck.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

/** Rebind D-pad directions and the right softkey, teach the launcher your softkeys, change icon size. */
class LauncherSettingsActivity : BaseActivity() {

    private class Row(val title: String, val value: String, val onClick: () -> Unit)

    private companion object {
        const val REQ_PICK_APP = 1
    }

    private lateinit var prefs: Prefs
    private lateinit var list: ListView
    private var rows: List<Row> = emptyList()
    private var pendingSlot: String? = null

    private val rowAdapter = object : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(position: Int): Any = rows[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val ctx = this@LauncherSettingsActivity
            val r = rows[position]
            val v = (convertView as? LinearLayout) ?: LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(8), dp(12), dp(8))
                addView(TextView(ctx).apply {
                    setTextColor(Ui.TEXT)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                })
                addView(TextView(ctx).apply {
                    setTextColor(Ui.ACCENT)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                })
            }
            (v.getChildAt(0) as TextView).text = r.title
            (v.getChildAt(1) as TextView).text = r.value
            return v
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        list = ListView(this).apply {
            Ui.tuneList(this)
            divider = null
            selector = Ui.selector(this@LauncherSettingsActivity)
            adapter = rowAdapter
            setOnItemClickListener { _, _, position, _ -> rows[position].onClick() }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
        }
        root.addView(Ui.header(this, "Launcher settings"))
        root.addView(list, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refresh()
        list.requestFocus()
    }

    private fun refresh() {
        rows = listOf(
            Row("Down key", Actions.label(this, prefs.getBinding(Prefs.DOWN))) {
                pickAction(Prefs.DOWN, "Down key")
            },
            Row("Left key", Actions.label(this, prefs.getBinding(Prefs.LEFT))) {
                pickAction(Prefs.LEFT, "Left key")
            },
            Row("Right key", Actions.label(this, prefs.getBinding(Prefs.RIGHT))) {
                pickAction(Prefs.RIGHT, "Right key")
            },
            Row("Right softkey action", Actions.label(this, prefs.getBinding(Prefs.SOFT_RIGHT))) {
                pickAction(Prefs.SOFT_RIGHT, "Right softkey action")
            },
            Row("Left softkey button (opens notices)", keyName(prefs.softLeftKey)) { learnKey(true) },
            Row("Right softkey button", keyName(prefs.softRightKey)) { learnKey(false) },
            Row("Menu look", if (prefs.menuList) "List" else "Grid") {
                prefs.menuList = !prefs.menuList
                refresh()
            },
            Row(
                "Menu icon size",
                when {
                    prefs.menuList && prefs.gridColumns == 2 -> "Huge"
                    prefs.menuList -> "Large"
                    prefs.gridColumns == 2 -> "Huge (2 per row)"
                    else -> "Large (3 per row)"
                }
            ) {
                prefs.gridColumns = if (prefs.gridColumns == 2) 3 else 2
                refresh()
            },
            Row(
                "Screen density",
                if (prefs.density == 0) "System default" else "${prefs.density} dpi"
            ) {
                prefs.density = when (prefs.density) {
                    120 -> 140
                    140 -> 160
                    160 -> 0
                    else -> 120
                }
                recreate()
            },
            Row(
                "Hide / unhide apps",
                if (prefs.hiddenApps.isEmpty()) "None hidden" else "${prefs.hiddenApps.size} hidden"
            ) {
                startActivity(
                    Intent(this, AppGridActivity::class.java)
                        .putExtra(AppGridActivity.EXTRA_MANAGE, true)
                )
            },
            Row(
                "Hebrew date",
                if (prefs.hebrewScript) "Hebrew letters" else "English letters"
            ) {
                prefs.hebrewScript = !prefs.hebrewScript
                refresh()
            },
            Row("Default home app", "Open system chooser") {
                try {
                    startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                } catch (e: Exception) {
                    Actions.toast(this, "Can't open that")
                }
            }
        )
        rowAdapter.notifyDataSetChanged()
    }

    private fun keyName(code: Int): String = KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")

    private fun pickAction(slot: String, title: String) {
        val labels = Actions.choices.map { it.second } + "Launch an app..."
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(labels.toTypedArray()) { _, which ->
                if (which < Actions.choices.size) {
                    prefs.setBinding(slot, Actions.choices[which].first)
                    refresh()
                } else {
                    pendingSlot = slot
                    val pick = Intent(this, AppGridActivity::class.java)
                        .putExtra(AppGridActivity.EXTRA_PICK, true)
                    @Suppress("DEPRECATION")
                    startActivityForResult(pick, REQ_PICK_APP)
                }
            }
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_APP && resultCode == RESULT_OK) {
            val pkg = data?.getStringExtra(AppGridActivity.EXTRA_PACKAGE)
            val slot = pendingSlot
            if (pkg != null && slot != null) {
                prefs.setBinding(slot, Actions.APP_PREFIX + pkg)
                refresh()
            }
        }
    }

    /** Waits for you to press the physical key you want to use as a softkey. */
    private fun learnKey(left: Boolean) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (left) "Left softkey" else "Right softkey")
            .setMessage("Press the key now.\n(Back cancels)")
            .create()
        dialog.setOnKeyListener { d, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) d.dismiss()
            } else if (event.action == KeyEvent.ACTION_UP) {
                if (left) prefs.softLeftKey = keyCode else prefs.softRightKey = keyCode
                d.dismiss()
                Actions.toast(this, "Saved: " + keyName(keyCode))
                refresh()
            }
            true
        }
        dialog.show()
    }
}
