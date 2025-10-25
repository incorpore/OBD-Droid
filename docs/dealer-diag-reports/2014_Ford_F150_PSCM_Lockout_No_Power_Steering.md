# Vehicle Diagnostic Report

> **Template Version:** 1.0  
> **Created:** October 22, 2025  
> **Last Updated:** October 22, 2025  
> **Status:** `CLOSED`  
> **Priority:** 🔴 CRITICAL

---

## 📋 Vehicle Information

| Field | Value |
|-------|-------|
| **Year** | 2014 |
| **Make** | Ford |
| **Model** | F-150 |
| **Trim** | 3.7L V6 DOHC Ti-VCT SEFI |
| **VIN** | 4JGDA5HB7JB158144 |
| **Issue** | No electric power steering assist |
| **Shop / Owner** | Internal diagnostic session |
| **Scan Date** | October 22, 2025 |

**OBD Adapter:** ELM327-compatible Bluetooth dongle (standard headers)  
**OBD Tool Version:** OBD-Droid nightly (2025-10-20)

---

## 🔍 Diagnosis Summary

### What is the Problem?
Electric power steering (EPAS) assist completely inoperative. Steering effort remains high at all speeds after pump replacement. Vehicle returned with customer complaint unresolved.

### Primary Symptoms
- [x] Steering wheel requires excessive effort
- [x] Power steering warning present
- [ ] Battery/charging warning indicators
- [x] Multiple TX errors detected during OBD-Droid scan
- [ ] Audible noises from steering system

### Fault Codes Detected
```
U3003-16: Battery voltage below threshold (stored in PSCM)
B1304-68: EPAS event information (stored in PSCM)
```
> Codes retrieved via Snap-on Zeus professional scanner; **not** surfaced by OBD-Droid.

### Observed Module Behavior
- PSCM remains in protective lockout (no assist).
- Engine control module reports normal operation.
- Vehicle voltage stable at 14.3 V while running.

---

## 🕵️ Root Cause Analysis

**Primary Cause:** Power Steering Control Module (PSCM) entered lockout after a previous low-voltage event. Module requires fault reset and recalibration before it will resume assist.

**Contributing Factors:**
- Ford modules require **active targeted probing** to respond; OBD-Droid only performed broadcast discovery.
- Damaged OBD connector increased TX error rate, further reducing discovery reliability.
- OBD-Droid currently lacks Ford-specific Mode 0x1A03 / 0x1803 / 0x1903 queries necessary to surface U/B/C codes.

**Resolution:** Clear PSCM DTCs, run steering angle sensor calibration, power cycle ignition. No hardware replacement required.

---

## 🧭 Diagnostic Process

### Phase 1: OBD-Droid Scan (Passive Discovery)
```
ECUs Found: 1
  - 0x7E8: PCM - PowertrainCtrl

Fault Codes: 0
Supported PIDs: 0
Communication: Multiple TX errors
```
**Outcome:** PSCM not discovered; no actionable data.

### Phase 2: Professional Scanner (Targeted Probing)
```
ECUs Found: 5+
  - PCM (Powertrain)
  - TCM (Transmission)
  - PSCM (Power Steering) ✅
  - ABS/ESP Module
  - BCM (Body Control)

PSCM Fault Codes:
  - U3003-16
  - B1304-68

Live Data:
  - Pull Compensation Enable Status: DISABLED
  - Power Mode Key State: UNDEFINED
  - Control Module Voltage: 14.3 V
```
**Outcome:** PSCM confirmed alive but locked out; low-voltage history explains failure.

---

## 🛰️ Module Lockout Signature

OBD-Droid should flag a PSCM lockout when **all** of the following are true:
- `Pull Compensation Enable Status` = Disabled
- `Power Mode Key State` = Undefined **or** `Power Mode Quality Factor` contains `EVALUATION IN PROGRESS`
- `Control Module Voltage` ≥ 12 V
- Final/Long/Short Term pull compensation values remain `0`

**Recommended Guidance:**  
> Module in EPAS lockout due to previous low-voltage event. Clear PSCM fault codes, run steering angle sensor calibration, then power-cycle ignition.

---

## 🧪 Testing Strategy

