package com.obddroid.features.trackmode.data;

import android.location.Location;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a racing track with start/finish line and sector boundaries
 */
public class Track implements Serializable {
    private long id;
    private String name;
    private String country;
    private double length; // Track length in meters
    private TrackPoint finishLine;
    private TrackPoint finishLineEnd;
    private List<TrackPoint> trackBoundary;
    private List<SectorLine> sectors;
    private long bestLapTime; // in milliseconds
    private String bestLapHolder;
    private long createdAt;
    private long lastUsedAt;
    private boolean isUserCreated;

    // Track configuration
    private double finishLineRadius = 15.0; // Detection radius in meters
    private double trackBoundaryRadius = 50.0; // Off-track detection radius

    public Track() {
        this.trackBoundary = new ArrayList<>();
        this.sectors = new ArrayList<>();
        this.createdAt = System.currentTimeMillis();
    }

    public Track(String name, String country) {
        this();
        this.name = name;
        this.country = country;
    }

    /**
     * Check if a location is near the finish line
     */
    public boolean isNearFinishLine(Location location) {
        if (finishLine == null) return false;

        float[] results = new float[1];
        Location.distanceBetween(
            location.getLatitude(), location.getLongitude(),
            finishLine.latitude, finishLine.longitude,
            results
        );

        return results[0] <= finishLineRadius;
    }

    /**
     * Check if the path between two locations crosses the finish line
     */
    public boolean crossesFinishLine(Location prev, Location current) {
        if (finishLine == null || finishLineEnd == null) return false;

        return lineSegmentsIntersect(
            prev.getLatitude(), prev.getLongitude(),
            current.getLatitude(), current.getLongitude(),
            finishLine.latitude, finishLine.longitude,
            finishLineEnd.latitude, finishLineEnd.longitude
        );
    }

    /**
     * Get the sector number for a given location (0-based)
     */
    public int getSectorForLocation(Location location) {
        if (sectors.isEmpty()) return 0;

        for (int i = 0; i < sectors.size(); i++) {
            SectorLine sector = sectors.get(i);
            if (!hasPassedSectorLine(location, sector)) {
                return i;
            }
        }
        return sectors.size(); // Last sector
    }

    private boolean hasPassedSectorLine(Location location, SectorLine sector) {
        float[] results = new float[1];
        Location.distanceBetween(
            location.getLatitude(), location.getLongitude(),
            sector.startPoint.latitude, sector.startPoint.longitude,
            results
        );

        // Simple check - more sophisticated implementation would track crossing direction
        return results[0] > sector.radius;
    }

    /**
     * Check if two line segments intersect
     */
    private boolean lineSegmentsIntersect(
        double x1, double y1, double x2, double y2,
        double x3, double y3, double x4, double y4) {

        double denom = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4);
        if (Math.abs(denom) < 0.0000001) return false; // Parallel lines

        double t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / denom;
        double u = -((x1 - x2) * (y1 - y3) - (y1 - y2) * (x1 - x3)) / denom;

        return t >= 0 && t <= 1 && u >= 0 && u <= 1;
    }

    // Data class for track points
    public static class TrackPoint implements Serializable {
        public double latitude;
        public double longitude;
        public double elevation;
        public float speed; // Optional reference speed at this point

        public TrackPoint(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public TrackPoint(double latitude, double longitude, double elevation) {
            this(latitude, longitude);
            this.elevation = elevation;
        }
    }

    // Sector boundary definition
    public static class SectorLine implements Serializable {
        public String name;
        public TrackPoint startPoint;
        public TrackPoint endPoint;
        public double radius = 15.0; // Detection radius

        public SectorLine(String name, TrackPoint start, TrackPoint end) {
            this.name = name;
            this.startPoint = start;
            this.endPoint = end;
        }
    }

    // Getters and Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public double getLength() { return length; }
    public void setLength(double length) { this.length = length; }

    public TrackPoint getFinishLine() { return finishLine; }
    public void setFinishLine(TrackPoint finishLine) { this.finishLine = finishLine; }

    public TrackPoint getFinishLineEnd() { return finishLineEnd; }
    public void setFinishLineEnd(TrackPoint finishLineEnd) { this.finishLineEnd = finishLineEnd; }

    public List<TrackPoint> getTrackBoundary() { return trackBoundary; }
    public void setTrackBoundary(List<TrackPoint> trackBoundary) { this.trackBoundary = trackBoundary; }

    public List<SectorLine> getSectors() { return sectors; }
    public void setSectors(List<SectorLine> sectors) { this.sectors = sectors; }

    public long getBestLapTime() { return bestLapTime; }
    public void setBestLapTime(long bestLapTime) { this.bestLapTime = bestLapTime; }

    public String getBestLapHolder() { return bestLapHolder; }
    public void setBestLapHolder(String bestLapHolder) { this.bestLapHolder = bestLapHolder; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(long lastUsedAt) { this.lastUsedAt = lastUsedAt; }

    public boolean isUserCreated() { return isUserCreated; }
    public void setUserCreated(boolean userCreated) { isUserCreated = userCreated; }

    public double getFinishLineRadius() { return finishLineRadius; }
    public void setFinishLineRadius(double finishLineRadius) { this.finishLineRadius = finishLineRadius; }

    public double getTrackBoundaryRadius() { return trackBoundaryRadius; }
    public void setTrackBoundaryRadius(double trackBoundaryRadius) { this.trackBoundaryRadius = trackBoundaryRadius; }
}