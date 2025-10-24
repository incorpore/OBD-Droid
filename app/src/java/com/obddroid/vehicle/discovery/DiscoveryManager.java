package com.obddroid.vehicle.discovery;

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import com.obddroid.core.obd.ElmProt;
import com.obddroid.services.CommService;
import com.obddroid.vehicle.EcuInfo;
import com.obddroid.vehicle.EcuManager;
import com.obddroid.vehicle.VehicleManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Coordinates ECU discovery logging so testers can gather consistent datasets across vehicles.
 */
public final class DiscoveryManager implements EcuManager.EcuManagerListener,
        VehicleManager.VehicleChangeListener {

    private static final String TAG = "DiscoveryPipeline";

    private static DiscoveryManager instance;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "discovery-log-writer");
        t.setDaemon(true);
        return t;
    });

    private final Set<Integer> discoveredAddresses = Collections.synchronizedSet(new HashSet<>());
    private final ConcurrentHashMap<Integer, JSONObject> ecuSnapshots = new ConcurrentHashMap<>();

    private final PropertyChangeListener elmListener = this::handleElmPropertyChange;

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean sessionActive = new AtomicBoolean(false);

    private Context appContext;
    private String sessionId;
    private DiscoveryLogWriter logWriter;
    private long sessionStartMs;
    private String adapterName;

    private DiscoveryManager() {
        // Singleton
    }

    public static synchronized DiscoveryManager getInstance() {
        if (instance == null) {
            instance = new DiscoveryManager();
        }
        return instance;
    }

    public void initialize(Context context) {
        if (initialized.get()) {
            return;
        }
        appContext = context.getApplicationContext();
        VehicleManager.getInstance(appContext).addListener(this);
        initialized.set(true);
    }

    public void startSession(@Nullable String adapterFriendlyName) {
        if (!initialized.get()) {
            throw new IllegalStateException("DiscoveryManager not initialized");
        }

        // End any prior session cleanly before starting anew
        endSession("Session restarted");

        sessionId = generateSessionId();
        sessionStartMs = System.currentTimeMillis();
        adapterName = adapterFriendlyName;
        discoveredAddresses.clear();
        ecuSnapshots.clear();

        try {
            logWriter = new DiscoveryLogWriter(appContext, sessionId);
        } catch (IOException ioException) {
            Log.e(TAG, "Unable to open discovery log file", ioException);
            sessionId = null;
            return;
        }

        sessionActive.set(true);

        recordEvent(DiscoveryEventType.SESSION_START, "Discovery session started", null, buildSessionMeta());
        recordCapabilities();

        // Listen for ECU address/property changes and logical ECU updates
        CommService.elm.addPropertyChangeListener(elmListener);
        EcuManager.getInstance().addListener(this);
    }

    public void updateAdapterName(@Nullable String name) {
        adapterName = name;
        if (!sessionActive.get() || TextUtils.isEmpty(name)) {
            return;
        }
        try {
            JSONObject data = new JSONObject();
            data.put("adapterName", name);
            recordEvent(DiscoveryEventType.CAPABILITY, "Adapter identified", null, data);
        } catch (JSONException e) {
            Log.w(TAG, "Failed to encode adapter name", e);
        }
    }

    public void recordEcuAddressSnapshot(Set<Integer> addresses) {
        if (!sessionActive.get() || addresses == null) {
            return;
        }
        for (Integer address : addresses) {
            if (address == null) {
                continue;
            }
            if (discoveredAddresses.add(address)) {
                try {
                    JSONObject data = new JSONObject();
                    data.put("source", "elm-prop");
                    recordEvent(DiscoveryEventType.PASSIVE_ADDRESS,
                        "Detected ECU address", address, data);
                } catch (JSONException e) {
                    Log.w(TAG, "Failed to encode passive address", e);
                }
            }
        }
    }

    public void endSession(@Nullable String reason) {
        if (!sessionActive.get()) {
            return;
        }

        CommService.elm.removePropertyChangeListener(elmListener);
        EcuManager.getInstance().removeListener(this);

        long elapsed = System.currentTimeMillis() - sessionStartMs;
        try {
            JSONObject summary = new JSONObject();
            summary.put("elapsedMs", elapsed);
            summary.put("adapterName", adapterName == null ? "unknown" : adapterName);
            summary.put("ecuCount", discoveredAddresses.size());

            JSONArray addressArray = new JSONArray();
            for (Integer addr : discoveredAddresses) {
                addressArray.put(String.format(Locale.US, "0x%X", addr));
            }
            summary.put("addresses", addressArray);
            recordEvent(DiscoveryEventType.SESSION_END,
                reason == null ? "Discovery session ended" : reason,
                null,
                summary);
        } catch (JSONException e) {
            Log.w(TAG, "Failed to encode discovery summary", e);
        }

        sessionActive.set(false);

        if (logWriter != null) {
            try {
                logWriter.close();
            } catch (IOException e) {
                Log.w(TAG, "Failed closing discovery log", e);
            }
        }
        logWriter = null;
        sessionId = null;
        discoveredAddresses.clear();
        ecuSnapshots.clear();
    }

    public void shutdown() {
        endSession("Discovery manager shutdown");
        VehicleManager.getInstance().removeListener(this);
        executor.shutdownNow();
        initialized.set(false);
    }

    public boolean isSessionActive() {
        return sessionActive.get();
    }

    public String getActiveSessionId() {
        return sessionId;
    }

    public String getAdapterName() {
        return adapterName;
    }

    public JSONObject getDiscoverySummary() {
        JSONObject summary = new JSONObject();
        try {
            summary.put("sessionId", sessionId);
            summary.put("adapterName", adapterName);
            summary.put("ecuCount", discoveredAddresses.size());
            JSONArray addresses = new JSONArray();
            for (Integer addr : discoveredAddresses) {
                addresses.put(String.format(Locale.US, "0x%X", addr));
            }
            summary.put("addresses", addresses);
        } catch (JSONException e) {
            Log.w(TAG, "Failed to build discovery summary", e);
        }
        return summary;
    }

    public Set<Integer> getDiscoveredAddressesSnapshot() {
        return new HashSet<>(discoveredAddresses);
    }

    public JSONObject getEcuSnapshot(int address) {
        JSONObject snapshot = ecuSnapshots.get(address);
        if (snapshot == null) {
            return null;
        }
        try {
            return new JSONObject(snapshot.toString());
        } catch (JSONException e) {
            return null;
        }
    }

    public JSONObject getAllEcuSnapshots() {
        JSONObject root = new JSONObject();
        for (Integer address : ecuSnapshots.keySet()) {
            JSONObject snapshot = ecuSnapshots.get(address);
            if (snapshot == null) {
                continue;
            }
            try {
                root.put(String.format(Locale.US, "0x%X", address), new JSONObject(snapshot.toString()));
            } catch (JSONException e) {
                Log.w(TAG, "Failed to clone ECU snapshot", e);
            }
        }
        return root;
    }

    private void recordCapabilities() {
        if (!sessionActive.get()) {
            return;
        }
        try {
            JSONObject data = new JSONObject();
            data.put("connectionType", CommService.medium.name());
            if (!TextUtils.isEmpty(adapterName)) {
                data.put("adapterName", adapterName);
            }
            data.put("device", Build.MANUFACTURER + " " + Build.MODEL);
            data.put("android", Build.VERSION.RELEASE);
            recordEvent(DiscoveryEventType.CAPABILITY, "Adapter capabilities captured", null, data);
        } catch (JSONException e) {
            Log.w(TAG, "Failed to encode capability payload", e);
        }
    }

    private JSONObject buildSessionMeta() {
        JSONObject meta = new JSONObject();
        try {
            meta.put("startedAt", sessionStartMs);
            meta.put("sessionId", sessionId);
        } catch (JSONException e) {
            Log.w(TAG, "Failed to encode session metadata", e);
        }
        return meta;
    }

    private void handleElmPropertyChange(PropertyChangeEvent event) {
        if (!sessionActive.get()) {
            return;
        }
        try {
            String propertyName = event.getPropertyName();
            if (ElmProt.PROP_STATUS.equals(propertyName)) {
                ElmProt.STAT status = (ElmProt.STAT) event.getNewValue();
                JSONObject payload = new JSONObject();
                payload.put("status", status.name());
                recordEvent(DiscoveryEventType.STATUS,
                    "Adapter status changed", null, payload);
            } else if (ElmProt.PROP_ECU_ADDRESS.equals(propertyName)) {
                Object value = event.getNewValue();
                if (value instanceof Set) {
                    @SuppressWarnings("unchecked")
                    Set<Integer> addresses = (Set<Integer>) value;
                    recordEcuAddressSnapshot(addresses);
                }
            }
        } catch (JSONException jsonException) {
            Log.w(TAG, "Failed to encode property change", jsonException);
        }
    }

    private void recordEvent(DiscoveryEventType type,
                             String message,
                             @Nullable Integer ecuAddress,
                             @Nullable JSONObject payload) {
        if (!sessionActive.get() || logWriter == null || sessionId == null) {
            return;
        }
        DiscoveryEvent event = new DiscoveryEvent(sessionId, type, message, ecuAddress, payload);
        executor.execute(() -> {
            try {
                logWriter.writeEvent(event);
            } catch (IOException e) {
                Log.e(TAG, "Failed to persist discovery event", e);
            }
        });
        Log.i(TAG, event.toLogcatString());
    }

    private static String generateSessionId() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
        sdf.setTimeZone(TimeZone.getDefault());
        return sdf.format(new Date()) + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    // region EcuManagerListener callbacks

    @Override
    public void onEcuDiscovered(EcuInfo ecu) {
        if (!sessionActive.get()) {
            return;
        }
        discoveredAddresses.add(ecu.getAddress());
        JSONObject snapshot = buildEcuSnapshot(ecu);
        ecuSnapshots.put(ecu.getAddress(), snapshot);
        recordEvent(DiscoveryEventType.ECU_DISCOVERED,
            "ECU discovered", ecu.getAddress(), snapshot);
    }

    @Override
    public void onEcuUpdated(EcuInfo ecu) {
        if (!sessionActive.get()) {
            return;
        }
        discoveredAddresses.add(ecu.getAddress());
        JSONObject snapshot = buildEcuSnapshot(ecu);
        ecuSnapshots.put(ecu.getAddress(), snapshot);
        recordEvent(DiscoveryEventType.ECU_UPDATED,
            "ECU updated", ecu.getAddress(), snapshot);
    }

    private JSONObject buildEcuSnapshot(EcuInfo ecu) {
        JSONObject json = new JSONObject();
        try {
            json.put("address", String.format(Locale.US, "0x%X", ecu.getAddress()));
            json.put("displayName", ecu.getDisplayName());
            json.put("type", ecu.getEcuType());
            if (ecu.getCalibrationId() != null) {
                json.put("calibrationId", ecu.getCalibrationId());
            }
            if (ecu.getCalibrationId2() != null) {
                json.put("calibrationId2", ecu.getCalibrationId2());
            }
            if (ecu.getCalibrationVerification() != null) {
                json.put("cvn", ecu.getCalibrationVerification());
            }
            json.put("responses", ecu.getResponseCount());
        } catch (JSONException e) {
            Log.w(TAG, "Failed to build ECU snapshot", e);
        }
        return json;
    }

    // endregion

    // region VehicleChangeListener callbacks

    @Override
    public void onVINChanged(String vin) {
        if (!sessionActive.get()) {
            return;
        }
        try {
            JSONObject data = new JSONObject();
            data.put("vin", vin);
            recordEvent(DiscoveryEventType.VEHICLE_ID, "VIN detected", null, data);
        } catch (JSONException e) {
            Log.w(TAG, "Failed to encode VIN event", e);
        }
    }

    @Override
    public void onVehicleDecoded(io.github.vindecoder.nhtsa.VehicleData vehicleData) {
        if (!sessionActive.get()) {
            return;
        }
        try {
            JSONObject data = new JSONObject();
            data.put("make", vehicleData.getMake());
            data.put("model", vehicleData.getModel());
            data.put("year", vehicleData.getModelYear());
            data.put("bodyClass", vehicleData.bodyClass);
            recordEvent(DiscoveryEventType.VEHICLE_ID, "Vehicle decoded", null, data);
        } catch (JSONException e) {
            Log.w(TAG, "Failed to encode vehicle decode", e);
        }
    }

    @Override
    public void onVehicleDisconnected() {
        // no-op
    }

    @Override
    public void onECUConnectionChanged(ElmProt.STAT state) {
        // handled via property listener already
    }

    @Override
    public void onVINRetrievalFailed() {
        if (!sessionActive.get()) {
            return;
        }
        recordEvent(DiscoveryEventType.VEHICLE_ID,
            "VIN retrieval failed", null, null);
    }

    @Override
    public void onDecodingStarted() {
        // no-op
    }

    @Override
    public void onDecodingError(String error) {
        if (!sessionActive.get()) {
            return;
        }
        try {
            JSONObject data = new JSONObject();
            data.put("error", error);
            recordEvent(DiscoveryEventType.ERROR,
                "VIN decode error", null, data);
        } catch (JSONException e) {
            Log.w(TAG, "Failed to encode decode error", e);
        }
    }

    // endregion
}
