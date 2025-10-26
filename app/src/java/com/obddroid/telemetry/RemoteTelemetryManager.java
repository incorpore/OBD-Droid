package com.obddroid.services.telemetry;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.MqttClientBuilder;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3BlockingClient;
import com.hivemq.client.mqtt.mqtt3.message.connect.Mqtt3ConnectBuilder;
import com.obddroid.R;
import com.obddroid.ecu.EcuDataPv;
import com.obddroid.obd.ObdProt;
import com.obddroid.common.ProcessVariables.PvChange;
import com.obddroid.common.ProcessVariables.PvChangeEvent;
import com.obddroid.common.ProcessVariables.PvChangeListener;
import com.obddroid.common.ProcessVariables.PvChangeType;
import com.obddroid.common.ProcessVariables.TypedPvChangeListener;
import com.obddroid.utils.SecurePreferences;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Publishes selected PID updates to a configured Remote Telemetry server at a periodic interval.
 */
public class RemoteTelemetryManager {

    private static final String TAG = "RemoteTelemetry";

    public static final String PREF_ENABLED_STATE = "remote_telemetry_enabled_state";
    public static final String PREF_PROTOCOL = "remote_telemetry_protocol";
    public static final String PREF_HOST = "remote_telemetry_host";
    public static final String PREF_PORT = "remote_telemetry_port";
    public static final String PREF_PREFIX = "remote_telemetry_prefix";
    public static final String PREF_USERNAME = "remote_telemetry_username";
    public static final String PREF_PASSWORD = "remote_telemetry_password";
    public static final String PREF_CLIENT_ID = "remote_telemetry_client_id";
    public static final String PREF_QOS = "remote_telemetry_qos";
    public static final String PREF_RETAIN = "remote_telemetry_retain";
    public static final String PREF_UPDATE_PERIOD = "remote_telemetry_update_period";
    public static final String PREF_SELECTED_ITEMS = "remote_telemetry_selected_items";
    public static final String PREF_STATUS = "remote_telemetry_status";
    public static final String PREF_LAST_STATUS_CODE = "remote_telemetry_last_status_code";
    public static final String PREF_LAST_STATUS_MESSAGE = "remote_telemetry_last_status_message";
    public static final String PREF_LAST_STATUS_TIME = "remote_telemetry_last_status_time";

    public static final String STATUS_IDLE = "idle";
    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_FAILURE = "failure";
    public static final String STATUS_STOPPED = "stopped";

    private final Context appContext;
    private final SharedPreferences preferences;
    private final SecurePreferences securePreferences;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final ConcurrentMap<EcuDataPv, PvChangeListener> pvListeners = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> valueMap = new ConcurrentHashMap<>();

    private final TypedPvChangeListener masterListener = new TypedPvChangeListener() {
        @Override
        public void pvChanged(PvChange change) {
            if (!active) {
                return;
            }
            PvChangeType type = change.getPrimaryType();
            if (type == PvChangeType.ADDED || type == PvChangeType.MODIFIED) {
                Object candidate = change.getValue();
                if (candidate instanceof EcuDataPv) {
                    attachToPv((EcuDataPv) candidate);
                } else {
                    Object key = change.getKey();
                    if (key instanceof String) {
                        EcuDataPv pv = ObdProt.PidPvs.getTyped(key);
                        if (pv != null) {
                            attachToPv(pv);
                        }
                    }
                }
            } else if (type == PvChangeType.REMOVED || type == PvChangeType.CLEARED) {
                Object candidate = change.getValue();
                if (candidate instanceof EcuDataPv) {
                    detachFromPv((EcuDataPv) candidate);
                }
            }
        }
    };

    private ScheduledExecutorService executor;
    private ScheduledFuture<?> publishTask;
    private boolean active = false;

    private boolean useSsl;
    private String host;
    private int port;
    private String topicPrefix;
    private String username;
    private String password;
    private String clientId;
    private int qos = 0;
    private boolean retainMessages = true;
    private int publishPeriodSeconds = 30;
    private Set<String> selectedMnemonics = Collections.emptySet();

