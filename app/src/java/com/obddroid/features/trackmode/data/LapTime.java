package com.obddroid.features.trackmode.data;

import android.location.Location;
import androidx.annotation.NonNull;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single lap with timing and telemetry data
 */
public class LapTime implements Serializable, Comparable<LapTime> {
    private long id;
    private long sessionId;
    private long trackId;
    private int lapNumber;
    private long lapTime; // Total lap time in milliseconds
    private long startTime; // Timestamp when lap started
    private long endTime; // Timestamp when lap ended
    private List<Long> sectorTimes; // Sector split times in milliseconds
    private boolean isValid; // False if lap had off-track or penalties
    private boolean isPurpleLap; // True if this is the best lap of session
    private LapStatus status;

    // Telemetry summary
    private float maxSpeed; // km/h
    private float avgSpeed; // km/h
    private float maxRpm;
    private float maxGForce;
    private float maxBraking; // Max deceleration
    private float maxThrottle; // Max throttle percentage
    private double totalDistance; // Lap distance in meters

    // Detailed telemetry data points
    private List<TelemetryPoint> telemetryPoints;

    // Delta to best lap (for predictive timer)
    private long deltaTime; // Difference to best lap in milliseconds

    public enum LapStatus {
        IN_PROGRESS,
        COMPLETED,
        INVALID,
        DNF // Did not finish
    }

    public LapTime() {
        this.sectorTimes = new ArrayList<>();
        this.telemetryPoints = new ArrayList<>();
        this.status = LapStatus.IN_PROGRESS;
        this.isValid = true;
    }

    public LapTime(long sessionId, long trackId, int lapNumber) {
        this();
        this.sessionId = sessionId;
        this.trackId = trackId;
        this.lapNumber = lapNumber;
        this.startTime = System.currentTimeMillis();
    }

    /**
     * Complete the lap and calculate final time
     */
    public void completeLap() {
        this.endTime = System.currentTimeMillis();
        this.lapTime = endTime - startTime;
        this.status = isValid ? LapStatus.COMPLETED : LapStatus.INVALID;
        calculateTelemetrySummary();
    }

    /**
     * Add a telemetry data point
     */
    public void addTelemetryPoint(TelemetryPoint point) {
        telemetryPoints.add(point);

        // Update max values in real-time
        if (point.speed > maxSpeed) maxSpeed = point.speed;
        if (point.rpm > maxRpm) maxRpm = point.rpm;
        if (Math.abs(point.gForceLateral) > maxGForce) maxGForce = Math.abs(point.gForceLateral);
        if (Math.abs(point.gForceLongitudinal) > maxGForce) maxGForce = Math.abs(point.gForceLongitudinal);
        if (point.throttlePosition > maxThrottle) maxThrottle = point.throttlePosition;
        if (point.gForceLongitudinal < -maxBraking) maxBraking = -point.gForceLongitudinal;
    }

    /**
     * Add sector split time
     */
    public void addSectorTime(long sectorTime) {
        sectorTimes.add(sectorTime);
    }

    /**
     * Get sector time for a specific sector (0-based)
     */
    public long getSectorTime(int sector) {
        if (sector < 0 || sector >= sectorTimes.size()) return 0;

        if (sector == 0) {
            return sectorTimes.get(0);
        } else {
            return sectorTimes.get(sector) - sectorTimes.get(sector - 1);
        }
    }

    /**
     * Calculate telemetry summary statistics
     */
    private void calculateTelemetrySummary() {
        if (telemetryPoints.isEmpty()) return;

        double totalSpeed = 0;
        double totalDist = 0;
        Location prevLocation = null;

        for (TelemetryPoint point : telemetryPoints) {
            totalSpeed += point.speed;

            if (prevLocation != null) {
                float[] results = new float[1];
                Location.distanceBetween(
                    prevLocation.getLatitude(), prevLocation.getLongitude(),
                    point.latitude, point.longitude,
                    results
                );
                totalDist += results[0];
            }

            prevLocation = new Location("");
            prevLocation.setLatitude(point.latitude);
            prevLocation.setLongitude(point.longitude);
        }

        avgSpeed = (float)(totalSpeed / telemetryPoints.size());
        totalDistance = totalDist;
    }

    /**
     * Get telemetry at a specific lap completion percentage
     */
    public TelemetryPoint getTelemetryAtProgress(double progressPercent) {
        if (telemetryPoints.isEmpty()) return null;

        int index = (int)(telemetryPoints.size() * progressPercent / 100.0);
        index = Math.min(Math.max(0, index), telemetryPoints.size() - 1);

        return telemetryPoints.get(index);
    }

