package com.obddroid.features.recalls.model;

import com.obddroid.features.vehiclehistory.model.AutoCheckReport;

import java.util.ArrayList;
import java.util.List;

/**
 * Container for open recall data returned by the AutoCheck API.
 *
 * This supplements the existing NHTSA recall search results by providing
 * vehicle-specific open recall campaigns sourced from AutoCheck.
 */
public class AutoCheckRecallResult {

    private final String vin;
    private final String vehicleName;
    private final String statusText;
    private final int openRecallCount;
    private final List<AutoCheckReport.RecallDetail> recallDetails;
    private final long fetchedAt;

    public AutoCheckRecallResult(String vin,
                                 String vehicleName,
                                 String statusText,
                                 int openRecallCount,
                                 List<AutoCheckReport.RecallDetail> recallDetails) {
        this.vin = vin;
        this.vehicleName = vehicleName;
        this.statusText = statusText != null ? statusText : "";
        this.openRecallCount = Math.max(openRecallCount, 0);
        this.recallDetails = recallDetails != null
            ? new ArrayList<>(recallDetails)
            : new ArrayList<>();
        this.fetchedAt = System.currentTimeMillis();
    }

    /**
     * Build an AutoCheck recall result from a full AutoCheck report.
     */
    public static AutoCheckRecallResult fromReport(AutoCheckReport report) {
        if (report == null) {
            return null;
        }

        int count = report.getOpenRecalls() != null ? report.getOpenRecalls() : 0;
        String status = null;
        if (report.getAtAGlance() != null && report.getAtAGlance().openRecallCheck != null) {
            status = report.getAtAGlance().openRecallCheck.status;
        }
        if (status == null) {
            status = report.getRecalls();
        }

        return new AutoCheckRecallResult(
            report.getVin(),
            report.getVehicleName(),
            status,
            count,
            report.getRecallDetails()
        );
    }

    public String getVin() {
        return vin;
    }

    public String getVehicleName() {
        return vehicleName;
    }

    public String getStatusText() {
        return statusText;
    }

    public int getOpenRecallCount() {
        return openRecallCount;
    }

    public List<AutoCheckReport.RecallDetail> getRecallDetails() {
        return new ArrayList<>(recallDetails);
    }

    public long getFetchedAt() {
        return fetchedAt;
    }

    /**
     * @return true if the AutoCheck report indicates any open recalls and detailed entries exist
     */
    public boolean hasOpenRecalls() {
        return openRecallCount > 0 && !recallDetails.isEmpty();
    }

    /**
     * @return true when AutoCheck reported open recalls but no detailed rows were extracted
     */
    public boolean hasCountMismatch() {
        return openRecallCount > 0 && recallDetails.isEmpty();
    }

    /**
     * @return true if AutoCheck explicitly indicates there are no open recalls
     */
    public boolean isClear() {
        return openRecallCount == 0 || statusText != null && statusText.toLowerCase().contains("no open");
    }
}

