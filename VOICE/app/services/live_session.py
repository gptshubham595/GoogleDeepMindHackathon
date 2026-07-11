import logging
from google import genai
from google.genai import types
from app.config import settings

logger = logging.getLogger(__name__)

class GeminiLiveService:
    def __init__(self):
        self.client = None
        self.initialize_client()

    def initialize_client(self):
        try:
            if settings.gemini_api_key:
                self.client = genai.Client(api_key=settings.gemini_api_key)
                logger.info("Gemini Live Service client initialized successfully.")
            else:
                logger.error("GEMINI_API_KEY is missing in settings.")
        except Exception as e:
            logger.error(f"Failed to initialize Gemini Client in GeminiLiveService: {e}")

    def connect(self, system_instruction: str = None):
        """
        Establishes the async Live Connection context manager.
        """
        if not self.client:
            self.initialize_client()
            if not self.client:
                raise ValueError("Gemini Client is not initialized. Please verify GEMINI_API_KEY.")

        config = types.LiveConnectConfig(
            response_modalities=["AUDIO"],  # Models like gemini-3.1-flash-live-preview require AUDIO modality
            system_instruction=types.Content(
                parts=[types.Part(text=system_instruction)]
            ) if system_instruction else None
        )
        
        logger.info(f"Connecting to Gemini Live API using model: {settings.model_live}")
        return self.client.aio.live.connect(model=settings.model_live, config=config)

live_service = GeminiLiveService()
