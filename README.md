# OBDroid

Android OBD-II diagnostics app for ELM327 Bluetooth/WiFi/USB adapters

## Features

### Core Diagnostics
- **Real-time OBD data** - Monitor live sensor data from your vehicle's ECU
- **Diagnostic trouble codes** - Read and clear DTCs with detailed descriptions and MIL status
- **Freeze frame data** - View snapshot data when codes were set
- **Vehicle information** - Read VIN, calibration IDs, and ECU information
- **Test control mode** - Execute OBD test procedures

### Connectivity & Display
- **Multi-connection support** - Bluetooth, WiFi, and USB ELM327 adapters
- **Demo Mode** - Built-in OBD simulator for testing without hardware
- **Multiple display modes** - Dashboard gauges, charts, lists, and HUD views
- **Full screen mode** - Immersive experience with status bar integration
- **Data logging** - Record and export diagnostic data to CSV

### Customization
- **Custom PIDs** - Add manufacturer-specific parameters
- **User-defined conversions** - Create custom data transformations
- **Multi-language support** - Available in 40+ languages
- **Configurable displays** - Customize colors, ranges, and update rates

## Requirements

- Android device running Android 4.2+ (API 17)
- ELM327 compatible OBD-II adapter (or use Demo Mode)
- Vehicle with OBD-II support (most cars manufactured after 1996)

## Quick Start

### Building from Source

1. Clone the repository:
```bash
git clone https://github.com/Wal33D/OBD-Droid.git
cd OBD-Droid
```

2. Build the app:
```bash
./gradlew assembleDebug
```

### Installing on Device

#### Quick Deploy (One Command)
```bash
# Build and deploy
./gradlew assembleDebug && adb install -r ./androbd/build/outputs/apk/debug/androbd-debug.apk && adb shell am start -n com.obddroid.ecu.gui.androbd/.MainActivity
```

#### WiFi Installation (Wireless ADB)
```bash
# Connect to device
adb connect YOUR_DEVICE_IP:5555

# Install and launch
adb install -r ./androbd/build/outputs/apk/debug/androbd-debug.apk
adb shell am start -n com.obddroid.ecu.gui.androbd/.MainActivity
```

#### USB Installation
```bash
# With device connected via USB
adb install -r ./androbd/build/outputs/apk/debug/androbd-debug.apk
adb shell am start -n com.obddroid.ecu.gui.androbd/.MainActivity
```

## Usage Guide

### Getting Started
1. **Launch OBDroid** - Start the application
2. **Select connection type** - Choose Bluetooth, USB, or Demo Mode
3. **Pair adapter** (if using hardware) - Select your ELM327 adapter
4. **Connect to vehicle** - With engine running or ignition on
5. **Access OBD services** - View data, codes, and diagnostics

### Demo Mode
Try the app without hardware! Demo Mode provides:
- Simulated OBD data and responses
- All app features available for testing
- Perfect for learning the interface
- Access via "Demo Mode" in device selection

### Navigation
- **Home button** - Quick return to main menu
- **Full screen mode** - Toggle in settings for immersive view
- **Service selection** - Access different OBD services from main screen
- **Clear codes** - Built-in dialog with safety confirmation

## Custom PIDs & Data Conversions

OBDroid supports custom vehicle-specific PIDs and data conversions for:
- Manufacturer-specific parameters
- Aftermarket sensors and modifications
- Custom calculated values

### Adding Custom PIDs

1. **Use template files** from `/custom-pids/`:
   - `example_basic_*` - Simple examples (turbo boost, oil temp)
   - `example_advanced_*` - Complex examples (bitmaps, state mappings)
   - `template_blank_*` - Empty templates to start fresh

2. **Copy to device**:
   - Store in `/sdcard/com.obddroid.ecu.gui.androbd/custom/`
   - Or select via app settings

3. **Files auto-load** on app start

### Configuration Files

#### cust_pids.csv - Define custom PIDs
| Field | Description | Example |
|-------|-------------|---------|
| svc | Service mode | 0x01 |
| pid | Parameter ID | 0x5F |
| formula | Conversion name | TURBO_BOOST_PSI |
| label | Display name | Turbo Boost |
| min/max | Value range | -14.7/30 |

#### cust_conversions.csv - Data transformations
| Type | Use Case | Example |
|------|----------|---------|
| LINEAR | Math conversions | Temperature, pressure |
| HASH | Value to text mapping | 1="Running", 2="Stopped" |
| BITMAP | Decode bit flags | Status indicators |
| ASCII | Protocol to text | VIN decoding |

## Project Structure

```
OBD-Droid/
├── androbd/          # Main Android application
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/     # Application source code
│   │   │   └── res/      # Resources (layouts, strings)
│   │   └── build.gradle  # App configuration
├── library/          # Core OBD protocol library
│   ├── src/
│   │   └── main/java/    # Protocol implementation
│   └── build.gradle      # Library configuration
├── custom-pids/      # Custom PID examples
└── CLAUDE.md        # Development instructions
```

## Technical Details

### Build Configuration
- **Compile SDK**: 34 (Android 14)
- **Minimum SDK**: 17 (Android 4.2)
- **Target SDK**: 25 (for compatibility)
- **Java Version**: 17
- **Gradle**: Modern Android build system

### Key Dependencies
- **USB Serial Library**: 3.9.0 - USB adapter support
- **SpeedView**: 1.6.1 - Gauge displays
- **AndroidX**: Modern support libraries

### Permissions Required
- **Bluetooth** - For Bluetooth adapters
- **Internet** - For WiFi adapters
- **Storage** - For data logging and custom files
- **Location** - Required for Bluetooth on Android 6.0+
- **Wake Lock** - Keep connection active

## Troubleshooting

### Connection Issues
- Ensure adapter is paired in Android Bluetooth settings
- Try Demo Mode to test app functionality
- Verify adapter is ELM327 compatible
- Check vehicle ignition is ON

### Data Issues
- Clear fault codes after vehicle repairs
- Verify custom PID syntax in CSV files
- Check measurement units in settings
- Review logs for protocol errors

### Common Problems
- **No data**: Ensure vehicle is OBD-II compliant
- **Connection drops**: Check adapter power and range
- **Wrong values**: Verify unit settings (metric/imperial)
- **Missing PIDs**: Not all vehicles support all parameters

## Development

### Setting Up Development Environment
1. Install Android Studio
2. Clone repository
3. Open project in Android Studio
4. Sync Gradle files
5. Run on device or emulator

### Contributing
1. Fork the repository
2. Create feature branch
3. Make changes following existing code style
4. Test on multiple devices
5. Submit pull request

### Author
**Waleed Judah (Wal33D)**
Email: aquataze@yahoo.com

---

*Keep on hacking... OBDroid Team*