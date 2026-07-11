# RAKSHAK Voice Backend - Digital Arrest Extortion Detection

## Overview
This backend provides AI-powered voice features for detecting digital arrest extortion scams in real-time using Google's Gemini AI models. It's designed as a plug-and-play service for Android integration.

## Features
- **Real-time Call Monitoring**: WebSocket endpoint for live call transcription and threat detection
- **Speech-to-Text (STT)**: Multi-language audio transcription supporting WAV, MP3, OGG, FLAC, M4A, and PCM formats
- **Multi-language Translation**: Support for Indian languages (Hindi, Telugu, Tamil, Bengali, Marathi, Gujarati, Kannada, Malayalam, Punjabi)
- **Text-to-Speech Warning**: Generate voice alerts in multiple languages with voice style control (urgent, calm, clear, emphatic)
- **Offline Fallback**: Local keyword-based classification when connectivity is compromised
- **Evidence Generation**: Cybercrime.gov.in ready complaint generation
- **Interactive Dashboard**: Web-based testing interface for all endpoints

## Hackathon Models Used
- `gemini-3.1-flash-live-preview` - Real-time audio processing for live calls
- `gemini-3.5-flash` - Translation, reasoning, classification, and STT transcription
- `gemini-3.1-flash-tts-preview` - Text-to-speech synthesis (with fallback to gemini-3.5-flash)

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

### WebSocket Endpoints

#### 1. Live Call Monitoring
**Endpoint:** `ws://your-server:8000/ws/call/{call_id}`

**Usage:** Connect for real-time call monitoring and threat detection

**Message Format:**
- Send: Text transcript chunks or audio bytes
- Receive: JSON events with threat analysis and warnings

#### 2. STT Streaming
**Endpoint:** `ws://your-server:8000/stt/stream/{language}`

**Usage:** Real-time speech-to-text streaming

**Supported Languages:** `hi-IN`, `en-IN`, `ta-IN`, `te-IN`, `bn-IN`, `mr-IN`, `gu-IN`, `kn-IN`, `ml-IN`, `pa-IN`

### REST Endpoints

#### 1. Translation
**POST** `/translate`
```json
{
  "text": "Aapka parcel Customs ne rok liya hai",
  "target_lang": "English"
}
```

**Response:**
```json
{
  "translated_text": "Your parcel has been held by Customs",
  "detected_lang": "hi"
}
```

#### 2. Text-to-Speech
**POST** `/tts/warning`
```json
{
  "text": "सावधान! यह कॉल एक डिजिटल अरेस्ट घोटाला है।",
  "voice_name": "Kore",
  "language": "hi-IN",
  "voice_style": "urgent"
}
```

**Supported Voice Styles:** `urgent`, `calm`, `clear`, `emphatic`

**Response:**
```json
{
  "audio_base64": "base64_encoded_audio",
  "text": "सावधान! यह कॉल एक डिजिटल अरेस्ट घोटाला है।",
  "language": "hi-IN",
  "voice_style": "urgent"
}
```

#### 3. Speech-to-Text (Base64 Audio)
**POST** `/stt/transcribe`
```json
{
  "audio_base64": "base64_encoded_audio",
  "language": "hi-IN",
  "mime_type": "audio/wav"
}
```

**Response:**
```json
{
  "transcription": "Transcribed text here",
  "language": "hi-IN",
  "translated_text": "English translation",
  "detected_lang": "hi"
}
```

#### 4. Speech-to-Text (Raw Audio Bytes)
**POST** `/stt/transcribe-bytes?language=hi-IN`

**Content-Type:** `application/octet-stream`

**Body:** Raw audio bytes (WAV, MP3, OGG, FLAC, M4A, PCM)

**Response:** Transcribed text as UTF-8 byte array

**Headers:**
- `X-Detected-Language`: Detected language code
- `X-Audio-Format`: Auto-detected audio format

#### 5. Offline Classification
**POST** `/offline/classify`
```json
{
  "transcript_chunk": "This is Delhi Crime Branch. Your SIM card is blocked."
}
```

**Response:**
```json
{
  "threat_state": "HIGH",
  "confidence": 0.95
}
```

#### 6. Evidence Export
**GET** `/call/{call_id}/evidence`

Returns cybercrime.gov.in ready complaint report

## Dashboard
Access the interactive dashboard at: `http://localhost:8000`

**Features:**
- **Translation Tab**: Test multi-language translation
- **TTS Tab**: Generate voice warnings with language and voice style selection
- **STT Tab**: Test speech-to-text with browser microphone or file upload (supports WAV, MP3, OGG, FLAC, M4A)
- **Offline Tab**: Test local threat classification
- **Live Call Tab**: Simulate a scam call for testing threat detection

## Architecture
- **FastAPI**: Web framework with WebSocket support
- **Gemini Live API** (`gemini-3.1-flash-live-preview`): Real-time audio processing for live calls
- **Gemini API** (`gemini-3.5-flash`): Translation, STT transcription, reasoning, classification, and TTS fallback
- **Gemini TTS** (`gemini-3.1-flash-tts-preview`): Text-to-speech synthesis with voice profiles
- **Pattern Matching**: Fast keyword detection for offline fallback
- **LLM Validation**: Semantic analysis using Gemini for accurate threat detection
- **Audio Processing**: WAV/PCM extraction and format detection for STT

## Security Notes
- Never commit `.env` file with real API keys
- Use HTTPS in production
- Implement proper authentication for mobile apps
