package com.androidblunders.rakshak.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.androidblunders.rakshak.audio.CallTranscriber
import com.androidblunders.rakshak.call.CallStreamStatus
import com.androidblunders.rakshak.core.model.ThreatLevel
import com.androidblunders.rakshak.core.status.ProtectionRuntimeStatus
import java.text.DateFormat
import java.util.Date

// "Vigilant Guardian" palette, kept local so this demo screen stays decoupled
// from the app's evolving ui.theme module.
private val GuardianBlue = Color(0xFF002045)
private val SafetyGreen = Color(0xFF0A6C44)
private val AlertRed = Color(0xFFBA1A1A)

/**
 * Minimal "Rakshak Dashboard" that observes the single source of truth
 * (orchestrator threat state) and drives the app's color/status by level.
 * Real screens (overlay interceptor, guidance mode, history) hang off this state.
 */
@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
    onOpenHistory: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val transactionResult by viewModel.latestTransactionResult.collectAsState()
    val callActive by CallStreamStatus.active.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    val context = LocalContext.current
    var draft by remember { mutableStateOf("This is the police. A warrant is issued. Pay a fine now or be arrested.") }

    LaunchedEffect(transactionResult?.timestamp, callActive, lifecycleState) {
        val transaction = transactionResult ?: return@LaunchedEffect
        if (!callActive && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) {
            viewModel.autoDraftTransactionIfNeeded(context, transaction)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(accentFor(state.threatLevel).copy(alpha = 0.06f))
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Rakshak", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = GuardianBlue)

        ThreatBanner(state)

        ProtectionReadinessSection()

        RuntimePipelineStatusSection()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onOpenHistory,
                modifier = Modifier.weight(1f).height(56.dp),
            ) { Text("History") }
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.weight(1f).height(56.dp),
            ) { Text("Settings") }
        }

        CallProtectionSection()
        
        transactionResult?.let { result ->
            SpamResultCard(
                result = result,
                onReportClicked = { viewModel.reportToCyberPolice(context, result) },
            )
        }

        state.spamResult
            ?.takeIf { it.timestamp != transactionResult?.timestamp }
            ?.let { result ->
                SpamResultCard(result = result, onReportClicked = {})
            }

        Card(
            colors = CardDefaults.cardColors(),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("AI detection engine", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    if (state.modelReady) "Active · ${state.backend}" else "Unavailable",
                    color = if (state.modelReady) SafetyGreen else AlertRed,
                )
                Text(
                    if (state.localModelAvailable) {
                        "Local Gemma weights are available for offline analysis."
                    } else {
                        "Using Gemini online fallback. Download Gemma for offline protection."
                    },
                    fontSize = 14.sp,
                )
                if (state.isDownloading) {
                    LinearProgressIndicator(
                        progress = { state.downloadProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Downloading ${(state.downloadProgress * 100).toInt()}%  " +
                            "(${state.downloadedMb} / ${state.totalMb} MB)",
                    )
                }
                state.downloadError?.let {
                    Text("⚠️ $it", color = AlertRed, fontSize = 14.sp)
                }
                OutlinedButton(
                    onClick = {
                        if (state.localModelAvailable) viewModel.prepareModel()
                        else viewModel.downloadGemma()
                    },
                    enabled = !state.isDownloading,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text(
                        when {
                            state.isDownloading -> "Downloading Gemma…"
                            state.localModelAvailable -> "Initialize local Gemma"
                            else -> "Download Gemma (~2.7 GB)"
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text("Simulate an incoming message", fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )
        Button(
            onClick = { viewModel.simulateMessage(draft) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("Analyze message (SMS path)") }
        Button(
            onClick = { viewModel.simulateLiveTranscript(draft) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("Analyze as live call speech (STT path)") }

        Spacer(Modifier.height(4.dp))
        // Live notification/SMS capture + permission gate. Every message shown here
        // is also fed through the spam-detection pipeline.
        MessageMonitorSection()
    }
}

@Composable
private fun RuntimePipelineStatusSection() {
    val context = LocalContext.current
    val sttState by ProtectionRuntimeStatus.sttState.collectAsState()
    val ttsState by ProtectionRuntimeStatus.ttsState.collectAsState()
    val lastInput by ProtectionRuntimeStatus.lastInput.collectAsState()
    val lastInputAt by ProtectionRuntimeStatus.lastInputAt.collectAsState()
    val messageCount by ProtectionRuntimeStatus.messageCount.collectAsState()
    val lastWarning by ProtectionRuntimeStatus.lastSpokenWarning.collectAsState()
    val smsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) ==
        PackageManager.PERMISSION_GRANTED

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Live protection pipeline", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            RuntimeStatusRow("STT", sttState.name, sttState != ProtectionRuntimeStatus.SttState.ERROR)
            RuntimeStatusRow("TTS", ttsState.name, ttsState != ProtectionRuntimeStatus.TtsState.ERROR)
            RuntimeStatusRow(
                "Message reader",
                if (smsGranted) "MONITORING · $messageCount received" else "PERMISSION REQUIRED",
                smsGranted,
            )
            Text(lastInput, fontWeight = FontWeight.SemiBold)
            if (lastInputAt > 0L) {
                Text(
                    "Last automatic activation: " +
                        DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(lastInputAt)),
                    fontSize = 14.sp,
                )
            }
            if (lastWarning.isNotBlank()) {
                Text("Last spoken warning: “${lastWarning.take(100)}”", fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun RuntimeStatusRow(label: String, status: String, healthy: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(
            status,
            color = if (healthy) SafetyGreen else AlertRed,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ProtectionReadinessSection() {
    val context = LocalContext.current
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var permissionRefresh by remember { mutableIntStateOf(0) }
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        permissionRefresh++
        overlayGranted = Settings.canDrawOverlays(context)
    }
    val micGranted = permissionRefresh.let {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }
    val smsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) ==
        PackageManager.PERMISSION_GRANTED
    val phoneGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
        PackageManager.PERMISSION_GRANTED
    val locationGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    val checks = listOf(
        "Call activation" to phoneGranted,
        "Audio capture" to micGranted,
        "Real SMS reading" to smsGranted,
        "Threat overlay" to overlayGranted,
        "Report location" to locationGranted,
    )
    val completed = checks.count { it.second }

    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Protection readiness · $completed/${checks.size}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            LinearProgressIndicator(
                progress = { completed.toFloat() / checks.size },
                modifier = Modifier.fillMaxWidth(),
            )
            checks.forEach { (label, ready) ->
                Text(
                    "${if (ready) "✓" else "○"} $label",
                    color = if (ready) SafetyGreen else AlertRed,
                )
            }
            if (!micGranted || !smsGranted || !phoneGranted || !locationGranted) {
                OutlinedButton(
                    onClick = {
                        settingsLauncher.launch(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text("Open app permissions") }
            }
            if (!overlayGranted) {
                OutlinedButton(
                    onClick = {
                        settingsLauncher.launch(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text("Allow threat overlay") }
            }
        }
    }
}

@Composable
private fun CallProtectionSection() {
    val context = LocalContext.current
    val active by CallStreamStatus.active.collectAsState()
    val connection by CallStreamStatus.connection.collectAsState()
    val bytesSent by CallStreamStatus.bytesSent.collectAsState()
    val bytesReceived by CallStreamStatus.bytesReceived.collectAsState()
    val transcript by CallStreamStatus.transcript.collectAsState()
    val serverState by CallStreamStatus.serverState.collectAsState()
    val serverThreat by CallStreamStatus.serverThreat.collectAsState()
    val lastError by CallStreamStatus.lastError.collectAsState()
    val sttState by ProtectionRuntimeStatus.sttState.collectAsState()
    val ttsState by ProtectionRuntimeStatus.ttsState.collectAsState()
    val lastWarning by ProtectionRuntimeStatus.lastSpokenWarning.collectAsState()
    val statusColor = when (connection) {
        CallStreamStatus.Connection.CONNECTED -> SafetyGreen
        CallStreamStatus.Connection.ERROR -> AlertRed
        CallStreamStatus.Connection.CONNECTING -> Color(0xFFD96C00)
        else -> GuardianBlue
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Live call protection", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                "Socket: ${connection.name} · Service: ${if (active) "ACTIVE" else "IDLE"}",
                color = statusColor,
                fontWeight = FontWeight.Bold,
            )
            Text(
                (if (active) "Live session" else "Last session") +
                    ": sent ${formatBytes(bytesSent)} PCM · received " +
                    "${formatBytes(bytesReceived)} JSON/audio",
            )
            RuntimeStatusRow(
                "Audio capture",
                when {
                    active && bytesSent > 0L -> "CAPTURING"
                    active -> "STARTING"
                    bytesSent > 0L -> "CAPTURED ${formatBytes(bytesSent)}"
                    else -> "IDLE"
                },
                !active || bytesSent > 0L,
            )
            RuntimeStatusRow(
                "STT",
                "$sttState · ${transcript.length} transcript chars",
                sttState != ProtectionRuntimeStatus.SttState.ERROR,
            )
            RuntimeStatusRow(
                "TTS warning",
                ttsState.name,
                ttsState != ProtectionRuntimeStatus.TtsState.ERROR,
            )
            Text("VOICE state: $serverState · Risk ${(serverThreat * 100).toInt()}%")
            lastError?.let { Text(it, color = AlertRed) }
            if (bytesSent > 0L && transcript.isBlank()) {
                Text(
                    "Audio reached VOICE, but no transcript was returned. Speak near the " +
                        "microphone and check the VOICE terminal for Gemini transcription errors.",
                    color = Color(0xFFD96C00),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (lastWarning.isNotBlank()) {
                Text("Last warning played: ${lastWarning.take(100)}", fontSize = 14.sp)
            }
            Text(
                "Audio is captured as 16 kHz mono PCM. On standard Android phones, " +
                    "speakerphone may be needed to hear both sides of a cellular call.",
                fontSize = 14.sp,
            )

            Button(
                onClick = {
                    if (active) {
                        CallTranscriber.stopTranscription(context)
                    } else {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) CallTranscriber.startTranscription(context)
                        else Toast.makeText(context, "Microphone permission is required", Toast.LENGTH_LONG).show()
                    }
                },
                enabled = connection != CallStreamStatus.Connection.CONNECTING,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(if (active) "Stop protection" else "Start call protection")
            }

            if (transcript.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Live transcript", fontWeight = FontWeight.SemiBold)
                Text(transcript, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun ThreatBanner(state: DashboardUiState) {
    val accent = accentFor(state.threatLevel)
    Card(
        colors = CardDefaults.cardColors(containerColor = accent),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                state.threatLevel.name.replace('_', ' '),
                color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold,
            )
            Text(state.statusLine, color = Color.White.copy(alpha = 0.9f))
            Text(
                "Confidence ${(state.confidence * 100).toInt()}%",
                color = Color.White.copy(alpha = 0.9f),
            )
        }
    }
}

@Composable
private fun SpamResultCard(
    result: com.androidblunders.rakshak.spam_detection.SpamDetectionResult,
    onReportClicked: () -> Unit,
) {
    val score = result.score.score
    val accent = when {
        score >= 0.80f -> AlertRed
        score >= 0.60f -> AlertRed.copy(alpha = 0.75f)
        score >= 0.35f -> Color(0xFFD96C00)
        else -> SafetyGreen
    }
    Card(
        colors = CardDefaults.cardColors(),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (result.hasTransaction) "Dangerous transaction alert" else "Latest scam analysis",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            Text("From ${result.sender}", color = GuardianBlue, fontWeight = FontWeight.SemiBold)
            Text("\"${result.messageBody.take(120)}\"", fontSize = 14.sp)
            Text(
                "${result.status}  ·  ${result.score.label}  ·  ${(score * 100).toInt()}%",
                color = accent, fontWeight = FontWeight.Bold, fontSize = 16.sp,
            )
            if (result.hasTransaction) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onReportClicked,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) { Text("Report to Cyber Police") }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.1f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}

private fun accentFor(level: ThreatLevel): Color = when (level) {
    ThreatLevel.ACTIVE_THREAT, ThreatLevel.EMERGENCY -> AlertRed
    ThreatLevel.MEDIUM -> AlertRed.copy(alpha = 0.8f)
    ThreatLevel.GENTLE_GUIDANCE -> SafetyGreen
    ThreatLevel.LOW, ThreatLevel.IDLE -> GuardianBlue
}
