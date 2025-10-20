# OBD-Droid Unified Planning Notes

## Chunk Assignment Index
- [Agent A – ECU Module Refactor Workstream](agent-ecu-module-refactor.md)
- [Agent B – Platform Enhancement Master Plan](agent-platform-enhancement.md)
- [Agent C – Telemetry & Vehicle History Workstreams](agent-telemetry-and-history.md)

## ECU Module Refactor Notes

> Detailed plan: [agent-ecu-module-refactor.md](agent-ecu-module-refactor.md)

### TODO
- [ ] Draft `RawToPhysicalConverter` + adapter implementation; submit for review to validate interface naming.
- [ ] Sketch data classes for `PidDefinition`/`PidRuntime` and outline migration facade for `EcuDataItem`.
- [ ] Prototype `DtcCatalog` interface and wire both resource + database implementations behind feature flags.
- [ ] Define CI test matrix and create placeholder Gradle tasks so future test suites slot in without friction.

### Class Renaming Matrix

| Current Class (File) | Ideal Name | Suggested Public API Renames |
| --- | --- | --- |
| `BitmapConversion.java` | `BitmaskLookupConversion.java` – clearer that it is a lookup-based conversion keyed by bit patterns | `memToPhys` → `convertRawToPhysical`; `physToMem` → `convertPhysicalToRaw`; `physToPhysFmtString` → `formatPhysicalValue` |
| `Conversion.java` | `ValueConversion.java` (interface) – highlights the intent | Same trio as above (`convertRawToPhysical`, `convertPhysicalToRaw`, `formatPhysicalValue`) |
| `Conversions.java` | `ConversionRegistry.java` – central catalogue of conversions | `memToPhys` → `convertRaw`; `physToMem` → `convertPhysical`; `getUnits` → `getUnitsForConversion`; `physToPhysFmtString` → `formatPhysicalValue` |
| `DTCDatabaseCodeList.java` | `DtcDatabaseCatalog.java` – encapsulates the SQLite-backed DTC catalogue | `get` → `lookup`; `values` → `listCodes`; `setLocale`/`getLocale` → `setLocaleTag`/`getLocaleTag`; `getDatabase` → `getBackingStore` |
| `EcuCodeItem.java` | `DtcRecord.java` – represents a single diagnostic trouble code entry | `getFields` → `getFieldNames`; optionally add `getDisplayLabel` instead of relying on `toString` |
| `EcuCodeList.java` | `DtcCatalog.java` – resource-backed description catalogue | `get` → `lookup`; `values` → `listDescriptions`; `memToPhys` → `interpretRawCode`; `memToString` → `formatRawCode`; `physToMem` → `encodePhysicalCode`; `physToPhysFmtString` → `formatPhysicalCode` |
| `EcuConversions.java` | `EcuConversionRegistry.java` – map from mnemonic → conversion array | `loadFromStream` → `loadDefinitions` |
| `EcuDataItem.java` | `PidDefinition.java` – describes a single PID field, math, and display metadata | `physVal` → `convertRawToPhysical`; `rawVal` → `convertPhysicalToRaw`; `updatePvFomBuffer` → `updateProcessVariableFromResponse` |
| `EcuDataItems.java` | `PidDefinitionRepository.java` – holds all PID definitions per service | `loadFromStream` → `loadDefinitions`; `getPidDataItems` → `getDefinitionsForPid`; `getSvcDataItems` → `getDefinitionsForService`; `appendItemToService` → `addDefinitionToService`; `updateDataItems` → `applyResponseBuffer` |
| `EcuDataPv.java` | `PidProcessVariable.java` – keyed process-variable instance for PID readings | `getUnits` → `resolveUnits`; `getRenderingComponent` → `getRenderer`; `setRenderingComponent` → `setRenderer` |
| `HashConversion.java` | `MappedValueConversion.java` – emphasises discrete mapping | Same conversion-method renames (`convertRawToPhysical`, `convertPhysicalToRaw`, `formatPhysicalValue`) |
| `IntConversion.java` | `IdentityConversion.java` (or `IntegerPassthroughConversion.java`) – passthrough conversion | Same conversion-method renames |
| `LinearConversion.java` | `LinearScaleConversion.java` – expresses the linear transformation | Same conversion-method renames |
| `NumericConversion.java` | `AbstractNumericConversion.java` – abstract base for number conversions | `physToPhysFmtString` → `formatPhysicalValue`; `memToString` → `formatRawValue`; abstract methods adopt new names |
| `ObdCodeItem.java` | `StandardDtcRecord.java` – explicit the record is from standard code sets | Constructors unchanged; no additional public methods |
| `ObdCodeList.java` | `ResourceBundleDtcCatalog.java` – resource-backed singleton | `setDatabaseInstance` → `overrideCatalogInstance`; `getInstance` → `getCatalog` |
| `ObdPid.java` | `PidHandle.java` (or `PidRequestSlot.java`) – wraps PID value & scheduling metadata | `setNextRequest` → `scheduleNextRequestAt`; `getNextRequest` → `getNextRequestAt`; rename static comparator to `NEXT_REQUEST_ORDER` |
| `ObdVidItem.java` | `VehicleIdRecord.java` – Mode 9 vehicle-identification record | `getFields` → `getFieldNames`; `getPidDescription` → `describePid` |
| `TestStatusConversion.java` | `MonitorStatusConversion.java` – decodes J1979 readiness bits | Same conversion-method renames; ensure override of `formatPhysicalValue` is explicit |
| `VagConversion.java` | `VagFormulaConversion.java` – VAG-specific formula-based conversion | Same conversion-method renames; `setMetaNw` → `setNetworkMeta`; `setMetaTblValues` → `setLookupTable` |

