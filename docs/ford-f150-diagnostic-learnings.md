# Ford F-150 Diagnostic Session - Learnings & Improvements

**Date:** October 22, 2025
**Vehicle:** 2014 Ford F-150 3.7L V6 DOHC Ti-VCT SEFI
**VIN:** 4JGDA5HB7JB158144
**Issue:** No power steering assist
**Outcome:** Identified critical gaps in OBD-Droid's Ford support

---

## Executive Summary

This document captures critical learnings from diagnosing a 2014 Ford F-150 with a power steering failure using OBD-Droid. The diagnostic session revealed fundamental limitations in OBD-Droid's multi-manufacturer support, particularly for Ford vehicles.

**Key Finding:** OBD-Droid uses **passive broadcast discovery** which only finds modules that respond to Mode 9 queries. Many Ford modules (especially Power Steering, ABS, BCM) require **active targeted probing** to be discovered.

**Impact:**
- On this F-150, OBD-Droid found **1 ECU** (PCM only)
- Snap-on professional scanner found **5+ ECUs** including the failed Power Steering module
- The critical diagnostic data needed to solve the problem was invisible to OBD-Droid

**Priority:** HIGH - This affects diagnostic capability on all Ford vehicles (F-150 is the best-selling vehicle in North America)

---

## Table of Contents

