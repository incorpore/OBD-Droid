# Process Variable Refactor Progress

## Status at a Glance
- [x] Introduce typed wrappers for `ProcessVar`/`PvList` – `TypedProcessVar`/`TypedPvList` live alongside the legacy structures to give covariant helpers during migration.
- [x] Migrate `ObdDataService` and `ObdProt` to typed APIs – service-backed stores now expose `TypedPvList` instances while keeping compatibility shims in place.
- [x] Update adapters/activities to consume typed PVs – UI helpers retrieve strongly typed items from the shared lists.
- [x] Add threading/locking improvements – `ProcessVar` now uses a `ReentrantReadWriteLock` to guard bulk updates and listener dispatch.
- [ ] Replace event bitmask with richer model – callers still rely on `PvChangeEvent` integer masks.
- [ ] Remove legacy raw-map classes once migration completes – the base classes still extend raw `HashMap`.

## Completed Milestones (Code References)
- **Typed wrappers introduced:** `ProcessVariables.TypedProcessVar` and `ProcessVariables.TypedPvList` wrap the raw map while exposing generic-friendly helpers (see `app/src/java/com/obddroid/core/pvs/ProcessVariables.java`).
- **Service and protocol bound to typed lists:** `ObdDataService` owns typed stores per service, and `ObdProt` exposes `TypedPvList` facades that proxy into the service (see `app/src/java/com/obddroid/services/ObdDataService.java` and `app/src/java/com/obddroid/core/obd/ObdProt.java`).
- **UI migration in place:** Activities/components pull data via typed accessors (for example `VehicleInfoFooter`, `VinDataHelper`, `FuelEconomyActivity`) which confirms the wrappers behave as intended.
- **Thread-safety upgrades applied:** `ProcessVar.putAll`/`put` now acquire a write lock before mutating state, reducing listener-race issues that used to surface under concurrent updates.

## Current Architecture Notes
- Legacy and typed pathways coexist. The wrappers are thin shells over the raw map, so we still rely on unchecked casts but the call sites now document their intent.
- All listeners attach to the shared `TypedPvList` instances exposed through `ObdProt`. Those lists shadow updates into the central `ObdDataService`, so any future structural change must keep that mirror in sync.
- `PvChangeEvent` remains the main notification primitive. Every high-level feature (VIN detection, dashboard widgets, charts, etc.) currently switches on the integer bitmask delivered by that class.

## Outstanding Work – Replace Event Bitmask

### Current Pain Points
- The bitmask encodes multiple semantics (action vs. child update) in a single `int`, which obscures intent and keeps listeners tightly coupled to the legacy constants.
- Many listeners only care about a subset of events but must reason about bit arithmetic (`&` checks), making mistakes easy and testing harder.
- Extending the event surface area (new lifecycle states, payload metadata) is risky because it would require inventing new bits that existing code might mishandle.

### High-Traffic Call Sites
- `MainActivity` dashboard refresh logic and VIN detection (switch statement on `PvChangeEvent` type).
- `VehicleInfoFooter`, `VinDataHelper`, and `EcuManager` VIN propagation flows.
- `FuelEconomyActivity`, `EmissionsActivity`, and `DashBoardActivity` UI updaters.
- `ObdItemAdapter` chart synchronization and list adapters.
- Utility code (`FileHelper`, `ObdDataService`) that mirrors data between stores.

### Proposed Migration Plan
1. **Define a richer event payload:** Introduce `PvChangeType` (enum) plus a `PvChange` value object that exposes `source`, `key`, `value`, `changeType`, and optional metadata (origin, child flag, timestamps).
2. **Add compatibility shims:** Extend `PvChangeEvent` to carry the new payload while still exposing the old `int` mask. During the transition, listeners can opt-in to the richer API without breaking existing code.
3. **Update listeners incrementally:** Convert the high-traffic listeners listed above to the new enum-based API. Provide helper methods that map legacy constants to the new types to avoid duplicated mapping logic.
4. **Deprecate mask usage:** Once call sites no longer depend on integer masks, deprecate bitwise accessors and eventually remove the constants in favour of `EnumSet<PvChangeType>`.
5. **Tighten tests/telemetry:** Add unit tests (where feasible) for the new event class and smoke-test key activities to ensure the enum mapping still fires UI updates as expected.

### Flag-to-Enum Mapping (Initial Draft)
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

## Outstanding Work – Retire Raw Map Implementations

### Current Usage Snapshot
- `ProcessVar` and `PvList` still extend raw `HashMap` and expose loosely typed getters/setters.
- Domain classes (`EcuDataPv`, dashboards, adapters) depend on the ability to stash arbitrary extras in the map, which complicates a straight refactor to generics.
- Serialization/deserialization paths (freeze frame cache, file helpers) assume `Map<Object, Object>` semantics.

### Proposed Path Forward
1. **Introduce an interface-first abstraction:** Define `ProcessVariable<K, V>` and `ProcessVariableList<K, PV>` interfaces that describe the minimal surface we need (typed `get/put`, listener hooks, iteration).
2. **Wrap legacy implementations:** Make `ProcessVar`/`PvList` implement those interfaces and keep the raw map internally until all clients switch to the interface.
3. **Update call sites gradually:** Migrate consumers to the interfaces, adding generic type parameters where possible (`EcuDataPv` already has fixed fields we can model explicitly).
4. **Constrain payload shapes:** Once callers compile against the typed interfaces, tighten the backing storage (e.g. convert to `LinkedHashMap<K, V>` with concrete key/value types) and remove raw-map methods.
5. **Remove legacy aliases:** After the generics migration completes, delete or inline the legacy `TypedProcessVar`/`TypedPvList` wrappers and collapse onto a single typed implementation.

### Considerations & Risks
- Some adapters rely on storing UI-only payloads inside PV objects (charts, view handles). We need to audit each usage and either formalise those fields or provide a dedicated attachment mechanism.
- Serialization compatibility matters: ensure that new typed implementations preserve the on-disk shape or provide migration utilities.
- Android components rely on being notified on the main thread; any new abstraction should keep existing threading guarantees or clearly document changes.

## Immediate Next Steps
1. Draft the `PvChangeType`/`PvChange` classes and add compatibility constructors in `ProcessVariables`.
2. Convert one representative listener (suggested: `MainActivity`) to exercise the new event API and validate the mapping.
3. Sketch the `ProcessVariable` interface and evaluate the impact on `EcuDataPv` plus the adapter layer.
4. Document testing touchpoints for the above changes (unit test harness or instrumentation smoke tests).
