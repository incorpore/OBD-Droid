# AndrOBD Plugin Migration — Current Snapshot

The AndrOBD plug-ins are no longer shipped as side-loaded APKs. Their
capabilities now live as first-party feature modules in the app, which keeps
telemetry, permissions, and UI flows consistent with the rest of OBD‑Droid.

## What’s Already Done

- **CSV logging** sits under `features/csvlogging/`, running as a foreground
  service with notifications, auto‑pause, and share support.
- **GPS telemetry** (`features/gps/`) exposes location data as synthetic PIDs so
  dashboards, CSV exports, and Live Data Sharing can consume the same stream.
- **Motion sensors** (`features/sensors/`) provide accelerometer channels with
  runtime toggles managed by `MainActivity`.
- **Live Data Sharing** (`features/livedatasharing/`) publishes user-selected
  PID values to any socket-compatible broker. Configuration, secure password
  storage, and UI feedback live in Settings and the main toolbar menu.

All legacy plug-in modules (`GpsProvider`, `SensorProvider`, and the MQTT
publisher) have been deleted, and their Gradle wiring cleaned up.

## Remaining Follow-Ups

- **Telemetry polish**
  - Add fused-location support and richer permission flows for GPS telemetry.
  - Extend sensor telemetry visualisations and tighten BODY_SENSORS handling on
    Android 13+.
  - Expand Live Data Sharing diagnostics (TLS validation, QoS/topic coverage,
    better error surfacing, Doze-aware scheduling).
- **Observability & QA**
  - Provide lightweight debug panels/logging hooks so testers can inspect the
    state of each telemetry stream without diving into logcat.
  - Backfill instrumentation tests for the feature toggles (CSV, GPS, sensors,
    Live Data Sharing) covering lifecycle and permissions.
- **Privacy & compliance**
  - Keep the privacy policy and in-app disclosures updated for background data
    capture (location, motion, and off-device publishing).
  - Reconfirm the GPL obligations from the original AndrOBD sources and ensure
    attribution is retained in the repo.

## Notes & References

- Original plug-in snapshots: `fr3ts0n/AndrOBD-Plugin` (archived locally during
  migration).
- Core data plane integration points: `CommService`, `ObdProt.PidPvs`, and the
  shared `FeatureToggleNotifier` utilities.
