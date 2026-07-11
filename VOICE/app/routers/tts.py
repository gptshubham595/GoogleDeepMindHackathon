from fastapi import APIRouter, HTTPException
from app.models.schemas import TTSRequest, TTSResponse
from app.services.tts import tts_service

router = APIRouter(prefix="/tts", tags=["text-to-speech"])

@router.post("/warning", response_model=TTSResponse)
async def generate_warning(payload: TTSRequest):
    try:
        base64_audio = await tts_service.generate_warning_speech_base64(
            payload.text, payload.voice_name
        )
        if not base64_audio:
            raise HTTPException(status_code=500, detail="Failed to synthesize speech")
        return TTSResponse(
            audio_base64=base64_audio,
            text=payload.text
        )
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"TTS generation failed: {str(e)}")
