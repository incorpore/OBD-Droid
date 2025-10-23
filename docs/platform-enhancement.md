# Platform Enhancement Master Plan

## TODO
- [x] Circulate Phase 2 UI/UX summary and copy recommendations to Design & Content asynchronously.
- [x] Implement `PvChange` prototype and migrate an initial listener to validate the new event flow (see `app/src/java/com/obddroid/core/pvs/ProcessVariables.java` + `MainActivity`).
- [x] Author abstraction proposals for ECU conversion/catalog interfaces and review with architecture stakeholders (`docs/ecu-conversion-abstractions.md`).
- [x] Compile VIN/PID regression matrices and distribute to QA (`docs/vin-pid-regression-matrix.md` shared with QA Vehicle Experiences).
- [x] Update analytics dashboards once Phase 2 features roll out (`docs/analytics-phase2-dashboard-update.md` delivered to Data team).

## Executive Summary

- **Vehicle History Experience (Customer-facing):** Phase 1 polish is code-complete; Phase 2 design/copy alignment is progressing asynchronously with regression assets/analytics specs ready to execute once Android implementation begins. Phase 3 (owner timelines + advanced visualizations) remains in discovery.
- **Process Variable (PV) Infrastructure Refactor (Core telemetry):** `PvChange` enum payload prototype is live with `MainActivity` + VIN helper migrated and JVM coverage in place; next focus is expanding listener adoption and collapsing the legacy bitmask.
- **ECU Module Refactor (Diagnostics domain):** Interface proposal for conversions/catalogs is published for architecture review (2024-07-23) to unblock staged renames aligned with PV interface work.

## Initiative Overview

| Initiative | Objective | Current Status | Upcoming Milestone |
| --- | --- | --- | --- |
| Vehicle History Experience | Deliver richer AutoCheck insights with accessible UI/analytics coverage | Phase 1 complete; Phase 2 alignment happening via async reviews; Android implementation not yet started; regression & analytics packs delivered | Apply design/content feedback and ship feature-flagged Phase 2 UI |
| PV Infrastructure Refactor | Modernize PV eventing + storage for type safety and maintainability | Typed lists live; `PvChange` prototype + MainActivity migration merged with unit coverage | Expand enum payload to remaining listeners and start interface extraction |
| ECU Module Refactor | Clean up conversion/catalog APIs and remove shared mutable state | Interface proposal circulated for architecture review | Incorporate council feedback and wire compatibility adapters |

## Cross-Team Alignment

- **Design & Content:** Async reviews in progress; distribute updated frames/copy decks via shared channel and capture approvals in Confluence.
- **Analytics/Data:** Event taxonomy and dashboard updates defined in `docs/analytics-phase2-dashboard-update.md`; Data Eng owns schema rollout and Looker updates ahead of feature ramp.
- **QA/Test Automation:** Regression matrix published in `docs/vin-pid-regression-matrix.md` and imported into TestRail runs TR-1893/TR-1894; automation owners assigned per scenario.
- **Architecture Council:** ECU abstraction proposal (`docs/ecu-conversion-abstractions.md`) added to 2024-07-23 council docket to validate sequencing with PV refactor.

## Initiative Detail

### Vehicle History Experience Refresh

**Current Position**
- Phase 1 typography/spacing and Score Analysis + expanded At a Glance checks are in code review/QA.
- API payloads for recall details, usage badges, and odometer sub-checks validated on staging but not yet surfaced.
- Analytics instrumentation + success metrics documented in `docs/analytics-phase2-dashboard-update.md`; Looker build targeted for 2024-07-22.

**Phase 2 Deliverables**
1. **Recall Module:** Expandable list with status chips, copy-to-clipboard IDs, and dealer CTA for open recalls; feature-flagged release.
2. **Usage & Classification Badges:** Chips under VIN header with tooltip modal; ensure multi-usage copy path.
3. **Odometer Sub-Checks:** Expandable breakdown tied to At a Glance row with locale-aware formatting.

**Design & Content Tasks**
- Finalize component specs (padding, typography, iconography) and empty-state copy with Design and Content partners.
- Confirm tooltip phrasing and accessibility guidance for usage badges.
- Produce dark mode specs and redline for new cards.

**Engineering Tasks**
- Extend `AutoCheckReport` to parse recall, usage, class, trim, owner history, and odometer sub-check models.
- Build UI components behind remote config flags (`vehicle_history_recalls`, `vehicle_history_usage`, `vehicle_history_odometer`).
- Add analytics hooks and localization coverage; unit/UI tests for expand/collapse + badge interactions.

**QA & Analytics**
- Regression matrix published in `docs/vin-pid-regression-matrix.md` and synced to TestRail runs TR-1893/TR-1894.
- Accessibility validation (TalkBack focus, dynamic type, contrast) queued for Phase 2 QA execution.
- Looker dashboard updates per `docs/analytics-phase2-dashboard-update.md` with production go-live scheduled 2024-07-30.

