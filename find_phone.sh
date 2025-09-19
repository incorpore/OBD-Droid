#!/bin/bash

# Quick script to find the S22 Ultra's wireless debugging port

echo "Looking for S22 Ultra at 192.168.1.110..."

# Method 1: Try adb connect on a range of ports
echo "Scanning wireless debugging ports..."

for port in $(seq 30000 50000); do
    # Show progress every 100 ports
    if [ $((port % 100)) -eq 0 ]; then
        echo -ne "\rScanning port $port..."
    fi

    # Try to connect
    result=$(timeout 0.1 adb connect "192.168.1.110:${port}" 2>&1)

    if echo "$result" | grep -q "connected to"; then
        echo -e "\n\nFOUND IT! Device is at 192.168.1.110:${port}"
        echo "Saving configuration..."
        echo "SAVED_DEVICE='192.168.1.110:${port}'" > "$HOME/.obdroid_config"
        exit 0
    fi

    # Disconnect failed attempts
    adb disconnect "192.168.1.110:${port}" >/dev/null 2>&1
done

echo -e "\nCouldn't find the wireless debugging port."
echo "Please check:"
echo "1. Wireless Debugging is enabled on your S22 Ultra"
echo "2. The phone shows IP: 192.168.1.110"
echo "3. Note the port number shown and run: ./deploy.sh set-device 192.168.1.110:PORT"