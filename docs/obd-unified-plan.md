# Core Platform Plan (Simplified)

## Must Do Next
- [ ] Stabilize PvChange enum payload and migrate top listeners (MainActivity, VehicleInfoFooter).
- [ ] Ship Vehicle History Phase 2 UI behind feature flags (recall list, usage badges, odometer sub-checks).
- [ ] Deploy AutoCheck companion API on our server to production and point the app at the hosted endpoint.
- [ ] Stand up Gauges hub page with selectable gauges and retire the legacy implementation.

## Stretch Goals
- [x] **COMPLETED** - Build full vehicle scan workflow + report export (ScanOrchestrator, ScanActivity, 4 MVP stages).
- [x] **COMPLETED** - Prototype Knight Rider-style CoPilot conversational UI (Full implementation with Lottie avatar, voice I/O, expert AI).
- [x] **COMPLETED** - Add AI diagnosis assistant on top of full-scan data (DiagnosticAnalyzer with one-tap analysis).

## Parking Lot / Research
- [ ] ECU conversion/class renames (ValueConversion etc.) once PvChange migration stabilizes.
- [ ] Typed ProcessVariable interfaces replacing raw maps.
- [ ] Dealer diagnostics backlog (see dealer-diagnostics-roadmap.md).
