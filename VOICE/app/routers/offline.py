from fastapi import APIRouter
from app.models.schemas import OfflineClassifyRequest, OfflineClassifyResponse
from app.services.gemma_offline import gemma_offline

router = APIRouter(prefix="/offline", tags=["offline-fallback"])

@router.post("/classify", response_model=OfflineClassifyResponse)
async def classify_offline(payload: OfflineClassifyRequest):
    result = gemma_offline.classify(payload.transcript_chunk)
    return OfflineClassifyResponse(
        state=result["state"],
        confidence=result["confidence"],
        matched_keywords=result["matched_keywords"],
        action_required=result["action_required"]
    )
