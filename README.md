# OBD‑Droid Platform

OBD‑Droid is a full-stack vehicle intelligence platform that combines Android-based live diagnostics, fuel economy analytics, emissions compliance reporting, and AutoCheck-powered vehicle history. This README consolidates every engineering note, analysis artifact, and status report created to date.

---

## TL;DR – Where We Stand

- **Android app:** Production-build ready with Bluetooth OBD-II stack, VIN-aware dashboards, rich fuel economy tooling, and an emissions readiness screen validated on a 2018 Mercedes GLE.
- **Fuel economy:** New speed-density engine with calibration dialog, per-VIN preferences, and CSV trip logging (gal/h, MAP, IAT, VE, IMAP, synthetic MAF, calc method, calibration flag).
- **Emissions diagnostics:** All EPA monitors parsed from Mode 9 IUMPR data; one outstanding counting bug because “No Data” monitors are still marked available.
- **OBD service stability:** Deep comparison of Mode 9 vs. Fault Codes services uncovered five root causes for state flicker; phase-by-phase remediation plan defined.
- **Vehicle history:** AutoCheck integration in place; relies on companion TypeScript scraper/API with Playwright or official Experian endpoints.

Use this document as the single source for architecture, current feature status, key code locations, setup guides, validation procedures, and follow-up work.

---

## Repository Map

```
OBD-Droid/
├── app/                         # Android application source
│   └── src/java/com/obddroid/   # Core business logic
├── modules/
│   ├── nhtsa-vin-decoder/       # Multi-language VIN decoder toolkit
│   ├── dtc-database/            # Diagnostic trouble code database
│   └── automotive-logo-library/ # OEM branding assets + helpers
├── build.gradle | settings.gradle | gradle/  # Android build tooling
└── README.md                    # ← you are here (all other *.md consolidated)
```

---

## Feature Overview & Status

### Live Diagnostics
- **Communication stack:** `comm/services/CommService.java`, `core/obd/ObdProt.java`, `core/obd/ElmProt.java`
- **Current capabilities:** PID polling, Mode 9 VIN decode, fault-code retrieval, dashboard widgets with change listeners.
- **Known debt:** Fault code reader triggers CONNECTING ↔ NODATA flicker because of service context mismatch, missing termination, and aggressive retries (see “OBD Service Reliability”).

### Fuel Economy Suite (2025 refresh)
- **Vehicle preferences:** `vehicle/VehiclePreferences.java` stores tank size, VE calibration, per-VIN prompts.
- **Estimation stack:** `vehicle/VehicleManager.java` provides tank capacity heuristics based on body class, luxury brand, displacement, drivetrain, and electrification level.
- **FuelEconomyActivity highlights:**
  - Speed-density MAP-based calculation with ideal gas law (MAP, IAT, RPM, VE, displacement, stoich).
  - Fallback RPM/load estimation when sensors unavailable.
  - Calibration dialog: user enters miles and gallons to persist VE (50–130% range validation).
  - Preferences-driven tank capacity prompt with VIN mask logging.
  - Trip CSV logging (per second) capturing MAP_kPa, IAT_C, VE_%, IMAP, SyntheticMAF_g/s, CalcMethod (“MAP”, “Estimation-NoMAP”, “Estimation-InvalidMAP”), calibration state, plus throttle, range, time-to-empty.
- **Outstanding work:** None for calculation accuracy; consider hooking tank prompt into VehicleManager change listener (already done) and optionally surface calc method badge in UI.

### Emissions Readiness
- **Implementation:** `ui/activities/EmissionsActivity.java` parses Mode 9 PID 0x08 (IUMPR) and distinguishes continuous vs. non-continuous monitors.
- **Validated monitors:** Misfire, Fuel, CCM, Catalyst, O2 Sensor, EGR, EVAP, Secondary Air, O2 Heater (with “No Data” fallback). Mercedes GLE test data confirmed large completions (e.g., O2 sensors 522,580/673,600; catalyst 1,103,820/783,680).
- **UI:** Card-based layout with progress bars, status icons, ready/not-ready summary banner.
- **Known bug:** Monitors showing “No Data” are still counted as available, so the summary banner reports “3 monitors need drive cycle” instead of “1” (EVAP). Fix by tightening `isAvailable` logic when completions/conditions = 0.
- **Future enhancement:** Actively trigger Mode 1 PID 0x01 on page load rather than relying on cached data from other screens.

### Vehicle History (AutoCheck)
- **Client components:**
  - `vehicle/AutoCheckReport.java` – data model with helpers (`hasAccidents()`, etc.)
  - `services/AutoCheckService.java` – network calls, background threading, UI callbacks.
  - Dashboard card entry point with VIN prefill from `VehicleManager`.
