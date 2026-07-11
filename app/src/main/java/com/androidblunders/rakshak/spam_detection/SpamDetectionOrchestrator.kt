package com.androidblunders.rakshak.spam_detection

import android.util.Log
import com.androidblunders.rakshak.messaging.MessageData
import com.androidblunders.rakshak.messaging.MessageExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** UI-facing result of a single analyzed message. */
data class SpamDetectionResult(
    val sender: String,
    val messageBody: String,
    val score: ThreatScore,
    val status: String,
    val timestamp: Long,
)

/**
 * SpamDetectionOrchestrator — the pipeline entry-point for SMS / notification analysis.
 *
 * ## What it does
 * 1. Subscribes to [MessageExtractor.messageFlow] (SharedFlow<MessageData>).
 * 2. Accumulates incoming messages into a **rolling SMS buffer** (max 25).
 * 3. On every new message, assembles a rich [CallContext] from the buffer
 *    (SMS list, device context, acoustic defaults) and sends it through
 *    the [ThreatFusionEngine].
 * 4. Logs the result (with TODO hooks for Room / StateFlow / notifications).
 *
 * ## Lifecycle
 * This singleton lives for the duration of the app process.
 * Call [startObserving] once from [RakshakApplication.onCreate].
 *
 * ## Thread safety
 * [smsBuffer] is protected by a [Mutex] — concurrent writes from the
 * SharedFlow collector and future SMS BroadcastReceiver are safe.
 *
 * ## Extending output
 * TODO: Replace the Log calls in [onThreatScored] with:
 *   1. Room DAO insert → security_history table
 *   2. MutableStateFlow<AggregatedRiskState> → SecurityHistoryViewModel
 *   3. NotificationManager alert when risk ≥ THRESHOLD_ALERT
 */
