package com.obddroid.ui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.obddroid.R;
import android.hardware.usb.UsbDevice;

import java.util.List;

public class ModernUsbDeviceAdapter extends BaseAdapter {

    public static class UsbDeviceInfo {
        public UsbDevice device;
        public UsbSerialPort port;
        public boolean isCompatible;

        public UsbDeviceInfo(UsbDevice device, UsbSerialPort port, boolean isCompatible) {
            this.device = device;
            this.port = port;
            this.isCompatible = isCompatible;
        }
    }

    private final Context context;
    private final List<UsbDeviceInfo> devices;
    private final LayoutInflater inflater;

    public ModernUsbDeviceAdapter(Context context, List<UsbDeviceInfo> devices) {
        this.context = context;
        this.devices = devices;
        this.inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return devices.size();
    }

    @Override
    public UsbDeviceInfo getItem(int position) {
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

        UsbDeviceInfo info = getItem(position);
        UsbDevice device = info.device;

        String deviceName = String.format("0x%04x/0x%04x",
                device.getVendorId(),
                device.getProductId());

        String vendorName = getUsbVendorName(device.getVendorId());
        if (!vendorName.equals("Unknown Vendor")) {
            deviceName = vendorName;
        }

        holder.deviceName.setText(deviceName);

        if (info.isCompatible && info.port != null) {
            String driverName = info.port.getDriver().getClass().getSimpleName().replace("SerialDriver", "");
            holder.deviceStatus.setText("Compatible OBD adapter • " + driverName);
            holder.deviceIcon.setImageResource(android.R.drawable.ic_menu_directions);
        } else {
            holder.deviceStatus.setText("Not a compatible serial adapter");
            holder.deviceIcon.setImageResource(android.R.drawable.ic_menu_manage);
        }

        return convertView;
    }

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

    private static class ViewHolder {
        TextView deviceName;
        TextView deviceStatus;
        ImageView deviceIcon;
    }
}
