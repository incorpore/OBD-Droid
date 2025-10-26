package com.obddroid.ui.adapters;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDevice;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.preference.PreferenceManager;

import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.obddroid.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Unified adapter for both Bluetooth and USB device lists
 *
 * Handles display of both Bluetooth OBD adapters and USB serial devices
 * with improved UI and device type detection.
 */
public class DeviceAdapter extends BaseAdapter {

    /**
     * Wrapper class to hold either Bluetooth or USB device information
     */
    public static class DeviceInfo {
        // Bluetooth fields
        public BluetoothDevice bluetoothDevice;

        // USB fields
        public UsbDevice usbDevice;
        public UsbSerialPort usbPort;
        public boolean isUsbCompatible;

        // Bluetooth constructor
        public DeviceInfo(BluetoothDevice bluetoothDevice) {
            this.bluetoothDevice = bluetoothDevice;
        }

        // USB constructor
        public DeviceInfo(UsbDevice usbDevice, UsbSerialPort port, boolean isCompatible) {
            this.usbDevice = usbDevice;
            this.usbPort = port;
            this.isUsbCompatible = isCompatible;
        }

        public boolean isBluetooth() {
            return bluetoothDevice != null;
        }

        public boolean isUsb() {
            return usbDevice != null;
        }
    }

    private final Context context;
    private final List<DeviceInfo> devices;
    private final LayoutInflater inflater;
    private String lastUsedDeviceAddress = null;

    public DeviceAdapter(Context context) {
        this.context = context;
        this.devices = new ArrayList<>();
        this.inflater = LayoutInflater.from(context);
    }

    public DeviceAdapter(Context context, List<DeviceInfo> devices) {
        this.context = context;
        this.devices = devices != null ? devices : new ArrayList<>();
        this.inflater = LayoutInflater.from(context);
    }

    public void addDevice(BluetoothDevice device) {
        DeviceInfo info = new DeviceInfo(device);
        if (!containsBluetoothDevice(device)) {
            devices.add(info);
            notifyDataSetChanged();
        }
    }

    public void addDevice(UsbDevice device, UsbSerialPort port, boolean isCompatible) {
        DeviceInfo info = new DeviceInfo(device, port, isCompatible);
        devices.add(info);
        notifyDataSetChanged();
    }

    public void setLastUsedDeviceAddress(String address) {
        this.lastUsedDeviceAddress = address;
        notifyDataSetChanged();
    }

    public void clear() {
        devices.clear();
        notifyDataSetChanged();
    }

