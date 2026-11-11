package com.soundbox.notifications

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

object NotificationAccess {
    const val ACTION_LISTENER_STATE_CHANGED = "com.soundbox.action.NOTIFICATION_LISTENER_STATE_CHANGED"

    @Volatile
    private var listenerConnected = false

    fun isEnabled(context: Context): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        val expected = ComponentName(context, PaymentNotificationListener::class.java).flattenToString()
        return enabledListeners.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    fun isListenerConnected(): Boolean = listenerConnected

    internal fun setListenerConnected(context: Context, connected: Boolean) {
        if (listenerConnected == connected) return
        listenerConnected = connected
        context.sendBroadcast(
            Intent(ACTION_LISTENER_STATE_CHANGED).setPackage(context.packageName)
        )
    }
}
