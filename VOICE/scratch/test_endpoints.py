import requests
import json
import base64

BASE_URL = "http://127.0.0.1:8000"

def test_translate():
    print("Testing /translate...")
    payload = {
        "text": "Aapko cbi office aana hoga. Aapka parcel block ho gaya hai airport par.",
        "target_lang": "English"
    }
    response = requests.post(f"{BASE_URL}/translate", json=payload)
    print(f"Status Code: {response.status_code}")
    print(f"Response: {response.text}\n")
    assert response.status_code == 200
    res = response.json()
    assert "translated_text" in res
    assert "detected_lang" in res

def test_tts():
    print("Testing /tts/warning...")
    payload = {
        "text": "Hang up the phone immediately. This is a scam.",
        "voice_name": "Kore"
    }
    response = requests.post(f"{BASE_URL}/tts/warning", json=payload)
    print(f"Status Code: {response.status_code}")
    print(f"Response (truncated): {response.text[:200]}...")
    assert response.status_code == 200
    res = response.json()
    assert "audio_base64" in res
    # Verify we can decode it
    audio_bytes = base64.b64decode(res["audio_base64"])
    print(f"Generated {len(audio_bytes)} bytes of audio data\n")
    assert len(audio_bytes) > 0

def test_offline_classify():
    print("Testing /offline/classify...")
    payload = {
        "transcript_chunk": "I am police officer. Stay on call and verify bank account to avoid digital arrest."
    }
    response = requests.post(f"{BASE_URL}/offline/classify", json=payload)
    print(f"Status Code: {response.status_code}")
    print(f"Response: {response.text}\n")
    assert response.status_code == 200
    res = response.json()
    assert res["state"] == "THREAT_DETECTED"
    assert res["action_required"] is True
    assert "digital arrest" in res["matched_keywords"]

def main():
    try:
        test_translate()
        test_tts()
        test_offline_classify()
        print("All REST tests passed successfully!")
    except Exception as e:
        print(f"Test failed with error: {e}")

if __name__ == "__main__":
    main()
