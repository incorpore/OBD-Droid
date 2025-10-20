# Telemetry & Vehicle History Workstreams

**Assigned Agent:** Agent C

## Process Variable Refactor Progress

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

---

## Vehicle History Page Enhancement Plan

### TODO
- [ ] Implement Phase 2 UI (recall module, usage badges, odometer sub-checks) behind feature flags.
- [ ] Finalize Phase 2 UX copy and localized strings across new components.
- [ ] Instrument analytics events (`vehicle_history_recall_expand`, `vehicle_history_usage_badge_tap`, `vehicle_history_odometer_expand`).
- [ ] Build VIN-based regression matrix and execute QA pass covering new data permutations.
- [ ] Prototype Phase 3 owner timeline concepts and validate data gaps with stakeholders.

> **Program context:** This workstream ties back to the overarching platform enhancement master plan. Keep both documents synchronized when scope changes.

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
