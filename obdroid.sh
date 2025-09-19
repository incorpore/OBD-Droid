#!/bin/bash

# OBDroid Build and Deploy Script
# Author: Wal33D
# Enhanced with automatic device discovery and manual configuration

set -e

# Configuration file for persistent settings
CONFIG_FILE="$HOME/.obdroid_config"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}=== OBDroid Build and Deploy Script ===${NC}"

# Load saved configuration if it exists
if [ -f "$CONFIG_FILE" ]; then
    source "$CONFIG_FILE"
fi

# Function to quickly check if host has Android
quick_android_check() {
    local ip=$1
    # Very fast check - just ping for now
    if ping -c 1 -W 0.1 $ip >/dev/null 2>&1; then
        return 0  # Host is alive, worth scanning
    fi
    return 1  # Host not responding
}

# Function to scan a single IP for Android debugging
scan_ip() {
    local ip=$1
    local found_file=$2

    # First do quick alive check
    if ! quick_android_check $ip; then
        return
    fi

    # Common ADB ports - try these first
    for port in 5555 5037 5554 5556; do
        # Try to connect directly with adb (faster than nc)
        result=$(timeout 0.5 adb connect "${ip}:${port}" 2>&1)
        if echo "$result" | grep -q "connected to"; then
            echo "$ip:$port" > "$found_file"
            return
        fi
        adb disconnect "${ip}:${port}" >/dev/null 2>&1
    done

    # Use nmap for efficient port scanning if available
    if command -v nmap >/dev/null 2>&1; then
        # Scan the most common wireless debugging range (37000-44000)
        open_port=$(nmap -sT "$ip" -p37000-44000 --open 2>/dev/null | awk -F/ '/tcp open/{print $1}' | head -1)
        if [ ! -z "$open_port" ]; then
            result=$(timeout 0.5 adb connect "${ip}:${open_port}" 2>&1)
            if echo "$result" | grep -q "connected to"; then
                echo "$ip:$open_port" > "$found_file"
                return
            fi
        fi
    else
        # Fallback: scan wireless debugging port range directly with adb
        # Focus on 37000-44000 range which covers most cases
        for port in $(seq 37000 44000); do
            # Quick connection test
            result=$(timeout 0.05 adb connect "${ip}:${port}" 2>&1)
            if echo "$result" | grep -q "connected to"; then
                echo "$ip:$port" > "$found_file"
                return
            fi
        done
    fi
}

# Function to save device configuration
save_device_config() {
    local device=$1
    echo "SAVED_DEVICE='$device'" > "$CONFIG_FILE"
    echo -e "${GREEN}Device configuration saved for future use${NC}"
}

