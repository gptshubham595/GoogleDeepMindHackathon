package com.androidblunders.rakshak.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.androidblunders.rakshak.call.LiveTranscriptBus
import com.androidblunders.rakshak.core.contract.TextGenerator
import com.androidblunders.rakshak.core.model.ThreatLevel
import com.androidblunders.rakshak.gemma.GemmaModelManager
import com.androidblunders.rakshak.messaging.MessageData
import com.androidblunders.rakshak.messaging.MessageExtractor
import com.androidblunders.rakshak.orchestrator.RakshakOrchestrator
import com.androidblunders.rakshak.orchestrator.ThreatFusionEngine
import com.androidblunders.rakshak.spam_detection.SpamDetectionOrchestrator
import com.androidblunders.rakshak.spam_detection.SpamDetectionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val threatLevel: ThreatLevel = ThreatLevel.IDLE,
    val confidence: Float = 0f,
    val modelReady: Boolean = false,
    val backend: String = "CPU",
    val downloadProgress: Float = 0f,
    val isDownloading: Boolean = false,
    val downloadedMb: Long = 0L,
    val totalMb: Long = 0L,
    val downloadError: String? = null,
    val statusLine: String = "Monitoring active",
    val spamResult: SpamDetectionResult? = null,
)

/**
 * Presentation glue. Observes both the orchestrator/fusion/gemma state AND the
 * spam-detection pipeline output, and exposes two demo actions.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    orchestrator: RakshakOrchestrator,
    fusionEngine: ThreatFusionEngine,
    private val spamDetection: SpamDetectionOrchestrator,
    // FallbackTextGenerator: uses local Gemma if downloaded, else the Gemini API.
    private val textGenerator: TextGenerator,
    private val modelManager: GemmaModelManager,
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        orchestrator.threatState,
        fusionEngine.currentConfidence,
        textGenerator.isReady,
        modelManager.status,
        spamDetection.latestResult,
    ) { level, confidence, ready, model, spam ->
        DashboardUiState(
            threatLevel = level,
            confidence = confidence,
            modelReady = ready,
            backend = textGenerator.backend.value,
            downloadProgress = model.progress,
            isDownloading = model.isDownloading,
            downloadedMb = model.downloadedBytes / 1_000_000,
            totalMb = model.totalBytes / 1_000_000,
            downloadError = model.error,
            statusLine = statusLineFor(level, ready),
            spamResult = spam,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    /**
     * Prepare the text generator. If Gemma weights are already downloaded it uses
     * the local engine; otherwise it's instantly ready via the Gemini API.
     */
    fun prepareModel() {
        viewModelScope.launch { textGenerator.prepare() }
    }

    /** Optional: download the ~2.7 GB Gemma weights for fully-offline operation. */
    fun downloadGemma() {
        viewModelScope.launch { modelManager.downloadModel() }
    }

    /**
     * Demo: inject a fake inbound message into [MessageExtractor], which both the
     * spam-detection pipeline and the orchestrator's NotificationMessageSource
     * observe — so one tap exercises the full real pipeline end-to-end.
     */
    fun simulateMessage(text: String) {
        if (text.isBlank()) return
        MessageExtractor.onMessageReceived(
            MessageData(
                sender = "Demo Sender",
                content = text,
                packageName = "com.rakshak.demo",
                timestamp = System.currentTimeMillis(),
            ),
        )
    }

    /**
     * Demo: simulate live-call speech-to-text. In production the STT module pushes
     * here; this proves the live-text → spam-detection path end-to-end.
     */
    fun simulateLiveTranscript(text: String) {
        LiveTranscriptBus.push(text, speaker = "Caller")
    }

    private fun statusLineFor(level: ThreatLevel, ready: Boolean): String = when {
        !ready -> "Offline model not loaded — tap Load Gemma"
        level == ThreatLevel.IDLE -> "Monitoring active"
        else -> "Threat detected: $level"
    }

    /** Draft an email to the Cyber Police if a transaction scam is detected. */
    fun reportToCyberPolice(context: android.content.Context, result: SpamDetectionResult) {
        val details = com.androidblunders.rakshak.reporting.TransactionDetailsExtractor.extractDetails(result.messageBody)
        com.androidblunders.rakshak.reporting.CyberPoliceReporter.draftEmail(
            context,
            result.sender,
            result.messageBody,
            details
        )
    }
}
