# OBD‑Droid Platform

OBD‑Droid is a full-stack vehicle intelligence platform that combines Android-based live diagnostics, fuel economy analytics, emissions compliance reporting, and AutoCheck-powered vehicle history. This README consolidates every engineering note, analysis artifact, and status report created to date.

---

## TL;DR – Where We Stand

- **Android app:** Production-build ready with Bluetooth OBD-II stack, VIN-aware dashboards, rich fuel economy tooling, and an emissions readiness screen validated on a 2018 Mercedes GLE.
- **Fuel economy:** Speed-density engine with calibration dialog, per-VIN preferences, and live charting (instant/average MPG, fuel flow, range) — CSV export removed.
- **Emissions diagnostics:** Mode 1 + Mode 9 readiness merged into a single dashboard; pending follow-up to auto-refresh readiness frames on activity launch.
- **OBD service stability:** Mode 9 vs. fault-code audit closed the service-context bug; remaining fixes target PV notifications, termination flags, and retry back-off.
- **Vehicle history:** AutoCheck integration in place using the local companion API (Playwright or Experian). Switching to the production host is a quick config update when ready.

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
- **Vehicle preferences:** `vehicle/VehiclePreferences.java` stores tank size, VE calibration, and whether per-VIN prompts have been shown.
- **Estimation stack:** `vehicle/VehicleManager.java` provides tank capacity heuristics using decoded VIN metadata (body class, displacement, drivetrain, electrification).
- **FuelEconomyActivity highlights:**
  - Speed-density MAP-based calculation with ideal gas law (MAP, IAT, RPM, VE, displacement, stoich) and RPM/load fallback when sensors are missing.
  - Calibration dialog that persists VE (50–130% validation) so subsequent calculations match real-world fill-up data.
  - Preferences-driven tank capacity prompt with VIN masking that keeps user-entered values per vehicle.
  - Real-time dashboard (instant/average MPG, fuel flow, range, throttle, time-to-empty) with in-app charting—no CSV export currently ships.
- **Outstanding work:** Surface the active calculation method in the UI and wire VE prompts into future trip-history tooling once requirements are defined.

### Emissions Readiness
- **Implementation:** `ui/activities/EmissionsActivity.java` parses Mode 9 PID 0x08 (IUMPR) and Mode 1 PID 0x01 readiness bits, combining them into a single dashboard.
- **Validated monitors:** Misfire, Fuel, CCM, Catalyst, O2 Sensor, EGR, EVAP, Secondary Air, O2 Heater (with “Not Equipped” fallback). Mercedes GLE test data confirmed large completions (e.g., O2 sensors 522,580/673,600; catalyst 1,103,820/783,680).
- **UI:** Card-based layout with progress bars, status icons, ready/not-ready summary banner.
- **Current limitation:** Mode 1 readiness is only refreshed when the PID frame is seen elsewhere; auto-triggering a readiness refresh on activity start is still on the backlog.
- **Future enhancement:** Expand the “Not Equipped” educational copy and add logging presets for common readiness troubleshooting workflows.

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

Latest audit of the Mode 9 (OBD_SVC_VEH_INFO) and fault-code services closed the most disruptive regressions and catalogued what is left to tighten up.

| Issue | Status | Notes & References |
|-------|--------|--------------------|
| Fault-code requests used the wrong service context | ✅ Fixed | `ObdProt.setService` now writes using the requested service (`app/src/java/com/obddroid/core/obd/ObdProt.java:1474-1487`). |
| Fault-code PV updates never emitted `PV_MODIFIED` | ⚠ Still outstanding | `tCodes` updates only call `put`, so listeners relying on `PV_MODIFIED` still miss refreshes. |
| No termination flag for fault-code sweeps | ⚠ Still outstanding | `ElmProt` lacks the equivalent of `pidsWrapped` for code services, so polling can continue unnecessarily. |
| Aggressive NODATA recovery spins the adapter | ⚠ Still outstanding | Recovery loop (`ElmProt` NODATA branch) still retries immediately; needs back-off and shared state with InitializationManager. |
| Cache restore order unclear | ⚠ Needs review | Cached fault codes are repopulated (`ObdProt.java:1638-1642`), but telemetry to confirm UI parity has not been instrumented. |

**Next Steps**
1. Emit `PV_MODIFIED` when `tCodes` mutate so UI components and analytics observers receive updates.
2. Add a termination condition for fault-code polling (counter or feature flag) and align retry timing with InitializationManager.
3. Instrument status transitions and cache restores to measure flicker frequency before and after back-off tuning.

---

## Key Code Map

| Area | File | Notes |
|------|------|-------|
| OBD protocol state machine | `app/src/java/com/obddroid/core/obd/ObdProt.java` | `setService()`, `handleTelegram()`, caching |
| ELM adapter state management | `app/src/java/com/obddroid/core/obd/ElmProt.java` | `setStatus()`, NODATA recovery |
| Initialization orchestration | `app/src/java/com/obddroid/core/obd/InitializationManager.java` | Mode 9 + fault-code service sequencing |
| Fuel economy activity | `app/src/java/com/obddroid/ui/activities/FuelEconomyActivity.java` | Speed-density calc, VE calibration, tank prompts, live charting |
| Emissions screen | `app/src/java/com/obddroid/ui/activities/EmissionsActivity.java` | IUMPR parsing, readiness aggregation, PDF/CSV export dialogs |
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
- Update the base URL inside `AutoCheckService` for on-device testing (`localhost` via adb reverse or your LAN IP). When the backend moves to production, swap the same constant to the hosted URL and update this section.

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
4. Let the session run for 5+ minutes and confirm recent/medium/long-term chart bins update smoothly.
5. Use the calibration dialog after a fill-up and verify the confirmation snackbar plus updated VE in preferences.

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

- `fuel_economy_trip_*.csv` ignores can be cleaned up; the historical CSV exporter has been removed from the app.
- Use `VehicleManager.getInstance(context)` sparingly (typically once per activity/screen) and remove listeners in `onDestroy()` to avoid leaks.
- When extending `FuelEconomyActivity`, keep `updateDisplayedValues()` and the chart datasets in sync with any new metrics you surface.
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
