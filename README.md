# OBD-Droid Platform

OBD-Droid is a full-stack vehicle intelligence suite that combines real-time OBD-II diagnostics with rich vehicle history, recall planning, and reusable automotive data modules for Android, Java, and Python applications.

## At a Glance
- Android app with live diagnostics, VIN-aware dashboards, and AutoCheck-powered vehicle history.
- Local AutoCheck service providing full reports via either official Experian API integration or Playwright-based browser automation.
- Recall notification roadmap outlining integration with NHTSA APIs and planned Canadian/TSB coverage.
- Reusable modules: world-class VIN decoder, comprehensive DTC database, and automotive logo library.
- Detailed go-to-market plan, testing scripts, and business-grade roadmap to monetize history reports.

## Repository Layout

```
OBD-Droid/
├── app/                         # Android application source
├── modules/
│   ├── nhtsa-vin-decoder/       # Multi-language VIN decoder toolkit
│   ├── dtc-database/            # Diagnostic trouble code database
│   └── automotive-logo-library/ # Logo + WMI helpers for OEM branding
├── build.gradle | settings.gradle | gradle/  # Android build tooling
├── MANIFEST.in | pom.xml | pyproject.toml    # Packaging metadata for modules
└── README.md                     # This consolidated guide
```

## Core Features

### OBD Diagnostics
- Bluetooth OBD-II communication via `app/src/java/com/obddroid/services/CommService.java`.
- Live data, DTC handling with freeze-frame information, and fuel economy tooling.
- Component responsibilities outlined in the Contributing & Workflow section below.

### Vehicle History & AutoCheck
- AutoCheck report ingestion with structured models (`AutoCheckReport.java`) and network service layer (`AutoCheckService.java`) that dispatches results to the UI thread.
- VIN auto-fill via Mode 09 PID 02 when available; manual entry fallback.
- Mileage fraud detection opportunities by comparing AutoCheck odometer with OBD readings.
- Dashboard card launches the Vehicle History flow and surfaces summary status.

### Recall Intelligence
- Roadmap includes automated recall polling after VIN retrieval with local caching and user notifications.
- Detail view design covers campaign ID, severity, remedies, and contact information for upcoming implementation.
- Planned additions: PDF exports, share flows, push notifications, and Canadian/TSB data parity.
- Data sources: NHTSA `recallsByVIN`, `recallsByVehicle`, `recallsByCampaign` with Transport Canada coverage slated for future work.

## AutoCheck Service Stack

### Companion Service Expectations
- The Android client targets the companion repository at `/path/to/autocheck-api/`.
- `AutoCheckService` calls the following endpoints:
  - `GET /health` – readiness probe used on activity startup.
  - `POST /api/autocheck/lookup` – returns a JSON payload consumed by `AutoCheckReport`.
  - `POST /api/autocheck/decode` – optional VIN decode helper.
- The current base URL is hard-coded to `http://192.168.0.153:3248`; update it to your machine’s IP when testing from a phone.
- Both `fetchReport` and `fetchReportWithPdf` execute off the main thread and marshal results back to the UI handler.

### Running the Companion Service
- Install dependencies and launch the Node/TypeScript service:
  ```bash
  cd /path/to/autocheck-api
  npm install
  cp .env.example .env
  npm run dev
  ```
- By default the service uses Playwright to scrape AutoCheck; with Experian credentials you can switch the implementation in `ExperianAutoCheckService.ts` to hit official endpoints while preserving the same JSON contract.

### Troubleshooting from the Android Client
- If the activity warns that the API is unreachable, ensure the service is running and the device can reach `http://<laptop-ip>:3248/health`.
- Free port 3248 when needed (`lsof -ti:3248 | xargs kill -9` on macOS).
- For slow or flaky scrapes, adjust headless/speed settings in the companion repo and watch its console logs.

