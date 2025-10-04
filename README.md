# OBDroid

Android OBD-II diagnostic application for vehicle diagnostics and monitoring.

## Features

- Real-time vehicle data monitoring
- Read and clear diagnostic trouble codes (DTCs)
- Freeze frame data analysis
- Vehicle information display (VIN decoding)
- Support for Bluetooth, USB, and WiFi ELM327 adapters
- Dashboard and heads-up display modes
- Data logging and export capabilities
- Custom PID support

## Requirements

- Android device running Android 5.0 (API 21) or higher
- ELM327 compatible OBD-II adapter (Bluetooth, USB, or WiFi)
- Vehicle with OBD-II support (1996+ in USA, 2001+ in EU)

## Building

```bash
# Build the app
./gradlew assembleDebug

# Install on connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Quick Deploy

```bash
# Build and deploy in one command
./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk && adb shell am start -n com.obddroid/.activities.MainActivity
```

## Project Structure

```
OBD-Droid/
├── app/                    # Main Android application
│   └── src/
│       ├── java/          # Application source code
│       └── res/           # Resources (layouts, drawables, strings)
├── library-enhanced/       # Core OBD protocol library
│   └── src/
│       ├── java/          # Protocol implementation
│       └── resources/     # Protocol resources and translations
└── custom-pids/           # Custom PID definitions
```

## Supported Protocols

- ISO 15765-4 (CAN)
- ISO 14230-4 (KWP2000)
- ISO 9141-2
- J1850 VPW
- J1850 PWM

## License

This project is open source. Please check the license file for details.

## Author

**Waleed Judah (Wal33D)**
Email: aquataze@yahoo.com