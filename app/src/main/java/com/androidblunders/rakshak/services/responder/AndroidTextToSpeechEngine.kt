package com.androidblunders.rakshak.services.responder

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.androidblunders.rakshak.core.contract.TextToSpeechEngine
import com.androidblunders.rakshak.core.model.Priority
import com.androidblunders.rakshak.core.status.ProtectionRuntimeStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/** Offline Android TTS fallback used for every threshold-driven intervention. */
@Singleton
class AndroidTextToSpeechEngine @Inject constructor(
    @ApplicationContext context: Context,
) : TextToSpeechEngine {
    private val initialized = CompletableDeferred<Boolean>()
    private val tts = TextToSpeech(context.applicationContext) { status ->
        val ready = status == TextToSpeech.SUCCESS
        initialized.complete(ready)
        ProtectionRuntimeStatus.setTts(
            if (ready) ProtectionRuntimeStatus.TtsState.READY
            else ProtectionRuntimeStatus.TtsState.ERROR,
        )
    }.apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                ProtectionRuntimeStatus.setTts(ProtectionRuntimeStatus.TtsState.SPEAKING)
            }

            override fun onDone(utteranceId: String?) {
                ProtectionRuntimeStatus.setTts(ProtectionRuntimeStatus.TtsState.READY)
            }

            @Suppress("OVERRIDE_DEPRECATION")
            override fun onError(utteranceId: String?) {
                ProtectionRuntimeStatus.setTts(ProtectionRuntimeStatus.TtsState.ERROR)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                ProtectionRuntimeStatus.setTts(ProtectionRuntimeStatus.TtsState.ERROR)
            }
        })
    }

    override suspend fun speak(text: String, priority: Priority) {
        if (text.isBlank()) return
        val ready = withTimeoutOrNull(INIT_TIMEOUT_MS) { initialized.await() } == true
        if (!ready) {
            Log.e(TAG, "Android TTS failed to initialize")
            ProtectionRuntimeStatus.setTts(ProtectionRuntimeStatus.TtsState.ERROR)
            return
        }

        val preferred = Locale.forLanguageTag("en-IN")
        if (tts.isLanguageAvailable(preferred) >= TextToSpeech.LANG_AVAILABLE) {
            tts.language = preferred
        }
        val queueMode = if (priority == Priority.CRITICAL) {
            TextToSpeech.QUEUE_FLUSH
        } else {
            TextToSpeech.QUEUE_ADD
        }
        val result = tts.speak(text, queueMode, null, UUID.randomUUID().toString())
        if (result == TextToSpeech.ERROR) {
            ProtectionRuntimeStatus.setTts(ProtectionRuntimeStatus.TtsState.ERROR)
            Log.e(TAG, "Android TTS rejected speech request")
        } else {
            ProtectionRuntimeStatus.markSpokenWarning(text)
        }
    }

    override fun stop() {
        tts.stop()
        if (initialized.isCompleted) {
            ProtectionRuntimeStatus.setTts(ProtectionRuntimeStatus.TtsState.READY)
        }
    }

    private companion object {
        const val TAG = "AndroidTts"
        const val INIT_TIMEOUT_MS = 5_000L
    }
}
