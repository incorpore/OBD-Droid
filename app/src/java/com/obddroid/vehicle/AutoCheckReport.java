package com.obddroid.vehicle;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * AutoCheck Vehicle History Report Data Model
 * Corresponds to the AutoCheckReport interface from the API
 */
public class AutoCheckReport {
    private String vin;
    private String year;
    private String make;
    private String model;
    private String style;
    private String engine;
    private String country;
    private Integer owners;
    private Integer lastOdometer;
    private Date lastOdometerDate;
    private Integer score;
    private ScoreRange scoreRange;
    private String titleBrand;
    private String accidentDamage;
    private Boolean totalLoss;
    private Boolean structuralDamage;
    private Boolean airbagDeployed;
    private Boolean odometerRollback;
    private String recalls;
    private Integer serviceRecords;
    private List<HistoryEvent> historyEvents;

    // Nested classes
    public static class ScoreRange {
        public int low;
        public int high;

        public ScoreRange(int low, int high) {
            this.low = low;
            this.high = high;
        }
    }

    public static class HistoryEvent {
        public String date;
        public String location;
        public String odometer;
        public String source;
        public String details;

        public HistoryEvent(String date, String details) {
            this.date = date;
            this.details = details;
        }
    }

    // Constructor
    public AutoCheckReport(String vin) {
        this.vin = vin;
        this.historyEvents = new ArrayList<>();
    }

    // Parse from JSON
    public static AutoCheckReport fromJSON(JSONObject json) throws JSONException {
        String vin = json.getString("vin");
        AutoCheckReport report = new AutoCheckReport(vin);

        if (json.has("year")) report.year = json.getString("year");
        if (json.has("make")) report.make = json.getString("make");
        if (json.has("model")) report.model = json.getString("model");
        if (json.has("style")) report.style = json.getString("style");
        if (json.has("engine")) report.engine = json.getString("engine");
        if (json.has("country")) report.country = json.getString("country");
        if (json.has("owners")) report.owners = json.getInt("owners");
        if (json.has("lastOdometer")) report.lastOdometer = json.getInt("lastOdometer");
        if (json.has("score")) report.score = json.getInt("score");
        if (json.has("titleBrand")) report.titleBrand = json.getString("titleBrand");
        if (json.has("accidentDamage")) report.accidentDamage = json.getString("accidentDamage");
        if (json.has("recalls")) report.recalls = json.getString("recalls");
        if (json.has("serviceRecords")) report.serviceRecords = json.getInt("serviceRecords");

        // Boolean fields
        if (json.has("totalLoss")) report.totalLoss = json.getBoolean("totalLoss");
        if (json.has("structuralDamage")) report.structuralDamage = json.getBoolean("structuralDamage");
        if (json.has("airbagDeployed")) report.airbagDeployed = json.getBoolean("airbagDeployed");
        if (json.has("odometerRollback")) report.odometerRollback = json.getBoolean("odometerRollback");

        // Score range
        if (json.has("scoreRange")) {
            JSONObject scoreRangeJson = json.getJSONObject("scoreRange");
            report.scoreRange = new ScoreRange(
                scoreRangeJson.getInt("low"),
                scoreRangeJson.getInt("high")
            );
        }

        // History events
        if (json.has("historyEvents")) {
            JSONArray eventsArray = json.getJSONArray("historyEvents");
            for (int i = 0; i < eventsArray.length(); i++) {
                JSONObject eventJson = eventsArray.getJSONObject(i);
                HistoryEvent event = new HistoryEvent(
                    eventJson.getString("date"),
                    eventJson.getString("details")
                );
                if (eventJson.has("location")) event.location = eventJson.getString("location");
                if (eventJson.has("odometer")) event.odometer = eventJson.getString("odometer");
                if (eventJson.has("source")) event.source = eventJson.getString("source");
                report.historyEvents.add(event);
            }
        }

        return report;
    }

    // Getters
    public String getVin() { return vin; }
    public String getYear() { return year; }
    public String getMake() { return make; }
    public String getModel() { return model; }
    public String getStyle() { return style; }
    public String getEngine() { return engine; }
    public String getCountry() { return country; }
    public Integer getOwners() { return owners; }
    public Integer getLastOdometer() { return lastOdometer; }
    public Date getLastOdometerDate() { return lastOdometerDate; }
    public Integer getScore() { return score; }
    public ScoreRange getScoreRange() { return scoreRange; }
    public String getTitleBrand() { return titleBrand; }
    public String getAccidentDamage() { return accidentDamage; }
    public Boolean getTotalLoss() { return totalLoss; }
    public Boolean getStructuralDamage() { return structuralDamage; }
    public Boolean getAirbagDeployed() { return airbagDeployed; }
    public Boolean getOdometerRollback() { return odometerRollback; }
    public String getRecalls() { return recalls; }
    public Integer getServiceRecords() { return serviceRecords; }
    public List<HistoryEvent> getHistoryEvents() { return historyEvents; }

    // Helper methods
    public String getVehicleName() {
        StringBuilder name = new StringBuilder();
        if (year != null) name.append(year).append(" ");
        if (make != null) name.append(make).append(" ");
        if (model != null) name.append(model);
        return name.toString().trim();
    }

    public boolean hasAccidents() {
        return accidentDamage != null && !accidentDamage.equalsIgnoreCase("none");
    }

    public boolean hasCleanTitle() {
        return titleBrand != null && (titleBrand.equalsIgnoreCase("clean") || titleBrand.equalsIgnoreCase("no problem"));
    }

    public String getScoreSummary() {
        if (score == null) return "N/A";
        if (scoreRange != null) {
            return String.format("%d (%d-%d)", score, scoreRange.low, scoreRange.high);
        }
        return String.valueOf(score);
    }

    @Override
    public String toString() {
        return String.format("AutoCheck Report: %s (VIN: %s, Score: %s, Owners: %d)",
                getVehicleName(), vin, getScoreSummary(), owners != null ? owners : 0);
    }
}
