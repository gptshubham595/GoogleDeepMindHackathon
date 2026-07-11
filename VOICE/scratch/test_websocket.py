import asyncio
import websockets
import json

BASE_WS_URL = "ws://127.0.0.1:8000/ws/call/test-ws-1"

async def test_websocket_scam_flow():
    print("Testing Live Call WebSocket flow...")
    try:
        async with websockets.connect(BASE_WS_URL) as ws:
            print("Connected to WebSocket successfully!")
            
            # 1. Receive session_started event
            first_msg = await ws.recv()
            data = json.loads(first_msg)
            print(f"Received initial event: {data}")
            assert data["event"] == "session_started"
            assert data["state"] == "NORMAL"
            
            # Send standard greetings (safe)
            print("\nSending: 'Hello, who is this?'")
            await ws.send("Hello, who is this?")
            
            # Wait for response (should be transcript update or state changed)
            resp = await ws.recv()
            data = json.loads(resp)
            print(f"Response: {data}")
            assert data["event"] in ["transcript_update", "state_changed"]
            
            # Send threatening extortion messages
            sentences = [
                "This is Inspector Kumar from Trai and CBI. Your sim card is block due to illegal package found at airport.",
                "Drugs and fake passports were found in a package with your name on it. You are under digital arrest.",
                "You must stay in a closed room, turn camera on Skype, and transfer money to RBI account immediately."
            ]
            
            for sentence in sentences:
                print(f"\nSending extortion text: '{sentence}'")
                await ws.send(sentence)
                
                # Receive responses
                resp = await ws.recv()
                data = json.loads(resp)
                print(f"Received event: {data['event']} | State: {data.get('state')} | Threat Score: {data.get('threat_score')}")
                
                # If state transitions to THREAT_DETECTED, verify we got warning_audio
                if data.get("state") == "THREAT_DETECTED":
                    print("SUCCESS: Threat state transitioned to THREAT_DETECTED!")
                    assert "warning_audio" in data
                    assert len(data["warning_audio"]) > 0
                    print(f"Generated TTS warning audio base64 size: {len(data['warning_audio'])} characters.")
                    break
            
            print("\nWebSocket Scam Flow Test passed!")
            
    except Exception as e:
        print(f"WebSocket test failed: {e}")

if __name__ == "__main__":
    asyncio.run(test_websocket_scam_flow())
