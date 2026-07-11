from fastapi import APIRouter, HTTPException
from app.models.schemas import TTSRequest, TTSResponse
from app.services.tts import tts_service

router = APIRouter(prefix="/tts", tags=["text-to-speech"])

@router.post("/warning", response_model=TTSResponse)
async def generate_warning(payload: TTSRequest):
    try:
        # Use language and voice_style parameters if provided
        base64_audio = await tts_service.generate_warning_speech_base64(
            payload.text, 
            payload.voice_name or "Kore"
        )
        if not base64_audio:
            # Return the text as fallback if audio generation fails
            # This allows the system to still function even without TTS
            return TTSResponse(
                audio_base64="",  # Empty audio
                text=payload.text,
                language=payload.language or "hi-IN",
                voice_style=payload.voice_style or "urgent"
            )
        return TTSResponse(
            audio_base64=base64_audio,
            text=payload.text,
            language=payload.language or "hi-IN",
            voice_style=payload.voice_style or "urgent"
        )
    except HTTPException:
        raise
    except Exception as e:
        # Return text fallback on error
        return TTSResponse(
            audio_base64="",  # Empty audio on error
            text=payload.text,
            language=payload.language or "hi-IN",
            voice_style=payload.voice_style or "urgent"
        )
