package com.androidblunders.rakshak.spam_detection

import android.util.Log
import com.androidblunders.rakshak.messaging.MessageData
import com.androidblunders.rakshak.messaging.MessageExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SpamDetectionOrchestrator — the pipeline entry-point.
 *
 * Subscribes to [MessageExtractor.messageFlow], converts each incoming
 * [MessageData] into a [CallContext], feeds it through [ThreatFusionEngine],
 * and logs / broadcasts the resulting [ThreatScore].
 *
 * ## Lifecycle
 * This singleton is created by Hilt and lives for the duration of the app
 * process. Call [startObserving] once from your Application class (or a
 * Hilt-injected initialiser) to activate the pipeline.
 *
 * ## Extending output
 * The `onThreatScored` lambda at the bottom of [startObserving] is the
 * integration point for UI updates, Room persistence, and notifications.
 * TODO: Replace the Log calls with a Room DAO insert and a LiveData/StateFlow
 *       emission once the security_history database is in place.
 */
@Singleton
class SpamDetectionOrchestrator @Inject constructor(
    private val fusionEngine: ThreatFusionEngine
) {
    companion object {
        private const val TAG = "SpamDetectionOrchestrator"

        // Thresholds for quick human-readable status in logs
        private const val THRESHOLD_SUSPICIOUS = 0.35f
        private const val THRESHOLD_WARN       = 0.60f
        private const val THRESHOLD_ALERT      = 0.80f
    }

    /**
     * A dedicated coroutine scope that survives configuration changes.
     * SupervisorJob ensures that a failure in one child coroutine does
     * not cancel the entire pipeline.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Begins observing [MessageExtractor.messageFlow].
     * Safe to call multiple times — subsequent calls are no-ops because
     * the flow collector is launched in the singleton [scope].
     */
    fun startObserving() {
        Log.i(TAG, "Starting spam detection pipeline…")

        MessageExtractor.messageFlow
            .onEach { message -> processMessage(message) }
            .catch { e -> Log.e(TAG, "messageFlow error — pipeline recovering", e) }
            .launchIn(scope)
    }

    // -------------------------------------------------------------------------
    // Private pipeline
    // -------------------------------------------------------------------------

    private fun processMessage(message: MessageData) {
        scope.launch {
            try {
                val context = message.toCallContext()
                Log.d(TAG, "Analysing message from ${context.sender} " +
                        "(${context.messageBody.take(60)}…)")

                val threatScore = fusionEngine.evaluate(context)
                onThreatScored(context, threatScore)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process message from ${message.sender}", e)
            }
        }
    }

    /**
     * Called with the final fused [ThreatScore] for every incoming message.
     *
     * Current behaviour: structured log output.
     *
     * TODO: Add the following integrations as the app grows —
     *   1. Room insert → security_history table
     *   2. StateFlow / LiveData emission → SecurityHistoryViewModel
     *   3. NotificationManager alert when score ≥ THRESHOLD_ALERT
     */
    private fun onThreatScored(ctx: CallContext, score: ThreatScore) {
        val level = when {
            score.score >= THRESHOLD_ALERT      -> "🚨 ALERT"
            score.score >= THRESHOLD_WARN       -> "⚠️  WARN"
            score.score >= THRESHOLD_SUSPICIOUS -> "🟡 SUSPICIOUS"
            else                                -> "✅ SAFE"
        }

        Log.i(
            TAG, """
            ┌── Spam Detection Result ─────────────────────────
            │  Sender   : ${ctx.sender}
            │  Package  : ${ctx.packageName}
            │  Score    : ${"%.3f".format(score.score)} (${(score.score * 100).toInt()}%)
            │  Label    : ${score.label}
            │  Confidence: ${"%.1f".format(score.confidence * 100)}%
            │  Status   : $level
            └──────────────────────────────────────────────────
            """.trimIndent()
        )
    }

    // -------------------------------------------------------------------------
    // Extension helpers
    // -------------------------------------------------------------------------

    /** Converts the messaging layer's [MessageData] to the spam detection [CallContext]. */
    private fun MessageData.toCallContext() = CallContext(
        sender      = sender,
        messageBody = content,
        packageName = packageName,
        timestamp   = timestamp
    )
}