# Function to find Android device on network
find_device() {
    # Check if we have a saved device to try first
    if [ ! -z "${SAVED_DEVICE:-}" ]; then
        echo -e "${YELLOW}Checking saved device: $SAVED_DEVICE${NC}"
        if adb connect "$SAVED_DEVICE" 2>/dev/null | grep -q "connected"; then
            echo -e "${GREEN}Connected to saved device: $SAVED_DEVICE${NC}"
            return 0
        fi
    fi

    echo -e "${YELLOW}Searching for Android devices on network...${NC}"

    # Use adb's built-in mDNS discovery (most reliable for Android 11+)
    echo -e "${YELLOW}Checking for wireless debugging via mDNS...${NC}"
    MDNS_DEVICES=$(adb mdns services 2>/dev/null | grep "_adb-tls-connect._tcp" | awk '{print $NF}')
    if [ ! -z "$MDNS_DEVICES" ]; then
        for device in $MDNS_DEVICES; do
            echo -e "${YELLOW}Found device broadcasting at: $device${NC}"
            if adb connect "$device" 2>/dev/null | grep -q "connected"; then
                echo -e "${GREEN}✓ Connected via mDNS to $device${NC}"
                save_device_config "$device"
                return 0
            fi
        done
    fi

    # First, check if device is already connected
    CONNECTED_DEVICES=$(adb devices | grep -E "device$" | awk '{print $1}')
    if [ ! -z "$CONNECTED_DEVICES" ]; then
        echo -e "${GREEN}Found connected device(s):${NC}"
        echo "$CONNECTED_DEVICES"
        # Save first device for future use
        FIRST_DEVICE=$(echo "$CONNECTED_DEVICES" | head -1)
        save_device_config "$FIRST_DEVICE"
        return 0
    fi

    # Get current network - more robust detection
    CURRENT_NETWORK=""
    LOCAL_IP=""

    # Try multiple methods to get network info
    # Method 1: Check active interfaces
    for interface in en0 en1 en2 wlan0 eth0 wifi0; do
        IP=$(ifconfig $interface 2>/dev/null | grep "inet " | grep -v 127.0.0.1 | awk '{print $2}')
        if [ ! -z "$IP" ]; then
            LOCAL_IP=$IP
            CURRENT_NETWORK=$(echo $IP | cut -d. -f1-3)
            echo -e "${GREEN}Found network $CURRENT_NETWORK.0/24 on interface $interface (Your IP: $LOCAL_IP)${NC}"
            break
        fi
    done

    # Method 2: Get default route network
    if [ -z "$CURRENT_NETWORK" ]; then
        DEFAULT_ROUTE=$(route -n get default 2>/dev/null | grep interface | awk '{print $2}')
        if [ ! -z "$DEFAULT_ROUTE" ]; then
            IP=$(ifconfig $DEFAULT_ROUTE 2>/dev/null | grep "inet " | awk '{print $2}')
            if [ ! -z "$IP" ]; then
                LOCAL_IP=$IP
                CURRENT_NETWORK=$(echo $IP | cut -d. -f1-3)
                echo -e "${GREEN}Found network $CURRENT_NETWORK.0/24 via default route (Your IP: $LOCAL_IP)${NC}"
            fi
        fi
    fi

    if [ -z "$CURRENT_NETWORK" ]; then
        echo -e "${RED}Could not determine network. Please connect manually.${NC}"
        return 1
    fi

    # Check known devices first for speed
    KNOWN_DEVICES_FILE="$HOME/.obdroid_devices"
    if [ -f "$KNOWN_DEVICES_FILE" ]; then
        echo -e "${YELLOW}Checking previously connected devices...${NC}"
        while read device; do
            if timeout 0.2 nc -zv $device 2>/dev/null; then
                if adb connect $device 2>/dev/null | grep -q "connected"; then
                    echo -e "${GREEN}Reconnected to known device: $device${NC}"
                    return 0
                fi
            fi
        done < "$KNOWN_DEVICES_FILE"
    fi

    # PARALLEL ASYNC NETWORK SCANNER
    echo -e "${YELLOW}Starting parallel network scan of $CURRENT_NETWORK.0/24${NC}"
    echo -e "${YELLOW}Scanning 254 IPs with smart port detection...${NC}"

    # Create temp file for found device
    FOUND_FILE="/tmp/obdroid_found_$$"
    rm -f "$FOUND_FILE"

    # Track progress
    TOTAL_IPS=254
    SCAN_START=$(date +%s)

    # First, do a quick scan for known Android device hostnames
    echo -e "${YELLOW}Quick scan for known Android devices...${NC}"

    # Check common Android hostnames
    for hostname in "waleed-judah-s-s22-ultra.home.local" "android.local" "*.home.local"; do
        DEVICE_IP=$(getent hosts "$hostname" 2>/dev/null | awk '{print $1}' | head -1)
        if [ ! -z "$DEVICE_IP" ]; then
            echo -e "${GREEN}Found device at $DEVICE_IP, scanning for wireless debugging port...${NC}"

            # Use nmap for fast port discovery if available
            if command -v nmap >/dev/null 2>&1; then
                echo -e "${YELLOW}Using nmap for fast port scanning (37000-44000)...${NC}"
                OPEN_PORT=$(nmap -sT "$DEVICE_IP" -p37000-44000 --open 2>/dev/null | awk -F/ '/tcp open/{print $1}' | head -1)
                if [ ! -z "$OPEN_PORT" ]; then
                    if adb connect "${DEVICE_IP}:${OPEN_PORT}" 2>/dev/null | grep -q "connected"; then
                        echo -e "${GREEN}✓ Connected to device at ${DEVICE_IP}:${OPEN_PORT}${NC}"
                        save_device_config "${DEVICE_IP}:${OPEN_PORT}"
                        return 0
                    fi
                fi
            else
                # Fallback to scan_ip function
                scan_ip "$DEVICE_IP" "$FOUND_FILE"
                if [ -f "$FOUND_FILE" ]; then
                    DEVICE=$(cat "$FOUND_FILE")
                    rm -f "$FOUND_FILE"
                    echo -e "${GREEN}✓ Found Android device at $DEVICE${NC}"
                    save_device_config "$DEVICE"
                    return 0
                fi
            fi
        fi
    done

    # Launch parallel scans for entire subnet
    for i in {1..254}; do
        IP="$CURRENT_NETWORK.$i"

        # Skip our own IP
        if [ "$IP" == "$LOCAL_IP" ]; then
            continue
        fi

        # Run scan in background
        (scan_ip "$IP" "$FOUND_FILE") &

        # Limit concurrent jobs to prevent overwhelming the network
        while [ $(jobs -r | wc -l) -ge 30 ]; do
            # Check if we found a device
            if [ -f "$FOUND_FILE" ]; then
                # Kill remaining background jobs
                jobs -p | xargs -r kill 2>/dev/null
                wait 2>/dev/null

                # Read the found device
                DEVICE=$(cat "$FOUND_FILE")
                rm -f "$FOUND_FILE"

                echo -e "${GREEN}✓ Found Android device at $DEVICE${NC}"

                # Save to known devices
                echo "$DEVICE" >> "$KNOWN_DEVICES_FILE"
                sort -u "$KNOWN_DEVICES_FILE" -o "$KNOWN_DEVICES_FILE" 2>/dev/null

                # Save as default device
                save_device_config "$DEVICE"

                return 0
            fi

            # Show progress
            COMPLETED=$((254 - $(jobs -r | wc -l)))
            PERCENT=$((COMPLETED * 100 / TOTAL_IPS))
            echo -ne "\r${YELLOW}Progress: [$COMPLETED/$TOTAL_IPS] ${PERCENT}%${NC}"

            sleep 0.1
        done
    done

    # Wait for remaining jobs
    echo -e "\n${YELLOW}Finishing scan...${NC}"
    wait

    # Final check if device was found
    if [ -f "$FOUND_FILE" ]; then
        DEVICE=$(cat "$FOUND_FILE")
        rm -f "$FOUND_FILE"

        echo -e "${GREEN}✓ Found Android device at $DEVICE${NC}"

        # Save to known devices
        echo "$DEVICE" >> "$KNOWN_DEVICES_FILE"
        sort -u "$KNOWN_DEVICES_FILE" -o "$KNOWN_DEVICES_FILE" 2>/dev/null

        # Save as default device
        save_device_config "$DEVICE"

        return 0
    fi

    # Calculate scan time
    SCAN_END=$(date +%s)
    SCAN_TIME=$((SCAN_END - SCAN_START))
    echo -e "${YELLOW}Scan completed in ${SCAN_TIME} seconds${NC}"

    # If we still haven't found anything, try alternative networks
    if [ "$CURRENT_NETWORK" != "192.168.0" ] && [ "$CURRENT_NETWORK" != "192.168.1" ]; then
        for alt_network in "192.168.0" "192.168.1"; do
            if [ "$alt_network" != "$CURRENT_NETWORK" ]; then
                echo -e "${YELLOW}Trying alternative network $alt_network.0/24...${NC}"

                # Quick scan of common IPs on alternative network
                for suffix in 125 100 101 102 103 104 105 150 200; do
                    IP="$alt_network.$suffix"

                    # Try common ports
                    for port in 5555 {35000..45000..1000}; do
                        if timeout 0.1 nc -zv $IP $port 2>/dev/null; then
                            if adb connect $IP:$port 2>/dev/null | grep -q "connected"; then
                                echo -e "${GREEN}✓ Found device on alternative network: $IP:$port${NC}"
                                echo "$IP:$port" >> "$KNOWN_DEVICES_FILE"
                                sort -u "$KNOWN_DEVICES_FILE" -o "$KNOWN_DEVICES_FILE" 2>/dev/null
                                save_device_config "$IP:$port"
                                return 0
                            fi
                        fi
                    done
                done
            fi
        done
    fi

    echo -e "${YELLOW}Scan complete. Device not found automatically.${NC}"
    echo ""
    echo "${YELLOW}Manual connection options:${NC}"
    echo "  1. ${GREEN}Wireless Debugging${NC} (Android 11+):"
    echo "     - Go to Settings → Developer Options → Wireless Debugging"
    echo "     - Enable it and note the IP address & port shown"
    echo "     - Run: ${GREEN}./deploy.sh set-device IP:PORT${NC}"
    echo ""
    echo "  2. ${GREEN}USB → WiFi${NC} (Any Android version):"
    echo "     - Connect device via USB"
    echo "     - Run: ${GREEN}adb tcpip 5555${NC}"
    echo "     - Disconnect USB"
    echo "     - Run: ${GREEN}./deploy.sh set-device IP:5555${NC}"
    echo ""
    echo "  3. ${GREEN}Direct connection${NC}:"
    echo "     - Run: ${GREEN}./deploy.sh connect IP:PORT${NC}"

    return 1
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
        # Ask if user wants to save this device
        read -p "Save this device for future use? (y/n): " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            save_device_config "$device_addr"
        fi
        return 0
    else
        echo -e "${RED}Connection failed!${NC}"
        return 1
    fi
}