### Companion Scraper Fix Checklist
- **Owners & usage extraction:** pull owner count from the `owner-icon-X.svg` filename and usage text from the `.owner .use` section so the Android stats card renders correctly.
- **Odometer checks:** inspect the `.odometer-box` tiles and flag rollback only when any tile deviates from "No issues reported".
- **Safety booleans:** derive structural damage, airbag deployment, and total loss from their respective icon alt attributes (`*-off` indicates false).
- **Reference VIN:** `4JGDA5HB7JB158144` should return owners `1`, usage `Lease`, and `odometerRollback=false` once the scraper logic is updated.

## Android Integration Details

| Component | Path | Purpose |
|-----------|------|---------|
| Data model | `app/src/java/com/obddroid/vehicle/AutoCheckReport.java` | Parses API payload, exposes helpers like `hasAccidents()` |
| Service | `app/src/java/com/obddroid/services/AutoCheckService.java` | Handles network calls, threading, and callbacks |
| UI | Vehicle History activity & dashboard card | UX for VIN entry, loading states, and rich report presentation |

### Next UI Enhancements
- Create dedicated activity layout with loading indicators, error states, and report cards.
- Add main menu entry point and persist VIN prefill from `VehicleManager`.
- Introduce mileage discrepancy alerts and premium upsell hooks.
- Optional upgrades: PDF export, VIN barcode scanner, batch lookup, recall alerts.

## Testing & Demo Guide

### Quick Start Checklist

1. **Verify backend health**
   ```bash
   cd /path/to/autocheck-api
   curl http://localhost:3248/health
   npm start # only if the previous check fails
   ```
2. **Confirm network connectivity**
   - Laptop and phone on the same Wi-Fi network.
   - From the phone browser, open `http://<laptop-ip>:3248/health` (example: `http://192.168.0.149:3248/health`).
3. **Launch the app**
   - Open OBD-Droid on the device or emulator.
   - Ensure the dashboard shows the blue “Vehicle History” card in the grid.
4. **Run a report**
   - Tap the card, enter VIN `1HGBH41JXMN109186`, and press “Check Vehicle History”.
   - Expect 30–60 seconds on first run while the scraper boots, logs in, and fetches data.
5. **Observe results**
   - Verify vehicle summary, AutoCheck score, owners, mileage, title status, accidents, recalls, and timeline entries.
6. **Capture logs when debugging**
   - Android: `adb logcat | grep -i autocheck`
   - API: tail the Node server output for Playwright activity and rate-limit notices.

### Scenario Matrix
- **Successful lookup** – VIN `1HGBH41JXMN109186`; sample data from the companion service returns ~75 score, three owners, ~108,904 miles, and one open recall.
- **API offline** – Stop the Node server and repeat lookup; UI should surface a connectivity error banner/toast.
- **Invalid VIN** – Use `123`; app shows “VIN must be exactly 17 characters”.
- **Network mismatch** – Disconnect the phone from Wi-Fi (use LTE) and try again; expect timeout messaging.
- **Repeated request** – Run the same VIN twice; second call should complete faster thanks to warm session caching.

### Demo Flow (15 minutes)
1. Show `http://localhost:3248/health` to prove service readiness.
2. Perform a live VIN lookup (use customer/employee vehicle if available).
3. Walk through key sections: score, owners, mileage, accidents, recalls.
4. Highlight upcoming additions (VIN scanner, mileage fraud alerts, PDF export, recall notifications).
5. Invite questions; keep troubleshooting cheatsheet handy.

### Troubleshooting Reference
- Free port 3248: `lsof -ti:3248 | xargs kill -9`
- Scraper flakiness: set `BROWSER_HEADLESS=false`, increase `BROWSER_SLOWMO`, or refresh credentials.
- Connectivity issues: confirm IP, firewall rules, and Wi-Fi; update base URL in `AutoCheckService`.
- VIN auto-read missing: verify Mode 09 PID 02 support; allow manual entry fallback.
- Unexpected scraper errors: clear session storage, re-login, monitor Playwright console output.

