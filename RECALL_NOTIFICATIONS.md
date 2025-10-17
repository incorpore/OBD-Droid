# Vehicle Recall Notifications & History Reports

## Overview

OBD-Droid should integrate vehicle recall notifications and comprehensive vehicle history reporting to provide users with critical safety information and complete vehicle diagnostics context.

## Why This Matters

### Safety & Legal Compliance
- **Life-threatening defects**: Recalls often address serious safety issues (airbags, brakes, steering, fuel systems)
- **Owner responsibility**: Vehicle owners may be unaware of open recalls on their vehicles
- **Legal liability**: Driving vehicles with open safety recalls can create legal exposure
- **Resale value**: Open recalls negatively impact vehicle value and buyer confidence

### User Experience Benefits
- **Proactive alerts**: Users learn about recalls immediately when connecting to their vehicle
- **Comprehensive diagnostics**: Recall context helps interpret fault codes and symptoms
- **One-stop solution**: Users don't need multiple apps for complete vehicle health information
- **Maintenance tracking**: Recall repair history integrated with DTC logs and service records

## Proposed Features

### 1. Recall Lookup & Notifications

#### VIN-Based Recall Check
- Automatic VIN retrieval from vehicle ECU (Mode 09, PID 02)
- Query NHTSA recall database on initial connection
- Cache results and check for updates periodically (weekly/monthly)
- Display badge/notification when open recalls exist

#### Recall Information Display
- **Recall Summary Card** on dashboard when active recalls detected
- **Detailed Recall View** showing:
  - Recall number (NHTSA campaign ID)
  - Issue description and affected components
  - Safety severity rating
  - Remedy/repair information
  - Manufacturer contact information
  - Date recall was issued

#### Notification System
- **In-app banner**: Prominent alert on main screen
- **Push notifications**: Optional alerts when new recalls discovered (requires backend service)
- **Persistent indicator**: Badge on vehicle info until recall resolved

### 2. Vehicle History Reports

#### Integrated History Report
Combine multiple data sources into comprehensive vehicle report:

**Current Implementation (Available)**
- VIN decoding (year, make, model, engine)
- Real-time DTC codes and freeze frame data
- OBD-II supported PIDs and test results
- Emission readiness monitors

**Proposed Additions**
- **Open recall campaigns**: Active safety recalls from NHTSA
- **Recall repair history**: Track when recalls were addressed
- **Service bulletins**: Technical service bulletins (TSBs) for common issues
- **Historical fault codes**: Log of all DTCs encountered over time
- **Mileage tracking**: Odometer readings from OBD-II over time

#### Report Export
- PDF generation for vehicle history reports
- Include recall status, DTC history, and vehicle specifications
- Shareable for insurance, resale, or service shops
- Print-friendly formatting

### 3. Data Sources

#### NHTSA Recall API
- **Free, official source**: National Highway Traffic Safety Administration
- **API endpoint**: `https://api.nhtsa.gov/recalls/recallsByVehicle`
- **Query by VIN**: Returns all open and closed recalls
- **Comprehensive data**: US market vehicles, extensive database
- **No authentication required**: Public API, no API keys needed

**Example API Call:**
```
GET https://api.nhtsa.gov/recalls/recallsByVehicle?make=TOYOTA&model=CAMRY&modelYear=2018
```

**Alternative by VIN:**
```
GET https://api.nhtsa.gov/vehicles/DecodeVinValues/{VIN}?format=json
```

#### Canadian Database (Transport Canada)
- For Canadian market vehicles
- Similar API structure to NHTSA
- Bilingual recall descriptions (English/French)

#### Service Bulletins (Optional)
- **NHTSA TSB Database**: Technical Service Bulletins
- **Manufacturer websites**: OEM-specific bulletins
- Community-sourced data for common issues

## Technical Implementation

### Architecture