    public RemoteTelemetryManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.preferences = PreferenceManager.getDefaultSharedPreferences(appContext);
        this.securePreferences = new SecurePreferences(appContext);
    }

    public boolean isActive() {
        return active;
    }

    public void setStatusListener(StatusListener listener) {
        this.statusListener = listener;
        if (listener != null) {
            final String lastCode = preferences.getString(PREF_LAST_STATUS_CODE, STATUS_IDLE);
            final String lastMessage = preferences.getString(PREF_LAST_STATUS_MESSAGE, "");
            mainHandler.post(() -> {
                StatusListener current = this.statusListener;
                if (current == listener) {
                    current.onStatusChanged(
                        lastCode != null ? lastCode : STATUS_IDLE,
                        lastMessage != null ? lastMessage : ""
                    );
                }
            });
        }
    }

    public boolean start() {
        if (active) {
            return true;
        }

        if (!loadConfiguration()) {
            recordStatus(STATUS_FAILURE, appContext.getString(R.string.remote_telemetry_status_error_configuration));
            return false;
        }

        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "live-data-sharing-publisher");
            thread.setDaemon(true);
            return thread;
        });

        attachExistingPvs();
        ObdProt.PidPvs.addPvChangeListener(masterListener, PvChangeEvent.PV_ALLEVENTS);

        publishTask = executor.scheduleAtFixedRate(
            this::publishSnapshotSafely,
            publishPeriodSeconds,
            publishPeriodSeconds,
            TimeUnit.SECONDS
        );

        recordStatus(STATUS_IDLE, appContext.getString(R.string.remote_telemetry_status_waiting_for_data));
        active = true;
        return true;
    }

    public void stop() {
        if (!active) {
            return;
        }

        if (publishTask != null) {
            publishTask.cancel(true);
            publishTask = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }

        ObdProt.PidPvs.removePvChangeListener(masterListener);
        detachAllPvs();
        valueMap.clear();

        recordStatus(STATUS_STOPPED, appContext.getString(R.string.remote_telemetry_status_stopped));
        active = false;
    }

    private boolean loadConfiguration() {
        String protocolValue = preferences.getString(PREF_PROTOCOL, "tcp");
        useSsl = "ssl".equalsIgnoreCase(protocolValue);

        host = preferences.getString(PREF_HOST, "");
        if (host == null || host.trim().isEmpty()) {
            Log.w(TAG, "Remote Telemetry host not configured");
            return false;
        }
        host = host.trim();

        String portString = preferences.getString(PREF_PORT, "1883");
        try {
            port = Integer.parseInt(portString);
        } catch (NumberFormatException ex) {
            port = 1883;
        }

        topicPrefix = preferences.getString(PREF_PREFIX, "obddroid/");
        if (topicPrefix == null || topicPrefix.isEmpty()) {
            topicPrefix = "obddroid/";
        }
        if (!topicPrefix.endsWith("/")) {
            topicPrefix = topicPrefix + "/";
        }

        username = preferences.getString(PREF_USERNAME, "");
        password = securePreferences.getRemoteTelemetryPassword();
        clientId = preferences.getString(PREF_CLIENT_ID, "");
        if (clientId == null || clientId.trim().isEmpty()) {
            clientId = "obddroid-" + UUID.randomUUID();
            preferences.edit().putString(PREF_CLIENT_ID, clientId).apply();
        }

        String qosString = preferences.getString(PREF_QOS, "0");
        try {
            qos = Math.max(0, Math.min(2, Integer.parseInt(qosString)));
        } catch (NumberFormatException ex) {
            qos = 0;
        }

        retainMessages = preferences.getBoolean(PREF_RETAIN, true);

        String periodString = preferences.getString(PREF_UPDATE_PERIOD, "30");
        try {
            publishPeriodSeconds = Math.max(1, Integer.parseInt(periodString));
        } catch (NumberFormatException ex) {
            publishPeriodSeconds = 30;
        }

        Set<String> selected = preferences.getStringSet(PREF_SELECTED_ITEMS, Collections.emptySet());
        if (selected == null) {
            selectedMnemonics = Collections.emptySet();
        } else {
            selectedMnemonics = new HashSet<>(selected);
        }

        return true;
    }

    private void publishSnapshotSafely() {
        if (!active) {
            return;
        }
        Map<String, String> snapshot = new HashMap<>(valueMap);
        if (snapshot.isEmpty()) {
            return;
        }

        try {
            boolean success = publishSnapshot(snapshot);
            if (success) {
                recordStatus(STATUS_SUCCESS, appContext.getString(R.string.remote_telemetry_status_publish_success));
            }
        } catch (Exception ex) {
            Log.e(TAG, "Remote Telemetry publish failed", ex);
            recordStatus(STATUS_FAILURE, summarizeException(ex));
        }
    }

    private boolean publishSnapshot(Map<String, String> snapshot) {
        Mqtt3BlockingClient client = buildClient();
        if (client == null) {
            recordStatus(STATUS_FAILURE, appContext.getString(R.string.remote_telemetry_status_error_client));
            return false;
        }

        boolean connected = false;
        final boolean[] hadFailure = {false};
        try {
            Mqtt3ConnectBuilder.Send<?> connectBuilder = client.connectWith()
                .cleanSession(true);
            if (!isEmpty(username) || !isEmpty(password)) {
                connectBuilder.simpleAuth()
                    .username(username != null ? username : "")
                    .password(password != null ? password.getBytes(StandardCharsets.UTF_8) : new byte[0])
                    .applySimpleAuth();
            }
            connectBuilder.send();
            connected = true;

            MqttQos mqttQos = MqttQos.fromCode(qos);
            snapshot.forEach((mnemonic, value) -> {
                try {
                    String topic = topicPrefix + sanitizeTopic(mnemonic);
                    client.publishWith()
                        .topic(topic)
                        .payload(value.getBytes(StandardCharsets.UTF_8))
                        .qos(mqttQos)
                        .retain(retainMessages)
                        .send();
                } catch (Exception publishException) {
                    Log.e(TAG, "Failed to publish Remote Telemetry topic", publishException);
                    recordStatus(STATUS_FAILURE, summarizeException(publishException));
                    hadFailure[0] = true;
                }
            });
        } catch (Exception ex) {
            Log.e(TAG, "Remote Telemetry publish failed", ex);
            recordStatus(STATUS_FAILURE, summarizeException(ex));
            hadFailure[0] = true;
        } finally {
            if (connected) {
                try {
                    client.disconnect();
                } catch (Exception disconnectException) {
                    Log.w(TAG, "Failed to disconnect Remote Telemetry client", disconnectException);
                }
            }
        }

        if (hadFailure[0]) {
            return false;
        }
        return true;
    }

    private Mqtt3BlockingClient buildClient() {
        try {
            MqttClientBuilder baseBuilder = MqttClient.builder()
                .identifier(clientId)
                .serverHost(host)
                .serverPort(port);
            if (useSsl) {
                baseBuilder.sslWithDefaultConfig();
            }
            return baseBuilder.useMqttVersion3().buildBlocking();
        } catch (Exception ex) {
            Log.e(TAG, "Unable to create Remote Telemetry client", ex);
            recordStatus(STATUS_FAILURE, summarizeException(ex));
            return null;
        }
    }

    private void attachExistingPvs() {
        Collection values = ObdProt.PidPvs.values();
        if (values == null) {
            return;
        }
        for (Object value : values) {
            if (value instanceof EcuDataPv) {
                attachToPv((EcuDataPv) value);
            }
        }
    }

    private void attachToPv(EcuDataPv pv) {
        if (pv == null || pvListeners.containsKey(pv)) {
            return;
        }
        PvChangeListener listener = event -> {
            if (!active) {
                return;
            }
            if (EcuDataPv.FIELDS[EcuDataPv.FID_VALUE].equals(event.getKey())) {
                Object valueObj = event.getValue();
                if (valueObj != null) {
                    handleValueUpdate(pv, valueObj);
                }
            }
        };
        pv.addPvChangeListener(listener, PvChangeEvent.PV_MODIFIED);
        pvListeners.put(pv, listener);
    }

    private void detachFromPv(EcuDataPv pv) {
        PvChangeListener listener = pvListeners.remove(pv);
        if (listener != null) {
            pv.removePvChangeListener(listener);
        }
    }

    private void detachAllPvs() {
        pvListeners.forEach((pv, listener) -> pv.removePvChangeListener(listener));
        pvListeners.clear();
    }

    private void handleValueUpdate(EcuDataPv pv, Object value) {
        String mnemonic = (String) pv.get(EcuDataPv.FID_MNEMONIC);
        if (mnemonic == null || mnemonic.isEmpty()) {
            mnemonic = pv.toString();
        }

        if (!selectedMnemonics.isEmpty() && !selectedMnemonics.contains(mnemonic)) {
            return;
        }

        String formattedValue = String.valueOf(value);
        valueMap.put(mnemonic, formattedValue);
    }

    private String sanitizeTopic(String mnemonic) {
        String sanitized = mnemonic.replaceAll("[\\s]+", "_")
            .replaceAll("[^A-Za-z0-9_\\-]", "").toLowerCase(Locale.US);
        if (sanitized.isEmpty()) {
            sanitized = mnemonic.replace(' ', '_');
        }
        return sanitized;
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void recordStatus(String statusCode, String detail) {
        String safeStatus = statusCode != null ? statusCode : STATUS_IDLE;
        String safeDetail = detail != null ? detail : "";
        preferences.edit()
            .putString(PREF_LAST_STATUS_CODE, safeStatus)
            .putString(PREF_LAST_STATUS_MESSAGE, safeDetail)
            .putLong(PREF_LAST_STATUS_TIME, System.currentTimeMillis())
            .apply();
        dispatchStatus(safeStatus, safeDetail);
    }

    private String summarizeException(Exception ex) {
        if (ex == null) {
            return appContext.getString(R.string.remote_telemetry_status_error_unknown);
        }
        String message = ex.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return ex.getClass().getSimpleName();
        }
        return message;
    }

    private void dispatchStatus(String statusCode, String detail) {
        StatusListener listener = statusListener;
        if (listener == null) {
            return;
        }
        String safeDetail = detail != null ? detail : "";
        mainHandler.post(() -> {
            StatusListener current = statusListener;
            if (current != null) {
                current.onStatusChanged(statusCode, safeDetail);
            }
        });
    }

    public interface StatusListener {
        void onStatusChanged(String statusCode, String detail);
    }

    private volatile StatusListener statusListener;
}