    private boolean containsBluetoothDevice(BluetoothDevice device) {
        for (DeviceInfo info : devices) {
            if (info.isBluetooth() && info.bluetoothDevice.equals(device)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getCount() {
        return devices.size();
    }

    @Override
    public DeviceInfo getItem(int position) {
        return devices.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.device_item_modern, parent, false);
            holder = new ViewHolder();
            holder.deviceName = convertView.findViewById(R.id.device_name);
            holder.deviceStatus = convertView.findViewById(R.id.device_status);
            holder.deviceIcon = convertView.findViewById(R.id.device_icon);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        DeviceInfo info = getItem(position);

        if (info.isBluetooth()) {
            setupBluetoothView(holder, info.bluetoothDevice, position);
        } else if (info.isUsb()) {
            setupUsbView(holder, info);
        }

        return convertView;
    }

    private void setupBluetoothView(ViewHolder holder, BluetoothDevice device, int position) {
        // Get device name and nickname
        String deviceName = device.getName();
        if (deviceName == null || deviceName.isEmpty()) {
            deviceName = "Unknown Device";
        }

        // Check for saved nickname
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String nickname = prefs.getString("device_nickname_" + device.getAddress(), "");
        String displayName = !nickname.isEmpty() ? nickname : deviceName;

        // Check if this is the last used device
        boolean isLastUsed = (lastUsedDeviceAddress != null &&
                            device.getAddress().equals(lastUsedDeviceAddress));

        // Add indicator for last used device
        if (isLastUsed && position == 0) {
            holder.deviceName.setText(displayName + " ★");
        } else {
            holder.deviceName.setText(displayName);
        }

        // Set status text
        if (isLastUsed && position == 0) {
            // Recently used device - always show this first
            holder.deviceStatus.setText("Recently used • Tap to reconnect");
        } else if (!nickname.isEmpty()) {
            // Show original name when nickname is set
            holder.deviceStatus.setText(deviceName + " • Tap to connect");
        } else {
            holder.deviceStatus.setText("Tap to connect");
        }

        // Set icon based on device type
        if (isOBDAdapter(displayName) || isOBDAdapter(deviceName)) {
            // OBD adapter icon
            holder.deviceIcon.setImageResource(R.drawable.ic_obd_adapter);
        } else {
            // Default Bluetooth icon
            holder.deviceIcon.setImageResource(R.drawable.ic_bluetooth_device);
        }
    }

    private void setupUsbView(ViewHolder holder, DeviceInfo info) {
        UsbDevice device = info.usbDevice;

        String deviceName = String.format("0x%04x/0x%04x",
                device.getVendorId(),
                device.getProductId());

        String vendorName = getUsbVendorName(device.getVendorId());
        if (!vendorName.equals("Unknown Vendor")) {
            deviceName = vendorName;
        }

        holder.deviceName.setText(deviceName);

        if (info.isUsbCompatible && info.usbPort != null) {
            String driverName = info.usbPort.getDriver().getClass().getSimpleName().replace("SerialDriver", "");
            holder.deviceStatus.setText("Compatible OBD adapter • " + driverName);
            holder.deviceIcon.setImageResource(android.R.drawable.ic_menu_directions);
        } else {
            holder.deviceStatus.setText("Not a compatible serial adapter");
            holder.deviceIcon.setImageResource(android.R.drawable.ic_menu_manage);
        }
    }

    private static class ViewHolder {
        TextView deviceName;
        TextView deviceStatus;
        ImageView deviceIcon;
    }

    /**
     * Check if a device name indicates it's an OBD adapter
     * @param deviceName The name to check
     * @return true if the name matches OBD adapter patterns
     */
    private boolean isOBDAdapter(String deviceName) {
        if (deviceName == null || deviceName.isEmpty()) {
            return false;
        }

        String nameLower = deviceName.toLowerCase();

        // Check for common OBD adapter name patterns
        return nameLower.contains("obd") ||
               nameLower.contains("elm327") ||
               nameLower.contains("elm") ||
               nameLower.contains("vgate") ||
               nameLower.contains("veepeak") ||
               nameLower.contains("topway") ||
               nameLower.contains("charcoal") ||
               nameLower.contains("obdii") ||
               nameLower.contains("obd2") ||
               nameLower.contains("obd-ii") ||
               nameLower.contains("car scanner") ||
               nameLower.contains("carista") ||
               nameLower.contains("konnwei") ||
               nameLower.contains("foxwell") ||
               nameLower.contains("bluedriver") ||
               nameLower.contains("bafx") ||
               nameLower.contains("panlong") ||
               nameLower.contains("ancel") ||
               nameLower.contains("autel") ||
               nameLower.contains("launch") ||
               nameLower.contains("thinkcar") ||
               nameLower.contains("thinkdiag") ||
               nameLower.contains("innova") ||
               nameLower.contains("actron");
    }

    /**
     * Get human-readable vendor name for USB vendor ID
     * @param vendorId USB vendor ID
     * @return Vendor name or "Unknown Vendor"
     */
    private String getUsbVendorName(int vendorId) {
        switch (vendorId) {
            case 0x04e8: return "Samsung";
            case 0x046d: return "Logitech";
            case 0x0b95: return "ASIX Electronics";
            case 0x1b1c: return "Corsair";
            case 0x0403: return "FTDI";
            case 0x1a86: return "QinHeng (CH340)";
            case 0x10c4: return "Silicon Labs (CP210x)";
            case 0x067b: return "Prolific (PL2303)";
            case 0x2341: return "Arduino";
            case 0x0557: return "ATEN";
            default: return "Unknown Vendor";
        }
    }
}
