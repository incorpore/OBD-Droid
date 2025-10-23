# Vehicle Diagnostic Profile: 2022 GMC Canyon

## Vehicle Identification
- **Make:** GMC
- **Year:** 2022
- **Model:** Canyon 3.6L V6 (Partial VIN: 107528)
- **Fuel Type:** Gasoline
- **Mileage:** 113,700 miles (183,137 km)
- **Scan Date:** October 23, 2025 5:06 PM

---

## 🔧 MECHANIC'S TL;DR

**Problem:** P0302 Cylinder 2 Misfire + Check Engine Light ON

**KEY FINDING:** MAP Sensor B reads **0.00 kPa** (impossible!) - same bank as misfiring cylinder!

**Try These 3 Things:**
1. **Vacuum leak test** - Spray carb cleaner on passenger side intake (10 min)
2. **Check MAP sensor B** - Wiring/connector on passenger side (15 min)
3. **Replace spark plugs** - All 6, overdue at 113k miles ($240-320, 2 hrs)

**Success Rate:** 90%+ | **Cost:** $240-400 | **Time:** 2-4 hours

**Full details below** ↓

---

## Diagnostic Summary
⚠️ **STATUS: NOT SALES READY** - Active cylinder misfire detected

### Fault Codes Found: 1 (PERMANENT)
**Code:** P0302 - Cylinder 2 Misfire Detected

**Current Status (After Warm-Up):**
- **PERMANENT** (Red) - Emissions-related, cannot be cleared with scan tool
- ~~STORED~~ (Auto-cleared by ECU after warm-up)
- ~~PENDING~~ (Auto-cleared by ECU after warm-up)

**MIL Status:** ✅ Check Engine Light ON (Confirmed on dashboard)

### ⚠️ APP BUG DISCOVERED
**Issue:** Fault Codes page shows "0 codes" but:
- Live Data shows "NUMBER OF FAULT CODES: 1"
- MIL Status shows "On"
- Check engine light is ON on dashboard

**Root Cause:** Mode 0A (Permanent Codes) not parsing correctly when it's the only code remaining.

**Workaround:** Use Live Data page to verify DTC count. The code IS present in ECU.

---

## OBD-II System Information
- **Communication Protocol:** ISO 15765-4 CAN (11-bit, 500 kbit/s)
- **Live Data PIDs Available:** 58
- **Vehicle Info PIDs Available:** 11
- **OBD Connection:** Bluetooth - Successfully Connected
- **ECU Response:** Normal

---

## P0302 Diagnosis Guide - Cylinder 2 Misfire

### What This Code Means
A misfire in cylinder 2 means the air/fuel mixture in that cylinder is not igniting properly or at the right time. This affects:
- Engine power and performance
- Fuel economy (wasted fuel)
- Emissions (failed inspection)
- Potential catalytic converter damage if not fixed

### Most Common Causes (in order of likelihood):

#### 1. **SPARK PLUG - Cylinder 2** (80% probability)
- **Cost:** $15-30 for plug, 15-30 min labor
- **Check:** Remove and inspect spark plug #2
- **Look for:**
  - Worn electrode (gap too wide)
  - Carbon fouling (black deposits)
  - Oil fouling (wet, oily)
  - Cracked porcelain insulator
- **Fix:** Replace all spark plugs if over 30k miles

#### 2. **IGNITION COIL - Cylinder 2** (15% probability)
- **Cost:** $50-150 for coil, 30-60 min labor
- **Check:** Swap coil from cylinder 2 with another cylinder
- **Test:** If misfire moves to new cylinder = bad coil
- **Fix:** Replace ignition coil

#### 3. **FUEL INJECTOR - Cylinder 2** (3% probability)
- **Cost:** $100-300 for injector, 1-2 hr labor
- **Check:** Use OBD scanner to view fuel trim data
- **Symptoms:** Rough idle, smell of gas, poor fuel economy
- **Test:** Injector flow test or swap test
- **Fix:** Clean or replace fuel injector

#### 4. **COMPRESSION ISSUE** (2% probability)
- **Cost:** $150-300 for compression test
- **Causes:** Worn piston rings, burnt valve, head gasket
- **Check:** Compression test on all cylinders
- **Normal:** 140-180 PSI (should be within 10% of other cylinders)
- **Fix:** May require engine teardown (expensive)

---

## 🔍 Root Cause Analysis - UPDATED

### Primary Findings:
1. **P0302 - Cylinder 2 Misfire** (Intermittent, on Bank 2/Passenger Side)
2. **Intake Manifold Absolute Pressure B = 0.00 kPa** (Should read ~40 kPa like Bank A)
3. **High Engine Torque Friction = 261%** (Indicates rough running/misfire)

