package com.obddroid.ecu.gui.androbd;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
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
            holder.signalIndicator = convertView.findViewById(R.id.signal_indicator);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        BluetoothDevice device = getItem(position);

        // Set device name
        String deviceName = device.getName();
        if (deviceName == null || deviceName.isEmpty()) {
            deviceName = "Unknown Device";
        }
        holder.deviceName.setText(deviceName);

        // Set status text
        holder.deviceStatus.setText("Tap to connect • Hold for MAC address");

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
        ImageView signalIndicator;
    }
}