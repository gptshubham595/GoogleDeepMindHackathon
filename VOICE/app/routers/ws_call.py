from fastapi import APIRouter, WebSocket, WebSocketDisconnect
from app.state.call_state import call_registry
from app.services.live_session import live_service
from app.services.translate import translation_service
from app.services.threat_classifier import threat_classifier
from app.services.tts import tts_service
from google.genai import types
import asyncio
import logging
import json

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/ws", tags=["live-call-websocket"])

@router.websocket("/call/{call_id}")
async def ws_call_endpoint(websocket: WebSocket, call_id: str):
    await websocket.accept()
    logger.info(f"WebSocket client connected for call_id: {call_id}")
    
    # Get or create call state
    call_state = await call_registry.get_or_create(call_id)
    
    # Establish connection with Gemini Live API
    system_instruction = (
        "You are a real-time call transcription and analysis agent. You will listen to "
        "the incoming audio of a phone call. For every piece of speech you hear, "
        "transcribe it exactly. Do not respond to the content or engage in conversation; "
        "only transcribe exactly what you hear in the language you hear it."
    )
    
    try:
        # Connect to the live session
        async with live_service.connect(system_instruction=system_instruction) as session:
            logger.info("Successfully connected to Gemini Live API session.")
            
            # Send initial message to indicate session started
            await websocket.send_json({
                "event": "session_started",
                "call_id": call_id,
                "state": call_state.state,
                "threat_score": call_state.threat_score
            })
            
            # Queue to coordinate client data to Gemini
            client_to_gemini_queue = asyncio.Queue()
            
            async def read_from_client():
                try:
                    while True:
                        message = await websocket.receive()
                        if "bytes" in message:
                            data = message["bytes"]
                            await client_to_gemini_queue.put(data)
                        elif "text" in message:
                            try:
                                text_data = json.loads(message["text"])
                                if text_data.get("command") == "stop":
                                    logger.info(f"Stop command received from client for call: {call_id}")
                                    break
                            except Exception:
                                # Standard string text can also be forwarded as text input to Gemini
                                await session.send_realtime_input(text=message["text"])
                except WebSocketDisconnect:
                    logger.info(f"Client disconnected for call: {call_id}")
                except Exception as e:
                    logger.error(f"Error reading from client: {e}")
                finally:
                    # Put None to signal end of stream
                    await client_to_gemini_queue.put(None)

            async def send_to_gemini():
                try:
                    while True:
                        data = await client_to_gemini_queue.get()
                        if data is None:
                            break
                        # Send PCM audio chunk to Gemini Live
                        await session.send_realtime_input(
                            audio=types.Blob(
                                data=data,
                                mime_type="audio/pcm;rate=16000"
                            )
                        )
                except Exception as e:
                    logger.error(f"Error sending to Gemini Live: {e}")

            async def read_from_gemini():
                try:
                    async for response in session.receive():
                        text_chunk = ""
                        
                        # Check for text in model output
                        if response.server_content and response.server_content.model_turn:
                            for part in response.server_content.model_turn.parts:
                                if part.text:
                                    text_chunk += part.text
                                    
                        # Check for server-side automatic input transcription
                        elif response.server_content and hasattr(response.server_content, 'input_transcription') and response.server_content.input_transcription:
                            if response.server_content.input_transcription.text:
                                text_chunk += response.server_content.input_transcription.text
                                
                        if text_chunk:
                            logger.info(f"[{call_id}] Live Transcript chunk: {text_chunk}")
                            
                            # Normalize text to English for extortion classification
                            translated_chunk, detected_lang = await translation_service.translate_text(text_chunk)
                            await call_state.add_transcript(text_chunk, translated_chunk)
                            
                            # Analyze rolling buffer
                            full_translated = call_state.get_full_translated_transcript()
                            new_state, score, segments = await threat_classifier.classify_transcript(full_translated)
                            
                            if new_state != call_state.state:
                                await call_state.update_state(new_state, score, segments)
                                
                                # If threat is detected, generate warning speech
                                audio_warning_b64 = ""
                                if new_state == "THREAT_DETECTED":
                                    warning_text = "Warning! This call is identified as a digital arrest extortion scam. Hang up immediately."
                                    if detected_lang and detected_lang.lower() == "hindi":
                                        warning_text = "सावधान! यह कॉल एक डिजिटल अरेस्ट घोटाला है। कृपया तुरंत फोन काट दें।"
                                    
                                    # Generate speech voice in the target language
                                    audio_warning_b64 = await tts_service.generate_warning_speech_base64(warning_text)
                                    
                                await websocket.send_json({
                                    "event": "state_changed",
                                    "state": new_state,
                                    "threat_score": score,
                                    "matched_keywords": [s["reason"] for s in segments],
                                    "warning_audio": audio_warning_b64,
                                    "transcript_chunk": text_chunk
                                })
                            else:
                                await websocket.send_json({
                                    "event": "transcript_update",
                                    "transcript_chunk": text_chunk,
                                    "full_transcript": call_state.get_full_transcript(),
                                    "threat_score": score,
                                    "state": call_state.state
                                })
                except Exception as e:
                    logger.error(f"Error receiving from Gemini: {e}")

            # Run client reader, gemini sender, and gemini reader concurrently
            await asyncio.gather(
                read_from_client(),
                send_to_gemini(),
                read_from_gemini()
            )
            
    except Exception as e:
        logger.error(f"Failed to connect to Live API session for {call_id}: {e}")
        try:
            await websocket.send_json({
                "event": "error",
                "message": f"Failed to connect to Live API session: {str(e)}"
            })
        except Exception:
            pass
    finally:
        # Mark call inactive
        await call_registry.deactivate(call_id)
        try:
            await websocket.close()
        except Exception:
            pass
        logger.info(f"WebSocket session finished for call_id: {call_id}")
