# OBD‑Droid Platform

OBD‑Droid is our in-house Android platform for professional-grade vehicle
diagnostics, telemetry, and reporting. The app now includes scoped feature
modules (live diagnostics, CSV logging, GPS & motion telemetry, Remote Telemetry export,
Vehicle History AutoCheck integration, Ford PSCM intelligence, etc.) and an
expanded documentation set that lives under `docs/`.

This README provides the up-to-date entry point for the repo: what ships today,
where the code lives, how to run it, and which documents track the remaining
work.

---

## Snapshot — April 2026

- **Live diagnostics** now include Ford PSCM lockout detection, extended
  address probing, and improved error surfacing (see
  `docs/ford-f150-diagnostic-learnings.md` + addendum).
- **Feature modules** – CSV logging, GPS telemetry, motion sensors, and Remote
  Telemetry – live under `app/src/java/com/obddroid/features/` and expose UI
  coordinators so screens just delegate.
- **Vehicle history** is delivered through the AutoCheck companion API and
  lives in `features/vehiclehistory` (migrated from the monolithic activity).
- **Documentation** has been reorganised: `docs/index.md` mirrors every
  markdown file, with dedicated backlogs (`docs/docs-master-todo.md`),
  dealer-specific roadmap, and Play Store asset checklist.
- **Roadmaps** were trimmed: `docs/obd-unified-plan.md` is a concise “Must Do /
  Stretch / Parking Lot” list.

---

## Repository Layout

```
OBD-Droid/
├── app/
│   ├── src/java/com/obddroid/features/   # Modularised features
│   │   ├── csvlogging/                   # Foreground service, UI coordinator
│   │   ├── gps/                          # Synthetic PID generator
│   │   ├── sensors/                      # Motion telemetry
│   │   └── remotetelemetry/              # Remote telemetry publisher + UI integration
│   ├── src/java/com/obddroid/ui/         # Screens, components, adapters
│   └── src/main/res/                     # Layouts, menu, drawables, strings
├── modules/                              # Shared libraries (VIN decoder, DTC DB,
│                                         #  automotive logo assets, vehicle history)
├── docs/                                 # Plans, backlogs, case studies (see below)
├── gradle*, build.gradle, settings.gradle
└── README.md                             # ← you are here
```

---

## Core Feature Areas

### 1. Live Diagnostics & Ford Support
- Discovery, protocol handling: `core/obd/ObdProt.java`, `services/CommService`.
- Ford improvements documented in:
  - `docs/ford-f150-diagnostic-learnings.md`
  - `docs/ford-f150-diagnostic-session-addendum.md`
- Lockout detection lives in the Ford feature module; CSV logging now receives
  broadcasts via `CsvLoggingUiCoordinator`.

### 2. Telemetry & Logging
- CSV logging: `features/csvlogging/` (foreground service + UI coordinator).
- GPS telemetry: `features/gps/data/GpsTelemetryManager` exposes synthetic PIDs.
- Motion sensors: `features/sensors/data/SensorTelemetryManager`.
- Remote Telemetry: `features/remotetelemetry/data/RemoteTelemetryManager`
  with `features/remotetelemetry/ui/RemoteTelemetryUiCoordinator`. Publishes
  the live dashboard values you choose to any server or broker you point it at,
  so teams can watch vehicle data in real time without touching the phone.
- All feature toggles surface consistent snackbars via the helper in
  `MainActivity`.

### 3. Emissions & Readiness
- UI + parsing: `ui/activities/EmissionsActivity.java`.
- Live data refresh TODO tracked in `docs/docs-master-todo.md`.

### 4. Vehicle History (AutoCheck)
- UI / domain code now lives in `features/vehiclehistory/`.
- API client still consumes the local companion service
  (`/path/to/autocheck-api`).
- Companion endpoint details remain in `docs/telemetry-and-history.md`.

### 5. Dealer Diagnostics
- Dealer roadmap and backlog: `docs/dealer-diagnostics-roadmap.md`.
- Core backlog intentionally excludes dealer items (`docs/docs-master-todo.md`).