### Refactor Objectives

- Stabilize a clear three-layer split: **conversion primitives** (math/lookup), **PID metadata** (definitions + runtime state), and **diagnostic code catalogues** (standard vs vendor).
- Remove shared mutable state (`EcuDataItem.cnvSystem`, static caches in `Conversions`, global `Messages` usage) in favour of injected collaborators and immutable value objects wherever practical.
- Replace legacy collections (`HashMap`, `Vector`, arrays) with typed `Map`/`List` structures and limit raw `Number` surfaces by introducing domain-centric value wrappers (e.g., `PidValue`, `PidUnits`).
- Encapsulate Android-bound concerns (context, logging) behind interfaces so the core module can be unit tested off-device and reused by non-Android consumers.
- Introduce consistent error surfaces: explicit exceptions for data-definition faults, result wrappers for conversion failures, and logging routed through a shared abstraction.

### Component Observations & Actions

#### Conversion Layer (`Conversion` + implementations + `Conversions`)
- `Conversion` currently conflates raw↔physical math with string formatting and unit metadata. Plan: split into a pure math interface (`RawToPhysicalConverter`) plus lightweight mixins for formatting/units; keep backwards compatibility by composing adapters during migration.
- `Conversions` acts as a static registry keyed by integer IDs. Target shape: injectable registry keyed by mnemonic (`PidConversionId`), with enum-backed IDs as compatibility shims. Extract the array literal into data classes or CSV seeds so we can load dynamically.
- `NumericConversion` and descendants rely on `java.util.logging` and raw `Number` returns. Introduce generics (`T extends Number`) or at least centralize conversions on `BigDecimal`/`double` to eliminate unchecked casts. Add unit tests covering rounding/overflow.
- `BitmapConversion` parses `"bit=value"` strings every instantiation. Move parsing to a dedicated DTO, normalise lookup tables once, and memoize translated labels per locale via `Messages`.

#### PID Definition Layer (`EcuDataItem`, `EcuDataItems`, `EcuConversions`, `EcuDataPv`)
- `EcuDataItem` mixes immutable definition state with live reading state (`pv`, error counters). Action: split into `PidDefinition` (immutable) and `PidRuntime` (mutable reading/errors). New class should expose typed accessors (`BitField`, `UpdatePeriod`) instead of raw ints.
- `EcuDataItems` extends `HashMap<Integer, HashMap<Integer, Vector<EcuDataItem>>>`, making call sites unreadable. Replace with richer types: `Map<ServiceId, Map<Pid, List<PidDefinition>>>`. Provide query helpers (`findByMnemonic`, `definitionsFor(ServiceId, Pid)`), and move CSV parsing into a dedicated repository class with dependency injection.
- CSV loading currently assumes resources and splits on tab; we need a streaming parser with schema validation + better error reporting. Capture malformed lines into a diagnostics object for unit tests.
- `EcuDataPv` encapsulates runtime presentation fields. During refactor, treat it as a `PidPresentationModel` that depends on new `PidDefinition`/`PidRuntime` objects and expose builders instead of mutating `Map`.

#### Diagnostic Code Catalogues (`EcuCodeItem`, `EcuCodeList`, `DTCDatabaseCodeList`, `ObdCodeList`)
- Ensure `EcuCodeItem`/`ObdCodeItem` share a single sealed hierarchy (`DtcRecord`, `StandardDtcRecord`, `CustomDtcRecord`). Provide value semantics (equals/hashCode/toString) and UI-ready formatted labels separate from core logic.
- `DTCDatabaseCodeList` logs heavily with Android `Log`. Replace with injected logger; add manufacturer scoping as a strategy object (`DtcLookupScope`) so unit tests can supply fakes.
- Resource-backed `ObdCodeList` and database-backed `DTCDatabaseCodeList` should implement a common interface (`DtcCatalog`). Add caching layer for repeated lookups and unify locale handling (currently stringly typed).

#### Request Scheduling (`ObdPid`, `EcuConversions`)
- `ObdPid` maintains scheduling metadata alongside conversion references. Clarify responsibilities by pulling scheduling into a `PidRequestSchedule` object. Document how `NEXT_REQUEST_ORDER` comparator interacts with new naming.
- `EcuConversions` currently returns raw arrays of `Conversion`. After renaming to `EcuConversionRegistry`, expose typed records (`ConversionBundle`) containing metric/imperial variants, units, and metadata flags (e.g., `isLookup`, `requiresScaling`).

