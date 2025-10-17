package com.obddroid.utils;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Manages multiple trip computers with custom names
 */
public class TripManager {
    private static final Logger log = Logger.getLogger(TripManager.class.getName());
    private static final String PREFS_NAME = "trip_manager";
    private static final String KEY_TRIPS = "trips";
    private static final String KEY_ACTIVE_TRIP_ID = "active_trip_id";

    private final Context context;
    private final SharedPreferences prefs;
    private final List<TripComputer> trips;
    private String activeTripId;

    public TripManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.trips = new ArrayList<>();
        loadTrips();
    }

    /**
     * Load all trips from SharedPreferences
     */
    private void loadTrips() {
        trips.clear();
        String tripsJson = prefs.getString(KEY_TRIPS, null);
        activeTripId = prefs.getString(KEY_ACTIVE_TRIP_ID, null);

        if (tripsJson != null) {
            try {
                JSONArray array = new JSONArray(tripsJson);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject tripJson = array.getJSONObject(i);
                    TripComputer trip = TripComputer.fromJSON(context, tripJson);
                    trips.add(trip);
                }
                log.info("Loaded " + trips.size() + " trips from storage");
            } catch (JSONException e) {
                log.warning("Failed to load trips: " + e.getMessage());
            }
        }

        // If no trips exist, create default "Current Trip"
        if (trips.isEmpty()) {
            createTrip("Current Trip");
        }

        // Ensure active trip ID is valid
        if (activeTripId == null || getTripById(activeTripId) == null) {
            if (!trips.isEmpty()) {
                activeTripId = trips.get(0).getId();
            }
        }
    }

    /**
     * Save all trips to SharedPreferences
     */
    private void saveTrips() {
        try {
            JSONArray array = new JSONArray();
            for (TripComputer trip : trips) {
                array.put(trip.toJSON());
            }
            prefs.edit()
                    .putString(KEY_TRIPS, array.toString())
                    .putString(KEY_ACTIVE_TRIP_ID, activeTripId)
                    .apply();
            log.info("Saved " + trips.size() + " trips to storage");
        } catch (JSONException e) {
            log.warning("Failed to save trips: " + e.getMessage());
        }
    }

    /**
     * Create a new trip with the given name
     */
    public TripComputer createTrip(String name) {
        String id = UUID.randomUUID().toString();
        TripComputer trip = new TripComputer(context, id, name);
        trips.add(trip);

        // Set as active trip if it's the only one
        if (trips.size() == 1) {
            activeTripId = id;
        }

        saveTrips();
        log.info("Created new trip: " + name + " (ID: " + id + ")");
        return trip;
    }

    /**
     * Delete a trip by ID
     */
    public boolean deleteTrip(String tripId) {
        TripComputer trip = getTripById(tripId);
        if (trip == null) {
            return false;
        }

        // Don't allow deleting the last trip
        if (trips.size() <= 1) {
            log.warning("Cannot delete the last trip");
            return false;
        }

        trips.remove(trip);

        // If deleting active trip, switch to first available trip
        if (tripId.equals(activeTripId)) {
            activeTripId = trips.get(0).getId();
        }

        saveTrips();
        log.info("Deleted trip: " + trip.getName());
        return true;
    }

    /**
     * Rename a trip
     */
    public boolean renameTrip(String tripId, String newName) {
        TripComputer trip = getTripById(tripId);
        if (trip == null) {
            return false;
        }

        trip.setName(newName);
        saveTrips();
        log.info("Renamed trip to: " + newName);
        return true;
    }

    /**
     * Reset a trip's data
     */
    public boolean resetTrip(String tripId) {
        TripComputer trip = getTripById(tripId);
        if (trip == null) {
            return false;
        }

        trip.reset();
        saveTrips();
        log.info("Reset trip: " + trip.getName());
        return true;
    }

    /**
     * Get trip by ID
     */
    public TripComputer getTripById(String tripId) {
        for (TripComputer trip : trips) {
            if (trip.getId().equals(tripId)) {
                return trip;
            }
        }
        return null;
    }

    /**
     * Get all trips
     */
    public List<TripComputer> getAllTrips() {
        return new ArrayList<>(trips);
    }

    /**
     * Get active trip
     */
    public TripComputer getActiveTrip() {
        return getTripById(activeTripId);
    }

    /**
     * Set active trip
     */
    public void setActiveTrip(String tripId) {
        if (getTripById(tripId) != null) {
            activeTripId = tripId;
            prefs.edit().putString(KEY_ACTIVE_TRIP_ID, activeTripId).apply();
            log.info("Set active trip to: " + getTripById(tripId).getName());
        }
    }

    /**
     * Update all trips with current speed and fuel flow
     */
    public void updateActiveTrip(float currentSpeedMph, float fuelFlowGalH) {
        TripComputer activeTrip = getActiveTrip();
        if (activeTrip != null) {
            activeTrip.update(currentSpeedMph, fuelFlowGalH);
            saveTrips();
        }
    }

    /**
     * Export all trips to CSV format
     */
    public String exportToCSV() {
        StringBuilder csv = new StringBuilder();
        csv.append("Trip Name,Distance (mi),Duration,Avg Speed (mph),Avg MPG,Fuel Used (gal),Cost ($)\n");

        for (TripComputer trip : trips) {
            csv.append(escapeCsv(trip.getName())).append(",");
            csv.append(String.format("%.2f", trip.getDistanceMiles())).append(",");
            csv.append(trip.getFormattedDuration()).append(",");
            csv.append(String.format("%.1f", trip.getAverageSpeedMph())).append(",");
            csv.append(String.format("%.1f", trip.getAverageMPG())).append(",");
            csv.append(String.format("%.2f", trip.getFuelUsedGallons())).append(",");
            csv.append(String.format("%.2f", trip.getTripCost())).append("\n");
        }

        return csv.toString();
    }

    /**
     * Export all trips to JSON format
     */
    public String exportToJSON() {
        try {
            JSONObject export = new JSONObject();
            export.put("exportDate", System.currentTimeMillis());
            export.put("version", "1.0");

            JSONArray tripsArray = new JSONArray();
            for (TripComputer trip : trips) {
                tripsArray.put(trip.toJSON());
            }
            export.put("trips", tripsArray);

            return export.toString(2); // Pretty print with 2 space indent
        } catch (JSONException e) {
            log.warning("Failed to export to JSON: " + e.getMessage());
            return "{}";
        }
    }

    /**
     * Escape CSV values
     */
    private String escapeCsv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Get trip count
     */
    public int getTripCount() {
        return trips.size();
    }

    /**
     * Get total distance across all trips
     */
    public double getTotalDistance() {
        double total = 0;
        for (TripComputer trip : trips) {
            total += trip.getDistanceMiles();
        }
        return total;
    }

    /**
     * Get total fuel used across all trips
     */
    public double getTotalFuelUsed() {
        double total = 0;
        for (TripComputer trip : trips) {
            total += trip.getFuelUsedGallons();
        }
        return total;
    }

    /**
     * Get suggested trip names
     */
    public static String[] getSuggestedNames() {
        return new String[]{
                "Current Trip",
                "Work Commute",
                "Road Trip",
                "City Drive",
                "Highway Drive",
                "Morning Commute",
                "Evening Commute",
                "Weekend Trip",
                "Vacation",
                "Errands"
        };
    }
}
