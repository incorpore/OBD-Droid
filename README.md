# OBD-Droid

**Professional-grade Android vehicle diagnostics and telemetry platform**

OBD-Droid is a comprehensive vehicle diagnostic application for Android that provides real-time OBD-II data monitoring, fault code analysis, emissions testing, vehicle history reports, AI-powered diagnostic assistance, and advanced ECU scanning capabilities.

---

## Features

### Core Diagnostics
- **Live Data Monitoring** - Real-time vehicle telemetry with customizable PIDs
- **Fault Code Analysis** - Read and clear diagnostic trouble codes (DTCs) with freeze frame data
- **Full Vehicle Scan** - Comprehensive ECU discovery and multi-module diagnostics
- **ECU Explorer** - Compare baseline scans to detect changes in vehicle modules
- **Emissions Testing** - Monitor readiness status and emission system health
- **Dashboard & Charts** - Visualize vehicle data with customizable gauges and graphs

### Advanced Features
- **CoPilot** - AI-powered diagnostic assistant with conversational interface
- **Vehicle History** - AutoCheck integration for comprehensive vehicle reports
- **Safety Recalls** - NHTSA recall lookup by VIN
- **Fuel Economy** - Track fuel consumption and driving efficiency
- **CSV Data Logging** - Export telemetry data with GPS and sensor integration

### Connectivity
- Bluetooth OBD-II adapters (ELM327 and compatible)
- USB OBD-II adapters
- Network/WiFi adapters
- Multi-protocol support (ISO 9141-2, KWP2000, CAN)

---

## Project Structure

```
OBD-Droid/
├── app/src/java/com/obddroid/
│   ├── features/              # Feature modules
│   │   ├── copilot/          # AI diagnostic assistant
│   │   ├── emissions/        # Emissions diagnostics
│   │   ├── fueleconomy/      # Fuel economy tracking
│   │   ├── recalls/          # NHTSA safety recalls
│   │   ├── vehiclehistory/   # AutoCheck integration
│   │   └── common/           # Shared feature code
│   ├── ui/                   # User interface
│   │   ├── activities/       # Main app screens
│   │   ├── adapters/         # List and data adapters
│   │   ├── components/       # Reusable UI components
│   │   ├── coordinators/     # Feature coordinators
│   │   └── helpers/          # UI helper utilities
│   ├── obd/                  # OBD-II protocol implementation
│   ├── can/                  # CAN bus support
│   ├── ecu/                  # ECU data handling
│   ├── scan/                 # Vehicle scanning engine
│   ├── telemetry/            # Telemetry management
│   ├── services/             # Background services
│   └── utils/                # Utility classes
├── modules/                  # External libraries (git submodules)
│   ├── automotive-logo-library/
│   ├── dtc-database/         # DTC code database
│   └── nhtsa-recall-lookup/  # NHTSA API integration
├── docs/                     # Planning and documentation
└── build.gradle             # Gradle build configuration
```

---

## Getting Started

### Prerequisites
- **Android Studio** Giraffe or newer
- **JDK** 17 or higher
- **Android SDK** API 21-34 (Android 5.0 - Android 14)
- **Android device or emulator** running Android 5.0+ (recommended: Android 8.0+)
- **OBD-II adapter** (Bluetooth, USB, or WiFi)

### Build & Install

```bash
# Clone the repository
git clone https://github.com/Wal33D/OBD-Droid.git
cd OBD-Droid

# Initialize submodules
git submodule update --init --recursive

# Build and install debug APK
./gradlew assembleDebug
./gradlew installDebug

# Launch the app
adb shell am start -n com.obddroid/.ui.activities.MainActivity
```

### Development Build

```bash
# Clean build
./gradlew clean

# Compile and run tests
./gradlew :app:testDebugUnitTest

# Run lint checks
./gradlew :app:lintDebug

# Build debug APK
./gradlew :app:assembleDebug
```

---

## Key Components

### MainActivity
**Location:** `app/src/java/com/obddroid/ui/activities/MainActivity.java`

Main application shell that manages vehicle connections, displays fault codes, and provides navigation to all features.

### Live Data & Dashboard
**Locations:**
- `app/src/java/com/obddroid/ui/activities/LiveDataActivity.java`
- `app/src/java/com/obddroid/ui/activities/DashBoardActivity.java`

Real-time vehicle telemetry monitoring with customizable data displays.

### OBD Protocol Handler
**Location:** `app/src/java/com/obddroid/obd/ObdProt.java`

Core OBD-II protocol implementation supporting multiple communication standards.

### Communication Services
**Location:** `app/src/java/com/obddroid/services/`

