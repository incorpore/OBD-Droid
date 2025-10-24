# Vehicle Diagnostic Report
**Powered by OBD-Droid**

> **Report Date:** [Date]
> **Status:** `ACTIVE` | `RESOLVED` | `ARCHIVED`
> **Priority:** 🔴 CRITICAL | 🟠 HIGH | 🟡 MEDIUM | 🟢 LOW

---

## 📋 Vehicle Information
*Captured via OBD-Droid VIN Decoder*

| Field | Value |
|-------|-------|
| **Year** | [Auto-filled from VIN] |
| **Make** | [Auto-filled from VIN] |
| **Model** | [Auto-filled from VIN] |
| **VIN** | [Full VIN from app] |
| **Engine** | [From app vehicle profile] |
| **Mileage** | [Current odometer reading] |
| **Scan Date** | [Date of OBD-Droid scan] |

**OBD-Droid Profile:** [Link to vehicle-profiles/YYYY_Make_Model.md if exists]

---

## 🔍 Diagnosis Summary

### What is the Problem?
<!-- Brief 1-3 sentence summary -->

**Primary Symptoms:**
- [ ] Check Engine Light (MIL)
- [ ] Performance issues
- [ ] Unusual noises
- [ ] Vibration
- [ ] Fuel economy issues
- [ ] Emissions test failure
- [ ] Other: ___________

---

## 📱 OBD-Droid Data Capture

### Step 1: Fault Code Scan
*Main Menu → Fault Codes*

**Confirmed Codes (Mode 03):**
```
[DTC] - [Description from app]
[DTC] - [Description from app]
```

**Pending Codes (Mode 07):**
```
[DTC] - [Description] - Not confirmed yet
```

**Permanent Codes (Mode 0A):**
```
[DTC] - [Description] - Stored in ECU
```

**MIL Status:** ON / OFF
**Codes Since Last Clear:** [Count]

---

### Step 2: Freeze Frame Data
*Tap on fault code → View freeze frame*

**Captured when [DTC] occurred:**
```
Parameter              | Value at Fault
-----------------------|---------------
Engine Speed           | [RPM]
Vehicle Speed          | [km/h]
Engine Load            | [%]
Coolant Temp           | [°C]
Fuel System Status     | [Status]
[Key parameter]        | [Value] ← Abnormal!
```

**Screenshot:** [Path to OBD-Droid screenshot]

---

### Step 3: Live Data Analysis
*Main Menu → Live Data*

**Key Parameters Monitored:**
```
Parameter                  | Current | Normal Range | Status
---------------------------|---------|--------------|--------
Engine RPM                 | [Value] | 600-750      | ✓ / ⚠️
Coolant Temp               | [Value] | 80-95°C      | ✓ / ⚠️
MAF Sensor                 | [Value] | 3-6 g/s      | ✓ / ⚠️
MAP Sensor                 | [Value] | 30-45 kPa    | ✓ / ⚠️
Throttle Position          | [Value] | 2-5% idle    | ✓ / ⚠️
Fuel Trim Bank 1 (STFT)    | [Value] | -10 to +10%  | ✓ / ⚠️
Fuel Trim Bank 2 (STFT)    | [Value] | -10 to +10%  | ✓ / ⚠️
O2 Sensor B1S1             | [Value] | 0.1-0.9V     | ✓ / ⚠️
[Critical parameter]       | [Value] | [Range]      | ⚠️ ABNORMAL
```

**Observations:**
- [Abnormality 1 found in live data]
- [Abnormality 2 found in live data]

**Screenshot:** [Path to live data screenshot from app]

---

### Step 4: Emissions Monitor Status
*Main Menu → Emissions*

**Monitor Readiness:**
```
Monitor                | Supported | Complete
-----------------------|-----------|----------
Catalyst               | Yes       | ✓ / ✗
Heated Catalyst        | Yes       | ✓ / ✗
Evaporative System     | Yes       | ✓ / ✗
Secondary Air System   | No        | N/A
O2 Sensor              | Yes       | ✓ / ✗
O2 Sensor Heater       | Yes       | ✓ / ✗
EGR System             | Yes       | ✓ / ✗
```

**Incomplete Monitors:** [List any not ready - may indicate recent code clear or system issue]

