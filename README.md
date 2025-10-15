# OBDroid – Open Diagnostics Platform

OBDroid is an Android application that turns any ELM327-compatible adapter into a powerful diagnostic workstation. It covers the “everyday” OBD-II workflows (live data, fault codes, freeze frames) and lays the groundwork for professional Unified Diagnostic Services (UDS) features such as service routines, actuator tests, and vehicle coding.

---

## Feature Snapshot

- **Live telemetry**: Real-time PID streaming, dashboards, HUD, and logging/export.
- **Trouble-code handling**: Read/clear generic & manufacturer DTCs, inspect freeze frames, decode VINs.
- **Adapter flexibility**: Works with Bluetooth, USB, and TCP adapters; includes connection wizards and reconnection logic.
- **UI modes**: List, filtered selection, dashboard, charting, and training overlays.
- **Road to pro diagnostics**: UDS scaffolding, security access flow, manufacturer detection, coding/adaptation stubs, and virtual ECU training concepts.

---

## Build & Deploy

Requirements
```
Android Studio / command-line tools (AGP 8.x)
Android SDK 33+
JDK 17 (toolchain) – app builds against Java 17 bytecode
Android device running Android 5.0+ and an ELM327-compatible adapter
```

Common tasks
```bash
# Assemble debug APK
./gradlew assembleDebug

# Install & launch
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.obddroid/.ui.activities.MainActivity

# Clean build
./gradlew clean
```

---

## Project Layout

```
app/
├── src/java/com/obddroid/
│   ├── core/obd/           # ObdProt, ElmProt, data services, PID catalogues
│   ├── core/ecu/           # Process-variable models (EcuDataPv, EcuCodeItem…)
│   ├── services/           # CommService + Bluetooth/USB/Network transports
│   └── ui/activities/      # MainActivity, dashboards, settings, training modes
├── src/main/res/           # Layouts, drawables, menus, strings
└── build.gradle            # Android module configuration

modules/
└── nhtsa-vin-decoder/      # Optional VIN decoding helper

docs/ (suggested)           # Use for detailed references if you need to export tables
```

---

## Supported Protocols & Adapters

| Transport | Details |
|-----------|---------|
| ISO 15765-4 CAN | Primary OBD-II transport (11-bit & 29-bit IDs) |
| ISO 14230-4 (KWP2000) | Legacy ISO protocol via serial ELM bridges |
| ISO 9141-2 | Chrysler/older Japanese vehicles |
| SAE J1850 VPW / PWM | GM/Ford legacy support |

Adapters confirmed: Classic Bluetooth ELM327, BLE-to-serial bridges (via companion service), USB FTDI/CP210x, WiFi sockets on TCP/35000.

---

## OBD-II & UDS Primer

### Diagnostic Modes Snapshot

| Mode | Purpose | Typical Use |
|------|---------|-------------|
| 01 | Current powertrain data (PIDs) | Live dashboards & logging |
| 02 | Freeze frame snapshot | DTC root-cause data |
| 03 | Stored DTCs | Fault inspection |
| 04 | Clear DTCs | Reset MIL / service lights |
| 05 | O2 sensor test data (Spark) | Emissions diagnostics |
| 06 | On-board monitoring | Component results / misfire counters |
| 07 | Pending DTCs | Pre-MIL faults |
| 08 | Control operations | Bi-directional tests (legacy) |
| 09 | Vehicle information | VIN, calibration IDs |
| 0A | Permanent DTCs | Post-clear retained codes |

### Essential Mode 01 PIDs

| PID | Description | Formula | Units |
|-----|-------------|---------|-------|
| 0x00 | Supported PIDs 0x01–0x20 | Bit mask | – |
| 0x01 | Monitor status/MIL | Bit mask | – |
| 0x03 | Fuel system status | Bit mask | – |
| 0x04 | Engine load | A × 100 / 255 | % |
| 0x05 | Coolant temp | A − 40 | °C |
| 0x0C | Engine RPM | (256A + B) / 4 | rpm |
| 0x0D | Vehicle speed | A | km/h |
| 0x10 | MAF rate | (256A + B) / 100 | g/s |
| 0x11 | Throttle | A × 100 / 255 | % |
| 0x1F | Run time since start | 256A + B | s |
| 0x42 | Control module voltage | (256A + B) / 1000 | V |
| 0x46 | Ambient air temp | A − 40 | °C |
| 0x5C | Engine oil temp | A − 40 | °C |