- `CommService` - Base communication layer
- `BluetoothCommService` - Bluetooth adapter communication
- `UsbCommService` - USB adapter communication
- `NetworkCommService` - WiFi/network adapter communication

### Scanning Engine
**Location:** `app/src/java/com/obddroid/scan/`

Multi-stage vehicle scanning system with ECU discovery and comprehensive diagnostics.

### Feature Activities
**Locations:**
- `features/copilot/ui/CoPilotActivity.java` - AI diagnostic assistant
- `features/emissions/ui/EmissionsActivity.java` - Emissions testing
- `features/fueleconomy/ui/FuelEconomyActivity.java` - Fuel economy tracking
- `features/recalls/ui/RecallActivity.java` - NHTSA safety recalls
- `features/vehiclehistory/ui/AutoCheckActivity.java` - Vehicle history reports

---

## Main Page Features

The OBD-Droid main page provides quick access to all diagnostic and vehicle intelligence features through an intuitive dashboard interface.

### Action Bar Controls

**Connect/Disconnect Button**
- Tap to connect to your OBD-II adapter (Bluetooth/USB/WiFi)
- When connected, displays current connection status
- Tap again while connected to safely disconnect

**Settings Button**
- Access app preferences and configuration
- Configure telemetry settings (CSV logging, GPS, sensors)
- Manage CoPilot API settings
- Adjust display units and connection preferences

### Dashboard Feature Cards

The main dashboard displays feature cards that become active once connected to a vehicle:

**Live Data**
- Real-time vehicle telemetry monitoring
- View all supported PIDs with live updates
- Customizable data display (list, chart, dashboard, heads-up)
- Export data to CSV with GPS/sensor integration
- **Location:** `app/src/java/com/obddroid/ui/activities/LiveDataActivity.java`

**Test Control**
- Access OBD-II test modes and control functions
- View oxygen sensor test results
- Monitor on-board monitoring test results
- Request test control data from ECU
- Requires active ECU connection

**Fault Codes**
- Read diagnostic trouble codes (DTCs) from all modules
- View detailed fault code information and descriptions
- Access freeze frame data for each code
- Clear codes (with vehicle off confirmation)
- Support for generic and manufacturer-specific codes
- **Location:** `app/src/java/com/obddroid/ui/activities/FaultCodesActivity.java`

**Fuel Economy**
- Track real-time fuel consumption
- Monitor average fuel economy
- View fuel economy history and trends
- Calculate trip efficiency
- **Location:** `app/src/java/com/obddroid/features/fueleconomy/ui/FuelEconomyActivity.java`

**Emissions Diagnostics**
- Check emissions readiness status
- View monitor readiness for emissions testing
- Verify catalytic converter efficiency
- Check oxygen sensor functionality
- View comprehensive emissions test results
- **Location:** `app/src/java/com/obddroid/features/emissions/ui/EmissionsActivity.java`

**Vehicle History**
- Access comprehensive AutoCheck vehicle history reports
- View ownership history and title information
- Check for reported accidents and damage
- Verify odometer readings and usage patterns
- Requires VIN (auto-detected from vehicle or manual entry)
- **Location:** `app/src/java/com/obddroid/features/vehiclehistory/ui/AutoCheckActivity.java`

**Safety Recalls**
- Search NHTSA database for active safety recalls
- View recall details and remedy information
- Check recall status by VIN
- Access manufacturer recall campaigns
- **Location:** `app/src/java/com/obddroid/features/recalls/ui/RecallActivity.java`

**ECU List**
- View all discovered electronic control units
- See detailed ECU information and addressing
- Compare current scan to baseline scans
- Track ECU changes over time
- Save baseline scans for future comparison
- **Location:** `app/src/java/com/obddroid/ui/activities/EcuListActivity.java`

**Vehicle Info**
- Display complete vehicle identification information
- Show decoded VIN details (make, model, year, engine)
- View vehicle specifications
- Display manufacturer logos
- Access vehicle-specific data
- **Location:** `app/src/java/com/obddroid/ui/activities/VehicleInfoActivity.java`

**CoPilot (AI Assistant)**
- AI-powered diagnostic assistant
- Ask questions about fault codes and symptoms
- Get repair recommendations and insights
- View conversation history
- Context-aware responses based on vehicle data
- Powered by Claude API integration
- **Location:** `app/src/java/com/obddroid/features/copilot/ui/CoPilotActivity.java`

### Additional Features

**Full Vehicle Scan**
- Accessible via menu or dashboard
- Comprehensive multi-ECU discovery
- Scan all available modules
- Generate detailed diagnostic reports
- Save scan results for baseline comparison
- **Location:** `app/src/java/com/obddroid/ui/activities/ScanActivity.java`

