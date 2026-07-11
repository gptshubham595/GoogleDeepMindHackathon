package com.androidblunders.rakshak.spam_detection

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoList
import javax.inject.Singleton

/**
 * Hilt DI module that wires the entire Spam Detection pipeline together.
 *
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │  HOW TO ADD A NEW ANALYZER                                          │
 * │  1. Create a class implementing [ThreatAnalyzer].                   │
 * │  2. Annotate it with @Singleton + @Inject constructor(...).          │
 * │  3. Add a @Binds @IntoList @Singleton fun below,                    │
 * │     pointing to your new class.                                     │
 * │  ThreatFusionEngine will pick it up automatically.                  │
 * └─────────────────────────────────────────────────────────────────────┘
 *
 * Current registered analyzers:
 *  • [GemmaAnalyzer] — on-device, always available (offline-first)
 *
 * Future analyzers to add here:
 *  • GeminiLiveAnalyzer — cloud WebSocket (add when API key is available)
 *  • RegexRuleAnalyzer  — fast deterministic rule set for known patterns
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SpamDetectionModule {

    /**
     * Registers [GemmaAnalyzer] as the primary (offline-first) threat analyzer.
     * It is placed into a Hilt multibinding list consumed by [ThreatFusionEngine].
     */
    @Binds
    @IntoList
    @Singleton
    abstract fun bindGemmaAnalyzer(impl: GemmaAnalyzer): ThreatAnalyzer

    companion object {

        /**
         * Provides the [ThreatFusionEngine] with the full list of registered
         * [ThreatAnalyzer] implementations.
         *
         * Note: The list is injected automatically by Hilt's multibinding
         * mechanism — no manual list construction needed.
         */
        @Provides
        @Singleton
        fun provideThreatFusionEngine(
            analyzers: List<@JvmSuppressWildcards ThreatAnalyzer>
        ): ThreatFusionEngine = ThreatFusionEngine(analyzers)
    }
}
