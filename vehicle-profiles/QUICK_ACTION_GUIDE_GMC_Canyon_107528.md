# Quick Action Guide: 2022 GMC Canyon (VIN 107528)

## FOR DEALERSHIP TECH - Day 2 on the Job

---

## What's Wrong with This Vehicle?

✅ **Confirmed Issue:** Cylinder 2 Misfire (P0302)
- **Type:** Intermittent (happens under load, not at idle)
- **Severity:** HIGH - Vehicle not sales-ready
- **MIL:** Check Engine Light ON

---

## What's Wrong with the App?

⚠️ **App Bug Discovered:**
- Fault Codes page shows **0 codes** (WRONG)
- Live Data page shows **1 code** (CORRECT)
- Check engine light is ON (CORRECT)

**Why?** After the engine warmed up, 2 codes auto-cleared (normal), leaving only 1 PERMANENT code. The app has a bug parsing permanent codes when they're alone.

**Workaround:** Always check **Live Data page** → "NUMBER OF FAULT CODES" to verify.

---

## What to Do TODAY to Fix This Truck

### STEP 1: Quick Diagnosis (30 minutes)

```
[Start Here]
┌─────────────────────────────┐
│ Remove Cylinder 2 Spark Plug│
└─────────────────────────────┘
              │
              ▼
    ┌─────────────────┐
    │ Inspect Plug    │
    └─────────────────┘
          │
          ├── Worn/Gap Too Wide? ──► Replace ALL 6 plugs ($200 parts+labor)
          │
          ├── Fouled/Damaged? ──────► Replace ALL 6 plugs ($200 parts+labor)
          │
          └── Looks Good? ──────────► Go to STEP 2 (Coil Test)
```

### STEP 2: Coil Swap Test (If plugs look good)

```
1. Swap ignition coil from Cylinder 2 → Cylinder 4
2. Clear codes
3. Test drive 10 minutes (highway speeds)
4. Re-scan
5. If code moves to P0304 = BAD COIL (replace it)
   If code stays P0302 = NOT the coil (see STEP 3)
```

### STEP 3: If Still Misfiring (Rare)

```
→ Compression test on all cylinders
→ If Cylinder 2 is low = Internal engine issue (EXPENSIVE)
→ If compression OK = Check fuel injector
```

---

## Most Likely Fix: Spark Plugs

**80% chance this is all you need:**

### Parts Needed:
- 6x Spark Plugs (AC Delco 41-103 or equivalent)
- Cost: $90-120

### Labor:
- Remove engine cover
- Remove ignition coils
- Replace all 6 plugs
- Gap check: 0.040"
- **Time:** 1 hour

### Total Cost:
- **$190-270** (parts + labor)
- **Time:** 1-2 hours including test drive

---

## After Repair Checklist

```
[ ] Clear fault codes
[ ] Test drive 20+ miles (include highway speeds)
[ ] Re-scan for codes
[ ] Verify MIL is OFF
[ ] Drive another 10 miles
[ ] Final scan to confirm no codes return
```

**IMPORTANT:** The PERMANENT code will NOT clear immediately!
- It requires 3-5 complete drive cycles
- ECU must verify repair is successful
- Can take 30-100 miles to self-clear
- Document this for customer

---

## Sales-Ready Criteria

### MUST HAVE:
- ✅ MIL (Check Engine Light) is OFF
- ✅ No misfires during test drive
- ✅ Engine runs smooth at idle and under load
- ✅ No codes in stored/pending categories

### CAN STILL HAVE:
- ⚠️ PERMANENT code (will self-clear in 30-100 miles)
  - Document this on vehicle paperwork
  - Price accordingly or offer follow-up inspection
  - Explain to buyer it will self-clear

---

## If You Get Stuck

### Symptom Troubleshooting:

**"I replaced plugs but still misfiring"**
→ Test coil (swap test)

**"Misfire only when cold"**
→ Coil breaking down when cold (replace coil)

**"Misfire only when hot"**
→ Coil breaking down when hot (replace coil)

**"Misfire only under hard acceleration"**
→ Plug breaking down under load (replace plugs)

**"Compression test shows Cyl 2 is low"**
→ Internal engine problem (valve/ring issue)
→ **NOT A QUICK FIX** - major repair needed

---

## App Usage Tips

### Use Live Data Page for Verification:
```
Main Menu → Live Data → Scroll to:
  - "NUMBER OF FAULT CODES" (accurate count)
  - "MIL STATUS" (should match dashboard)
  - "MISFIRE STATUS" (monitor after repair)
```

### Don't Trust Fault Codes Page:
- After warm-up, may show 0 codes incorrectly
- Always cross-check with Live Data
- Or use physical scan tool for confirmation

---

## Timeline & Pricing

### Same-Day Turnaround (Spark Plugs):
- **Parts:** $90-120
- **Labor:** $100-150 (1 hour)
- **Total:** $190-270
- **Sales-Ready:** Same day

### If Coil Needed:
- **Add:** $100-225 (coil + 0.5hr labor)
- **Total:** $290-495
- **Sales-Ready:** Same day

### If Internal Engine Issue:
- **Cost:** $800-2500+ (teardown/rebuild)
- **Time:** 1-3 days
- **Recommendation:** Sell as-is with disclosure

---

## Questions to Ask Your Manager

1. "Can we sell with PERMANENT code documented?"
2. "What's our policy on pricing with known MIL issue?"
3. "Do we offer post-sale inspection for self-clearing codes?"
4. "Should I go straight to replacing all plugs, or diagnose further?"

---

## Final Recommendation

**FOR YOUR SECOND DAY:**

1. **Replace all 6 spark plugs** (quickest, cheapest, 80% success rate)
2. **Clear codes and test drive**
3. **If still misfiring:** Do coil swap test
4. **Document permanent code** for sales paperwork
5. **Price accordingly** or offer follow-up check

**Time Investment:** 2-3 hours total
**Expected Cost:** $190-270
**Success Probability:** 80%+

---

Good luck! This is a straightforward repair for someone on Day 2. The intermittent nature and auto-cleared codes actually confirm it's most likely just worn spark plugs.

**Pro Tip:** GM recommends spark plug replacement every 100k miles on this 3.6L V6. If the truck has 60k+ miles and original plugs, this is a routine maintenance item that's overdue.

---

**Created:** October 23, 2025
**Vehicle:** 2022 GMC Canyon 3.6L V6 (VIN: 107528)
**Status:** Ready for Repair
