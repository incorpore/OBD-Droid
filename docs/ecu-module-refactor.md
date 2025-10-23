# ECU Module Refactor Workstream

## TODO
- [x] Draft `RawToPhysicalConverter` + adapter implementation; submit for review to validate interface naming.
- [x] Sketch data classes for `PidDefinition`/`PidRuntime` and outline migration facade for `EcuDataItem`.
- [x] Prototype `DtcCatalog` interface and wire both resource + database implementations behind feature flags.
- [x] Define CI test matrix and create placeholder Gradle tasks so future test suites slot in without friction.

## Class Renaming Matrix

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

## Refactor Objectives

- Stabilize a clear three-layer split: **conversion primitives** (math/lookup), **PID metadata** (definitions + runtime state), and **diagnostic code catalogues** (standard vs vendor).
- Remove shared mutable state (`EcuDataItem.cnvSystem`, static caches in `Conversions`, global `Messages` usage) in favour of injected collaborators and immutable value objects wherever practical.
- Replace legacy collections (`HashMap`, `Vector`, arrays) with typed `Map`/`List` structures and limit raw `Number` surfaces by introducing domain-centric value wrappers (e.g., `PidValue`, `PidUnits`).
- Encapsulate Android-bound concerns (context, logging) behind interfaces so the core module can be unit tested off-device and reused by non-Android consumers.
- Introduce consistent error surfaces: explicit exceptions for data-definition faults, result wrappers for conversion failures, and logging routed through a shared abstraction.

## Component Observations & Actions

### Conversion Layer (`Conversion` + implementations + `Conversions`)
- `Conversion` currently conflates raw↔physical math with string formatting and unit metadata. Plan: split into a pure math interface (`RawToPhysicalConverter`) plus lightweight mixins for formatting/units; keep backwards compatibility by composing adapters during migration.
- `Conversions` acts as a static registry keyed by integer IDs. Target shape: injectable registry keyed by mnemonic (`PidConversionId`), with enum-backed IDs as compatibility shims. Extract the array literal into data classes or CSV seeds so we can load dynamically.
- `NumericConversion` and descendants rely on `java.util.logging` and raw `Number` returns. Introduce generics (`T extends Number`) or at least centralize conversions on `BigDecimal`/`double` to eliminate unchecked casts. Add unit tests covering rounding/overflow.
- `BitmapConversion` parses `"bit=value"` strings every instantiation. Move parsing to a dedicated DTO, normalise lookup tables once, and memoize translated labels per locale via `Messages`.

### PID Definition Layer (`EcuDataItem`, `EcuDataItems`, `EcuConversions`, `EcuDataPv`)
- `EcuDataItem` mixes immutable definition state with live reading state (`pv`, error counters). Action: split into `PidDefinition` (immutable) and `PidRuntime` (mutable reading/errors). New class should expose typed accessors (`BitField`, `UpdatePeriod`) instead of raw ints.
- `EcuDataItems` extends `HashMap<Integer, HashMap<Integer, Vector<EcuDataItem>>>`, making call sites unreadable. Replace with richer types: `Map<ServiceId, Map<Pid, List<PidDefinition>>>`. Provide query helpers (`findByMnemonic`, `definitionsFor(ServiceId, Pid)`), and move CSV parsing into a dedicated repository class with dependency injection.
- CSV loading currently assumes resources and splits on tab; we need a streaming parser with schema validation + better error reporting. Capture malformed lines into a diagnostics object for unit tests.
- `EcuDataPv` encapsulates runtime presentation fields. During refactor, treat it as a `PidPresentationModel` that depends on new `PidDefinition`/`PidRuntime` objects and expose builders instead of mutating `Map`.

### Diagnostic Code Catalogues (`EcuCodeItem`, `EcuCodeList`, `DTCDatabaseCodeList`, `ObdCodeList`)
- Ensure `EcuCodeItem`/`ObdCodeItem` share a single sealed hierarchy (`DtcRecord`, `StandardDtcRecord`, `CustomDtcRecord`). Provide value semantics (equals/hashCode/toString) and UI-ready formatted labels separate from core logic.
- `DTCDatabaseCodeList` logs heavily with Android `Log`. Replace with injected logger; add manufacturer scoping as a strategy object (`DtcLookupScope`) so unit tests can supply fakes.
- Resource-backed `ObdCodeList` and database-backed `DTCDatabaseCodeList` should implement a common interface (`DtcCatalog`). Add caching layer for repeated lookups and unify locale handling (currently stringly typed).

