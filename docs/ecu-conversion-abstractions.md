# ECU Conversion & Catalog Abstractions – Proposal

**Author:** Agent B  
**Review Window:** 2024-07-19 – 2024-07-24  
**Intended Reviewers:** Architecture Council, Diagnostics Platform Tech Leads, QA Lead (Diagnostics)

## Objective
Introduce interface-first boundaries for ECU conversion logic and diagnostic catalog data so the upcoming refactor can proceed without repeated churn. The abstractions below decouple call sites from the legacy `HashMap` implementations while staying binary-compatible with existing modules.

## Proposed Interfaces
```java
package com.obddroid.core.ecu;

public interface ValueConversion {
    double convertRawToPhysical(int rawValue);
    int convertPhysicalToRaw(double physicalValue);
    String formatPhysicalValue(double physicalValue);
}

public interface PidDefinitionRepository {
    PidDefinition findByPid(int service, int pid);
    Collection<PidDefinition> listAll(int service);
}

public interface DtcCatalog {
    DtcEntry findByCode(String code);
    Collection<DtcEntry> search(String queryText);
    Locale getLocale();
}
```

### Compatibility Layer
- `Conversions` gains adapter methods that wrap existing static helpers in lightweight `ValueConversion` implementations.
- `EcuDataItems` exposes a `PidDefinitionRepository` backed by the current JSON assets (`PidDefinitionStore` shim).
- `DtcDatabaseCatalog` implements `DtcCatalog`, delegating to the current resource bundle while the new localization pipeline matures.

## Migration Strategy
1. **Phase 0 – Define & Stage (complete with this proposal):** Add interfaces, default adapters, and package documentation.  
2. **Phase 1 – Opt-In Callers:** Update `EcuManager`, `VehicleInfoFooter`, and conversion utilities to request interfaces instead of raw classes. Provide constructors/factories returning adapters so behaviour remains unchanged.  
3. **Phase 2 – Swap Implementations:** Replace in-memory `HashMap` storage with typed containers once PV infrastructure surfaces typed accessors. Stage via feature flag to verify telemetry stability.  
4. **Phase 3 – Remove Legacy Paths:** Delete direct static helpers, collapse adapter layer, and update tests to target interface contracts exclusively.

## Testing & Tooling
- Add contract tests for each interface (baseline implemented in JVM tests, instrumentation coverage for Android-specific locale rendering).  
- Snapshot tests for conversion formatting to guarantee legacy text remains stable.  
- Build-time lint to forbid direct instantiation of legacy `Conversions` classes once Phase 2 lands.

## Architectural Review Plan
- Circulate this proposal and require async comments by 2024-07-22.  
- Live review scheduled with Architecture Council on 2024-07-23 (30 minutes, prior to council agenda).  
- Track decisions/action items in `ARC-217` Jira. Acceptance criteria: no blocking concerns, identified risks have owners.  
- Post-review, publish finalized interfaces and adapters in `modules/dtc-database` + `app` modules with change summary.

## Risks & Mitigations
- **Binary Compatibility:** Android builds that still reference concrete classes may fail if adapters are missing—ship adapters with no-op behaviour first.  
- **Serialization Coupling:** Some freeze-frame helpers rely on concrete classes; add migration helpers that convert to/from the interface types until consumers migrate.  
- **Testing Surface Increase:** New interfaces require additional mocks; provide Kotlin extension shims to simplify test scaffolding.  
- **Timeline Pressure:** Align milestones with PV refactor deliverables; track via shared roadmap to avoid sequencing conflicts.
