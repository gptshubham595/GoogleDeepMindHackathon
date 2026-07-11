from fastapi import FastAPI
from fastapi.responses import HTMLResponse
from fastapi.middleware.cors import CORSMiddleware
from app.config import settings
from app.routers import ws_call, translate, tts, offline, evidence
import logging

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
logger = logging.getLogger(__name__)

app = FastAPI(
    title="RAKSHAK Voice Backend",
    description="Digital Arrest Extortion Live Call Threat Detection Service using Gemini AI",
    version="1.0.0"
)

# CORS Policy
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Register routers
app.include_router(ws_call.router)
app.include_router(translate.router)
app.include_router(tts.router)
app.include_router(offline.router)
app.include_router(evidence.router)

# Embedded Dashboard HTML
DASHBOARD_HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>RAKSHAK | Threat Detection Control Center</title>
    <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;600;800&family=JetBrains+Mono:wght@400;700&display=swap" rel="stylesheet">
    <style>
        :root {
            --bg-base: #0a0c10;
            --bg-panel: rgba(18, 22, 30, 0.7);
            --border-glow: rgba(0, 242, 254, 0.15);
            --primary: #00f2fe;
            --secondary: #4facfe;
            --success: #00e676;
            --warning: #ffd600;
            --danger: #ff1744;
            --text-main: #f0f4f8;
            --text-muted: #8a99ad;
        }

        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
        }

        body {
            font-family: 'Outfit', sans-serif;
            background-color: var(--bg-base);
            color: var(--text-main);
            min-height: 100vh;
            overflow-x: hidden;
            background-image: 
                radial-gradient(circle at 10% 20%, rgba(79, 172, 254, 0.08) 0%, transparent 40%),
                radial-gradient(circle at 90% 80%, rgba(0, 242, 254, 0.06) 0%, transparent 40%);
        }

        header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 2rem 4rem;
            border-bottom: 1px solid rgba(255, 255, 255, 0.05);
            backdrop-filter: blur(10px);
            position: sticky;
            top: 0;
            z-index: 100;
        }

        .logo-container {
            display: flex;
            align-items: center;
            gap: 12px;
        }

        .logo-badge {
            background: linear-gradient(135deg, var(--primary), var(--secondary));
            width: 42px;
            height: 42px;
            border-radius: 12px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-weight: 800;
            color: #050505;
            box-shadow: 0 0 20px rgba(0, 242, 254, 0.4);
            font-size: 1.3rem;
        }

        h1.brand {
            font-size: 1.8rem;
            font-weight: 800;
            letter-spacing: 2px;
            background: linear-gradient(to right, #fff, var(--text-muted));
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
        }

        .status-pill {
            display: flex;
            align-items: center;
            gap: 8px;
            background: rgba(0, 230, 118, 0.1);
            border: 1px solid var(--success);
            padding: 6px 14px;
            border-radius: 20px;
            font-size: 0.85rem;
            font-weight: 600;
            color: var(--success);
        }

        .pulse-dot {
            width: 8px;
            height: 8px;
            background-color: var(--success);
            border-radius: 50%;
            animation: pulse 1.5s infinite;
        }

        @keyframes pulse {
            0% { transform: scale(0.9); opacity: 0.6; }
            50% { transform: scale(1.2); opacity: 1; }
            100% { transform: scale(0.9); opacity: 0.6; }
        }

        main {
            max-width: 1400px;
            margin: 0 auto;
            padding: 3rem 2rem;
            display: grid;
            grid-template-columns: 1.6fr 1fr;
            gap: 2.5rem;
        }

        .card {
            background: var(--bg-panel);
            border: 1px solid rgba(255, 255, 255, 0.05);
            border-radius: 20px;
            backdrop-filter: blur(16px);
            padding: 2rem;
            box-shadow: 0 10px 30px rgba(0,0,0,0.3);
            display: flex;
            flex-direction: column;
            gap: 1.5rem;
            transition: all 0.3s ease;
        }

        .card:hover {
            border-color: var(--border-glow);
        }

        h2 {
            font-size: 1.4rem;
            font-weight: 600;
            display: flex;
            align-items: center;
            gap: 10px;
            border-bottom: 1px solid rgba(255,255,255,0.05);
            padding-bottom: 0.8rem;
        }

        /* Simulator Styles */
        .simulator-console {
            background: rgba(5, 7, 10, 0.9);
            border-radius: 12px;
            padding: 1.5rem;
            font-family: 'JetBrains Mono', monospace;
            min-height: 250px;
            max-height: 350px;
            overflow-y: auto;
            border: 1px solid rgba(255, 255, 255, 0.03);
            display: flex;
            flex-direction: column;
            gap: 10px;
            font-size: 0.9rem;
        }

        .console-line {
            line-height: 1.4;
        }
        .line-system { color: var(--secondary); }
        .line-caller { color: var(--warning); }
        .line-warning { color: var(--danger); font-weight: bold; background: rgba(255, 23, 68, 0.1); padding: 5px 8px; border-radius: 4px; }
        .line-success { color: var(--success); }

        .btn {
            background: linear-gradient(135deg, var(--primary), var(--secondary));
            border: none;
            padding: 12px 24px;
            color: #050505;
            font-weight: 700;
            border-radius: 12px;
            cursor: pointer;
            transition: transform 0.2s, box-shadow 0.2s;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            gap: 8px;
        }

        .btn:hover {
            transform: translateY(-2px);
            box-shadow: 0 5px 15px rgba(0, 242, 254, 0.3);
        }

        .btn:active {
            transform: translateY(0);
        }

        .btn-danger {
            background: var(--danger);
            color: white;
        }
        .btn-danger:hover {
            box-shadow: 0 5px 15px rgba(255, 23, 68, 0.3);
        }

        /* Metrics */
        .metrics-grid {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 1rem;
        }

        .metric-card {
            background: rgba(255,255,255,0.02);
            border: 1px solid rgba(255,255,255,0.03);
            border-radius: 12px;
            padding: 1.2rem;
            text-align: center;
        }

        .metric-title {
            font-size: 0.8rem;
            color: var(--text-muted);
            text-transform: uppercase;
            letter-spacing: 1px;
            margin-bottom: 6px;
        }

        .metric-value {
            font-size: 1.6rem;
            font-weight: 800;
        }

        .state-normal { color: var(--success); }
        .state-suspicious { color: var(--warning); }
        .state-threat { color: var(--danger); animation: pulse-text 1.5s infinite; }

        @keyframes pulse-text {
            0% { opacity: 0.8; }
            50% { opacity: 1; text-shadow: 0 0 10px rgba(255, 23, 68, 0.4); }
            100% { opacity: 0.8; }
        }

        /* Playground styles */
        .input-group {
            display: flex;
            flex-direction: column;
            gap: 8px;
        }

        label {
            font-size: 0.85rem;
            color: var(--text-muted);
            font-weight: 600;
        }

        textarea, input, select {
            background: rgba(255, 255, 255, 0.03);
            border: 1px solid rgba(255, 255, 255, 0.08);
            border-radius: 10px;
            padding: 12px;
            color: white;
            font-family: inherit;
            font-size: 0.9rem;
            outline: none;
            transition: border-color 0.2s;
        }

        textarea:focus, input:focus, select:focus {
            border-color: var(--primary);
        }

        .result-box {
            background: rgba(255,255,255,0.02);
            border: 1px solid rgba(255,255,255,0.05);
            border-radius: 10px;
            padding: 15px;
            font-family: 'JetBrains Mono', monospace;
            font-size: 0.85rem;
            min-height: 80px;
            max-height: 200px;
            overflow-y: auto;
            white-space: pre-wrap;
        }

        .tabs {
            display: flex;
            gap: 10px;
            border-bottom: 1px solid rgba(255,255,255,0.05);
            padding-bottom: 10px;
        }

        .tab-btn {
            background: none;
            border: none;
            color: var(--text-muted);
            cursor: pointer;
            padding: 8px 16px;
            border-radius: 8px;
            font-weight: 600;
            font-size: 0.9rem;
            transition: all 0.2s;
        }

        .tab-btn.active {
            background: rgba(0, 242, 254, 0.1);
            color: var(--primary);
            border: 1px solid rgba(0, 242, 254, 0.2);
        }

        /* Overlay modal */
        #hard-lock-overlay {
            position: fixed;
            top: 0;
            left: 0;
            width: 100vw;
            height: 100vh;
            background: rgba(10, 0, 5, 0.95);
            z-index: 1000;
            display: none;
            align-items: center;
            justify-content: center;
            backdrop-filter: blur(20px);
        }

        .lock-panel {
            background: rgba(30, 5, 10, 0.8);
            border: 2px solid var(--danger);
            border-radius: 30px;
            padding: 3rem;
            max-width: 600px;
            width: 90%;
            text-align: center;
            box-shadow: 0 0 50px rgba(255, 23, 68, 0.5);
            animation: bounceIn 0.5s ease-out;
        }

        @keyframes bounceIn {
            0% { transform: scale(0.3); opacity: 0; }
            50% { transform: scale(1.05); }
            70% { transform: scale(0.9); }
            100% { transform: scale(1); opacity: 1; }
        }

        .lock-icon {
            font-size: 5rem;
            color: var(--danger);
            margin-bottom: 1.5rem;
            animation: ring 1s infinite alternate;
        }

        @keyframes ring {
            from { transform: rotate(-5deg); }
            to { transform: rotate(5deg); }
        }

        .lock-title {
            font-size: 2.2rem;
            font-weight: 800;
            color: #ff1744;
            margin-bottom: 1rem;
        }

        .lock-desc {
            font-size: 1.1rem;
            color: var(--text-main);
            margin-bottom: 2rem;
            line-height: 1.5;
        }
    </style>
