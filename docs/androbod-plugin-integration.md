# AndrOBD Plugin Migration Plan

- Migrated the AndrOBD CSV logging plugin into native feature code (`com.obddroid.features.csvlogging.data`) with notifications, auto-pause, and share support; the original plugin sources have been removed.
- Upstream repo version: `fr3ts0n/AndrOBD-Plugin` (snapshot referenced during migration).
- The legacy plugin modules (`modules/androbod-plugins/GpsProvider`, `SensorProvider`, `MqttPublisher`) have been removed now that their behaviour lives in-core under `com.obddroid.features.*`.
- All upstream plugins ship GPLv3+ headers; preserve attribution when reusing code.

## Core Integration Themes

- Treat the plugins as reference implementations of four features we want to ship natively: continuous CSV logging, GPS telemetry, on-device sensor streaming, MQTT publishing.
- Replace the plugin broadcast contract with direct wiring against our core data plane (`CommService.elm`, `ObdProt`/`EcuDataItems`, UI view-models); no intent round-tripping once ported.
- Consolidate configuration into our existing settings surface (Jetpack `PreferenceFragmentCompat` + Compose scaffolding) and retire the legacy `PreferenceActivity` classes.
- Translate long-running background work into lifecycle-aware Android components (foreground service + notification for logging, `WorkManager`/coroutines for MQTT, listeners tied to app foreground for sensors/GPS).
- Ensure we surface runtime permission flows (location, body sensors where required) through the app shell rather than per-feature dialogs.

## CSV Logger (Integrated)

- Feature: Records every PID update to rolling CSV segments inside `getExternalFilesDir()`, provides a foreground notification, auto-pause after inactivity, and share support.
- Implementation: `com.obddroid.features.csvlogging.data` package (service, state holder, writer thread) with UI hooks in `MainActivity`.
- Follow-ups: Extend settings coverage (user-configurable timeouts, storage location), add instrumentation tests for start/stop flows, ensure storage quota handling and analytics integration.

## GPS Provider (Integrated)

- Feature: GPS location (lat/long/altitude/bearing/speed) surfaced as synthetic PIDs so dashboards, CSV logging, and future analytics can consume the telemetry.
- Implementation: `com.obddroid.features.gps.GpsTelemetryManager` listens to `LocationManager`, creates native `EcuDataItem`s, registers them with `ObdProt.PidPvs`, and updates values in real time. MainActivity exposes a runtime toggle that checks location permission and survives app resumes.
- Follow-ups: add fused-provider support when Google Play services are present, expose the telemetry inside dashboards/trip history, and add instrumentation coverage for permission flows.

## Sensor Provider (Integrated)

- Feature: Accelerometer X/Y/Z axes exported as telemetry channels for logging and visualisation.
- Implementation: `com.obddroid.features.sensors.SensorTelemetryManager` registers for the accelerometer, injects the synthetic items, and surfaces a menu toggle to start/stop streaming.
- Follow-ups: consider BODY_SENSORS permission handling on Android 13+, wire readings into UI visualisations, and add tests around lifecycle registration.

## MQTT Publisher (Integrated)

- Feature: Periodically publishes selected PID values to a user-configured MQTT broker.
- Implementation: `com.obddroid.features.mqtt.data.MqttTelemetryManager` uses HiveMQ’s blocking client, a scheduled executor to push snapshots, and shared preferences (with `SecurePreferences` backing for passwords) for configuration. Settings now expose broker host/port/protocol/topic prefix/user credentials/QoS/interval, data-item selection, and a live status summary of the last publish attempt; `MainActivity` listens for status callbacks to surface failures and recoveries via snackbars. MainActivity toggle starts/stops publishing and persists the enabled state.
- Follow-ups: add connection diagnostics/notifications, tighten TLS certificate handling, expand tests for configuration parsing and topic construction, and consider migrating to WorkManager for Doze-aware scheduling.

## Cross-Cutting Tasks

- **Support Library Alignment**: Confirm the `com.fr3ts0n.androbd.plugin` classes we already host mirror upstream; if gaps exist, port missing pieces (e.g., `PluginReceiver`, shared resources) or strip dependency during migration.
- **Gradle Wiring**: Convert the imported modules into plain source sets (or merge code into app module) so we are not shipping extra APKs; remove obsolete `apply plugin: 'com.android.application'` once porting begins.
- **Permissions & Privacy**: Update the manifest and privacy policy describing background data capture (GPS, sensors, MQTT publish). Ensure `ACCESS_FINE_LOCATION`, `BODY_SENSORS`, and `POST_NOTIFICATIONS` prompt flows exist.
- **Telemetry Surfacing**: Decide how new data channels integrate with trip storage, dashboards, alerts, and exports; update `TripManager`, `EcuListActivity`, and analytics accordingly.
- **Observability**: Add logging hooks and debug panels so QA can verify MQTT connections, CSV writer status, and sensor readings without digging into logcat.

## Open Questions & Risks

- GPL obligations require shipping source or offering download—importing code means our whole app inherits GPL; confirm with leadership whether dual-licensing or clean-room rewrite is required.
- Wake-lock handling needs re-evaluation when moving into our service layer; ensure we do not break background execution limits on Android 14.
- CSV writer currently uses `System.currentTimeMillis()` for row IDs; decide whether to align with existing timestamp semantics (UTC vs local) for trip analytics.
- MQTT credential storage currently plain-text; consider encryption or requiring user re-entry on each session.
- Need to map plugin discovery (IDENTIFY handshake) to our existing plugin manager if we keep backwards compatibility for external plugins.

## Suggested Next Steps

- [ ] Draft architectural RFC covering how the new telemetry managers integrate with the broader data pipeline (dashboards, trip history, exports).
- [ ] Add instrumentation/unit coverage for CSV logging, GPS, accelerometer, and MQTT toggles (permissions, lifecycle start/stop, publish cadence).
- [ ] Extend settings UX (user-configurable CSV timeouts, motion/GPS persistence, MQTT diagnostics) and document privacy/permission impacts.
- [ ] Harden MQTT connectivity (TLS validation, error surfacing) and consider moving scheduling to WorkManager for Doze awareness.
