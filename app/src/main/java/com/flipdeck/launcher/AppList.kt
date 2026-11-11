package com.flipdeck.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper

/**
 * Keeps the list of launchable apps (labels + icons) in memory, so the menu opens instantly
 * instead of asking the system for every icon each time. Loaded once in the background when the
 * home screen starts, and reloaded when apps are installed, removed or updated.
 */
object AppList {

    class App(val label: String, val pkg: String, val component: ComponentName, val icon: Drawable?)

    @Volatile
    private var cached: List<App>? = null

    @Volatile
    private var loading = false

    // Set when apps change while a load is already running, so that load is redone.
    @Volatile
    private var stale = false
    private val waiting = ArrayList<(List<App>) -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    /** The apps already in memory, or null if they haven't been loaded yet. */
    fun peek(): List<App>? = cached

    /** Calls [done] on the main thread with the app list, loading it first if needed. */
    fun get(context: Context, done: (List<App>) -> Unit) {
        val now = cached
        if (now != null) {
            done(now)
            return
        }
        synchronized(waiting) { waiting.add(done) }
        load(context.applicationContext)
    }

    /** Throws the list away and loads it again (apps were installed / removed / updated). */
    fun invalidate(context: Context) {
        cached = null
        stale = true
        load(context.applicationContext)
    }

    private fun load(context: Context) {
        synchronized(waiting) {
            if (loading) return
            loading = true
            stale = false
        }
        Thread {
            val result = try {
                query(context)
            } catch (e: Exception) {
                emptyList()
            }
            val callbacks: List<(List<App>) -> Unit>? = synchronized(waiting) {
                loading = false
                if (stale) {
                    null
                } else {
                    cached = result
                    val list = ArrayList(waiting)
                    waiting.clear()
                    list
                }
            }
            if (callbacks == null) {
                // Apps changed mid-load: load again; whoever is waiting gets the fresh list.
                main.post { load(context) }
            } else {
                main.post { callbacks.forEach { it(result) } }
            }
        }.apply { priority = Thread.NORM_PRIORITY - 1 }.start()
    }

    private fun query(context: Context): List<App> {
        val pm = context.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(query, 0)
            .filter {
                val pkg = it.activityInfo.packageName
                pkg != context.packageName && pkg != Ui.BLOCKED_PACKAGE
            }
            .map {
                App(
                    it.loadLabel(pm).toString(),
                    it.activityInfo.packageName,
                    ComponentName(it.activityInfo.packageName, it.activityInfo.name),
                    it.loadIcon(pm)
                )
            }
            .sortedBy { it.label.lowercase() }
    }
}
