package io.github.vindecoder.recall;

import com.google.gson.annotations.SerializedName;

/**
 * Data model representing a single NHTSA recall campaign entry.
 */
public class RecallRecord {

    @SerializedName("Manufacturer")
    private String manufacturer;

    @SerializedName("NHTSACampaignNumber")
    private String nhtsaCampaignNumber;

    @SerializedName("NHTSAActionNumber")
    private String nhtsaActionNumber;

    @SerializedName("ReportReceivedDate")
    private String reportReceivedDate;

    @SerializedName("Component")
    private String component;

    @SerializedName("Summary")
    private String summary;

    @SerializedName("Consequence")
    private String consequence;

    @SerializedName("Remedy")
    private String remedy;

    @SerializedName("Notes")
    private String notes;

    @SerializedName("ModelYear")
    private String modelYear;

    @SerializedName("Make")
    private String make;

    @SerializedName("Model")
    private String model;

    @SerializedName("MfrRecallNumber")
    private String mfrRecallNumber;

    @SerializedName("ParkIt")
    private String parkItRaw;

    @SerializedName("ParkOutSide")
    private String parkOutsideRaw;

    @SerializedName("ParkOutside")
    private String parkOutsideAltRaw;

    @SerializedName("ParkOutsideYn")
    private String parkOutsideYnRaw;

    @SerializedName("OverTheAirUpdate")
    private String overTheAirUpdateRaw;

    @SerializedName("overTheAirUpdateYn")
    private String overTheAirUpdateYnRaw;

    public String getManufacturer() {
        return manufacturer;
    }

    public String getNhtsaCampaignNumber() {
        return nhtsaCampaignNumber;
    }

    public String getNhtsaActionNumber() {
        return nhtsaActionNumber;
    }

    public String getReportReceivedDate() {
        return reportReceivedDate;
    }

    public String getComponent() {
        return component;
    }

    public String getSummary() {
        return summary;
    }

    public String getConsequence() {
        return consequence;
    }

    public String getRemedy() {
        return remedy;
    }

    public String getNotes() {
        return notes;
    }

    public String getModelYear() {
        return modelYear;
    }

    public String getMake() {
        return make;
    }

    public String getModel() {
        return model;
    }

    public String getMfrRecallNumber() {
        return mfrRecallNumber;
    }

    /**
     * Indicates whether this recall advises parking the vehicle immediately.
     *
     * @return Boolean.TRUE if park-it flag is set, Boolean.FALSE if explicitly false, null otherwise.
     */
    public Boolean getParkIt() {
        return parseBoolean(parkItRaw);
    }

    /**
     * Indicates whether this recall advises parking the vehicle outside.
     *
     * @return Boolean.TRUE if park-outside flag is set, Boolean.FALSE if explicitly false, null otherwise.
     */
    public Boolean getParkOutside() {
        Boolean value = parseBoolean(parkOutsideRaw);
        if (value != null) return value;
        value = parseBoolean(parkOutsideAltRaw);
        if (value != null) return value;
        return parseBoolean(parkOutsideYnRaw);
    }

    /**
     * Indicates whether the recall supports over-the-air resolution.
     */
    public Boolean getOverTheAirUpdate() {
        Boolean value = parseBoolean(overTheAirUpdateRaw);
        if (value != null) return value;
        return parseBoolean(overTheAirUpdateYnRaw);
    }

    /**
     * Helper to determine if recall is critical.
     */
    public boolean isCriticalSafety() {
        Boolean parkIt = getParkIt();
        Boolean parkOutside = getParkOutside();
        return Boolean.TRUE.equals(parkIt) || Boolean.TRUE.equals(parkOutside);
    }

    /**
     * Helper to determine if recall is serviceable via OTA update.
     */
    public boolean isOverTheAir() {
        return Boolean.TRUE.equals(getOverTheAirUpdate());
    }

    private Boolean parseBoolean(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase();
        if (normalized.isEmpty() || normalized.equals("null") || normalized.equals("n/a")) {
            return null;
        }
        if (normalized.equals("y") || normalized.equals("yes") || normalized.equals("true") || normalized.equals("t")
                || normalized.equals("1")) {
            return Boolean.TRUE;
        }
        if (normalized.equals("n") || normalized.equals("no") || normalized.equals("false") || normalized.equals("f")
                || normalized.equals("0")) {
            return Boolean.FALSE;
        }
        return null;
    }
}
