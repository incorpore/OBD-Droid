package com.obddroid.features.sensors.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import com.obddroid.R;
import com.obddroid.core.ecu.Conversion;
import com.obddroid.core.ecu.EcuDataItem;
import com.obddroid.core.ecu.EcuDataItems;
import com.obddroid.core.ecu.EcuDataPv;
import com.obddroid.core.ecu.LinearConversion;
import com.obddroid.core.obd.ObdProt;
import com.obddroid.ui.activities.SettingsActivity;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Streams device motion sensor data (accelerometer) into the PID list so it can be graphed,
 * logged, and exported alongside standard OBD telemetry.
 */
public class SensorTelemetryManager implements SensorEventListener {

    private static final String TAG = "SensorTelemetry";
    private static final int SERVICE = ObdProt.OBD_SVC_DATA;
    private static final int CUSTOM_PID_BASE = 0xF120;

    private final Context appContext;
    private final SensorManager sensorManager;
    private final Map<DataField, EcuDataItem> items = new EnumMap<>(DataField.class);
    private final Map<DataField, EcuDataPv> dataPvs = new EnumMap<>(DataField.class);
    private final Set<String> registeredKeys = new HashSet<>();

    private Sensor accelerometer;
    private boolean active = false;

    public SensorTelemetryManager(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.sensorManager = (SensorManager) appContext.getSystemService(Context.SENSOR_SERVICE);
    }

    public boolean isActive() {
        return active;
    }

    @MainThread
    public void start() {
        if (active) {
            return;
        }
        if (sensorManager == null) {
            Log.w(TAG, "SensorManager unavailable");
            return;
        }
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer == null) {
            Log.w(TAG, "Accelerometer sensor not available on this device");
            return;
        }

        registerDataItems();
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        active = true;
    }

    @MainThread
    public void stop() {
        if (!active || sensorManager == null) {
            return;
        }
        sensorManager.unregisterListener(this);
        active = false;
    }

    private void registerDataItems() {
        for (DataField field : DataField.values()) {
            if (items.containsKey(field)) {
                continue;
            }

            int pid = CUSTOM_PID_BASE + field.ordinal();
            Conversion[] conversions = createIdentityConversions(field.units);
            String description = appContext.getString(field.labelResId);

            EcuDataItem item = new EcuDataItem(
                pid,
                0,
                0,
                0,
                32,
                0xFFFFFFFFL,
                conversions,
                field.formatPattern,
                field.min,
                field.max,
                0,
                description,
                field.mnemonic
            );
            item.pv.put(EcuDataPv.FID_VALUE, Double.valueOf(0d));
            item.pv.put(EcuDataPv.FID_UNITS, field.units);
            item.pv.put(EcuDataPv.FID_FORMAT, field.formatPattern);

            EcuDataItems.byMnemonic.put(field.mnemonic, item);
            ObdProt.dataItems.appendItemToService(SERVICE, item);
            String key = item.toString();
            registeredKeys.add(key);
            ObdProt.PidPvs.putTyped(key, item.pv);

            items.put(field, item);
            dataPvs.put(field, item.pv);
        }
        ensureLiveDataPreferences();
    }

    private static Conversion[] createIdentityConversions(String units) {
        Conversion metric = new LinearConversion(1, 1, 0, 0, units);
        Conversion imperial = new LinearConversion(1, 1, 0, 0, units);
        return new Conversion[]{metric, imperial};
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!active || event.sensor == null || event.values == null || event.values.length < 3) {
            return;
        }
        updateField(DataField.ACC_X, event.values[0]);
        updateField(DataField.ACC_Y, event.values[1]);
        updateField(DataField.ACC_Z, event.values[2]);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // No-op
    }

    private void updateField(DataField field, float value) {
        EcuDataPv pv = dataPvs.get(field);
        if (pv == null) {
            return;
        }
        double clipped = clamp(value, field.min, field.max);
        pv.put(EcuDataPv.FID_VALUE, Double.valueOf(clipped));
        pv.put(EcuDataPv.FID_UNITS, field.units);
        pv.put(EcuDataPv.FID_DESCRIPT, appContext.getString(field.labelResId));
    }

    private double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private void ensureLiveDataPreferences() {
        if (registeredKeys.isEmpty()) {
            return;
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(appContext);
        Set<String> current = prefs.getStringSet(SettingsActivity.KEY_DATA_ITEMS, null);

        // If preference is null or empty, create a new set with sensor items
        if (current == null || current.isEmpty()) {
            Set<String> updated = new HashSet<>(registeredKeys);
            prefs.edit().putStringSet(SettingsActivity.KEY_DATA_ITEMS, updated).apply();
            return;
        }

        // If sensor items are already in preferences, no need to update
        if (current.containsAll(registeredKeys)) {
            return;
        }

        // Add sensor items to existing preferences
        Set<String> updated = new HashSet<>(current);
        updated.addAll(registeredKeys);
        prefs.edit().putStringSet(SettingsActivity.KEY_DATA_ITEMS, updated).apply();
    }

    private enum DataField {
        ACC_X("ACC_X", R.string.sensor_field_acc_x, "m/s²", -9.81d, 9.81d, "%.2f"),
        ACC_Y("ACC_Y", R.string.sensor_field_acc_y, "m/s²", -9.81d, 9.81d, "%.2f"),
        ACC_Z("ACC_Z", R.string.sensor_field_acc_z, "m/s²", -9.81d, 9.81d, "%.2f");

        final String mnemonic;
        final int labelResId;
        final String units;
        final double min;
        final double max;
        final String formatPattern;

        DataField(String mnemonic, int labelResId, String units, double min, double max, String formatPattern) {
            this.mnemonic = mnemonic;
            this.labelResId = labelResId;
            this.units = units;
            this.min = min;
            this.max = max;
            this.formatPattern = formatPattern;
        }
    }
}
