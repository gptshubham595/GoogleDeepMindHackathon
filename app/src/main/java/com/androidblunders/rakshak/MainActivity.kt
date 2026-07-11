package com.androidblunders.rakshak

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.androidblunders.rakshak.ui.theme.RakshakTheme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.androidblunders.rakshak.messaging.MessageData
import com.androidblunders.rakshak.messaging.MessageExtractor
import kotlinx.coroutines.flow.collectLatest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import android.util.Log

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Auto-request runtime permissions for the hackathon
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val permissions = mutableListOf(
                android.Manifest.permission.READ_SMS,
                android.Manifest.permission.RECEIVE_SMS
            )
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            requestPermissions(permissions.toTypedArray(), 101)
        }

        // 1. registerListener: Captures FUTURE messages that arrive while the app is alive.
        //[Hareesh] Read future message's here.
        MessageExtractor.registerListener { message ->
            Log.d("RakshakPlugin", "Exposed Listener captured: ${message.content}")
        }

        enableEdgeToEdge()
        setContent {
            RakshakTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MessageExtractorUI(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun MessageExtractorUI(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var isPermissionGranted by remember { mutableStateOf(MessageExtractor.isPermissionGranted(context)) }
    val messages = remember { mutableStateListOf<MessageData>() }

    // This effect runs every time the permission status changes
    LaunchedEffect(isPermissionGranted) {
        if (isPermissionGranted) {
            // Permission just granted! Pull any existing history
            //[Hareesh] Read historic message's here.
            val history = MessageExtractor.getLast25Messages()
            messages.clear()
            messages.addAll(history)
            Log.d("RakshakPlugin", "Permission active. Loaded ${history.size} historical messages.")
        }
    }

    LaunchedEffect(Unit) {
        MessageExtractor.messageFlow.collectLatest { message ->
            if (!messages.contains(message)) {
                messages.add(0, message)
            }
        }
    }

    // Refresh permission status when activity resumes
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isPermissionGranted = MessageExtractor.isPermissionGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (!isPermissionGranted) {
            Text(text = "Notification Access is required to capture messages.")
            Button(onClick = { MessageExtractor.openPermissionSettings(context) }) {
                Text("Grant Permission")
            }
        } else {
            Text(text = "Capturing real-time messages...")
            LazyColumn {
                items(messages) { message ->
                    MessageItem(message)
                }
            }
        }
    }
}

@Composable
fun MessageItem(message: MessageData) {
    Column(modifier = Modifier.padding(8.dp)) {
        Text(text = "From: ${message.sender} (${message.packageName})", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        Text(text = message.content)
        Text(text = java.util.Date(message.timestamp).toString(), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        androidx.compose.material3.HorizontalDivider()
    }
}
