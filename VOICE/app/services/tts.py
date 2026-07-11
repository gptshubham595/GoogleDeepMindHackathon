import logging
import base64
from google import genai
from google.genai import types
from app.config import settings

logger = logging.getLogger(__name__)

class TTSService:
    def __init__(self):
        try:
            self.client = genai.Client(api_key=settings.gemini_api_key)
        except Exception as e:
            logger.error(f"Failed to initialize Gemini Client in TTSService: {e}")
            self.client = None

    async def generate_warning_speech(self, text: str, voice_name: str = "Kore") -> bytes | None:
        """
        Generates TTS audio bytes for a warning message.
        Supports multi-language TTS for Indian languages.
        """
        if not self.client:
            logger.error("Gemini Client not initialized for TTS")
            return None

        last_error = None

        # Try different approaches for TTS
        for approach in ["audio_response", "text_with_audio", "simple_text"]:
            try:
                if approach == "audio_response":
                    # Try with audio response modality
                    speech_config = types.SpeechConfig(
                        voice_config=types.VoiceConfig(
                            prebuilt_voice_config=types.PrebuiltVoiceConfig(
                                voice_name=voice_name
                            )
                        )
                    )

                    config = types.GenerateContentConfig(
                        response_modalities=["AUDIO"],
                        speech_config=speech_config,
                        temperature=0.1
                    )

                    prompt = f"""Speak the following warning message exactly: "{text}" """

                    response = self.client.models.generate_content(
                        model=settings.model_reasoning,
                        contents=prompt,
                        config=config
                    )

                    logger.info(f"TTS audio_response - got response with {len(response.candidates) if response.candidates else 0} candidates")
                    
                    # Try to get audio from response
                    if response.candidates and response.candidates[0].content:
                        for part in response.candidates[0].content.parts:
                            logger.info(f"Part has inline_data: {hasattr(part, 'inline_data')}")
                            if hasattr(part, 'inline_data') and part.inline_data and part.inline_data.data:
                                logger.info(f"TTS generated successfully for text: {text[:50]}...")
                                return part.inline_data.data

                elif approach == "text_with_audio":
                    # Fallback: Try without audio modality, see if we get any response
                    prompt = f"""You are a voice assistant. Generate a spoken version of this warning message: "{text}"
                    
                    If you can generate audio, include it as inline data. Otherwise, just respond with the text exactly as written."""
                    
                    response = self.client.models.generate_content(
                        model=settings.model_reasoning,
                        contents=prompt,
                        config=types.GenerateContentConfig(temperature=0.1)
                    )

                    logger.info(f"TTS text_with_audio - got response")
                    
                    # Check for audio in response
                    if response.candidates and response.candidates[0].content:
                        for part in response.candidates[0].content.parts:
                            if hasattr(part, 'inline_data') and part.inline_data and part.inline_data.data:
                                logger.info(f"TTS generated successfully for text: {text[:50]}...")
                                return part.inline_data.data
                            
                            # Also check if there's text response
                            if hasattr(part, 'text') and part.text:
                                logger.info(f"Got text response instead of audio: {part.text[:100]}")

                else:
                    # Simple text approach - just return the text as fallback
                    logger.warning("Audio generation not available, returning text fallback")
                    # Return None to indicate audio generation failed
                    return None

            except Exception as e:
                logger.warning(f"TTS approach {approach} failed: {e}")
                last_error = e

        logger.error(f"All TTS approaches failed. Last error: {last_error}")
        return None

    async def generate_warning_speech_base64(self, text: str, voice_name: str = "Kore") -> str:
        """
        Generates TTS audio and returns base64 encoded string.
        """
        audio_bytes = await self.generate_warning_speech(text, voice_name)
        if audio_bytes:
            return base64.b64encode(audio_bytes).decode("utf-8")
        return ""

tts_service = TTSService()