### Phased Migration Outline

1. **Introduce new names alongside adapters**
   - Create alias classes with new names delegating to legacy implementations.
   - Add factory methods (`ConversionRegistry.createLegacy()`), mark old names `@Deprecated`, and add compile-time links so callers can opt into new APIs gradually.
2. **Refine data structures under feature flags**
   - Implement `PidDefinition`/`PidDefinitionRepository` with immutable data and typed lookups.
   - Provide bridging methods that adapt old `Vector<EcuDataItem>` usages to the new structures; write regression tests for CSV parsing and raw↔physical conversions.
3. **Decouple platform concerns**
   - Introduce interfaces for logging, locale translation, and database access.
   - Update Android layer to supply implementations; provide JVM test doubles.
4. **Remove deprecated surface**
   - After callers migrate, delete legacy classes, remove global state, and enforce constructor injection.
   - Add documentation and Kotlin extension wrappers for Android UI consumption.

### Detailed Workstreams

- **Conversion Layer Rewrite**
  - Implement `RawToPhysicalConverter`/`PhysicalValueFormatter` interfaces; supply adapter wrapping existing `Conversion`.
  - Refactor each concrete conversion (`Linear`, `Hash`, `Bitmap`, `TestStatus`, `Vag`) to new interfaces and add focused unit tests.
  - Externalize conversion registry data into structured definitions (CSV/JSON) and write loader converting to `ConversionBundle`.
- **PID Definition Split**
  - Introduce `PidDefinition`, `PidRuntime`, and `PidPresentationModel`; migrate `EcuDataItem` usages behind compatibility facade.
  - Replace nested `HashMap`/`Vector` with immutable collections (`Map<ServiceId, Map<Pid, List<PidDefinition>>>`).
  - Rebuild CSV loader with schema validation and diagnostics; seed repository from resources and support dependency injection for tests.
- **DTC Catalogue Unification**
  - Define `DtcCatalog` interface with async/sync lookup semantics, caching policy, and locale handling.
  - Create `ResourceBundleDtcCatalog` and `DtcDatabaseCatalog` implementations; ensure consistent logging abstraction.
  - Consolidate DTOs (`DtcRecord`, `StandardDtcRecord`, manufacturer variants) and expose formatting helpers.
- **Request Scheduling & Runtime Pipeline**
  - Extract `PidRequestSchedule` from `ObdPid`; document scheduling invariants and comparator semantics.
  - Rework update path (`updateProcessVariableFromResponse`) to consume `PidDefinition` + new conversion APIs.
  - Audit error handling/logging; funnel through shared telemetry interface for analytics.
- **Cross-Cutting Concerns**
  - Provide logging, localization, and database access abstractions with Android + JVM implementations.
  - Update dependency injection graph (Dagger/Hilt or manual) to wire new components.
  - Document migration path for downstream callers (UI, services) including Kotlin extension helpers.

### Dependency & Sequencing Map

- `Conversion` refactor (Phase 1) must complete before `PidDefinition` split to avoid touchpoints with legacy API.
- CSV loader modernization depends on conversion registry adapter being in place (ensures new loader can resolve conversion mnemonics).
- `DtcCatalog` interface can land in parallel with early conversion work, but UI integration waits until `PidPresentationModel` exists.
- Request scheduling updates require `PidRuntime` to own timing metadata; sequence after definition split.
- Cross-cutting logging/localization abstractions should land early to unblock unit testing across all workstreams.

### Testing & Validation Strategy

- **Unit Tests**
  - Conversion math suites using captured CSV fixtures and boundary values (min/max, overflow cases).
  - PID definition parsing tests validating schema errors, optional fields, and localization hooks.
  - DTC lookup tests with in-memory database and resource bundles to ensure locale fallbacks.
- **Integration Tests**
  - Simulated PID polling pipeline: feed raw frames, assert formatted output and update cadence.
  - Android instrumentation smoke test verifying DI wiring and database access in `DtcDatabaseCatalog`.
- **Regression & Snapshot Tests**
  - Snapshot expected formatted outputs (JSON/CSV) for representative PIDs; compare pre-/post-refactor.
  - Record scheduler trace logs to assert ordering stability when porting `ObdPid`.
- **Tooling**
  - Add Gradle tasks for lint/unit/integration groups (`:core:ecu:testConversions`, etc.).
  - Integrate with CI to run parameterized suites on PR; publish coverage deltas for conversions package.

### Tooling & Documentation Updates

- Extend developer docs with architecture diagrams (conversion pipeline, PID lifecycle) and migration checklist.
- Provide sample Kotlin/Java usage snippets for new APIs in `/docs/examples`.
- Update ProGuard/R8 keep rules to preserve compatibility facades during transition.
- Add logging guidelines to ensure unified tag/structured payload usage across modules.

### Immediate Action Items

- Draft `RawToPhysicalConverter` + adapter implementation; submit for review to validate interface naming.
- Sketch data classes for `PidDefinition`/`PidRuntime` and outline migration facade for `EcuDataItem`.
- Prototype `DtcCatalog` interface and wire both resource + database implementations behind feature flags.
- Define CI test matrix and create placeholder Gradle tasks so future test suites slot in without friction.

