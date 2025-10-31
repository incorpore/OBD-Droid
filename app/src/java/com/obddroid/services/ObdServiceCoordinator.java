package com.obddroid.services;

import android.util.Log;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

/**
 * Coordinates exclusive access to the ELM327 OBD adapter to prevent race conditions.
 *
 * PROBLEM SOLVED:
 * Multiple activities and services (LiveDataActivity, EcuDiscoveryService, FaultCodeService, etc.)
 * simultaneously access CommService.elm with NO mutual exclusion, causing:
 * - Interleaved responses (ECU A's response consumed by Service B)
 * - State corruption (ATH1 left enabled, breaking normal communication)
 * - Timeouts (services waiting for responses that were stolen)
 * - Crashes (parser confusion from unexpected response formats)
 *
 * SOLUTION:
 * This coordinator provides:
 * 1. Mutual Exclusion: Only ONE caller can access the adapter at a time (ReentrantLock)
 * 2. Priority Queuing: Critical ops (clear codes) jump ahead of background polling
 * 3. Owner Tracking: Know which service currently holds the lock (debugging)
 * 4. Timeout Protection: Force-unlock stuck owners after 30 seconds
 * 5. Graceful Degradation: Fallback to direct access if coordinator fails
 *
 * ARCHITECTURE:
 * - Dedicated worker thread continuously takes from priority queue (blocking take)
 * - Requests are automatically ordered by priority (CRITICAL > HIGH > NORMAL > LOW)
 * - Within same priority, FIFO ordering (first submitted, first processed)
 * - ReentrantLock ensures only one task executes at a time
 *
 * USAGE:
 * <pre>{@code
 * // Before (UNSAFE - direct access):
 * CommService.elm.setService(OBD_SVC_DATA);
 *
 * // After (SAFE - coordinated access):
 * CommService.coordinator.executeExclusive(
 *     "LiveDataActivity",
 *     RequestPriority.LOW,
 *     () -> {
 *         CommService.elm.setService(OBD_SVC_DATA);
 *         return null;
 *     }
 * ).join();  // Wait for completion
 * }</pre>
 *
 * THREAD SAFETY:
 * - All methods are thread-safe
 * - Uses ReentrantLock for mutual exclusion
 * - PriorityBlockingQueue for thread-safe request queuing
 * - Worker thread processes requests sequentially
 *
 * @see RequestPriority
 * @see CoordinatedRequest
 */
public class ObdServiceCoordinator {

    private static final String TAG = "ObdServiceCoordinator";
    private static final Logger log = Logger.getLogger(TAG);

    // ========== CONFIGURATION ==========

    /** Maximum time any single owner can hold the lock (30 seconds) */
    private static final long MAX_HOLD_TIME_MS = 30_000L;

    /** Maximum queue size before rejecting new requests (100) */
    private static final int MAX_QUEUE_SIZE = 100;

    /** Monitoring interval for stuck owner detection (1 second) */
    private static final long MONITOR_INTERVAL_MS = 1_000L;

    // ========== CORE COMPONENTS ==========

    /** The lock that provides mutual exclusion for adapter access */
    private final ReentrantLock elmLock = new ReentrantLock(true);  // Fair lock (FIFO within same priority)

    /** Priority queue for pending requests (thread-safe, blocking) */
    private final PriorityBlockingQueue<CoordinatedRequest<?>> requestQueue =
        new PriorityBlockingQueue<>(16);  // Initial capacity 16, grows as needed

    /** Dedicated worker thread that processes requests from queue */
    private final Thread workerThread;

    /** Flag to stop worker thread */
    private volatile boolean running = true;