- **Companion service requirements:**
  - Repo: `/path/to/autocheck-api`
  - Endpoints: `GET /health`, `POST /api/autocheck/lookup`, `POST /api/autocheck/decode`
  - Base URL (update to local IP for device testing): `http://192.168.0.153:3248`
  - Launch: `npm install && npm run dev`
  - Playwright scraping by default; Experian API drop-in supported.
- **Scraper TODOs:** Owner count from icon filename, usage text, odometer rollback detection, boolean status from icon alt attributes (e.g., `structural-off`).

---

## OBD Service Reliability – Executive Summary

A deep comparison between Mode 9 (OBD_SVC_VEH_INFO) and Fault Code services revealed five issues explaining why fault-code loads flicker the UI:

| # | Issue | File / Line | Severity | Impact |
|---|-------|-------------|----------|--------|
| 1 | `writeTelegram` uses `OBD_SVC_DATA` instead of requested service | `ObdProt.java:1483` | CRITICAL | Command queue confusion |
| 2 | No termination flag equivalent to `pidsWrapped` | `ElmProt.java:~825` | CRITICAL | Infinite polling loop |
| 3 | Fault-code PV updates lack `PvChangeEvent.PV_MODIFIED` | `ObdProt.java:1218` | HIGH | UI receives no update |
| 4 | Aggressive NODATA recovery with immediate retries | `ElmProt.java:1515-1569` | HIGH | CONNECTED ↔ NODATA flicker |
| 5 | Cache restore order unclear | `ObdProt.java:1635` | MEDIUM | Intermittent data loss |

**Remediation Plan**
1. **Phase 1 (1–2h, unblock UI):** Fix service context, add `faultCodesRequestComplete` counter, break loop when all three code services queried.
2. **Phase 2 (1–2h):** Emit `PV_MODIFIED`, verify cache restoration, add instrumentation to count status transitions.
3. **Phase 3 (3–5h):** Back-off retries, align InitializationManager polling, consider batching responses similar to Mode 9.

**Validation Metrics (post-fix targets):**
- Status transitions per load cycle: `< 5` (currently 15–20)
- Time in NODATA: `< 5%` (currently 30–50%)
- Visible flicker: none
- Data persistence: consistent across service switches

---

## Key Code Map

| Area | File | Notes |
|------|------|-------|
| OBD protocol state machine | `app/src/java/com/obddroid/core/obd/ObdProt.java` | `setService()`, `handleTelegram()`, caching |
| ELM adapter state management | `app/src/java/com/obddroid/core/obd/ElmProt.java` | `setStatus()`, NODATA recovery |
| Initialization orchestration | `app/src/java/com/obddroid/core/obd/InitializationManager.java` | Mode 9 + fault-code service sequencing |
| Fuel economy activity | `app/src/java/com/obddroid/ui/activities/FuelEconomyActivity.java` | Speed-density calc, VE calibration, trip logging |
| Emissions screen | `app/src/java/com/obddroid/ui/activities/EmissionsActivity.java` | IUMPR parsing, monitor availability |
| Vehicle preferences | `app/src/java/com/obddroid/vehicle/VehiclePreferences.java` | Tank size, VE calibration, per-VIN prompts |
| Vehicle manager | `app/src/java/com/obddroid/vehicle/VehicleManager.java` | VIN listeners, tank capacity heuristics |
| AutoCheck integration | `app/src/java/com/obddroid/services/AutoCheckService.java` | Companion API calls |

---

## Setup & Companion Services

### Prerequisites
- Android Studio / Gradle (JDK required for local builds).
- Bluetooth OBD-II adapter (e.g., ELM327) for on-vehicle testing.
- Node.js ≥ 18 for AutoCheck service.

### AutoCheck Companion Setup
```bash
cd /path/to/autocheck-api
npm install
cp .env.example .env   # Configure credentials if available
npm run dev
```
- Confirm readiness: `curl http://localhost:3248/health`
- From device, ensure `http://<laptop-ip>:3248/health` is reachable.
- Update base URL inside `AutoCheckService` when testing on device.

### Android Build & Install
```bash
./gradlew assembleDebug        # requires local JDK
./gradlew installDebug
```

---

## Validation Playbooks

### Fuel Economy
1. Connect to vehicle, open Fuel Economy screen.
2. On first VIN use, verify tank capacity dialog with estimated gallons (can adjust).
3. Confirm instant MPG stays within realistic bounds (20–40 MPG under cruise).
4. Record a trip, then stop – check `Documents/OBDroid/fuel_economy_trip_*.csv` for full diagnostic columns.
5. Use calibration dialog post fill-up to apply new VE; ensure snackbar confirmation.

