from fastapi import APIRouter, HTTPException
from app.models.schemas import TranslateRequest, TranslateResponse
from app.services.translate import translation_service

router = APIRouter(prefix="/translate", tags=["translation"])

@router.post("", response_model=TranslateResponse)
async def translate(payload: TranslateRequest):
    try:
        translated_text, detected_lang = await translation_service.translate_text(
            payload.text, payload.target_lang
        )
        return TranslateResponse(
            translated_text=translated_text,
            detected_lang=detected_lang
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Translation failed: {str(e)}")
