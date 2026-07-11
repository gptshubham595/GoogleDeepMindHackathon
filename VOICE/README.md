# RAKSHAK Voice Backend - Digital Arrest Extortion Detection

## Overview
This backend provides AI-powered voice features for detecting digital arrest extortion scams in real-time using Google's Gemini AI models. It's designed as a plug-and-play service for Android integration.

## Features
- **Real-time Call Monitoring**: WebSocket endpoint for live call transcription and threat detection
- **Multi-language Translation**: Support for Indian languages (Hindi, Telugu, Tamil, Bengali, Marathi, Gujarati, Kannada, Malayalam, Punjabi)
- **Text-to-Speech Warning**: Generate voice alerts in multiple languages
- **Offline Fallback**: Local keyword-based classification when connectivity is compromised
- **Evidence Generation**: Cybercrime.gov.in ready complaint generation

## Hackathon Models Used
- `gemini-3.1-flash-live-preview` - Real-time audio processing
- `gemini-3.5-live-translate-preview` - Multi-language translation
- `gemini-3.1-flash-tts-preview` - Text-to-speech synthesis
- `gemini-3.5-flash` - Reasoning and classification

## Setup

### 1. Set API Key
Replace `your_gemini_api_key_here` in `.env` with your actual Gemini API key:
```bash
GEMINI_API_KEY=your_actual_api_key_here
```

### 2. Activate Virtual Environment
```bash
.venv\Scripts\activate
```

### 3. Install Dependencies (if needed)
```bash
uv sync
```

## Running the Server

### Start the server:
```bash
.venv\Scripts\activate
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

Or using uv directly:
```bash
.venv\Scripts\activate
uv run uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

## API Endpoints for Android Integration

### WebSocket Endpoint
**Endpoint:** `ws://your-server:8000/ws/call/{call_id}`

**Usage:** Connect for real-time call monitoring and threat detection

**Message Format:**
- Send: Text transcript chunks or audio bytes
- Receive: JSON events with threat analysis and warnings

### REST Endpoints

#### 1. Translation
**POST** `/translate`
```json
{
  "text": "Aapka parcel Customs ne rok liya hai",
  "target_lang": "English"
}
```

#### 2. Text-to-Speech
**POST** `/tts/warning`
```json
{
  "text": "सावधान! यह कॉल एक डिजिटल अरेस्ट घोटाला है।",
  "voice_name": "Kore"
}
```

#### 3. Offline Classification
**POST** `/offline/classify`
```json
{
  "transcript_chunk": "This is Delhi Crime Branch. Your SIM card is blocked."
}
```

#### 4. Evidence Export
**GET** `/call/{call_id}/evidence`

Returns cybercrime.gov.in ready complaint report

## Dashboard
Access the interactive dashboard at: `http://localhost:8000`

## Architecture
- **FastAPI**: Web framework
- **Gemini Live API**: Real-time audio processing
- **Gemini Translation**: Multi-language support
- **Gemini TTS**: Voice synthesis
- **Pattern Matching**: Fast keyword detection
- **LLM Validation**: Semantic analysis

## Security Notes
- Never commit `.env` file with real API keys
- Use HTTPS in production
- Implement proper authentication for mobile apps