### Emissions Readiness
1. Ensure Mode 9 data primed (visit Live Data or hook Mode 1 request).
2. Open Emissions screen; expect cards populated with ratios, icons, helpful text.
3. Verify summary banner counts (should go green once “No Data” monitors excluded).
4. Capture logs (`adb logcat | grep EmissionsActivity`) if parsing issues appear.

### OBD Fault Code Service (post-fix checklist)
1. Trigger fault code refresh from dashboard or initialization flow.
2. Monitor status transitions (`adb logcat | grep ElmProt` once instrumentation added).
3. Confirm PV_MODIFIED events update UI without manual refresh.

---

## Roadmap & Outstanding Items

| Area | Next Step | Owner | Effort |
|------|-----------|-------|--------|
| Fault codes | Implement termination flag + service context fix (Phase 1) | Engineering | 1–2h |
| Fault codes | Emit PV_MODIFIED & add state counters (Phase 2) | Engineering | 1h |
| Fault codes | Back-off retry logic (Phase 3) | Engineering | 2h |
| Emissions | Refine monitor availability to exclude “No Data” cards | Engineering | 30m |
| Emissions | Trigger Mode 1 PID 0x01 on page load | Engineering | 1h |
| Fuel economy | Optional calc method badge + UI polish | Product | 1h |
| Vehicle history | Complete scraper fixes (owner usage, rollback, booleans) | Backend | 2–3h |
| Vehicle history | Add PDF export / VIN scanner (stretch) | Product | TBD |

---

## Appendix – Key Metrics from Real Vehicle (2018 Mercedes-Benz GLE)

| Monitor | Status | Completions / Conditions | Notes |
|---------|--------|--------------------------|-------|
| Misfire | ✅ Ready | 51,360 / 51,360 | Continuous |
| Fuel System | ✅ Ready | 51,360 / 51,360 | Continuous |
| CCM | ✅ Ready | 51,360 / 51,360 | Continuous |
| Catalyst | ✅ Ready | 1,103,820 / 783,680 | Bank 1 & 2 aggregated |
| O2 Sensor | ✅ Ready | 522,580 / 673,600 | Includes secondary sensors |
| EGR | ✅ Ready | 456,010 / 30,880 | |
| EVAP | ⚠ Not Ready | 0 / 188 | Needs specific drive cycle |
| O2 Heater | — No Data | 0 / 0 | Likely not equipped |
| Secondary Air | — No Data | 0 / 0 | Likely not equipped |

Fuel economy calibration result example (post fill-up):
- Actual MPG: 29.3 (287.5 miles / 9.8 gallons)
- App MPG pre-calibration: 25.1
- VE adjustment ratio: 1.167 → new VE 99.2%
- Stored per VIN in `VehiclePreferences`

---

## Contributing & Housekeeping

- Trip CSV exports are ignored via `.gitignore` (`fuel_economy_trip_*.csv`).
- Always instantiate `VehicleManager` once per activity and remove listeners in `onDestroy`.
- When modifying `FuelEconomyActivity`, ensure `lastFuelCalcDiagnostics` is updated so CSVs remain accurate.
- For emissions parsing, add verbose logs for new PID descriptions to aid future debugging.
- **Do not** reintroduce per-screen markdown artifacts; update this README instead.

---

## Support & Contact

- Companion API issues: check Playwright logs or Experian API credentials.
- Bluetooth communication failures: inspect `ElmProt` logs for NODATA loops.
- Feature questions: reference relevant sections in this README or the corresponding Java source.

Happy hacking! 🚗💨
### Privacy & Offline Behavior
- No account required; VINs can remain on-device with optional hashing if cloud sync is introduced.
- Cached recall state persists offline, with timestamp showing last successful update.
- User controls to disable lookups or clear cached data.

### User Benefits
- Immediate awareness of safety-critical campaigns.
- Maintains service compliance records for resale and insurance.
- Cross-references recall fixes with DTC history and AutoCheck events.
- Drives dealership loyalty by surfacing repair scheduling prompts.

### Next Steps
1. Build `RecallService` with caching, background refresh, and API integration.
2. Implement dashboard card, detail activity/fragment, and preference toggles.
3. Add PDF export that merges recall status with DTC history and AutoCheck summaries.
4. Extend data sources to Canadian recalls and TSBs.
5. Consider push notification backend for off-device alerting.

### Resources
- NHTSA API docs: https://vpic.nhtsa.dot.gov/api/
- Transport Canada recalls: https://tc.canada.ca/en/road-transportation/defect-investigations-recalls
- NHTSA recall lookup: https://www.nhtsa.gov/recalls
- OBD-II VIN retrieval reference: SAE J1979 Mode 09 PID 02

