package com.androidblunders.rakshak.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.androidblunders.rakshak.core.model.ThreatLevel
import com.androidblunders.rakshak.audio.CallTranscriber
import com.androidblunders.rakshak.presentation.*

import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay

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
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var draft by remember { mutableStateOf("This is the police. A warrant is issued. Pay a fine now or be arrested.") }

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

        AudioStreamMonitorSection()
        
        state.spamResult?.let { result ->
            SpamResultCard(
                result = result,
                onReportClicked = { viewModel.reportToCyberPolice(context, result) },
            )
        }

        Card(
            colors = CardDefaults.cardColors(),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("On-device Gemma", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    if (state.modelReady) "Ready · ${state.backend}" else "Not loaded",
                    color = if (state.modelReady) SafetyGreen else AlertRed,
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
                    onClick = viewModel::prepareModel,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text(if (state.isDownloading) "Downloading…" else "Load Gemma") }
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

        LiveCallSection()

        Spacer(Modifier.height(4.dp))
        // Live notification/SMS capture + permission gate. Every message shown here
        // is also fed through the spam-detection pipeline.
        MessageMonitorSection()
    }
}

@Composable
private fun LiveCallSection() {
    val context = LocalContext.current
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Live call protection", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                "Starts a foreground mic recorder (16kHz PCM) that feeds the STT " +
                    "module. Requires microphone permission.",
                fontSize = 14.sp,
            )
            Button(
                onClick = { com.androidblunders.rakshak.audio.CallTranscriber.startTranscription(context) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Start live call protection") }
            OutlinedButton(
                onClick = { com.androidblunders.rakshak.audio.CallTranscriber.stopTranscription(context) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Stop") }
        }
    }
}

@Composable
private fun AudioStreamMonitorSection() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isStreaming by remember { mutableStateOf(false) }
    var transcript by remember { mutableStateOf("") }

    LaunchedEffect(isStreaming) {
        while (isStreaming) {
            transcript = CallTranscriber.getTranscript()
            delay(1000)
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Real-time Call Analysis", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(if (isStreaming) "Streaming audio..." else "Standby", color = if (isStreaming) SafetyGreen else GuardianBlue)
            
            Button(
                onClick = {
                    if (isStreaming) {
                        CallTranscriber.stopTranscription(context)
                    } else {
                        CallTranscriber.startTranscription(context)
                    }
                    isStreaming = !isStreaming
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isStreaming) "Stop Streaming" else "Start Analysis")
            }
            
            if (transcript.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Transcript:", fontWeight = FontWeight.SemiBold)
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
            Text("Latest spam analysis (Gemma)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
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

private fun accentFor(level: ThreatLevel): Color = when (level) {
    ThreatLevel.ACTIVE_THREAT, ThreatLevel.EMERGENCY -> AlertRed
    ThreatLevel.MEDIUM -> AlertRed.copy(alpha = 0.8f)
    ThreatLevel.GENTLE_GUIDANCE -> SafetyGreen
    ThreatLevel.LOW, ThreatLevel.IDLE -> GuardianBlue
}
