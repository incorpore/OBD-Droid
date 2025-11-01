package com.obddroid.features.trackmode.data;

import androidx.annotation.NonNull;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a complete track session with multiple laps
 */
public class TrackSession implements Serializable {
    private long id;
    private long trackId;
    private String trackName;
    private SessionType sessionType;
    private long startTime;
    private long endTime;
    private String vehicleName;
    private String driverName;
    private WeatherCondition weatherCondition;
    private float ambientTemp; // Celsius
    private float trackTemp; // Celsius

    // Session statistics
    private int totalLaps;
    private int validLaps;
    private long bestLapTime;
    private int bestLapNumber;
    private float maxSpeed;
    private float maxRpm;
    private double totalDistance;

    // Lap data
    private List<LapTime> laps;
    private LapTime currentLap;
    private LapTime bestLap;

    // Session status
    private SessionStatus status;
    private boolean isRecording;

    public enum SessionType {
        PRACTICE("Practice"),
        QUALIFYING("Qualifying"),
        RACE("Race"),
        TIME_ATTACK("Time Attack"),
        TEST("Test");

        private final String displayName;

        SessionType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum WeatherCondition {
        SUNNY("Sunny"),
        CLOUDY("Cloudy"),
        OVERCAST("Overcast"),
        LIGHT_RAIN("Light Rain"),
        HEAVY_RAIN("Heavy Rain"),
        WET("Wet Track"),
        DRY("Dry Track");

        private final String displayName;

        WeatherCondition(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum SessionStatus {
        NOT_STARTED,
        IN_PROGRESS,
        PAUSED,
        COMPLETED,
        CANCELLED
    }

    public TrackSession() {
        this.laps = new ArrayList<>();
        this.status = SessionStatus.NOT_STARTED;
        this.sessionType = SessionType.PRACTICE;
        this.weatherCondition = WeatherCondition.DRY;
        this.startTime = System.currentTimeMillis();
    }

    public TrackSession(long trackId, String trackName, SessionType type) {
        this();
        this.trackId = trackId;
        this.trackName = trackName;
        this.sessionType = type;
    }

    /**
     * Start the session
     */
    public void startSession() {
        this.startTime = System.currentTimeMillis();
        this.status = SessionStatus.IN_PROGRESS;
        this.isRecording = true;
    }

    /**
     * End the session
     */
    public void endSession() {
        this.endTime = System.currentTimeMillis();
        this.status = SessionStatus.COMPLETED;
        this.isRecording = false;
        calculateSessionStatistics();
    }

    /**
     * Start a new lap
     */
    public LapTime startNewLap() {
        if (currentLap != null && currentLap.getStatus() == LapTime.LapStatus.IN_PROGRESS) {
            // Complete the current lap first
            currentLap.setStatus(LapTime.LapStatus.DNF);
            currentLap.setValid(false);
            laps.add(currentLap);
        }

        int lapNumber = laps.size() + 1;
        currentLap = new LapTime(id, trackId, lapNumber);
        return currentLap;
    }

    /**
     * Complete the current lap
     */
    public void completeLap() {
        if (currentLap == null) return;

        currentLap.completeLap();
        laps.add(currentLap);

        // Check if this is the best lap
        if (currentLap.isValid()) {
            validLaps++;
            if (bestLap == null || currentLap.getLapTime() < bestLap.getLapTime()) {
                bestLap = currentLap;
                bestLap.setPurpleLap(true);
                bestLapTime = currentLap.getLapTime();
                bestLapNumber = currentLap.getLapNumber();

                // Remove purple flag from previous best
                for (LapTime lap : laps) {
                    if (lap != bestLap) {
                        lap.setPurpleLap(false);
                    }
                }
            }
        }

        totalLaps++;
        currentLap = null;
    }

    /**
     * Calculate session statistics
     */
    private void calculateSessionStatistics() {
        if (laps.isEmpty()) return;

        totalLaps = laps.size();
        validLaps = 0;
        maxSpeed = 0;
        maxRpm = 0;
        totalDistance = 0;

        for (LapTime lap : laps) {
            if (lap.isValid()) {
                validLaps++;
                if (lap.getMaxSpeed() > maxSpeed) maxSpeed = lap.getMaxSpeed();
                if (lap.getMaxRpm() > maxRpm) maxRpm = lap.getMaxRpm();
                totalDistance += lap.getTotalDistance();
            }
        }
    }

    /**
     * Get sorted list of valid laps (fastest first)
     */
    public List<LapTime> getSortedValidLaps() {
        List<LapTime> validLapList = new ArrayList<>();
        for (LapTime lap : laps) {
            if (lap.isValid()) {
                validLapList.add(lap);
            }
        }
        Collections.sort(validLapList);
        return validLapList;
    }

    /**
     * Get lap by lap number
     */
    public LapTime getLap(int lapNumber) {
        for (LapTime lap : laps) {
            if (lap.getLapNumber() == lapNumber) {
                return lap;
            }
        }
        return null;
    }

    /**
     * Get theoretical best lap (best sectors combined)
     */
    public long getTheoreticalBestLap() {
        if (laps.isEmpty()) return 0;

        // Find the maximum number of sectors
        int maxSectors = 0;
        for (LapTime lap : laps) {
            if (lap.isValid() && lap.getSectorTimes().size() > maxSectors) {
                maxSectors = lap.getSectorTimes().size();
            }
        }

        if (maxSectors == 0) return bestLapTime;

        // Find best time for each sector
        long theoreticalTime = 0;
        for (int i = 0; i < maxSectors; i++) {
            long bestSectorTime = Long.MAX_VALUE;
            for (LapTime lap : laps) {
                if (lap.isValid() && i < lap.getSectorTimes().size()) {
                    long sectorTime = lap.getSectorTime(i);
                    if (sectorTime > 0 && sectorTime < bestSectorTime) {
                        bestSectorTime = sectorTime;
                    }
                }
            }
            if (bestSectorTime != Long.MAX_VALUE) {
                theoreticalTime += bestSectorTime;
            }
        }

        return theoreticalTime > 0 ? theoreticalTime : bestLapTime;
    }

    /**
     * Get session duration
     */
    public long getSessionDuration() {
        if (endTime > 0) {
            return endTime - startTime;
        } else if (status == SessionStatus.IN_PROGRESS) {
            return System.currentTimeMillis() - startTime;
        }
        return 0;
    }

    /**
     * Get formatted session duration
     */
    public String getFormattedDuration() {
        long duration = getSessionDuration();
        long hours = duration / 3600000;
        long minutes = (duration % 3600000) / 60000;
        long seconds = (duration % 60000) / 1000;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }

    // Getters and Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getTrackId() { return trackId; }
    public void setTrackId(long trackId) { this.trackId = trackId; }

    public String getTrackName() { return trackName; }
    public void setTrackName(String trackName) { this.trackName = trackName; }

    public SessionType getSessionType() { return sessionType; }
    public void setSessionType(SessionType sessionType) { this.sessionType = sessionType; }

    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }

    public long getEndTime() { return endTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }

    public String getVehicleName() { return vehicleName; }
    public void setVehicleName(String vehicleName) { this.vehicleName = vehicleName; }

    public String getDriverName() { return driverName; }
    public void setDriverName(String driverName) { this.driverName = driverName; }

    public WeatherCondition getWeatherCondition() { return weatherCondition; }
    public void setWeatherCondition(WeatherCondition weatherCondition) { this.weatherCondition = weatherCondition; }

    public float getAmbientTemp() { return ambientTemp; }
    public void setAmbientTemp(float ambientTemp) { this.ambientTemp = ambientTemp; }

    public float getTrackTemp() { return trackTemp; }
    public void setTrackTemp(float trackTemp) { this.trackTemp = trackTemp; }

    public int getTotalLaps() { return totalLaps; }
    public void setTotalLaps(int totalLaps) { this.totalLaps = totalLaps; }

    public int getValidLaps() { return validLaps; }
    public void setValidLaps(int validLaps) { this.validLaps = validLaps; }

    public long getBestLapTime() { return bestLapTime; }
    public void setBestLapTime(long bestLapTime) { this.bestLapTime = bestLapTime; }

    public int getBestLapNumber() { return bestLapNumber; }
    public void setBestLapNumber(int bestLapNumber) { this.bestLapNumber = bestLapNumber; }

    public float getMaxSpeed() { return maxSpeed; }
    public void setMaxSpeed(float maxSpeed) { this.maxSpeed = maxSpeed; }

    public float getMaxRpm() { return maxRpm; }
    public void setMaxRpm(float maxRpm) { this.maxRpm = maxRpm; }

    public double getTotalDistance() { return totalDistance; }
    public void setTotalDistance(double totalDistance) { this.totalDistance = totalDistance; }

    public List<LapTime> getLaps() { return laps; }
    public void setLaps(List<LapTime> laps) { this.laps = laps; }

    public LapTime getCurrentLap() { return currentLap; }
    public void setCurrentLap(LapTime currentLap) { this.currentLap = currentLap; }

    public LapTime getBestLap() { return bestLap; }
    public void setBestLap(LapTime bestLap) { this.bestLap = bestLap; }

    public SessionStatus getStatus() { return status; }
    public void setStatus(SessionStatus status) { this.status = status; }

    public boolean isRecording() { return isRecording; }
    public void setRecording(boolean recording) { isRecording = recording; }
}