</head>
<body>

    <header>
        <div class="logo-container">
            <div class="logo-badge">R</div>
            <h1 class="brand">RAKSHAK</h1>
        </div>
        <div class="status-pill">
            <div class="pulse-dot"></div>
            <span>RAKSHAK System Active</span>
        </div>
    </header>

    <main>
        <!-- Left Side: Simulator Console -->
        <div class="card">
            <h2>📞 Live Extortion Call Simulator (WebSockets)</h2>
            <p style="color: var(--text-muted); font-size: 0.9rem;">
                This simulator opens a bidirectional WebSocket session to the RAKSHAK backend. It feeds scripted audio sentences to emulate a suspicious call, runs the translation normalization, feeds the text to our Gemini extortion classifier, and triggers warning voice injections on danger alert.
            </p>
            
            <div class="metrics-grid">
                <div class="metric-card">
                    <div class="metric-title">Risk State</div>
                    <div id="sim-state" class="metric-value state-normal">NORMAL</div>
                </div>
                <div class="metric-card">
                    <div class="metric-title">Threat Score</div>
                    <div id="sim-score" class="metric-value">0.0%</div>
                </div>
            </div>

            <div class="simulator-console" id="console-output">
                <div class="console-line line-system">[System] Press "Start Call Simulation" to initialize connection.</div>
            </div>

            <div style="display: flex; gap: 1rem;">
                <button class="btn" id="btn-start-sim" onclick="startCallSimulation()">Start Call Simulation</button>
                <button class="btn btn-danger" id="btn-stop-sim" onclick="stopCallSimulation()" style="display: none;">Hang Up Call</button>
                <button class="btn" id="btn-download-evidence" onclick="downloadEvidence()" style="display: none; background: #3f51b5; color: white;">Download Evidence Complaint</button>
            </div>
        </div>

        <!-- Right Side: API Playgrounds -->
        <div class="card">
            <h2>⚡ REST Service Playground</h2>
            <div class="tabs">
                <button class="tab-btn active" onclick="switchTab('translate')">Translate</button>
                <button class="tab-btn" onclick="switchTab('tts')">TTS</button>
                <button class="tab-btn" onclick="switchTab('offline')">Gemma Offline</button>
            </div>

            <!-- Translate Tab -->
            <div id="tab-translate" class="playground-tab" style="display: flex; flex-direction: column; gap: 1rem;">
                <div class="input-group">
                    <label for="trans-input">Source Audio Transcript (Hindi / Multilingual)</label>
                    <textarea id="trans-input" rows="3">Aapka parcel Customs ne rok liya hai Delhi airport par. Isme narcotics mile hain.</textarea>
                </div>
                <button class="btn" onclick="runTranslate()">Translate & Normalize</button>
                <div class="input-group">
                    <label>Translation Result</label>
                    <div id="trans-result" class="result-box">Click Translate to process.</div>
                </div>
            </div>

            <!-- TTS Tab -->
            <div id="tab-tts" class="playground-tab" style="display: none; flex-direction: column; gap: 1rem;">
                <div class="input-group">
                    <label for="tts-input">TTS Warning Text (Warning voice message)</label>
                    <textarea id="tts-input" rows="3">सावधान! यह कॉल एक डिजिटल अरेस्ट घोटाला है। कृपया तुरंत फोन काट दें।</textarea>
                </div>
                <div class="input-group">
                    <label for="tts-voice">Voice Profile</label>
                    <select id="tts-voice">
                        <option value="Kore">Kore (Standard Male)</option>
                        <option value="Fen">Fen (Soft Female)</option>
                        <option value="Puck">Puck (Fast/Alert)</option>
                        <option value="Aoede">Aoede (Clear Vocal)</option>
                    </select>
                </div>
                <button class="btn" onclick="runTTS()">Generate Warning Voice</button>
                <div class="input-group">
                    <label>Audio Player</label>
                    <div id="tts-player-container" style="margin-top: 5px;">
                        <audio id="tts-audio-element" controls style="width: 100%; display: none;"></audio>
                        <div id="tts-result-text" class="result-box" style="font-size:0.8rem;">Click Generate to synthesize.</div>
                    </div>
                </div>
            </div>

            <!-- Offline Tab -->
            <div id="tab-offline" class="playground-tab" style="display: none; flex-direction: column; gap: 1rem;">
                <div class="input-group">
                    <label for="offline-input">Local Transcript Chunk (simulating on-device ASR)</label>
                    <textarea id="offline-input" rows="3">This is Delhi Crime Branch. Your SIM card is blocked. You must stay on Skype and verify your bank account funds to avoid digital arrest.</textarea>
                </div>
                <button class="btn" onclick="runOffline()">Classify with Local Gemma</button>
                <div class="input-group">
                    <label>Gemma Fallback Evaluation</label>
                    <div id="offline-result" class="result-box">Click Classify to process locally.</div>
                </div>
            </div>
        </div>
    </main>

    <!-- Hard Lock Overlay System -->
    <div id="hard-lock-overlay">
        <div class="lock-panel">
            <div class="lock-icon">⚠️</div>
            <div class="lock-title">SCAM ALERT DETECTED</div>
            <div class="lock-desc">
                This call was identified as a <strong>Digital Arrest Extortion Scam</strong>. 
                The system has injected a voice warning into the caller stream and locked your terminal interface for safety.
            </div>
            <button class="btn btn-danger" onclick="dismissLockOverlay()">Hang Up & Dismiss Alert</button>
        </div>
    </div>

    <script>
        let ws = null;
        let callId = null;
        const scriptedSentences = [
            { text: "Hello, can you hear me? I am officer Vijay Kumar from Delhi Cyber Cell division.", role: "caller" },
            { text: "Hanji, boliye. Kya baat hai?", role: "victim" },
            { text: "Aapka passport aur SIM card block hone wala hai. Aapke naam par ek parcel customs ne pakda hai Delhi airport par.", role: "caller" },
            { text: "Kya? Kaunsa parcel? Maine koi parcel nahi bheja!", role: "victim" },
            { text: "Is parcel me 5 fake passports aur 100 gram narcotics drugs mile hain. Yeh national security ka mamla hai.", role: "caller" },
            { text: "Aapko abhi Skype video call par aana hoga verification ke liye. Aur camera chalu rakhiye.", role: "caller" },
            { text: "Aap digital arrest me hain. Kisi se baat mat karna, kamra band rakhiye aur call disconnect mat karna.", role: "caller" },
            { text: "Arrest warrant cancel karne ke liye aapko 50,000 rupees security deposit bank account me transfer karna hoga.", role: "caller" }
        ];
        let sentenceIdx = 0;
        let simulationInterval = null;

        function appendConsole(text, type = "system") {
            const consoleBox = document.getElementById("console-output");
            const line = document.createElement("div");
            line.className = `console-line line-${type}`;
            
            const timestamp = new Date().toLocaleTimeString();
            line.innerHTML = `[${timestamp}] ${text}`;
            consoleBox.appendChild(line);
            consoleBox.scrollTop = consoleBox.scrollHeight;
        }

        function startCallSimulation() {
            sentenceIdx = 0;
            callId = "call-" + Math.floor(Math.random() * 100000);
            
            const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
            const wsUrl = `${protocol}//${window.location.host}/ws/call/${callId}`;
            
            appendConsole(`[System] Initializing connection to RAKSHAK backend...`);
            ws = new WebSocket(wsUrl);
            
            ws.onopen = () => {
                appendConsole(`[System] WebSocket connected. Session ID: ${callId}`, "success");
                document.getElementById("btn-start-sim").style.display = "none";
                document.getElementById("btn-stop-sim").style.display = "inline-flex";
                document.getElementById("btn-download-evidence").style.display = "none";
            };
            
            ws.onmessage = (event) => {
                const data = JSON.parse(event.data);
                
                if (data.event === "session_started") {
                    appendConsole(`[System] Live transcription channel open. Script simulation starting...`, "success");
                    startScriptingFeed();
                } else if (data.event === "transcript_update") {
                    appendConsole(`[Gemini Live] Incoming transcript: "${data.transcript_chunk}"`, "system");
                    updateMetrics(data.state, data.threat_score);
                } else if (data.event === "state_changed") {
                    appendConsole(`[RAKSHAK Alert] Risk state changed to: ${data.state} (Score: ${(data.threat_score * 100).toFixed(1)}%)`, "warning");
                    
                    if (data.matched_keywords && data.matched_keywords.length > 0) {
                        appendConsole(`[RAKSHAK Flags] Suspicious matches: ${data.matched_keywords.join(" | ")}`, "warning");
                    }
                    
                    updateMetrics(data.state, data.threat_score);
                    
                    if (data.state === "THREAT_DETECTED") {
                        appendConsole(`[RAKSHAK Interruption] INJECTING EXPORT VOICE INTERVENTION!`, "warning");
                        triggerInterventionLock(data.warning_audio);
                    }
                }
            };
            
            ws.onclose = () => {
                appendConsole(`[System] Call simulation session closed.`);
                clearInterval(simulationInterval);
                document.getElementById("btn-start-sim").style.display = "inline-flex";
                document.getElementById("btn-stop-sim").style.display = "none";
            };
            
            ws.onerror = (err) => {
                appendConsole(`[Error] WebSocket encountered error: ${err}`, "warning");
            };
        }

        function startScriptingFeed() {
            simulationInterval = setInterval(() => {
                if (sentenceIdx >= scriptedSentences.length) {
                    clearInterval(simulationInterval);
                    appendConsole(`[System] Call ended. Please hang up or inspect evidence report.`);
                    return;
                }
                
                const step = scriptedSentences[sentenceIdx];
                appendConsole(`[${step.role === "caller" ? "Extortionist" : "Victim"}] "${step.text}"`, step.role === "caller" ? "caller" : "success");
                
                // Forward the mock speech transcript to the WebSocket
                // This simulates user/caller voice audio transcribing in real-time
                if (ws && ws.readyState === WebSocket.OPEN) {
                    ws.send(step.text);
                }
                
                sentenceIdx++;
            }, 3000);
        }

        function stopCallSimulation() {
            if (ws) {
                ws.close();
            }
            clearInterval(simulationInterval);
            document.getElementById("btn-download-evidence").style.display = "inline-flex";
        }

        function updateMetrics(state, score) {
            const stateEl = document.getElementById("sim-state");
            const scoreEl = document.getElementById("sim-score");
            
            stateEl.innerText = state;
            scoreEl.innerText = `${(score * 100).toFixed(1)}%`;
            
            stateEl.className = "metric-value";
            if (state === "NORMAL") stateEl.classList.add("state-normal");
            else if (state === "SUSPICIOUS") stateEl.classList.add("state-suspicious");
            else if (state === "THREAT_DETECTED") stateEl.classList.add("state-threat");
        }

        function triggerInterventionLock(base64Audio) {
            // Play the injected voice warning
            if (base64Audio) {
                const snd = new Audio("data:audio/wav;base64," + base64Audio);
                snd.play();
            }
            
            // Show lock overlay
            document.getElementById("hard-lock-overlay").style.display = "flex";
        }

        function dismissLockOverlay() {
            document.getElementById("hard-lock-overlay").style.display = "none";
            stopCallSimulation();
        }

        async function downloadEvidence() {
            if (!callId) return;
            try {
                const response = await fetch(`/call/${callId}/evidence`);
                const report = await response.json();
                
                const dataStr = "data:text/json;charset=utf-8," + encodeURIComponent(JSON.stringify(report, null, 2));
                const downloadAnchor = document.createElement('a');
                downloadAnchor.setAttribute("href",     dataStr);
                downloadAnchor.setAttribute("download", `evidence-report-${callId}.json`);
                document.body.appendChild(downloadAnchor);
                downloadAnchor.click();
                downloadAnchor.remove();
                
                appendConsole(`[System] Cybercrime evidence report downloaded. Ready for cybercrime.gov.in submission!`, "success");
            } catch (err) {
                alert("Failed to download evidence: " + err);
            }
        }

        /* REST Playground Code */
        function switchTab(tabId) {
            document.querySelectorAll(".tab-btn").forEach(btn => btn.classList.remove("active"));
            document.querySelectorAll(".playground-tab").forEach(tab => tab.style.display = "none");
            
            event.target.classList.add("active");
            document.getElementById(`tab-${tabId}`).style.display = "flex";
        }

        async function runTranslate() {
            const text = document.getElementById("trans-input").value;
            const resBox = document.getElementById("trans-result");
            resBox.innerText = "Translating...";
            
            try {
                const response = await fetch("/translate", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ text: text })
                });
                const data = await response.json();
                resBox.innerText = `Detected Lang: ${data.detected_lang}\\nTranslated Text: "${data.translated_text}"`;
            } catch (e) {
                resBox.innerText = "Error: " + e;
            }
        }

        async function runTTS() {
            const text = document.getElementById("tts-input").value;
            const voice = document.getElementById("tts-voice").value;
            const resText = document.getElementById("tts-result-text");
            const audioEl = document.getElementById("tts-audio-element");
            
            resText.innerText = "Synthesizing voice warning...";
            audioEl.style.display = "none";
            
            try {
                const response = await fetch("/tts/warning", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ text: text, voice_name: voice })
                });
                const data = await response.json();
                
                resText.innerText = "Synthesis completed. Playing audio.";
                audioEl.src = "data:audio/wav;base64," + data.audio_base64;
                audioEl.style.display = "block";
                audioEl.play();
            } catch (e) {
                resText.innerText = "Error: " + e;
            }
        }

        async function runOffline() {
            const text = document.getElementById("offline-input").value;
            const resBox = document.getElementById("offline-result");
            resBox.innerText = "Running local classification...";
            
            try {
                const response = await fetch("/offline/classify", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ transcript_chunk: text })
                });
                const data = await response.json();
                resBox.innerText = JSON.stringify(data, null, 2);
            } catch (e) {
                resBox.innerText = "Error: " + e;
            }
        }
    </script>
</body>
</html>
"""

@app.get("/", response_class=HTMLResponse)
async def dashboard():
    return DASHBOARD_HTML
