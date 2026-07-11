import re
import logging
from typing import Dict, List, Tuple
from google import genai
from google.genai import types
from app.config import settings

logger = logging.getLogger(__name__)

# Pattern indicators categorized for extortion mechanics
PATTERNS = {
    "authority": {
        "keywords": [
            r"\bcbi\b", r"\bnarcotics\b", r"\bncb\b", r"\bcustoms\b", r"\bpolice\b", r"\btrai\b", 
            r"\bsupreme court\b", r"\bcrime branch\b", r"\benforcement directorate\b", r"\bed\b",
            r"\btelecom authority\b", r"\bips officer\b", r"\bdcp\b", r"\bcyber cell\b",
            # Hindi/transliterated variants
            r"\bthan\b", r"\bthana\b", r"\bwarden\b", r"\badalat\b", r"\bkanoon\b"
        ],
        "weight": 0.3
    },
    "isolation": {
        "keywords": [
            r"\bdigital arrest\b", r"\bdo not disconnect\b", r"\bcamera\s*(on|chalu)\b", 
            r"\bclosed room\b", r"\bdon't tell anyone\b", r"\bconfidential\b", r"\bnational security\b", 
            r"\bkeep the call active\b", r"\bdon't talk to anyone\b", r"\bisolate\b", r"\bstay in\b",
            # Hindi/transliterated variants
            r"\bcamera chalu\b", r"\bkatna mat\b", r"\bkisiko mat batana\b", r"\bkamre me\b", r"\bandar raho\b"
        ],
        "weight": 0.4
    },
    "allegation": {
        "keywords": [
            r"\billegal parcel\b", r"\bcontraband\b", r"\bdrugs found\b", r"\bpassport stolen\b", 
            r"\bsim card blocked\b", r"\bidentity theft\b", r"\bmoney laundering\b", r"\bweapons\b",
            r"\bpornography\b", r"\barrest warrant\b", r"\bcrime committed\b", r"\bsuspension\b",
            # Hindi/transliterated variants
            r"\bparcel mila\b", r"\bblock ho gaya\b", r"\bdrugs mile\b", r"\bgalat kaam\b"
        ],
        "weight": 0.3
    },
    "demand": {
        "keywords": [
            r"\bverify funds\b", r"\bsecurity deposit\b", r"\brbi account\b", r"\bbank transfer\b", 
            r"\baudit account\b", r"\btransfer money\b", r"\bavoid arrest\b", r"\bpay bail\b",
            r"\bsettlement\b", r"\bliquidation\b",
            # Hindi/transliterated variants
            r"\bpaise bhejo\b", r"\btransfer karo\b", r"\baccount me\b", r"\bkhata\b", r"\bbachna hai\b"
        ],
        "weight": 0.4
    }
}

class ThreatClassifier:
    def __init__(self):
        try:
            self.client = genai.Client(api_key=settings.gemini_api_key)
        except Exception as e:
            logger.error(f"Failed to initialize Gemini Client in ThreatClassifier: {e}")
            self.client = None

    def pattern_scan(self, text: str) -> Tuple[float, List[Dict[str, str]]]:
        """
        Fast, synchronous keyword-based threat scanner.
        """
        text_lower = text.lower()
        matched_categories = set()
        matched_segments = []
        score = 0.0

        for category, config in PATTERNS.items():
            category_matched = False
            for pattern in config["keywords"]:
                matches = re.findall(pattern, text_lower)
                if matches:
                    category_matched = True
                    # Try to capture surrounding context for evidence
                    for match in set(matches):
                        # Simple regex context extraction around the keyword
                        idx = text_lower.find(match)
                        start = max(0, idx - 40)
                        end = min(len(text), idx + len(match) + 40)
                        matched_segments.append({
                            "text": text[start:end].strip(),
                            "reason": f"Matched extortion pattern '{match}' in category: {category}"
                        })
            if category_matched:
                matched_categories.add(category)
                score += config["weight"]

        # Cap score at 1.0
        score = min(1.0, score)
        return score, matched_segments

    async def classify_transcript(self, transcript: str, use_llm: bool = True) -> Tuple[str, float, List[Dict[str, str]]]:
        """
        Analyzes the transcript buffer. Combines fast rule-based scanner with deep LLM semantic check.
        Returns: Tuple[State (NORMAL/SUSPICIOUS/THREAT_DETECTED), ThreatScore (0-1), DetectedSegments]
        """
        if not transcript.strip():
            return "NORMAL", 0.0, []

        # 1. Fast Pattern Check
        pattern_score, segments = self.pattern_scan(transcript)

        # State determined by pattern scan as first pass
        state = "NORMAL"
        if pattern_score >= 0.7:
            state = "THREAT_DETECTED"
        elif pattern_score >= 0.3:
            state = "SUSPICIOUS"

        # 2. Async LLM Validation if possible and enabled
        if use_llm and self.client and len(transcript) > 50:
            try:
                prompt = f"""
                You are a security subagent analyzing a phone call transcript for a "Digital Arrest" extortion scam.
                Digital Arrest scams involve bad actors pretending to be police/CBI/NCB/Customs/TRAI calling the victim to say a package with illegal items (drugs, fake passports) was caught in their name, or their SIM card will be blocked due to illegal activity. They coerce the victim to stay on a camera call (Skype/WhatsApp) inside a closed room (digital arrest), demanding financial verification transfers to avoid arrest.

                Analyze this transcript snippet:
                ---
                "{transcript}"
                ---

                Respond strictly in the following JSON format:
                {{
                  "is_scam": true/false,
                  "confidence": 0.85,
                  "reason": "Short summary of why it is or isn't a scam, highlighting claims of authority (CBI/Customs), coercion (digital arrest), or payment demands.",
                  "extortion_indicators": ["list of indicators detected e.g. 'threat of SIM block', 'CBI authority claim', 'isolation instructions'"]
                }}
                """
                response = self.client.models.generate_content(
                    model=settings.model_reasoning,
                    contents=prompt,
                    config=types.GenerateContentConfig(
                        response_mime_type="application/json",
                        temperature=0.1
                    )
                )
                import json
                result = json.loads(response.text)
                
                llm_confidence = float(result.get("confidence", 0.0))
                is_scam = bool(result.get("is_scam", False))
                
                # Combine pattern scan and LLM results
                if is_scam:
                    # Boost confidence score
                    combined_score = max(pattern_score, llm_confidence)
                    state = "THREAT_DETECTED" if combined_score >= 0.75 else "SUSPICIOUS"
                    
                    # Add reason to segments
                    segments.append({
                        "text": transcript[-100:],  # Show recent context
                        "reason": f"Semantic analysis: {result.get('reason')}"
                    })
                    pattern_score = combined_score
                else:
                    # If LLM is highly confident it's safe, slightly tone down pattern score
                    if llm_confidence > 0.8:
                        pattern_score = max(0.0, pattern_score - 0.2)
                        state = "SUSPICIOUS" if pattern_score >= 0.3 else "NORMAL"
                        
            except Exception as e:
                logger.error(f"Error calling Gemini in ThreatClassifier: {e}")
                # Fallback to pattern state on API failure

        return state, pattern_score, segments

threat_classifier = ThreatClassifier()
