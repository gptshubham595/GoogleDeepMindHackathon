from fastapi import APIRouter, HTTPException, WebSocket, WebSocketDisconnect
from app.models.schemas import STTRequest, STTResponse, STTStreamResponse
from app.services.stt import stt_service
from app.services.translate import translation_service
import asyncio
import json
import logging

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/stt", tags=["speech-to-text"])

@router.post("/transcribe", response_model=STTResponse)
async def transcribe_audio(payload: STTRequest):
    """
    Transcribe base64 audio to text.
    Supports multi-language STT for Indian languages.
    """
    try:
        transcription = await stt_service.transcribe_audio_base64(
            payload.audio_base64,
            payload.language or "hi-IN",
            payload.mime_type or "audio/pcm;rate=16000"
        )
        
        # Optional: translate to English for downstream processing
        translated_text = ""
        detected_lang = ""
        if payload.translate_to_english and transcription:
            translated_text, detected_lang = await translation_service.translate_text(
                transcription, "English"
            )
        
        return STTResponse(
            transcription=transcription,
            language=payload.language or "hi-IN",
            translated_text=translated_text,
            detected_lang=detected_lang
        )
    except Exception as e:
        logger.error(f"STT transcription failed: {e}")
        raise HTTPException(status_code=500, detail=f"STT transcription failed: {str(e)}")

@router.websocket("/stream/{language}")
async def stt_stream_endpoint(websocket: WebSocket, language: str = "hi-IN"):
    """
    Real-time STT via WebSocket.
    Send audio bytes (PCM 16kHz) and receive real-time transcriptions.
    """
    await websocket.accept()
    logger.info(f"STT WebSocket client connected for language: {language}")
    
    try:
        async with stt_service.connect(language=language) as session:
            await websocket.send_json({
                "event": "stt_session_started",
                "language": language
            })
            
            async def receive_audio_from_client():
                try:
                    while True:
                        message = await websocket.receive()
                        if "bytes" in message:
                            await session.send_realtime_input(
                                audio=stt_service.create_blob(message["bytes"], "audio/pcm;rate=16000")
                            )
                        elif "text" in message:
                            try:
                                data = json.loads(message["text"])
                                if data.get("command") == "stop":
                                    break
                            except Exception:
                                pass
                except WebSocketDisconnect:
                    logger.info("STT WebSocket client disconnected")
                except Exception as e:
                    logger.error(f"Error receiving audio from client: {e}")
            
            async def send_transcriptions_to_client():
                try:
                    async for response in session.receive():
                        text_chunk = ""
                        
                        if response.server_content and response.server_content.model_turn:
                            for part in response.server_content.model_turn.parts:
                                if part.text:
                                    text_chunk += part.text
                        
                        elif response.server_content and hasattr(response.server_content, 'input_transcription') and response.server_content.input_transcription:
                            if response.server_content.input_transcription.text:
                                text_chunk += response.server_content.input_transcription.text
                        
                        if text_chunk:
                            await websocket.send_json({
                                "event": "transcription",
                                "text": text_chunk,
                                "language": language
                            })
                except Exception as e:
                    logger.error(f"Error sending transcription to client: {e}")
            
            await asyncio.gather(
                receive_audio_from_client(),
                send_transcriptions_to_client()
            )
            
    except Exception as e:
        logger.error(f"STT WebSocket session failed: {e}")
        try:
            await websocket.send_json({
                "event": "error",
                "message": f"STT session failed: {str(e)}"
            })
        except Exception:
            pass
    finally:
        try:
            await websocket.close()
        except Exception:
            pass
        logger.info("STT WebSocket session finished")
