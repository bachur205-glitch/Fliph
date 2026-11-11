package com.flipdeck.launcher

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Lets the launcher read the phone's notifications so it can show them as "Notices". */
class NoticeService : NotificationListenerService() {

    override fun onListenerConnected() {
        NoticeStore.service = this
        NoticeStore.changed()
    }

    override fun onListenerDisconnected() {
        if (NoticeStore.service === this) NoticeStore.service = null
        NoticeStore.changed()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = NoticeStore.changed()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = NoticeStore.changed()
}

object NoticeStore {
    @Volatile
    var service: NoticeService? = null

    @Volatile
    var listener: (() -> Unit)? = null

    fun changed() {
        listener?.invoke()
    }

    /** Dismissable notifications, newest first. Ongoing ones (music, calls) are left out. */
    fun active(): List<StatusBarNotification> = try {
        service?.activeNotifications
            ?.filter {
                it.packageName != Ui.BLOCKED_PACKAGE &&
                    !it.isOngoing &&
                    (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0
            }
            ?.sortedByDescending { it.postTime }
            ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    fun isEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            ?: return false
        return flat.split(":").any {
            ComponentName.unflattenFromString(it)?.packageName == context.packageName
        }
    }
}
