package com.androidblunders.rakshak.messaging

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class MessageNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn?.let {
            val packageName = it.packageName
            val extras = it.notification.extras
            val title = extras.getString(Notification.EXTRA_TITLE)
            var text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

            // Handle MessagingStyle notifications (common in WhatsApp, etc.)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
                if (messages != null && messages.isNotEmpty()) {
                    val lastMessage = messages.last() as? android.os.Bundle
                    text = lastMessage?.getCharSequence("text")?.toString() ?: text
                }
            }

            if (text.isNullOrEmpty()) {
                text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            }
            
            // Expanded list of messaging apps
            val messagingApps = listOf(
                "com.whatsapp",
                "com.facebook.orca", // Messenger
                "com.google.android.apps.messaging", // Google Messages (SMS)
                "com.tencent.mm", // WeChat
                "org.telegram.messenger",
                "com.instagram.android",
                "org.thoughtcrime.securesms", // Signal
                "com.viber.voip",
                "com.skype.raider",
                "com.microsoft.teams",
                "com.slack"
            )

            if (packageName in messagingApps || it.notification.category == Notification.CATEGORY_MESSAGE) {
                Log.d("MessageExtractor", "New message from $packageName: $title - $text")
                
                // Notify subscribers
                MessageExtractor.onMessageReceived(
                    MessageData(
                        sender = title ?: "Unknown",
                        content = text ?: "",
                        packageName = packageName,
                        timestamp = it.postTime
                    )
                )
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
}
