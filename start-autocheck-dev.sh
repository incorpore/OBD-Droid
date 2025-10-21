#!/bin/bash
# Auto-start AutoCheck API and setup adb reverse tunnel for development
# Usage: ./start-autocheck-dev.sh [--build]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AUTOCHECK_API_DIR="/path/to/autocheck-api"
API_PORT=3248

echo "🚀 OBD-Droid AutoCheck Development Setup"
echo "========================================"
echo ""

# Check if AutoCheck API directory exists
if [ ! -d "$AUTOCHECK_API_DIR" ]; then
    echo "❌ AutoCheck API directory not found at: $AUTOCHECK_API_DIR"
    echo "Please clone the autocheck-api repo first."
    exit 1
fi

# Check if API is already running
echo "🔍 Checking if AutoCheck API is already running..."
if curl -s http://localhost:$API_PORT/health > /dev/null 2>&1; then
    echo "✅ AutoCheck API is already running on port $API_PORT"
else
    echo "🔧 Starting AutoCheck API..."
    cd "$AUTOCHECK_API_DIR"

    # Check if node_modules exists, if not run npm install
    if [ ! -d "node_modules" ]; then
        echo "📦 Installing dependencies..."
        npm install
    fi

    # Start the API in the background
    echo "🌐 Launching API server..."
    npm run dev > /tmp/autocheck-api.log 2>&1 &
    API_PID=$!
    echo "   Process ID: $API_PID"
    echo "   Logs: /tmp/autocheck-api.log"

    # Wait for API to be ready
    echo "⏳ Waiting for API to start..."
    for i in {1..30}; do
        if curl -s http://localhost:$API_PORT/health > /dev/null 2>&1; then
            echo "✅ AutoCheck API is ready!"
            break
        fi
        if [ $i -eq 30 ]; then
            echo "❌ API failed to start within 30 seconds"
            echo "Check logs: tail -f /tmp/autocheck-api.log"
            exit 1
        fi
        sleep 1
        echo -n "."
    done
    echo ""
fi

cd "$SCRIPT_DIR"

# Setup adb reverse tunnel
echo ""
echo "🔧 Setting up adb reverse tunnel..."
if ! command -v adb &> /dev/null; then
    echo "❌ adb not found. Please install Android SDK Platform Tools."
    exit 1
fi

# Check if device is connected
if ! adb devices | grep -q "device$"; then
    echo "❌ No Android device connected via adb"
    echo "Please connect your device or start an emulator"
    exit 1
fi

# Setup reverse port forwarding
adb reverse tcp:$API_PORT tcp:$API_PORT
echo "✅ adb reverse tunnel established: device:$API_PORT -> laptop:$API_PORT"

# Build and install if --build flag is passed
if [ "$1" == "--build" ]; then
    echo ""
    echo "🔨 Building and installing app..."
    ./gradlew assembleDebug installDebug
    echo "✅ App installed!"

    echo ""
    echo "🚀 Launching app..."
    adb shell am start -n com.obddroid/.ui.activities.MainActivity
fi

echo ""
echo "✅ Development environment ready!"
echo ""
echo "📝 Quick commands:"
echo "   • View API logs: tail -f /tmp/autocheck-api.log"
echo "   • Test API health: curl http://localhost:$API_PORT/health"
echo "   • Build & install app: ./gradlew assembleDebug installDebug"
echo "   • Launch app: adb shell am start -n com.obddroid/.ui.activities.MainActivity"
echo "   • View app logs: adb logcat | grep -E '(AutoCheck|OBD)'"
echo ""
echo "💡 The Android app will now connect to localhost:$API_PORT"
echo "   No need to update IP addresses anymore!"
echo ""