### Request Scheduling (`ObdPid`, `EcuConversions`)
- `ObdPid` maintains scheduling metadata alongside conversion references. Clarify responsibilities by pulling scheduling into a `PidRequestSchedule` object. Document how `NEXT_REQUEST_ORDER` comparator interacts with new naming.
- `EcuConversions` currently returns raw arrays of `Conversion`. After renaming to `EcuConversionRegistry`, expose typed records (`ConversionBundle`) containing metric/imperial variants, units, and metadata flags (e.g., `isLookup`, `requiresScaling`).

## Phased Migration Outline

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

## Detailed Workstreams

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

## Dependency & Sequencing Map

- `Conversion` refactor (Phase 1) must complete before `PidDefinition` split to avoid touchpoints with legacy API.
- CSV loader modernization depends on conversion registry adapter being in place (ensures new loader can resolve conversion mnemonics).
- `DtcCatalog` interface can land in parallel with early conversion work, but UI integration waits until `PidPresentationModel` exists.
- Request scheduling updates require `PidRuntime` to own timing metadata; sequence after definition split.
- Cross-cutting logging/localization abstractions should land early to unblock unit testing across all workstreams.

## Testing & Validation Strategy

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

## Tooling & Documentation Updates

- Extend developer docs with architecture diagrams (conversion pipeline, PID lifecycle) and migration checklist.
- Provide sample Kotlin/Java usage snippets for new APIs in `/docs/examples`.
- Update ProGuard/R8 keep rules to preserve compatibility facades during transition.
- Add logging guidelines to ensure unified tag/structured payload usage across modules.

### Recent Implementations

- Introduced `RawToPhysicalConverter` / `PhysicalValueFormatter` contracts alongside `LegacyConversionAdapter` wrappers.
- Added immutable `PidDefinition`, mutable `PidRuntime`, and `LegacyEcuDataItemBridge` to stage the migration away from `EcuDataItem`.
- Created `DtcCatalog` interface with a `DtcCatalogProvider` that switches between resource and database catalogues via a supplied feature toggle.
- Registered Gradle placeholder tasks (`testEcuConversions`, `testPidDefinitions`) ready for CI wiring.
- Exposed PID definitions/runtimes via `EcuDataItems.bridgesByMnemonic` helpers for early adopters.
- Hooked `DtcCatalogProvider` into `MainActivity` and added JVM unit tests covering adapters, bridges, and provider toggling.

## CI Test Matrix

- `conversion-regression`: Validate each conversion implementation (linear, hash, bitmap, readiness) over representative raw payloads.
- `pid-definition-parsing`: Exercise CSV/resource ingestion into `PidDefinition` objects, including bitmask edge cases.
- `catalog-lookup`: Compare results between resource-backed and database-backed catalogues under identical inputs and locale settings.
- `runtime-pipeline-smoke`: Ensure `LegacyEcuDataItemBridge` integrates with existing polling/update flows without altering payload semantics.

## Immediate Action Items

- [ ] Migrate high-traffic consumers (`EcuConversions`, fault-code services) to retrieve `PidDefinition`/`PidRuntime` via the new bridge helpers.
- [ ] Replace ad-hoc boolean toggle in `MainActivity` with a real feature flag (remote config or settings) driving `DtcCatalogProvider`.
- [ ] Populate `testEcuConversions` / `testPidDefinitions` with concrete suites and verify in CI once JDK is available locally.

## Open Questions / Pending Investigations

- Confirm whether `Messages` (resource bundle) can be replaced with the shared localization layer from `modules/dtc-database`; ensure no duplicate translations.
- Do we need to preserve binary compatibility for external plugins using `EcuDataItem`? If yes, consider shading new APIs or providing ProGuard keep rules.
- How should the refactored registry expose units for composite conversions (e.g., metric base unit vs imperial override)? Decide between `Unit` enum vs plain string.
- What is the testing strategy for conversion accuracy? Proposal: snapshot tests seeded from CSV + parameterized JUnit suites per conversion type.
- Investigate whether `modules/dtc-database` already offers a data-contract we can lean on for the DTC catalogue surface, to avoid duplicate DTOs.
