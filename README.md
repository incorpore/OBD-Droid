# OBD-Droid

Android OBD-II diagnostics app for ELM327 Bluetooth/WiFi/USB adapters

## Features

- **Real-time OBD data** - Read live sensor data from your vehicle
- **Diagnostic trouble codes** - Read and clear DTCs with descriptions
- **Freeze frame data** - View snapshot data when codes were set
- **Multi-connection support** - Bluetooth, WiFi, and USB ELM327 adapters
- **Data logging** - Record and export diagnostic data
- **Customizable displays** - Dashboard views, charts, and gauges
- **Multi-language** - Support for 40+ languages
- **Plugin framework** - Extend functionality with plugins
- **Data customization** - Add custom PIDs and conversions

### Version 1.2.x Features

- **CSV based control data** for PIDs, conversions, and code lists
- **New conversion types** - Bitmap and hash conversions for state messages
- **Bar gauge display** in data screen
- **Consistent coloring** across all display modes
- **Advanced customization** via user-defined CSV files

## Requirements

- Android device running Android 4.4+
- ELM327 compatible OBD-II adapter
- Vehicle with OBD-II support (most cars manufactured after 1996)

## Quick Start

### Building from Source

1. Clone the repository:
```bash
git clone https://github.com/Wal33D/OBD-Droid.git
cd OBD-Droid
```

2. Set up local SDK path:
```bash
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
```

3. Build the app:
```bash
./gradlew assembleDebug
```

### Installing on Device

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

## Usage

1. **Pair your adapter** - Connect your ELM327 adapter via Bluetooth/WiFi
2. **Launch OBD-Droid** - Start the application
3. **Select adapter** - Choose your adapter from the connection menu
4. **Connect to vehicle** - With engine running or in ACC mode
5. **Access OBD services** - View data, codes, and diagnostics

## Configuration & Customization

### Data Model Structure

OBD-Droid uses a CSV-based configuration system with three main components:

#### 1. Code Lists
Simple mapping of diagnostic fault codes to descriptions:

| Column | Description |
|--------|-------------|
| 1 | DFC number |
| 2 | DFC description |

#### 2. Data Conversions
Define how raw OBD data is converted to meaningful values:

| Type | Description |
|------|-------------|
| LINEAR | Linear numeric conversion for physical values |
| HASH | Convert numeric values to state messages |
| BITMAP | Convert bit masks to descriptive states |
| PCODELIST | Convert OBD fault codes to descriptions |
| CODELIST | Convert any code number to description |
| ASCII | Convert protocol buffer to ASCII string |

**Linear Conversion Formula:**
```
physicalValue = ((rawValue + OFFS) * FACT / DIV) + PhOf
```

#### 3. Data Items / PIDs
Configure how data is extracted from OBD responses:

| Field | Description |
|-------|-------------|
| svc | OBD service(s) (comma-separated HEX) |
| pid | OBD PID number (HEX) |
| ofs | Byte offset in response |
| len | Length in bytes |
| bit_ofs | Bit offset within extracted value |
| bit_len | Length in bits |
| bit_mask | Bit mask for extraction |
| formula | Conversion ID to use |
| format | Display format (printf syntax) |
| min/max | Value limits |
| label | Display label |

### Custom Data Files

Add custom PIDs, conversions, or fault codes by:

1. Create CSV files with your custom data
2. Store in `/sdcard/com.obddroid.ecu.gui.androbd/custom/`
3. Select files in app settings
4. Files load at each app startup

Template files are available in `/customization/templates/`

## Plugin Framework

OBD-Droid supports extension plugins for additional functionality.

### Plugin Architecture

Plugins are separate APK packages that:
- Cannot run standalone
- Are invoked by OBD-Droid
- Handle their own configuration and storage
- Communicate via Android intents

### Plugin Features

Plugins declare supported features via bitmask:

| Bit | Feature | Description |
|-----|---------|-------------|
| 0 | PLUGIN_CONFIG | Configuration dialog support |
| 1 | MANUAL_ACTION | Manual trigger support |
| 2 | DATA_UPDATE | Receives data from OBD-Droid |
| 3 | DATA_PROVISION | Provides data to OBD-Droid |

### Plugin Communication

#### Identification
Plugins respond to broadcast intent:
- `com.obddroid.androbd.plugin.Plugin.IDENTIFY`

Response includes:
- NAME, VERSION, CLASS
- FEATURES (bitmask)
- DESCRIPTION, COPYRIGHT, LICENSE

#### Data Exchange
For DATA_UPDATE/DATA_PROVISION features:

**DATALIST** - List of available data items:
- Name (unique mnemonic)
- SVC/PID (OBD identifiers)
- Description
- Units

**DATA** - Value updates:
- Name (data item identifier)
- Value (textual representation)

### Creating Plugins

1. Extend `com.obddroid.androbd.plugin.Plugin`
2. Implement required intent handlers
3. Declare supported features
4. Package as separate APK

Example plugins:
- **CSV Logger** - Log OBD data to CSV files
- **MQTT Publisher** - Publish data to MQTT broker

## Permissions

Required permissions:
- **Bluetooth** - For Bluetooth adapters
- **Internet** - For WiFi adapters and online features
- **Storage** - For data logging and custom files
- **Location** - Required for Bluetooth on Android 6.0+
- **Wake Lock** - Keep connection active with screen off

## Development

### Project Structure

```
OBD-Droid/
├── androbd/          # Main Android application
├── library/          # Core OBD library
├── plugin/           # Plugin framework
└── customization/    # Template files
```

### Technologies

- **Build System**: Gradle
- **Language**: Java
- **Min SDK**: API 19 (Android 4.4)
- **Target SDK**: Latest stable

### Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly
5. Submit a pull request

## Troubleshooting

### Connection Issues
- Ensure adapter is properly paired
- Check vehicle compatibility
- Try different connection protocols
- Verify adapter firmware is up to date

### Data Issues
- Clear stored DTCs after repairs
- Check custom PID configurations
- Verify measurement system settings
- Review log files for errors

## Support
- Check documentation in `/customization/templates/`
- Review plugin examples for extending functionality

---

*Keep on hacking... OBD-Droid Team*