## Automotive Data Modules

### NHTSA VIN Decoder (`modules/nhtsa-vin-decoder`)
- 2,015+ WMI codes synchronized across Java (`java/io/github/vindecoder/`) and Python (`python/nhtsa_vin_decoder.py`) implementations with ISO-compliant year decoding.
- Supports offline decoding, manufacturer-specific extensions, and automatic online fallback to NHTSA vPIC.
- Includes Android wrapper module, Gradle/Maven build files, packaging metadata, and GitHub Actions workflows.
- Highlights:
  - Offline decode <1 ms, batch decode >1,800 VINs/sec (per included benchmarks).
  - Extend by adding manufacturer decoders under `java/io/github/vindecoder/offline` and updating the Python equivalents.
  - Installation consists of copying the language-specific modules or wiring the Gradle submodule into your Android project.
  - Roadmap: add Honda, BMW, Nissan decoders and publish artifacts to Maven Central/PyPI.

### DTC Database (`modules/dtc-database`)
- SQLite dataset with 28,220 codes covering P/B/C/U categories and 33 manufacturers (`modules/dtc-database/data/dtc_codes.db`).
- Python API (`python/dtc_database.py`) and Java core (`java/DTCDatabaseCore.java`) expose caching, batch lookups, keyword search, and manufacturer filters.
- Android library (`android/dtc-database-android/`) bundles the database asset for on-device lookups.
- `build_database.py` regenerates the SQLite file from `data/source-data/*.txt`, keeping the dataset reproducible.

### Automotive Logo Library (`modules/automotive-logo-library`)
- Cross-language helpers to map manufacturer names or VINs to logo assets stored under `assets/logos/`.
- Python package (`python/carlogohelper/`), Java helper (`java/com/automotivelogolibrary/`), and Android wrapper (`android/automotive-logo-library-android/`) expose consistent APIs such as `getManufacturerFromVIN`, `getLogoFilename`, and `hasLogo`.
- Supports 60+ OEMs with alias matching and VIN-based manufacturer hints.
- To extend: add the PNG, update `LOGO_MAP`/`VIN_WMI_MAP` in both languages, and run the included smoke scripts.

### NHTSA Recall Client (`modules/nhtsa-recall-client`)
- Sister project to the VIN decoder that targets `api.nhtsa.gov/recalls` for VIN, vehicle, and campaign queries.
- Provides Java, Android (async callbacks), and Python clients with a shared schema for recall records.
- Under active construction; scaffolding mirrors the VIN decoder with planned caching, retries, and CI parity.
- Roadmap priorities include richer Python models, Maven publication, shared caching, recorded fixtures, and CLI/demo tooling.

## Strategy & Roadmap Highlights

### AutoCheck Integration Plan
- Internal roadmap recommends a standalone AutoCheck API with API keys, usage tracking, caching, and billing support.
- Android flow: automatic VIN retrieval, one-tap history request, in-app purchase flow, cached summaries.
- Launch phases (subject to refinement):
  1. **Internal Testing (Week 1-2):** validate accuracy, refine rate limits.
  2. **Integration (Week 3-4):** embed in OBD-Droid, add dashboard card, hook VIN auto-read.
  3. **Soft Launch (Month 2):** beta rollout, monitoring, pricing adjustments.
  4. **Growth (Month 4+):** influencer partnerships, affiliate program, SEO, B2B API offerings.
- Monetization targets: $15-25 per lookup vs ~$5-8 cost; upsell dealerships and consumer premium tiers.
- Success metrics under consideration: >95% scraper success, <15 s uncached response, 99.5% uptime, 10k users / $50k ARR target by month 12.

### Future Enhancements
- VIN scanner, batch VIN processing, WhatsApp bot integrations, PDF export, dealer-ready printouts.
- Recall notification automation, analytics dashboards, fraud detection alerts.
- API productization with customer billing, usage dashboards, and webhook notifications.

## Support & Contact

- Author: Waleed Judah (Wal33D) — aquataze@yahoo.com
- GitHub Issues: use respective module repositories when applicable.
- Companion AutoCheck service documentation covers Experian onboarding and Playwright configuration.

## Business Value Summary

- Delivers a differentiated OBD-II experience bundling recalls, history, and diagnostics.
- Enables dealership demos (60-minute prep, 15-minute presentation) and unlocks premium upsells.
- Future-ready for B2B licensing, affiliate programs, and API monetization with clear action items.
- With AutoCheck, recall intelligence, VIN decoding, DTC insights, and branding assets in one stack, OBD-Droid positions itself as the all-in-one automotive intelligence platform.
