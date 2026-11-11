package com.flipdeck.launcher

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.animation.Animation
import android.view.animation.DecelerateInterpolator
import android.view.animation.TranslateAnimation
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

/**
 * Your launchable apps, as a big-icon grid or a list (Launcher settings > Menu look).
 * D-pad moves, OK opens, hold OK hides the app.
 *
 * EXTRA_PICK   returns the chosen package (used when binding a D-pad key to an app; shows hidden apps too).
 * EXTRA_MANAGE shows every app, hidden ones dimmed; OK hides or unhides the selected app.
 */
class AppGridActivity : BaseActivity() {

    companion object {
        const val EXTRA_PICK = "pick"
        const val EXTRA_MANAGE = "manage"
        const val EXTRA_PACKAGE = "package"
        private const val SETTINGS_LABEL = "Launcher settings"
    }

    private class Entry(
        val label: String,
        val pkg: String?,
        val component: ComponentName?,
        val icon: Drawable?,
        var hidden: Boolean = false
    )

    private val items = ArrayList<Entry>()
    private lateinit var prefs: Prefs
    private lateinit var grid: AbsListView
    private lateinit var header: TextView
    private var pickMode = false
    private var manageMode = false
    private var listLook = false
    private var columns = 3
    private var iconSize = 0
    private var shownSignature = ""

