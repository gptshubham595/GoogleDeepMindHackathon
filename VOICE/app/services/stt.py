import logging
import base64
import asyncio
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
            response_modalities=["TEXT"],  # We only need text transcription
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
    
    async def transcribe_audio_base64(self, audio_base64: str, language: str = "hi-IN", mime_type: str = "audio/pcm;rate=16000") -> str:
        """
        Transcribes a base64 encoded audio file using Gemini Live API.
        """
        if not self.client:
            logger.error("STT client not initialized")
            return ""
        
        try:
            audio_bytes = base64.b64decode(audio_base64)
            
            system_instruction = (
                f"You are a speech-to-text transcription agent. Listen to the audio and "
                f"transcribe exactly what you hear in {language}. Do not respond to the content, "
                f"only transcribe the speech in the language it is spoken."
            )
            
            transcription = ""
            
            async with self.connect(language=language, system_instruction=system_instruction) as session:
                # Send audio data
                await session.send_realtime_input(
                    audio=types.Blob(
                        data=audio_bytes,
                        mime_type=mime_type
                    )
                )
                
                # Receive transcription
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
                    
                    # Break after getting some transcription
                    if transcription.strip():
                        await asyncio.sleep(0.5)
                        break
            
            return transcription.strip()
        except Exception as e:
            logger.error(f"Error transcribing base64 audio: {e}")
            return ""

stt_service = STTService()
