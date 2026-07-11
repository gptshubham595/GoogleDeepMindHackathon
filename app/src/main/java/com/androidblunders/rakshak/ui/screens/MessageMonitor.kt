package com.androidblunders.rakshak.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.androidblunders.rakshak.messaging.MessageData
import com.androidblunders.rakshak.messaging.MessageExtractor
import java.util.Date
import kotlinx.coroutines.flow.collectLatest

/**
 * Notification-access gate + captured-message feed.
 *
 * The live [MessageExtractor.messageFlow] is ALSO consumed by
 * `NotificationMessageSource`, so every message shown here is simultaneously run
 * through the orchestrator's threat pipeline. This section is the visible half of
 * that: permission gating, historic backfill, and the running log.
 */
@Composable
fun MessageMonitorSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var isPermissionGranted by remember { mutableStateOf(MessageExtractor.isPermissionGranted(context)) }
    val messages = remember { mutableStateListOf<MessageData>() }

    // Backfill any already-captured history (SMS + notifications) on first show.
    LaunchedEffect(Unit) {
        val history = MessageExtractor.getLast25Messages()
        if (history.isNotEmpty()) {
            messages.clear()
            messages.addAll(history)
        }
        // Live capture — dedup and prepend.
        MessageExtractor.messageFlow.collectLatest { message ->
            if (!messages.contains(message)) {
                messages.add(0, message)
            }
        }
    }

    // Re-check permission whenever the user returns from the settings screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isPermissionGranted = MessageExtractor.isPermissionGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Card(shape = RoundedCornerShape(16.dp), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Captured messages · ${messages.size}", fontWeight = FontWeight.Bold, fontSize = 18.sp)

            // SMS is read directly via the SMS receiver (RECEIVE_SMS). Notification
            // access is only needed to ALSO monitor WhatsApp / chat apps.
            if (!isPermissionGranted) {
                Text(
                    "SMS is monitored directly. Enable notification access to also scan " +
                        "WhatsApp & other chat apps.",
                    fontSize = 13.sp,
                )
                Button(
                    onClick = { MessageExtractor.openPermissionSettings(context) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text("Enable chat-app monitoring") }
            }

            if (messages.isEmpty()) {
                Text("Waiting for messages… send an SMS, or use the demo button above.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(messages) { message -> MessageRow(message) }
                }
            }
        }
    }
}

@Composable
private fun MessageRow(message: MessageData) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            "${message.sender} · ${message.packageName}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(message.content, style = MaterialTheme.typography.bodyMedium)
        Text(Date(message.timestamp).toString(), style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
    }
}
