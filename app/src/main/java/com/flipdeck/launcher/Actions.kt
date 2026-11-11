package com.flipdeck.launcher

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast

/**
 * Everything a D-pad direction can be bound to. An action is stored as a string:
 * one of the constants below, or "app:<package>" to launch any installed app.
 */
object Actions {
    const val NONE = "none"
    const val MENU = "menu"
    const val QUICK = "quick"
    const val NOTICES = "notices"
    const val CONTACTS = "contacts"
    const val DIALER = "dialer"
    const val MESSAGES = "messages"
    const val CAMERA = "camera"
    const val FLASHLIGHT = "flashlight"
    const val PLAYPAUSE = "playpause"
    const val SETTINGS = "settings"
    const val APP_PREFIX = "app:"

    /** Shown in the picker, in this order. "Launch an app…" is added by the picker itself. */
    val choices: List<Pair<String, String>> = listOf(
        MENU to "Open menu",
        QUICK to "Quick settings",
        NOTICES to "Notices",
        CONTACTS to "Contacts",
        DIALER to "Phone",
        MESSAGES to "Messages",
        CAMERA to "Camera",
        FLASHLIGHT to "Flashlight",
        PLAYPAUSE to "Play / pause music",
        SETTINGS to "System settings",
        NONE to "Do nothing"
    )

    fun label(context: Context, action: String): String {
        if (action.startsWith(APP_PREFIX)) {
            val pkg = action.removePrefix(APP_PREFIX)
            if (pkg == Ui.BLOCKED_PACKAGE) return "Do nothing"
            return try {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (e: Exception) {
                "Missing app"
            }
        }
        return choices.firstOrNull { it.first == action }?.second ?: "Do nothing"
    }

    /** Short name for the softkey bar on the home screen. */
    fun shortLabel(context: Context, action: String): String = when (action) {
        MENU -> "Menu"
        QUICK -> "Quick"
        NOTICES -> "Notices"
        CONTACTS -> "Contacts"
        DIALER -> "Phone"
        MESSAGES -> "Messages"
        CAMERA -> "Camera"
        FLASHLIGHT -> "Flashlight"
        PLAYPAUSE -> "Play/Pause"
        SETTINGS -> "Settings"
        NONE -> ""
        else -> label(context, action)
    }

    fun run(context: Context, action: String) {
        try {
            when {
                action == MENU -> context.startActivity(Intent(context, AppGridActivity::class.java))
                action == QUICK -> context.startActivity(Intent(context, QuickSettingsActivity::class.java))
                action == NOTICES -> context.startActivity(Intent(context, NoticesActivity::class.java))
                action == CONTACTS -> openContacts(context)
                action == DIALER -> startExternal(context, Intent(Intent.ACTION_DIAL))
                action == MESSAGES -> startExternal(
                    context,
                    Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING)
                )
                action == CAMERA -> startExternal(context, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
                action == FLASHLIGHT -> toggleFlashlight(context)
                action == PLAYPAUSE -> playPause(context)
                action == SETTINGS -> startExternal(context, Intent(Settings.ACTION_SETTINGS))
                action.startsWith(APP_PREFIX) -> launchPackage(context, action.removePrefix(APP_PREFIX))
                else -> Unit
            }
        } catch (e: Exception) {
            toast(context, "Can't open that")
        }
    }

    /** Opens the phone's dialer with [number] already typed in. It does not place the call. */
    fun dial(context: Context, number: String) {
        try {
            startExternal(context, Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))
        } catch (e: Exception) {
            toast(context, "Can't open that")
        }
    }

    fun openContacts(context: Context) {
        try {
            startExternal(
                context,
                Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CONTACTS)
            )
        } catch (e: ActivityNotFoundException) {
            startExternal(context, Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI))
        }
    }

    private fun launchPackage(context: Context, pkg: String) {
        if (pkg == Ui.BLOCKED_PACKAGE) return
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) toast(context, "App not found") else startExternal(context, intent)
    }

    private fun startExternal(context: Context, intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Sends a media play/pause key press to whatever app is currently playing audio or video. */
    private fun playPause(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
    }

    private var torchOn = false

    private fun toggleFlashlight(context: Context) {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        if (id == null) {
            toast(context, "No flash found")
            return
        }
        val target = !torchOn
        try {
            cm.setTorchMode(id, target)
            torchOn = target
        } catch (e: Exception) {
            toast(context, "Flashlight unavailable")
        }
    }

    fun toast(context: Context, msg: String) =
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}
