package com.obddroid.ecu.gui.androbd;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Modern adapter for Bluetooth device list with improved UI
 */
public class ModernDeviceAdapter extends BaseAdapter {

    private final Context context;
    private final List<BluetoothDevice> devices;
    private final LayoutInflater inflater;
    private String lastUsedDeviceAddress = null;

    public ModernDeviceAdapter(Context context) {
        this.context = context;
        this.devices = new ArrayList<>();
        this.inflater = LayoutInflater.from(context);
    }

    public void addDevice(BluetoothDevice device) {
        if (!devices.contains(device)) {
            devices.add(device);
            notifyDataSetChanged();
        }
    }

    public void setLastUsedDeviceAddress(String address) {
        this.lastUsedDeviceAddress = address;
        notifyDataSetChanged();
    }

    public void clear() {
        devices.clear();
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return devices.size();
    }

    @Override
    public BluetoothDevice getItem(int position) {
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

        BluetoothDevice device = getItem(position);

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

        // Set icon based on device type (you can customize this)
        if (deviceName.toLowerCase().contains("obd") ||
            deviceName.toLowerCase().contains("elm") ||
            deviceName.toLowerCase().contains("vgate")) {
            // OBD adapter icon (use car icon if available)
            holder.deviceIcon.setImageResource(android.R.drawable.ic_menu_directions);
        } else {
            // Default Bluetooth icon
            holder.deviceIcon.setImageResource(android.R.drawable.stat_sys_data_bluetooth);
        }

        return convertView;
    }

    private static class ViewHolder {
        TextView deviceName;
        TextView deviceStatus;
        ImageView deviceIcon;
    }
}