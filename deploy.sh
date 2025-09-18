#!/bin/bash

# OBDroid Build and Deploy Script
# Author: Wal33D
# Enhanced with automatic device discovery

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}=== OBDroid Build and Deploy Script ===${NC}"

# Function to find Android device on network
find_device() {
    echo -e "${YELLOW}Searching for Android devices on network...${NC}"

    # First, check if device is already connected via USB or WiFi
    CONNECTED_DEVICES=$(adb devices | grep -E "device$" | awk '{print $1}')

    if [ ! -z "$CONNECTED_DEVICES" ]; then
        echo -e "${GREEN}Found connected device(s):${NC}"
        echo "$CONNECTED_DEVICES"

        # If device is connected via USB, get its IP for potential WiFi connection
        for device in $CONNECTED_DEVICES; do
            if [[ ! $device =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+:[0-9]+$ ]]; then
                # This is a USB device, try to get its IP
                IP=$(adb -s $device shell ip addr show wlan0 2>/dev/null | grep "inet " | awk '{print $2}' | cut -d/ -f1)
                if [ ! -z "$IP" ]; then
                    echo -e "${YELLOW}USB device IP address: $IP${NC}"
                    echo -e "${YELLOW}You can connect via WiFi: adb connect $IP:5555${NC}"
                fi
            fi
        done
        return 0
    fi

    # Try to find devices using mDNS/Bonjour (if available on macOS)
    if command -v dns-sd &> /dev/null; then
        echo -e "${YELLOW}Scanning for Android devices via mDNS...${NC}"
        timeout 3 dns-sd -B _adb-tls-connect._tcp 2>/dev/null || true
    fi

    # Scan common Android wireless debugging ports
    echo -e "${YELLOW}Scanning local network for Android devices...${NC}"

    # Get local network IP range
    LOCAL_IP=$(ifconfig | grep "inet " | grep -v 127.0.0.1 | head -1 | awk '{print $2}')
    NETWORK_PREFIX=$(echo $LOCAL_IP | cut -d. -f1-3)

    echo -e "${YELLOW}Scanning $NETWORK_PREFIX.0/24 network...${NC}"

    # Common ports for wireless debugging
    # Include specific known port 34853 and scan range
    PORTS=(5555 34853)
    for ((port=32000; port<=45000; port+=100)); do
        PORTS+=($port)
    done

    for port in "${PORTS[@]}"; do
        # Quick scan for open ports (using nc with timeout)
        for i in {1..254}; do
            IP="$NETWORK_PREFIX.$i"
            (timeout 0.1 nc -zv $IP $port 2>/dev/null && echo "$IP:$port") &

            # Limit concurrent connections
            if [ $(jobs -r | wc -l) -ge 50 ]; then
                wait -n
            fi
        done
    done
    wait

    echo -e "${YELLOW}Scan complete. If your device wasn't found:${NC}"
    echo "1. Enable Developer Options and Wireless Debugging on your Android device"
    echo "2. Note the IP address and port shown in Wireless Debugging settings"
    echo "3. Run: adb connect <IP>:<PORT>"
    echo "4. Or connect via USB and run: adb tcpip 5555"
}

# Function to build the app
build_app() {
    echo -e "${GREEN}Building OBDroid...${NC}"

    if [ "$1" == "clean" ]; then
        echo -e "${YELLOW}Performing clean build...${NC}"
        ./gradlew clean assembleDebug
    else
        ./gradlew assembleDebug
    fi

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}Build successful!${NC}"
        return 0
    else
        echo -e "${RED}Build failed!${NC}"
        return 1
    fi
}

# Function to deploy the app
deploy_app() {
    echo -e "${GREEN}Deploying OBDroid...${NC}"

    # Check if APK exists
    APK_PATH="./app/build/outputs/apk/debug/app-debug.apk"

    if [ ! -f "$APK_PATH" ]; then
        echo -e "${RED}APK not found! Please build first.${NC}"
        return 1
    fi

    echo -e "${YELLOW}Installing APK: $APK_PATH${NC}"
    adb install -r "$APK_PATH"

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}App installed successfully!${NC}"

        # Launch the app
        echo -e "${YELLOW}Launching OBDroid...${NC}"
        adb shell am start -n com.obddroid/com.obddroid.ui.activities.MainActivity

        if [ $? -eq 0 ]; then
            echo -e "${GREEN}App launched!${NC}"
        else
            echo -e "${YELLOW}Could not auto-launch app. Please launch manually.${NC}"
        fi
        return 0
    else
        echo -e "${RED}Installation failed!${NC}"
        return 1
    fi
}

# Function to connect to device
connect_device() {
    local device_addr=$1

    if [ -z "$device_addr" ]; then
        echo -e "${YELLOW}Please provide device address (IP:PORT)${NC}"
        echo "Example: ./deploy.sh connect 192.168.0.125:5555"
        return 1
    fi

    echo -e "${YELLOW}Connecting to $device_addr...${NC}"
    adb connect "$device_addr"

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}Connected successfully!${NC}"
        return 0
    else
        echo -e "${RED}Connection failed!${NC}"
        return 1
    fi
}

# Main script logic
case "${1:-}" in
    "build")
        build_app "${2:-}"
        ;;
    "deploy")
        deploy_app
        ;;
    "connect")
        connect_device "$2"
        ;;
    "find")
        find_device
        ;;
    "clean")
        build_app "clean"
        ;;
    "all"|"")
        # Default: find device, build, and deploy
        find_device

        # Check if any device is connected
        DEVICE_COUNT=$(adb devices | grep -c "device$" || echo "0")
        if [ "$DEVICE_COUNT" -eq "0" ]; then
            echo -e "${RED}No devices connected!${NC}"
            echo "Please connect your device using one of these methods:"
            echo "  1. USB: Connect device and ensure USB debugging is enabled"
            echo "  2. WiFi: Run './deploy.sh connect <IP>:<PORT>'"
            exit 1
        fi

        build_app "${2:-}"
        if [ $? -eq 0 ]; then
            deploy_app
        fi
        ;;
    "help"|"-h"|"--help")
        echo "Usage: ./deploy.sh [command] [options]"
        echo ""
        echo "Commands:"
        echo "  all, (empty)     - Find device, build, and deploy (default)"
        echo "  build [clean]    - Build the app (optionally with clean)"
        echo "  deploy           - Deploy the app to connected device"
        echo "  connect IP:PORT  - Connect to device via WiFi"
        echo "  find             - Find Android devices on network"
        echo "  clean            - Clean build"
        echo "  help             - Show this help message"
        echo ""
        echo "Examples:"
        echo "  ./deploy.sh                          # Full build and deploy"
        echo "  ./deploy.sh clean                    # Clean build and deploy"
        echo "  ./deploy.sh connect 192.168.0.125:5555  # Connect to specific device"
        echo "  ./deploy.sh build                    # Build only"
        echo "  ./deploy.sh deploy                   # Deploy only"
        ;;
    *)
        echo -e "${RED}Unknown command: $1${NC}"
        echo "Run './deploy.sh help' for usage information"
        exit 1
        ;;
esac

exit 0