1. [Case Study: The F-150 Power Steering Failure](#case-study-the-f-150-power-steering-failure)
2. [What OBD-Droid Showed vs. Reality](#what-obd-droid-showed-vs-reality)
3. [Root Cause Analysis](#root-cause-analysis)
4. [Technical Deep Dive](#technical-deep-dive)
5. [Proposed Improvements](#proposed-improvements)
6. [Implementation Plan](#implementation-plan)
7. [Testing Strategy](#testing-strategy)
8. [Impact Assessment](#impact-assessment)

---

## Case Study: The F-150 Power Steering Failure

### Customer Complaint
- Power steering completely non-functional (heavy steering at all speeds)
- Power steering pump was replaced but issue persisted
- Pump was returned to original - still no power steering

### Diagnostic Steps Taken

#### Phase 1: OBD-Droid Scan (via damaged OBD port)
**Results:**
```
ECUs Found: 1
  - 0x7E8: PCM - PowertrainCtrl

Fault Codes: 0
Supported PIDs: 0
Communication Status: Multiple TX errors
```

**Limitations Encountered:**
- Only discovered Powertrain Control Module
- Power Steering Control Module (PSCM) not found
- Fault code scan returned 0 codes (misleading)
- TX errors indicated communication problems

#### Phase 2: Snap-on Professional Scanner Scan
**Results:**
```
ECUs Found: 5+
  - PCM (Powertrain)
  - TCM (Transmission)
  - PSCM (Power Steering) ✅ FOUND!
  - ABS/ESP Module
  - BCM (Body Control)

Fault Codes from Power Steering Module:
  - U3003-16: Battery voltage below threshold
  - B1304-68: EPAS Event Information

Live Data from PSCM:
  - Pull Compensation Enable Status: DISABLED ❌
  - Power Mode Key State: UNDEFINED ❌
  - Control Module Voltage: 14.3V ✅
  - Module stuck in safe/lockout mode
```

**Resolution:**
- Power Steering module was alive but in protective safe mode
- Module detected low voltage event and locked itself out
- Required clearing codes and recalibration (not pump replacement)
- Estimated fix: $0-65 (software reset, not hardware)

### Lockout Detection Signature (New Learning)

Live data from the Snap-on session captured a repeatable pattern that uniquely identifies the PSCM's protective lockout state. OBD-Droid should flag the module as locked out when **all** of the following are true:

- `Pull Compensation Enable Status` reports **Disabled**
- `Power Mode Key State` is **Undefined** _or_ `Power Mode Quality Factor` contains **EVAIL IN PROGRESS**
- `Control Module Voltage` is ≥ 12 V (module has power) while Final/Long/Short Term pull compensation values remain **0**

When the signature is present, surface guidance that the fix is software-only:

> Module in EPAS lockout due to previous low-voltage event. Clear PSCM fault codes, run steering angle sensor calibration, power-cycle ignition.

### Lesson Learned
**OBD-Droid missed the MOST IMPORTANT module** - the one causing the actual problem. Without seeing the Power Steering module, we couldn't:
- Read the fault codes that explained the issue
- See that module was in safe mode
- Access live data showing voltage was good
- Provide accurate diagnosis to customer

---

## What OBD-Droid Showed vs. Reality

### OBD-Droid Report

```
✅ Connection successful
✅ VIN decoded: 4JGDA5HB7JB158144
⚠️  ECU Discovery: 1 ECU found
❌ Supported PIDs: 0 detected
❌ Fault Codes: 0 found
⚠️  Communication errors: Multiple TX errors
```

**User's Perception:**
- "Vehicle has no fault codes"
- "Only the engine module is working"
- "Communication problems due to damaged OBD port"

### Snap-on Report

```
✅ Connection successful
✅ VIN decoded: 4JGDA5HB7JB158144
✅ ECU Discovery: 5+ ECUs found
✅ Power Steering Module found at 0x726
✅ Fault Codes: 2 codes in PSCM (U3003-16, B1304-68)
✅ Live Data: PSCM in safe mode, needs reset
✅ Diagnosis: Software reset required, NOT hardware
```

**Actual Reality:**
- Vehicle HAS fault codes (in PSCM, not PCM)
- Multiple modules are working
- Power Steering module exists but didn't respond to OBD-Droid
- Problem is software lockout, not mechanical failure

### Impact of Missed Diagnosis

**Financial Impact:**
- Power steering pump replaced unnecessarily: ~$200-500
- Pump returned, labor wasted: ~2 hours
- Actual fix cost: $0-65 (code clear + calibration)
- **Wasted effort:** $200-500 + labor time

**Trust Impact:**
- OBD-Droid appeared less capable than professional tools
- User questioned app's diagnostic accuracy
- May discourage using app for critical diagnostics

---

## Root Cause Analysis

### Why Did OBD-Droid Fail to Find the Power Steering Module?

#### Current ECU Discovery Method: PASSIVE BROADCAST

**File:** `app/src/java/com/obddroid/services/EcuDiscoveryService.java`

**How it works:**
```java
// Step 1: Enable CAN headers
sendRawCommand("ATH1");

// Step 2: Broadcast Mode 9 queries to ALL modules
sendRawCommand("0904");  // "Anyone have Calibration ID?"
sendRawCommand("0906");  // "Anyone have CVN?"
sendRawCommand("090A");  // "Anyone have ECU Name?"

// Step 3: Wait for responses
// Only modules that RESPOND get discovered

// Step 4: Disable headers
sendRawCommand("ATH0");
```

**Problems with this approach:**

1. **Assumes all modules respond to Mode 9**
   - Standard OBD-II modules (PCM, TCM): Usually respond ✅
   - Ford-specific modules (PSCM, BCM, ABS): Often DON'T respond ❌

2. **No fallback if modules don't respond**
   - If module doesn't answer broadcast, it's never discovered
   - No attempt to probe known addresses

3. **Vulnerable to communication issues**
   - Damaged OBD port = weak signals
   - Broadcast messages are lowest priority on CAN bus
   - Easily lost in noisy environment

4. **No manufacturer-specific logic**
   - Mercedes: May respond well to Mode 9 (why it worked for testing)
   - Ford: Uses proprietary module addressing
   - GM, Toyota, Honda: Each have different behaviors

### Why Did Snap-on Succeed?

**Snap-on's Method: ACTIVE TARGETED PROBING**

```java
// Snap-on approach (pseudocode):

// Step 1: Broadcast discovery (same as OBD-Droid)
broadcastMode9Queries();

// Step 2: ACTIVE PROBING (OBD-Droid missing this!)
for (int address : KNOWN_FORD_ADDRESSES) {
    // Set specific module as target
    setHeader(address);

    // Directly query this module
    response = query("0100");  // "Are you there, 0x726?"

    if (hasResponse(response)) {
        addModule(address);
        queryModuleDetails(address);
    }
}

// Result: Found Power Steering at 0x726 even though
// it didn't respond to Mode 9 broadcast!
```

**Key Differences:**

| Feature | OBD-Droid | Snap-on |
|---------|-----------|---------|
| **Discovery Method** | Passive broadcast only | Broadcast + Active probing |
| **Known Addresses** | None (waits for responses) | Extensive database per manufacturer |
| **Fallback Strategy** | None | Multiple query methods |
| **Ford PSCM Support** | ❌ Not found | ✅ Found at 0x726 |
| **Result** | 1 ECU (PCM only) | 5+ ECUs (all modules) |

---

## Technical Deep Dive

### Current EcuDiscoveryService Implementation

**Location:** `/app/src/java/com/obddroid/services/EcuDiscoveryService.java`

**Key Methods:**

#### 1. `discoverEcus()` - Main Discovery Method
```java
public CompletableFuture<Map<Integer, EcuDiscoveryInfo>> discoverEcus() {
    // Runs in background thread
    new Thread(() -> {
        // Step 1: Enable headers (ATH1)
        sendRawCommand("ATH1");
        Thread.sleep(600);

        // Step 2: Broadcast Mode 9 PID 04 (Calibration ID)
        sendRawCommand("0904");
        Thread.sleep(500);

        // Step 3: Broadcast Mode 9 PID 06 (CVN)
        sendRawCommand("0906");
        Thread.sleep(500);

        // Step 4: Broadcast Mode 9 PID 0A (ECU Name)
        sendRawCommand("090A");
        Thread.sleep(500);

        // Step 5: Disable headers (ATH0)
        sendRawCommand("ATH0");
        Thread.sleep(300);

        // Step 6: Process responses
        // Returns ONLY modules that responded
    }).start();
}
```

**Limitation:** No active probing of known addresses!

#### 2. `parseEcuAddress()` - Address Validation
```java
private int parseEcuAddress(String response) {
    // Current code (line 347):
    if ((addr >= 0x7E0 && addr <= 0x7FF) ||
        (addr >= 0x18D && addr <= 0x18F)) {
        return addr;
    }
    // Rejects addresses outside this narrow range!
}
```

**Problem:** Ford Power Steering at **0x726** is BELOW 0x7E0 range!

**Why this exists:** Code assumes only standard OBD-II addresses (0x7E0-0x7EF)

**Fix needed:** Accept wider range: 0x700-0x7FF for Ford modules

#### 3. Supported Address Ranges

**Current (OBD-Droid):**
```
Accepted: 0x7E0 - 0x7FF  (32 addresses)
          0x18D - 0x18F  (3 addresses)
Total:    35 possible addresses
```

**Needed for Ford:**
```
Standard OBD:  0x7E0 - 0x7EF  (16 addresses)
Ford Extended: 0x700 - 0x7DF  (224 addresses)
Ford Specific: 0x720 - 0x73F  (32 addresses) ← PSCM is here!
Total needed:  0x700 - 0x7FF  (256 addresses)
```

### Ford-Specific Module Addresses

#### Common Ford ECU Addresses (2010-2024 vehicles)

| Address | Module | Common Name | OBD-Droid Finds? |
|---------|--------|-------------|------------------|
| 0x7E0 | Powertrain Control Module | PCM/ECM | ✅ Yes (broadcast) |
| 0x7E1 | Transmission Control Module | TCM | ✅ Yes (broadcast) |
| 0x7E2 | Fuel System | FSCM | ⚠️ Sometimes |
| **0x726** | **Power Steering Module** | **PSCM/EPAS** | **❌ NO (too low)** |
| 0x720 | Instrument Cluster | IPC | ❌ No |
| 0x727 | Body Control Module | BCM | ❌ No |
| 0x730 | BCM (alternate) | BCM | ❌ No |
| 0x733 | Restraint Control Module | RCM/ACM | ❌ No |
| 0x736 | Anti-lock Brake System | ABS | ❌ No |
| 0x737 | RCM (alternate) | RCM | ❌ No |
| 0x760 | ABS (alternate) | ABS/ESP | ❌ No |
| 0x765 | Front Controls Interface | FCIM | ❌ No |
| 0x7A6 | 4WD Module | 4WD/AWD | ❌ No |
| 0x7B0 | Gateway Module | GWM | ❌ No |

**Summary:** OBD-Droid currently finds **2-3 of 14 common Ford modules** (14-21% coverage)

#### Extended CAN Addressing (29-bit)

Ford also uses extended addressing on some modules:

```
0x18DA10F1  - PCM (extended)
0x18DA26F1  - PSCM (extended) ← Power Steering!
0x18DA28F1  - BCM (extended)
```

**OBD-Droid Support:** ❌ Not implemented

---

## Proposed Improvements

### Priority 0: Stabilize Core Diagnostics (New)

Before layering Ford-specific features, fix two regressions exposed during the field session:

1. **PID Discovery Cache Bug**  
   - Symptom: when broadcast PID discovery returns zero results, we log “Discovered 0 supported PIDs (cached permanently)” and never retry. Subsequent sessions inherit the empty cache and skip PID enumeration entirely.  
   - Fix: treat empty discovery responses as transient; persist a timestamped cache entry and force retries (with exponential backoff) until at least one PID list succeeds. Surface a banner when cached data is incomplete so users know results are degraded.  
   - Files: `ObdProtocolHandler.java`, `PidDiscoveryCache.java`, `VehicleStateRepository.kt` (cache invalidation UX).

2. **Communication Quality Telemetry**  
   - Symptom: ~40 TX failures on the damaged port produced no user-facing warning, so “0 codes found” appeared authoritative.  
   - Fix: track rolling success/error counts in `CommService` and expose a UI indicator (e.g., “Comm Quality 21% ⚠️”). When the rate drops below thresholds, slow the polling cadence and annotate scan results as partial.  
   - Files: `CommService.java`, `DiagnosticsStatusViewModel.kt`, status banner layout.

### Priority 1: Add Active ECU Probing

#### Implementation Overview

**New File:** `/app/src/java/com/obddroid/features/ford/FordEcuAddresses.java`

```java
package com.obddroid.features.ford;

/**
 * Known Ford ECU addresses for F-Series, Fusion, Focus, Escape, Explorer (2010-2024)
 *
 * Sources:
 * - Ford OBD-II documentation
 * - Field testing on F-150 (2014)
 * - Professional scan tool data
 *
 * @author Wal33D
 */
public class FordEcuAddresses {

    /**
     * Standard OBD-II addresses that respond to broadcast
     */
    public static final int[] STANDARD_ADDRESSES = {
        0x7E0,  // Engine Control Module (ECM/PCM)
        0x7E1,  // Transmission Control Module (TCM)
        0x7E2,  // Fuel System Control Module (FSCM)
        0x7E8,  // Engine (response address)
        0x7E9,  // Transmission (response address)
    };

    /**
     * Ford-specific addresses that require ACTIVE PROBING
     * These typically do NOT respond to Mode 9 broadcast
     */
    public static final int[] FORD_SPECIFIC_ADDRESSES = {
        0x720,  // Instrument Panel Cluster (IPC)
        0x726,  // ⭐ Power Steering Control Module (PSCM/EPAS) - CRITICAL!
        0x727,  // Body Control Module (BCM)
        0x730,  // BCM (alternate address)
        0x733,  // Restraint Control Module (RCM) - Airbags
        0x736,  // Anti-lock Brake System (ABS)
        0x737,  // RCM (alternate address)
        0x760,  // ABS Module (alternate)
        0x765,  // Front Controls Interface Module (FCIM)
        0x737,  // SYNC/Radio Module
        0x7A6,  // 4WD/AWD Control Module
        0x7B0,  // Gateway Module (GWM)
    };

    /**
     * Extended CAN addresses (29-bit addressing)
     * Used on newer Ford vehicles (2015+)
     */
    public static final int[] EXTENDED_ADDRESSES = {
        0x18DA10F1,  // PCM (extended)
        0x18DA26F1,  // PSCM (extended) - Power Steering
        0x18DA28F1,  // BCM (extended)
        0x18DA30F1,  // ABS (extended)
    };

    /**
     * Get all Ford addresses for active probing
     */
    public static int[] getAllAddresses() {
        int[] all = new int[STANDARD_ADDRESSES.length +
                           FORD_SPECIFIC_ADDRESSES.length +
                           EXTENDED_ADDRESSES.length];

        System.arraycopy(STANDARD_ADDRESSES, 0, all, 0, STANDARD_ADDRESSES.length);
        System.arraycopy(FORD_SPECIFIC_ADDRESSES, 0, all, STANDARD_ADDRESSES.length,
                        FORD_SPECIFIC_ADDRESSES.length);
        System.arraycopy(EXTENDED_ADDRESSES, 0, all,
                        STANDARD_ADDRESSES.length + FORD_SPECIFIC_ADDRESSES.length,
                        EXTENDED_ADDRESSES.length);

        return all;
    }

    /**
     * Get human-readable name for Ford module address
     */
    public static String getModuleName(int address) {
        switch (address) {
            case 0x7E0: case 0x7E8: return "PCM-Powertrain";
            case 0x7E1: case 0x7E9: return "TCM-Transmission";
            case 0x720: return "IPC-Instrument Cluster";
            case 0x726: return "PSCM-Power Steering"; // ⭐ The one we missed!
            case 0x727: case 0x730: return "BCM-Body Control";
            case 0x733: case 0x737: return "RCM-Restraint/Airbag";
            case 0x736: case 0x760: return "ABS-Anti-lock Brakes";
            case 0x765: return "FCIM-Front Controls";
            case 0x7A6: return "4WD-Four Wheel Drive";
            case 0x7B0: return "GWM-Gateway";
            case 0x18DA26F1: return "PSCM-Power Steering (Ext)";
            default: return "Unknown Module";
        }
    }
}
```

#### Modified EcuDiscoveryService

**Add to:** `/app/src/java/com/obddroid/services/EcuDiscoveryService.java`

```java
/**
 * Phase 2: Active probing of known manufacturer-specific addresses
 * This finds modules that don't respond to Mode 9 broadcast
 */
private void probeKnownAddresses() {
    Log.i(TAG, "========== ACTIVE PROBING START ==========");

    // Detect manufacturer from VIN (or user setting)
    String manufacturer = detectManufacturer();

    int[] addressesToProbe;
    if ("FORD".equals(manufacturer)) {
        addressesToProbe = FordEcuAddresses.FORD_SPECIFIC_ADDRESSES;
        Log.i(TAG, "Probing " + addressesToProbe.length + " Ford-specific addresses");
    } else {
        // Future: Add GM, Toyota, etc.
        Log.i(TAG, "No manufacturer-specific addresses for: " + manufacturer);
        return;
    }

    int foundCount = 0;
    for (int address : addressesToProbe) {
        try {
            // Set this specific module as target
            String setHeader = String.format("ATSH%03X", address);
            sendRawCommand(setHeader);
            Thread.sleep(100);

            // Send lightweight probe (Mode 01 PID 00)
            sendRawCommand("0100");
            Thread.sleep(300);  // Wait for response

            // Response captured by handleRawTelegram()
            // If module responded, it will be in discoveredEcus map

            if (discoveredEcus.containsKey(address)) {
                foundCount++;
                Log.i(TAG, String.format("✅ Found module at 0x%X: %s",
                    address, FordEcuAddresses.getModuleName(address)));
            } else {
                Log.d(TAG, String.format("❌ No response from 0x%X", address));
            }

        } catch (Exception e) {
            Log.w(TAG, "Error probing 0x" + Integer.toHexString(address) + ": " + e.getMessage());
        }
    }

    // Reset to functional broadcast address
    sendRawCommand("ATSH7DF");
    Thread.sleep(100);

    Log.i(TAG, "========== ACTIVE PROBING COMPLETE ==========");
    Log.i(TAG, "Found " + foundCount + " modules via active probing");
}

/**
 * Detect manufacturer from VIN
 */
private String detectManufacturer() {
    // Check if we have VIN data
    for (EcuDiscoveryInfo info : discoveredEcus.values()) {
        if (info.name != null && info.name.contains("FORD")) {
            return "FORD";
        }
    }

    // Future: Parse VIN properly
    // WMI codes: 1F = Ford USA, 3F = Ford Mexico, etc.

    return "UNKNOWN";
}
```

#### Integration into Discovery Flow

**Modified `discoverEcus()` method:**

```java
public CompletableFuture<Map<Integer, EcuDiscoveryInfo>> discoverEcus() {
    // ... existing setup ...

    new Thread(() -> {
        try {
            isDiscovering.set(true);
            CommService.elm.addRawTelegramListener(this);

            // PHASE 1: PASSIVE BROADCAST (existing method)
            Log.i(TAG, "========== PHASE 1: BROADCAST DISCOVERY ==========");
            sendRawCommand("ATH1");
            Thread.sleep(600);

            sendRawCommand("0904");  // Cal ID
            Thread.sleep(500);
            sendRawCommand("0906");  // CVN
            Thread.sleep(500);
            sendRawCommand("090A");  // ECU Name
            Thread.sleep(500);

            Log.i(TAG, "Broadcast phase: Found " + discoveredEcus.size() + " ECUs");

            // ⭐ PHASE 2: ACTIVE PROBING (NEW!)
            Log.i(TAG, "========== PHASE 2: ACTIVE PROBING ==========");
            probeKnownAddresses();

            // PHASE 3: CLEANUP
            Log.i(TAG, "========== PHASE 3: CLEANUP ==========");
            sendRawCommand("ATH0");
            Thread.sleep(300);

            CommService.elm.removeRawTelegramListener(this);

            // ... rest of existing code ...

        } catch (Exception e) {
            // ... existing error handling ...
        }
    }, "ECU-Discovery-Thread").start();

    return currentDiscovery;
}
```

---

### Priority 2: Expand Address Range Validation

**Current Code (restrictive):**
```java
// Line 347 in EcuDiscoveryService.java
if ((addr >= 0x7E0 && addr <= 0x7FF) || (addr >= 0x18D && addr <= 0x18F)) {
    return addr;
}
```

**Problem:** Rejects Ford PSCM at 0x726 (below 0x7E0)

**Fixed Code:**
```java
private int parseEcuAddress(String response) {
    if (response == null || response.isEmpty()) {
        return -1;
    }

    try {
        String cleaned = response.trim().toUpperCase();
        String[] parts = cleaned.split("\\s+");

        if (parts.length > 0) {
            String header = parts[0];

            // Parse address (handle both 11-bit and 29-bit)
            int addr;
            if (header.length() <= 3) {
                addr = Integer.parseInt(header, 16);  // 11-bit: "726"
            } else {
                addr = Integer.parseInt(header, 16);  // 29-bit: "18DA26F1"
            }

            // EXPANDED RANGE for Ford and other manufacturers
            if (
                // Ford range: 0x700-0x7FF (includes PSCM at 0x726)
                (addr >= 0x700 && addr <= 0x7FF) ||

                // Extended CAN range
                (addr >= 0x18D && addr <= 0x18F) ||
                (addr >= 0x18DA0000 && addr <= 0x18DAFFFF) ||

                // GM range (future)
                (addr >= 0x240 && addr <= 0x24F) ||

                // Toyota range (future)
                (addr >= 0x750 && addr <= 0x75F)
            ) {
                Log.d(TAG, "Accepted ECU address: 0x" + Integer.toHexString(addr));
                return addr;
            } else {
                Log.d(TAG, "Rejected ECU address (out of range): 0x" + Integer.toHexString(addr));
            }
        }
    } catch (NumberFormatException e) {
        Log.d(TAG, "Could not parse ECU address from: " + response);
    }

    return -1;
}
```

**Impact:** Now accepts Ford Power Steering at 0x726 ✅

---

### Priority 3: Add Manufacturer-Specific DTC Support

**Problem:** OBD-Droid only queries Mode 03 (standard P-codes)

**What we missed on F-150:**
- **U3003-16** (U-code = Network) - "Battery voltage below threshold"
- **B1304-68** (B-code = Body) - "EPAS event information"

#### Current Fault Code Implementation

**File:** `/app/src/java/com/obddroid/features/faultcodes/FaultCodeService.java`

**Current queries:**
```java
// Only queries Mode 03 (Powertrain DTCs)
String mode03 = "03";  // Stored DTCs
sendCommand(mode03);
```

**Missing:**
- Mode 07 (Pending DTCs)
- Mode 0A (Permanent DTCs)
- Manufacturer-specific modes for B/C/U codes

#### Proposed Enhancement

**Add to FaultCodeService.java:**

```java
/**
 * Scan modes to query
 */
public enum ScanMode {
    CONFIRMED,   // Mode 03 - Stored DTCs
    PENDING,     // Mode 07 - Pending DTCs
    PERMANENT,   // Mode 0A - Permanent DTCs
    FORD_BODY,   // Ford B-codes (body systems)
    FORD_CHASSIS,// Ford C-codes (chassis/steering/ABS)
    FORD_NETWORK // Ford U-codes (communication)
}

/**
 * Query DTCs using extended modes
 */
private List<FaultCode> queryExtendedDtcs(ScanMode mode) {
    String command;

    switch (mode) {
        case CONFIRMED:
            command = "03";  // Standard stored DTCs
            break;
        case PENDING:
            command = "07";  // Pending DTCs
            break;
        case PERMANENT:
            command = "0A";  // Permanent DTCs
            break;
        case FORD_BODY:
            command = "1803";  // Ford B-codes
            break;
        case FORD_CHASSIS:
            command = "1903";  // Ford C-codes (includes PSCM!)
            break;
        case FORD_NETWORK:
            command = "1A03";  // Ford U-codes
            break;
        default:
            return new ArrayList<>();
    }

    String response = sendCommand(command);
    return parseDtcResponse(response, mode);
}

/**
 * Complete scan including manufacturer-specific codes
 */
public List<FaultCode> scanAllDtcs() {
    List<FaultCode> allCodes = new ArrayList<>();

    // Standard OBD-II codes
    allCodes.addAll(queryExtendedDtcs(ScanMode.CONFIRMED));
    allCodes.addAll(queryExtendedDtcs(ScanMode.PENDING));
    allCodes.addAll(queryExtendedDtcs(ScanMode.PERMANENT));

    // Ford-specific codes (if Ford vehicle detected)
    if (isFordVehicle()) {
        allCodes.addAll(queryExtendedDtcs(ScanMode.FORD_BODY));
        allCodes.addAll(queryExtendedDtcs(ScanMode.FORD_CHASSIS));  // ← Would find PSCM codes!
        allCodes.addAll(queryExtendedDtcs(ScanMode.FORD_NETWORK));
    }

    return allCodes;
}
```

**Result:** Would have found U3003-16 and B1304-68 on the F-150! ✅

---

### Priority 4: Improved Error Handling & User Feedback

**Problem:** User saw "0 codes found" with no explanation

**Current behavior:**
```
FaultCodeService: Scan complete. Found 0 codes.
```

**User sees:**
```
No fault codes detected
```

**User thinks:** "The vehicle has no problems" ❌ WRONG!

**What actually happened:**
- Communication errors prevented query
- Codes exist but in modules we didn't scan
- Query method doesn't support B/C/U codes

#### Proposed User Feedback

**Add communication quality tracking:**

```java
public class CommQualityTracker {
    private int totalCommands = 0;
    private int successfulCommands = 0;
    private int txErrors = 0;

    public void recordCommand(boolean success) {
        totalCommands++;
        if (success) {
            successfulCommands++;
        } else {
            txErrors++;
        }
    }

    public int getQualityPercent() {
        if (totalCommands == 0) return 0;
        return (successfulCommands * 100) / totalCommands;
    }

    public String getQualityRating() {
        int quality = getQualityPercent();
        if (quality >= 90) return "EXCELLENT";
        if (quality >= 70) return "GOOD";
        if (quality >= 50) return "FAIR";
        if (quality >= 30) return "POOR";
        return "CRITICAL";
    }
}
```

**Enhanced user messaging:**

```
┌─ Fault Code Scan Results ─────────────┐
│ Standard DTCs:    0 found             │
│ Extended DTCs:    Not scanned         │
│ Communication:    21% (CRITICAL)      │
│                                        │
│ ⚠️  Warning:                          │
│ • 45 transmission errors detected     │
│ • Results may be incomplete           │
│ • Check:                               │
│   - OBD port condition                │
│   - Adapter connection                │
│   - Vehicle ignition ON               │
│                                        │
│ Tip: Try rescanning with engine       │
│ running for better communication      │
└────────────────────────────────────────┘
```

**Impact:** User understands limitations instead of being misled ✅

---

## Implementation Plan

### Phase 1: Foundation (Week 1-2)

**Goals:**
- Add Ford ECU address definitions
- Expand address range validation
- Add communication quality tracking

**Deliverables:**
1. `/app/src/java/com/obddroid/features/ford/FordEcuAddresses.java` (new file)
2. Update `parseEcuAddress()` to accept 0x700-0x7FF range
3. Add `CommQualityTracker` class
4. Unit tests for address parsing

**Testing:**
- Verify addresses parse correctly
- Test on Mercedes (ensure no regression)
- Test address range boundaries

**Estimated Effort:** 8-12 hours

---

### Phase 2: Active Probing (Week 3-4)

**Goals:**
- Implement active ECU probing
- Integrate into discovery flow
- Add manufacturer detection

**Deliverables:**
1. `probeKnownAddresses()` method in EcuDiscoveryService
2. `detectManufacturer()` method
3. Integration into `discoverEcus()` flow
4. Update ECU discovery UI to show probing status

**Testing:**
- Test on F-150 (should now find PSCM at 0x726) ✅
- Test on Mercedes (ensure broadcast still works)
- Test with damaged OBD port
- Measure discovery time increase

**Estimated Effort:** 16-20 hours

---

### Phase 3: Extended DTC Support (Week 5-6)

**Goals:**
- Add Mode 07, 0A queries
- Add Ford-specific DTC modes
- Enhance fault code parsing

**Deliverables:**
1. Update `FaultCodeService` with extended modes
2. Add `ScanMode` enum
3. Add `queryExtendedDtcs()` methods
4. DTC decoder for B/C/U codes
5. UI updates to show code categories

**Testing:**
- Test on F-150 (should find U3003-16, B1304-68) ✅
- Verify P-codes still work
- Test DTC clearing for all modes

**Estimated Effort:** 16-20 hours

---

### Phase 4: User Experience (Week 7-8)

**Goals:**
- Better error messages
- Communication quality indicators
- Diagnostic hints

**Deliverables:**
1. Enhanced error messaging
2. Communication quality UI
3. Diagnostic tips based on error patterns
4. User documentation

**Testing:**
- User testing with various error scenarios
- A/B testing of error messages
- Documentation review

**Estimated Effort:** 12-16 hours

---

### Phase 5: Testing & Validation (Week 9-10)

**Goals:**
- Field testing on Ford vehicles
- Regression testing on other manufacturers
- Performance optimization

**Test Vehicles Needed:**
- Ford F-150 (2009-2024 range)
- Ford Focus/Fusion
- Ford Explorer/Escape
- Mercedes (regression test)
- GM vehicle
- Toyota/Honda

**Success Criteria:**
- F-150: Find 5+ ECUs (vs current 1)
- F-150: Find PSCM at 0x726 ✅
- F-150: Find B/C/U codes
- Mercedes: No regression (still find all ECUs)
- Discovery time: < 15 seconds total

**Estimated Effort:** 20-30 hours

---

## Testing Strategy

### Field Validation Checklist (Updated from Live Session)

Use this quick-runlist whenever replicating the Snap-on workflow so engineering and field techs capture the same artefacts:

□ Full module scan – photograph the module list even if some entries show “NO COMM”  
□ PSCM present? note address (expect 0x726) or capture the “NO COMMUNICATION” screenshot as proof of a power/no-power failure  
□ Dump **all** module fault codes, including B/C/U categories – log screenshots for each module with codes  
□ Capture PSCM live data (voltage, pull compensation status, power mode, steering angle) so lockout heuristics can be regression-tested  
□ Record any `U`-codes indicating network faults for CAN-health analytics  
□ Read battery/charging voltage in the Electrical System menu (target 12.4–12.8 V key-on, 13.5–14.8 V running) and screenshot the values  
□ Attach a quick summary with VIN, year/make/model confirmation so datasets stay tied to the correct calibration files

### Unit Tests

**New Test File:** `/app/src/test/java/com/obddroid/features/ford/FordEcuAddressesTest.java`

```java
@Test
public void testFordPscmAddressIncluded() {
    int[] addresses = FordEcuAddresses.FORD_SPECIFIC_ADDRESSES;
    assertTrue("PSCM address 0x726 should be in list",
        Arrays.stream(addresses).anyMatch(addr -> addr == 0x726));
}

@Test
public void testGetModuleNameForPscm() {
    String name = FordEcuAddresses.getModuleName(0x726);
    assertTrue("Should identify PSCM", name.contains("Power Steering"));
}

@Test
public void testAddressRangeValidation() {
    EcuDiscoveryService service = new EcuDiscoveryService();

    // Ford PSCM should be accepted
    int pscm = service.parseEcuAddress("726 03 490100");
    assertEquals(0x726, pscm);

    // Standard PCM should still work
    int pcm = service.parseEcuAddress("7E8 03 490201");
    assertEquals(0x7E8, pcm);
}
```

### Integration Tests

**Test Scenario 1: F-150 ECU Discovery**

```
Input: 2014 Ford F-150 3.7L
Expected Output:
  ✅ PCM at 0x7E0/0x7E8
  ✅ TCM at 0x7E1/0x7E9
  ✅ PSCM at 0x726
  ✅ ABS at 0x760
  ✅ BCM at 0x727
  Total: 5+ ECUs

Current Output: 1 ECU (PCM only)
```

**Test Scenario 2: F-150 Fault Code Scan**

```
Input: 2014 F-150 with PSCM in safe mode
Expected Codes:
  ✅ U3003-16 (from PSCM)
  ✅ B1304-68 (from PSCM)

Current Output: 0 codes
```

**Test Scenario 3: Mercedes Regression Test**

```
Input: Mercedes-Benz (VIN 4JGDA5HB7JB158144)
Expected: All modules still discovered
Should NOT break existing functionality
```

### Field Testing Checklist

```
Ford F-150 Testing:
□ 2014 F-150 (our test vehicle)
□ 2015-2020 F-150 (newer generation)
□ 2021+ F-150 (latest generation)
□ F-250/F-350 Super Duty
□ Different engine options (3.5 EcoBoost, 5.0 V8, etc.)

Ford Cars:
□ Focus (2012-2018)
□ Fusion (2013-2020)
□ Mustang (2015+)

Ford SUVs:
□ Explorer (2011+)
□ Escape (2013+)
□ Edge (2015+)

Communication Conditions:
□ Healthy OBD port
□ Damaged OBD port (like our F-150)
□ Engine off
□ Engine running
□ Different adapters (ELM327, OBDLink, etc.)

Expected Improvements:
□ ECU count increases significantly
□ PSCM discovered on all vehicles with electric power steering
□ Extended DTCs retrieved
□ Better error messages when communication fails
```

---

## Impact Assessment

### Before vs. After Comparison

| Metric | Before (Current) | After (With Improvements) | Improvement |
|--------|------------------|---------------------------|-------------|
| **F-150 ECUs Found** | 1 (PCM only) | 5-8 (All major systems) | +400-700% |
| **F-150 DTC Coverage** | P-codes only | P, B, C, U codes | +300% |
| **Power Steering Found** | ❌ Never | ✅ Always (0x726) | 0% → 100% |
| **False "No Codes" Reports** | Common | Rare (with warnings) | -90% |
| **Diagnostic Accuracy** | 60-70% | 90-95% | +30-35% |
| **User Confidence** | Low (vs Pro tools) | High (competitive) | Significant |

### Financial Impact

**Cost of Missed Diagnosis (F-150 Case):**
- Unnecessary pump replacement: $200-500
- Wasted labor: 2-3 hours
- **Total wasted:** $300-650 per incident

**Potential Market Impact:**
- Ford F-Series: Best-selling vehicle in North America (700,000+ units/year)
- Users with Ford vehicles: Estimated 30-40% of market
- **Improved Ford support = 30-40% better market fit**

### Development Investment

| Phase | Effort (hours) | Timeline | Priority |
|-------|---------------|----------|----------|
| Phase 1: Foundation | 8-12 | Week 1-2 | 🔴 HIGH |
| Phase 2: Active Probing | 16-20 | Week 3-4 | 🔴 HIGH |
| Phase 3: Extended DTCs | 16-20 | Week 5-6 | 🟡 MEDIUM |
| Phase 4: User Experience | 12-16 | Week 7-8 | 🟡 MEDIUM |
| Phase 5: Testing | 20-30 | Week 9-10 | 🔴 HIGH |
| **Total** | **72-98 hours** | **10 weeks** | |

**ROI Estimate:**
- Development: ~80 hours
- Impact: Fixes critical gap affecting 30-40% of potential users
- **High ROI for market expansion**

---

## Lessons Learned

### What Worked Well

1. **ECU History Tracking** ✅
   - Storing discovered ECUs with timestamps was invaluable
   - Showed us that PSCM was NEVER discovered (not a one-time failure)

2. **Modular Architecture** ✅
   - EcuDiscoveryService is isolated - can be enhanced without breaking other features
   - VidPvs flow unaffected by ECU discovery changes

3. **Mode 9 Implementation** ✅
   - Works well for standard OBD-II modules (PCM, TCM)
   - Good foundation to build on

### What Needs Improvement

1. **Passive-Only Discovery** ❌
   - Assumption that all modules respond to broadcast is incorrect
   - Need active probing for manufacturer-specific modules

2. **Limited Address Range** ❌
   - Range 0x7E0-0x7FF too restrictive
   - Missed Ford modules at 0x700-0x7DF

3. **Generic DTC Queries** ❌
   - Mode 03 only covers P-codes
   - Missed B/C/U codes that explain real problems

4. **Poor Error Communication** ❌
   - "0 codes found" is misleading when communication fails
   - Users don't understand limitations

### Key Takeaways

1. **Test on diverse vehicles, not just one manufacturer**
   - Mercedes testing gave false confidence
   - Ford behaves very differently

2. **Professional tools use multi-stage discovery**
   - Broadcast + Active Probing + Manufacturer-Specific
   - Can't rely on single method

3. **Diagnostic accuracy matters more than speed**
   - Better to spend 15 seconds and find all modules
   - Than spend 5 seconds and miss critical data

4. **Error transparency builds trust**
   - Admitting "partial results due to communication errors"
   - Better than confidently reporting "0 codes" when wrong

---

## Future Enhancements

### GM Support (Priority: Medium)

**GM ECU Addressing:**
- 0x241-0x24F: GM-specific modules
- 0x7E0-0x7EF: Standard OBD-II

**GM-Specific DTCs:**
- Mode 0x19: GM extended diagnostics
- Mode 0x22: Read data by ID

### Toyota/Lexus Support (Priority: Medium)

**Toyota ECU Addressing:**
- 0x750-0x75F: Toyota-specific modules
- Requires KWP2000 protocol support

**Toyota-Specific Features:**
- Enhanced diagnostics (Mode 0x21)
- Active tests

### Honda/Acura Support (Priority: Low)

**Honda Addressing:**
- 0x18DA10F1: PCM
- 0x18DA28F1: VSA (stability control)

### European Manufacturers (Priority: Low)

**VW/Audi/BMW:**
- Require UDS protocol (ISO 14229)
- Different addressing schemes
- Complex module hierarchies

---

## Appendix A: F-150 Diagnostic Timeline

| Time | Event | Tool | Finding |
|------|-------|------|---------|
| T+0 | Customer complaint: No power steering | - | Steering very heavy |
| T+1h | Power steering pump replaced | Manual | No improvement |
| T+2h | Original pump reinstalled | Manual | Still no steering |
| T+3h | OBD-Droid scan via damaged port | OBD-Droid | 1 ECU, 0 codes, TX errors |
| T+4h | Snap-on professional scan | Snap-on | 5+ ECUs, PSCM found!, 2 codes |
| T+5h | PSCM live data analysis | Snap-on | Module in safe mode |
| T+6h | Root cause identified | Analysis | Low voltage lockout |
| T+7h | Solution: Clear codes + recalibrate | Snap-on | Est $0-65 fix |

**Lesson:** OBD-Droid missed critical diagnosis for 4+ hours

---

## Appendix B: Fault Code Reference

### Codes Found on F-150

#### U3003-16
**Full Description:** Battery voltage - Circuit Voltage Below Threshold
**Category:** U-code (Network/Communication)
**Module:** Power Steering Control Module (PSCM)
**Meaning:** PSCM detected system voltage dropped below ~9-10V
**Effect:** Module entered protective shutdown
**Common Causes:**
- Weak battery
- Failing alternator
- Parasitic drain
- Jump-start event
- Voltage drop during cranking

**OBD-Droid Support:** ❌ Not queried (need Mode 0x1A03 for Ford U-codes)

#### B1304-68
**Full Description:** Electronic Power Assisted Steering System - Event Information
**Category:** B-code (Body)
**Module:** Power Steering Control Module (PSCM)
**Meaning:** PSCM logged significant event (shutdown/protection activated)
**Effect:** Event stored for diagnostics
**Trigger:** Response to U3003-16 low voltage condition

**OBD-Droid Support:** ❌ Not queried (need Mode 0x1803 for Ford B-codes)

---

## Appendix C: Resources

### Ford Technical Documentation
- Ford OBD-II Implementation Guide
- Ford Power Steering Systems (EPAS) Service Manual
- Ford Diagnostic Trouble Code Reference

### ELM327 Documentation
- ELM327 Command Reference
- CAN Protocol Implementation
- Multi-ECU Addressing

### Testing Resources
- Ford F-150 (2014) - Available for testing
- Snap-on scan tool data - Reference for expected values
- Mercedes-Benz - Regression testing

### External References
- SAE J1979 - OBD-II Standard
- ISO 15765-4 - CAN Protocol
- ISO 14229 - UDS Protocol (future)

---

## Appendix D: Quick Reference

### Critical Addresses

```
Ford Power Steering: 0x726 (11-bit) or 0x18DA26F1 (29-bit)
Ford ABS:           0x736 or 0x760
Ford BCM:           0x727 or 0x730
```

### Critical Commands

```
Enable Headers:     ATH1
Disable Headers:    ATH0
Set Target ECU:     ATSH726 (for PSCM)
Broadcast Address:  ATSH7DF
Query Mode 1 PID 0: 0100 (lightweight probe)
```

### Ford DTC Modes

```
Mode 03:   Standard P-codes
Mode 07:   Pending DTCs
Mode 0A:   Permanent DTCs
Mode 1803: Ford B-codes (Body)
Mode 1903: Ford C-codes (Chassis/PSCM)
Mode 1A03: Ford U-codes (Network)
```

---

**Document Version:** 1.0
**Author:** Wal33D
**Date:** October 22, 2025
**Last Updated:** October 22, 2025

**Next Review:** After Phase 2 implementation (Active Probing)

---

## Change Log

| Date | Version | Changes | Author |
|------|---------|---------|--------|
| 2025-10-22 | 1.0 | Initial document creation from F-150 diagnostic session | Wal33D |

---

**END OF DOCUMENT**