### Open Questions / Pending Investigations

- Confirm whether `Messages` (resource bundle) can be replaced with the shared localization layer from `modules/dtc-database`; ensure no duplicate translations.
- Do we need to preserve binary compatibility for external plugins using `EcuDataItem`? If yes, consider shading new APIs or providing ProGuard keep rules.
- How should the refactored registry expose units for composite conversions (e.g., metric base unit vs imperial override)? Decide between `Unit` enum vs plain string.
- What is the testing strategy for conversion accuracy? Proposal: snapshot tests seeded from CSV + parameterized JUnit suites per conversion type.
- Investigate whether `modules/dtc-database` already offers a data-contract we can lean on for the DTC catalogue surface, to avoid duplicate DTOs.

## Platform Enhancement Master Plan

> Detailed plan: [agent-platform-enhancement.md](agent-platform-enhancement.md)

### TODO
- [ ] Schedule cross-team workshop to finalize Vehicle History Phase 2 UI/UX and copy.
- [ ] Implement `PvChange` prototype and migrate an initial listener to validate the new event flow.
- [ ] Author abstraction proposals for ECU conversion/catalog interfaces and review with architecture stakeholders.
- [ ] Compile VIN/PID regression matrices and distribute to QA.
- [ ] Update analytics dashboards once Phase 2 features roll out.

### Executive Summary

- **Vehicle History Experience (Customer-facing):** Phase 1 polish is code-complete; Phase 2 focuses on recall, usage, and odometer surfacing with cross-team alignment in progress. Phase 3 (owner timelines + advanced visualizations) remains in discovery.
- **Process Variable (PV) Infrastructure Refactor (Core telemetry):** Typed wrappers are deployed; remaining work centers on introducing a richer event payload and retiring raw map implementations.
- **ECU Module Refactor (Diagnostics domain):** Naming, API normalization, and layering strategy outlined; awaiting execution roadmap that dovetails with PV changes to minimize churn.

### Initiative Overview

| Initiative | Objective | Current Status | Upcoming Milestone |
| --- | --- | --- | --- |
| Vehicle History Experience | Deliver richer AutoCheck insights with accessible UI/analytics coverage | Phase 1 complete; Phase 2 requirements staged | Finalize Phase 2 design/content specs and implement gated features |
| PV Infrastructure Refactor | Modernize PV eventing + storage for type safety and maintainability | Typed lists live; event bitmask + raw maps pending | Implement `PvChange` enum payload and migrate top listeners |
| ECU Module Refactor | Clean up conversion/catalog APIs and remove shared mutable state | Discovery + naming audit complete | Build abstraction interfaces and begin renames with compatibility layer |

### Cross-Team Alignment

- **Design & Content:** Schedule joint review (Vehicle History + Design + Content) to lock recall list layouts, usage badge language, odometer guidance copy, and tooltip patterns. Capture sign-off artifacts in Figma + Confluence.
- **Analytics/Data:** Define event taxonomy (`vehicle_history_recall_expand`, `vehicle_history_usage_badge_tap`, `vehicle_history_odometer_expand`) and dashboard requirements; ensure PV/ECU instrumentations do not regress existing tracking.
- **QA/Test Automation:** Create shared VIN/PID regression matrix covering Vehicle History edge cases, PV event transitions, and ECU conversion scenarios. Align on automated coverage (Espresso/Compose + JUnit parameterized suites).
- **Architecture Council:** Present combined roadmap to confirm sequencing (PV event overhaul should precede ECU refactor that leans on new interfaces).

### Initiative Detail

#### Vehicle History Experience Refresh

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

#### Process Variable Infrastructure Refactor

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

#### ECU Module Refactor

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

### Sequencing & Timeline (Proposed)

1. **Sprint 1-2:** Lock Vehicle History Phase 2 designs/content; begin recall module implementation behind feature flag. Draft `PvChange` model and migrate one pilot listener.
2. **Sprint 3-4:** Complete Vehicle History usage badges + odometer module; roll out analytics. Expand PV migration to remaining listeners; introduce PV interface abstractions.
3. **Sprint 5-6:** Initiate ECU refactor renames leveraging stabilized PV infrastructure; build conversion tests and catalog interfaces. Kick off Vehicle History Phase 3 discovery workshops.

### Risks & Mitigations

- **Design/Content Drift:** Weekly sync with Design/Content to prevent requirements churn; store finalized specs in versioned repository.
- **Feature Flag Coordination:** Maintain rollout checklist ensuring flags default to off in production; document monitoring/rollback procedures.
- **API Contract Changes:** Add schema validation tests for AutoCheck payloads and PV/ECU serialization formats prior to release.
- **Testing Gaps:** Expand automated coverage (Compose, JUnit) before large refactors land; share VIN/PID regression matrix with QA.

### Unified Next Steps

