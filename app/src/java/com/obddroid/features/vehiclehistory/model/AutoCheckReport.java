package com.obddroid.features.vehiclehistory.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
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
    private String trim;
    private String style;
    private String engine;
    private String country;
    private String vehicleClass;
    private Integer owners;
    private String usage;  // "Lease", "Personal", "Commercial", etc.
    private Integer lastOdometer;
    private Date lastOdometerDate;
    private Integer score;
    private ScoreRange scoreRange;
    private Integer vehicleAge;
    private String titleBrand;
    private String accidentDamage;
    private Boolean totalLoss;
    private Boolean structuralDamage;
    private Boolean airbagDeployed;
    private Boolean overturned;
    private Boolean odometerRollback;
    private String recalls;
    private Integer openRecalls;
    private List<RecallDetail> recallDetails;
    private Integer serviceRecords;
    private List<HistoryEvent> historyEvents;

    // Score Analysis
    private String vehicleComparison;
    private String vehicleOutlook;
    private List<String> increasingFactors;
    private List<String> decreasingFactors;

    // New API fields
    private String bodyStyle;
    private String vehicleUsage;
    private String damageMessage;
    private AtAGlance atAGlance;
    private OdometerSubChecks odometerSubChecks;
    private List<OwnerHistory> ownerHistory;

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

    public static class RecallDetail {
        public String recallDate;
        public String recallType;
        public String nhtsaRecallNo;
        public String oemRecallNo;
        public String combinedRecallNo;
        public String campaignDescription;
        public String status;

        public RecallDetail(String recallDate, String recallType, String campaignDescription) {
            this.recallDate = recallDate;
            this.recallType = recallType;
            this.campaignDescription = campaignDescription;
        }
    }

    public static class GlanceCheck {
        public String status;
        public String statusType;  // "No Issue", "Issue Found", "Events Reported", etc.
        public String description;
        public String subtitle;
        public Integer count;
    }

    public static class AtAGlance {
        public GlanceCheck stateTitleBrand;
        public GlanceCheck auctionBrandIssues;
        public GlanceCheck accidentDamage;
        public GlanceCheck openRecallCheck;
        public GlanceCheck insuranceLossTransfer;
        public GlanceCheck odometerCheck;
        public GlanceCheck certifiedPreOwned;
        public GlanceCheck serviceRepair;
        public GlanceCheck additionalHistory;
    }

    public static class OdometerSubChecks {
        public String stateTitleOdometerCheck;
        public String auctionOdometerCheck;
        public String odometerCalculationCheck;
    }

    public static class OwnerHistory {
        public int ownerNumber;
        public String location;
        public String ownedFrom;
        public String ownedTo;
        public String usage;
        public List<HistoryEvent> events;

        public OwnerHistory() {
            this.events = new ArrayList<>();
        }
    }

    // Constructor
    public AutoCheckReport(String vin) {
        this.vin = vin;
        this.historyEvents = new ArrayList<>();
        this.recallDetails = new ArrayList<>();
        this.increasingFactors = new ArrayList<>();
        this.decreasingFactors = new ArrayList<>();
        this.ownerHistory = new ArrayList<>();
    }

    // Parse from JSON
    public static AutoCheckReport fromJSON(JSONObject json) throws JSONException {
        String vin = json.getString("vin");
        AutoCheckReport report = new AutoCheckReport(vin);

        if (json.has("year")) report.year = json.getString("year");
        if (json.has("make")) report.make = json.getString("make");
        if (json.has("model")) report.model = json.getString("model");
        if (json.has("trim")) report.trim = json.getString("trim");
        if (json.has("style")) report.style = json.getString("style");
        if (json.has("engine")) report.engine = json.getString("engine");
        if (json.has("country")) report.country = json.getString("country");
        if (json.has("vehicleClass")) report.vehicleClass = json.getString("vehicleClass");
        if (json.has("owners")) report.owners = json.getInt("owners");
        if (json.has("usage")) report.usage = json.getString("usage");
        if (json.has("lastOdometer")) report.lastOdometer = json.getInt("lastOdometer");
        if (json.has("score")) report.score = json.getInt("score");
        if (json.has("titleBrand")) report.titleBrand = json.getString("titleBrand");
        if (json.has("accidentDamage")) report.accidentDamage = json.getString("accidentDamage");
        if (json.has("recalls")) report.recalls = json.getString("recalls");
        if (json.has("serviceRecords")) report.serviceRecords = json.getInt("serviceRecords");
        if (json.has("vehicleAge")) {
            Integer parsedAge = parseInteger(json.opt("vehicleAge"));
            if (parsedAge != null && parsedAge >= 0) {
                report.vehicleAge = parsedAge;
            }
        }

        // Boolean fields
        if (json.has("totalLoss")) report.totalLoss = json.getBoolean("totalLoss");
        if (json.has("structuralDamage")) report.structuralDamage = json.getBoolean("structuralDamage");
        if (json.has("airbagDeployed")) report.airbagDeployed = json.getBoolean("airbagDeployed");
        if (json.has("overturned")) report.overturned = json.getBoolean("overturned");
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

                // Get date - try "eventDate" first, then "date", default to "Unknown"
                String date = "Unknown Date";
                if (eventJson.has("eventDate")) {
                    date = eventJson.getString("eventDate");
                } else if (eventJson.has("date")) {
                    date = eventJson.getString("date");
                }

                // Get details - default to empty if not present
                String details = eventJson.has("details") ? eventJson.getString("details") : "";

                HistoryEvent event = new HistoryEvent(date, details);

                if (eventJson.has("location")) event.location = eventJson.getString("location");
                if (eventJson.has("odometer")) event.odometer = eventJson.getString("odometer");
                if (eventJson.has("source")) event.source = eventJson.getString("source");
                if (eventJson.has("dataSource")) event.source = eventJson.getString("dataSource");

                report.historyEvents.add(event);
            }
        }

        // Score Analysis fields
        if (json.has("vehicleComparison")) report.vehicleComparison = json.getString("vehicleComparison");
        if (json.has("vehicleOutlook")) report.vehicleOutlook = json.getString("vehicleOutlook");

        if (json.has("increasingFactors")) {
            JSONArray factors = json.getJSONArray("increasingFactors");
            for (int i = 0; i < factors.length(); i++) {
                report.increasingFactors.add(factors.getString(i));
            }
        }

        if (json.has("decreasingFactors")) {
            JSONArray factors = json.getJSONArray("decreasingFactors");
            for (int i = 0; i < factors.length(); i++) {
                report.decreasingFactors.add(factors.getString(i));
            }
        }

        // Parse recall information
        if (json.has("openRecalls")) {
            report.openRecalls = json.getInt("openRecalls");
        }

        if (json.has("recallDetails")) {
            JSONArray recallsArray = json.getJSONArray("recallDetails");
            for (int i = 0; i < recallsArray.length(); i++) {
                JSONObject recallJson = recallsArray.getJSONObject(i);

                String recallDate = recallJson.optString("recallDate", "Unknown");
                String recallType = recallJson.optString("recallType", "Unknown");
                String campaignDescription = recallJson.optString("campaignDescription", "No description available");

                RecallDetail recall = new RecallDetail(recallDate, recallType, campaignDescription);

                if (recallJson.has("nhtsaRecallNo")) {
                    recall.nhtsaRecallNo = recallJson.getString("nhtsaRecallNo");
                }
                if (recallJson.has("oemRecallNo")) {
                    recall.oemRecallNo = recallJson.getString("oemRecallNo");
                }
                if (recallJson.has("combinedRecallNo")) {
                    recall.combinedRecallNo = recallJson.getString("combinedRecallNo");
                }
                if (recallJson.has("status")) {
                    recall.status = recallJson.getString("status");
                }

                report.recallDetails.add(recall);
            }
        }

        // Parse new fields (v1.0.3+)
        if (json.has("bodyStyle")) report.bodyStyle = json.getString("bodyStyle");
        if (json.has("vehicleUsage")) report.vehicleUsage = json.getString("vehicleUsage");
        if (json.has("damageMessage")) report.damageMessage = json.getString("damageMessage");

        // Parse At-A-Glance
        if (json.has("atAGlance")) {
            JSONObject ataglanceJson = json.getJSONObject("atAGlance");
            report.atAGlance = new AtAGlance();

            if (ataglanceJson.has("stateTitleBrand")) {
                report.atAGlance.stateTitleBrand = parseGlanceCheck(ataglanceJson.getJSONObject("stateTitleBrand"));
            }
            if (ataglanceJson.has("auctionBrandIssues")) {
                report.atAGlance.auctionBrandIssues = parseGlanceCheck(ataglanceJson.getJSONObject("auctionBrandIssues"));
            }
            if (ataglanceJson.has("accidentDamage")) {
                report.atAGlance.accidentDamage = parseGlanceCheck(ataglanceJson.getJSONObject("accidentDamage"));
            }
            if (ataglanceJson.has("openRecallCheck")) {
                report.atAGlance.openRecallCheck = parseGlanceCheck(ataglanceJson.getJSONObject("openRecallCheck"));
            }
            if (ataglanceJson.has("insuranceLossTransfer")) {
                report.atAGlance.insuranceLossTransfer = parseGlanceCheck(ataglanceJson.getJSONObject("insuranceLossTransfer"));
            }
            if (ataglanceJson.has("odometerCheck")) {
                report.atAGlance.odometerCheck = parseGlanceCheck(ataglanceJson.getJSONObject("odometerCheck"));
            }
            if (ataglanceJson.has("certifiedPreOwned")) {
                report.atAGlance.certifiedPreOwned = parseGlanceCheck(ataglanceJson.getJSONObject("certifiedPreOwned"));
            }
            if (ataglanceJson.has("serviceRepair")) {
                report.atAGlance.serviceRepair = parseGlanceCheck(ataglanceJson.getJSONObject("serviceRepair"));
            }
            if (ataglanceJson.has("additionalHistory")) {
                report.atAGlance.additionalHistory = parseGlanceCheck(ataglanceJson.getJSONObject("additionalHistory"));
            }
        }

        // Parse Odometer Sub-Checks
        if (json.has("odometerSubChecks")) {
            JSONObject odometerJson = json.getJSONObject("odometerSubChecks");
            report.odometerSubChecks = new OdometerSubChecks();

            if (odometerJson.has("stateTitleOdometerCheck")) {
                report.odometerSubChecks.stateTitleOdometerCheck = odometerJson.getString("stateTitleOdometerCheck");
            }
            if (odometerJson.has("auctionOdometerCheck")) {
                report.odometerSubChecks.auctionOdometerCheck = odometerJson.getString("auctionOdometerCheck");
            }
            if (odometerJson.has("odometerCalculationCheck")) {
                report.odometerSubChecks.odometerCalculationCheck = odometerJson.getString("odometerCalculationCheck");
            }
        }

        // Parse Owner History
        if (json.has("ownerHistory")) {
            JSONArray ownersArray = json.getJSONArray("ownerHistory");
            for (int i = 0; i < ownersArray.length(); i++) {
                JSONObject ownerJson = ownersArray.getJSONObject(i);
                OwnerHistory owner = new OwnerHistory();

                if (ownerJson.has("ownerNumber")) owner.ownerNumber = ownerJson.getInt("ownerNumber");
                if (ownerJson.has("location")) owner.location = ownerJson.getString("location");
                if (ownerJson.has("ownedFrom")) owner.ownedFrom = ownerJson.getString("ownedFrom");
                if (ownerJson.has("ownedTo")) owner.ownedTo = ownerJson.getString("ownedTo");
                if (ownerJson.has("usage")) owner.usage = ownerJson.getString("usage");

                // Parse events for this owner
                if (ownerJson.has("events")) {
                    JSONArray eventsArray = ownerJson.getJSONArray("events");
                    for (int j = 0; j < eventsArray.length(); j++) {
                        JSONObject eventJson = eventsArray.getJSONObject(j);

                        String date = "Unknown Date";
                        if (eventJson.has("eventDate")) {
                            date = eventJson.getString("eventDate");
                        } else if (eventJson.has("date")) {
                            date = eventJson.getString("date");
                        }

                        String details = eventJson.has("details") ? eventJson.getString("details") : "";
                        HistoryEvent event = new HistoryEvent(date, details);

                        if (eventJson.has("location")) event.location = eventJson.getString("location");
                        if (eventJson.has("odometer")) event.odometer = eventJson.getString("odometer");
                        if (eventJson.has("source")) event.source = eventJson.getString("source");
                        if (eventJson.has("dataSource")) event.source = eventJson.getString("dataSource");

                        owner.events.add(event);
                    }
                }

                report.ownerHistory.add(owner);
            }
        }

        return report;
    }

    // Getters
    public String getVin() { return vin; }
    public String getYear() { return year; }
    public String getMake() { return make; }
    public String getModel() { return model; }
    public String getTrim() { return trim; }
    public String getStyle() { return style; }
    public String getEngine() { return engine; }
    public String getCountry() { return country; }
    public String getVehicleClass() { return vehicleClass; }
    public Integer getOwners() { return owners; }
    public String getUsage() { return usage; }
    public Integer getLastOdometer() { return lastOdometer; }
    public Date getLastOdometerDate() { return lastOdometerDate; }
    public Integer getScore() { return score; }
    public ScoreRange getScoreRange() { return scoreRange; }
    public Integer getVehicleAge() {
        if (vehicleAge != null && vehicleAge >= 0) {
            return vehicleAge;
        }
        if (year == null) {
            return null;
        }
        String numericYear = year.replaceAll("\\D", "");
        if (numericYear.isEmpty()) {
            return null;
        }
        try {
            int modelYear = Integer.parseInt(numericYear);
            int currentYear = Calendar.getInstance().get(Calendar.YEAR);
            int age = currentYear - modelYear;
            if (age < 0 || age > 150) {
                return null;
            }
            return age;
        } catch (NumberFormatException e) {
            return null;
        }
    }
    public String getTitleBrand() { return titleBrand; }
    public String getAccidentDamage() { return accidentDamage; }
    public Boolean getTotalLoss() { return totalLoss; }
    public Boolean getStructuralDamage() { return structuralDamage; }
    public Boolean getAirbagDeployed() { return airbagDeployed; }
    public Boolean getOverturned() { return overturned; }
    public Boolean getOdometerRollback() { return odometerRollback; }
    public String getRecalls() { return recalls; }
    public Integer getOpenRecalls() { return openRecalls; }
    public List<RecallDetail> getRecallDetails() { return recallDetails; }
    public Integer getServiceRecords() { return serviceRecords; }
    public List<HistoryEvent> getHistoryEvents() { return historyEvents; }

    // Score Analysis getters
    public String getVehicleComparison() { return vehicleComparison; }
    public String getVehicleOutlook() { return vehicleOutlook; }
    public List<String> getIncreasingFactors() { return increasingFactors; }
    public List<String> getDecreasingFactors() { return decreasingFactors; }

    // New field getters (v1.0.3+)
    public String getBodyStyle() { return bodyStyle; }
    public String getVehicleUsage() { return vehicleUsage; }
    public String getDamageMessage() { return damageMessage; }
    public AtAGlance getAtAGlance() { return atAGlance; }
    public OdometerSubChecks getOdometerSubChecks() { return odometerSubChecks; }
    public List<OwnerHistory> getOwnerHistory() { return ownerHistory; }

    // Setters (for basic decode functionality)
    public void setYear(String year) { this.year = year; }
    public void setMake(String make) { this.make = make; }
    public void setModel(String model) { this.model = model; }

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

    private static GlanceCheck parseGlanceCheck(JSONObject json) throws JSONException {
        GlanceCheck check = new GlanceCheck();
        if (json.has("status")) check.status = json.getString("status");
        if (json.has("statusType")) check.statusType = json.getString("statusType");
        if (json.has("description")) check.description = json.getString("description");
        if (json.has("subtitle")) check.subtitle = json.getString("subtitle");
        if (json.has("count")) check.count = json.getInt("count");
        return check;
    }

    private static Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            String digits = ((String) value).replaceAll("[^0-9-]", "");
            if (digits.isEmpty()) {
                return null;
            }
            try {
                return Integer.parseInt(digits);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
