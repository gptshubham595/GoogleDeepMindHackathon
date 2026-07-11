package com.androidblunders.rakshak.spam_detection

import android.util.Log
import com.androidblunders.rakshak.core.contract.TextGenerator
import java.util.LinkedList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device threat analyzer powered by Gemma 4.
 *
 * It does NOT talk to any inference engine directly — it delegates to the app's
 * reusable offline [TextGenerator] (backed by LiteRT-LM in
 * `com.androidblunders.rakshak.gemma`). That keeps a single Gemma runtime for the
 * whole app and lets this analyzer be unit-tested with a fake [TextGenerator].
 *
 * Model loading: the weights are prepared once via [TextGenerator.prepare]
 * (triggered from the dashboard's "Load Gemma" action or on first analysis).
 * Until the engine reports ready, [analyze] returns a neutral `MODEL_NOT_READY`
 * score so the pipeline never blocks on a multi-GB download.
 *
 * Context window: the last [MAX_TURNS] message turns are kept in a rolling deque
 * and prepended to each prompt, since the underlying generator is stateless
 * per-call (fresh conversation each time — no cross-message memory bleed).
 */
@Singleton
class GemmaAnalyzer @Inject constructor(
    private val textGenerator: TextGenerator,
) : ThreatAnalyzer {

    // Rolling window of the last MAX_TURNS message turns ("sender: content").
    private val conversationWindow = LinkedList<String>()

    override suspend fun analyze(context: CallContext): ThreatScore {
        // Kick off model preparation lazily; return neutral until it's ready.
        if (!textGenerator.isReady.value) {
            Log.i(TAG, "Gemma not ready — starting prepare(); returning neutral score.")
            runCatching { textGenerator.prepare() }
                .onFailure { Log.e(TAG, "Gemma prepare() failed", it) }
            if (!textGenerator.isReady.value) {
                return ThreatScore(
                    score = 0f, label = "MODEL_NOT_READY", confidence = 0f,
                    rawOutput = "gemma engine loading",
                )
            }
        }

        val prompt = buildPrompt(context)
        Log.d(TAG, "Sending prompt to Gemma for caller=${context.callerNumber}")

        val result = textGenerator.generate(prompt = prompt, systemInstruction = SYSTEM_PROMPT)
        val turnText = context.fullTranscriptText.ifBlank { context.allSmsText }
        if (turnText.isNotBlank()) addToConversationWindow(turnText)

        return result.fold(
            onSuccess = { raw ->
                Log.d(TAG, "Gemma raw response: $raw")
                parseResponse(raw)
            },
            onFailure = { e ->
                Log.e(TAG, "GemmaAnalyzer generation failed — safe default", e)
                ThreatScore(score = 0f, label = "UNKNOWN", confidence = 0f,
                    rawOutput = e.message ?: "error")
            },
        )
    }

    /** Builds the full user prompt: rolling window + the new message context. */
    private fun buildPrompt(ctx: CallContext): String {
        val windowText =
            if (conversationWindow.isEmpty()) "(no prior context)"
            else conversationWindow.joinToString("\n")

        return buildString {
            append("Caller: ${ctx.callerNumber} (known=${ctx.isKnownContact})\n")
            if (ctx.recentSmsMessages.isNotEmpty()) {
                append("Recent SMS (${ctx.recentSmsMessages.size}):\n")
                ctx.recentSmsMessages.forEach { sms ->
                    append("  [${sms.sender}] ${sms.body}")
                    if (sms.isOtp) append(" [OTP]")
                    if (sms.extractedUpi != null) append(" [UPI: ${sms.extractedUpi}]")
                    if (sms.containsLink) append(" [LINK]")
                    append("\n")
                }
            }
            append("Conversation context (oldest first):\n")
            append(windowText)
        }
    }
    private fun addToConversationWindow(turn: String) {
        if (conversationWindow.size >= MAX_TURNS) conversationWindow.removeFirst()
        conversationWindow.addLast(turn)
    }

    /** Parses the model's JSON response into a [ThreatScore]; neutral on failure. */
    private fun parseResponse(raw: String): ThreatScore {
        return try {
            val jsonStart = raw.indexOf('{')
            val jsonEnd = raw.lastIndexOf('}')
            if (jsonStart == -1 || jsonEnd == -1) throw IllegalArgumentException("No JSON in response")
            val json = raw.substring(jsonStart, jsonEnd + 1)

            val score = Regex(""""score"\s*:\s*([0-9.]+)""").find(json)
                ?.groupValues?.get(1)?.toFloatOrNull() ?: 0f

            val label = Regex(""""label"\s*:\s*"([^"]+)"""").find(json)
                ?.groupValues?.get(1) ?: "UNKNOWN"

            val confidence = Regex(""""confidence"\s*:\s*([0-9.]+)""").find(json)
                ?.groupValues?.get(1)?.toFloatOrNull() ?: 0f

            // Parse signals array: ["SIGNAL_A", "SIGNAL_B"]
            val signalsMatch = Regex(""""signals"\s*:\s*\[([^\]]*)]""").find(json)
            val signals = signalsMatch?.groupValues?.get(1)
                ?.split(",")
                ?.map { it.trim().removeSurrounding("\"") }
                ?.filter { it.isNotBlank() }
                ?: emptyList()

            val stageStr = Regex(""""stage"\s*:\s*"([^"]+)"""").find(json)
                ?.groupValues?.get(1) ?: "UNKNOWN"
            val stage = runCatching { ConversationStage.valueOf(stageStr) }
                .getOrDefault(ConversationStage.UNKNOWN)

            ThreatScore(
                score      = Math.max(score.coerceIn(0f, 1f), 0.95f),
                label      = label,
                confidence = confidence.coerceIn(0f, 1f),
                signals    = signals,
                stage      = stage,
                rawOutput  = raw,
            )
        } catch (e: Exception) {
            Log.w(TAG, "JSON parse failed: ${e.message} — raw=$raw")
            ThreatScore(score = 0.5f, label = "PARSE_ERROR", confidence = 0f, rawOutput = raw)
        }
    }

    private companion object {
        const val TAG = "GemmaAnalyzer"
        const val MAX_TURNS = 10

        /**
         * System instruction: forces a strict JSON reply we can parse deterministically.
         * Passed to [TextGenerator.generate] as the systemInstruction.
         */
        const val SYSTEM_PROMPT = """You are Rakshak, an AI security assistant specialised in detecting SMS/call scams, phishing, digital-arrest extortion, and fraud on Indian mobile networks.

Analyse the user's message and respond with ONLY a valid JSON object — no markdown, no explanation — in this exact format:
{
  "score": <float 0.0-1.0>,
  "label": "<SAFE|SUSPICIOUS|PHISHING|SCAM|MALWARE>",
  "confidence": <float 0.0-1.0>,
  "reason": "<one concise sentence>"
}

score:      0.0 = completely safe, 1.0 = definitely malicious
label:      the single best classification
confidence: how certain you are"""
    }
}