### Ready-to-Go Demo Script
> “We pull the VIN straight from the vehicle, trigger a full AutoCheck report in about a minute, and surface it alongside live OBD diagnostics—no extra websites or per-report fees.”

1. Start API (`npm start`) and open the health endpoint.
2. On Android, tap Vehicle History, paste VIN, press “Check”.
3. Narrate the returned insights (score trend, owners, mileage, recall count).
4. Close with the roadmap: premium upsells, B2B API, recall alerts, mileage fraud detection.

## Recall & Safety Roadmap

### Why Recall Intelligence Matters
- Life-safety issues (airbags, brakes, steering, fuel systems) demand proactive alerts.
- Owners may be legally responsible for addressing open recalls, and unresolved campaigns reduce resale value.
- Integrating recalls alongside fault codes gives users full diagnostic context without app switching.

### Feature Stack
1. **VIN-based recall checks**
   - Automatic read via Mode 09 PID 02; manual entry fallback.
   - Queries NHTSA on first connect and then on a scheduled cadence (daily/weekly).
   - Caches responses locally to minimize quota usage and support offline display.
2. **Recall presentation**
   - Dashboard badge when open recalls exist, highlighting severity via color-coded chips.
   - Detail view with campaign ID, affected component, risk summary, remedy instructions, and manufacturer contact info.
   - Historical log of resolved campaigns and timestamps.
3. **Notification channels**
   - In-app banner, persistent dashboard indicator, and optional push notifications for newly detected recalls.
   - Manual refresh control and snooze/“mark resolved” workflows for user acknowledgement.
4. **Vehicle history synergy**
   - Consolidates recalls, TSBs, AutoCheck history, DTC logs, and mileage trends into a single exportable report.
   - Planned PDF/email/share flows for dealerships, insurance, and resale documentation.

### Data Sources
- **NHTSA Recalls API** – `https://api.nhtsa.gov/recalls/recallsByVehicle`, `recallsByVIN`, `recallsByCampaign`.
- **Transport Canada** – bilingual feed for Canadian-market vehicles.
- **Technical Service Bulletins** – optional expansion using NHTSA TSB dataset and OEM feeds.
- **VIN decoding** – leverage internal VIN decoder module for make/model/year normalization.

### Architecture Blueprint

```
┌──────────────┐     ┌────────────────┐     ┌────────────────────┐
│ VIN Decoder  │────▶│ Recall Manager │────▶│ UI & Notification   │
│ (Mode 09)    │     │  • Cache store │     │  • Dashboard badge  │
└──────────────┘     │  • Scheduler   │     │  • Detail screens   │
                      │  • API client  │     │  • Push service     │
                      └────────────────┘     └────────────────────┘
                              │
                              ▼
                     ┌─────────────────┐
                     │ NHTSA / TC APIs │
                     └─────────────────┘
```

Refresh heuristic pseudocode:

```java
if (cache.isExpired(vin)) {
    List<Recall> recalls = api.fetchRecalls(vin);
    cache.store(vin, recalls, Instant.now());
    notifier.handle(recalls);
}
```

### UI Concepts

Dashboard card sketch:
```
┌─────────────────────────────────────────┐
│ ⚠️  SAFETY RECALL NOTICE (2 open)       │
│  • Airbag Inflator – HIGH               │
│  • Fuel Pump Module – MODERATE          │
│  [ View Details ]  [ Mark Resolved ]    │
└─────────────────────────────────────────┘
```

Detail dialog sketch:
```
┌─────────────────────────────────────────┐
│ Campaign 23V456 – Airbag Inflator       │
│ Severity: HIGH                          │
│ Issue: Inflator may rupture...          │
│ Remedy: Dealer replaces inflator free   │
│ Contact: 1-800-XXX-XXXX                 │
│ [ Schedule Repair ]  [ Dismiss ]        │
└─────────────────────────────────────────┘
```

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
