Android SpamGuard: Real-Time Scam Detection Module
Implementation Plan
Version: 1.0
Target Platform: Android API 26+
Language: Kotlin
Architecture: Plug-and-Play SDK Module
1. Executive Summary
This document outlines the phased implementation of the SpamGuard Scam Detection Module as a self-contained, plug-and-play SDK. The consuming application only interacts with a simple facade (SpamGuardSession) by feeding inputs and receiving risk state updates. All pipeline complexity—including preprocessing, AI classification, risk aggregation, and intervention orchestration—is internal to the module.
2. Plug-and-Play Contract
2.1 Public API Surface
The module exposes exactly one entry point and one output stream to the host app.
kotlin
// Host app calls these simple methods:
SpamGuardSession.start(callMetadata: CallMetadata)
SpamGuardSession.pushAudio(chunk: CallAudioChunk)
SpamGuardSession.pushTranscript(segment: TranscriptSegment)
SpamGuardSession.pushSMS(sms: SMSMessage)
SpamGuardSession.pushDeviceContext(context: DeviceContext)
SpamGuardSession.end()

// Host app observes this:
SpamGuardSession.riskState: Flow<AggregatedRiskState>
SpamGuardSession.interventionEvents: Flow<InterventionEvent>
2.2 Integration Promise
Table
Concern	Host App Responsibility	Module Responsibility
Input Collection	Gather audio, SMS, metadata, device state	Accept via typed contracts
Pipeline Orchestration	None	Full end-to-end management
AI/ML Logic	None	Gemini integration, prompt management, fallback
Risk Calculation	None	Exponential decay, hard triggers, aggregation
User Intervention	None (optional UI delegation available)	Audio alerts, banners, hard-lock UI
Data Privacy	Grant permissions	On-device processing, encrypted persistence
Lifecycle	Call start() / end()	Internal CoroutineScope, cleanup, crash recovery
3. Phase Breakdown
Phase 1: Foundation & Contracts (Week 1)
Goal: Establish the immutable API boundary and data structures.
Deliverables:
Define all data classes (CallMetadata, TranscriptSegment, SMSMessage, DeviceContext, CallAudioChunk, AcousticFeatures, AggregatedRiskState, InterventionEvent)
Create SpamGuardSession facade interface
Set up internal module structure with clear package boundaries:
api/ → Public facade and data contracts
internal/pipeline/ → All processing modules
internal/ai/ → Gemini wrapper
internal/intervention/ → UI/Audio triggers
internal/persistence/ → EncryptedSharedPreferences
Implement ContextBuffer as a thread-safe StateFlow-backed singleton with Mutex protection
Add EncryptedSharedPreferences adapter for crash recovery snapshots
Key Decisions:
ContextBuffer is the single source of truth; all modules read from and write to it
Public API uses immutable data classes only; no internal state leaks to host
Snapshot interval: Every 10 seconds to encrypted storage
Phase 2: Preprocessing Engine (Week 2)
Goal: Build the fast on-device filtering layer (<100ms).
Deliverables:
PreprocessingEngine with Flow<TranscriptSegment> input and Flow<PreprocessingResult> output
HardKeywordMatcher: Regex engine with weighted scoring
UPIExtractor: Pattern matcher with entity storage in ContextBuffer
HinglishNormalizer: Lightweight string normalization map (no ML, static mapping + regex)
SMSCorrelator (initial sync version): Checks ContextBuffer.recentSMS against transcript keywords
AcousticFeatureExtractor: Calculates talk ratio, WPM, interruptions from CallAudioChunk streams
Integration Point:
Host app pushes TranscriptSegment → SpamGuardSession → PreprocessingEngine → updates ContextBuffer immediately
Host app pushes CallAudioChunk → AcousticFeatureExtractor → updates ContextBuffer
Performance Gates:
Regex matching must complete in <50ms for 500-character strings
Acoustic feature calculation must run off-main-thread with Dispatchers.Default
Phase 3: Context Buffer & State Machine (Week 3)
Goal: Implement the stateful conversation tracker.
Deliverables:
Full ConversationState data structure with all fields
State transition logic for stage progression (INTRO → AUTHORITY_ESTABLISHMENT → THREAT_DELIVERY, etc.)
Turn counting and talk duration tracking
Automatic pruning: Keep max 20 transcript segments or last 5 minutes, whichever is smaller
firedSignalCategories deduplication (don't double-count same regex match)
Thread-safe updates via Mutex or Channel actor pattern
Validation Criteria:
Concurrent updates from audio, SMS, and Gemini threads must not corrupt state
State snapshots must serialize/deserialize correctly for crash recovery
Stage transitions must be deterministic and unit-testable
Phase 4: Gemini Flash Integration (Week 4)
Goal: Build the AI classification tier with strict output contracts.
Deliverables:
GeminiFlashClassifier internal class
Prompt template builder: Serializes ContextBuffer into the exact JSON input payload specified
API wrapper with:
Bounded queue (max 1 pending request, drop intermediate if backpressure)
Debounce (min 3 seconds between calls)
Trigger conditions: Every 5-10s OR >10 new words in transcript OR regex score jump >20
Network unavailable fallback: Skip Gemini, cap risk score at 75
JSON schema enforcement for Gemini output (risk_score, signals, confidence, stage_assessment, reasoning)
Response parsing and validation
Error Handling:
Malformed JSON → Retry once, then fallback to regex-only mode
Timeout (>1200ms) → Abort and use last known good score
Quota exhaustion → Enter degraded mode automatically
Phase 5: Risk Aggregation Engine (Week 5)
Goal: Implement the "brain" with continuous risk scoring.
Deliverables:
RiskAggregator with Flow<AggregatedRiskState> output
ExponentialDecayAccumulator:
Decay factor: 0.92 per second
Weighted turn addition with recency weights (1.0, 0.8, 0.6)
Hard trigger override system:
Digital arrest pattern → Score 100
Full script progression → Score 100
Active financial extraction → Score 100
UPI extortion triad → Score 95
Known contact mitigation → Cap at 30
SMSBoost: +20 points when OTP SMS correlates with transcript
StageProgressionBoost: +10 per transition within 3 minutes
Threshold evaluation: Watch(40), Warn(65), Alert(80), Intervene(90), HardLock(100)
Output Contract:
kotlin
data class AggregatedRiskState(
    val currentScore: Float,        // 0-100
    val trend: RiskTrend,           // RISING, FALLING, STABLE, SPIKE
    val timeAtCurrentLevel: Long,   // ms above current threshold
    val dominantSignals: List<String>,
    val recommendedAction: InterventionAction
)
Phase 6: Intervention Engine (Week 6)
Goal: Build the non-blocking action executor.
Deliverables:
InterventionEngine that consumes AggregatedRiskState
Threshold-action mapping:
≥40: MONITOR (status indicator)
≥65: DISPLAY_WARNING (banner notification)
≥80: PLAY_AUDIO_ALERT (beep + spoken caution)
≥90: INJECT_VOICE (TTS over speaker, not routed to caller)
≥100: HARD_LOCK (full-screen modal + optional auto-disconnect)
InterventionEvent sealed class for host app observation
Audio injection system using AudioManager + TextToSpeech
Hard-lock UI overlay using WindowManager with TYPE_APPLICATION_OVERLAY
Dismissal handling: 60-second pause on user explicit dismiss, with logging
Safety Requirements:
Audio must route to speaker only, never to telephony uplink
Hard-lock modal must require explicit confirmation to dismiss
All interventions must be cancellable if score drops rapidly
Phase 7: SMS Correlator Service (Week 7)
Goal: Background SMS monitoring with correlation logic.
Deliverables:
SMSCorrelatorService using BroadcastReceiver (SMS) or NotificationListenerService (fallback)
Correlation rules:
OTP/verification SMS during active call + risk >30 → +20 boost
UPI transaction SMS + transcript mentions "refund"/"excess" → +15 boost
SMS sender matches caller number + payment link → +25 boost
JobScheduler/WorkManager integration for background correlation when call is inactive
Permission handling documentation for host app
Phase 8: Integration, Testing & Hardening (Week 8)
Goal: Ensure plug-and-play reliability.
Deliverables:
Unit Tests:
RiskAggregator with mock score sequences (test decay, accumulation, hard triggers)
HardKeywordMatcher with known scam transcript fixtures
ContextBuffer state transition tests
Mock Gemini JSON response parsing
Integration Tests:
End-to-end pipeline with fake inputs
Thread-safety stress tests (concurrent updates)
Memory leak validation (ensure 5-minute pruning works)
Real-World Validation:
Play recorded scam audio, verify score progression matches expected escalation
Test Hinglish transcripts
Test offline mode (airplane mode during call)
Edge Case Handling:
STT failure: Fallback to acoustic + metadata only
Network unavailable: Degraded mode cap at 75
Known contact: Cap score at 30 unless payment keywords present
Short calls (<30s): Suppress high scores
Battery optimization: Verify background SMS correlation works with Doze mode
Phase 9: SDK Packaging & Documentation (Week 9)
Goal: Make it truly plug-and-play for other teams.
Deliverables:
AAR library packaging with ProGuard/R8 rules
README.md with:
Gradle import instructions
Gemini API key setup
Required permissions list with rationale
5-minute integration guide (start → push inputs → observe risk)
Architecture diagram
Sample host app demonstrating integration
Changelog and versioning strategy
4. Data Flow Architecture
plain
Host App
   │
   ├─► SpamGuardSession.start(CallMetadata)
   ├─► SpamGuardSession.pushAudio(CallAudioChunk) ─┐
   ├─► SpamGuardSession.pushTranscript(TranscriptSegment) ─┤
   ├─► SpamGuardSession.pushSMS(SMSMessage) ───────────────┤
   ├─► SpamGuardSession.pushDeviceContext(DeviceContext) ──┤
   │                                                       │
   │   ┌───────────────────────────────────────────────────┘
   │   │
   ▼   ▼
┌─────────────────────────────────────────────────────────────┐
│                    ContextBuffer (Mutex-protected)           │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────────┐    │
│  │ Transcript  │  │ Extracted    │  │ Device / Call    │    │
│  │ History     │  │ Entities     │  │ Metadata         │    │
│  └─────────────┘  └──────────────┘  └──────────────────┘    │
└─────────────────────────────────────────────────────────────┘
   │                           ▲                    ▲
   ▼                           │                    │
┌──────────────────┐           │                    │
│ Preprocessing    │───────────┘                    │
│ Engine           │                                │
│ (Regex/UPI/Audio)│                                │
└──────────────────┘                                │
   │                                                 │
   ▼                                                 │
┌──────────────────┐     ┌─────────────────┐       │
│ GeminiFlash      │◄────│ Trigger Logic   │       │
│ Classifier       │     │ (5-10s / words) │       │
│ (JSON Schema)    │     └─────────────────┘       │
└──────────────────┘                               │
   │                                                 │
   ▼                                                 │
┌──────────────────┐                                │
│ RiskAggregator   │────────────────────────────────┘
│ (Decay + Triggers)│
└──────────────────┘
   │
   ▼
┌──────────────────┐     ┌─────────────────┐
│ Intervention     │────►│ Host App        │
│ Engine           │     │ (Observes Flow) │
└──────────────────┘     └─────────────────┘
5. Public API Specification
5.1 Session Lifecycle
kotlin
// Initialize (host app provides application context + config)
val session = SpamGuard.createSession(
    context = applicationContext,
    config = SpamGuardConfig(geminiApiKey = "...", enabledLanguages = listOf("en", "hi"))
)

// During call
session.start(callMetadata)
session.pushTranscript(segment)
session.pushSMS(sms)

// Observe
session.riskState.collect { state -> updateUI(state) }
session.interventionEvents.collect { event -> handleIntervention(event) }

// Cleanup
session.end()
5.2 Configuration Options
Table
Parameter	Type	Default	Description
geminiApiKey	String	Required	Google AI SDK key
enabledLanguages	List<String>	["en", "hi"]	STT language support
interventionEnabled	Boolean	true	Allow auto-interventions
offlineModeCap	Float	75.0	Max risk without network
decayFactor	Float	0.92	Risk decay per second
autoDisconnectDelay	Long	10000	ms before auto-hangup at HardLock
6. Threading Model
Table
Component	Dispatcher	Strategy
Public API input	Dispatchers.Main	Accept inputs immediately, queue internally
Preprocessing	Dispatchers.Default	Parallel regex + audio
ContextBuffer	Mutex + StateFlow	Single-writer, multi-reader
Gemini Calls	Dispatchers.IO	Bounded queue, drop stale
RiskAggregator	Dispatchers.Default	Timer-based decay (1s interval)
Intervention	Dispatchers.Main	UI/audio must be main thread
7. Security & Privacy Checklist
[ ] All PII stays in EncryptedSharedPreferences
[ ] No transcript or audio leaves device except Gemini API calls (opt-in)
[ ] Gemini prompts must not include raw phone numbers (hash or mask)
[ ] Crash snapshots encrypted with AES-256
[ ] No logging of OTP content or UPI IDs in plaintext
[ ] Host app must declare all permissions; module validates at runtime
8. Success Metrics
Table
Metric	Target
End-to-end latency (Tier-1)	<800ms
Gemini classification latency	<1200ms
Preprocessing latency	<100ms
Offline mode functionality	100% operational (capped at 75)
False positive rate (known contacts)	<5%
Battery impact per call	<3% drain per 10 minutes
Host app integration time	<30 minutes
9. Risk Mitigation
Table
Risk	Mitigation
Gemini API quota exhaustion	Debounce + degraded mode fallback
Thread contention on ContextBuffer	Mutex + actor pattern, no blocking reads
Memory leaks from transcript buffer	Hard limit: 20 segments / 5 minutes
Audio routing to caller	Use AudioManager.STREAM_MUSIC, not STREAM_VOICE_CALL
Permission denial	Graceful degradation; module works with reduced inputs
10. Deliverables Checklist
[ ] Kotlin source for all 7 internal modules
[ ] Public API facade (SpamGuardSession)
[ ] Immutable data class definitions
[ ] Thread-safe ContextBuffer implementation
[ ] Gemini prompt template + JSON schema enforcement
[ ] RiskAggregator with exponential decay + hard triggers
[ ] InterventionEngine with audio injection + hard-lock UI
[ ] SMSCorrelator background service
[ ] Unit tests for RiskAggregator and regex engine
[ ] AndroidManifest.xml with all required permissions
[ ] README with 5-minute integration guide
[ ] AAR packaging configuration