1. **Schedule cross-team workshop** (Design, Content, Engineering, Analytics) to finalize Vehicle History Phase 2 UI/UX and associated copy.
2. **Implement `PvChange` prototype** and convert `MainActivity` listener to establish migration patterns.
3. **Author abstraction proposals** for ECU conversion/catalog interfaces and review with architecture stakeholders.
4. **Compile regression matrices** (VINs, PID datasets) and distribute to QA to prepare for upcoming launches.
5. **Update analytics dashboards** in coordination with Data team to track engagement once Phase 2 features roll out.

## Process Variable Refactor Progress

> Detailed plan: [agent-telemetry-and-history.md](agent-telemetry-and-history.md)

### TODO
- [x] Introduce typed wrappers for `ProcessVar`/`PvList` – `TypedProcessVar`/`TypedPvList` live alongside the legacy structures.
- [x] Migrate `ObdDataService` and `ObdProt` to typed APIs – service-backed stores now expose `TypedPvList` instances while keeping compatibility shims in place.
- [x] Update adapters/activities to consume typed PVs – UI helpers retrieve strongly typed items from the shared lists.
- [x] Add threading/locking improvements – `ProcessVar` now uses a `ReentrantReadWriteLock` to guard bulk updates and listener dispatch.
- [ ] Replace event bitmask with richer model – callers still rely on `PvChangeEvent` integer masks.
- [ ] Remove legacy raw-map classes once migration completes – the base classes still extend raw `HashMap`.

### Status at a Glance
- [x] Introduce typed wrappers for `ProcessVar`/`PvList` – `TypedProcessVar`/`TypedPvList` live alongside the legacy structures to give covariant helpers during migration.
- [x] Migrate `ObdDataService` and `ObdProt` to typed APIs – service-backed stores now expose `TypedPvList` instances while keeping compatibility shims in place.
- [x] Update adapters/activities to consume typed PVs – UI helpers retrieve strongly typed items from the shared lists.
- [x] Add threading/locking improvements – `ProcessVar` now uses a `ReentrantReadWriteLock` to guard bulk updates and listener dispatch.
- [ ] Replace event bitmask with richer model – callers still rely on `PvChangeEvent` integer masks.
- [ ] Remove legacy raw-map classes once migration completes – the base classes still extend raw `HashMap`.

### Completed Milestones (Code References)
- **Typed wrappers introduced:** `ProcessVariables.TypedProcessVar` and `ProcessVariables.TypedPvList` wrap the raw map while exposing generic-friendly helpers (see `app/src/java/com/obddroid/core/pvs/ProcessVariables.java`).
- **Service and protocol bound to typed lists:** `ObdDataService` owns typed stores per service, and `ObdProt` exposes `TypedPvList` facades that proxy into the service (see `app/src/java/com/obddroid/services/ObdDataService.java` and `app/src/java/com/obddroid/core/obd/ObdProt.java`).
- **UI migration in place:** Activities/components pull data via typed accessors (for example `VehicleInfoFooter`, `VinDataHelper`, `FuelEconomyActivity`) which confirms the wrappers behave as intended.
- **Thread-safety upgrades applied:** `ProcessVar.putAll`/`put` now acquire a write lock before mutating state, reducing listener-race issues that used to surface under concurrent updates.

### Current Architecture Notes
- Legacy and typed pathways coexist. The wrappers are thin shells over the raw map, so we still rely on unchecked casts but the call sites now document their intent.
- All listeners attach to the shared `TypedPvList` instances exposed through `ObdProt`. Those lists shadow updates into the central `ObdDataService`, so any future structural change must keep that mirror in sync.
- `PvChangeEvent` remains the main notification primitive. Every high-level feature (VIN detection, dashboard widgets, charts, etc.) currently switches on the integer bitmask delivered by that class.

### Outstanding Work – Replace Event Bitmask

#### Current Pain Points
- The bitmask encodes multiple semantics (action vs. child update) in a single `int`, which obscures intent and keeps listeners tightly coupled to the legacy constants.
- Many listeners only care about a subset of events but must reason about bit arithmetic (`&` checks), making mistakes easy and testing harder.
- Extending the event surface area (new lifecycle states, payload metadata) is risky because it would require inventing new bits that existing code might mishandle.

#### High-Traffic Call Sites
- `MainActivity` dashboard refresh logic and VIN detection (switch statement on `PvChangeEvent` type).
- `VehicleInfoFooter`, `VinDataHelper`, and `EcuManager` VIN propagation flows.
- `FuelEconomyActivity`, `EmissionsActivity`, and `DashBoardActivity` UI updaters.
- `ObdItemAdapter` chart synchronization and list adapters.
- Utility code (`FileHelper`, `ObdDataService`) that mirrors data between stores.

#### Proposed Migration Plan
1. **Define a richer event payload:** Introduce `PvChangeType` (enum) plus a `PvChange` value object that exposes `source`, `key`, `value`, `changeType`, and optional metadata (origin, child flag, timestamps).
2. **Add compatibility shims:** Extend `PvChangeEvent` to carry the new payload while still exposing the old `int` mask. During the transition, listeners can opt-in to the richer API without breaking existing code.
3. **Update listeners incrementally:** Convert the high-traffic listeners listed above to the new enum-based API. Provide helper methods that map legacy constants to the new types to avoid duplicated mapping logic.
4. **Deprecate mask usage:** Once call sites no longer depend on integer masks, deprecate bitwise accessors and eventually remove the constants in favour of `EnumSet<PvChangeType>`.
5. **Tighten tests/telemetry:** Add unit tests (where feasible) for the new event class and smoke-test key activities to ensure the enum mapping still fires UI updates as expected.