```
┌─────────────────────────────────────────────────────┐
│                  OBD-Droid App                      │
├─────────────────────────────────────────────────────┤
│                                                      │
│  ┌──────────────┐      ┌─────────────────────┐    │
│  │  VIN Decoder │─────▶│  Recall Manager     │    │
│  │  (Mode 09)   │      │                     │    │
│  └──────────────┘      │  - Cache recalls    │    │
│                        │  - Check updates    │    │
│  ┌──────────────┐      │  - Notify user      │    │
│  │  Vehicle DB  │─────▶│                     │    │
│  │  (Local)     │      └─────────────────────┘    │
│  └──────────────┘                │                 │
│                                   │                 │
│                        ┌──────────▼──────────────┐ │
│                        │   History Report        │ │
│                        │   Generator             │ │
│                        │                         │ │
│                        │   - DTCs                │ │
│                        │   - Recalls             │ │
│                        │   - Service Bulletins   │ │
│                        │   - PDF Export          │ │
│                        └─────────────────────────┘ │
└─────────────────────────────────────────────────────┘
                               │
                               │ API Calls
                               ▼
                   ┌───────────────────────┐
                   │   NHTSA API           │
                   │   (Public)            │
                   │                       │
                   │   - Recalls by VIN    │
                   │   - VIN decoding      │
                   │   - TSB lookup        │
                   └───────────────────────┘
```

### Database Schema

#### Recalls Table
```sql
CREATE TABLE recalls (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    vin TEXT NOT NULL,
    campaign_id TEXT NOT NULL,
    manufacturer TEXT,
    component TEXT,
    summary TEXT,
    consequence TEXT,
    remedy TEXT,
    recall_date INTEGER,
    status TEXT DEFAULT 'open', -- 'open', 'completed', 'dismissed'
    completed_date INTEGER,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    UNIQUE(vin, campaign_id)
);

CREATE INDEX idx_recalls_vin ON recalls(vin);
CREATE INDEX idx_recalls_status ON recalls(status);
```

#### Vehicle History Table (Enhanced)
```sql
CREATE TABLE vehicle_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    vin TEXT NOT NULL,
    event_type TEXT NOT NULL, -- 'dtc', 'recall', 'service', 'milestone'
    event_data TEXT, -- JSON blob
    odometer INTEGER,
    timestamp INTEGER NOT NULL,
    notes TEXT,
    FOREIGN KEY (vin) REFERENCES vehicles(vin)
);

CREATE INDEX idx_history_vin ON vehicle_history(vin);
CREATE INDEX idx_history_timestamp ON vehicle_history(timestamp);
```

### Implementation Phases

#### Phase 1: Core Recall Lookup (MVP)
- VIN extraction from ECU (already implemented via VehicleManager)
- NHTSA API integration for recall lookup
- Simple recall display card on dashboard
- Local recall cache in SQLite

**Estimated effort**: 2-3 days
**Files to create/modify**:
- `app/src/java/com/obddroid/services/RecallService.java` (new)
- `app/src/java/com/obddroid/ui/dialogs/RecallDetailsDialog.java` (new)
- `MainActivity.java` - add recall check on VIN retrieval

#### Phase 2: History & Tracking
- Recall status tracking (open/completed/dismissed)
- Mark recalls as resolved with date/mileage
- Vehicle history timeline view
- Historical DTC logging

**Estimated effort**: 3-4 days
**Files to create/modify**:
- `app/src/java/com/obddroid/vehicle/VehicleHistory.java` (new)
- `app/src/java/com/obddroid/ui/activities/VehicleHistoryActivity.java` (new)
- Database migrations for history tables

#### Phase 3: Reports & Export
- PDF vehicle history report generation
- Include recalls, DTCs, vehicle specs
- Share/export functionality
- Print-friendly layouts

**Estimated effort**: 2-3 days
**Dependencies**: PDF library (iText, PDFBox, or Android PDF API)

#### Phase 4: Notifications & Polish
- Push notification service (requires backend)
- Periodic recall check scheduler
- User preferences for notifications
- Recall severity badges/icons

**Estimated effort**: 3-4 days
**Infrastructure**: May require backend service for push notifications

## Code Examples

### RecallService (Conceptual)

```java
public class RecallService {
    private static final String NHTSA_API_BASE = "https://api.nhtsa.gov";
    private final Context context;
    private final RecallDatabase database;

    public RecallService(Context context) {
        this.context = context;
        this.database = new RecallDatabase(context);
    }

    /**
     * Fetch recalls for a VIN from NHTSA API
     */
    public List<Recall> fetchRecallsForVin(String vin) {
        // API call to NHTSA
        // Parse JSON response
        // Store in local database
        // Return list of active recalls
    }

    /**
     * Check if recalls need update (weekly check)
     */
    public boolean shouldCheckForUpdates(String vin) {
        long lastCheck = database.getLastCheckTime(vin);
        long weekInMillis = 7 * 24 * 60 * 60 * 1000;
        return (System.currentTimeMillis() - lastCheck) > weekInMillis;
    }

    /**
     * Get count of open recalls for VIN
     */
    public int getOpenRecallCount(String vin) {
        return database.getOpenRecalls(vin).size();
    }
}
```

