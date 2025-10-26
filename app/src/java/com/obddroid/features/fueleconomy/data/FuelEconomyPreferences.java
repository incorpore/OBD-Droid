package com.obddroid.features.fueleconomy.data;

import android.content.Context;
import com.obddroid.utils.VehiclePreferences;

/**
 * Fuel Economy-specific preferences wrapper
 *
 * Handles persistence for:
 * - Tank capacity (user-set values per VIN)
 * - Volumetric Efficiency calibration (per VIN)
 * - Tank size prompt tracking (per VIN)
 *
 * This class wraps VehiclePreferences to provide a cleaner API
 * for fuel economy settings.
 */
public class FuelEconomyPreferences {

    private final VehiclePreferences vehiclePreferences;

    public FuelEconomyPreferences(Context context) {
        this.vehiclePreferences = new VehiclePreferences(context);
    }

    /**
     * Get user-set tank capacity for a vehicle
     *
     * @param vin Vehicle Identification Number
     * @return Tank capacity in gallons, or null if not set
     */
    public Float getTankCapacity(String vin) {
        if (vin == null || vin.isEmpty()) {
            return null;
        }
        return vehiclePreferences.getTankCapacity(vin);
    }

    /**
     * Set tank capacity for a vehicle
     *
     * @param vin Vehicle Identification Number
     * @param capacityGallons Tank capacity in gallons
     * @return true if saved successfully
     */
    public boolean setTankCapacity(String vin, float capacityGallons) {
        if (vin == null || vin.isEmpty()) {
            return false;
        }
        return vehiclePreferences.setTankCapacity(vin, capacityGallons);
    }

    /**
     * Get calibrated Volumetric Efficiency for a vehicle
     *
     * @param vin Vehicle Identification Number
     * @return VE percentage (e.g., 85.0 for 85%), or null if not calibrated
     */
    public Float getCalibratedVE(String vin) {
        if (vin == null || vin.isEmpty()) {
            return null;
        }
        return vehiclePreferences.getVolumetricEfficiency(vin);
    }

    /**
     * Set calibrated Volumetric Efficiency for a vehicle
     *
     * @param vin Vehicle Identification Number
     * @param vePercent VE percentage (e.g., 85.0 for 85%)
     * @return true if saved successfully
     */
    public boolean setCalibratedVE(String vin, float vePercent) {
        if (vin == null || vin.isEmpty()) {
            return false;
        }
        return vehiclePreferences.setVolumetricEfficiency(vin, vePercent);
    }

    /**
     * Check if this vehicle has been calibrated
     *
     * @param vin Vehicle Identification Number
     * @return true if calibrated VE exists
     */
    public boolean isCalibrated(String vin) {
        return getCalibratedVE(vin) != null;
    }

    /**
     * Calculate VE accuracy level based on calibration status
     *
     * @param vin Vehicle Identification Number
     * @return Accuracy string like "~95% (calibrated)" or "~88% (uncalibrated)"
     */
    public String getAccuracyLevel(String vin) {
        return isCalibrated(vin) ? "~95% (calibrated)" : "~88% (uncalibrated)";
    }

    /**
     * Reset all calibration data for a vehicle
     *
     * @param vin Vehicle Identification Number
     */
    public void resetCalibration(String vin) {
        if (vin != null && !vin.isEmpty()) {
            vehiclePreferences.setVolumetricEfficiency(vin, 0.0f);
        }
    }
}