#### Flag-to-Enum Mapping (Initial Draft)

| Legacy Flag | Proposed Enum | Notes |
| --- | --- | --- |
| `PV_ADDED` | `ADDED` | Fired on first insert of a PV or attribute. |
| `PV_MODIFIED` | `UPDATED` | Covers updates to existing values. |
| `PV_DELETED` | `REMOVED` | Rare today but mapped directly. |
| `PV_CLEARED` | `CLEARED` | Signals a bulk clear; keep payload optional. |
| `PV_CONFIRMED` | `CONFIRMED` | Consider modelling as a secondary attribute on the event (e.g. `EnumSet` flag). |
| `PV_MANUAL_MOD` | `MANUAL_OVERRIDE` | Distinct type so UI can show manual edits. |
| `PV_ERROR` | `ERROR` | Triggers error states in UI if needed. |
| `PV_CHILDCHANGE` | `CHILD_CHANGED` | Instead of a bitmask, expose a boolean flag or dedicated enum and include child payload. |

### Outstanding Work – Retire Raw Map Implementations

#### Current Usage Snapshot
- `ProcessVar` and `PvList` still extend raw `HashMap` and expose loosely typed getters/setters.
- Domain classes (`EcuDataPv`, dashboards, adapters) depend on the ability to stash arbitrary extras in the map, which complicates a straight refactor to generics.
- Serialization/deserialization paths (freeze frame cache, file helpers) assume `Map<Object, Object>` semantics.

#### Proposed Path Forward
1. **Introduce an interface-first abstraction:** Define `ProcessVariable<K, V>` and `ProcessVariableList<K, PV>` interfaces that describe the minimal surface we need (typed `get/put`, listener hooks, iteration).
2. **Wrap legacy implementations:** Make `ProcessVar`/`PvList` implement those interfaces and keep the raw map internally until all clients switch to the interface.
3. **Update call sites gradually:** Migrate consumers to the interfaces, adding generic type parameters where possible (`EcuDataPv` already has fixed fields we can model explicitly).
4. **Constrain payload shapes:** Once callers compile against the typed interfaces, tighten the backing storage (e.g. convert to `LinkedHashMap<K, V>` with concrete key/value types) and remove raw-map methods.
5. **Remove legacy aliases:** After the generics migration completes, delete or inline the legacy `TypedProcessVar`/`TypedPvList` wrappers and collapse onto a single typed implementation.

#### Considerations & Risks
- Some adapters rely on storing UI-only payloads inside PV objects (charts, view handles). We need to audit each usage and either formalise those fields or provide a dedicated attachment mechanism.
- Serialization compatibility matters: ensure that new typed implementations preserve the on-disk shape or provide migration utilities.
- Android components rely on being notified on the main thread; any new abstraction should keep existing threading guarantees or clearly document changes.

### Immediate Next Steps
1. Draft the `PvChangeType`/`PvChange` classes and add compatibility constructors in `ProcessVariables`.
2. Convert one representative listener (suggested: `MainActivity`) to exercise the new event API and validate the mapping.
3. Sketch the `ProcessVariable` interface and evaluate the impact on `EcuDataPv` plus the adapter layer.
4. Document testing touchpoints for the above changes (unit test harness or instrumentation smoke tests).

## Vehicle History Page Enhancement Plan

> Detailed plan: [agent-telemetry-and-history.md](agent-telemetry-and-history.md)

### TODO
- [ ] Implement Phase 2 UI (recall module, usage badges, odometer sub-checks) behind feature flags.
- [ ] Finalize Phase 2 UX copy and localized strings across new components.
- [ ] Instrument analytics events (`vehicle_history_recall_expand`, `vehicle_history_usage_badge_tap`, `vehicle_history_odometer_expand`).
- [ ] Build VIN-based regression matrix and execute QA pass covering new data permutations.
- [ ] Prototype Phase 3 owner timeline concepts and validate data gaps with stakeholders.

> **Program context:** This document feeds into the platform enhancement master plan, which aggregates cross-initiative status (Vehicle History, Process Variable refactor, ECU module refactor). Update both when scope changes.

### Current Status

- Phase 1 deliverables (readability tweaks, Score Analysis card, expanded At a Glance checks) have been implemented and are pending final QA sign-off before public release.
- API payloads for recall details, vehicle usage/classification, and odometer sub-checks are available in staging responses; UI mapping and polish remain outstanding.
- Owner history data requires new UI surfaces and timeline interactions; discovery continues as part of Phase 3 scope definition.
- Analytics instrumentation and success criteria are not yet defined for the newly surfaced data; coordination with Data/Analytics is needed.

