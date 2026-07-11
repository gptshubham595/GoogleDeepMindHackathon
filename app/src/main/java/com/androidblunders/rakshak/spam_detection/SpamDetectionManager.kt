package com.androidblunders.rakshak.spam_detection

/**
 * Represents the contextual input fed into any ThreatAnalyzer.
 *
 * @param sender       The originating phone number or contact name.
 * @param messageBody  The raw text content of the SMS / notification.
 * @param packageName  The app package that delivered this message (e.g. "com.android.mms").
 * @param timestamp    Unix epoch milliseconds when the message was received.
 */
data class CallContext(
    val sender: String,
    val messageBody: String,
    val packageName: String,
    val timestamp: Long
)

/**
 * The output produced by every ThreatAnalyzer.
 *
 * @param score      Normalised threat probability in the range [0.0, 1.0].
 *                   0.0 = definitely safe, 1.0 = definitely malicious.
 * @param label      Human-readable classification (e.g. "PHISHING", "SAFE", "SUSPICIOUS").
 * @param confidence Confidence of the model in its own output [0.0, 1.0].
 * @param rawOutput  The verbatim model response, useful for debugging / logging.
 */
data class ThreatScore(
    val score: Float,
    val label: String,
    val confidence: Float,
    val rawOutput: String = ""
)

/**
 * Unified contract that every threat-analysis model must satisfy.
 *
 * Implementations:
 *  - [GemmaAnalyzer]       — on-device, offline, MediaPipe LLM Inference API
 *  - [GeminiLiveAnalyzer]  — (future) cloud WebSocket, richer context
 *
 * New engines (e.g. regex rule-set, BERT classifier) can be added by
 * implementing this interface and registering them in [SpamDetectionModule].
 */
interface ThreatAnalyzer {
    /**
     * Analyses the given [context] and returns a [ThreatScore].
     * Must be safe to call from any coroutine context; implementations
     * should switch to an appropriate dispatcher internally.
     */
    suspend fun analyze(context: CallContext): ThreatScore
}