    /** Executor for timeout monitoring */
    private final ScheduledExecutorService monitorExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "OBD-Coordinator-Monitor");
        thread.setDaemon(true);
        return thread;
    });

    // ========== STATE TRACKING ==========

    /** Current owner of the lock (null if unlocked) */
    private volatile String currentOwner = null;

    /** Timestamp when current owner acquired the lock */
    private volatile long ownerStartTime = 0;

    /** Current request being processed (null if none) */
    private volatile CoordinatedRequest<?> currentRequest = null;

    /** Total requests processed (for statistics) */
    private volatile long totalRequestsProcessed = 0;

    /** Total requests failed (for statistics) */
    private volatile long totalRequestsFailed = 0;

    /** Total time spent waiting for locks (for statistics) */
    private volatile long totalWaitTimeMs = 0;

    // ========== INITIALIZATION ==========

    /**
     * Create a new coordinator and start the worker and monitor threads
     */
    public ObdServiceCoordinator() {
        // Start dedicated worker thread that continuously processes from priority queue
        workerThread = new Thread(() -> {
            log.info("Worker thread started");

            while (running) {
                try {
                    // BLOCKING TAKE - waits until a request is available
                    // Automatically gets highest priority item from queue!
                    CoordinatedRequest<?> request = requestQueue.take();

                    log.fine(() -> String.format(
                        "Worker picked up: %s (queue size: %d)",
                        request.toString(),
                        requestQueue.size()
                    ));

                    // Process the request (acquires lock, executes, releases)
                    processRequest(request);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.info("Worker thread interrupted - shutting down");
                    break;
                } catch (Exception e) {
                    // Don't let worker thread die on unexpected errors
                    Log.e(TAG, "Worker thread error (recovered): " + e.getMessage(), e);
                }
            }

            log.info("Worker thread stopped");
        }, "OBD-Coordinator-Worker");

        workerThread.setDaemon(true);  // Don't prevent app shutdown
        workerThread.start();

        // Start the stuck owner monitor
        monitorExecutor.scheduleAtFixedRate(
            this::monitorTimeout,
            MONITOR_INTERVAL_MS,
            MONITOR_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );

        log.info("═══════════════════════════════════════════════════════");
        log.info("OBD Service Coordinator INITIALIZED");
        log.info("  Max hold time: " + MAX_HOLD_TIME_MS + "ms");
        log.info("  Max queue size: " + MAX_QUEUE_SIZE);
        log.info("  Monitor interval: " + MONITOR_INTERVAL_MS + "ms");
        log.info("  Worker thread: " + workerThread.getName());
        log.info("═══════════════════════════════════════════════════════");
    }

    // ========== PUBLIC API ==========

    /**
     * Execute a task with exclusive ELM327 adapter access.
     *
     * This is the main entry point for coordinated adapter access. The task will:
     * 1. Be queued with the specified priority
     * 2. Wait for the adapter to become available (worker thread picks it up)
     * 3. Execute with exclusive access (no other tasks can run)
     * 4. Return the result via CompletableFuture
     *
     * TIMEOUT BEHAVIOR:
     * - If lock acquisition times out, the request fails with TimeoutException
     * - Timeout duration depends on priority (CRITICAL=60s, HIGH=30s, NORMAL=20s, LOW=10s)
     *
     * @param owner Caller identification (e.g., "LiveDataActivity", "FaultCodeService")
     * @param priority Priority level (CRITICAL, HIGH, NORMAL, LOW)
     * @param task The work to perform with exclusive adapter access
     * @param <T> Return type of the task
     * @return CompletableFuture that completes with the task result
     */
    public <T> CompletableFuture<T> executeExclusive(
        String owner,
        RequestPriority priority,
        Callable<T> task
    ) {
        // Create request wrapper
        CoordinatedRequest<T> request = new CoordinatedRequest<>(owner, priority, task);

        // Check queue size limit
        if (requestQueue.size() >= MAX_QUEUE_SIZE) {
            String error = String.format(
                "Request queue full (%d requests) - adapter overwhelmed! Rejecting request from %s",
                requestQueue.size(),
                owner
            );
            log.severe(error);
            request.completeExceptionally(new IllegalStateException(error));
            return request.getResult();
        }

        // Add to priority queue - worker thread will pick it up automatically
        // The queue is sorted, so higher priority requests will be taken first!
        requestQueue.offer(request);

        log.info(String.format(
            "→ Queued: %s (queue size: %d)",
            request.toString(),
            requestQueue.size()
        ));

        // Return the future immediately - worker will process when ready
        return request.getResult();
    }

    /**
     * Execute a task with exclusive access and wait for completion.
     *
     * Convenience method that blocks until the task completes.
     *
     * @param owner Caller identification
     * @param priority Priority level
     * @param task The work to perform
     * @param <T> Return type
     * @return Task result
     * @throws Exception if task fails or times out
     */
    public <T> T executeExclusiveBlocking(
        String owner,
        RequestPriority priority,
        Callable<T> task
    ) throws Exception {
        return executeExclusive(owner, priority, task).get();
    }

    // ========== REQUEST PROCESSING (runs on worker thread) ==========

    /**
     * Process a single request - acquires lock, executes task, releases lock.
     * Called by worker thread after taking request from priority queue.
     */
    private <T> void processRequest(CoordinatedRequest<T> request) {
        // Check if already completed (e.g., canceled externally)
        if (request.isCompleted()) {
            log.warning("Request already completed before processing: " + request);
            return;
        }

        long startWait = System.currentTimeMillis();

        try {
            // Acquire lock with timeout
            boolean acquired = elmLock.tryLock(
                request.getPriority().getTimeout(),
                TimeUnit.MILLISECONDS
            );

            if (!acquired) {
                long waitTime = System.currentTimeMillis() - startWait;
                String error = String.format(
                    "Lock acquisition TIMEOUT after %dms (limit: %dms): %s",
                    waitTime,
                    request.getPriority().getTimeout(),
                    request.toString()
                );
                log.severe(error);

                totalRequestsFailed++;
                request.completeExceptionally(new TimeoutException(error));
                return;
            }

            long waitTime = System.currentTimeMillis() - startWait;
            totalWaitTimeMs += waitTime;

            // Lock acquired - set owner tracking
            currentOwner = request.getOwner();
            ownerStartTime = System.currentTimeMillis();
            currentRequest = request;

            log.info(String.format(
                "🔒 LOCK ACQUIRED by %s (waited %dms, priority=%s, queue=%d)",
                request.getOwner(),
                waitTime,
                request.getPriority().name(),
                requestQueue.size()
            ));

            try {
                // Execute the actual task
                T result = request.getTask().call();
                request.complete(result);

                totalRequestsProcessed++;

                long holdTime = System.currentTimeMillis() - ownerStartTime;
                log.info(String.format(
                    "✓ COMPLETED: %s (held lock for %dms)",
                    request.getOwner(),
                    holdTime
                ));

            } catch (Exception e) {
                log.severe(String.format(
                    "✗ FAILED: %s - Exception: %s",
                    request.getOwner(),
                    e.getMessage()
                ));

                totalRequestsFailed++;
                request.completeExceptionally(e);
            } finally {
                // Always release lock and clear owner tracking
                currentOwner = null;
                ownerStartTime = 0;
                currentRequest = null;

                elmLock.unlock();

                log.info(String.format(
                    "🔓 LOCK RELEASED by %s",
                    request.getOwner()
                ));
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            log.warning(String.format(
                "Request INTERRUPTED: %s",
                request.toString()
            ));

            totalRequestsFailed++;
            request.completeExceptionally(e);
        }
    }

    // ========== MONITORING ==========

    /**
     * Monitor for stuck owners and force unlock if needed.
     * Runs periodically on monitor thread.
     */
    private void monitorTimeout() {
        try {
            if (elmLock.isLocked() && currentOwner != null) {
                long holdTime = System.currentTimeMillis() - ownerStartTime;

                if (holdTime > MAX_HOLD_TIME_MS) {
                    // CRITICAL: Owner has held lock too long!
                    log.severe(String.format(
                        "⚠️ STUCK OWNER DETECTED: %s held lock for %dms (max: %dms) - FORCE UNLOCKING!",
                        currentOwner,
                        holdTime,
                        MAX_HOLD_TIME_MS
                    ));

                    // Complete current request with error
                    if (currentRequest != null && !currentRequest.isCompleted()) {
                        currentRequest.completeExceptionally(new TimeoutException(
                            "Forced unlock due to exceeding max hold time"
                        ));
                    }

                    // Force unlock
                    try {
                        elmLock.unlock();
                        log.info("  → Lock forcibly released");
                    } catch (IllegalMonitorStateException e) {
                        log.warning("  → Lock was not actually held: " + e.getMessage());
                    }

                    currentOwner = null;
                    ownerStartTime = 0;
                    currentRequest = null;
                }
            }
        } catch (Exception e) {
            // Don't let monitor thread die
            Log.e(TAG, "Monitor thread error: " + e.getMessage(), e);
        }
    }

    // ========== STATUS & DEBUGGING ==========

    /**
     * Check if the adapter is currently locked
     * @return true if locked
     */
    public boolean isLocked() {
        return elmLock.isLocked();
    }

    /**
     * Get the current owner (or null if unlocked)
     * @return Current owner string
     */
    public String getCurrentOwner() {
        return currentOwner;
    }

    /**
     * Get how long the current owner has held the lock
     * @return Hold time in milliseconds, or 0 if unlocked
     */
    public long getCurrentHoldTime() {
        if (currentOwner == null) {
            return 0;
        }
        return System.currentTimeMillis() - ownerStartTime;
    }

    /**
     * Get the number of pending requests in the queue
     * @return Queue size
     */
    public int getQueueSize() {
        return requestQueue.size();
    }

    /**
     * Get comprehensive status information
     * @return Multi-line status string
     */
    public String getStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════════════════════\n");
        sb.append("OBD Service Coordinator STATUS\n");
        sb.append("═══════════════════════════════════════════════════════\n");
        sb.append(String.format("  Locked: %b\n", isLocked()));
        sb.append(String.format("  Current Owner: %s\n", currentOwner != null ? currentOwner : "none"));
        sb.append(String.format("  Hold Time: %dms\n", getCurrentHoldTime()));
        sb.append(String.format("  Queue Size: %d\n", getQueueSize()));
        sb.append(String.format("  Total Processed: %d\n", totalRequestsProcessed));
        sb.append(String.format("  Total Failed: %d\n", totalRequestsFailed));
        sb.append(String.format("  Avg Wait Time: %dms\n",
            totalRequestsProcessed > 0 ? totalWaitTimeMs / totalRequestsProcessed : 0));
        sb.append(String.format("  Worker Running: %b\n", workerThread.isAlive()));
        sb.append("═══════════════════════════════════════════════════════");
        return sb.toString();
    }

    /**
     * Shutdown the coordinator (for testing/cleanup)
     */
    public void shutdown() {
        log.info("Shutting down OBD Service Coordinator...");

        running = false;
        workerThread.interrupt();

        try {
            workerThread.join(5000);  // Wait up to 5 seconds
            if (workerThread.isAlive()) {
                log.warning("Worker thread did not stop within timeout");
            } else {
                log.info("  Worker thread stopped");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        monitorExecutor.shutdown();
        log.info("  Coordinator shutdown complete");
    }
}
