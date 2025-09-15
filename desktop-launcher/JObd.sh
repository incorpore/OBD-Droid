#!/bin/sh

# Get the directory where this script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
# Move up one directory to get project root
PROJECT_ROOT="$( cd "$SCRIPT_DIR/.." && pwd )"

# Set the JAR file path
JARFILE="$PROJECT_ROOT/library/build/libs/library.jar"

# Check if JAR exists
if [ ! -f "$JARFILE" ]; then
    echo "ERROR: Library JAR not found at $JARFILE"
    echo "Please build it first with: ./gradlew :library:jar"
    exit 1
fi

# Configure serial port if provided
if [ "$1" != "" ]; then
    echo "Configuring serial port: $1"
    # Configure serial port for ELM327 communication
    stty -F $1 38400 sane ignbrk -brkint -icrnl -imaxbel -isig -icanon -iexten -echo -echoe -echok -echoctl -echoke 2>/dev/null
    if [ $? -ne 0 ]; then
        echo "WARNING: Could not configure serial port $1"
        echo "Make sure the port exists and you have permission to access it"
    fi
else
    echo "Starting in DEMO mode (no serial port specified)"
    echo "Usage: $0 [serial_port]"
    echo "Example: $0 /dev/ttyUSB0"
fi

# Start the desktop OBD application
echo "Starting OBD Desktop Application..."
echo "Using JAR: $JARFILE"

# Run with logging configuration
java -Djava.util.logging.config.file="$SCRIPT_DIR/logging.properties" \
     -cp "$JARFILE" \
     com.obddroid.ecu.gui.application.ObdTestFrame $*