import asyncio
from datetime import datetime
from typing import Dict, List, Any
from pydantic import BaseModel, Field

class SuspiciousSegmentInternal(BaseModel):
    text: str
    reason: str
    timestamp: datetime = Field(default_factory=datetime.utcnow)

class CallState:
    def __init__(self, call_id: str):
        self.call_id: str = call_id
        self.state: str = "NORMAL"  # NORMAL -> SUSPICIOUS -> THREAT_DETECTED -> INTERVENTION
        self.transcript_buffer: List[str] = []
        self.translated_buffer: List[str] = []
        self.threat_score: float = 0.0
        self.suspicious_segments: List[SuspiciousSegmentInternal] = []
        self.last_updated: datetime = datetime.utcnow()
        self.lock: asyncio.Lock = asyncio.Lock()
        self.is_active: bool = True

    async def add_transcript(self, text: str, translated: str = None):
        async with self.lock:
            self.transcript_buffer.append(text)
            if translated:
                self.translated_buffer.append(translated)
            self.last_updated = datetime.utcnow()

    async def update_state(self, state: str, threat_score: float, new_suspicious_segments: List[Dict[str, str]] = None):
        async with self.lock:
            self.state = state
            self.threat_score = max(self.threat_score, threat_score)
            if new_suspicious_segments:
                for seg in new_suspicious_segments:
                    self.suspicious_segments.append(
                        SuspiciousSegmentInternal(text=seg["text"], reason=seg["reason"])
                    )
            self.last_updated = datetime.utcnow()

    def get_full_transcript(self) -> str:
        return " ".join(self.transcript_buffer)

    def get_full_translated_transcript(self) -> str:
        return " ".join(self.translated_buffer) if self.translated_buffer else self.get_full_transcript()

    def to_dict(self) -> Dict[str, Any]:
        return {
            "call_id": self.call_id,
            "state": self.state,
            "threat_score": self.threat_score,
            "full_transcript": self.get_full_transcript(),
            "full_translated": self.get_full_translated_transcript(),
            "suspicious_segments": [
                {"text": s.text, "reason": s.reason, "timestamp": s.timestamp.isoformat()}
                for s in self.suspicious_segments
            ],
            "last_updated": self.last_updated.isoformat(),
            "is_active": self.is_active
        }

class CallStateRegistry:
    def __init__(self):
        self._calls: Dict[str, CallState] = {}
        self._lock = asyncio.Lock()

    async def get_or_create(self, call_id: str) -> CallState:
        async with self._lock:
            if call_id not in self._calls:
                self._calls[call_id] = CallState(call_id)
            return self._calls[call_id]

    async def get(self, call_id: str) -> CallState | None:
        async with self._lock:
            return self._calls.get(call_id)

    async def deactivate(self, call_id: str):
        async with self._lock:
            if call_id in self._calls:
                self._calls[call_id].is_active = False

    async def remove(self, call_id: str):
        async with self._lock:
            if call_id in self._calls:
                del self._calls[call_id]

# Singleton registry
call_registry = CallStateRegistry()