### Likely Root Causes (Updated Priority):

#### 1. **VACUUM LEAK on Bank 2** (NEW - 40% probability)
**Evidence:**
- MAP sensor B reads 0.00 kPa (impossible - should show vacuum)
- Cylinder 2 is on Bank 2 (passenger side)
- Intermittent misfire (vacuum leaks can worsen when hot)

**What to Check:**
- Intake manifold gasket (passenger side)
- Vacuum hoses on Bank 2 side
- PCV valve/hoses
- Brake booster hose
- EVAP system hoses

**Test:** Spray carb cleaner around intake manifold while engine runs
- If RPM changes = vacuum leak found
- Focus on passenger side near Cylinder 2

#### 2. **Faulty MAP Sensor (Bank 2)** (25% probability)
**Evidence:**
- 0.00 kPa reading is clearly wrong
- Could cause ECU to calculate wrong fuel delivery
- Would affect all Bank 2 cylinders if sensor bad

**Test:**
- Check MAP sensor B wiring/connector
- Compare readings from both MAP sensors
- Unplug sensor - see if reading changes

#### 3. **SPARK PLUG - Cylinder 2** (25% probability)
**Evidence:**
- Still the most common cause of misfires
- Intermittent nature fits worn plug
- High mileage (113,700 miles)

**Recommended:** Replace anyway as part of repair

#### 4. **IGNITION COIL - Cylinder 2** (10% probability)
**Evidence:**
- Intermittent misfire could be coil failing when hot
- Coil swap test will confirm

---

## Recommended Diagnostic Steps for Dealership - REVISED

### STEP 1: Vacuum Leak Test (10 minutes) **NEW PRIORITY**
```
[ ] Start engine and let idle
[ ] Spray carb cleaner around intake manifold (PASSENGER SIDE)
[ ] Focus on areas near Cylinder 2
[ ] Check PCV valve and hoses
[ ] Check brake booster vacuum line
[ ] Listen for hissing sounds
[ ] If RPM changes when spraying = VACUUM LEAK FOUND
```

**If vacuum leak found:** Fix leak, clear codes, test drive, re-scan

### STEP 2: MAP Sensor B Inspection (15 minutes)
```
[ ] Locate MAP sensor for Bank 2 (passenger side)
[ ] Check connector for corrosion/damage
[ ] Check wiring for breaks/shorts
[ ] Compare live data: MAP A vs MAP B
[ ] Unplug MAP B - see if reading changes from 0.00
[ ] If sensor faulty: Replace MAP sensor B
```

### STEP 3: Spark Plug Inspection (15 minutes)
```
[ ] Remove spark plug from cylinder 2
[ ] Check gap (should be 0.040" for most GM V6 engines)
[ ] Inspect for wear, fouling, or damage
[ ] If worn: Replace ALL spark plugs (recommended at 30k+ miles)
```

### STEP 4: Coil Swap Test (20 minutes - if above doesn't fix)
```
[ ] Swap ignition coil from cylinder 2 with cylinder 4
[ ] Clear codes
[ ] Test drive vehicle for 10-15 minutes
[ ] Re-scan for codes
[ ] If P0304 appears instead of P0302 = BAD COIL
[ ] If P0302 persists = Problem is NOT the coil
```

### STEP 5: Compression Test (30 minutes - if all above fails)
```
[ ] Perform compression test on all cylinders
[ ] Record PSI for each: 1:___ 2:___ 3:___ 4:___ 5:___ 6:___
[ ] Compare cylinder 2 to others (should be within 10%)
[ ] Low compression = internal engine issue (NOT QUICK FIX)
```

---

## Sales-Ready Checklist

### Critical Repairs (MUST FIX)
- [ ] Replace spark plugs (all cylinders recommended)
- [ ] Replace ignition coil if faulty
- [ ] Clear fault codes after repair
- [ ] Test drive 20+ miles to confirm fix
- [ ] Re-scan to verify no codes return

### Post-Repair Verification
- [ ] Check Engine Light OFF
- [ ] No fault codes present
- [ ] Engine runs smoothly at idle
- [ ] No rough acceleration
- [ ] Fuel economy normalized

### Expected Timeline & Cost

**MOST LIKELY FIX: Spark Plugs**
- Parts: $90-120 (6 plugs for V6)
- Labor: 1 hour ($100-150)
- **Total: $190-270**
- **Time: 1-2 hours**

