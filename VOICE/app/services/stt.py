import logging
import base64
import asyncio
import io
from typing import AsyncGenerator, Optional
from google import genai
from google.genai import types
from app.config import settings

logger = logging.getLogger(__name__)

class STTService:
    """
    Speech-to-Text Service using Gemini Live API for real-time audio transcription.
    Supports multi-language speech recognition for Indian languages.
    """
    
    def __init__(self):
        self.client = None
        self.initialize_client()
    
    def initialize_client(self):
        try:
            if settings.gemini_api_key:
                self.client = genai.Client(api_key=settings.gemini_api_key)
                logger.info("STT Service client initialized successfully.")
            else:
                logger.error("GEMINI_API_KEY is missing in settings for STT service.")
        except Exception as e:
            logger.error(f"Failed to initialize Gemini Client in STTService: {e}")
    
    @staticmethod
    def create_blob(data: bytes, mime_type: str = "audio/pcm;rate=16000"):
        """
        Helper to create a Blob from audio bytes.
        """
        return types.Blob(data=data, mime_type=mime_type)
    
    def connect(self, language: str = "hi-IN", system_instruction: str = None):
        """
        Establishes async Live Connection for STT.
        """
        if not self.client:
            self.initialize_client()
            if not self.client:
                raise ValueError("Gemini Client is not initialized. Please verify GEMINI_API_KEY.")
        
        instruction = system_instruction or (
            f"You are a real-time speech-to-text transcription agent. Listen to the audio and "
            f"transcribe exactly what you hear in {language}. Do not respond to the content, "
            f"only transcribe the speech in the language it is spoken."
        )
        
        config = types.LiveConnectConfig(
            response_modalities=["AUDIO"],  # Use AUDIO modality as supported by live model
            system_instruction=types.Content(
                parts=[types.Part(text=instruction)]
            )
        )
        
        logger.info(f"Connecting to Gemini Live API for STT using model: {settings.model_live}")
        return self.client.aio.live.connect(model=settings.model_live, config=config)
    
    async def transcribe_audio_stream(self, audio_stream: AsyncGenerator[bytes, None], language: str = "hi-IN") -> str:
        """
        Transcribes a stream of audio chunks using Gemini Live API.
        """
        if not self.client:
            logger.error("STT client not initialized")
            return ""
        
        system_instruction = (
            f"You are a real-time speech-to-text transcription agent. Listen to the audio and "
            f"transcribe exactly what you hear in {language}. Do not respond to the content, "
            f"only transcribe the speech in the language it is spoken."
        )
        
        transcription = ""
        
        try:
            async with self.connect(language=language, system_instruction=system_instruction) as session:
                # Start consumer task to receive transcriptions
                receive_task = asyncio.create_task(self._receive_transcriptions(session, transcription))
                
                # Feed audio chunks
                async for audio_chunk in audio_stream:
                    await session.send_realtime_input(
                        audio=types.Blob(
                            data=audio_chunk,
                            mime_type="audio/pcm;rate=16000"
                        )
                    )
                
                # Wait for final transcriptions
                receive_task.cancel()
                try:
                    await receive_task
                except asyncio.CancelledError:
                    pass
                
                return transcription
        except Exception as e:
            logger.error(f"Error in STT transcription: {e}")
            return ""
    
    async def _receive_transcriptions(self, session, transcription: str):
        """
        Helper to receive transcription chunks from Gemini Live API.
        """
        try:
            async for response in session.receive():
                if response.server_content and response.server_content.model_turn:
                    for part in response.server_content.model_turn.parts:
                        if part.text:
                            transcription += part.text + " "
                            logger.info(f"STT Transcription: {part.text}")
                elif response.server_content and hasattr(response.server_content, 'input_transcription') and response.server_content.input_transcription:
                    if response.server_content.input_transcription.text:
                        transcription += response.server_content.input_transcription.text + " "
                        logger.info(f"STT Input Transcription: {response.server_content.input_transcription.text}")
        except asyncio.CancelledError:
            pass
        except Exception as e:
            logger.error(f"Error receiving STT transcription: {e}")
    
    def extract_pcm_from_wav(self, audio_bytes: bytes, target_rate: int = 16000) -> tuple[bytes, int]:
        """
        Extract raw PCM bytes from a WAV file.
        Returns (pcm_bytes, sample_rate).
        """
        try:
            import wave
            wav = wave.open(io.BytesIO(audio_bytes), 'rb')
            n_channels = wav.getnchannels()
            sample_width = wav.getsampwidth()
            sample_rate = wav.getframerate()
            n_frames = wav.getnframes()
            
            raw_data = wav.readframes(n_frames)
            wav.close()
            
            # Convert stereo to mono if needed
            if n_channels == 2:
                # Simple stereo to mono conversion for 16-bit samples
                if sample_width == 2:
                    import array
                    samples = array.array('h', raw_data)
                    mono = array.array('h')
                    for i in range(0, len(samples), 2):
                        left = samples[i]
                        right = samples[i+1]
                        mono.append((left + right) // 2)
                    raw_data = mono.tobytes()
                # For other sample widths, just use left channel
                else:
                    raw_data = raw_data[::n_channels * sample_width]
            
            # Simple resampling is complex; if sample rate is not target, we return as-is
            # and let the caller know. For proper resampling, use librosa/pydub.
            if sample_rate != target_rate:
                logger.warning(f"Audio sample rate is {sample_rate}Hz, target is {target_rate}Hz. Consider resampling for best results.")
            
            return raw_data, sample_rate
        except Exception as e:
            logger.warning(f"Could not parse as WAV: {e}")
            return audio_bytes, 16000
    
    async def transcribe_audio_base64(self, audio_base64: str, language: str = "hi-IN", mime_type: str = "audio/pcm;rate=16000") -> str:
        """
        Transcribes a base64 encoded audio file using Gemini API.
        Supports WAV, MP3, OGG, FLAC, M4A, and raw PCM audio.
        """
        if not self.client:
            logger.error("STT client not initialized")
            return ""
        
        try:
            audio_bytes = base64.b64decode(audio_base64)
            logger.info(f"Audio size: {len(audio_bytes)} bytes, MIME type: {mime_type}")
            
            # For WAV files, keep as WAV for Gemini API
            if audio_bytes.startswith(b'RIFF'):
                logger.info("Detected WAV file")
                mime_type = "audio/wav"
            
            # Create prompt for transcription
            prompt = f"""Transcribe the audio content exactly as spoken. 
Provide ONLY the transcription text, nothing else.
If the audio is in {language}, transcribe in that language.
Do not add any explanations, timestamps, or additional text."""
            
            # Use Gemini API with audio for transcription
            logger.info(f"Sending audio to Gemini API for transcription")
            
            # Run in executor since Gemini SDK might be blocking
            loop = asyncio.get_event_loop()
            
            def transcribe_sync():
                try:
                    response = self.client.models.generate_content(
                        model=settings.model_reasoning,  # Use reasoning model for better accuracy
                        contents=[
                            prompt,
                            types.Part(
                                inline_data=types.Blob(
                                    mime_type=mime_type,
                                    data=audio_bytes
                                )
                            )
                        ]
                    )
                    
                    if response and response.text:
                        text = response.text.strip()
                        logger.info(f"Transcription result: {text[:200]}")
                        return text
                    else:
                        logger.warning("Empty response from Gemini API")
                        return ""
                        
                except Exception as e:
                    logger.error(f"Gemini API transcription error: {e}", exc_info=True)
                    return ""
            
            result = await loop.run_in_executor(None, transcribe_sync)
            logger.info(f"Final transcription: {result[:100] if result else '(empty)'}")
            return result
            
        except Exception as e:
            logger.error(f"Error transcribing base64 audio: {e}", exc_info=True)
            return ""

stt_service = STTService()