**Screenshot:** [Path to emissions screen screenshot]

---

### Step 5: Vehicle History Check
*Main Menu → Vehicle History (if AutoCheck available)*

**Recall Status:**
- [Recall 1 - Status: Open/Completed]
- [Recall 2 - Status: Open/Completed]

**Service History Notes:**
- [Previous related repairs if known]
- [Maintenance records if available]

---

## 🕵️ Root Cause Analysis

### What Caused This Issue?

**Based on OBD-Droid data, the root cause is:**

[Detailed explanation linking the fault codes + freeze frame + live data to the actual problem]

**Data Evidence:**
1. **Fault Code [DTC]** indicates: [What the code means]
2. **Freeze Frame** shows: [Key parameter that was abnormal when fault occurred]
3. **Live Data** confirms: [Current abnormal readings that support diagnosis]
4. **Emissions Monitors** show: [Any incomplete/failed monitors related to issue]

**Primary Cause:**
[The actual failing component or system]

**Why it Failed:**
- [Contributing factor 1]
- [Contributing factor 2]
- [Contributing factor 3]

**Timeline:**
- **When did it start?** [Recent / Gradual / Sudden]
- **Frequency:** [Constant / Intermittent / Specific conditions]

---

## 💥 Damage Assessment

### What Damage Has Been Caused?

**Current Damage:**
| Component | Condition | Severity | Evidence from OBD-Droid |
|-----------|-----------|----------|-------------------------|
| [Component 1] | [Description] | 🔴/🟠/🟡 | [Fault code or data reading] |
| [Component 2] | [Description] | 🔴/🟠/🟡 | [Live data parameter] |
| [Component 3] | [Description] | 🔴/🟠/🟡 | [Freeze frame evidence] |

**Potential Future Damage if Not Repaired:**
- ⚠️ [Risk 1 - e.g., "Catalytic converter damage from misfires"]
- ⚠️ [Risk 2 - e.g., "Engine damage from lean condition"]
- ⚠️ [Risk 3]

**Safety Assessment:**
- [ ] Safe to drive normally
- [ ] Drive with caution (short trips only)
- [ ] Do not drive - tow recommended

---

## 🔧 Recommended Repairs

### What Needs to Be Done to Fix It?

---

### 💡 Quick Fix Option (Try This First!)

<!-- ONLY include if applicable - delete if complex diagnosis needed -->

**Simple Part Replacement to Try:**

**Component:** [Part name]
**Part Number:** [OEM / Aftermarket options]
**Difficulty:** ⭐⭐☆☆☆ [Easy/Moderate/Difficult]
**Time:** [Minutes/Hours]
**Cost:** $[Parts] + $[Labor if needed]

**Why try this first?**
Based on OBD-Droid data showing [specific reading], this part may be faulty.

**How to verify with OBD-Droid after replacement:**
1. Clear codes: *Fault Codes → Menu → Clear Codes*
2. Drive for [miles/time]
3. Re-scan with OBD-Droid
4. Check live data for [specific parameter]
   - **FIXED:** [Parameter] now reads [normal value]
   - **NOT FIXED:** [Parameter] still abnormal → See full repair below

**Success Rate:** 🎯 **[X]%**
- ✅ Fixes issue if: [Condition]
- ❌ Won't fix if: [Other causes]

**Cost:** $[Amount] vs $[Full repair cost]

---

### 🔨 Full Repair Solution

**Priority 1 - Immediate:**

1. **[Repair Item 1]**
   - **What:** [Description of repair]
   - **Why:** [Based on which OBD-Droid data]
   - **Parts:** [Part list] = $[Cost]
   - **Labor:** [Hours] @ $[Rate] = $[Cost]
   - **Total:** $[Subtotal]

2. **[Repair Item 2]**
   - **What:** [Description]
   - **Why:** [OBD evidence]
   - **Parts:** [List] = $[Cost]
   - **Labor:** [Hours] @ $[Rate] = $[Cost]
   - **Total:** $[Subtotal]

**Priority 2 - Recommended Soon:**
- [Related repair to prevent future issues]
- [Preventive maintenance while system is open]

**Priority 3 - Preventive:**
- [General maintenance items]

---

