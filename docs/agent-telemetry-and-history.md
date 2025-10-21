# Telemetry & Vehicle History Workstreams

**Assigned Agent:** Agent C  
**Last Updated:** 2024-07-24

## Workstream Snapshot
| Initiative | Objective | Current Status | Upcoming Milestone |
| --- | --- | --- | --- |
| Process Variable (PV) Infrastructure Refactor | Replace legacy event bitmasks and raw maps with typed, thread-safe telemetry primitives | Typed wrappers live; pilot `PvChange` enum ready for wider rollout | Migrate top listeners to enum payloads and deprecate bitmask accessors |
| Vehicle History Experience Refresh | Ship Phase 2 UI (recall module, usage badges, odometer sub-checks) and shape Phase 3 timelines | Phase 1 polish completed; Phase 2 UI awaits implementation behind feature flags | Deliver gated Phase 2 components with analytics + regression coverage |
| Analytics & QA Alignment | Keep telemetry/data consumers ready for PV + Vehicle History changes | VIN/PID regression matrix distributed, analytics spec drafted | Validate instrumentation in staging and sync sign-off before flag ramp |

## TODO
- [x] Introduce `TypedProcessVar`/`TypedPvList` wrappers to coexist with legacy structures.
- [x] Migrate `ObdDataService`, `ObdProt`, and UI adapters to typed PV access.
- [x] Harden concurrency via `ReentrantReadWriteLock` around PV updates.
- [ ] Replace bitmask-based `PvChangeEvent` with enum payload across listeners.
- [ ] Retire raw-map PV classes after enum rollout verifies parity.
- [ ] Implement Vehicle History Phase 2 UI modules (recalls, usage badges, odometer sub-checks) behind remote config flags.
- [ ] Finalize localized copy + accessibility guidance for new Vehicle History components.
- [ ] Complete analytics instrumentation and dashboard updates for Phase 2 engagement metrics.

## Process Variable Infrastructure Refactor

### Current State
- `TypedProcessVar` and `TypedPvList` wrappers are merged and exercised by core services (`ObdDataService`, `ObdProt`) and primary UI adapters.
- `PvChange` enum prototype is available and validated in `MainActivity`; compatibility shims still expose legacy bitmask values.
- Thread-safety tightened via read/write locks around listener dispatch, reducing race conditions during bulk updates.

### Near-Term Deliverables
1. Expand `PvChange` enum adoption to VehicleInfoFooter, data-service listeners, and adapter layers; emit dual payloads during transition.
2. Publish `ProcessVariable<K, V>` interface and adapter so legacy `HashMap` implementations can be phased out without breaking callers.
3. Update unit/integration coverage to assert enum ↔ UI mappings, threading guarantees, and serialization compatibility.

### Dependencies & Coordination
- Align rollout with ECU refactor timelines so PID runtime objects can rely on the new typed interfaces.
- Keep QA in the loop using the VIN/PID regression matrix (`docs/vin-pid-regression-matrix.md`) to validate telemetry behavior.
- Coordinate with Analytics to ensure enum-backed events land in downstream pipelines before bitmask removal.

### Risks & Mitigations
- **Listener Drift:** Track migration status per listener to avoid inconsistent payload handling; add lint checks once adoption passes 80%.
- **Binary Compatibility:** Ship adapters that expose both legacy and new APIs until downstream modules confirm readiness.
- **Testing Surface:** Expand JVM tests and targeted instrumentation cases to keep parity visible and auditable.

## Vehicle History Experience Refresh

### Phase Overview
- **Phase 1 (Complete):** Typography/spacing polish, Score Analysis card, and expanded At a Glance checks shipped and awaiting broad release.
- **Phase 2 (In Progress):** Detailed recall module, vehicle usage badges, and odometer sub-check expansion to launch behind feature flags.
- **Phase 3 (Discovery):** Owner history timelines, advanced visualizations, and export/share workflows under research.

### Phase 2 Deliverables
| Module | Key Tasks | Status Notes |
| --- | --- | --- |
| Recall Module | Extend `AutoCheckReport` parsing, build expandable list UI, add CTA/tap analytics, gate behind `vehicle_history_recalls` | API payload validated on staging; UI work pending |
| Usage & Classification Badges | Map usage/class data, create `UsageBadgeView`, add tooltip analytics, finalize copy | Requires Design/Content sign-off for multi-usage phrasing |
| Odometer Sub-Checks | Model sub-check payloads, build accordion, ensure accessibility, hook analytics | Depends on At a Glance entry integration |

### Data & UX Considerations
- Localize new copy and tooltip content; ensure dark mode specs and CLDR-derived units display correctly.
- Guard against missing data (null arrays, absent dates) with safe defaults and instrumentation.
- Provide TalkBack focus order, dynamic type scaling, and contrast validation as part of QA exit criteria.

## Analytics & QA Alignment
- Event taxonomy (`vehicle_history_recall_expand`, `vehicle_history_usage_badge_tap`, `vehicle_history_odometer_expand`, `vehicle_history_vin_autodetect`) defined in `docs/analytics-phase2-dashboard-update.md`; Airflow + Looker updates scheduled around the feature ramp.
- Regression assets curated in `docs/vin-pid-regression-matrix.md`, covering VIN fixtures, PID datasets, and owners for staged testing (TestRail runs TR-1893/TR-1894).
- Staging validation to include logcat checks, Snowplow stream verification, and dashboard smoke tests prior to production rollout.

## Next Actions
1. Finalize cross-functional design/copy decisions for recall lists, usage badges, and odometer modules; publish the approved specs.
2. Migrate high-traffic listeners to the `PvChange` enum and monitor telemetry for regressions before deprecating bitmask accessors.
3. Implement Phase 2 Vehicle History UI components behind remote config flags and wire analytics events per the dashboard plan.
4. Execute the VIN/PID regression matrix during staging builds and capture QA sign-off prior to increasing feature-flag exposure.
