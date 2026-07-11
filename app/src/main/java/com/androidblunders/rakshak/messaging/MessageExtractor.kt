package com.androidblunders.rakshak.messaging

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.channels.BufferOverflow
import com.androidblunders.rakshak.core.status.ProtectionRuntimeStatus

data class MessageData(
    val sender: String,
    val content: String,
    val packageName: String,
    val timestamp: Long
)

object MessageExtractor {
    private val _messageFlow = MutableSharedFlow<MessageData>(
        extraBufferCapacity = 25,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messageFlow = _messageFlow.asSharedFlow()

    // Internal cache for the last 25 messages
    private val messageHistory = java.util.Collections.synchronizedList(mutableListOf<MessageData>())

    internal fun onMessageReceived(data: MessageData) {
        if (data.content.isBlank()) return
        synchronized(messageHistory) {
            val duplicate = messageHistory.any { previous ->
                previous.content == data.content &&
                    kotlin.math.abs(previous.timestamp - data.timestamp) < DUPLICATE_WINDOW_MS
            }
            if (duplicate) return
            messageHistory.add(0, data)
            while (messageHistory.size > MAX_HISTORY) {
                messageHistory.removeAt(messageHistory.lastIndex)
            }
        }
        ProtectionRuntimeStatus.markMessage(data.sender, sourceLabel(data.packageName))
        _messageFlow.tryEmit(data)
    }

    /**
     * Returns the last 25 captured messages.
     */
    fun getLast25Messages(): List<MessageData> {
        return synchronized(messageHistory) { messageHistory.toList() }
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

    private const val MAX_HISTORY = 25
    private const val DUPLICATE_WINDOW_MS = 2_000L

    private fun sourceLabel(packageName: String): String = when {
        packageName.contains("Telephony", ignoreCase = true) -> "SMS"
        packageName == "com.rakshak.demo" -> "Demo"
        else -> "Notification"
    }
}
