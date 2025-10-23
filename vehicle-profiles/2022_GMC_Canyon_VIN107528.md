# Vehicle Diagnostic Profile: 2022 GMC Canyon

## Vehicle Identification
- **Make:** GMC
- **Year:** 2022
- **Model:** Canyon (Partial VIN: 107528)
- **Fuel Type:** Gasoline
- **Scan Date:** October 23, 2025 5:06 PM

---

## Diagnostic Summary
⚠️ **STATUS: NOT SALES READY** - Active cylinder misfire detected

### Fault Codes Found: 3
**Code:** P0302 - Cylinder 2 Misfire Detected

**Code Appears in Multiple States:**
1. **PERMANENT** (Red) - Emissions-related, cannot be cleared with scan tool
2. **STORED** (Orange) - Confirmed/active fault code
3. **PENDING** (Yellow) - Monitored condition

**MIL Status:** ✅ Check Engine Light ON

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

## Recommended Diagnostic Steps for Dealership

### STEP 1: Quick Visual Inspection (5 minutes)
```
[ ] Check for loose/damaged wires on ignition coil #2
[ ] Look for vacuum leaks near cylinder 2
[ ] Check air filter condition
[ ] Inspect spark plug boots for cracks/damage
```

### STEP 2: Spark Plug Inspection (15 minutes)
```
[ ] Remove spark plug from cylinder 2
[ ] Check gap (should be 0.040" for most GM V6 engines)
[ ] Inspect for wear, fouling, or damage
[ ] If worn: Replace ALL spark plugs (recommended at 30k+ miles)
```

### STEP 3: Coil Swap Test (20 minutes)
```
[ ] Swap ignition coil from cylinder 2 with cylinder 4
[ ] Clear codes
[ ] Test drive vehicle for 10-15 minutes
[ ] Re-scan for codes
[ ] If P0304 appears instead of P0302 = BAD COIL
[ ] If P0302 persists = Problem is NOT the coil
```

### STEP 4: Compression Test (30 minutes - if above fails)
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
*(Available on next connection - 58 PIDs supported)*

Supported data includes:
- Engine RPM, Speed, Coolant Temp
- Fuel System Status, Fuel Trim
- Throttle Position, MAF Sensor
- O2 Sensors, Catalyst Temp
- And 50+ more parameters

---

## Technician Notes

**Scan Date:** October 23, 2025
**Scanned By:** OBD-Droid App v2.x
**Location:** Dealership Service Department

**Status:** ⚠️ NEEDS REPAIR BEFORE SALE

---

## Profile Created By
OBD-Droid Vehicle Profiling System
Profile ID: GMC-2022-107528-20251023
