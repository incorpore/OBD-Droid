package io.github.vindecoder.nhtsa;

import io.github.vindecoder.offline.OfflineVINDecoder;
import io.github.vindecoder.recall.RecallLookupService;
import io.github.vindecoder.recall.RecallRecord;
import java.io.IOException;
import java.util.List;

/**
 * High-level service that combines VIN decoding with NHTSA recall lookup.
 */
public class VehicleSafetyService {

    private final VINDecoderService vinDecoderService;
    private final RecallLookupService recallLookupService;
    private final OfflineVINDecoder offlineVINDecoder;

    /**
     * Callback for asynchronous vehicle safety reports.
     */
    public interface VehicleSafetyCallback {
        void onSuccess(VehicleSafetyReport report);
        void onError(String error);
    }

    public VehicleSafetyService() {
        this(VINDecoderService.getInstance(), RecallLookupService.getInstance(), new OfflineVINDecoder());
    }

    public VehicleSafetyService(VINDecoderService vinDecoderService,
                                RecallLookupService recallLookupService,
                                OfflineVINDecoder offlineVINDecoder) {
        this.vinDecoderService = vinDecoderService;
        this.recallLookupService = recallLookupService;
        this.offlineVINDecoder = offlineVINDecoder;
    }

    /**
     * Asynchronously fetch a combined vehicle + recall report.
     */
    public void getVehicleSafetyReport(String vin, VehicleSafetyCallback callback) {
        if (vin == null || vin.trim().isEmpty()) {
            callback.onError("VIN cannot be empty");
            return;
        }

        final String normalizedVin = vin.trim().toUpperCase();
        final VehicleSafetyReport report = new VehicleSafetyReport();

        // Seed report with offline data for resilience
        VehicleData offlineData = offlineVINDecoder.decode(normalizedVin);
        report.setVehicle(offlineData);

        vinDecoderService.decodeVIN(normalizedVin, new VINDecoderService.VINDecoderCallback() {
            @Override
            public void onSuccess(VehicleData vehicleData) {
                if (vehicleData != null) {
                    report.setVehicle(vehicleData);
                }
                fetchRecalls(normalizedVin, report, report.getVehicle(), callback);
            }

            @Override
            public void onError(String error) {
                report.addError(error);
                fetchRecalls(normalizedVin, report, report.getVehicle(), callback);
            }
        });
    }

    /**
     * Blocking helper for desktop/server code.
     */
    public VehicleSafetyReport getVehicleSafetyReportBlocking(String vin, String modelYear) throws IOException {
        if (vin == null || vin.trim().isEmpty()) {
            throw new IllegalArgumentException("VIN cannot be empty");
        }

        final String normalizedVin = vin.trim().toUpperCase();
        VehicleSafetyReport report = new VehicleSafetyReport();

        VehicleData offlineData = offlineVINDecoder.decode(normalizedVin);
        report.setVehicle(offlineData);

        try {
            VehicleData decoded = (modelYear != null && !modelYear.trim().isEmpty())
                    ? vinDecoderService.decodeVINWithYearBlocking(normalizedVin, modelYear.trim())
                    : vinDecoderService.decodeVINBlocking(normalizedVin);
            if (decoded != null) {
                report.setVehicle(decoded);
            }
        } catch (IOException ex) {
            report.addError("VIN decode failed: " + ex.getMessage());
        }

        try {
            List<RecallRecord> recalls = recallLookupService.getRecallsByVinBlocking(normalizedVin);
            if (recalls.isEmpty() && hasMakeModel(report.getVehicle())) {
                recalls = recallLookupService.getRecallsBlocking(
                        report.getVehicle().getMake(),
                        report.getVehicle().getModel(),
                        report.getVehicle().getModelYear()
                );
            }
            report.setRecalls(recalls);
        } catch (IOException ex) {
            report.addError("Recall lookup failed: " + ex.getMessage());
        }

        return report;
    }

    /**
     * Clears caches for both VIN decoder and recall lookup services.
     */
    public void clearCache() {
        vinDecoderService.clearCache();
        recallLookupService.clearCache();
    }

    private void fetchRecalls(String vin,
                              VehicleSafetyReport report,
                              VehicleData vehicleContext,
                              VehicleSafetyCallback callback) {
        recallLookupService.getRecallsByVin(vin, new RecallLookupService.RecallCallback() {
            @Override
            public void onSuccess(List<RecallRecord> recalls) {
                if (!recalls.isEmpty()) {
                    report.setRecalls(recalls);
                    callback.onSuccess(report);
                } else {
                    fetchRecallsByVehicle(report, vehicleContext, callback);
                }
            }

            @Override
            public void onError(String error) {
                report.addError(error);
                fetchRecallsByVehicle(report, vehicleContext, callback);
            }
        });
    }

    private void fetchRecallsByVehicle(VehicleSafetyReport report,
                                       VehicleData vehicleContext,
                                       VehicleSafetyCallback callback) {
        if (!hasMakeModel(vehicleContext)) {
            callback.onSuccess(report);
            return;
        }

        recallLookupService.getRecalls(
                vehicleContext.getMake(),
                vehicleContext.getModel(),
                vehicleContext.getModelYear(),
                new RecallLookupService.RecallCallback() {
                    @Override
                    public void onSuccess(List<RecallRecord> recalls) {
                        report.setRecalls(recalls);
                        callback.onSuccess(report);
                    }

                    @Override
                    public void onError(String error) {
                        report.addError(error);
                        callback.onSuccess(report);
                    }
                }
        );
    }

    private boolean hasMakeModel(VehicleData vehicle) {
        if (vehicle == null) {
            return false;
        }
        return !isBlank(vehicle.getMake()) && !isBlank(vehicle.getModel());
    }

    private boolean isBlank(String value) {
        if (value == null) {
            return true;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() || "Not Applicable".equalsIgnoreCase(trimmed);
    }
}
