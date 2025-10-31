package com.obddroid.services;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents a coordinated request for exclusive ELM327 adapter access.
 *
 * This class wraps a task that needs exclusive access to the OBD adapter,
 * along with metadata for prioritization, timeout tracking, and result delivery.
 *
 * THREAD SAFETY:
 * - Instances are immutable after construction
 * - CompletableFuture handles thread-safe result delivery
 * - AtomicLong ensures unique request IDs across threads
 * - Comparable for priority queue ordering
 *
 * @param <T> Return type of the task
 */
public class CoordinatedRequest<T> implements Comparable<CoordinatedRequest<?>> {

    /** Unique identifier for debugging */
    private final long requestId;

    /** Human-readable owner name (activity/service class name) */
    private final String owner;

    /** Priority level for queue ordering */
    private final RequestPriority priority;

    /** The actual work to be performed */
    private final Callable<T> task;

    /** Future for result delivery */
    private final CompletableFuture<T> result;

    /** Timestamp when request was submitted (for timeout tracking) */
    private final long submitTime;

    /** Thread-safe counter for generating unique request IDs */
    private static final AtomicLong nextRequestId = new AtomicLong(1);

    /**
     * Create a new coordinated request
     *
     * @param owner Caller identification (e.g., "LiveDataActivity", "FaultCodeService")
     * @param priority Priority level for queue ordering
     * @param task The actual work to perform with exclusive adapter access
     */
    public CoordinatedRequest(String owner, RequestPriority priority, Callable<T> task) {
        this.requestId = nextRequestId.getAndIncrement();  // Thread-safe ID generation
        this.owner = owner;
        this.priority = priority;
        this.task = task;
        this.result = new CompletableFuture<>();
        this.submitTime = System.currentTimeMillis();
    }

    /**
     * Get the unique request ID
     * @return Request ID
     */
    public long getRequestId() {
        return requestId;
    }

    /**
     * Get the owner/caller name
     * @return Owner string (e.g., "LiveDataActivity")
     */
    public String getOwner() {
        return owner;
    }

    /**
     * Get the priority level
     * @return Priority
     */
    public RequestPriority getPriority() {
        return priority;
    }

    /**
     * Get the task to execute
     * @return Callable task
     */
    public Callable<T> getTask() {
        return task;
    }

    /**
     * Get the result future
     * @return CompletableFuture for result delivery
     */
    public CompletableFuture<T> getResult() {
        return result;
    }

    /**
     * Get the submission timestamp
     * @return Timestamp in milliseconds
     */
    public long getSubmitTime() {
        return submitTime;
    }

    /**
     * Calculate how long this request has been waiting
     * @return Wait time in milliseconds
     */
    public long getWaitTime() {
        return System.currentTimeMillis() - submitTime;
    }

    /**
     * Compare with another request for priority queue ordering.
     *
     * ORDERING RULES:
     * 1. Higher priority comes first (CRITICAL > HIGH > NORMAL > LOW)
     * 2. For same priority, FIFO (earlier submission time first)
     *
     * @param other Other request to compare
     * @return Negative if this comes first, positive if other comes first, 0 if equal
     */
    @Override
    public int compareTo(CoordinatedRequest<?> other) {
        // Primary sort: Priority (descending - higher priority first)
        int priorityCompare = Integer.compare(
            other.priority.getValue(),  // Note: reversed for descending order
            this.priority.getValue()
        );

        if (priorityCompare != 0) {
            return priorityCompare;
        }

        // Secondary sort: Submission time (ascending - FIFO)
        return Long.compare(this.submitTime, other.submitTime);
    }

    /**
     * Mark this request as completed successfully
     * @param value Result value
     */
    public void complete(T value) {
        result.complete(value);
    }

    /**
     * Mark this request as failed
     * @param throwable Exception that caused failure
     */
    public void completeExceptionally(Throwable throwable) {
        result.completeExceptionally(throwable);
    }

    /**
     * Check if this request has been completed (success or failure)
     * @return true if completed
     */
    public boolean isCompleted() {
        return result.isDone();
    }

    /**
     * Check if this request timed out waiting for lock acquisition
     * @return true if wait time exceeds priority timeout
     */
    public boolean isTimedOut() {
        return getWaitTime() > priority.getTimeout();
    }

    @Override
    public String toString() {
        return String.format(
            "Request#%d[owner=%s, priority=%s, waitTime=%dms, completed=%b]",
            requestId,
            owner,
            priority.name(),
            getWaitTime(),
            isCompleted()
        );
    }

    /**
     * Get detailed debug information about this request
     * @return Multi-line debug string
     */
    public String toDebugString() {
        return String.format(
            "CoordinatedRequest #%d\n" +
            "  Owner: %s\n" +
            "  Priority: %s (%d)\n" +
            "  Submitted: %d (%d ms ago)\n" +
            "  Timeout: %d ms\n" +
            "  Completed: %b\n" +
            "  Timed out: %b",
            requestId,
            owner,
            priority.name(),
            priority.getValue(),
            submitTime,
            getWaitTime(),
            priority.getTimeout(),
            isCompleted(),
            isTimedOut()
        );
    }
}