# Function to set and save device
set_device() {
    local device_addr=$1

    if [ -z "$device_addr" ]; then
        echo -e "${YELLOW}Please provide device address (IP:PORT)${NC}"
        echo "Example: ./deploy.sh set-device 192.168.0.125:5555"
        return 1
    fi

    echo -e "${YELLOW}Testing connection to $device_addr...${NC}"
    if adb connect "$device_addr" 2>/dev/null | grep -q "connected"; then
        echo -e "${GREEN}Connected successfully!${NC}"
        save_device_config "$device_addr"
        return 0
    else
        echo -e "${RED}Could not connect to $device_addr${NC}"
        echo "Make sure:"
        echo "  - Device is on the same network"
        echo "  - Wireless debugging is enabled"
        echo "  - IP and port are correct"
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
    "set-device")
        set_device "$2"
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
        FIND_RESULT=$?

        # Check if any device is connected
        DEVICE_COUNT=$(adb devices | grep -c "device$" || true)
        if [ -z "$DEVICE_COUNT" ] || [ "$DEVICE_COUNT" -eq "0" ]; then
            echo -e "${RED}No devices connected!${NC}"
            echo ""
            echo "Quick setup:"
            echo "  1. Check your Android device's Wireless Debugging settings"
            echo "  2. Note the IP address and port shown"
            echo "  3. Run: ${GREEN}./deploy.sh set-device IP:PORT${NC}"
            echo ""
            echo "Once configured, just run ${GREEN}./deploy.sh${NC} to build and deploy!"
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
        echo "  set-device IP:PORT - Set and save default device"
        echo "  find             - Find Android devices on network"
        echo "  clean            - Clean build"
        echo "  help             - Show this help message"
        echo ""
        echo "Examples:"
        echo "  ./deploy.sh                          # Full build and deploy"
        echo "  ./deploy.sh clean                    # Clean build and deploy"
        echo "  ./deploy.sh set-device 192.168.0.125:37555  # Save device for auto-connect"
        echo "  ./deploy.sh connect 192.168.0.125:5555      # One-time connection"
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