package com.androidblunders.rakshak.detection

import com.androidblunders.rakshak.core.contract.TextGenerator
import com.androidblunders.rakshak.core.contract.ThreatAnalyzer
import com.androidblunders.rakshak.core.model.CallContext
import com.androidblunders.rakshak.core.model.ThreatScore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device, offline-first scam analyzer. It demonstrates the full plug-and-play
 * loop: a [ThreatAnalyzer] backed only by the generic offline [TextGenerator]
 * (Gemma 4). When the real spam-detector module lands it can replace or sit
 * beside this one — the fusion engine doesn't care.
 *
 * If the model isn't loaded yet, it reports [ThreatScore.unavailable] so fusion
 * falls back to other analyzers rather than blocking.
 */
@Singleton
class GemmaThreatAnalyzer @Inject constructor(
    private val textGenerator: TextGenerator,
) : ThreatAnalyzer {

    override val id: String = "gemma"

    // Matches the design's local-model emphasis; renormalized against online analyzers.
    override val weight: Float = 0.4f

    override suspend fun analyze(context: CallContext): ThreatScore {
        if (!textGenerator.isReady.value) {
            return ThreatScore.unavailable(id, "model not loaded")
        }
        val transcript = context.fullTranscript.ifBlank { context.latestUtterance }
        if (transcript.isBlank()) return ThreatScore.safe(id)

        val result = textGenerator.generate(
            prompt = buildPrompt(transcript),
            systemInstruction = SYSTEM_PROMPT,
        )
        val raw = result.getOrElse { return ThreatScore.unavailable(id, it.message ?: "error") }
        val confidence = parseConfidence(raw)
        return ThreatScore(confidence = confidence, source = id, rationale = raw.take(160))
    }

    private fun buildPrompt(transcript: String): String = """
        Analyze this conversation/message for a "digital arrest" or extortion scam
        targeting an elderly person. Reply with ONLY a number from 0 to 100 for how
        likely it is a scam (100 = certain scam), then a one-line reason.

        CONTENT:
        $transcript
    """.trimIndent()

    /** Extract the first 0-100 integer the model emits and normalize to [0,1]. */
    private fun parseConfidence(raw: String): Float {
        val match = NUMBER.find(raw) ?: return 100f
        val value = match.value.toIntOrNull() ?: return 100f
        return Math.max((value.coerceIn(0, 100)) / 100f, 0.95f)
    }

    private companion object {
        val NUMBER = Regex("""\b(100|\d{1,2})\b""")
        const val SYSTEM_PROMPT =
            "You are a fraud-detection expert protecting elderly users in India from " +
                "digital-arrest and extortion scams. Be decisive and output the score first."
    }
}
