# Vehicle Profiles
**OBD-Droid Baseline & Diagnostic Records**

Central hub for vehicle profile reports captured with OBD-Droid. Use this folder to document baseline scans, diagnostic snapshots, and post-repair confirmations for every stock unit.

---

## 📂 Directory Structure

```
vehicle-profiles/
├── README.md                        ← Overview + template
├── 2022_GMC_Canyon_VIN107528.md      ← Example profile (diagnostic)
└── 2017_Nissan_Frontier_VIN778459.md ← Example profile (baseline)
```

---

## 🚀 Quick Start

1. **Scan the vehicle** with OBD-Droid (Modes 01/02/03/06/07/09 as needed) and gather screenshots/logs.
2. **Create a new profile file** named `YYYY_Make_Model_VINXXXXXX.md` (use last 6 of VIN for uniqueness).
3. **Copy the template section below** (`## 📋 Vehicle Profile Template`) into the new file.
4. **Fill in each section** with data from the scan, highlighting abnormal readings and action items.
5. **Update over time**: add notes for repairs, follow-up scans, and final verification.

Tip: Keep supporting artifacts (screenshots, logs) in a matching folder under `vehicle-profiles/<VIN>/` if needed.

---

## 📋 Vehicle Profile Template

# Vehicle Profile: [YYYY Make Model]
**Complete OBD-Droid Baseline Scan**

> **Profile Created:** [Date]
> **Profile Type:** `BASELINE` | `DIAGNOSTIC` | `PRE-SALE` | `POST-REPAIR`
> **VIN:** [Last 6 digits]

---

## 📋 Vehicle Identification
*Auto-populated from OBD-Droid Mode 09*

| Field | Value | Source |
|-------|-------|--------|
| **VIN** | [Full 17-digit VIN] | Mode 09 PID 02 |
| **Year** | [YYYY] | VIN decode |
| **Make** | [Manufacturer] | VIN decode |
| **Model** | [Model name] | VIN decode |
| **Trim** | [Trim level] | VIN decode |
| **Engine** | [Displacement + Type] | VIN decode |
| **Cylinders** | [4/6/8] | Mode 01 PID 1F |
| **Fuel Type** | [Gas/Diesel/Hybrid] | Mode 01 PID 51 |
| **Transmission** | [Auto/Manual] | Inferred |
| **Drive Type** | [FWD/RWD/AWD] | VIN decode |
| **Mileage** | [Current odometer] | Mode 01 PID 31 / Manual |
| **Scan Date** | [Date + Time] | System timestamp |

**OBD Protocol:** [ISO 15765-4 CAN / ISO 9141-2 / etc.]
**Adapter:** [ELM327 type/version]

---

## 🔌 ECU Information
*From OBD-Droid Mode 09 & ECU Module Scan*

### Primary ECU (Engine Control)
```
ECU Address:        0x7E8
ECU Name:           [From Mode 09 PID 0A]
Calibration ID:     [From Mode 09 PID 04]
Calibration ID 2:   [From Mode 09 PID 04 if multi-part]
CVN:                [From Mode 09 PID 06]
ECU Software:       [Version if available]
```

### Additional ECUs Discovered
*From ECU Module Scan (Main Menu → ECU Modules)*

| Address | Name | Calibration ID | Responses |
|---------|------|----------------|-----------|
| 0x7E8 | ECM - EngineControl | [Cal ID] | [Count] |
| 0x7E9 | TCM - Transmission | [Cal ID] | [Count] |
| 0x7EA | [Module name] | [Cal ID] | [Count] |

**Total ECUs Found:** [X]

**Log Command:**
```bash
adb logcat -d | grep -E "ECU.*Discovery|Mode.*09" > logs/[VIN]_ecu_scan.log
```

---

## 🚨 Fault Code Scan
*From OBD-Droid Mode 03, 07, 0A*

### Confirmed Fault Codes (Mode 03)
```
[Count] codes stored

P0XXX - [Description] - [Status: ACTIVE/INTERMITTENT/OLD]
P0XXX - [Description] - [Status: ACTIVE/INTERMITTENT/OLD]

OR

✓ No confirmed fault codes
```

### Pending Codes (Mode 07)
```
[Count] pending codes

P0XXX - [Description] - Not yet confirmed

OR

✓ No pending codes
```

