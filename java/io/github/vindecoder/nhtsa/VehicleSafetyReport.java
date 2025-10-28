package io.github.vindecoder.nhtsa;

import io.github.vindecoder.recall.RecallRecord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregated result containing decoded vehicle information and recall data.
 */
public class VehicleSafetyReport {

    private VehicleData vehicle;
    private List<RecallRecord> recalls = Collections.emptyList();
    private final List<String> errors = new ArrayList<>();

    public VehicleSafetyReport() {
    }

    public VehicleSafetyReport(VehicleData vehicle, List<RecallRecord> recalls) {
        this.vehicle = vehicle;
        setRecalls(recalls);
    }

    public VehicleData getVehicle() {
        return vehicle;
    }

    public void setVehicle(VehicleData vehicle) {
        this.vehicle = vehicle;
    }

    public List<RecallRecord> getRecalls() {
        return recalls;
    }

    public void setRecalls(List<RecallRecord> recalls) {
        if (recalls == null || recalls.isEmpty()) {
            this.recalls = Collections.emptyList();
        } else {
            this.recalls = Collections.unmodifiableList(new ArrayList<>(recalls));
        }
    }

    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public void addError(String error) {
        if (error != null && !error.trim().isEmpty()) {
            errors.add(error);
        }
    }

    public boolean hasRecalls() {
        return !recalls.isEmpty();
    }

    public boolean hasCriticalRecalls() {
        return recalls.stream().anyMatch(RecallRecord::isCriticalSafety);
    }
}
