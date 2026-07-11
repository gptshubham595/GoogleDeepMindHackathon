from fastapi import APIRouter, HTTPException
from app.models.schemas import EvidenceReport, SuspiciousSegment
from app.state.call_state import call_registry
from datetime import datetime

router = APIRouter(prefix="/call", tags=["evidence-export"])

@router.get("/{call_id}/evidence", response_model=EvidenceReport)
async def get_evidence(call_id: str):
    call_state = await call_registry.get(call_id)
    if not call_state:
        raise HTTPException(
            status_code=404,
            detail=f"Call session '{call_id}' not found or has been cleared."
        )

    state_dict = call_state.to_dict()
    
    # Format suspicious segments
    susp_segs = [
        SuspiciousSegment(
            text=seg["text"],
            reason=seg["reason"],
            timestamp=datetime.fromisoformat(seg["timestamp"])
        )
        for seg in state_dict["suspicious_segments"]
    ]

    # Generate a detailed evidence summary for cybercrime filing
    full_transcript = state_dict["full_transcript"]
    translated = state_dict["full_translated"]
    
    summary_text = (
        f"INCIDENT REPORT: Digital Arrest Extortion Attempt\n"
        f"Session ID: {call_id}\n"
        f"Risk Score: {state_dict['threat_score']*100:.1f}%\n"
        f"Final Risk State: {state_dict['state']}\n\n"
        f"--- Incident Summary ---\n"
        f"The user received a suspicious high-stress call. The automated RAKSHAK threat detection system "
        f"identified this call as a potential Digital Arrest Extortion scam. The caller attempted to "
        f"impersonate legal/government authority to intimidate the victim.\n\n"
        f"--- Suspicious Indicators Flagged ---\n"
    )
    for idx, seg in enumerate(susp_segs, 1):
        summary_text += f"{idx}. Statement: \"{seg.text}\" | Flagged Reason: {seg.reason}\n"

    # Create cybercrime.gov.in complaint JSON format
    cybercrime_report = {
        "incident_category": "Online Extortion / Impersonation Scam",
        "incident_subcategory": "Digital Arrest Extortion (CBI/Customs/Narcotics Impersonation)",
        "national_portal_ready": True,
        "evidence_payload": {
            "caller_voice_log_id": call_id,
            "threat_confidence_pct": state_dict["threat_score"] * 100,
            "verdict": state_dict["state"],
            "full_transcript_text": full_transcript,
            "translated_transcript_english": translated,
            "identified_indicators": [seg["reason"] for seg in state_dict["suspicious_segments"]],
            "complaint_draft_summary": summary_text
        },
        "instructions": (
            "1. Visit https://cybercrime.gov.in/\n"
            "2. Select File a Complaint -> Report Crime Against Women/Children or Other Cyber Crimes\n"
            "3. Copy the 'complaint_draft_summary' text block into the 'Additional Info' section.\n"
            "4. Save this response JSON as evidence file."
        )
    }

    return EvidenceReport(
        call_id=call_id,
        final_state=state_dict["state"],
        threat_score=state_dict["threat_score"],
        full_transcript=full_transcript,
        generated_at=datetime.utcnow(),
        suspicious_segments=susp_segs,
        cybercrime_report=cybercrime_report
    )