> Tip: `ObdProt.dataItems` maps PIDs to `EcuDataPv` entries. Use `ObdItemAdapter` for formatting and display.

---

## Professional Diagnostics Roadmap

OBDroid already houses abstractions for advanced services. The roadmap below consolidates the old PID reference and pro-diagnostics plan into a single vision.

### Core UDS Services

| Service | ID | Purpose |
|---------|----|---------|
| DiagnosticSessionControl | 0x10 | Switch to default/extended/programming sessions |
| ECUReset | 0x11 | Controlled module reset |
| ClearDiagnosticInformation | 0x14 | DTC erase with conditions |
| ReadDTCInformation | 0x19 | Rich DTC queries, snapshots, records |
| ReadDataByIdentifier | 0x22 | Read VIN, coding, calibration values |
| SecurityAccess | 0x27 | Seed/key unlock for protected routines |
| WriteDataByIdentifier | 0x2E | Persist adaptations, coding flags |
| InputOutputControlByIdentifier | 0x2F | Actuator/solenoid control |
| RoutineControl | 0x31 | Service resets, calibrations, regen routines |
| RequestDownload/TransferData | 0x34/0x36 | Firmware flashing (future) |

### Implementation Phases

1. **Foundation (Weeks 1–2)**  
   - Integrate ISO-TP transport and UDS session/state machine.  
   - Extend `CommService` to negotiate baud/headers per manufacturer.  
   - Mirror `ediabaslib`/`Implementation_UDS_CAN` patterns for message framing.

2. **Service Function Layer (Weeks 3–4)**  
   - Build `ServiceFunction` abstractions (oil reset, SAS calibration, battery registration, DPF regen, injector coding).  
   - Add safety wrapper (`SafetyManager`) to enforce preconditions (ignition state, brakes, engine off).  
   - Implement DID database using `python-uds` descriptors for UI-friendly labels.

3. **Manufacturer Packs (Weeks 5–6)**  
   - VIN-based detection (WMI/WVIN) to load brand profiles.  
   - BMW: KOMBI coding, NBT retrofits via `ediabaslib` .prg/.ncd knowledge.  
   - VAG: Long coding editor inspired by `ediabaslib` TP2.0 handling.  
   - Mercedes/Ford/Toyota: targeted service menus leveraging UDS routines.

4. **Training & Simulation**  
   - Bundle scenarios backed by `ecu-simulator` style virtual ECUs.  
   - Offer interactive lessons (read VIN, clear DTCs, run DPF regen) without a real car.

5. **UI/Monetization**  
   - “Service & Coding” tab with tile-based navigation.  
   - Monetization tiers: free (basic OBD), Pro (service resets, diagnostics), Expert (coding, manufacturer packs).  
   - Provide try-before-you-buy paths using virtual ECU practice mode.

---

## Safety & Best Practices

- Block destructive commands unless vehicle state matches routine expectations.
- Confirm critical operations (coding, resets) with double prompts and disclaimers.
- Keep adapters powered safely; advise against using with low battery voltage.
- Log UDS traffic (with user consent) for traceability and bug reports.
- Provide “simulation mode” for learning without touching a real ECU.

---

## Contributing

1. Fork & clone the repository.  
2. Create a feature branch: `git checkout -b feature/my-change`.  
3. Run `./gradlew lint ktlint detekt` (if configured) before pushing.  
4. Submit a pull request that explains the problem, solution, and testing performed.  
5. For protocol changes, attach traces (if available) with sensitive data redacted.

---

## Roadmap Resources

- **ediabaslib** – BMW/VAG protocols, coding formats  
  <https://github.com/uholeschak/ediabaslib>
- **Implementation_UDS_CAN** – Reference ISO-TP + UDS stack in C  
  <https://github.com/nizarmojab/Implementation_UDS_CAN>
- **python-uds** – DID database, response parsing, negative response codes  
  <https://github.com/pylessard/python-uds>
- **ecu-simulator** – Virtual ECU for testing & training  
  <https://github.com/lbenthins/ecu-simulator>
- **SAE J1979 / ISO 14229** – Standards for PIDs & UDS messaging

---

## License & Contact

OBDroid is open source. Refer to `LICENSE` for distribution terms.  
Maintainer: **Waleed Judah (Wal33D)** · aquataze@yahoo.com

Let’s build professional-grade diagnostics together—pull requests, protocol traces, and real-world testing feedback are always welcome.
