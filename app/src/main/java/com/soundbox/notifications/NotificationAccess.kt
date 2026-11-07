package com.soundbox.notifications

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object NotificationAccess {
    fun isEnabled(context: Context): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        val expected = ComponentName(context, PaymentNotificationListener::class.java).flattenToString()
        return enabledListeners.split(':').any { it.equals(expected, ignoreCase = true) }
    }
}