1. Acquire Ford F-150 (2014-2016) test vehicle with accessible PSCM.  
2. Validate active probing sequence that targets PSCM address (`0x726` or `0x18DA26F1`).  
3. Confirm retrieval of Ford U/B/C codes via Modes `0x1A03`, `0x1803`, `0x1903`.  
4. Reproduce lockout signature using controlled battery sag, then verify detection logic.  
5. Run regression on Mercedes-Benz baseline to ensure no cross-manufacturer regressions.

---

## 🛠️ Implementation Plan for OBD-Droid

### Phase 1 – Discovery Upgrades (Immediate)
- Implement active ECU probing with Ford address seeds.  
- Prioritize steering (`0x726`), ABS (`0x736`/`0x760`), BCM (`0x727`) modules.  
- Add retry logic when TX errors detected on damaged connectors.

### Phase 2 – Ford-Specific Mode Support
- Extend PID catalog with Ford U/B/C code modes (`0x1A03`, `0x1803`, `0x1903`).  
- Build parsing layer for Ford extended DTC format (two-byte suffix).  
- Surface friendly module names and subsystems in UI.

### Phase 3 – Guided Repair Experience
- Detect PSCM lockout signature and show corrective action banner.  
- Provide step-by-step clearing workflow with calibration checklist.  
- Log firmware versions and calibration IDs for future comparison.

---

## 📈 Impact Assessment

| Category | Impact |
|----------|--------|
| **Coverage** | Ford vehicles represent the top-selling platform in North America; current passive discovery misses critical ECUs. |
| **User Trust** | Missing PSCM created false perception that no fault codes existed, risking customer confidence. |
| **Time to Fix** | Without full module visibility, issue remained unresolved for 4+ hours; correct software workflow avoids unnecessary pump replacement. |
| **Revenue Risk** | Inability to diagnose Ford steering failures could eliminate dealer upsell opportunities and damage brand credibility. |

---

## ⏱️ Diagnostic Timeline

| Time | Event | Tool | Finding |
|------|-------|------|---------|
| T+0 | Customer complaint: No power steering | - | Steering extremely heavy |
| T+1h | Power steering pump replaced | Manual | No improvement |
| T+2h | Original pump reinstalled | Manual | Still no assist |
| T+3h | OBD-Droid scan via damaged port | OBD-Droid | 1 ECU, 0 codes, TX errors |
| T+4h | Snap-on professional scan | Snap-on | 5+ ECUs, PSCM discovered |
| T+5h | PSCM live data review | Snap-on | Lockout signature present |
| T+6h | Root cause identified | Analysis | Low voltage lockout |
| T+7h | Resolution | Snap-on | Clear codes + recalibrate |

---

## 📚 Fault Code Reference

### U3003-16 — Battery Voltage Below Threshold
- **Module:** Power Steering Control Module (PSCM)  
- **Meaning:** Module detected supply voltage droop (<9–10 V).  
- **Effect:** PSCM enters protective shutdown, disables assist.  
- **Action:** Clear DTC, verify charging system, recalibrate EPAS.

### B1304-68 — EPAS Event Information
- **Module:** PSCM  
- **Meaning:** Historical event logged; indicates protective action taken.  
- **Effect:** Confirms lockout state triggered by low-voltage event.  
- **Action:** Review vehicle voltage history, confirm no harness faults.

---

## 🔭 Technical Deep Dive (Ford-Specific)

- **Discovery Limitation:** Passive broadcast (`0x7DF`) only returns modules responding to Mode 09. Ford PSCM requires targeted `ATSH726` / `ATSH18DA26F1`.  
- **Header Strategy:** Enable headers (`ATH1`) to validate CAN responses and differentiate modules.  
- **Retry Logic:** Introduce backoff and header rotation to handle damaged connectors generating TX errors.  
- **Extended Modes:** Ford uses proprietary extension of SAE J1979; implement `ATCRA` filters or UDS session for complete coverage.

---

## 🧰 Resources & References

- Ford OBD-II Implementation Guide  
- Ford EPAS Service Manual (2011–2014)  
- SAE J1979, ISO 15765-4, ISO 14229 standards  
- Snap-on Zeus data logs (available on request)

---

## Change Log

| Date | Version | Changes | Author |
|------|---------|---------|--------|
| 2025-10-22 | 1.0 | Converted Ford F-150 diagnostic learnings into dealer diagnostic report format | Wal33D |

---

**END OF REPORT**
