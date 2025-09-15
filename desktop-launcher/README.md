# Desktop Launcher - Java Desktop Testing Tool

This directory contains scripts for running OBD-Droid's diagnostic core on desktop computers using Java Swing GUI (not Android).

## What It Does

The desktop launcher runs `ObdTestFrame` - a Java Swing application that:
- Tests OBD protocol communication without Android
- Connects directly to serial ports (USB/Bluetooth adapters)
- Provides a desktop GUI for protocol development
- Runs in simulation mode for testing without hardware

## Files

- **JObd.sh** - Linux/Mac launcher script
- **JObd.bat** - Windows launcher script
- **logging.properties** - Java logging configuration

## How It Works

1. **The Core Library** (`/library`) contains both:
   - Android-compatible classes (used by the app)
   - Desktop GUI classes (excluded from Android build)

2. **ObdTestFrame** is a full Java Swing application that:
   - Uses the same OBD protocol handlers as the Android app
   - Provides serial port communication via Java
   - Shows real-time OBD data in desktop windows
   - Supports VAG (Volkswagen Group) specific protocols

3. **Serial Port Setup**:
   - Script configures serial port settings (38400 baud, 8N1)
   - Passes port name to Java application
   - Falls back to DEMO mode if no port specified

## Usage

### Linux/Mac:
```bash
# With serial port (e.g., USB adapter)
./JObd.sh /dev/ttyUSB0

# With Bluetooth serial
./JObd.sh /dev/rfcomm0

# Demo mode (no hardware)
./JObd.sh
```

### Windows:
```batch
REM With serial port
JObd.bat COM3

REM Demo mode
JObd.bat
```

## Requirements

- Java 8 or higher
- Serial port drivers for your adapter
- The compiled library JAR file

## Building the Desktop Version

The library builds automatically with Gradle:
```bash
./gradlew :library:jar
```

This creates `library/build/libs/library.jar` containing:
- Core OBD protocol classes
- Desktop GUI components (ObdTestFrame, VagTestFrame)
- Serial communication handlers

## Important Notes

1. **Not for End Users** - This is a development tool
2. **Different from Android App** - Uses Java Swing, not Android UI
3. **Same Protocol Core** - Tests the exact same OBD logic
4. **Serial Port Access** - Requires proper permissions on Linux/Mac

## Architecture

```
Desktop Mode:
JObd.sh → Java → ObdTestFrame (Swing GUI) → ElmProt → Serial Port → OBD Adapter

Android Mode:
MainActivity → ElmProt → BluetoothCommService → OBD Adapter
```

Both modes share the same protocol implementation (`ElmProt`, `ObdProt`) ensuring consistency between desktop testing and Android deployment.

## Excluded from Android

The build.gradle excludes these desktop-only packages from Android:
- `com/obddroid/pvs/gui` - Desktop GUI components
- `com/obddroid/prot/gui` - Protocol GUI handlers
- `com/obddroid/ecu/gui` - ECU desktop interface
- `com/obddroid/ecu/prot/vag` - VAG-specific desktop tools

## Use Cases

1. **Protocol Development** - Test new PIDs without deploying to Android
2. **Debugging** - Direct serial port access for troubleshooting
3. **Simulation** - Test UI and logic without OBD hardware
4. **Cross-platform Testing** - Verify protocol works on different OS