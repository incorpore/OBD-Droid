package com.obddroid.features.trackmode;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import com.obddroid.R;
import com.obddroid.obd.ObdProt;
import com.obddroid.features.trackmode.data.LapTime;
import com.obddroid.features.trackmode.data.Track;
import com.obddroid.features.trackmode.data.TrackDatabase;
import com.obddroid.features.trackmode.data.TrackSession;
import com.obddroid.features.trackmode.detection.LapTimingManager;
import com.obddroid.features.trackmode.recording.TelemetryRecorder;
import com.obddroid.features.trackmode.ui.GForceMeterView;
import com.obddroid.features.trackmode.ui.LapTimerView;
import com.obddroid.services.CommService;
import com.obddroid.ui.components.VehicleInfoFooter;
import com.obddroid.utils.PermissionManager;
import com.obddroid.utils.SnackbarHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Activity for track mode - lap timing and telemetry recording
 */
public class TrackModeActivity extends AppCompatActivity implements
        LapTimingManager.LapTimingListener {

    private static final String TAG = "TrackModeActivity";

    // UI Components
    private TextView tvTrackName;
    private TextView tvSessionInfo;
    private TextView tvCurrentLapTime;
    private TextView tvLastLapTime;
    private TextView tvBestLapTime;
    private TextView tvDeltaTime;
    private TextView tvLapCount;
    private TextView tvSpeed;
    private TextView tvRpm;
    private TextView tvGear;
    private LinearLayout sectorTimesLayout;
    private Button btnStartSession;
    private Button btnEndSession;
    private CardView lapTimerCard;
    private GForceMeterView gForceMeter;
    private LapTimerView lapTimerView;
    private VehicleInfoFooter vehicleInfoFooter;

    // Track components
    private TrackDatabase trackDatabase;
    private Track selectedTrack;
    private TrackSession currentSession;
    private LapTimingManager lapTimingManager;
    private TelemetryRecorder telemetryRecorder;

    // Communication service
    private CommService commService;
    private ObdProt obdProtocol;
    private boolean isServiceBound = false;

    // Update handler
    private Handler updateHandler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;

    // Session state
    private boolean isSessionActive = false;
    private List<Long> sectorSplits = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_track_mode);

        // Keep screen on during track sessions
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Initialize database
        trackDatabase = new TrackDatabase(this);

        // Initialize managers
        lapTimingManager = new LapTimingManager();
        lapTimingManager.addListener(this);
        telemetryRecorder = new TelemetryRecorder(this, lapTimingManager);

        // Setup UI
        setupViews();
        setupUpdateHandler();

        // Check permissions
        if (!PermissionManager.hasLocationPermission(this)) {
            PermissionManager.requestLocationPermission(this);
        }

        // Setup action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Track Mode");
        }

        // Load tracks and show selector
        showTrackSelector();
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindCommService();
        startUpdates();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopUpdates();
        if (isServiceBound) {
            unbindService(serviceConnection);
            isServiceBound = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isSessionActive) {
            endSession();
        }
        lapTimingManager.removeListener(this);
    }

    /**
     * Setup UI views
     */
    private void setupViews() {
        tvTrackName = findViewById(R.id.tv_track_name);
        tvSessionInfo = findViewById(R.id.tv_session_info);
        tvCurrentLapTime = findViewById(R.id.tv_current_lap_time);
        tvLastLapTime = findViewById(R.id.tv_last_lap_time);
        tvBestLapTime = findViewById(R.id.tv_best_lap_time);
        tvDeltaTime = findViewById(R.id.tv_delta_time);
        tvLapCount = findViewById(R.id.tv_lap_count);
        tvSpeed = findViewById(R.id.tv_speed);
        tvRpm = findViewById(R.id.tv_rpm);
        tvGear = findViewById(R.id.tv_gear);
        sectorTimesLayout = findViewById(R.id.sector_times_layout);
        btnStartSession = findViewById(R.id.btn_start_session);
        btnEndSession = findViewById(R.id.btn_end_session);
        lapTimerCard = findViewById(R.id.lap_timer_card);
        gForceMeter = findViewById(R.id.g_force_meter);
        lapTimerView = findViewById(R.id.lap_timer_view);
        vehicleInfoFooter = findViewById(R.id.vehicle_info_footer);

        // Set initial states
        btnEndSession.setEnabled(false);
        updateSessionUI();

        // Button listeners
        btnStartSession.setOnClickListener(v -> startSession());
        btnEndSession.setOnClickListener(v -> endSession());
    }

    /**
     * Setup update handler for UI refresh
     */
    private void setupUpdateHandler() {
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                updateUI();
                updateHandler.postDelayed(this, 100); // Update 10 times per second
            }
        };
    }

    /**
     * Start UI updates
     */
    private void startUpdates() {
        updateHandler.post(updateRunnable);
    }

    /**
     * Stop UI updates
     */
    private void stopUpdates() {
        updateHandler.removeCallbacks(updateRunnable);
    }

    /**
     * Update UI with current telemetry
     */
    private void updateUI() {
        if (!isSessionActive || telemetryRecorder == null) return;

        // Update speed
        float speed = telemetryRecorder.getCurrentSpeed();
        tvSpeed.setText(String.format(Locale.US, "%.0f", speed));

        // Update RPM
        float rpm = telemetryRecorder.getCurrentRpm();
        tvRpm.setText(String.format(Locale.US, "%.0f", rpm));

        // Update gear
        int gear = calculateGear(speed, rpm);
        tvGear.setText(gear > 0 ? String.valueOf(gear) : "N");

        // Update G-force meter
        if (gForceMeter != null) {
            gForceMeter.updateGForce(
                telemetryRecorder.getGForceLateral(),
                telemetryRecorder.getGForceLongitudinal()
            );
        }

        // Update lap timer view
        if (lapTimerView != null && lapTimingManager.getCurrentLap() != null) {
            long currentTime = System.currentTimeMillis() -
                               lapTimingManager.getCurrentLap().getStartTime();
            lapTimerView.updateLapTime(currentTime, lapTimingManager.getDeltaTime());
        }

        // Update delta time color
        long delta = lapTimingManager.getDeltaTime();
        if (delta > 0) {
            tvDeltaTime.setTextColor(Color.RED);
            tvDeltaTime.setText("+" + LapTime.formatTime(Math.abs(delta)));
        } else if (delta < 0) {
            tvDeltaTime.setTextColor(Color.GREEN);
            tvDeltaTime.setText("-" + LapTime.formatTime(Math.abs(delta)));
        } else {
            tvDeltaTime.setTextColor(Color.WHITE);
            tvDeltaTime.setText("--:--.---");
        }
    }

    /**
     * Calculate gear from speed and RPM
     */
    private int calculateGear(float speedKmh, float rpm) {
        if (rpm < 500) return 0;
        float ratio = (speedKmh * 1000 / 60) / (float)(rpm * 2 * Math.PI * 0.3);

        if (ratio < 0.3) return 1;
        else if (ratio < 0.5) return 2;
        else if (ratio < 0.7) return 3;
        else if (ratio < 0.9) return 4;
        else if (ratio < 1.2) return 5;
        else return 6;
    }

    /**
     * Show track selector dialog
     */
    private void showTrackSelector() {
        List<Track> tracks = trackDatabase.getAllTracks();
        String[] trackNames = new String[tracks.size()];

        for (int i = 0; i < tracks.size(); i++) {
            Track track = tracks.get(i);
            trackNames[i] = track.getName() + " (" + track.getCountry() + ")";
        }

        new AlertDialog.Builder(this)
                .setTitle("Select Track")
                .setItems(trackNames, (dialog, which) -> {
                    selectedTrack = tracks.get(which);
                    tvTrackName.setText(selectedTrack.getName());
                    trackDatabase.updateTrackLastUsed(selectedTrack.getId());
                })
                .setNeutralButton("Create New", (dialog, which) -> {
                    // TODO: Implement track creation mode
                    Toast.makeText(this, "Track creation coming soon!", Toast.LENGTH_SHORT).show();
                })
                .setCancelable(false)
                .show();
    }

    /**
     * Show session type selector
     */
    private void showSessionTypeSelector() {
        TrackSession.SessionType[] types = TrackSession.SessionType.values();
        String[] typeNames = new String[types.length];

        for (int i = 0; i < types.length; i++) {
            typeNames[i] = types[i].getDisplayName();
        }

        new AlertDialog.Builder(this)
                .setTitle("Select Session Type")
                .setItems(typeNames, (dialog, which) -> {
                    TrackSession.SessionType selectedType = types[which];
                    createSession(selectedType);
                })
                .setCancelable(false)
                .show();
    }

    /**
     * Start track session
     */
    private void startSession() {
        if (selectedTrack == null) {
            SnackbarHelper.showError(this,
                    "Please select a track first");
            return;
        }

        showSessionTypeSelector();
    }

    /**
     * Create and start a new session
     */
    private void createSession(TrackSession.SessionType type) {
        // Create session
        currentSession = new TrackSession(selectedTrack.getId(),
                selectedTrack.getName(), type);

        // Get vehicle info if available
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String vehicleName = prefs.getString("vehicle_name", "");
        String driverName = prefs.getString("driver_name", "");

        currentSession.setVehicleName(vehicleName);
        currentSession.setDriverName(driverName);

        // Start session
        currentSession.startSession();
        lapTimingManager.startSession(selectedTrack, currentSession);
        telemetryRecorder.startRecording(obdProtocol);

        isSessionActive = true;
        btnStartSession.setEnabled(false);
        btnEndSession.setEnabled(true);

        updateSessionUI();

        SnackbarHelper.showInfo(this,
                "Session started - Cross finish line to begin lap timing");
    }

    /**
     * End current session
     */
    private void endSession() {
        if (!isSessionActive) return;

        // Stop recording
        lapTimingManager.stopSession();
        telemetryRecorder.stopRecording();
        currentSession.endSession();

        // Save session to database
        long sessionId = trackDatabase.insertSession(currentSession);
        currentSession.setId(sessionId);

        // Save all laps
        for (LapTime lap : currentSession.getLaps()) {
            lap.setSessionId(sessionId);
            trackDatabase.insertLap(lap);
        }

        isSessionActive = false;
        btnStartSession.setEnabled(true);
        btnEndSession.setEnabled(false);

        updateSessionUI();

        // Show session summary
        showSessionSummary();
    }

    /**
     * Update session UI
     */
    private void updateSessionUI() {
        if (currentSession != null) {
            tvSessionInfo.setText(String.format("%s - %s",
                    currentSession.getSessionType().getDisplayName(),
                    currentSession.getFormattedDuration()));

            tvLapCount.setText(String.format("Lap %d/%d",
                    currentSession.getTotalLaps() + 1,
                    currentSession.getValidLaps()));
        } else {
            tvSessionInfo.setText("No active session");
            tvLapCount.setText("Lap 0/0");
        }
    }

    /**
     * Show session summary dialog
     */
    private void showSessionSummary() {
        if (currentSession == null) return;

        String summary = String.format(
                "Session Complete!\n\n" +
                "Type: %s\n" +
                "Duration: %s\n" +
                "Total Laps: %d\n" +
                "Valid Laps: %d\n" +
                "Best Lap: %s\n" +
                "Max Speed: %.1f km/h\n" +
                "Total Distance: %.2f km",
                currentSession.getSessionType().getDisplayName(),
                currentSession.getFormattedDuration(),
                currentSession.getTotalLaps(),
                currentSession.getValidLaps(),
                LapTime.formatTime(currentSession.getBestLapTime()),
                currentSession.getMaxSpeed(),
                currentSession.getTotalDistance() / 1000.0
        );

        new AlertDialog.Builder(this)
                .setTitle("Session Summary")
                .setMessage(summary)
                .setPositiveButton("OK", null)
                .setNeutralButton("View Details", (dialog, which) -> {
                    // TODO: Open detailed session view
                })
                .show();
    }

    // Lap timing listener callbacks
    @Override
    public void onLapStarted(int lapNumber) {
        runOnUiThread(() -> {
            tvLapCount.setText(String.format("Lap %d", lapNumber));
            sectorSplits.clear();
            SnackbarHelper.showInfo(this,
                    "Lap " + lapNumber + " started");
        });
    }

    @Override
    public void onLapCompleted(LapTime lap) {
        runOnUiThread(() -> {
            tvLastLapTime.setText(lap.getFormattedLapTime());

            if (lap.isPurpleLap()) {
                tvBestLapTime.setText(lap.getFormattedLapTime());
                tvBestLapTime.setTextColor(Color.parseColor("#9B59B6")); // Purple
                SnackbarHelper.showSuccess(this,
                        "New best lap! " + lap.getFormattedLapTime());
            }

            if (!lap.isValid()) {
                SnackbarHelper.showWarning(this,
                        "Lap invalidated");
            }

            updateSessionUI();
        });
    }

    @Override
    public void onSectorCompleted(int sector, long sectorTime) {
        runOnUiThread(() -> {
            sectorSplits.add(sectorTime);
            updateSectorTimes();
            SnackbarHelper.showInfo(this,
                    "Sector " + (sector + 1) + ": " + LapTime.formatTime(sectorTime));
        });
    }

    @Override
    public void onFinishLineApproaching(double distanceMeters) {
        runOnUiThread(() -> {
            // Flash lap timer card when approaching finish
            if (distanceMeters < 50) {
                lapTimerCard.setCardBackgroundColor(Color.YELLOW);
                updateHandler.postDelayed(() -> {
                    lapTimerCard.setCardBackgroundColor(Color.parseColor("#1E1E1E"));
                }, 200);
            }
        });
    }

    @Override
    public void onLapTimeUpdate(long currentLapTime, long predictedTotal, long delta) {
        runOnUiThread(() -> {
            tvCurrentLapTime.setText(LapTime.formatTime(currentLapTime));
            // Delta is updated in updateUI()
        });
    }

    @Override
    public void onLapInvalidated(String reason) {
        runOnUiThread(() -> {
            SnackbarHelper.showWarning(this,
                    "Lap invalid: " + reason);
        });
    }

    /**
     * Update sector times display
     */
    private void updateSectorTimes() {
        sectorTimesLayout.removeAllViews();

        for (int i = 0; i < sectorSplits.size(); i++) {
            TextView sectorView = new TextView(this);
            sectorView.setText(String.format("S%d: %s", i + 1,
                    LapTime.formatTime(sectorSplits.get(i))));
            sectorView.setTextColor(Color.WHITE);
            sectorView.setPadding(16, 8, 16, 8);
            sectorTimesLayout.addView(sectorView);
        }
    }

    /**
     * Bind to communication service
     */
    private void bindCommService() {
        Intent intent = new Intent(this, CommService.class);
        bindService(intent, serviceConnection, BIND_AUTO_CREATE);
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // TODO: Properly integrate with CommService when binder is available
            // For now, we'll skip the OBD protocol integration
            isServiceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            commService = null;
            obdProtocol = null;
            isServiceBound = false;
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.track_mode_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == R.id.action_track_history) {
            // TODO: Open track history
            Toast.makeText(this, "Track history coming soon!", Toast.LENGTH_SHORT).show();
            return true;
        } else if (item.getItemId() == R.id.action_track_settings) {
            // TODO: Open track settings
            Toast.makeText(this, "Track settings coming soon!", Toast.LENGTH_SHORT).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}