**Phase 3 Outlook**
- Owner timeline visualization (per-owner cards, event clusters).
- Interactive score/usage charts and export/share workflows; discovery ongoing.

### Process Variable Infrastructure Refactor

**Accomplishments**
- `TypedProcessVar`/`TypedPvList` wrappers coexist with legacy structures.
- `ObdDataService`, `ObdProt`, and UI adapters migrated to typed APIs.
- Thread-safety improved via `ReentrantReadWriteLock` usage.
- `PvChange` enum/value object prototype delivered with `MainActivity` + VIN helper migration and JVM regression tests (`ProcessVariablesTest`).

**Outstanding Work**
1. **Event Payload Modernization:** Extend `PvChange` adoption beyond pilot listeners and plan removal of bitmask-only code paths.
2. **Retire Raw Map Implementations:** Introduce typed interfaces for PV storage, migrate call sites, and phase out raw `HashMap` inheritance.

**Migration Strategy**
- Add compatibility shims that allow listeners to consume both bitmask and enum payload during transition.
- Prioritize high-traffic listeners (`MainActivity`, `VehicleInfoFooter`, `VinDataHelper`, `EcuManager`, adapters) for early migration.
- Introduce `ProcessVariable<K, V>`/`ProcessVariableList` interfaces; adapt legacy implementations before swapping underlying storage.
- Expand unit/integration coverage to validate enum mapping, threading guarantees, and serialization compatibility.

**Dependencies & Coordination**
- Coordinate with ECU refactor team so new PV interfaces expose data needed by refactored PID/diagnostic modules.
- Document testing touchpoints and ensure QA has instrumentation smoke tests for key dashboard flows.

### ECU Module Refactor

**Goals**
- Establish clear separation between conversion primitives, PID metadata, and diagnostic catalogues.
- Normalize naming/API surface to improve readability and testing.
- Remove shared mutable state and legacy collections to enable deterministic behavior.

**Key Renaming/Restructuring Themes**
- Conversion classes adopt `convertRawToPhysical` / `convertPhysicalToRaw` / `formatPhysicalValue`.
- Catalog classes (`DtcDatabaseCatalog`, `DtcCatalog`, `ResourceBundleDtcCatalog`) clarify data source responsibilities.
- PID definitions split into `PidDefinition` (metadata) and `PidProcessVariable` (runtime value carrier).

**Refactor Steps**
1. **Define Interfaces:** `ValueConversion`, `PidDefinitionRepository`, `DtcCatalog` abstractions decoupled from concrete storage (see `docs/ecu-conversion-abstractions.md`).
2. **Compatibility Layer:** Wrap legacy implementations to satisfy new interfaces while preserving binary compatibility.
3. **Incremental Renames:** Apply renames in modules (`modules/dtc-database`, `app` package) with lint checks ensuring consistency.
4. **Testing Strategy:** Introduce snapshot/parameterized tests for conversions; ensure freeze-frame/file helpers remain compatible.
5. **Dependency Cleanup:** Replace `Messages` resource bundle usage with shared localization layer; review serialization impacts.

**Coordination Points**
- Align with PV refactor timelines—PID runtime objects will rely on new typed PV interfaces.
- Engage QA early to capture conversion accuracy benchmarks and targeted regression suites.
- Architecture review scheduled 2024-07-23 to ratify interface plan and adapter rollout sequencing.

## Sequencing Plan

1. Consolidate asynchronous Phase 2 feedback from Design/Content, publish decisions, and update design/content specs plus engineering backlog accordingly.
2. Expand `PvChange` adoption to VehicleInfoFooter, data services, and adapter layers while sketching `ProcessVariable` interfaces for shared use.
3. Complete (or reschedule) the ECU abstraction architecture review, spin up compatibility adapters, and align QA/analytics checkpoints ahead of the Phase 2 feature flag ramp.

## Risks & Mitigations

- **Design/Content Drift:** Maintain regular syncs with Design/Content to prevent requirements churn; store finalized specs in versioned repository.
- **Feature Flag Coordination:** Maintain rollout checklist ensuring flags default to off in production; document monitoring/rollback procedures.
- **API Contract Changes:** Add schema validation tests for AutoCheck payloads and PV/ECU serialization formats prior to release.
- **Testing Gaps:** Expand automated coverage (Compose, JUnit) before large refactors land; share VIN/PID regression matrix with QA.

## Unified Next Steps

1. **Capture async Vehicle History Phase 2 decisions**, publish an updated spec packet, and reflect action items in the delivery backlog.
2. **Expand `PvChange` adoption** to VehicleInfoFooter, ObdDataService listeners, and adapters while queuing interface design spikes.
3. **Present ECU abstraction proposal** at Architecture Council (originally targeted for 2024-07-23) and translate feedback into implementation tickets once the session is re-slotted.
4. **Support QA execution** of the VIN/PID regression matrix during staging builds; monitor analytics event logging alongside tests.
5. **Build & validate Looker updates** outlined in `docs/analytics-phase2-dashboard-update.md` before feature flag ramp (re-baseline once client events are implemented).