## 💰 Total Cost Estimate

| Item | Cost |
|------|------|
| **Parts** | $[Total parts] |
| **Labor** | $[Total labor] |
| **Shop Supplies** | $[Supplies] |
| **Tax** | $[Tax] |
| **GRAND TOTAL** | **$[Total]** |

**Alternative Options:**
- **Quick fix only:** $[Cost] (try part replacement first)
- **Full repair:** $[Cost] (recommended for permanent fix)
- **DIY:** $[Parts only] (if comfortable doing yourself)

---

## 📸 Documentation from OBD-Droid

### Screenshots Captured

**Fault Code Screen:**
- [Path to screenshot or embed]
- Shows: [DTCs detected]

**Freeze Frame:**
- [Path to screenshot]
- Shows: [Key parameters at fault moment]

**Live Data:**
- [Path to screenshot]
- Shows: [Abnormal readings]

**Emissions Status:**
- [Path to screenshot]
- Shows: [Monitor readiness]

**Vehicle Info:**
- [Path to vehicle profile screenshot]
- Shows: [VIN decode, engine info]

---

## ✅ Repair Verification with OBD-Droid

**Before Repair:**
- [x] Fault codes documented in app
- [x] Freeze frame captured
- [x] Live data screenshot taken
- [x] Emissions monitor status recorded
- [ ] Customer approved estimate

**After Repair - Verify with OBD-Droid:**

1. **Clear Codes**
   - *Fault Codes → Menu → Clear Codes*
   - All codes and freeze frames cleared ✓

2. **Check Live Data**
   - *Main Menu → Live Data*
   - [Parameter] now reads: [Normal value] ✓
   - All readings within normal range ✓

3. **Test Drive**
   - Drive for minimum [miles/time]
   - Include highway + city + idle
   - Monitor live data during drive

4. **Re-Scan for Codes**
   - *Main Menu → Fault Codes*
   - **No codes returned** ✓
   - MIL status: OFF ✓

5. **Check Emissions Monitors**
   - *Main Menu → Emissions*
   - All monitors complete ✓ (may need drive cycle)
   - Ready for emissions test ✓

6. **Document Final State**
   - Take screenshots of:
     - No fault codes screen
     - Normal live data
     - Complete emissions monitors
   - Save to resolved/ folder

---

## 📊 Diagnosis Confidence

Based on OBD-Droid data analysis:

**Confidence Level:** 🟢 High / 🟡 Medium / 🔴 Low

**Reasoning:**
- [Why this diagnosis is confident or uncertain]
- [What additional tests might be needed]
- [Any data that conflicts or is unclear]

**Recommended Next Steps:**
1. [Step 1]
2. [Step 2]
3. [Step 3]

---

## 📝 Technical Notes

**OBD-Droid Connection Info:**
- **Adapter:** [ELM327 type/model]
- **Protocol:** [ISO 15765-4 CAN / etc.]
- **Communication:** [Successful / Issues noted]

**Diagnostic Process:**
```
[Document the diagnostic logic flow]
1. Scanned fault codes → Found [DTC]
2. Checked freeze frame → Saw [parameter] = [abnormal value]
3. Monitored live data → Confirmed [parameter] still abnormal
4. Checked emissions → [Monitor] incomplete
5. Conclusion: [Component] is failing
```

**Known Issues / TSBs:**
- [Technical Service Bulletin reference if applicable]
- [Common problem on this vehicle if known]

**Special Considerations:**
- [Any unique aspects of this vehicle or repair]

---

## ⏱️ Action Plan

**Timeline:**
- [x] Initial scan with OBD-Droid completed
- [x] Data captured and analyzed
- [x] Diagnosis determined
- [ ] Customer approval for repairs
- [ ] Parts ordered ([X] days)
- [ ] Repair appointment scheduled
- [ ] Repair completed
- [ ] Post-repair OBD-Droid verification
- [ ] Final customer approval

**Follow-Up with OBD-Droid:**
- [ ] 50 miles - Re-scan (customer can do with app)
- [ ] 500 miles - Verify no codes returned
- [ ] Document final state in vehicle profile

---

**Report Generated by:** OBD-Droid App
**Technician:** [Name]
**Date:** [Date]
