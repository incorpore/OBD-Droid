# ECU Module Refactor Notes

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
- Introduce consistent error surfaces:

- Sketch data classes for `PidDefinition`/`PidRuntime` and outline migration facade for `EcuDataItem`.
- Prototype `DtcCatalog` interface and wire both resource + database implementations behind feature flags.
- Define CI test matrix and create placeholder Gradle tasks so future test suites slot in without friction.

## Open Questions / Pending Investigations

- Confirm whether `Messages` (resource bundle) can be replaced with the shared localization layer from `modules/dtc-database`; ensure no duplicate translations.
- Do we need to preserve binary compatibility for external plugins using `EcuDataItem`? If yes, consider shading new APIs or providing ProGuard keep rules.
- How should the refactored registry expose units for composite conversions (e.g., metric base unit vs imperial override)? Decide between `Unit` enum vs plain string.
- What is the testing strategy for conversion accuracy? Proposal: snapshot tests seeded from CSV + parameterized JUnit suites per conversion type.
- Investigate whether `modules/dtc-database` already offers a data-contract we can lean on for the DTC catalogue surface, to avoid duplicate DTOs.