    private val gridAdapter = object : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun hasStableIds() = false

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val cell = (convertView as? LinearLayout) ?: newCell()
            val e = items[position]
            val dimmed = manageMode && e.hidden
            val icon = cell.getChildAt(0) as ImageView
            icon.setImageDrawable(e.icon)
            // Adaptive icons already fill the circle; older square icons get some breathing room.
            val pad = if (e.icon is AdaptiveIconDrawable) 0 else (iconSize * 0.16f).toInt()
            icon.setPadding(pad, pad, pad, pad)
            icon.alpha = if (dimmed) 0.3f else 1f
            val name = cell.getChildAt(1) as TextView
            name.text = if (dimmed) e.label + " (hidden)" else e.label
            name.setTextColor(if (dimmed) Ui.SUB else Ui.TEXT)
            return cell
        }
    }

    /** One grid tile (icon above name) or one list row (icon left of name). */
    private fun newCell(): LinearLayout {
        val ctx = this
        val cell = LinearLayout(ctx).apply {
            if (listLook) {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(6), dp(10), dp(6))
            } else {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(2), dp(8), dp(2), dp(6))
            }
        }
        val icon = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
            // Round icons: a circular backing that also clips whatever is drawn in it.
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#E8EEF4"))
            }
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
        }
        val name = TextView(ctx).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(Ui.TEXT)
            setShadowLayer(4f, 0f, 1f, Color.BLACK)
            if (listLook) {
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                setTextSize(TypedValue.COMPLEX_UNIT_SP, if (columns == 2) 20f else 17f)
                setPadding(dp(12), 0, 0, 0)
            } else {
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, if (columns == 2) 15f else 12f)
                setPadding(0, dp(4), 0, 0)
            }
        }
        cell.addView(icon)
        if (listLook) {
            cell.addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        } else {
            cell.addView(name)
        }
        return cell
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No system transition; the menu slides itself up (see below), which works even when the
        // phone has system animations turned off.
        overridePendingTransition(R.anim.stay, R.anim.stay)
        prefs = Prefs(this)
        pickMode = intent.getBooleanExtra(EXTRA_PICK, false)
        manageMode = intent.getBooleanExtra(EXTRA_MANAGE, false)
        listLook = prefs.menuList
        columns = prefs.gridColumns
        iconSize = if (listLook) {
            dp(if (columns == 2) 56 else 42)
        } else {
            (resources.displayMetrics.widthPixels / columns * 0.6f).toInt()
        }

        grid = if (listLook) {
            ListView(this).apply {
                divider = null
                dividerHeight = 0
                setPadding(dp(4), dp(4), dp(4), dp(4))
            }
        } else {
            GridView(this).apply {
                numColumns = columns
                stretchMode = GridView.STRETCH_COLUMN_WIDTH
                verticalSpacing = dp(6)
                horizontalSpacing = dp(4)
                setPadding(dp(4), dp(6), dp(4), dp(6))
            }
        }
        grid.apply {
            Ui.tuneList(this)
            clipToPadding = false
            selector = Ui.selector(this@AppGridActivity)
            setDrawSelectorOnTop(false)
            adapter = gridAdapter
            setOnItemClickListener { _, _, position, _ -> items.getOrNull(position)?.let { onEntryChosen(it) } }
            setOnItemLongClickListener { _, _, position, _ ->
                items.getOrNull(position)?.let { onEntryLongPressed(it) } ?: false
            }
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) =
                    updateHeader()

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }

        val title = when {
            pickMode -> "Pick an app"
            manageMode -> "Hide / unhide apps"
            else -> "All apps"
        }
        // Light dark tint over the wallpaper so icons and names stay readable.
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#66000000"))
        }
        // Top bar: the name of the selected app (or the screen title). See-through, text shadowed.
        header = Ui.header(this, title).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setShadowLayer(5f, 0f, 1f, Color.BLACK)
        }
        root.addView(header)
        root.addView(grid, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        setContentView(root)

        // Show what's already in memory right away, so the menu is never empty while sliding up.
        AppList.peek()?.let { showApps(it) }

        // Slide the whole menu up from the bottom of the screen, drawn on the GPU for smoothness.
        root.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        root.startAnimation(
            TranslateAnimation(
                Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, 0f,
                Animation.RELATIVE_TO_PARENT, 1f, Animation.RELATIVE_TO_PARENT, 0f
            ).apply {
                duration = 220
                interpolator = DecelerateInterpolator(1.6f)
                setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationStart(a: Animation?) = Unit
                    override fun onAnimationRepeat(a: Animation?) = Unit
                    override fun onAnimationEnd(a: Animation?) {
                        root.setLayerType(View.LAYER_TYPE_NONE, null)
                    }
                })
            }
        )
    }

    override fun onResume() {
        super.onResume()
        // Cheap: uses the in-memory list. Picks up changes made in the hide/unhide screen.
        AppList.get(this) { showApps(it) }
    }

    /** Rebuilds the visible entries. Does nothing if they haven't changed, so there's no flicker. */
    private fun showApps(apps: List<AppList.App>) {
        val hiddenSet = prefs.hiddenApps
        val entries = apps
            .map { Entry(it.label, it.pkg, it.component, it.icon, it.pkg in hiddenSet) }
            .filter { manageMode || pickMode || !it.hidden }
            .toMutableList()
        if (!pickMode && !manageMode) {
            // Sorted in with the apps alphabetically, not pinned to the top.
            entries.add(Entry(SETTINGS_LABEL, null, null, getDrawable(android.R.drawable.ic_menu_preferences)))
            entries.sortBy { it.label.lowercase() }
        }
        val signature = entries.joinToString("|") { "${it.component}:${it.hidden}:${it.label}" }
        if (signature == shownSignature) return
        val firstLoad = items.isEmpty()
        shownSignature = signature

        val keep = grid.selectedItemPosition
        items.clear()
        items.addAll(entries)
        gridAdapter.notifyDataSetChanged()
        grid.setSelection(if (!firstLoad && keep in 0 until items.size) keep else 0)
        grid.requestFocus()
        updateHeader()
    }

    /** At the end of a row, right goes on to the first icon of the next row (and left goes back). */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!listLook && event.action == KeyEvent.ACTION_DOWN && ::grid.isInitialized) {
            val pos = grid.selectedItemPosition
            if (pos >= 0) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_RIGHT ->
                        if (pos % columns == columns - 1 && pos + 1 < items.size) {
                            grid.setSelection(pos + 1)
                            return true
                        }
                    KeyEvent.KEYCODE_DPAD_LEFT ->
                        if (pos % columns == 0 && pos > 0) {
                            grid.setSelection(pos - 1)
                            return true
                        }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun updateHeader() {
        // In the list look every name is already on screen, so the bar keeps its title.
        if (pickMode || manageMode || listLook) return
        val i = grid.selectedItemPosition
        val text = if (i in 0 until items.size) items[i].label else ""
        if (header.text.toString() != text) header.text = text
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.stay, R.anim.slide_down)
    }

    private fun onEntryChosen(e: Entry) {
        when {
            e.pkg == null -> startActivity(Intent(this, LauncherSettingsActivity::class.java))
            manageMode -> setHidden(e, !e.hidden)
            pickMode -> {
                setResult(RESULT_OK, Intent().putExtra(EXTRA_PACKAGE, e.pkg))
                finish()
            }
            else -> {
                val launch = Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(e.component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                try {
                    startActivity(launch)
                } catch (ex: Exception) {
                    Actions.toast(this, "Can't open that")
                }
            }
        }
    }

    /** Hold OK on an app in the normal menu to hide it. */
    private fun onEntryLongPressed(e: Entry): Boolean {
        if (e.pkg == null || pickMode || manageMode) return false
        AlertDialog.Builder(this)
            .setTitle("Hide " + e.label + "?")
            .setMessage("Unhide it later in Launcher settings.")
            .setPositiveButton("Hide") { _, _ ->
                setHidden(e, true)
                AppList.peek()?.let { showApps(it) }
            }
            .setNegativeButton("Cancel", null)
            .show()
        return true
    }

    private fun setHidden(e: Entry, hide: Boolean) {
        val pkg = e.pkg ?: return
        val set = prefs.hiddenApps.toMutableSet()
        if (hide) set.add(pkg) else set.remove(pkg)
        prefs.hiddenApps = set
        e.hidden = hide
        AppList.peek()?.let { showApps(it) } ?: gridAdapter.notifyDataSetChanged()
    }
}
