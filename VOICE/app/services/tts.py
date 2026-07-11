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
        """
        if not self.client:
            logger.error("Gemini Client not initialized for TTS")
            return None

        # Choose voice configuration
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

        # Fallback list for model safety
        models_to_try = [settings.model_tts, settings.model_reasoning]
        last_error = None

        for model in models_to_try:
            try:
                # Instruct the model to speak the message exactly
                prompt = f"Speak the following statement exactly. Do not add any greeting or other content: \"{text}\""
                
                # Run the model call in an executor since the SDK might block
                response = self.client.models.generate_content(
                    model=model,
                    contents=prompt,
                    config=config
                )

                # Locate inline data in the parts
                for part in response.candidates[0].content.parts:
                    if part.inline_data:
                        # inline_data.data contains raw bytes
                        return part.inline_data.data
                        
            except Exception as e:
                logger.warning(f"TTS failed on model {model}: {e}")
                last_error = e

        logger.error(f"All TTS models failed. Last error: {last_error}")
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