@Singleton
class SpamDetectionOrchestrator @Inject constructor(
    private val fusionEngine: ThreatFusionEngine
) {
    companion object {
        private const val TAG = "SpamDetectionOrchestrator"

        /** Maximum SMS messages kept in the rolling buffer. */
        private const val MAX_SMS_BUFFER = 25

        /** Max recent results kept in memory for the UI StateFlow. */
        private const val MAX_RECENT = 25

        // Risk thresholds (score is 0.0–1.0, multiply by 100 for display)
        private const val THRESHOLD_SUSPICIOUS = 0.35f
        private const val THRESHOLD_WARN       = 0.60f
        private const val THRESHOLD_ALERT      = 0.80f
        private const val THRESHOLD_INTERVENE  = 0.90f
    }

    /**
     * Process-wide coroutine scope — SupervisorJob ensures a failure in
     * one child coroutine never cancels the whole pipeline.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Latest analyzed message result. Observed by the dashboard / debug UI. */
    private val _latestResult = MutableStateFlow<SpamDetectionResult?>(null)
    val latestResult: StateFlow<SpamDetectionResult?> = _latestResult.asStateFlow()

    /** Rolling log of recent results (newest first), capped for memory. */
    private val _recentResults = MutableStateFlow<List<SpamDetectionResult>>(emptyList())
    val recentResults: StateFlow<List<SpamDetectionResult>> = _recentResults.asStateFlow()

    /** Thread-safe rolling buffer of recent SMS / notification messages. */
    private val smsBuffer = mutableListOf<SMSMessage>()
    private val bufferMutex = Mutex()

    /**
     * Begins observing [MessageExtractor.messageFlow].
     * Idempotent — safe to call multiple times; the singleton [scope] ensures
     * a single active collector.
     */
    fun startObserving() {
        Log.i(TAG, "Starting spam detection pipeline…")

        MessageExtractor.messageFlow
            .onEach  { message -> handleIncomingMessage(message) }
            .catch   { e -> Log.e(TAG, "messageFlow error — pipeline recovering", e) }
            .launchIn(scope)
    }

    /**
     * Pushes an [SMSMessage] directly into the buffer (e.g. from an SMS
     * BroadcastReceiver) and immediately triggers analysis.
     * Can be called from any thread.
     */
    fun pushSms(sms: SMSMessage) {
        scope.launch {
            addToBuffer(sms)
            analyzeCurrentBuffer()
        }
    }

    // -------------------------------------------------------------------------
    // Private pipeline
    // -------------------------------------------------------------------------

    /** Called when a new notification / message arrives from MessageExtractor. */
    private fun handleIncomingMessage(message: MessageData) {
        scope.launch {
            val sms = message.toSmsMessage()
            addToBuffer(sms)
            analyzeCurrentBuffer()
        }
    }

    /** Adds an [SMSMessage] to the rolling buffer (evicts oldest when over limit). */
    private suspend fun addToBuffer(sms: SMSMessage) = bufferMutex.withLock {
        smsBuffer.add(sms)
        if (smsBuffer.size > MAX_SMS_BUFFER) {
            smsBuffer.removeAt(0)
        }
    }

    /** Snapshots the current buffer and runs it through the fusion engine. */
    private suspend fun analyzeCurrentBuffer() {
        val snapshot = bufferMutex.withLock { smsBuffer.toList() }
        if (snapshot.isEmpty()) return

        val latestSms = snapshot.last()

        // Build the rich CallContext from the accumulated buffer.
        // Note: transcript and acoustic features are empty here because this
        // orchestrator handles SMS only. When the call-recording pipeline is
        // wired up (Phase 2–3), TranscriptSegments and AcousticFeatures will
        // be merged in from the ContextBuffer.
        val context = CallContext(
            callMetadata = CallMetadata(
                callerNumber   = latestSms.sender,
                isKnownContact = false,             // TODO: resolve against contacts DB
                callDirection  = CallDirection.INCOMING,
                callStartTimeMs = snapshot.first().receivedAtMs
            ),
            recentSmsMessages  = snapshot,
            transcriptSegments = emptyList(),       // TODO: merge from ContextBuffer (Phase 3)
            deviceContext      = DeviceContext(),    // TODO: populate from DeviceContextProvider
            acousticFeatures   = AcousticFeatures() // TODO: populate from AcousticFeatureExtractor
        )

        try {
            Log.d(TAG, "Analysing buffer: ${snapshot.size} SMS messages, " +
                    "latest from ${latestSms.sender}")
            val threatScore = fusionEngine.evaluate(context)
            onThreatScored(context, threatScore)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to analyse buffer", e)
        }
    }

    /**
     * Called with the fused [ThreatScore] for every analysis pass.
     * Currently writes a structured log. Hook in Room / StateFlow / Notification here.
     */
    private fun onThreatScored(ctx: CallContext, score: ThreatScore) {
        val scaledScore = score.score

        val level = when {
            scaledScore >= THRESHOLD_INTERVENE  -> "🚨 INTERVENE"
            scaledScore >= THRESHOLD_ALERT      -> "🔴 ALERT"
            scaledScore >= THRESHOLD_WARN       -> "⚠️  WARN"
            scaledScore >= THRESHOLD_SUSPICIOUS -> "🟡 SUSPICIOUS"
            else                                -> "✅ SAFE"
        }

        val latestSms = ctx.recentSmsMessages.lastOrNull()
        val otpFlag   = if (ctx.hasOtpSms) " [OTP]" else ""
        val upiFlag   = if (ctx.hasUpiSms) " [UPI]" else ""
        val signalStr = if (score.signals.isEmpty()) "none" else score.signals.joinToString()

        // Publish to StateFlow observers (UI / dashboard).
        val result = SpamDetectionResult(
            sender      = ctx.callerNumber,
            messageBody = latestSms?.body ?: ctx.allSmsText.take(120),
            score       = score,
            status      = level,
            timestamp   = ctx.analysisTimestampMs,
        )
        _latestResult.value = result
        _recentResults.value = (listOf(result) + _recentResults.value).take(MAX_RECENT)

        Log.i(
            TAG, """
            ┌── Spam Detection Result ──────────────────────────────────
            │  Caller       : ${ctx.callerNumber}
            │  Known contact: ${ctx.isKnownContact}
            │  SMS in buffer: ${ctx.recentSmsMessages.size}$otpFlag$upiFlag
            │  Stage        : ${score.stage}
            │  Score        : ${"%.0f".format(scaledScore * 100)}/100
            │  Label        : ${score.label}
            │  Confidence   : ${"%.1f".format(score.confidence * 100)}%
            │  Signals      : $signalStr
            │  Status       : $level
            └───────────────────────────────────────────────────────────
            """.trimIndent()
        )

        // TODO (Phase 5): Emit to RiskAggregator instead of logging directly
        // TODO (Phase 6): Trigger InterventionEngine based on scaledScore
        // TODO: Insert into Room security_history DAO
    }

    // -------------------------------------------------------------------------
    // Extension helpers
    // -------------------------------------------------------------------------

    /**
     * Converts the messaging layer's [MessageData] into an [SMSMessage].
     * Also performs lightweight OTP and link detection.
     */
    private fun MessageData.toSmsMessage() = SMSMessage(
        sender       = sender,
        body         = content,
        packageName  = packageName,
        receivedAtMs = timestamp,
        isOtp        = OTP_REGEX.containsMatchIn(content),
        extractedUpi = UPI_REGEX.find(content)?.value,
        containsLink = LINK_REGEX.containsMatchIn(content)
    )

    companion object Patterns {
        /** Detects common OTP patterns: "OTP is 123456", "Your code: 456789", etc. */
        private val OTP_REGEX = Regex(
            """(?i)(otp|one.time.pass|verification.code|code\s+is)\D{0,10}\d{4,8}"""
        )

        /** Detects UPI IDs: "pay@upi", "attacker@ybl", etc. */
        private val UPI_REGEX = Regex(
            """[a-zA-Z0-9.\-_]{2,256}@[a-zA-Z]{2,64}"""
        )

        /** Detects URLs and shortened links. */
        private val LINK_REGEX = Regex(
            """(https?://|bit\.ly|tinyurl|t\.co|goo\.gl)\S+"""
        )
    }
}