---

## Documentation Map (quick links)

| Area | File |
|------|------|
| Feature backlog (core) | `docs/docs-master-todo.md` |
| Ford diagnostic learnings | `docs/ford-f150-diagnostic-learnings.md` |
| Live diagnostic session addendum | `docs/ford-f150-diagnostic-session-addendum.md` |
| Launch & growth | `docs/launch-plan.md`, `docs/store-assets-checklist.md` |
| Dealer roadmap | `docs/dealer-diagnostics-roadmap.md` |
| Telemetry roadmap | `docs/telemetry-and-history.md` |
| Gauges & ECU refactor planning | `docs/ecu-module-refactor.md`, `docs/platform-enhancement.md` |
| Full index | `docs/index.md` |

---

## Development Quick Start

### Prerequisites
- Android Studio Giraffe or newer (with Android SDK 24–34 platforms).
- JDK 17+ (the Gradle build uses toolchains; avoid Java 8).
- Android device or emulator (Android 8.0+ recommended).
- Optional: AutoCheck companion API (NodeJS 18+; see below).

### Build & Run
```bash
# Install dependencies (first run only)
./gradlew clean

# Build & install debug APK on the connected device/emulator
./gradlew installDebug

# Start the app
adb shell am start -n com.obddroid/.ui.activities.MainActivity
```

### Tests & Tooling
```bash
./gradlew :app:testDebugUnitTest      # JVM tests
./gradlew :app:lintDebug              # Android lint
./gradlew :app:compileDebugJavaWithJavac  # Sanity compile
```

### AutoCheck Companion (Vehicle History)
```bash
cd /path/to/autocheck-api
npm install
npm run dev   # Defaults to http://127.0.0.1:3248 (update base URL for device)
```
Update the base URL in the Android app settings (or via remote config) before
testing on an actual device.

---

## Operational Checklists

### Telemetry Toggles (in-app)
1. Open Main screen or Live Data.
2. Enable/disable CSV Logging, GPS telemetry, Motion telemetry, Remote Telemetry.
3. Confirm green/informational snackbar appears just above the footer for each
   toggle.
4. Check `Settings → Telemetry` for Remote Telemetry configuration and status history.

### Ford PSCM Regression
1. Run full module scan (expect PSCM at 0x726).
2. Verify lockout detection messaging when “Pull Compensation” is disabled.
3. Ensure `docs/ford-f150-diagnostic-session-addendum.md` checklist remains
   satisfied.

### AutoCheck Flow
1. Start the companion API.
2. Enter a VIN in the Vehicle History screen.
3. Validate the report renders (owner counts, usage classification, rollbacks).
4. For failures, capture logs and update the backlog item in
   `docs/telemetry-and-history.md`.

---

## Roadmap & Backlog

- **Core backlog** – `docs/docs-master-todo.md` (Status column blank for
  updates, aligned with feature modules).
- **Unified plan** – `docs/obd-unified-plan.md` (Must Do / Stretch / Parking).
- **Dealer initiatives** – `docs/dealer-diagnostics-roadmap.md`.
- **Play Store launch tasks** – `docs/launch-plan.md` +
  `docs/store-assets-checklist.md`.

The docs folder is the source of truth; update the relevant file alongside any
code change that impacts scope, scope assumptions, or validation notes.

---

## Contributing Guidelines

1. Keep feature-specific UI/logic inside `app/src/java/com/obddroid/features/...`
   and provide coordinators/adapters for activities when needed.
2. Wire snackbars, dialogs, and preference updates through coordinators rather
   than duplicating implementations in screens.
3. Update `docs/index.md` and the appropriate detailed document whenever a
   behaviour or milestone changes.
4. Run `./gradlew :app:compileDebugJavaWithJavac` before submitting — it
   ensures feature packages build even if IDE code analysis passes.
5. For AutoCheck or telemetry work, update `docs/telemetry-and-history.md` with
   session notes and outstanding items.

Need anything else? Reach out via the issue tracker or drop notes into the
relevant doc so the next iteration stays aligned.
