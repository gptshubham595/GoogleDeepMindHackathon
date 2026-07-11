package com.androidblunders.rakshak.spam_detection

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.LinkedList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device threat analyzer powered by the Gemma 4 model via
 * the MediaPipe LLM Inference API.
 *
 * ┌─────────────────────────────────────────────────────────────┐
 * │  MODEL SETUP                                                │
 * │  Place the Gemma .task file at:                            │
 * │      app/src/main/assets/gemma.task                         │
 * │                                                             │
 * │  TODO: If the model is not bundled in assets, implement     │
 * │        a download-on-first-run strategy here.               │
 * └─────────────────────────────────────────────────────────────┘
 *
 * Context window: The last [MAX_TURNS] message turns are kept in a
 * rolling deque so the model retains short-term conversational memory.
 */
@Singleton
class GemmaAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context
) : ThreatAnalyzer {

    companion object {
        private const val TAG = "GemmaAnalyzer"

        /** Path to the Gemma .task file inside the app's assets folder. */
        private const val MODEL_ASSET_PATH = "gemma.task"

        /** Maximum number of previous message turns kept in the rolling context window. */
        private const val MAX_TURNS = 10

        /**
         * Prompt template sent to the model.
         * The model is instructed to reply with ONLY a JSON object so
         * we can parse the score deterministically.
         */
        private const val SYSTEM_PROMPT = """You are Rakshak, an AI security assistant specialised in detecting SMS-based scams, phishing, and fraud on Indian mobile networks.

Analyse the following message and respond with ONLY a valid JSON object — no markdown, no explanation — in this exact format:
{
  "score": <float 0.0-1.0>,
  "label": "<SAFE|SUSPICIOUS|PHISHING|SCAM|MALWARE>",
  "confidence": <float 0.0-1.0>,
  "reason": "<one concise sentence>"
}

score:      0.0 = completely safe, 1.0 = definitely malicious
label:      the single best classification
confidence: how certain you are

Conversation context (oldest first):
"""
    }

    // Rolling window of the last MAX_TURNS message turns (sender: content).
    private val conversationWindow = LinkedList<String>()

    // Lazily-initialised MediaPipe LLM session — created once on first use.
    private var llmInference: LlmInference? = null

    // -------------------------------------------------------------------------
    // ThreatAnalyzer implementation
    // -------------------------------------------------------------------------

    override suspend fun analyze(context: CallContext): ThreatScore =
        withContext(Dispatchers.IO) {
            try {
                val inference = getOrCreateInference()
                val prompt = buildPrompt(context)

                Log.d(TAG, "Sending prompt to Gemma for sender=${context.sender}")
                val rawResponse = inference.generateResponse(prompt)
                Log.d(TAG, "Gemma raw response: $rawResponse")

                // Update the rolling context window AFTER analysis
                addToConversationWindow("${context.sender}: ${context.messageBody}")

                parseResponse(rawResponse)
            } catch (e: Exception) {
                Log.e(TAG, "GemmaAnalyzer failed — returning safe default", e)
                ThreatScore(score = 0f, label = "UNKNOWN", confidence = 0f,
                    rawOutput = e.message ?: "error")
            }
        }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the existing [LlmInference] session or creates one from the
     * model bundled in assets.
     *
     * TODO: If you want to support runtime-downloaded models, replace
     *       [LlmInferenceOptions.builder().setModelAssetPath] with the
     *       absolute path to the downloaded file in internal storage.
     */
    private fun getOrCreateInference(): LlmInference {
        if (llmInference != null) return llmInference!!

        val options = LlmInferenceOptions.builder()
            .setModelAssetPath(MODEL_ASSET_PATH)
            .setMaxTokens(1024)
            .setTopK(40)
            .setTemperature(0.1f)   // Low temperature → more deterministic JSON output
            .setRandomSeed(42)
            .build()

        llmInference = LlmInference.createFromOptions(this.context, options)
        Log.i(TAG, "LlmInference session created from assets/$MODEL_ASSET_PATH")
        return llmInference!!
    }

    /** Builds the full prompt string from the system prompt + rolling window + new message. */
    private fun buildPrompt(ctx: CallContext): String {
        val windowText = if (conversationWindow.isEmpty()) {
            "(no prior context)"
        } else {
            conversationWindow.joinToString("\n")
        }

        return buildString {
            append(SYSTEM_PROMPT)
            append(windowText)
            append("\n\nNew message from ${ctx.sender}:\n")
            append(ctx.messageBody)
        }
    }

    /** Adds a turn to the rolling context window, evicting the oldest if over limit. */
    private fun addToConversationWindow(turn: String) {
        if (conversationWindow.size >= MAX_TURNS) {
            conversationWindow.removeFirst()
        }
        conversationWindow.addLast(turn)
    }

    /**
     * Parses the model's JSON response into a [ThreatScore].
     * Falls back to a neutral score if parsing fails.
     */
    private fun parseResponse(raw: String): ThreatScore {
        return try {
            // Extract the JSON block (model may add stray whitespace / backticks)
            val jsonStart = raw.indexOf('{')
            val jsonEnd = raw.lastIndexOf('}')
            if (jsonStart == -1 || jsonEnd == -1) throw IllegalArgumentException("No JSON in response")

            val json = raw.substring(jsonStart, jsonEnd + 1)

            val score = Regex("\"score\"\\s*:\\s*([0-9.]+)").find(json)
                ?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
            val label = Regex("\"label\"\\s*:\\s*\"([^\"]+)\"").find(json)
                ?.groupValues?.get(1) ?: "UNKNOWN"
            val confidence = Regex("\"confidence\"\\s*:\\s*([0-9.]+)").find(json)
                ?.groupValues?.get(1)?.toFloatOrNull() ?: 0f

            ThreatScore(
                score = score.coerceIn(0f, 1f),
                label = label,
                confidence = confidence.coerceIn(0f, 1f),
                rawOutput = raw
            )
        } catch (e: Exception) {
            Log.w(TAG, "JSON parse failed: ${e.message} — raw=$raw")
            ThreatScore(score = 0.5f, label = "PARSE_ERROR", confidence = 0f, rawOutput = raw)
        }
    }
}
