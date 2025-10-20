# Platform Enhancement Master Plan

**Assigned Agent:** Agent B

## TODO
- [ ] Schedule cross-team workshop to finalize Vehicle History Phase 2 UI/UX and copy.
- [ ] Implement `PvChange` prototype and migrate an initial listener to validate the new event flow.
- [ ] Author abstraction proposals for ECU conversion/catalog interfaces and review with architecture stakeholders.
- [ ] Compile VIN/PID regression matrices and distribute to QA.
- [ ] Update analytics dashboards once Phase 2 features roll out.

## Executive Summary

- **Vehicle History Experience (Customer-facing):** Phase 1 polish is code-complete; Phase 2 focuses on recall, usage, and odometer surfacing with cross-team alignment in progress. Phase 3 (owner timelines + advanced visualizations) remains in discovery.
- **Process Variable (PV) Infrastructure Refactor (Core telemetry):** Typed wrappers are deployed; remaining work centers on introducing a richer event payload and retiring raw map implementations.
- **ECU Module Refactor (Diagnostics domain):** Naming, API normalization, and layering strategy outlined; awaiting execution roadmap that dovetails with PV changes to minimize churn.

## Initiative Overview

| Initiative | Objective | Current Status | Upcoming Milestone |
| --- | --- | --- | --- |
| Vehicle History Experience | Deliver richer AutoCheck insights with accessible UI/analytics coverage | Phase 1 complete; Phase 2 requirements staged | Finalize Phase 2 design/content specs and implement gated features |
| PV Infrastructure Refactor | Modernize PV eventing + storage for type safety and maintainability | Typed lists live; event bitmask + raw maps pending | Implement `PvChange` enum payload and migrate top listeners |
| ECU Module Refactor | Clean up conversion/catalog APIs and remove shared mutable state | Discovery + naming audit complete | Build abstraction interfaces and begin renames with compatibility layer |

## Cross-Team Alignment

- **Design & Content:** Schedule joint review (Vehicle History + Design + Content) to lock recall list layouts, usage badge language, odometer guidance copy, and tooltip patterns. Capture sign-off artifacts in Figma + Confluence.
- **Analytics/Data:** Define event taxonomy (`vehicle_history_recall_expand`, `vehicle_history_usage_badge_tap`, `vehicle_history_odometer_expand`) and dashboard requirements; ensure PV/ECU instrumentations do not regress existing tracking.
- **QA/Test Automation:** Create shared VIN/PID regression matrix covering Vehicle History edge cases, PV event transitions, and ECU conversion scenarios. Align on automated coverage (Espresso/Compose + JUnit parameterized suites).
- **Architecture Council:** Present combined roadmap to confirm sequencing (PV event overhaul should precede ECU refactor that leans on new interfaces).

## Initiative Detail

### Vehicle History Experience Refresh

**Current Position**
- Phase 1 typography/spacing and Score Analysis + expanded At a Glance checks are in code review/QA.
- API payloads for recall details, usage badges, and odometer sub-checks validated on staging but not yet surfaced.
- Analytics instrumentation + success metrics not yet formalized.

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
- VIN matrix covering: vehicles with multiple recalls, vehicles with mixed usage history, odometer discrepancies, clean records.
- Accessibility validation (TalkBack focus, dynamic type, contrast).
- Dashboard updates in Looker with weekly monitoring post-launch.

**Phase 3 Outlook**
- Owner timeline visualization (per-owner cards, event clusters).
- Interactive score/usage charts and export/share workflows; discovery ongoing.

### Process Variable Infrastructure Refactor

**Accomplishments**
- `TypedProcessVar`/`TypedPvList` wrappers coexist with legacy structures.
- `ObdDataService`, `ObdProt`, and UI adapters migrated to typed APIs.
- Thread-safety improved via `ReentrantReadWriteLock` usage.

**Outstanding Work**
1. **Event Payload Modernization:** Replace `PvChangeEvent` bitmask with `PvChangeType` enum + `PvChange` value object.
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
1. **Define Interfaces:** `ValueConversion`, `PidDefinitionRepository`, `DtcCatalog` abstractions decoupled from concrete storage.
2. **Compatibility Layer:** Wrap legacy implementations to satisfy new interfaces while preserving binary compatibility.
3. **Incremental Renames:** Apply renames in modules (`modules/dtc-database`, `app` package) with lint checks ensuring consistency.
4. **Testing Strategy:** Introduce snapshot/parameterized tests for conversions; ensure freeze-frame/file helpers remain compatible.
5. **Dependency Cleanup:** Replace `Messages` resource bundle usage with shared localization layer; review serialization impacts.

**Coordination Points**
- Align with PV refactor timelines—PID runtime objects will rely on new typed PV interfaces.
- Engage QA early to capture conversion accuracy benchmarks and targeted regression suites.

## Sequencing & Timeline (Proposed)

1. **Sprint 1-2:** Lock Vehicle History Phase 2 designs/content; begin recall module implementation behind feature flag. Draft `PvChange` model and migrate one pilot listener.
2. **Sprint 3-4:** Complete Vehicle History usage badges + odometer module; roll out analytics. Expand PV migration to remaining listeners; introduce PV interface abstractions.
3. **Sprint 5-6:** Initiate ECU refactor renames leveraging stabilized PV infrastructure; build conversion tests and catalog interfaces. Kick off Vehicle History Phase 3 discovery workshops.

## Risks & Mitigations

- **Design/Content Drift:** Weekly sync with Design/Content to prevent requirements churn; store finalized specs in versioned repository.
- **Feature Flag Coordination:** Maintain rollout checklist ensuring flags default to off in production; document monitoring/rollback procedures.
- **API Contract Changes:** Add schema validation tests for AutoCheck payloads and PV/ECU serialization formats prior to release.
- **Testing Gaps:** Expand automated coverage (Compose, JUnit) before large refactors land; share VIN/PID regression matrix with QA.

## Unified Next Steps

1. **Schedule cross-team workshop** (Design, Content, Engineering, Analytics) to finalize Vehicle History Phase 2 UI/UX and associated copy.
2. **Implement `PvChange` prototype** and convert `MainActivity` listener to establish migration patterns.
3. **Author abstraction proposals** for ECU conversion/catalog interfaces and review with architecture stakeholders.
4. **Compile regression matrices** (VINs, PID datasets) and distribute to QA to prepare for upcoming launches.
5. **Update analytics dashboards** in coordination with Data team to track engagement once Phase 2 features roll out.
