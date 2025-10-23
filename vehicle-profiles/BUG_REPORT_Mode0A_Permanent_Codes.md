# BUG REPORT: Mode 0A Permanent Codes Not Displaying

## Bug Summary
**Severity:** HIGH
**Component:** FaultCodeService
**Discovered:** October 23, 2025
**Vehicle:** 2022 GMC Canyon 3.6L V6 (VIN: 107528)

---

## Problem Description

The Fault Codes page shows **0 codes** even though:
1. ✅ Check Engine Light is ON (confirmed by driver)
2. ✅ Live Data page shows "NUMBER OF FAULT CODES: 1"
3. ✅ Live Data page shows "MIL STATUS: On"
4. ✅ Earlier scan showed P0302 PERMANENT code

### Evidence

**Live Data (Mode 01 PID 01):**
```
NUMBER OF FAULT CODES: 1
MIL STATUS: On
```

**FaultCodeService Scan Results:**
```
17:12:55 - Scan complete. Found 0 codes.
17:13:01 - Scan complete. Found 0 codes.
17:14:14 - Scan complete. Found 0 codes.
```

**All scans included Mode 0A (Permanent Codes):**
```
Starting fault code scan for modes: [CONFIRMED, PENDING, PERMANENT]
```

---

## Root Cause Analysis

### Theory 1: ECU Returns "NO DATA" for Mode 0A
Some ECUs (especially GM vehicles) may not respond properly to Mode 0A requests when:
- The permanent code is the ONLY code present
- All stored/pending codes have auto-cleared
- ECU firmware variation

**Evidence:**
- Scan returned 0 codes for all modes (03, 07, 0A)
- Earlier scan DID find the permanent code
- After engine warmed up, codes disappeared from app

### Theory 2: Response Format Issue
GM ECUs might format Mode 0A responses differently than expected:
- Standard format: `4A [count] [DTC1] [DTC2]...`
- GM might use: `4A 00` (no data) even with permanent codes
- Or: Different header format

### Theory 3: Timeout Issue
Mode 0A might be timing out before response arrives:
- Default timeout: 2500ms
- GM ECUs might be slower to respond to Mode 0A
- Response might arrive after timeout

---

## Steps to Reproduce

1. Connect to 2022 GMC Canyon 3.6L with P0302 permanent code
2. Scan codes when engine is warm
3. Observe Fault Codes page shows 0 codes
4. Navigate to Live Data page
5. Observe "NUMBER OF FAULT CODES: 1" and "MIL STATUS: On"

---

## Current Workaround

**For Users:**
1. Check Live Data page for "NUMBER OF FAULT CODES"
2. This reliably shows the count from Mode 01 PID 01
3. If MIL is ON and count > 0, codes exist even if not displayed

**For Dealership:**
- Use physical scan tool to confirm codes
- Or use Live Data readout as confirmation

---

## Proposed Fixes

### Fix 1: Enhanced Logging (DEBUG)
Add logging to see actual ECU responses:

```java
// In FaultCodeService.parseFaultCodes()
private List<FaultCodeInfo> parseFaultCodes(String response, CodeType codeType) {
    // ADD THIS:
    log.info("Parsing " + codeType + " response: " + response);

    // ... existing code ...
}
```

### Fix 2: Fallback to Mode 01 PID 01
If Mode 03/07/0A all return 0 but Mode 01 shows codes:

```java
// After scanning all modes:
if (allScansReturnZero && mode01ShowsCodes) {
    // Display warning: "ECU reports N codes but scan returned none"
    // Suggest using physical scan tool
}
```

### Fix 3: Increase Mode 0A Timeout
```java
// Current
private static final long SCAN_TIMEOUT_MS = 2_500L;

// Change to:
private static final long SCAN_TIMEOUT_MS = 5_000L;  // 5 seconds
```

### Fix 4: Alternative Mode 0A Query
Some ECUs respond better to:
```
01 01  (Mode 01 PID 01 - includes DTC count and MIL status)
```
Then cross-reference with Mode 03/07 results.

---

## Impact

**Affects:**
- Any vehicle with ONLY permanent codes remaining
- GM vehicles (potentially all GM ECUs)
- Users trying to verify repairs before selling

**Does NOT Affect:**
- Vehicles with stored/confirmed codes
- Vehicles with pending codes
- Initial scans when all code types present

---

## Testing Needed

1. ✅ **Verified:** Live Data correctly reads DTC count from Mode 01
2. ⏳ **Need:** Raw Mode 0A response logging
3. ⏳ **Need:** Test with extended timeout (5s)
4. ⏳ **Need:** Test on other GM vehicles
5. ⏳ **Need:** Compare with physical scan tool results

---

## Related Code Files

- `/app/src/java/com/obddroid/services/FaultCodeService.java:318-373` (parseFaultCodes)
- `/app/src/java/com/obddroid/services/FaultCodeService.java:199-234` (executeScan)
- `/app/src/java/com/obddroid/core/obd/ObdProt.java:54-61` (OBD service constants)

---

## Temporary Solution for Vehicle Profile

**For 2022 GMC Canyon (VIN 107528):**

**Known Fault Code:**
- P0302 - Cylinder 2 Misfire Detected (PERMANENT)
- Confirmed via: Initial scan + Live Data + Dashboard MIL
- Status: Present but not displayed after warm-up

**Action Required:**
1. Replace spark plugs (all 6 cylinders)
2. Test drive 20+ miles
3. Permanent code will self-clear after ECU verifies repair
4. Use Live Data page to monitor DTC count

---

## Priority: HIGH

This bug affects real-world use case (dealership trying to verify repairs). Users cannot trust the Fault Codes page when only permanent codes remain.

**Recommend:** Immediate investigation with enhanced logging enabled.

---

**Reported By:** Claude Code Diagnostics
**Date:** October 23, 2025
**Vehicle:** 2022 GMC Canyon 3.6L V6
**App Version:** OBD-Droid Latest Build
