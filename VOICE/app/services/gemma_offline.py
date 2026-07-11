from typing import Dict, List, Any
import re

# Offline keyword dictionary with weights specifically for Digital Arrest Extortion
OFFLINE_KEYWORDS = {
    # CBI, Police, Custom, Narcotics, TRAI, Skype, Court
    "authority": [
        "cbi", "police", "customs", "narcotics", "ncb", "trai", "supreme court", "court", 
        "inspector", "commissioner", "sim block", "telecom", "illegal package", "contraband"
    ],
    # Digital arrest, don't hang up, camera on, closed door
    "coercion": [
        "digital arrest", "do not disconnect", "do not hang up", "stay on call", 
        "camera on", "closed room", "isolate", "confidentiality", "secret", "national security",
        "don't tell anyone", "skype call", "video call"
    ],
    # Transfer money, verify, security deposit, bank details, RBI, lock account
    "demand": [
        "transfer", "verify funds", "security deposit", "rbi", "bank account", "payment", 
        "liquidation", "audit", "avoid arrest", "settlement"
    ]
}

class GemmaOfflineClassifier:
    """
    Simulates a local, on-device Gemma-4 / E2B classifier running on-device
    without sending any data to remote servers. This runs quickly and acts
    as the offline fallback system when connectivity is compromised.
    """
    
    def classify(self, transcript: str) -> Dict[str, Any]:
        if not transcript:
            return {
                "state": "NORMAL",
                "confidence": 0.0,
                "matched_keywords": [],
                "action_required": False
            }

        text_lower = transcript.lower()
        matched = []
        scores = {
            "authority": 0,
            "coercion": 0,
            "demand": 0
        }

        # Check keyword counts
        for category, keywords in OFFLINE_KEYWORDS.items():
            for kw in keywords:
                if re.search(r'\b' + re.escape(kw) + r'\b', text_lower):
                    matched.append(kw)
                    scores[category] += 1

        # Calculate semantic score based on how many distinct components are matched
        # A true Digital Arrest scam has high overlap of: Authority + Coercion/Allegation + Demand
        categories_matched = sum(1 for v in scores.values() if v > 0)
        total_hits = sum(scores.values())

        if categories_matched == 3:
            # High risk
            confidence = min(0.95, 0.6 + (total_hits * 0.08))
            state = "THREAT_DETECTED"
        elif categories_matched == 2:
            # Medium risk
            confidence = min(0.74, 0.4 + (total_hits * 0.07))
            state = "SUSPICIOUS"
        elif categories_matched == 1 and total_hits >= 2:
            # Low-medium risk
            confidence = min(0.45, 0.2 + (total_hits * 0.08))
            state = "SUSPICIOUS"
        else:
            confidence = min(0.25, total_hits * 0.1)
            state = "NORMAL"

        return {
            "state": state,
            "confidence": round(confidence, 2),
            "matched_keywords": matched,
            "action_required": state == "THREAT_DETECTED"
        }

gemma_offline = GemmaOfflineClassifier()