### Permanent Codes (Mode 0A)
```
[Count] permanent codes

P0XXX - [Description] - Cannot be cleared until repaired

OR

✓ No permanent codes
```

**MIL (Check Engine Light):** ON / OFF
**Codes Since Last Clear:** [Count]

**Log Command:**
```bash
adb logcat -d | grep -E "OBD_SVC_READ_CODES|PENDINGCODES|PERMACODES" > logs/[VIN]_fault_codes.log
```

---

## 📸 Freeze Frame Data
*From OBD-Droid Mode 02 (if codes present)*

**Freeze Frame for DTC: [P0XXX]**

| Parameter | Value at Fault | Normal Range | Status |
|-----------|----------------|--------------|--------|
| Engine RPM | [RPM] | 600-750 idle | ✓ / ⚠️ |
| Vehicle Speed | [km/h] | Varies | ✓ / ⚠️ |
| Engine Load | [%] | 15-25% idle | ✓ / ⚠️ |
| Coolant Temp | [°C] | 80-95°C | ✓ / ⚠️ |
| Fuel System | [Status] | CL/OL | ✓ / ⚠️ |
| [Critical param] | [Value] | [Range] | ⚠️ |

**OR**

✓ No freeze frames stored (no fault codes present)

**Log Command:**
```bash
adb logcat -d | grep -E "OBD_SVC_FREEZEFRAME|Frame.*0" > logs/[VIN]_freeze_frame.log
```

---

## 📊 Live Data Baseline (Mode 01)
*From OBD-Droid Main Menu → Live Data*

### Engine Parameters (Idle - Engine Warmed)

**Conditions:** Engine warmed to operating temp, idle, A/C off, all accessories off

| PID | Parameter | Value | Normal Range | Status |
|-----|-----------|-------|--------------|--------|
| 0x0C | Engine RPM | [RPM] | 600-750 | ✓ / ⚠️ |
| 0x0D | Vehicle Speed | 0 km/h | 0 | ✓ |
| 0x05 | Coolant Temp | [°C] | 80-95°C | ✓ / ⚠️ |
| 0x0F | Intake Air Temp | [°C] | 20-45°C | ✓ / ⚠️ |
| 0x11 | Throttle Position | [%] | 2-5% | ✓ / ⚠️ |
| 0x10 | MAF Sensor | [g/s] | 3-6 g/s | ✓ / ⚠️ |
| 0x0B | Intake MAP | [kPa] | 30-45 kPa | ✓ / ⚠️ |
| 0x04 | Engine Load | [%] | 15-25% | ✓ / ⚠️ |
| 0x0E | Timing Advance | [°] | 10-20° | ✓ / ⚠️ |

### Fuel System

| PID | Parameter | Value | Normal Range | Status |
|-----|-----------|-------|--------------|--------|
| 0x03 | Fuel System Status | [CL/OL] | CL (warmed) | ✓ / ⚠️ |
| 0x06 | ST Fuel Trim Bank 1 | [%] | -10 to +10% | ✓ / ⚠️ |
| 0x07 | LT Fuel Trim Bank 1 | [%] | -10 to +10% | ✓ / ⚠️ |
| 0x08 | ST Fuel Trim Bank 2 | [%] | -10 to +10% | ✓ / ⚠️ |
| 0x09 | LT Fuel Trim Bank 2 | [%] | -10 to +10% | ✓ / ⚠️ |
| 0x2F | Fuel Tank Level | [%] | 0-100% | Info |
| 0x0A | Fuel Pressure | [kPa] | 300-400 kPa | ✓ / ⚠️ |

### O2 Sensors (Idle)

| Bank | Sensor | Voltage | Normal Range | Status |
|------|--------|---------|--------------|--------|
| 1 | 1 (Upstream) | [V] | 0.1-0.9V cycling | ✓ / ⚠️ |
| 1 | 2 (Downstream) | [V] | 0.6-0.8V steady | ✓ / ⚠️ |
| 2 | 1 (Upstream) | [V] | 0.1-0.9V cycling | ✓ / ⚠️ |
| 2 | 2 (Downstream) | [V] | 0.6-0.8V steady | ✓ / ⚠️ |

**O2 Sensor Response:** [Fast/Slow/Lazy/Stuck]

### Misfire Counters

