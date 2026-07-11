from pydantic import BaseModel, Field
from typing import List, Dict, Optional
from datetime import datetime

class TranslateRequest(BaseModel):
    text: str = Field(..., description="The text chunk or transcript to translate")
    target_lang: str = Field("English", description="Target language to translate into, default English for classifier normalization")

class TranslateResponse(BaseModel):
    translated_text: str
    detected_lang: Optional[str] = "unknown"

class TTSRequest(BaseModel):
    text: str = Field(..., description="Text warning message to synthesize to speech")
    voice_name: Optional[str] = Field("Kore", description="Voice profile for Gemini TTS (e.g. Kore, Fen, Puck, Charon, Aoede)")
    language: Optional[str] = Field("hi-IN", description="Target language code (e.g. hi-IN, en-IN, ta-IN)")
    voice_style: Optional[str] = Field("urgent", description="Voice style: urgent, clear, calm, emphatic")

class TTSResponse(BaseModel):
    audio_base64: str = Field(..., description="Base64 encoded audio bytes (PCM/WAV) containing warning")
    text: str = Field(..., description="Text that was synthesized")
    language: str = Field("hi-IN", description="Language code used for synthesis")
    voice_style: str = Field("urgent", description="Voice style used: urgent, clear, calm, emphatic")

class STTRequest(BaseModel):
    audio_base64: str = Field(..., description="Base64 encoded audio bytes (PCM/WAV) to transcribe")
    language: str = Field("hi-IN", description="Source language code (e.g. hi-IN, en-IN, ta-IN, te-IN)")
    mime_type: str = Field("audio/pcm;rate=16000", description="Audio MIME type and sample rate")
    translate_to_english: bool = Field(True, description="Whether to also translate the transcription to English")

class STTResponse(BaseModel):
    transcription: str = Field(..., description="Transcribed text from audio")
    language: str = Field(..., description="Source language code used")
    translated_text: Optional[str] = Field("", description="English translation of transcription if requested")
    detected_lang: Optional[str] = Field("", description="Detected source language")

class STTStreamResponse(BaseModel):
    event: str
    text: Optional[str] = ""
    language: str = "hi-IN"

class OfflineClassifyRequest(BaseModel):
    transcript_chunk: str = Field(..., description="Cumulative or single transcript snippet from on-device local ASR")

class OfflineClassifyResponse(BaseModel):
    state: str = Field(..., description="NORMAL, SUSPICIOUS, or THREAT_DETECTED")
    confidence: float = Field(..., description="Score between 0.0 and 1.0")
    matched_keywords: List[str] = Field(..., description="Scam indicators matched locally")
    action_required: bool = Field(..., description="Whether to trigger audio injection/overlay immediately")

class SuspiciousSegment(BaseModel):
    text: str
    reason: str
    timestamp: datetime

class EvidenceReport(BaseModel):
    call_id: str
    final_state: str
    threat_score: float
    full_transcript: str
    generated_at: datetime
    suspicious_segments: List[SuspiciousSegment]
    cybercrime_report: Dict = Field(..., description="Formatted object ready for cybercrime.gov.in copy-paste or auto-filling")
