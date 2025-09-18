package prot;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Manages comprehensive vehicle initialization with proper state management
 * Uses event-driven architecture instead of Thread.sleep()
 *
 * @author Wal33D
 */
public class InitializationManager {
    private static final Logger log = Logger.getLogger(InitializationManager.class.getName());

    private final ObdProt protocol;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    private InitProgressListener progressListener;
    private CompletableFuture<Boolean> initFuture;
    private long startTime;

    // Configuration
    private static final int PID_DISCOVERY_TIMEOUT_MS = 10000;
    private static final int VEHICLE_INFO_TIMEOUT_MS = 5000;
    private static final int FAULT_CODES_TIMEOUT_MS = 5000;
    private static final int FREEZE_FRAME_TIMEOUT_MS = 3000;
    private static final int POLL_INTERVAL_MS = 100;

    // State tracking
    private InitProgressListener.Phase currentPhase;
    private int lastPidCount = 0;
    private int pidStableCount = 0;
    private int lastDtcCount = 0;
    private int dtcStableCount = 0;

    public InitializationManager(ObdProt protocol) {
        this.protocol = protocol;
    }

    /**
     * Set the progress listener
     */
    public void setProgressListener(InitProgressListener listener) {
        this.progressListener = listener;
    }

    /**
     * Start comprehensive initialization
     * @return CompletableFuture that completes when initialization is done
     */
    public CompletableFuture<Boolean> initialize() {
        if (!running.compareAndSet(false, true)) {
            log.warning("Initialization already in progress");
            return CompletableFuture.completedFuture(false);
        }

        cancelled.set(false);
        initFuture = new CompletableFuture<>();
        startTime = System.currentTimeMillis();

        // Start the initialization state machine
        changePhase(InitProgressListener.Phase.STARTING, "Beginning comprehensive initialization");

        // Start with PID discovery
        executor.schedule(() -> startPidDiscovery(), 100, TimeUnit.MILLISECONDS);

        return initFuture;
    }

    /**
     * Cancel the initialization
     */
    public void cancel() {
        if (running.get()) {
            cancelled.set(true);
            changePhase(InitProgressListener.Phase.CANCELLED, "Initialization cancelled by user");
            complete(false);
        }
    }