**Data View Modes**
- List View - Traditional scrolling data list
- Chart View - Real-time graphing of selected PIDs
- Dashboard - Gauge-style data visualization
- Heads-Up Display - Minimalist view for in-vehicle use

### Card Behavior

- **Disabled State:** Cards appear grayed out when vehicle is not connected
- **Active State:** Cards become fully interactive once ECU connection is established
- **Press Animation:** Cards provide visual feedback with press animations
- **Quick Access:** Tap any card to immediately launch that feature
- **Status Indicators:** Some cards show live data (e.g., fault code count)

---

## Usage

### Connecting to a Vehicle

1. Launch OBD-Droid
2. Tap the connection button or navigate to adapter selection
3. Choose your connection method (Bluetooth/USB/Network)
4. Select your OBD-II adapter from the list
5. Wait for connection and protocol initialization
6. Start monitoring live data or run diagnostics

### Reading Fault Codes

1. Connect to your vehicle
2. Fault codes automatically load on the main screen
3. Tap any code to view detailed information and freeze frame data
4. Use the menu to clear codes (requires ignition off)

### Running a Full Vehicle Scan

1. From the main screen, tap "Full Vehicle Scan"
2. The app will discover and scan all available ECUs
3. View detailed results for each module
4. Save baseline scans for future comparison

### Using CoPilot AI Assistant

1. Tap the CoPilot icon from the main screen
2. Ask questions about your vehicle or diagnostic codes
3. CoPilot analyzes your vehicle data and provides insights
4. View conversation history and previous diagnostic sessions

### Vehicle History & Recalls

1. Navigate to Vehicle History or Safety Recalls
2. Enter your VIN or scan from connected vehicle
3. View comprehensive reports including:
   - Ownership history (AutoCheck)
   - Accident reports
   - Active safety recalls (NHTSA)
   - Title information

---

## Configuration

### Settings
Access settings from the menu to configure:
- Adapter connection preferences
- Data logging options
- Telemetry settings (GPS, sensors)
- CoPilot API configuration
- Display units and preferences

### CSV Data Logging
Enable CSV logging to export vehicle telemetry:
1. Go to Settings > Telemetry
2. Enable CSV Logging
3. Configure logging interval and data points
4. Access exported CSV files from app storage

---

## Architecture

### Services
- **CsvLoggingService** - Foreground service for continuous data logging
- **ScanOrchestrator** - Manages full vehicle scan operations
- **ObdDataService** - Handles OBD data collection and processing

### State Management
- **StateManager** - Centralized application state
- **ProcessVariables** - Real-time data variable system
- **VehicleManager** - Vehicle information management

### Data Flow
```
Adapter → CommService → ObdProt → ObdDataService → UI Activities
                                 ↓
                          Telemetry → CSV Logging
```

---

## Contributing

### Code Guidelines
1. Follow existing code style and conventions
2. Keep feature-specific code within `features/` package
3. Use coordinators for complex UI interactions
4. Update documentation when adding features
5. Test on multiple Android versions

### Testing
```bash
# Run unit tests
./gradlew :app:testDebugUnitTest

# Run instrumentation tests
./gradlew :app:connectedDebugAndroidTest

# Check for compilation errors
./gradlew :app:compileDebugJavaWithJavac
```

---

## External Dependencies

### Submodules
- **dtc-database** - Comprehensive diagnostic trouble code database
- **nhtsa-recall-lookup** - NHTSA vehicle recall API integration
- **automotive-logo-library** - Vehicle manufacturer logo assets

### AutoCheck API
The vehicle history feature requires the AutoCheck companion API service:
```bash
cd /path/to/autocheck-api
npm install
npm run dev
```
Update the API endpoint in app settings or configuration.

---

## Troubleshooting

### Connection Issues
- Ensure Bluetooth/USB permissions are granted
- Check adapter is properly plugged into OBD-II port
- Verify vehicle ignition is on
- Try different protocol settings if auto-detect fails

### No Data Displayed
- Confirm vehicle supports OBD-II (1996+ for US vehicles)
- Check adapter compatibility
- Verify correct protocol is selected
- Try manual protocol selection

### Build Errors
- Ensure JDK 17+ is installed
- Update Android SDK to latest version
- Sync Gradle files
- Clean and rebuild project

---

## License

This project is private and proprietary. All rights reserved.

---

## Support

For issues, questions, or feature requests, please open an issue on GitHub or contact the development team.

---

**Current Version:** OBDroid v20616
**Last Updated:** October 2025
**Minimum Android:** 5.0 (API 21)
**Target Android:** 14 (API 34)