| Cylinder | Count | Status |
|----------|-------|--------|
| Cylinder 1 | [Count] | ✓ / ⚠️ |
| Cylinder 2 | [Count] | ✓ / ⚠️ |
| Cylinder 3 | [Count] | ✓ / ⚠️ |
| Cylinder 4 | [Count] | ✓ / ⚠️ |
| Cylinder 5 | [Count] | ✓ / ⚠️ |
| Cylinder 6 | [Count] | ✓ / ⚠️ |

**Total Misfires:** [Sum]
**Misfire %:** [Percentage]

### Additional Sensors

| Parameter | Value | Normal Range | Status |
|-----------|-------|--------------|--------|
| Battery Voltage | [V] | 13.5-14.5V (running) | ✓ / ⚠️ |
| Catalyst Temp B1S1 | [°C] | 400-900°C | ✓ / ⚠️ |
| Catalyst Temp B2S1 | [°C] | 400-900°C | ✓ / ⚠️ |
| EGR Position | [%] | 0% idle | ✓ / ⚠️ |
| Evap Purge | [%] | 0-5% idle | ✓ / ⚠️ |

**PIDs Supported:** [Total count] PIDs

**Log Command:**
```bash
adb logcat -d | grep -E "OBD_SVC_DATA|PID.*0x" > logs/[VIN]_live_data_idle.log
```

---

## 🚗 Live Data Under Load (Driving)

### Highway Cruise (60 km/h, Steady Speed)

| Parameter | Value | Expected | Status |
|-----------|-------|----------|--------|
| Engine RPM | [RPM] | 1800-2200 | ✓ / ⚠️ |
| Engine Load | [%] | 30-50% | ✓ / ⚠️ |
| Throttle Position | [%] | 15-25% | ✓ / ⚠️ |
| MAF Sensor | [g/s] | 12-20 g/s | ✓ / ⚠️ |
| ST Fuel Trim B1 | [%] | -10 to +10% | ✓ / ⚠️ |
| ST Fuel Trim B2 | [%] | -10 to +10% | ✓ / ⚠️ |

### Acceleration (Wide Open Throttle Snapshot)

| Parameter | Value | Expected | Status |
|-----------|-------|----------|--------|
| Engine RPM | [RPM] | 4000-6000 | ✓ / ⚠️ |
| Engine Load | [%] | 90-100% | ✓ / ⚠️ |
| Throttle Position | [%] | 90-100% | ✓ / ⚠️ |
| MAF Sensor | [g/s] | 50-80 g/s | ✓ / ⚠️ |
| Timing Advance | [°] | 10-35° | ✓ / ⚠️ |

**Log Command:**
```bash
adb logcat -d | grep -E "Speed.*60|Load.*[5-9][0-9]" > logs/[VIN]_live_data_driving.log
```

---

## 🔬 Emissions Monitor Readiness (Mode 06)
*From OBD-Droid Main Menu → Emissions*

### Monitor Status

| Monitor | Supported | Complete | Notes |
|---------|-----------|----------|-------|
| Misfire | Yes/No | ✓ / ✗ | |
| Fuel System | Yes/No | ✓ / ✗ | |
| Components | Yes/No | ✓ / ✗ | CCM |
| Catalyst | Yes/No | ✓ / ✗ | |
| Heated Catalyst | Yes/No | ✓ / ✗ | |
| Evaporative System | Yes/No | ✓ / ✗ | Often slow to complete |
| Secondary Air | Yes/No | ✓ / ✗ | N/A on many vehicles |
| A/C Refrigerant | Yes/No | ✓ / ✗ | |
| O2 Sensor | Yes/No | ✓ / ✗ | |
| O2 Sensor Heater | Yes/No | ✓ / ✗ | |
| EGR System | Yes/No | ✓ / ✗ | |

**Monitors Complete:** [X] / [Y]
**Ready for Emissions Test:** YES / NO

**MIL Status:** [ON/OFF]
**Monitors Since Clear:** [Time/miles since codes cleared]

### IUMPR (In-Use Monitor Performance Ratio)

*If supported - shows how often monitors run vs opportunities*

| Monitor | Numerator | Denominator | Ratio |
|---------|-----------|-------------|-------|
| Catalyst Mon B1 | [Count] | [Count] | [Ratio] |
| Catalyst Mon B2 | [Count] | [Count] | [Ratio] |
| O2 Sensor B1S1 | [Count] | [Count] | [Ratio] |
| EVAP Monitor | [Count] | [Count] | [Ratio] |