    /**
     * Get telemetry at a specific time offset from lap start
     */
    public TelemetryPoint getTelemetryAtTime(long timeOffset) {
        for (TelemetryPoint point : telemetryPoints) {
            if (point.timestamp - startTime >= timeOffset) {
                return point;
            }
        }
        return telemetryPoints.isEmpty() ? null : telemetryPoints.get(telemetryPoints.size() - 1);
    }

    /**
     * Format lap time as string (MM:SS.mmm)
     */
    public String getFormattedLapTime() {
        return formatTime(lapTime);
    }

    public static String formatTime(long milliseconds) {
        if (milliseconds == 0) return "--:--.---";

        long minutes = milliseconds / 60000;
        long seconds = (milliseconds % 60000) / 1000;
        long millis = milliseconds % 1000;

        return String.format("%02d:%02d.%03d", minutes, seconds, millis);
    }

    /**
     * Telemetry data point
     */
    public static class TelemetryPoint implements Serializable {
        public long timestamp;
        public double latitude;
        public double longitude;
        public double altitude;
        public float speed; // km/h
        public float bearing; // degrees
        public float accuracy; // meters

        // OBD Data
        public float rpm;
        public float throttlePosition; // percentage
        public float engineLoad; // percentage
        public float coolantTemp; // celsius
        public float oilTemp; // celsius
        public float intakeTemp; // celsius
        public int gear; // calculated or from OBD

        // Motion sensors
        public float gForceLateral; // G
        public float gForceLongitudinal; // G
        public float gForceVertical; // G
        public float yaw; // degrees/sec
        public float pitch; // degrees
        public float roll; // degrees

        public TelemetryPoint(long timestamp, double lat, double lon) {
            this.timestamp = timestamp;
            this.latitude = lat;
            this.longitude = lon;
        }
    }

    @Override
    public int compareTo(@NonNull LapTime other) {
        // Sort by lap time (fastest first)
        if (!this.isValid && !other.isValid) return 0;
        if (!this.isValid) return 1;
        if (!other.isValid) return -1;

        return Long.compare(this.lapTime, other.lapTime);
    }

    // Getters and Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getSessionId() { return sessionId; }
    public void setSessionId(long sessionId) { this.sessionId = sessionId; }

    public long getTrackId() { return trackId; }
    public void setTrackId(long trackId) { this.trackId = trackId; }

    public int getLapNumber() { return lapNumber; }
    public void setLapNumber(int lapNumber) { this.lapNumber = lapNumber; }

    public long getLapTime() { return lapTime; }
    public void setLapTime(long lapTime) { this.lapTime = lapTime; }

    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }

    public long getEndTime() { return endTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }

    public List<Long> getSectorTimes() { return sectorTimes; }
    public void setSectorTimes(List<Long> sectorTimes) { this.sectorTimes = sectorTimes; }

    public boolean isValid() { return isValid; }
    public void setValid(boolean valid) { isValid = valid; }

    public boolean isPurpleLap() { return isPurpleLap; }
    public void setPurpleLap(boolean purpleLap) { isPurpleLap = purpleLap; }

    public LapStatus getStatus() { return status; }
    public void setStatus(LapStatus status) { this.status = status; }

    public float getMaxSpeed() { return maxSpeed; }
    public void setMaxSpeed(float maxSpeed) { this.maxSpeed = maxSpeed; }

    public float getAvgSpeed() { return avgSpeed; }
    public void setAvgSpeed(float avgSpeed) { this.avgSpeed = avgSpeed; }

    public float getMaxRpm() { return maxRpm; }
    public void setMaxRpm(float maxRpm) { this.maxRpm = maxRpm; }

    public float getMaxGForce() { return maxGForce; }
    public void setMaxGForce(float maxGForce) { this.maxGForce = maxGForce; }

    public float getMaxBraking() { return maxBraking; }
    public void setMaxBraking(float maxBraking) { this.maxBraking = maxBraking; }

    public float getMaxThrottle() { return maxThrottle; }
    public void setMaxThrottle(float maxThrottle) { this.maxThrottle = maxThrottle; }

    public double getTotalDistance() { return totalDistance; }
    public void setTotalDistance(double totalDistance) { this.totalDistance = totalDistance; }

    public List<TelemetryPoint> getTelemetryPoints() { return telemetryPoints; }
    public void setTelemetryPoints(List<TelemetryPoint> telemetryPoints) { this.telemetryPoints = telemetryPoints; }

    public long getDeltaTime() { return deltaTime; }
    public void setDeltaTime(long deltaTime) { this.deltaTime = deltaTime; }
}