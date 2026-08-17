package com.tyejaedon.coverscreenos.services

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class CoverNotificationListenerService : NotificationListenerService() {

    companion object {
        @Volatile
        private var listenerConnected: Boolean = false

        fun isListenerConnected(): Boolean = listenerConnected
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        listenerConnected = true
        Log.d("CoverNotifListener", "Notification listener connected")
    }

    override fun onListenerDisconnected() {
        listenerConnected = false
        Log.d("CoverNotifListener", "Notification listener disconnected")
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
}

