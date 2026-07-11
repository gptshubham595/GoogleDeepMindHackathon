package com.androidblunders.rakshak

import android.app.Application
import com.androidblunders.rakshak.spam_detection.SpamDetectionOrchestrator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Rakshak Application — the Hilt DI entry-point for the entire app.
 *
 * [HiltAndroidApp] triggers Hilt's code generation and bootstraps the
 * dependency injection graph at app start-up.
 *
 * The [SpamDetectionOrchestrator] is started here so it begins observing
 * incoming messages as soon as the process is alive, independent of any
 * Activity lifecycle.
 */
@HiltAndroidApp
class RakshakApplication : Application() {

    @Inject
    lateinit var spamDetectionOrchestrator: SpamDetectionOrchestrator

    override fun onCreate() {
        super.onCreate()

        // Activate the spam detection pipeline.
        // From this point on, every message captured by MessageExtractor
        // flows through ThreatFusionEngine → GemmaAnalyzer automatically.
        spamDetectionOrchestrator.startObserving()
    }
}
