package com.androidblunders.rakshak.messaging

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

/**
 * Reads real incoming SMS directly (no notification-access dependency).
 *
 * Statically registered for `SMS_RECEIVED`, so it fires even when the app UI is
 * closed. Every message is normalized into [MessageData] and pushed onto
 * [MessageExtractor.messageFlow] — the same stream the notification listener and
 * the demo button feed — so the spam-detection pipeline analyzes it identically.
 *
 * Requires the `RECEIVE_SMS` runtime permission (requested in MainActivity).
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = runCatching { Telephony.Sms.Intents.getMessagesFromIntent(intent) }
            .getOrNull() ?: return
        if (messages.isEmpty()) return

        // A single SMS can arrive as multiple PDUs (long messages) — concatenate.
        val sender = messages.first().originatingAddress ?: "Unknown"
        val body = messages.joinToString("") { it.messageBody.orEmpty() }
        if (body.isBlank()) return

        Log.d(TAG, "SMS from $sender: ${body.take(60)}")
        MessageExtractor.onMessageReceived(
            MessageData(
                sender = sender,
                content = body,
                packageName = "android.provider.Telephony.SMS",
                timestamp = System.currentTimeMillis(),
            ),
        )
    }

    private companion object { const val TAG = "SmsReceiver" }
}