### Available But Not Displayed Data

#### 1. Score Analysis (HIGH PRIORITY)
**Status:** Completed (Phase 1)

**API Fields:**
- `vehicleComparison`: How this vehicle compares to similar vehicles
- `vehicleOutlook`: Future prognosis/outlook
- `increasingFactors[]`: Array of factors increasing the score
- `decreasingFactors[]`: Array of factors decreasing the score

**UI Implementation:**
- Add expandable "Score Analysis" card below the score circle
- Show comparison text in highlighted box
- List increasing factors with green checkmarks
- List decreasing factors with warning icons

**Follow-up Work:**
- Add analytics events for expand/collapse and CTA taps.
- Validate fallback copy when API omits comparison or outlook strings.
- Ensure dark mode theme tokens match updated color palette.

#### 2. Comprehensive "At a Glance" Checks (HIGH PRIORITY)
**Status:** Completed (Phase 1)

**API Fields (atAGlance object):**
- State Title Brand Check (status, count)
- Auction Brand Issues
- Open Recall Check
- Insurance Loss Transfer
- Odometer Check (with sub-checks)
- Certified Pre-Owned status
- Service/Repair summary
- Additional History

**Current State:** Only showing 5 items (Title, Accident, Structural, Airbag, Rollback)  
**Enhancement:** Show all 9 comprehensive checks with status indicators

**Follow-up Work:**
- Update empty-state copy when `statusType` is `UNKNOWN`.
- QA pass for TalkBack and dynamic type.
- Confirm color usage meets AA contrast for warning states.

#### 3. Detailed Recall Information (MEDIUM PRIORITY)
**Status:** In progress (Phase 2)

**API Fields:**
- `recallDetails[]`: Array of recall objects with:
  - Recall date and type
  - NHTSA recall number
  - OEM recall number
  - Campaign description
  - Current status

**UI Implementation:**
- Expandable recall list with summary row showing date, type, and a status chip.
- Each expanded row displays campaign description, NHTSA/OEM numbers (tap to copy), and resolution guidance.
- Surface "Contact dealer" CTA when status is `OPEN`.
- Provide empty-state card that educates users when no recalls exist.

**Engineering Tasks:**
- [ ] Extend `AutoCheckReport` parsing to populate `List<RecallDetail>`.
- [ ] Build `RecallDetailAdapter` with diff util and view binding.
- [ ] Gate the UI behind remote feature flag `vehicle_history_recalls`.
- [ ] Add deep link support to NHTSA recall look-up when `nhtsaRecallNo` is present.

**QA Considerations:**
- Verify chronological sort (newest first) and formatting for missing dates.
- Confirm behavior when API returns null/empty arrays.
- Ensure copy/CTA is localized and works with long strings.

**Open Questions:**
- Do we display resolved recalls older than five years?
- Should we surface manufacturer contact info when provided in payload?

#### 4. Vehicle Usage & Classification (LOW PRIORITY)
**Status:** Not started (Phase 2)

**API Fields:**
- `vehicleUsage`: "Personal", "Lease", "Rental", "Fleet", etc.
- `vehicleClass`: Classification
- `trim`: Trim level

**UI Implementation:**
- Display usage badges directly under the VIN/vehicle summary with color-coded chips.
- Show classification and trim as secondary text; collapse into single line on small devices.
- Provide tooltip modal explaining implications of fleet/rental classifications.

**Engineering Tasks:**
- [ ] Map the new fields in data layer and expose via `VehicleHistoryViewState`.
- [ ] Create reusable `UsageBadgeView` component supporting multiple usages.
- [ ] Add instrumentation for badge taps (opens tooltip).
- [ ] Ensure legacy devices gracefully wrap long trim names.

**Open Questions:**
- Confirm copy for multi-usage vehicles (e.g., "Personal → Fleet").
- Determine if we need icons for each usage type.

#### 5. Owner History Details (LOW PRIORITY)
**Status:** Discovery (Phase 3)

**API Fields:**
- `ownerHistory[]`: Per-owner details with dates, location, usage, events

**UI Implementation:**
- Timeline component showing each owner with start/end dates, location, and usage tags.
- Expandable rows to show notable events (service, title transfers).
- Provide quick summary banner (e.g., "3 owners over 8 years, last owned in CA").

**Engineering Tasks:**
- [ ] Design timeline component that reuses existing `VehicleEventView`.
- [ ] Add pagination/fetch guard for long histories.
- [ ] Handle missing location data with fallback copy.

**Research Items:**
- Audit data fidelity for older vehicles (pre-1990) to determine if we need disclaimers.
- Explore whether we can cluster events by owner for clarity.

#### 6. Odometer Sub-Checks (LOW PRIORITY)
**Status:** Not started (Phase 2)

**API Fields:**
- State Title Odometer Check
- Auction Odometer Check
- Odometer Calculation Check

**UI Implementation:**
- Convert existing odometer row into an expandable module showing each sub-check with iconography.
- Highlight failing sub-checks in amber with action guidance.
- For clean records, keep module collapsed with summary ("No odometer discrepancies detected").