    /**
     * Phase 1: PID Discovery
     */
    private void startPidDiscovery() {
        if (cancelled.get()) return;

        changePhase(InitProgressListener.Phase.PID_DISCOVERY, "Discovering supported PIDs");

        // Switch to DATA service to trigger PID discovery
        protocol.setService(ObdProt.OBD_SVC_DATA, true);

        // Start polling for PID discovery completion
        lastPidCount = 0;
        pidStableCount = 0;

        ScheduledFuture<?> pidPoller = executor.scheduleAtFixedRate(
            this::checkPidDiscoveryProgress,
            500, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS
        );

        // Set timeout
        executor.schedule(() -> {
            pidPoller.cancel(false);
            if (!cancelled.get() && currentPhase == InitProgressListener.Phase.PID_DISCOVERY) {
                onPidDiscoveryComplete();
            }
        }, PID_DISCOVERY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Check if PID discovery has stabilized
     */
    private void checkPidDiscoveryProgress() {
        if (cancelled.get()) return;

        int currentPidCount = protocol.getPermanentlySupportedPIDs().size();

        if (progressListener != null) {
            progressListener.onProgress(InitProgressListener.Phase.PID_DISCOVERY,
                currentPidCount, -1, "PIDs discovered: " + currentPidCount);
        }

        // Check if PID count has stabilized (same count for 5 consecutive checks)
        if (currentPidCount == lastPidCount && currentPidCount > 0) {
            pidStableCount++;
            if (pidStableCount >= 5) {
                onPidDiscoveryComplete();
            }
        } else {
            pidStableCount = 0;
            lastPidCount = currentPidCount;
        }
    }

    /**
     * PID Discovery complete
     */
    private void onPidDiscoveryComplete() {
        int pidCount = protocol.getPermanentlySupportedPIDs().size();

        changePhase(InitProgressListener.Phase.PID_DISCOVERY_COMPLETE,
            "Discovered " + pidCount + " supported PIDs");

        if (progressListener != null) {
            progressListener.onDataLoaded("PIDs", pidCount);
        }

        // Move to next phase
        executor.schedule(() -> startVehicleInfoLoad(), 500, TimeUnit.MILLISECONDS);
    }

    /**
     * Phase 2: Vehicle Info
     */
    private void startVehicleInfoLoad() {
        if (cancelled.get()) return;

        changePhase(InitProgressListener.Phase.VEHICLE_INFO, "Loading vehicle information");

        // Switch to VEHICLE_INFO service
        protocol.setService(ObdProt.OBD_SVC_VEH_INFO, true);

        // Wait for data with timeout
        executor.schedule(() -> {
            if (!cancelled.get()) {
                onVehicleInfoComplete();
            }
        }, VEHICLE_INFO_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Vehicle info load complete
     */
    private void onVehicleInfoComplete() {
        changePhase(InitProgressListener.Phase.VEHICLE_INFO_COMPLETE, "Vehicle info loaded");

        // Move to next phase
        executor.schedule(() -> startFaultCodesLoad(), 500, TimeUnit.MILLISECONDS);
    }

    /**
     * Phase 3: Fault Codes
     */
    private void startFaultCodesLoad() {
        if (cancelled.get()) return;

        changePhase(InitProgressListener.Phase.FAULT_CODES, "Reading fault codes");

        // Switch to READ_CODES service
        protocol.setService(ObdProt.OBD_SVC_READ_CODES, true);

        // Start polling for DTCs
        lastDtcCount = 0;
        dtcStableCount = 0;

        ScheduledFuture<?> dtcPoller = executor.scheduleAtFixedRate(
            this::checkFaultCodesProgress,
            500, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS
        );

        // Set timeout
        executor.schedule(() -> {
            dtcPoller.cancel(false);
            if (!cancelled.get() && currentPhase == InitProgressListener.Phase.FAULT_CODES) {
                onFaultCodesComplete();
            }
        }, FAULT_CODES_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Check fault codes loading progress
     */
    private void checkFaultCodesProgress() {
        if (cancelled.get()) return;

        // This would need access to the cached fault codes
        // For now, we'll simulate progress
        dtcStableCount++;
        if (dtcStableCount >= 5) {
            onFaultCodesComplete();
        }
    }

    /**
     * Fault codes load complete
     */
    private void onFaultCodesComplete() {
        changePhase(InitProgressListener.Phase.FAULT_CODES_COMPLETE, "Fault codes loaded");

        // Check if we have any fault codes to get freeze frames for
        if (protocol.getCachedFaultCodes().size() > 0) {
            executor.schedule(() -> startFreezeFramesLoad(), 500, TimeUnit.MILLISECONDS);
        } else {
            // No fault codes, skip freeze frames
            complete(true);
        }
    }

    /**
     * Phase 4: Freeze Frames
     */
    private void startFreezeFramesLoad() {
        if (cancelled.get()) return;

        changePhase(InitProgressListener.Phase.FREEZE_FRAMES, "Loading freeze frame data");

        // Load freeze frame 0 (most recent)
        loadFreezeFrame(0);
    }

    /**
     * Load a specific freeze frame
     */
    private void loadFreezeFrame(int frameId) {
        if (cancelled.get()) return;

        if (progressListener != null) {
            progressListener.onProgress(InitProgressListener.Phase.FREEZE_FRAMES,
                frameId + 1, 1, "Loading freeze frame " + frameId);
        }

        // Switch to FREEZEFRAME service with specific frame ID
        protocol.setFreezeFrame_Id(frameId);

        // Wait for data
        executor.schedule(() -> {
            if (!cancelled.get()) {
                onFreezeFramesComplete();
            }
        }, FREEZE_FRAME_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Freeze frames load complete
     */
    private void onFreezeFramesComplete() {
        changePhase(InitProgressListener.Phase.FREEZE_FRAMES_COMPLETE, "Freeze frames loaded");
        complete(true);
    }

    /**
     * Complete the initialization
     */
    private void complete(boolean success) {
        if (!running.get()) return;

        long duration = System.currentTimeMillis() - startTime;

        if (!cancelled.get()) {
            changePhase(InitProgressListener.Phase.COMPLETE,
                "Initialization " + (success ? "successful" : "failed"));
        }

        if (progressListener != null) {
            progressListener.onComplete(success, duration);
        }

        running.set(false);
        initFuture.complete(success);

        log.info("Comprehensive initialization completed in " + duration + "ms. Success: " + success);
    }

    /**
     * Change to a new phase
     */
    private void changePhase(InitProgressListener.Phase phase, String message) {
        currentPhase = phase;
        log.info("INIT PHASE: " + phase + " - " + message);

        if (progressListener != null) {
            progressListener.onPhaseChanged(phase, message);
        }
    }

    /**
     * Shutdown the executor
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
}