# OBD-Droid

Android OBD-II diagnostics app for ELM327 Bluetooth/WiFi/USB adapters

## Features

- Read real-time OBD data
- Read and clear diagnostic trouble codes (DTCs)
- View freeze frame data
- Support for Bluetooth, WiFi, and USB ELM327 adapters
- Data logging and export capabilities
- Customizable dashboard views
- Multiple language support

## Requirements

- Android device running Android 4.4+
- ELM327 compatible OBD-II adapter
- Vehicle with OBD-II support (most cars manufactured after 1996)

## Quick Start

### Building from Source

1. Clone the repository:
```bash
git clone https://github.com/obddroid/OBD-Droid.git
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

1. Pair your ELM327 adapter with your Android device
2. Launch OBD-Droid
3. Select your adapter from the connection menu
4. Connect to your vehicle with engine running or in ACC mode
5. Navigate through OBD services to view data, codes, and more

## Permissions

The app requires the following permissions:
- Bluetooth (for Bluetooth adapters)
- Internet (for WiFi adapters)
- Storage (for data logging)
- Location (required for Bluetooth on Android 6.0+)

## Development

This project uses:
- Gradle build system
- Android SDK
- Java
