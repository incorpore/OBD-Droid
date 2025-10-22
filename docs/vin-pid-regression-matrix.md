# VIN & PID Regression Matrix

**Prepared by:** Agent B  
**Distribution:** QA Vehicle Experiences (2024-07-16) via QA Slack + TestRail import  
**Purpose:** Provide deterministic coverage for Vehicle History Phase 2 UI, PV enum migration, and ECU catalog refactor staging.

## VIN Coverage
| Scenario | VIN Fixture | Data Characteristics | Notes |
| --- | --- | --- | --- |
| Multiple open recalls + mixed statuses | `1FTFW1E58PKE12345` | 3 recalls (2 open, 1 completed), dealer contact info present | Validates recall card chips, CTA, copy-to-clipboard |
| Clean history (control) | `2GCEK13T961234567` | No recalls, single usage type, odometer consistent | Ensures empty states + success toast |
| Commercial + rental history | `JHMCM56557C404321` | Usage badges: Commercial + Rental, tooltip multi-line copy | Accessibility focus order + tooltip interaction |
| Odometer discrepancy | `1FAFP4041YF123890` | Staged odometer rollback alert plus At a Glance mismatch | Validates red banner + analytics event |
| International VIN (metric units) | `WBA3A5C59E5398765` | Metric odometer, localized copy from CLDR dataset | Ensures locale-sensitive formatting |

## PID Dataset Coverage
| Scenario | Service/PID | Dataset | Expected Behaviour |
| --- | --- | --- | --- |
| High-frequency updates | S01 PID 0C (RPM) | Simulated CAN capture `capture_rpm_highload.pcap` | Verifies `PvChange` enum payload dispatch + UI throttling |
| Manual override path | S01 PID 05 (Coolant Temp) | `manual_override_temp.json` | Confirms `MANUAL_OVERRIDE` flag propagates to UI |
| Error state propagation | S06 PID 12 (O2 Sensor) | `sensor_fault_sample.bin` | Tests ERROR type + dashboard alert banner |
| PID removal scenario | S09 PID 02 (VIN) | `mode9_missing_vin.json` | Ensures REMOVED events clear VIN footer gracefully |
| PID child change | S02 PID 1F (Fuel Trim) | `fuel_trim_child_update.json` | Validates child updates fan out via new enum payload |

## Execution Notes
- Imported VIN fixtures into TestRail suite `VH-Phase2-Regression`; run IDs TR-1893 (staging) and TR-1894 (pre-prod).  
- PID datasets available in shared Drive folder `QA/OBD/VH-Phase2`. Link added to TestRail suite + Confluence page.  
- Analytics validation checklist appended to each VIN case (events fire via local logcat capture + Looker dev dashboard).  
- QA sign-off required before toggling `vehicle_history_recalls`, `vehicle_history_usage`, and `vehicle_history_odometer` flags in production.
