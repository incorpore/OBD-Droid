# Dealer Diagnostic Reports
**OBD-Droid In-House Shop Diagnostics**

Quick diagnostic reports for dealer shop use - diagnose → fix → parts → cost → time.

---

## 📂 Directory Structure

```
dealer-diag-reports/
├── TEMPLATE_diagnostic.md          ← Copy this for new vehicles
├── active/                         ← Currently diagnosing
│   └── Stock_XXX_YYYY_Make_Model.md
├── resolved/                       ← Completed repairs
│   └── Stock_XXX_YYYY_Make_Model.md
├── archived/                       ← Old records (1+ years)
└── screenshots/                    ← OBD-Droid screenshots
    └── Stock_XXX/
        ├── fault_codes.png
        ├── live_data.png
        └── final_clean.png
```

---

## 🚀 Quick Start

**1. Scan vehicle with OBD-Droid:**
```
Main Menu → Fault Codes (Mode 03, 07, 0A)
Main Menu → Live Data (Mode 01)
Main Menu → Emissions (Mode 06)
Tap code → Freeze Frame (Mode 02)
```

**2. Create report:**
```bash
cp TEMPLATE_diagnostic.md active/Stock_001_2017_Nissan_Frontier.md
```

**3. Fill in:**
- VIN (auto-decodes vehicle info)
- Fault codes from OBD scan
- Freeze frame data
- Live data abnormalities
- Diagnosis + repair options
- Parts sources & prices
- Time estimate

**4. Track repair:**
- Update as work progresses
- Screenshot clean scan when done
- Move to `resolved/` when complete

---

## 📱 OBD-Droid Data Collection

**6 Modes to Use:**
1. **Mode 03/07/0A** - Fault codes (confirmed, pending, permanent)
2. **Mode 02** - Freeze frame (parameters when fault occurred)
3. **Mode 01** - Live data (current real-time parameters)
4. **Mode 06** - Emissions (monitor readiness)
5. **Mode 09** - Vehicle info (VIN, calibration IDs)
6. **ECU Scan** - Multi-ECU discovery (advanced)

Each mode documented in template with:
- What it does
- How to use (app navigation)
- What to look for
- Log commands for detailed analysis

---

## 🔧 Repair Approach

**Option 1: Quick Fix** (try first)
- Cheap part replacement ($20-100)
- 15-30 minutes labor
- Test with OBD-Droid
- If works: Done! ✓
- If fails: Go to Option 2

**Option 2: Proper Fix** (if needed)
- Complete repair ($200-500)
- 3-6 hours labor
- Permanent solution
- Verify with OBD-Droid

---

## 💡 Example Report

See `active/2022_GMC_Canyon_P0302_Cylinder2_Misfire.md` for complete example.

**Shows:**
- Full OBD-Droid data collection (all 6 modes)
- Diagnosis correlation (code + freeze + live data)
- Quick fix: MAP sensor swap ($50, 30% success)
- Proper fix: Intake gasket ($470, 95% success)
- Parts from junkyard → Amazon → AutoZone
- Verification steps with OBD-Droid

---

## ✅ Pre-Lot Checklist

**Before selling vehicle, verify with OBD-Droid:**
- [ ] No fault codes (Mode 03/07/0A)
- [ ] MIL (Check Engine) OFF
- [ ] All live data normal (Mode 01)
- [ ] Emissions monitors READY (Mode 06)
- [ ] Test drive 10+ miles
- [ ] Screenshot clean scan

---

**Last Updated:** October 2025