**Engineering Tasks:**
- [ ] Parse odometer sub-check payloads into new `OdometerChecks` model.
- [ ] Build expandable UI linked to At a Glance row.
- [ ] Add UI unit tests covering pass/fail/unknown permutations.

**QA Considerations:**
- Validate copy for unknown/missing statuses.
- Confirm formatting for large mileage values (locale-aware separators).

### Readability Improvements

#### Typography
- Increase body text: 14sp → 15sp
- Increase section headers: 18sp → 20sp
- Increase card titles: 24sp → 26sp
- Add more line spacing for easier reading

#### Spacing
- Increase card margins: 12dp → 16dp
- Add more padding in dense sections
- Better separation between timeline events

#### Color Contrast
- Improve secondary text contrast
- Add subtle backgrounds to differentiate sections

### Implementation Priority

**Phase 1 (Immediate):**
1. ✅ Readability improvements (typography & spacing)
2. ✅ Score Analysis section
3. ✅ Enhanced "At a Glance" checks

**Phase 2 (Next):**
1. Detailed Recall expansion
2. Vehicle Usage badges
3. Odometer sub-checks

**Phase 3 (Future):**
1. Per-owner history timeline
2. Interactive charts/graphs
3. Export/share enhancements

### Phase 2 Work Breakdown

1. Finalize UX copy and visual design for recall list, usage badges, and odometer module (Design).
2. Extend data/model layer to support recall details, usage/classification, and odometer sub-checks (Engineering).
3. Build UI components behind configurable feature flags and integrate analytics hooks (Engineering).
4. Author unit/UI tests and create VIN-based regression matrix covering new edge cases (Engineering/QA).
5. Run staging regression with production-like payloads and capture sign-off from QA + Product.

### QA & Validation Plan

- Define VIN test matrix covering combinations of recalls, usage types, odometer discrepancies, and clean histories.
- Add Espresso/Compose tests for recall list expansion, badge rendering, and odometer accordion states.
- Verify accessibility: TalkBack focus order, dynamic type scaling, and contrast ratios.
- Confirm analytics events fire via debug console and are documented for Analytics handoff.

### Analytics & Success Metrics

- Events: `vehicle_history_recall_expand`, `vehicle_history_usage_badge_tap`, `vehicle_history_odometer_expand`.
- KPIs: percentage of sessions interacting with new modules, dwell time on Vehicle History page, tap-through rate on "Contact dealer" CTA.
- Dashboards: add Looker tiles to existing Vehicle History report, with weekly monitoring during launch window.

### Risks & Mitigations

- **API Contract Drift:** Mitigate with null-safe parsing and staging contract tests before release.
- **UI Density:** Use progressive disclosure (expanders) and responsive layout checks on small devices.
- **Feature Flag Rollout:** Coordinate gradual rollout with remote config and monitoring to throttle if issues arise.

### Data Model Changes Needed

```java
// Add to AutoCheckReport.java
private String vehicleComparison;
private String vehicleOutlook;
private List<String> increasingFactors;
private List<String> decreasingFactors;
private String vehicleUsage;
private String vehicleClass;
private String trim;
private AtAGlance atAGlance;
private List<RecallDetail> recallDetails;
private List<OwnerRecord> ownerHistory;
private OdometerChecks odometerChecks;

// Consider exposing helper getters for null-safe UI consumption.
public static class AtAGlance {
    public GlanceCheck stateTitleBrand;
    public GlanceCheck auctionBrandIssues;
    public GlanceCheck accidentDamage;
    public GlanceCheck openRecallCheck;
    public GlanceCheck insuranceLossTransfer;
    public GlanceCheck odometerCheck;
    public GlanceCheck certifiedPreOwned;
    public GlanceCheck serviceRepairSummary;
    public GlanceCheck additionalHistory;
}

public static class GlanceCheck {
    public String status;
    public String statusType;
    public String subtitle;
    public Integer count;
}

public static class RecallDetail {
    public String recallDate;
    public String recallType;
    public String nhtsaRecallNo;
    public String oemRecallNo;
    public String campaignDescription;
    public String status;
}

public static class OwnerRecord {
    public int ownerNumber;
    public String ownershipPeriod;
    public String location;
    public String usage;
    public List<String> notableEvents;
}

public static class OdometerChecks {
    public GlanceCheck stateTitle;
    public GlanceCheck auction;
    public GlanceCheck calculation;
}
```

### Estimated Impact

- **User Value:** ⭐⭐⭐⭐⭐ (Significantly more comprehensive)
- **Development Time:** 4-6 hours
- **Complexity:** Medium
- **Risk:** Low (additive only, no breaking changes)

### Next Steps

1. Align with Design and Content on Phase 2 UI/UX specifications (recall, usage, odometer modules).
2. Implement data parsing and gated UI for recall details, usage badges, and odometer sub-checks.
3. Define and instrument analytics events with Data/Analytics team.
4. Execute Phase 2 QA checklist, including VIN matrix regression and accessibility validation.
5. Start Phase 3 discovery by prototyping owner timeline concepts and identifying data gaps.
