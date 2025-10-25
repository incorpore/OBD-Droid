# Core Platform & Diagnostics Backlog
| Status | Time | Task | Source Document(s) |
|---|---|---|---|
|  | Very Long | Implement Toyota/Lexus-specific diagnostics (KWP2000 addressing, mode 0x21 active tests) | ford-f150-diagnostic-learnings.md |
|  | Very Long | Expand European OEM support (VW/Audi/BMW) with UDS hierarchies and module mapping | ford-f150-diagnostic-learnings.md |
| ✅ | Very Long | Implement an end-to-end "Full Vehicle Scan" workflow that iterates every supported OBD/UDS mode, logs results, and generates a structured report surfaced in-app and for export | README.md · **COMPLETED** - ScanOrchestrator with 4 MVP stages, ScanActivity UI, report generation |
| ✅ | Very Long | Add an AI diagnosis assistant that consumes full-scan reports and app telemetry to suggest fixes, surface warnings, and optionally run automated scans | README.md · **COMPLETED** - DiagnosticAnalyzer integrated with ScanActivity |
|  | Very Long | Deliver an in-app Vehicle History Library that lists previously scanned vehicles, stores their diagnostic reports/telemetry, and supports filtering/search/export | README.md |
|  | Long | Migrate high-traffic consumers (EcuConversions, fault-code services, etc.) to the new PidDefinition/PidRuntime bridge helpers | agent-ecu-module-refactor.md |
|  | Long | Implement GM-specific module discovery (0x241–0x24F) and extended DTC support (mode 0x19) | ford-f150-diagnostic-learnings.md |
|  | Long | Implement Honda/Acura extended CAN addressing (18DAxxF1) and related diagnostics | ford-f150-diagnostic-learnings.md |
|  | Long | Harden Remote Telemetry connectivity with TLS validation, richer diagnostics, and error surfacing | androbod-plugin-integration.md |
|  | Long | Add active ECU probing for Ford modules and integrate VIN-based manufacturer detection | ford-f150-diagnostic-learnings.md |
|  | Long | Implement manufacturer-specific DTC scanning (modes 07, 0A, 1803, 1903, 1A03, etc.) | ford-f150-diagnostic-learnings.md |
|  | Long | Build symptom-based diagnostic analyzer with cost estimates (e.g., PSCM lockout patterns) | ford-f150-diagnostic-session-addendum.md |
|  | Long | Implement Vehicle History Phase 2 UI modules (recall list, usage badges, odometer sub-checks) behind feature flags | agent-telemetry-and-history.md |
|  | Long | Retire raw-map PV classes once enum payload rollout proves parity | agent-telemetry-and-history.md |
|  | Long | Execute full Ford field/regression testing per Phase 5 checklist (multi-model, damaged port, adapters) | ford-f150-diagnostic-learnings.md |
|  | Long | Design and build a dedicated Gauges hub accessible from the dashboard with modernized UI/UX (layout presets, responsive sizing, dark/light support) | README.md |
|  | Long | Implement gauge selection & configuration workflows (data source binding, units, thresholds) with persistence and validation, replacing ad-hoc settings | README.md |
|  | Long | Migrate existing gauge rendering to the new system, retire legacy fragments/adapters, and handle backward compatibility for stored preferences | README.md |
|  | Long | Integrate new telemetry streams (GPS, sensors, Remote Telemetry, CSV) into trip storage, dashboards, and exports | androbod-plugin-integration.md |
|  | Long | Implement WorkManager- (or equivalent) based scheduling for Remote Telemetry publishing to survive Doze | androbod-plugin-integration.md |
|  | Long | Add fused-provider support (Google Play Services) for GPS telemetry | androbod-plugin-integration.md |
|  | Long | Surface GPS telemetry in dashboards/trip history views | androbod-plugin-integration.md |
|  | Long | Integrate motion sensor readings into UI visualisations/analytics | androbod-plugin-integration.md |
| ✅ | Long | Align remaining `com.fr3ts0n.androbd.plugin` support classes with upstream or retire them cleanly | androbod-plugin-integration.md |
| ✅ | Long | Build an interactive "CoPilot" dashboard experience with an animated AI avatar (Knight Rider-style) for conversational diagnostics, tips, and playful responses | README.md · **COMPLETED** - Full CoPilot UI with Lottie avatar, voice I/O, expert AI prompts |
|  | Medium | Implement Ford PSCM lockout detection with on-screen guidance and reset playbook | ford-f150-diagnostic-session-addendum.md |
|  | Medium | Implement adaptive communication strategy for degraded OBD ports (dynamic timeouts, retries, user alerts) | ford-f150-diagnostic-session-addendum.md |
|  | Medium | Populate `testEcuConversions`/`testPidDefinitions` with concrete suites and wire into CI | agent-ecu-module-refactor.md |
|  | Medium | Add communication quality indicator with thresholds, adaptive polling, and UI messaging | ford-f150-diagnostic-session-addendum.md |
|  | Medium | Replace bitmask-based `PvChangeEvent` usage with enum payloads across remaining listeners | agent-telemetry-and-history.md |
|  | Medium | Expand PvChange adoption to VehicleInfoFooter, data services, and adapter layers | agent-platform-enhancement.md |
|  | Medium | Deploy the AutoCheck companion API to the production infrastructure (Node 18 runtime, TLS reverse proxy, Experian credentials via secrets manager, uptime monitoring) | README.md · claude.md · start-autocheck-dev.sh |
|  | Medium | Implement Vehicle History Phase 2 analytics events in the Android client (recall expand, badge tap, odometer expand, VIN auto-detect) | archive/analytics-phase2-dashboard-update.md |
|  | Medium | Update analytics schema/ETL (event spec, Airflow DAG `vh_feature_events`, enum mapping) for Phase 2 | archive/analytics-phase2-dashboard-update.md |
|  | Medium | Add lifecycle tests for sensor telemetry manager registration/unregistration | androbod-plugin-integration.md |
|  | Medium | Draft architectural RFC describing telemetry managers’ integration with dashboards/exports | androbod-plugin-integration.md |
|  | Medium | Extend settings UX (CSV timeouts, motion/GPS persistence, Remote Telemetry diagnostics) and document privacy impacts | androbod-plugin-integration.md |
|  | Medium | Add CSV logging storage quota enforcement and analytics event instrumentation | androbod-plugin-integration.md |
| ✅ | Medium | Convert legacy plugin Gradle modules into source sets / remove obsolete `com.android.application` usage | androbod-plugin-integration.md |
|  | Medium | Integrate telemetry observability hooks/debug panels for QA visibility | androbod-plugin-integration.md |
|  | Medium | Implement Remote Telemetry connection diagnostics UI and strict TLS certificate validation | androbod-plugin-integration.md |
|  | Medium | Improve user messaging when scans are partial or degraded (warnings, tips, retry guidance) | ford-f150-diagnostic-learnings.md |
|  | Medium | Implement VIN-based manufacturer detection pipeline for discovery heuristics | ford-f150-diagnostic-session-addendum.md |
|  | Medium | Create architectural diagrams, migration checklist, and documentation for ECU conversion refactor | agent-ecu-module-refactor.md |
|  | Medium | Provide sample usage snippets for new ECU APIs in `/docs/examples` | agent-ecu-module-refactor.md |
|  | Medium | Update ProGuard/R8 keep rules for new ECU abstraction adapters | agent-ecu-module-refactor.md |
|  | Medium | Capture cross-functional decisions for Vehicle History Phase 2 and update specs/backlog accordingly | agent-platform-enhancement.md |
|  | Medium | Present ECU abstraction proposal to Architecture Council and translate feedback into actionable work | agent-platform-enhancement.md |
|  | Medium | Support QA executing VIN/PID regression matrix (TR-1893/TR-1894) and monitor analytics events | agent-platform-enhancement.md · vin-pid-regression-matrix.md |
|  | Medium | Build and validate Looker dashboard updates for Vehicle History Phase 2 metrics | agent-platform-enhancement.md · archive/analytics-phase2-dashboard-update.md |
|  | Medium | Finalize localized copy and accessibility guidance for Vehicle History Phase 2 components | agent-telemetry-and-history.md |
|  | Medium | Complete analytics instrumentation & dashboard updates once client events land | agent-telemetry-and-history.md |
|  | Medium | Backfill 30 days of historical analytics data for Phase 2 dashboards | archive/analytics-phase2-dashboard-update.md |
|  | Medium | Validate Phase 2 events in Looker dev workspace and staging log streams | archive/analytics-phase2-dashboard-update.md |
|  | Medium | Configure PagerDuty alerting for analytics ingestion failures (>15 min) | archive/analytics-phase2-dashboard-update.md |
|  | Medium | Add instrumentation/unit coverage for CSV logging, GPS, sensor, and Remote Telemetry toggles (permissions, lifecycle, cadence) | androbod-plugin-integration.md |
|  | Medium | Add Remote Telemetry configuration/topic parsing tests | androbod-plugin-integration.md |
|  | Medium | Update privacy policy and disclosures for GPS, sensor, and Remote Telemetry background data capture | androbod-plugin-integration.md |
|  | Medium | Resolve GPL compliance approach for migrated AndrOBD code (dual-license vs clean-room) | androbod-plugin-integration.md |
|  | Medium | Audit wake-lock usage in CSV/GPS/Remote Telemetry services for Android 14 background limits | androbod-plugin-integration.md |
|  | Medium | Define stance on legacy plugin IDENTIFY handshake and communicate compatibility to users | androbod-plugin-integration.md |
|  | Quick | QA review competitor screenshots in `ideas/` subdirectories to document features OBD-Droid lacks | ideas/ |
|  | Quick | Fix PID discovery cache bug so zero responses trigger retries instead of permanent empty caches | ford-f150-diagnostic-session-addendum.md |
|  | Quick | Expand ECU address validation to cover 0x700–0x7FF and 29-bit extended headers | ford-f150-diagnostic-learnings.md |
|  | Quick | Ensure BODY_SENSORS permission handling for motion telemetry on Android 13+ | androbod-plugin-integration.md |
|  | Quick | Align CSV writer timestamp semantics with trip analytics (UTC vs local) | androbod-plugin-integration.md |
|  | Quick | Add logging guidelines to unify telemetry across modules | agent-ecu-module-refactor.md |
|  | Quick | Switch Android client configuration from localhost to the hosted AutoCheck base URL (feature flag or remote config, secure API key injection, update `AutoCheckService` and docs) | README.md · start-autocheck-dev.sh |
