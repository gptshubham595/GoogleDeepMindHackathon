from pydantic_settings import BaseSettings, SettingsConfigDict
from pydantic import Field

class Settings(BaseSettings):
    gemini_api_key: str = Field(..., validation_alias="GEMINI_API_KEY")
    host: str = Field("0.0.0.0", validation_alias="HOST")
    port: int = Field(8000, validation_alias="PORT")
    
    # Core Gemini Models for the Hackathon Problem Statement
    model_live: str = Field("gemini-3.1-flash-live-preview", validation_alias="MODEL_LIVE")
    model_translate: str = Field("gemini-3.5-flash", validation_alias="MODEL_TRANSLATE")  # Fixed: live-translate-preview not available via REST
    model_tts: str = Field("gemini-3.5-flash", validation_alias="MODEL_TTS")  # Fixed: TTS model might not be available, using flash
    model_reasoning: str = Field("gemini-3.5-flash", validation_alias="MODEL_REASONING")
    
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore"
    )

settings = Settings()