**Log Command:**
```bash
adb logcat -d | grep -E "Emissions|Monitor.*Status|IUMPR" > logs/[VIN]_emissions.log
```

---

## 🧪 Test Results (Mode 05 & 06)
*From OBD-Droid if available*

### O2 Sensor Test Results (Mode 05)

| Sensor | Test ID | Min | Max | Current | Limit | Pass/Fail |
|--------|---------|-----|-----|---------|-------|-----------|
| B1S1 | [TID] | [V] | [V] | [V] | [Limit] | ✓ / ✗ |
| B1S2 | [TID] | [V] | [V] | [V] | [Limit] | ✓ / ✗ |
| B2S1 | [TID] | [V] | [V] | [V] | [Limit] | ✓ / ✗ |
| B2S2 | [TID] | [V] | [V] | [V] | [Limit] | ✓ / ✗ |

### Monitor Test Results (Mode 06)

| Monitor | Test | Value | Min | Max | Units | Status |
|---------|------|-------|-----|-----|-------|--------|
| Catalyst B1 | Efficiency | [Val] | [Min] | [Max] | % | ✓ / ✗ |
| Catalyst B2 | Efficiency | [Val] | [Min] | [Max] | % | ✓ / ✗ |
| EVAP | Leak Test | [Val] | [Min] | [Max] | [Unit] | ✓ / ✗ |
| EGR | Flow | [Val] | [Min] | [Max] | % | ✓ / ✗ |

---

## 📈 Historical Data (If Available)

### Previous Scans

| Date | Mileage | Codes | Notes |
|------|---------|-------|-------|
| [Date] | [Miles] | [Count] | [Brief note] |
| [Date] | [Miles] | [Count] | [Brief note] |

### Maintenance History

- [Date] - [Service performed]
- [Date] - [Service performed]

---

## 📝 Vehicle-Specific Notes

### Known Issues for This Model/Year
- [TSB reference if applicable]
- [Common problems for this vehicle]
- [Recalls to check]

### Observations from Scan
- [Any unusual readings]
- [Interesting findings]
- [Recommendations for monitoring]

### Baseline Health Assessment

**Overall:** ✅ EXCELLENT / 🟢 GOOD / 🟡 FAIR / 🟠 NEEDS ATTENTION / 🔴 POOR

**Reasoning:**
- [Summary of findings]
- [Key parameters in/out of range]
- [Any concerns or recommendations]

---

## 🔧 Recommended Actions

**Immediate:**
- [ ] [Action item if critical issues found]

**Near Term (1-3 months):**
- [ ] [Preventive maintenance recommendation]

**Monitor:**
- [ ] [Parameters to watch on future scans]

---

## 📊 Data Collection Summary

### Modes Captured

- [x] Mode 01 - Live Data (Idle + Driving)
- [x] Mode 02 - Freeze Frame (if codes present)
- [x] Mode 03 - Confirmed Fault Codes
- [x] Mode 05 - O2 Sensor Test Results
- [x] Mode 06 - Monitor Test Results
- [x] Mode 07 - Pending Codes
- [x] Mode 09 - Vehicle Information
- [x] Mode 0A - Permanent Codes
- [x] ECU Module Scan

### Total PIDs Logged: [Count]

### Log Files Generated
```
logs/[VIN]_ecu_scan.log
logs/[VIN]_fault_codes.log
logs/[VIN]_freeze_frame.log
logs/[VIN]_live_data_idle.log
logs/[VIN]_live_data_driving.log
logs/[VIN]_emissions.log
logs/[VIN]_complete_session.log
```

**Generate Complete Log:**
```bash
adb logcat -d | grep -E "ObdProt|Mode.*0[0-9]|OBD_SVC|ECU" > logs/[VIN]_complete_session.log
```

---

## 🎯 Profile Usage

**This profile can be used for:**
- ✅ Baseline reference for future diagnostics
- ✅ Pre-sale inspection documentation
- ✅ Post-repair verification
- ✅ Performance monitoring over time
- ✅ Identifying trends/degradation

**Next Profile Recommended:** [Date] or [Mileage]

---

**Profile Created By:** [Tech name]
**Profile Date:** [Date]
**Profile Version:** 1.0
**OBD-Droid Version:** [App version]

---

*Complete vehicle baseline captured using OBD-Droid - All modes scanned*
