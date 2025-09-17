# OBDroid

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
- **Custom vehicle support** - Add custom PIDs and conversions

### Version 1.2.x Features

- **CSV based control data** for PIDs, conversions, and code lists
- **New conversion types** - Bitmap and hash conversions for state messages
- **Bar gauge display** in data screen
- **Consistent coloring** across all display modes
- **Advanced configuration** via user-defined CSV files

## Requirements

- Android device running Android 4.4+
- ELM327 compatible OBD-II adapter
- Vehicle with OBD-II support (most cars manufactured after 1996)

## Quick Start

### Building from Source

1. Clone the repository:
```bash
git clone https://github.com/Wal33D/OBDroid.git
cd OBDroid
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
2. **Launch OBDroid** - Start the application
3. **Select adapter** - Choose your adapter from the connection menu
4. **Connect to vehicle** - With engine running or in ACC mode
5. **Access OBD services** - View data, codes, and diagnostics

## Configuration & Customization

### Data Model Structure

OBDroid uses a CSV-based configuration system with three main components:

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

### Custom Vehicle PIDs & Data Conversions

OBDroid allows you to add support for non-standard PIDs and custom data conversions that are specific to your vehicle. This is useful for:
- Manufacturer-specific PIDs not in the OBD-II standard
- Aftermarket sensors and modifications
- Custom calculated values from existing PIDs

#### How to Add Custom PIDs:

1. **Start with template files** in `/custom-pids/` directory:
   - **For beginners**: Use `example_basic_*` files as a starting point
   - **For advanced users**: See `example_advanced_*` files for complex examples
   - **Blank templates**: Use `template_blank_*` files to start from scratch

2. **Copy your customized files** to your Android device:
   - Store in `/sdcard/com.obddroid.ecu.gui.androbd/custom/`
   - Or select files via app settings menu

3. **Files automatically load** when the app starts

#### Understanding the CSV Files:

**cust_pids.csv** - Defines custom PIDs:
- `svc`: Service mode (0x01, 0x02, etc.)
- `pid`: Parameter ID in hex (e.g., 0x5F)
- `formula`: Links to conversion formula name
- `label`: Display name in the app
- `min/max`: Expected value range

**cust_conversions.csv** - Defines data conversion formulas:
- `LINEAR`: Mathematical conversions (multiply/divide/offset)
- `HASH`: Map numeric values to text (1="Running", 2="Stopped")
- `BITMAP`: Decode bit flags for status indicators
- Supports both METRIC and IMPERIAL units

**Example Files Included**:
- `example_basic_*` - Simple examples (turbo boost, oil temp, transmission temp)
- `example_advanced_*` - Complex examples (hash states, bitmaps, multi-unit conversions)
- `template_blank_*` - Empty templates ready for your custom PIDs

**Quick Example**: Adding turbo boost pressure (see example_basic files):
1. Conversion: `TURBO_BOOST_PSI,LINEAR,0,IMPERIAL,0.145,1,0,-14.7,psi`
2. PID: `0x01,0x67,0,1,0,8,0xFF,TURBO_BOOST_PSI,%.1f,-14.7,30,turbo_boost,Turbo Boost`
3. Copy to device and restart app

## Plugin System

OBDroid features an extensible plugin architecture that allows third-party developers to add functionality without modifying the core app.

### Plugin Manager

Access the Plugin Manager from the main menu to:
- View installed OBDroid compatible plugins
- See plugin capabilities and features
- Configure plugin settings (when supported)
- Enable/disable individual plugins

### How Plugins Work

Plugins are separate Android apps that:
- Register to handle the `com.obddroid.androbd.plugin.IDENTIFY` intent
- Cannot run standalone - they extend OBDroid functionality
- Communicate with OBDroid via Android intents
- Handle their own data storage and configuration

### Plugin Capabilities

Plugins declare their features using a bitmask system:

| Bit | Feature | Description |
|-----|---------|-------------|
| 0 | CONFIG | Has configuration interface |
| 1 | ACTION | Supports manual trigger actions |
| 2 | DATA | Receives OBD data updates |
| 3 | DATA_PROVIDER | Provides data to OBDroid |

### Creating a Plugin

#### 1. Basic Plugin Structure

Create a new Android app with an Activity that handles the IDENTIFY intent:

```xml
<!-- AndroidManifest.xml -->
<activity android:name=".PluginActivity">
    <intent-filter>
        <action android:name="com.obddroid.androbd.plugin.IDENTIFY" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</activity>
```

#### 2. Respond to Identification

When OBDroid queries for plugins, respond with your plugin's information:

```java
// In your plugin's activity
Intent response = new Intent();
response.putExtra("NAME", "My OBD Plugin");
response.putExtra("VERSION", "1.0");
response.putExtra("FEATURES", 0x05); // CONFIG + DATA
response.putExtra("DESCRIPTION", "Logs OBD data to CSV");
setResult(RESULT_OK, response);
```

#### 3. Handle Data Updates

If your plugin has the DATA feature, register to receive OBD data:

```java
<receiver android:name=".DataReceiver">
    <intent-filter>
        <action android:name="com.obddroid.androbd.plugin.DATA" />
    </intent-filter>
</receiver>
```

#### 4. Provide Custom Data (Optional)

Plugins with DATA_PROVIDER can inject custom sensor data back to OBDroid.

### Example Plugin Ideas

- **CSV Logger** - Save OBD data to CSV files for analysis
- **Cloud Sync** - Upload driving data to cloud services
- **MQTT Bridge** - Stream real-time data to IoT platforms
- **Performance Analyzer** - Calculate 0-60 times, quarter mile, etc.
- **Fuel Tracker** - Monitor fuel economy and costs
- **Maintenance Reminder** - Track service intervals
- **Custom Gauges** - Additional visualization options
- **Voice Alerts** - Spoken warnings for parameters

### Plugin Development Tips

1. **Test with Plugin Manager** - Ensure your plugin appears in the list
2. **Handle permissions** - Request necessary Android permissions
3. **Respect battery** - Don't drain battery with excessive processing
4. **Follow conventions** - Use the intent structure documented above
5. **Provide settings** - Let users configure plugin behavior

### Available Plugin Examples

Check the `/plugin` directory for the plugin framework structure. While full plugin examples are coming soon, the framework provides:
- Intent definitions
- Data exchange protocols
- Communication patterns

Visit the [OBDroid GitHub](https://github.com/Wal33D/OBDroid) for plugin examples and templates

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
OBDroid/
├── androbd/          # Main Android application
├── library/          # Core OBD library
├── plugin/           # Plugin framework
├── custom-pids/      # Custom PID template files
└── desktop-launcher/ # Desktop Java test scripts (development only)
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
- Check template files in `/custom-pids/` for examples
- Review plugin examples for extending functionality

---

*Keep on hacking... OBDroid Team*