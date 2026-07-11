import logging
from google import genai
from google.genai import types
from app.config import settings

logger = logging.getLogger(__name__)

class TranslationService:
    def __init__(self):
        try:
            self.client = genai.Client(api_key=settings.gemini_api_key)
        except Exception as e:
            logger.error(f"Failed to initialize Gemini Client in TranslationService: {e}")
            self.client = None

    async def translate_text(self, text: str, target_lang: str = "English") -> tuple[str, str]:
        """
        Translates text to target language (usually English).
        Returns: (translated_text, detected_language)
        """
        if not text.strip():
            return "", "unknown"
            
        if not self.client:
            logger.warning("Gemini Client not initialized, returning original text")
            return text, "unknown"

        # Note: gemini-3.5-live-translate-preview only works over the Live API WebSocket,
        # not via REST generateContent. Use gemini-3.5-flash for REST-based translation.
        models_to_try = [settings.model_reasoning]
        last_error = None

        for model_name in models_to_try:
            try:
                prompt = f"""
                Translate the following text to {target_lang}. If it is already in {target_lang}, leave it as is.
                Detect the source language. 

                Respond strictly in the following JSON format:
                {{
                  "translated_text": "the translated text here",
                  "detected_lang": "the name of the source language (e.g. Hindi, Telugu, English)"
                }}

                Text to translate:
                "{text}"
                """
                response = self.client.models.generate_content(
                    model=model_name,
                    contents=prompt,
                    config=types.GenerateContentConfig(
                        response_mime_type="application/json",
                        temperature=0.1
                    )
                )
                import json
                result = json.loads(response.text)
                return result.get("translated_text", text), result.get("detected_lang", "unknown")
            except Exception as e:
                logger.warning(f"Failed to translate using model {model_name}: {e}")
                last_error = e

        logger.error(f"All translation models failed. Last error: {last_error}")
        return text, "unknown"

translation_service = TranslationService()
