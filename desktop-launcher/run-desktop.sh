#!/bin/bash
# Desktop launcher script for OBD-Droid

echo "Starting OBD-Droid Desktop Application..."

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "Error: Java is not installed. Please install Java 8 or higher."
    exit 1
fi

# Get the directory where this script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Find the desktop JAR file
JAR_FILE="$SCRIPT_DIR/../library/build/libs/library-desktop-all.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "Error: Desktop JAR not found at $JAR_FILE"
    echo "Please build the desktop JAR first using: ./gradlew desktopJar"
    exit 1
fi

# Run the desktop application
# Pass serial port as first argument if provided
if [ $# -eq 0 ]; then
    echo "Usage: $0 <serial-port>"
    echo "Example: $0 /dev/ttyUSB0"
    echo "Starting in simulation mode..."
    java -jar "$JAR_FILE"
else
    echo "Using serial port: $1"
    java -jar "$JAR_FILE" "$@"
fi