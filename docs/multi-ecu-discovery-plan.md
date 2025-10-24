# Multi-ECU Discovery & Logging Plan

Last updated: October 2025

## Goals

1. Discover and enumerate all ECUs reachable from the diagnostic adapter, not
   just powertrain modules exposed via generic Mode 01/09.
2. Support multiple adapter capabilities (ELM327 clones, STN-based tools,
   advanced CAN interfaces) without regressing current behaviour.
3. Capture structured telemetry so field testers can replay discovery sessions,
   compare vehicles, and iterate on heuristics quickly.

## Current State & Gaps

- `ElmProt` already accumulates responding addresses while the user polls
  standard PIDs, but we only surface them indirectly via `EcuManager`.
- `EcuManager` maps Mode 09 results to a single logical ECU list; it does not
  actively probe for body/ABS/SRS modules or maintain per-ECU metadata.
- Logging is unstructured (`java.util.logging` -> Logcat). Reviewing discovery
  sessions requires hunting through verbose logs with no correlation IDs.

## Proposed Discovery Pipeline

### 1. Capability Negotiation

Detect adapter features at connection time:

- Firmware type (ELM, STN, OBDLink, CANable, etc.).
- Supported CAN modes (11-bit, 29-bit, ISO-TP, automatic addressing).
- Custom commands available (`STMA`, `ATD0`, `ATSH`, multi-frame support).

Persist the capability profile so scanning logic can branch accordingly.

### 2. Passive Harvesting

- Continue harvesting addresses from normal traffic (as `ElmProt` already
  does). Tag each discovered address with the service that revealed it.
- Emit lightweight discovery events so the UI (and logs) know when ECUs appear
  without running explicit probes.

### 3. Active Probing Stages

1. **Functional Broadcast Sweep**
   - Send functional requests (e.g. `0x7DF` for standard services, `0x7E0`-`0x7EF`
     for diagnostics) with short timeouts to catch willing responders.
   - Expand to extended CAN IDs when the adapter supports 29-bit.

2. **Targeted Address Scan**
   - Use a per-make registry of known module addresses (e.g. Nissan: ECM 0x7E0,
     TCM 0x7E1, ABS 0x760, SRS 0x743, IPDM 0x7B0).
   - Probe in batches with adaptive delays to avoid flooding slower modules.

3. **Protocol Handshake**
   - For responders, attempt lightweight identification (UDS `0x22F101`, ISO14229
     `ReadDataByIdentifier`, SAE J1979 Mode 09).
   - Record ECU type, supported services, and health status.

### 4. Session Management

- Represent each ECU as an `EcuSession` object encapsulating:
  - Addressing (physical/functional IDs, 11/29-bit headers).
  - Supported transport (ISO-TP segmentation, timing parameters).
  - Available data endpoints (DTCs, live data groups, configuration).
- Provide a discovery lifecycle: `FOUND → IDENTIFIED → READY` (or `DEGRADED`).
- Expose events to the rest of the app so UI components can subscribe.

## Logging & Telemetry Strategy

### Structured Event Model

- Introduce a `DiscoveryEvent` data class with fields like:
  - `sessionId`, `timestamp`, `adapterId`
  - `eventType` (`CAPABILITY`, `PROBE_SENT`, `PROBE_RESPONSE`, `ECU_IDENTIFIED`,
    `ERROR`)
  - `ecuAddress`, `protocol`, `payloadSummary`
  - `latencyMs`, `attempt`

- Serialize events as newline-delimited JSON (`.ndjson`) saved under
  `Android/data/.../logs/discovery/<timestamp>.json` for easy ingestion.

### Real-Time Debug Stream

- Mirror the structured events to Logcat using a distinct tag
  (`DiscoveryPipeline`) so testers can tail in the field.
- Include correlation IDs so multi-vehicle sessions can be separated.

### Telemetry Summaries

- After each session, generate a summary block (CSV/JSON) listing:
  - Discovered ECUs (address, name, protocol, status).
  - Probe counts and success rates.
  - Adapter capabilities and firmware info.
- Optionally prompt the user to share/upload the bundle for analysis.

## Implementation Phases

1. **Foundations**
   - Capability detection module.
   - Basic discovery manager emitting structured events.
   - Persist events locally; add developer option to export.

2. **Make/Model Registry**
   - Seed initial address maps (Nissan Frontier, GMC trucks, Ford F-series).
   - Provide fallback heuristics (common SAE ranges if make not known).

3. **UI Exposure**
   - New “ECU Modules” screen backed by discovery manager.
   - Status chips (Discovering, Identified, Error) with retry controls.

4. **Advanced Protocol Support**
   - ISO-TP segmentation for multi-frame responses.
   - UDS services for richer identification (where supported).
   - Adapter-specific optimisations (batch probing, wake-up sequences).

## Next Steps for Field Testing

- Implement the capability detector + passive harvesting events.
- Capture logs across multiple vehicles to build the address registry.
- Iterate on probe timing and retry policies using the structured logs.
- Validate storage footprint and privacy (VIN redaction when exporting).

With this approach, testers can gather consistent datasets across different
vehicles, and we can evolve the scanning heuristics without guessing at what
actually happened on the CAN bus.