### Dashboard Integration

```java
// In MainActivity.java - after VIN retrieval
private void onVinRetrieved(String vin) {
    // Existing VIN processing...
    VehicleManager.getInstance(this).setVin(vin);

    // NEW: Check for recalls
    RecallService recallService = new RecallService(this);
    if (recallService.shouldCheckForUpdates(vin)) {
        // Background thread
        new Thread(() -> {
            List<Recall> recalls = recallService.fetchRecallsForVin(vin);
            runOnUiThread(() -> {
                int openRecalls = recalls.stream()
                    .filter(r -> r.isOpen())
                    .count();
                if (openRecalls > 0) {
                    showRecallNotification(openRecalls);
                }
            });
        }).start();
    }
}
```

## User Interface Mockup

### Dashboard Recall Card
```
┌─────────────────────────────────────────┐
│  ⚠️  SAFETY RECALL NOTICE               │
├─────────────────────────────────────────┤
│  2 open recalls for your vehicle        │
│                                          │
│  • Airbag Inflator - HIGH PRIORITY      │
│  • Fuel Pump Module - MODERATE          │
│                                          │
│  [ View Details ]    [ Mark Resolved ]  │
└─────────────────────────────────────────┘
```

### Recall Details Dialog
```
┌─────────────────────────────────────────┐
│  NHTSA Campaign: 23V456                 │
│  Component: Airbag                       │
│  Severity: HIGH                          │
├─────────────────────────────────────────┤
│                                          │
│  Issue Summary:                          │
│  Passenger airbag inflator may rupture   │
│  causing metal fragments to spray...     │
│                                          │
│  Risk: Serious injury or death          │
│                                          │
│  Remedy: Dealer will replace airbag     │
│  inflator free of charge                 │
│                                          │
│  Recall Date: Jan 15, 2024              │
│                                          │
│  [ Schedule Repair ]  [ Dismiss ]       │
│  [ Call Dealer: 1-800-XXX-XXXX ]        │
└─────────────────────────────────────────┘
```

## Privacy & Offline Considerations

### Privacy Protection
- **No account required**: Recall lookups don't require user accounts
- **Local caching**: Recall data stored locally, not transmitted to third parties
- **VIN anonymization**: If backend needed, VINs should be hashed
- **User control**: Option to disable recall checks in settings

### Offline Mode
- **Last known status**: Display cached recall status when offline
- **Manual refresh**: Allow user to trigger manual recall check when online
- **Graceful degradation**: App functions normally without recall feature if offline

## Benefits to Users

1. **Safety awareness**: Users immediately informed of critical safety issues
2. **Proactive maintenance**: Schedule repairs before breakdowns occur
3. **Resale value**: Demonstrate recall compliance to buyers
4. **Cost savings**: Recall repairs are free from manufacturers
5. **Complete diagnostics**: Recall context helps diagnose fault codes
6. **One app solution**: No need for separate VIN check websites/apps

## Next Steps

1. **Research phase**:
   - Test NHTSA API with sample VINs
   - Evaluate response times and rate limits
   - Assess Canadian recall database integration

2. **Design review**:
   - Create UI mockups for recall cards and dialogs
   - Design database schema
   - Plan PDF report layout

3. **Prototype**:
   - Build RecallService with NHTSA API integration
   - Create simple recall display dialog
   - Test with known recalled vehicles

4. **Integration**:
   - Hook into existing VIN retrieval flow
   - Add recall cache database
   - Implement user preferences

5. **Testing**:
   - Test with various VINs (with/without recalls)
   - Verify API error handling
   - Test offline behavior

## Resources

- **NHTSA API Documentation**: https://vpic.nhtsa.dot.gov/api/
- **NHTSA Recalls Search**: https://www.nhtsa.gov/recalls
- **Transport Canada Recalls**: https://tc.canada.ca/en/road-transportation/defect-investigations-recalls
- **VIN Decoding Standard**: ISO 3779, ISO 4030
- **OBD-II VIN Retrieval**: SAE J1979 Mode 09, PID 02

---

**Document Version**: 1.0
**Last Updated**: 2025-01-15
**Author**: Development Team
**Status**: Proposed Feature
