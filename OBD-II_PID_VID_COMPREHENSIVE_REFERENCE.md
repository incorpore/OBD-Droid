# OBD-II PID/VID Comprehensive Reference Guide

## Table of Contents
1. [OBD-II Overview](#obd-ii-overview)
2. [Communication Protocol](#communication-protocol)
3. [Mode 01 - Show Current Data](#mode-01---show-current-data)
4. [Mode 02 - Show Freeze Frame Data](#mode-02---show-freeze-frame-data)
5. [Mode 03 - Show Diagnostic Trouble Codes](#mode-03---show-diagnostic-trouble-codes)
6. [Mode 04 - Clear Trouble Codes](#mode-04---clear-trouble-codes)
7. [Mode 05 - Test Results](#mode-05---test-results)
8. [Mode 06 - Test Results](#mode-06---test-results)
9. [Mode 07 - Show Pending DTCs](#mode-07---show-pending-dtcs)
10. [Mode 08 - Control Operation](#mode-08---control-operation)
11. [Mode 09 - Request Vehicle Information](#mode-09---request-vehicle-information)
12. [Mode 0A - Permanent DTCs](#mode-0a---permanent-dtcs)

## OBD-II Overview

### What is OBD-II?
On-Board Diagnostics II (OBD-II) is a standardized system that allows external electronics to interface with a car's computer system. Since 1996, all cars sold in the US have been required to have an OBD-II port.

### Standard Reference
- **SAE J1979**: Primary standard defining OBD-II PIDs
- **ISO 15765-4**: CAN bus diagnostic protocols
- **SAE J2012**: Diagnostic Trouble Code definitions

## Communication Protocol

### CAN Bus Communication
- **Request ID**: 0x7DF (broadcast) or 0x7E0-0x7E7 (specific ECU)
- **Response ID**: 0x7E8-0x7EF (8 higher than request)
- **Data Format**: 8 bytes total

### Request Format
```
[Length] [Mode] [PID] [Padding...]
```
Example: `02 01 0C 55 55 55 55 55` (Request Engine RPM)

### Response Format
```
[Length] [Mode+0x40] [PID] [Data...] [Padding...]
```
Example: `04 41 0C 1A F8 55 55 55` (Response: 1726 RPM)

## Mode 01 - Show Current Data

### Complete PID List with Formulas

| PID (hex) | Description | Data Bytes | Formula | Min Value | Max Value | Units |
|-----------|-------------|------------|---------|-----------|-----------|-------|
| 00 | PIDs supported [01-20] | 4 | Bit encoded | - | - | - |
| 01 | Monitor status since DTCs cleared | 4 | Bit encoded | - | - | - |
| 02 | Freeze DTC | 2 | - | - | - | - |
| 03 | Fuel system status | 2 | Bit encoded | - | - | - |
| 04 | Calculated engine load | 1 | A*100/255 | 0 | 100 | % |
| 05 | Engine coolant temperature | 1 | A-40 | -40 | 215 | °C |
| 06 | Short term fuel trim—Bank 1 | 1 | (A-128)*100/128 | -100 | 99.2 | % |
| 07 | Long term fuel trim—Bank 1 | 1 | (A-128)*100/128 | -100 | 99.2 | % |
| 08 | Short term fuel trim—Bank 2 | 1 | (A-128)*100/128 | -100 | 99.2 | % |
| 09 | Long term fuel trim—Bank 2 | 1 | (A-128)*100/128 | -100 | 99.2 | % |
| 0A | Fuel pressure | 1 | A*3 | 0 | 765 | kPa |
| 0B | Intake manifold absolute pressure | 1 | A | 0 | 255 | kPa |
| 0C | Engine speed | 2 | ((A*256)+B)/4 | 0 | 16,383.75 | rpm |
| 0D | Vehicle speed | 1 | A | 0 | 255 | km/h |
| 0E | Timing advance | 1 | A/2-64 | -64 | 63.5 | ° before TDC |
| 0F | Intake air temperature | 1 | A-40 | -40 | 215 | °C |
| 10 | Mass air flow sensor (MAF) | 2 | ((A*256)+B)/100 | 0 | 655.35 | grams/sec |
| 11 | Throttle position | 1 | A*100/255 | 0 | 100 | % |
| 12 | Commanded secondary air status | 1 | Bit encoded | - | - | - |
| 13 | Oxygen sensors present (2 banks) | 1 | Bit encoded | - | - | - |
| 14 | Oxygen Sensor 1 (Bank 1) | 2 | A/200, (B-128)*100/128 | 0-1.275V, -100-99.2% | | V, % |
| 15 | Oxygen Sensor 2 (Bank 1) | 2 | A/200, (B-128)*100/128 | 0-1.275V, -100-99.2% | | V, % |
| 16 | Oxygen Sensor 3 (Bank 1) | 2 | A/200, (B-128)*100/128 | 0-1.275V, -100-99.2% | | V, % |
| 17 | Oxygen Sensor 4 (Bank 1) | 2 | A/200, (B-128)*100/128 | 0-1.275V, -100-99.2% | | V, % |
| 18 | Oxygen Sensor 5 (Bank 2) | 2 | A/200, (B-128)*100/128 | 0-1.275V, -100-99.2% | | V, % |
| 19 | Oxygen Sensor 6 (Bank 2) | 2 | A/200, (B-128)*100/128 | 0-1.275V, -100-99.2% | | V, % |
| 1A | Oxygen Sensor 7 (Bank 2) | 2 | A/200, (B-128)*100/128 | 0-1.275V, -100-99.2% | | V, % |
| 1B | Oxygen Sensor 8 (Bank 2) | 2 | A/200, (B-128)*100/128 | 0-1.275V, -100-99.2% | | V, % |
| 1C | OBD standards vehicle conforms to | 1 | Enumerated | - | - | - |
| 1D | Oxygen sensors present (4 banks) | 1 | Bit encoded | - | - | - |
| 1E | Auxiliary input status | 1 | A0 = PTO status | - | - | - |
| 1F | Run time since engine start | 2 | (A*256)+B | 0 | 65,535 | seconds |
| 20 | PIDs supported [21-40] | 4 | Bit encoded | - | - | - |
| 21 | Distance traveled with MIL on | 2 | (A*256)+B | 0 | 65,535 | km |
| 22 | Fuel Rail Pressure | 2 | ((A*256)+B)*0.079 | 0 | 5177.265 | kPa |
| 23 | Fuel Rail Gauge Pressure | 2 | ((A*256)+B)*10 | 0 | 655,350 | kPa |
| 24 | O2S1_WR_lambda(1): ER/Voltage | 4 | ((A*256)+B)/32768, ((C*256)+D)/8192-4 | 0-2, -4-4 | | ratio, V |
| 25 | O2S2_WR_lambda(1): ER/Voltage | 4 | ((A*256)+B)/32768, ((C*256)+D)/8192-4 | 0-2, -4-4 | | ratio, V |
| 26 | O2S3_WR_lambda(1): ER/Voltage | 4 | ((A*256)+B)/32768, ((C*256)+D)/8192-4 | 0-2, -4-4 | | ratio, V |
| 27 | O2S4_WR_lambda(1): ER/Voltage | 4 | ((A*256)+B)/32768, ((C*256)+D)/8192-4 | 0-2, -4-4 | | ratio, V |
| 28 | O2S5_WR_lambda(1): ER/Voltage | 4 | ((A*256)+B)/32768, ((C*256)+D)/8192-4 | 0-2, -4-4 | | ratio, V |
| 29 | O2S6_WR_lambda(1): ER/Voltage | 4 | ((A*256)+B)/32768, ((C*256)+D)/8192-4 | 0-2, -4-4 | | ratio, V |
| 2A | O2S7_WR_lambda(1): ER/Voltage | 4 | ((A*256)+B)/32768, ((C*256)+D)/8192-4 | 0-2, -4-4 | | ratio, V |
| 2B | O2S8_WR_lambda(1): ER/Voltage | 4 | ((A*256)+B)/32768, ((C*256)+D)/8192-4 | 0-2, -4-4 | | ratio, V |
| 2C | Commanded EGR | 1 | A*100/255 | 0 | 100 | % |
| 2D | EGR Error | 1 | (A-128)*100/128 | -100 | 99.2 | % |
| 2E | Commanded evaporative purge | 1 | A*100/255 | 0 | 100 | % |
| 2F | Fuel Tank Level Input | 1 | A*100/255 | 0 | 100 | % |
| 30 | Warm-ups since codes cleared | 1 | A | 0 | 255 | count |
| 31 | Distance traveled since codes cleared | 2 | (A*256)+B | 0 | 65,535 | km |
| 32 | Evap. System Vapor Pressure | 2 | ((A*256)+B)/4-8192 | -8192 | 8191.75 | Pa |
| 33 | Absolute Barometric Pressure | 1 | A | 0 | 255 | kPa |
| 34 | O2S1_WR_lambda(1): ER/Current | 4 | ((A*256)+B)/32768, ((C*256)+D)/256-128 | 0-2, -128-127.996 | | ratio, mA |
| 35 | O2S2_WR_lambda(1): ER/Current | 4 | ((A*256)+B)/32768, ((C*256)+D)/256-128 | 0-2, -128-127.996 | | ratio, mA |
| 36 | O2S3_WR_lambda(1): ER/Current | 4 | ((A*256)+B)/32768, ((C*256)+D)/256-128 | 0-2, -128-127.996 | | ratio, mA |
| 37 | O2S4_WR_lambda(1): ER/Current | 4 | ((A*256)+B)/32768, ((C*256)+D)/256-128 | 0-2, -128-127.996 | | ratio, mA |
| 38 | O2S5_WR_lambda(1): ER/Current | 4 | ((A*256)+B)/32768, ((C*256)+D)/256-128 | 0-2, -128-127.996 | | ratio, mA |
| 39 | O2S6_WR_lambda(1): ER/Current | 4 | ((A*256)+B)/32768, ((C*256)+D)/256-128 | 0-2, -128-127.996 | | ratio, mA |
| 3A | O2S7_WR_lambda(1): ER/Current | 4 | ((A*256)+B)/32768, ((C*256)+D)/256-128 | 0-2, -128-127.996 | | ratio, mA |
| 3B | O2S8_WR_lambda(1): ER/Current | 4 | ((A*256)+B)/32768, ((C*256)+D)/256-128 | 0-2, -128-127.996 | | ratio, mA |
| 3C | Catalyst Temperature: Bank 1, Sensor 1 | 2 | ((A*256)+B)/10-40 | -40 | 6513.5 | °C |
| 3D | Catalyst Temperature: Bank 2, Sensor 1 | 2 | ((A*256)+B)/10-40 | -40 | 6513.5 | °C |
| 3E | Catalyst Temperature: Bank 1, Sensor 2 | 2 | ((A*256)+B)/10-40 | -40 | 6513.5 | °C |
| 3F | Catalyst Temperature: Bank 2, Sensor 2 | 2 | ((A*256)+B)/10-40 | -40 | 6513.5 | °C |
| 40 | PIDs supported [41-60] | 4 | Bit encoded | - | - | - |
| 41 | Monitor status this drive cycle | 4 | Bit encoded | - | - | - |
| 42 | Control module voltage | 2 | ((A*256)+B)/1000 | 0 | 65.535 | V |
| 43 | Absolute load value | 2 | ((A*256)+B)*100/255 | 0 | 25,700 | % |
| 44 | Commanded Air-Fuel Equivalence Ratio | 2 | ((A*256)+B)/32768 | 0 | 2 | ratio |
| 45 | Relative throttle position | 1 | A*100/255 | 0 | 100 | % |
| 46 | Ambient air temperature | 1 | A-40 | -40 | 215 | °C |
| 47 | Absolute throttle position B | 1 | A*100/255 | 0 | 100 | % |
| 48 | Absolute throttle position C | 1 | A*100/255 | 0 | 100 | % |
| 49 | Accelerator pedal position D | 1 | A*100/255 | 0 | 100 | % |
| 4A | Accelerator pedal position E | 1 | A*100/255 | 0 | 100 | % |
| 4B | Accelerator pedal position F | 1 | A*100/255 | 0 | 100 | % |
| 4C | Commanded throttle actuator | 1 | A*100/255 | 0 | 100 | % |
| 4D | Time run with MIL on | 2 | (A*256)+B | 0 | 65,535 | minutes |
| 4E | Time since trouble codes cleared | 2 | (A*256)+B | 0 | 65,535 | minutes |
| 4F | Maximum values (fuel–air ER, O2 voltage, current, MAP) | 4 | A, B, C, D | Various | Various | Various |
| 50 | Maximum value for mass air flow sensor | 4 | A*10, B, C, D | 0-2550 g/s | | g/s |
| 51 | Fuel Type | 1 | Enumerated | - | - | - |
| 52 | Ethanol fuel % | 1 | A*100/255 | 0 | 100 | % |
| 53 | Absolute Evap system Vapor Pressure | 2 | ((A*256)+B)/200 | 0 | 327.675 | kPa |
| 54 | Evap system vapor pressure | 2 | ((A*256)+B)-32767 | -32,767 | 32,768 | Pa |
| 55 | Short term secondary O2 sensor trim, bank 1 | 2 | (A-128)*100/128, (B-128)*100/128 | -100 | 99.2 | % |
| 56 | Long term secondary O2 sensor trim, bank 1 | 2 | (A-128)*100/128, (B-128)*100/128 | -100 | 99.2 | % |
| 57 | Short term secondary O2 sensor trim, bank 2 | 2 | (A-128)*100/128, (B-128)*100/128 | -100 | 99.2 | % |
| 58 | Long term secondary O2 sensor trim, bank 2 | 2 | (A-128)*100/128, (B-128)*100/128 | -100 | 99.2 | % |
| 59 | Fuel rail absolute pressure | 2 | ((A*256)+B)*10 | 0 | 655,350 | kPa |
| 5A | Relative accelerator pedal position | 1 | A*100/255 | 0 | 100 | % |
| 5B | Hybrid battery pack remaining life | 1 | A*100/255 | 0 | 100 | % |
| 5C | Engine oil temperature | 1 | A-40 | -40 | 215 | °C |
| 5D | Fuel injection timing | 2 | ((A*256)+B)/128-210 | -210 | 301.992 | ° |
| 5E | Engine fuel rate | 2 | ((A*256)+B)/20 | 0 | 3276.75 | L/h |
| 5F | Emission requirements | 1 | Bit encoded | - | - | - |
| 60 | PIDs supported [61-80] | 4 | Bit encoded | - | - | - |
| 61 | Driver's demand engine - percent torque | 1 | A-125 | -125 | 130 | % |
| 62 | Actual engine - percent torque | 1 | A-125 | -125 | 130 | % |
| 63 | Engine reference torque | 2 | (A*256)+B | 0 | 65,535 | Nm |
| 64 | Engine percent torque data | 5 | A-125 (5 different values) | -125 | 130 | % |
| 65 | Auxiliary input / output supported | 2 | Bit encoded | - | - | - |
| 66 | Mass air flow sensor | 5 | Various | - | - | - |
| 67 | Engine coolant temperature | 3 | Various | - | - | °C |
| 68 | Intake air temperature sensor | 7 | Various | - | - | °C |
| 69 | Commanded EGR and EGR Error | 7 | Various | - | - | % |
| 6A | Commanded Diesel intake air flow control | 7 | Various | - | - | % |
| 6B | Exhaust gas recirculation temperature | 5 | Various | - | - | °C |
| 6C | Commanded throttle actuator control | 5 | Various | - | - | % |
| 6D | Fuel pressure control system | 6 | Various | - | - | kPa |
| 6E | Injection pressure control system | 5 | Various | - | - | kPa |
| 6F | Turbocharger compressor inlet pressure | 3 | Various | - | - | kPa |
| 70 | Boost pressure control | 9 | Various | - | - | kPa |
| 71 | Variable Geometry turbo control | 6 | Various | - | - | % |
| 72 | Wastegate control | 5 | Various | - | - | % |
| 73 | Exhaust pressure | 5 | Various | - | - | kPa |
| 74 | Turbocharger RPM | 5 | Various | - | - | rpm |
| 75 | Turbocharger temperature | 7 | Various | - | - | °C |
| 76 | Turbocharger temperature | 7 | Various | - | - | °C |
| 77 | Charge air cooler temperature | 5 | Various | - | - | °C |
| 78 | Exhaust Gas temperature Bank 1 | 9 | Special | - | - | °C |
| 79 | Exhaust Gas temperature Bank 2 | 9 | Special | - | - | °C |
| 7A | Diesel particulate filter | 7 | Various | - | - | % |
| 7B | Diesel particulate filter | 7 | Various | - | - | % |
| 7C | Diesel Particulate filter temperature | 9 | ((A*256)+B)/10-40 | -40 | 6513.5 | °C |
| 7D | NOx NTE control area status | 1 | Bit encoded | - | - | - |
| 7E | PM NTE control area status | 1 | Bit encoded | - | - | - |
| 7F | Engine run time | 13 | Various | - | - | seconds |
| 80 | PIDs supported [81-A0] | 4 | Bit encoded | - | - | - |
| 81 | Engine run time for AECD | 21 | Various | - | - | seconds |
| 82 | Engine run time for AECD | 21 | Various | - | - | seconds |
| 83 | NOx sensor | 10 | Various | - | - | ppm |
| 84 | Manifold surface temperature | 1 | Bit encoded | - | - | - |
| 85 | NOx reagent system | 10 | Various | - | - | % |
| 86 | Particulate matter sensor | 5 | Various | - | - | mg/m³ |
| 87 | Intake manifold absolute pressure | 5 | Various | - | - | kPa |
| 88 | SCR Induce System | 13 | Various | - | - | - |
| 89 | Run Time for AECD #11-#15 | 41 | Various | - | - | seconds |
| 8A | Run Time for AECD #16-#20 | 41 | Various | - | - | seconds |
| 8B | Diesel Aftertreatment | 7 | Various | - | - | mg/m³ |
| 8C | O2 Sensor (Wide Range) | ? | Various | - | - | - |
| 8D | Throttle Position G | 1 | A*100/255 | 0 | 100 | % |
| 8E | Engine Friction - Percent Torque | 1 | A-125 | -125 | 130 | % |
| 8F | PM Sensor Bank 1 & 2 | 5 | Various | - | - | seconds |
| 90 | WWH-OBD Vehicle OBD System Info | 3 | Various | - | - | - |
| 91 | WWH-OBD Vehicle OBD System Info | 5 | Various | - | - | - |
| 92 | Fuel System Control | 1 | Various | - | - | - |
| 93 | WWH-OBD Vehicle OBD Counters | 3 | Various | - | - | - |
| 94 | NOx Warning And Inducement System | 12 | Various | - | - | - |
| 98 | Exhaust Gas Temperature Sensor | 9 | Various | - | - | °C |
| 99 | Exhaust Gas Temperature Sensor | 9 | Various | - | - | °C |
| 9A | Hybrid/EV Vehicle System Data | 6 | Various | - | - | - |
| 9B | Diesel Exhaust Fluid Sensor Data | 4 | Various | - | - | % |
| 9C | O2 Sensor Data | 17 | Various | - | - | - |
| 9D | Engine Fuel Rate | 4 | ((A*256)+B)/100 | 0 | 655.35 | g/s |
| 9E | Engine Exhaust Flow Rate | 2 | ((A*256)+B)*0.05 | 0 | 3276.75 | kg/h |
| 9F | Fuel System Percentage | 9 | Various | - | - | % |
| A0 | PIDs supported [A1-C0] | 4 | Bit encoded | - | - | - |

### Formula Legend:
- **A, B, C, D**: Represent the 1st, 2nd, 3rd, 4th data bytes respectively
- **Bit encoded**: Each bit represents a boolean value or specific state
- **Enumerated**: Specific value meanings defined in standard

## Mode 02 - Show Freeze Frame Data

Mode 02 accepts the same PIDs as Mode 01, with the same meanings and formulas, but returns values from when a Diagnostic Trouble Code (DTC) was set. The data represents a "snapshot" of vehicle conditions at the moment of fault detection.

### Key differences from Mode 01:
- **PID 00**: Shows which PIDs are available in freeze frame
- **PID 02**: Shows the DTC that triggered the freeze frame
- All other PIDs use the same formulas as Mode 01

## Mode 03 - Show Diagnostic Trouble Codes

Returns stored Diagnostic Trouble Codes (DTCs). No PID is required for this mode.

### Response Format:
- First byte: Number of trouble codes
- Following bytes: Trouble codes (2 bytes each)

### DTC Format:
```
First byte: [P/C/B/U][0-3][Hex digit][Hex digit]
Second byte: [Hex digit][Hex digit]
```

### DTC Categories:
- **P**: Powertrain (engine & transmission)
- **C**: Chassis
- **B**: Body
- **U**: User/Network

### Example DTCs:
- **P0171**: System Too Lean (Bank 1)
- **P0300**: Random/Multiple Cylinder Misfire
- **P0420**: Catalyst System Efficiency Below Threshold
- **U0001**: High Speed CAN Communication Bus

## Mode 04 - Clear Trouble Codes

Clears all diagnostic trouble codes and resets all monitors. No PID required.

### Command: `04`
### Response: `44` (success)

## Mode 05 - Test Results (Oxygen Sensor Monitoring)

Non-CAN only. Returns oxygen sensor test results.

### PIDs:
- **0100**: OBD Monitor IDs supported ($01 – $20)
- **0101-011F**: O2 Sensor Monitor Bank test results

## Mode 06 - Test Results (Other Components)

Returns test results for continuously and non-continuously monitored systems.

### Test IDs (TID):
- **00**: Supported TIDs [01-20]
- **01-1F**: Various component test results

## Mode 07 - Show Pending DTCs

Shows DTCs detected during current or last driving cycle but not yet confirmed.
Same format as Mode 03.

## Mode 08 - Control Operation

Allows control of on-board systems for testing. Rarely used in consumer applications.

### Example TIDs:
- **01**: Enable/Disable on-board component

## Mode 09 - Request Vehicle Information

### Vehicle Information PIDs:

| PID | Description | Notes |
|-----|-------------|-------|
| 00 | Supported PIDs [01-20] | Bit encoded |
| 01 | VIN Message Count | For ISO 9141-2, ISO 14230-4, SAE J1850 |
| 02 | Vehicle Identification Number (VIN) | 17 characters |
| 03 | Calibration ID Message Count | |
| 04 | Calibration ID | Multiple messages |
| 05 | CVN Message Count | |
| 06 | Calibration Verification Numbers | |
| 07 | In-use performance tracking (spark) | |
| 08 | In-use performance tracking (spark) | |
| 09 | ECU name Message Count | |
| 0A | ECU name | ASCII string |
| 0B | In-use performance tracking (compression) | |
| 0C | ESN Message Count | |
| 0D | Engine Serial Number (ESN) | |

### VIN Decoding Example:
```
Request: 09 02
Response: 49 02 01 [VIN characters in ASCII]
```

## Mode 0A - Permanent DTCs

Returns DTCs that cannot be cleared with Mode 04. These codes remain until the fault is fixed and the vehicle completes the required drive cycles.

## CAN Bus Extended PIDs (Manufacturer Specific)

Many manufacturers implement proprietary PIDs beyond the standard set:

### Common Extended PID Ranges:
- **Mode 21**: Toyota specific
- **Mode 22**: GM/Ford enhanced diagnostics
- **Mode 23-2F**: Various manufacturer specific

### Examples of Manufacturer PIDs:
- **Toyota 21 01**: Hybrid battery voltage
- **GM 22 1940**: Transmission fluid temperature
- **Ford 22 FE01**: PCM calibration number

## Implementation Notes

### Determining Supported PIDs:
Always query PID 00, 20, 40, 60, 80, A0 first to determine which PIDs are supported:
```
Request: 01 00
Response: 41 00 BE 1F A8 13
```
Each bit represents support for a specific PID.

### Response Time:
- Standard requires response within 50ms
- Timeout typically set to 200ms

### Error Codes:
- **7F [mode] 11**: Service not supported
- **7F [mode] 12**: Sub-function not supported
- **7F [mode] 22**: Conditions not correct

## Common Use Cases

### Basic Engine Monitoring:
1. Engine RPM (01 0C)
2. Vehicle Speed (01 0D)
3. Engine Coolant Temp (01 05)
4. Throttle Position (01 11)

### Fuel Economy:
1. MAF Air Flow (01 10)
2. Fuel System Status (01 03)
3. Fuel Pressure (01 0A)
4. O2 Sensor readings (01 14-1B)

### Emissions Testing:
1. Monitor Status (01 01)
2. O2 Sensor tests (Mode 05/06)
3. Catalyst Temperature (01 3C-3F)
4. EGR System (01 2C-2D)

### Diagnostic:
1. Read DTCs (Mode 03)
2. Freeze Frame (Mode 02)
3. Pending DTCs (Mode 07)
4. Clear DTCs (Mode 04)

## References

- SAE J1979: E/E Diagnostic Test Modes
- SAE J2012: Diagnostic Trouble Code Definitions
- ISO 15765-4: Diagnostics on CAN
- ISO 14229: Unified Diagnostic Services (UDS)
- ISO 9141-2: K-Line protocol
- ISO 14230: KWP2000 protocol

---
*This document compiled from SAE J1979 standard and OBD-II implementation guides*
*Last Updated: 2024*