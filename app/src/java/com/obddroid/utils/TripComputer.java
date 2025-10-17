package com.obddroid.utils;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/**
 * Trip Computer helper class
 * Manages trip data (distance, duration, fuel consumption, etc.) with persistent storage
 */
public class TripComputer {

    private static final String PREFS_NAME = "TripComputerPrefs";
    private static final float DEFAULT_FUEL_PRICE = 3.50f; // Default $/gallon

    private final Context context;
    private final SharedPreferences prefs;
    private final String id;
    private String name;

    // Trip metrics
    private float distanceMiles = 0f;
    private long durationSeconds = 0;
    private float fuelUsedGallons = 0f;
    private long lastUpdateTime = 0;
    private long createdTime = 0;

    /**
     * Constructor for new trip with ID and name
     */
    public TripComputer(Context context, String id, String name) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.id = id;
        this.name = name;
        this.createdTime = System.currentTimeMillis();
    }

    /**
     * Legacy constructor for backwards compatibility (Trip A / Trip B)
     */
    public TripComputer(Context context, String tripId) {
        this(context, tripId, tripId.equals("tripA") ? "Trip A" : "Trip B");
        loadFromPreferences();
    }

    /**
     * Update trip with current OBD data
     * @param speedMph Current speed in mph
     * @param fuelFlowGalH Current fuel flow rate in gal/h
     */
    public void update(float speedMph, float fuelFlowGalH) {
        long currentTime = System.currentTimeMillis();

        // Initialize on first update
        if (lastUpdateTime == 0) {
            lastUpdateTime = currentTime;
            return;
        }

        // Calculate time elapsed since last update (in seconds)
        float deltaSeconds = (currentTime - lastUpdateTime) / 1000f;
        lastUpdateTime = currentTime;

        // Only accumulate if vehicle is moving
        if (speedMph > 0.5f) {
            // Update distance: distance += speed * time
            distanceMiles += speedMph * (deltaSeconds / 3600f); // Convert seconds to hours

            // Update duration (only when moving)
            durationSeconds += (long) deltaSeconds;

            // Update fuel used: fuel += flow_rate * time
            fuelUsedGallons += fuelFlowGalH * (deltaSeconds / 3600f); // Convert seconds to hours

            saveToPreferences();
        }
    }

    /**
     * Reset this trip to zero
     */
    public void reset() {
        distanceMiles = 0f;
        durationSeconds = 0;
        fuelUsedGallons = 0f;
        lastUpdateTime = 0;
        saveToPreferences();
    }

    /**
     * Get distance traveled
     * @return Distance in miles
     */
    public float getDistanceMiles() {
        return distanceMiles;
    }

    /**
     * Get trip duration
     * @return Duration in seconds
     */
    public long getDurationSeconds() {
        return durationSeconds;
    }

    /**
     * Get formatted duration string (H:MM:SS)
     * @return Formatted duration
     */
    public String getFormattedDuration() {
        long hours = durationSeconds / 3600;
        long minutes = (durationSeconds % 3600) / 60;
        long seconds = durationSeconds % 60;
        return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
    }

    /**
     * Get fuel used
     * @return Fuel in gallons
     */
    public float getFuelUsedGallons() {
        return fuelUsedGallons;
    }

    /**
     * Get average speed
     * @return Average speed in mph
     */
    public float getAverageSpeedMph() {
        if (durationSeconds == 0) return 0f;
        return distanceMiles / (durationSeconds / 3600f);
    }

    /**
     * Get average MPG
     * @return Average MPG
     */
    public float getAverageMPG() {
        if (fuelUsedGallons < 0.01f) return 0f;
        return distanceMiles / fuelUsedGallons;
    }

    /**
     * Get trip cost
     * @return Cost in dollars
     */
    public float getTripCost() {
        float fuelPrice = prefs.getFloat("fuel_price", DEFAULT_FUEL_PRICE);
        return fuelUsedGallons * fuelPrice;
    }

    /**
     * Set fuel price for cost calculation
     * @param pricePerGallon Price in $/gallon
     */
    public static void setFuelPrice(Context context, float pricePerGallon) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putFloat("fuel_price", pricePerGallon).apply();
    }

    /**
     * Get current fuel price
     * @return Price in $/gallon
     */
    public static float getFuelPrice(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getFloat("fuel_price", DEFAULT_FUEL_PRICE);
    }

    /**
     * Get trip ID
     */
    public String getId() {
        return id;
    }

    /**
     * Get trip name
     */
    public String getName() {
        return name;
    }

    /**
     * Set trip name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Get creation time
     */
    public long getCreatedTime() {
        return createdTime;
    }

    /**
     * Convert trip to JSON
     */
    public JSONObject toJSON() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("name", name);
        json.put("distance", distanceMiles);
        json.put("duration", durationSeconds);
        json.put("fuelUsed", fuelUsedGallons);
        json.put("created", createdTime);
        json.put("lastUpdate", lastUpdateTime);
        return json;
    }

    /**
     * Create trip from JSON
     */
    public static TripComputer fromJSON(Context context, JSONObject json) throws JSONException {
        String id = json.getString("id");
        String name = json.getString("name");
        TripComputer trip = new TripComputer(context, id, name);
        trip.distanceMiles = (float) json.getDouble("distance");
        trip.durationSeconds = json.getLong("duration");
        trip.fuelUsedGallons = (float) json.getDouble("fuelUsed");
        trip.createdTime = json.getLong("created");
        trip.lastUpdateTime = json.getLong("lastUpdate");
        return trip;
    }

    private void loadFromPreferences() {
        String tripPrefix = id + "_";
        distanceMiles = prefs.getFloat(tripPrefix + "distance", 0f);
        durationSeconds = prefs.getLong(tripPrefix + "duration", 0);
        fuelUsedGallons = prefs.getFloat(tripPrefix + "fuel", 0f);
        lastUpdateTime = prefs.getLong(tripPrefix + "lastUpdate", 0);
    }

    private void saveToPreferences() {
        String tripPrefix = id + "_";
        prefs.edit()
            .putFloat(tripPrefix + "distance", distanceMiles)
            .putLong(tripPrefix + "duration", durationSeconds)
            .putFloat(tripPrefix + "fuel", fuelUsedGallons)
            .putLong(tripPrefix + "lastUpdate", lastUpdateTime)
            .apply();
    }
}
