package com.androidblunders.rakshak.messaging

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

data class MessageData(
    val sender: String,
    val content: String,
    val packageName: String,
    val timestamp: Long
)

object MessageExtractor {
    private val _messageFlow = MutableSharedFlow<MessageData>(extraBufferCapacity = 10)
    val messageFlow = _messageFlow.asSharedFlow()

    // Internal cache for the last 25 messages
    private val messageHistory = java.util.Collections.synchronizedList(mutableListOf<MessageData>())

    internal fun onMessageReceived(data: MessageData) {
        messageHistory.add(0, data)
        // Keep only the last 25
        if (messageHistory.size > 25) {
            messageHistory.removeAt(messageHistory.size - 1)
        }
        _messageFlow.tryEmit(data)
    }

    /**
     * Returns the last 25 captured messages.
     */
    fun getLast25Messages(): List<MessageData> {
        return messageHistory.toList()
    }

    /**
     * Call this to check if the notification listener permission is granted.
     */
    fun isPermissionGranted(context: android.content.Context): Boolean {
        val packageName = context.packageName
        val flat = android.provider.Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        return flat?.contains(packageName) == true
    }

    /**
     * Call this to register a simple callback for message updates.
     */
    fun registerListener(onMessage: (MessageData) -> Unit) {
        // In a real app, you'd want to handle lifecycle and multiple listeners
        // For this utility, we'll just use a CoroutineScope to bridge Flow to callback
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            messageFlow.collect { onMessage(it) }
        }
    }

    /**
     * Opens the settings page to enable notification access.
     */
    fun openPermissionSettings(context: android.content.Context) {
        val intent = android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Checks if the service is running.
     */
    fun isServiceRunning(context: android.content.Context): Boolean {
        val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        @Suppress("DEPRECATION")
        for (service in am.getRunningServices(Int.MAX_VALUE)) {
            if (MessageNotificationListenerService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }
}
