package com.androidblunders.rakshak.spam_detection

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The ThreatFusionEngine is the "brain" that aggregates scores from every
 * registered [ThreatAnalyzer] and produces a single, reliable [ThreatScore].
 *
 * ## Current configuration (Gemma-only / offline-first)
 * Because no cloud analyzer is registered, the engine operates in
 * full local mode using weight = 1.0 for the Gemma score.
 *
 * ## Future: Adding Gemini Live
 * To enable the cloud analyzer, add [GeminiLiveAnalyzer] (or any other
 * [ThreatAnalyzer] implementation) to the [analyzers] list in
 * [SpamDetectionModule]. The fusion formula below will automatically
 * incorporate it:
 *
 *   finalScore = (gemmaScore * 0.4) + (geminiScore * 0.6)
 *
 * If Gemini returns null (network error / timeout), the engine transparently
 * falls back to Gemma-only mode — no crash, no interruption.
 *
 * @param analyzers The ordered list of [ThreatAnalyzer] instances provided
 *                  by the Hilt DI graph. Order: [GemmaAnalyzer] first,
 *                  cloud analyzers second.
 */
@Singleton
class ThreatFusionEngine @Inject constructor(
    private val analyzers: List<@JvmSuppressWildcards ThreatAnalyzer>
) {
    companion object {
        private const val TAG = "ThreatFusionEngine"

        // Weights used when BOTH local + cloud scores are available
        private const val WEIGHT_LOCAL = 0.4f
        private const val WEIGHT_CLOUD = 0.6f

        // Labels used in the fused result
        private const val LABEL_SAFE       = "SAFE"
        private const val LABEL_SUSPICIOUS = "SUSPICIOUS"
        private const val LABEL_PHISHING   = "PHISHING"
        private const val LABEL_SCAM       = "SCAM"
    }

    /**
     * Runs all registered analyzers against [context] and returns a fused
     * [ThreatScore].
     *
     * Scoring strategy:
     * - 1 analyzer  → weight = 1.0  (local-only mode)
     * - 2 analyzers → local * 0.4 + cloud * 0.6
     * - N analyzers → equal weights averaged (graceful extension)
     */
    suspend fun evaluate(context: CallContext): ThreatScore =
        withContext(Dispatchers.Default) {
            if (analyzers.isEmpty()) {
                Log.w(TAG, "No analyzers registered — returning safe default")
                return@withContext ThreatScore(0f, LABEL_SAFE, 0f, "no analyzers")
            }

            // Collect scores; nulls mean an analyzer failed / timed out
            val scores: List<ThreatScore?> = analyzers.map { analyzer ->
                try {
                    analyzer.analyze(context)
                } catch (e: Exception) {
                    Log.w(TAG, "${analyzer::class.simpleName} threw an exception", e)
                    null
                }
            }

            val validScores = scores.filterNotNull()

            if (validScores.isEmpty()) {
                Log.e(TAG, "All analyzers failed — returning safe default")
                return@withContext ThreatScore(0f, LABEL_SAFE, 0f, "all analyzers failed")
            }

            // --- Fusion logic ---
            val fusedScore: Float = when {
                // Single valid result → take it as-is
                validScores.size == 1 -> validScores[0].score

                // Exactly two results: index-0 = local, index-1 = cloud
                validScores.size == 2 -> {
                    val local = validScores[0].score
                    val cloud = validScores[1].score
                    (local * WEIGHT_LOCAL) + (cloud * WEIGHT_CLOUD)
                }

                // N results: simple average (future-proof for 3+ analyzers)
                else -> validScores.map { it.score }.average().toFloat()
            }

            val fusedConfidence = validScores.map { it.confidence }.average().toFloat()
            val dominantLabel = decideDominantLabel(fusedScore)

            Log.d(TAG, "Fused score=$fusedScore label=$dominantLabel " +
                    "(${validScores.size}/${analyzers.size} analyzers responded)")

            ThreatScore(
                score      = fusedScore.coerceIn(0f, 1f),
                label      = dominantLabel,
                confidence = fusedConfidence.coerceIn(0f, 1f),
                rawOutput  = validScores.joinToString(" | ") { it.rawOutput }
            )
        }

    /** Maps a numeric fused score to a human-readable threat label. */
    private fun decideDominantLabel(score: Float): String = when {
        score >= 0.80f -> LABEL_PHISHING
        score >= 0.60f -> LABEL_SCAM
        score >= 0.35f -> LABEL_SUSPICIOUS
        else           -> LABEL_SAFE
    }
}
