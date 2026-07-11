package com.androidblunders.rakshak.spam_detection

import android.util.Log
import com.androidblunders.rakshak.call.LiveTranscript
import com.androidblunders.rakshak.call.LiveTranscriptBus
import com.androidblunders.rakshak.core.model.ThreatLevel
import com.androidblunders.rakshak.messaging.MessageData
import com.androidblunders.rakshak.messaging.MessageExtractor
import com.androidblunders.rakshak.orchestrator.ThreatFusionEngine as CoreThreatState
import com.androidblunders.rakshak.reporting.TransactionDetailsExtractor
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
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

/** UI-facing result of a single analysis pass. */
data class SpamDetectionResult(
    val sender: String,
    val messageBody: String,
    val score: ThreatScore,
    val status: String,
    val timestamp: Long,
    val hasTransaction: Boolean = false,
)

/**
 * Fuses inbound SMS/chat notifications and live-call transcripts into the rich
 * [CallContext] consumed by [ThreatFusionEngine]. Results are published to the
 * dashboard and mapped into the app-wide core [ThreatLevel].
 */
@Singleton
class SpamDetectionOrchestrator @Inject constructor(
    private val fusionEngine: ThreatFusionEngine,
    private val coreThreatState: CoreThreatState,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val bufferMutex = Mutex()
    private val observing = AtomicBoolean(false)

    private val smsBuffer = ArrayDeque<SMSMessage>()
    private val transcriptBuffer = ArrayDeque<TranscriptSegment>()

    private val _latestResult = MutableStateFlow<SpamDetectionResult?>(null)
    val latestResult: StateFlow<SpamDetectionResult?> = _latestResult.asStateFlow()

    private val _recentResults = MutableStateFlow<List<SpamDetectionResult>>(emptyList())
    val recentResults: StateFlow<List<SpamDetectionResult>> = _recentResults.asStateFlow()

    private val _latestTransactionResult = MutableStateFlow<SpamDetectionResult?>(null)
    val latestTransactionResult: StateFlow<SpamDetectionResult?> =
        _latestTransactionResult.asStateFlow()

    /** Starts both collectors exactly once for the lifetime of this singleton. */
    fun startObserving() {
        if (!observing.compareAndSet(false, true)) return
        Log.i(TAG, "Starting spam detection pipeline")

        MessageExtractor.messageFlow
            .onEach(::onMessage)
            .catch { error -> Log.e(TAG, "Message flow failed", error) }
            .launchIn(scope)

        LiveTranscriptBus.transcripts
            .onEach(::onTranscript)
            .catch { error -> Log.e(TAG, "Transcript flow failed", error) }
            .launchIn(scope)
    }

    /** Direct typed injection for callers that already hold a normalized SMS. */
    fun pushSms(sms: SMSMessage) {
        scope.launch {
            val context = bufferMutex.withLock {
                smsBuffer.addLast(sms)
                trim(smsBuffer)
                buildSmsContext()
            }
            evaluate(context, sender = sms.sender, body = sms.body)
        }
    }

    private fun onMessage(message: MessageData) {
        scope.launch {
            val sms = message.toSms()
            val context = bufferMutex.withLock {
                smsBuffer.addLast(sms)
                trim(smsBuffer)
                buildSmsContext()
            }
            evaluate(context, sender = sms.sender, body = sms.body)
        }
    }

    private fun onTranscript(transcript: LiveTranscript) {
        scope.launch {
            val segment = TranscriptSegment(
                speaker = transcript.speaker,
                text = transcript.text,
            )
            val context = bufferMutex.withLock {
                transcriptBuffer.addLast(segment)
                trim(transcriptBuffer)
                buildTranscriptContext()
            }
            evaluate(context, sender = transcript.speaker, body = transcript.text)
        }
    }

    private suspend fun evaluate(context: CallContext, sender: String, body: String) {
        try {
            val score = fusionEngine.evaluate(context)
            publishResult(sender, body, score)
        } catch (error: Exception) {
            Log.e(TAG, "Analysis failed for $sender", error)
        }
    }

    private fun publishResult(sender: String, body: String, score: ThreatScore) {
        val isSuspiciousDebit = TransactionDetailsExtractor.isSuspiciousDebitSms(body)
        val deterministicFloor = TransactionDetailsExtractor.deterministicThreatFloor(body)
        val effectiveScore = if (isSuspiciousDebit && score.score < deterministicFloor) {
            score.copy(
                score = deterministicFloor,
                label = "PHISHING",
                confidence = maxOf(score.confidence, 0.95f),
                signals = (score.signals + "DEBIT_REVERSAL_LINK").distinct(),
            )
        } else {
            score
        }
        val status = statusFor(effectiveScore.score)
        val result = SpamDetectionResult(
            sender = sender,
            messageBody = body,
            score = effectiveScore,
            status = status,
            timestamp = System.currentTimeMillis(),
            hasTransaction = TransactionDetailsExtractor.isTransactionSms(body),
        )

        _latestResult.value = result
        _recentResults.value = (listOf(result) + _recentResults.value).take(MAX_BUFFER)
        if (result.hasTransaction) _latestTransactionResult.value = result
        coreThreatState.override(mapToThreatLevel(effectiveScore.score))

        Log.i(
            TAG,
            "Spam result: sender=$sender score=${(effectiveScore.score * 100).toInt()}% " +
                "label=${effectiveScore.label} stage=${effectiveScore.stage} " +
                "status=$status signals=${effectiveScore.signals}",
        )
    }

    private fun buildSmsContext(): CallContext = CallContext(
        callMetadata = CallMetadata(callerNumber = smsBuffer.lastOrNull()?.sender ?: "unknown"),
        recentSmsMessages = smsBuffer.toList(),
    )

    private fun buildTranscriptContext(): CallContext = CallContext(
        callMetadata = CallMetadata(callerNumber = "Live Call"),
        transcriptSegments = transcriptBuffer.toList(),
        recentSmsMessages = smsBuffer.toList(),
    )

    private fun MessageData.toSms() = SMSMessage(
        sender = sender,
        body = content,
        packageName = packageName,
        receivedAtMs = timestamp,
        isOtp = OTP_REGEX.containsMatchIn(content),
        extractedUpi = UPI_REGEX.find(content)?.value,
        containsLink = LINK_REGEX.containsMatchIn(content),
    )

    private fun <T> trim(buffer: ArrayDeque<T>) {
        while (buffer.size > MAX_BUFFER) buffer.removeFirst()
    }

    private fun statusFor(score: Float): String = when {
        score >= THRESHOLD_ALERT -> "🚨 ALERT"
        score >= THRESHOLD_WARN -> "⚠️ WARN"
        score >= THRESHOLD_SUSPICIOUS -> "🟡 SUSPICIOUS"
        else -> "✅ SAFE"
    }

    private fun mapToThreatLevel(score: Float): ThreatLevel = when {
        score >= 0.92f -> ThreatLevel.EMERGENCY
        score >= 0.75f -> ThreatLevel.ACTIVE_THREAT
        score >= 0.55f -> ThreatLevel.MEDIUM
        score >= 0.30f -> ThreatLevel.LOW
        else -> ThreatLevel.IDLE
    }

    private companion object {
        const val TAG = "SpamDetectionOrchestrator"
        const val MAX_BUFFER = 25
        const val THRESHOLD_SUSPICIOUS = 0.35f
        const val THRESHOLD_WARN = 0.60f
        const val THRESHOLD_ALERT = 0.80f

        val OTP_REGEX = Regex(
            """\b\d{4,8}\b.*(otp|code|password)|(otp|code|password).*\b\d{4,8}\b""",
            RegexOption.IGNORE_CASE,
        )
        val UPI_REGEX = Regex("""[a-zA-Z0-9._-]+@[a-zA-Z]{2,}""")
        val LINK_REGEX = Regex(
            """https?://|\bwww\.|\b[a-z0-9-]+\.(com|in|net|org|xyz|link|ly)\b""",
            RegexOption.IGNORE_CASE,
        )
    }
}