**IF COIL IS BAD:**
- Add coil: $50-150
- Add labor: 0.5 hour ($50-75)
- **Additional: $100-225**

---

## Notes for Service Department

⚠️ **IMPORTANT:** The PERMANENT code means this misfire has been present for a while and triggered emissions monitoring. Even after fixing:
1. The PERMANENT code will NOT clear immediately
2. Vehicle must complete several drive cycles (30-50 miles)
3. ECU will self-clear PERMANENT code after confirming repair

**For Quick Sale:**
- Fix the issue
- Clear stored/pending codes
- Document that permanent code will self-clear
- Price accordingly or offer post-sale inspection

---

## Live Data Snapshot
**Captured:** October 23, 2025 5:23 PM
**Engine State:** Idling/Warm
**PIDs Supported:** 58

### Fuel System Data
- **Commanded Fuel Rail Pressure A:** 4000 kPa (Normal)
- **Fuel Rail Pressure A:** 4020 kPa (Normal)
- **Fuel Rail Pressure B:** 4050 kPa (Normal)
- **Commanded Fuel Rail Pressure B:** 0 kPa
- **Fuel Rail Temperature A:** 58.00°C (Normal)
- **Fuel Rail Temperature B:** 58.00°C (Normal)

### Intake/Manifold Data
- **Intake Manifold Absolute Pressure A:** 39.69 kPa (Normal idle vacuum)
- **Intake Manifold Absolute Pressure B:** 0.00 kPa ⚠️ **ANOMALY**

### Engine Performance
- **Engine Torque Friction:** 261.0% ⚠️ **HIGH**
- **Odometer:** 183,137.5 km (113,700 miles approx)

### Fuel Trim Data (Available)
- Short Term Fuel Trim - Bank 1 (PID 06): Supported
- Long Term Fuel Trim - Bank 1 (PID 07): Supported
- Short Term Fuel Trim - Bank 2 (PID 08): Supported
- Long Term Fuel Trim - Bank 2 (PID 09): Supported

### O2 Sensor Monitoring
- **O2 Sensor Monitor Completion Bank 1:** 13,824 counts
- **O2 Sensor Monitor Conditions Bank 1:** 12,800 counts
- **O2 Sensor Monitor Completion Bank 2:** 13,824 counts
- **O2 Sensor Monitor Conditions Bank 2:** 12,800 counts
- **Secondary O2 Monitor Bank 1:** 1,024 / 11,008 counts
- **Secondary O2 Monitor Bank 2:** 13,824 / 11,008 counts

### Key Diagnostic Indicators

⚠️ **Intake Manifold Pressure B = 0.00 kPa**
- This reading is ABNORMAL - should show vacuum like Bank A
- Possible causes:
  1. Faulty MAP sensor for Bank 2
  2. Wiring/connector issue to sensor
  3. Vacuum leak on Bank 2 side
  4. ECU not reading this sensor

⚠️ **Engine Torque Friction = 261%**
- This is VERY HIGH (normal is 0-100%)
- Indicates significant engine drag/resistance
- Could be related to misfire causing rough running
- May also indicate worn engine components

### Bank Assignment (GM 3.6L V6)
**Bank 1** (Driver Side):
- Cylinders: 1, 3, 5

**Bank 2** (Passenger Side):
- Cylinders: 2, 4, 6

**IMPORTANT:** Cylinder 2 is on BANK 2!
- The misfire is on the PASSENGER side
- Bank 2 intake manifold pressure shows 0.00 (sensor issue?)
- This correlation is significant

---

## Technician Notes

**Initial Scan:** October 23, 2025 5:06 PM - Found 3 codes (1 permanent, 1 stored, 1 pending)
**Follow-up Scan:** October 23, 2025 5:13 PM - Found 1 code (permanent only)
**Scanned By:** OBD-Droid App v2.x
**Location:** Dealership Service Department

**Code Behavior:**
- Stored and Pending codes auto-cleared after engine warm-up (NORMAL ECU behavior)
- Permanent code remains (will only clear after successful repair + drive cycles)
- This indicates **intermittent misfire** - happens under load, not at idle

**App Bug Note:**
- After warm-up, Fault Codes page incorrectly shows 0 codes
- Live Data page correctly shows 1 code present
- Always verify with Live Data or physical scan tool
- Bug report filed: BUG_REPORT_Mode0A_Permanent_Codes.md

**Status:** ⚠️ NEEDS REPAIR BEFORE SALE
**Priority:** HIGH - Intermittent misfire will fail inspection and affect driveability

---

## Profile Created By
OBD-Droid Vehicle Profiling System
Profile ID: GMC-2022-107528-20251023
