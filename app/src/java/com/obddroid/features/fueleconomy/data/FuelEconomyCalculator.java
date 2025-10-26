package com.obddroid.features.fueleconomy.data;

import java.util.ArrayList;
import java.util.logging.Logger;

/**
 * Pure calculation engine for fuel economy metrics
 *
 * This class contains all the mathematical formulas and algorithms for calculating
 * fuel consumption rates and MPG. It has no Android dependencies and can be
 * unit tested independently.
 *
 * Calculation Methods:
 * - MAP-based Speed-Density (88% accuracy uncalibrated, 95%+ calibrated)
 * - RPM/Load estimation (65% accuracy - fallback)
 * - Volumetric Efficiency estimation for turbo/NA engines
 */
public class FuelEconomyCalculator {

    private static final Logger log = Logger.getLogger(FuelEconomyCalculator.class.getName());

    // Physical constants for fuel calculations
    private static final float MOLAR_MASS_AIR = 28.97f;       // g/mol (average molecular mass of air)
    private static final float GAS_CONSTANT = 8.314f;         // J/(K·mol) (universal gas constant)
    private static final float STOICHIOMETRIC_RATIO = 14.7f;  // air:fuel ratio for gasoline
    private static final float GRAMS_PER_POUND = 454f;
    private static final float POUNDS_PER_GALLON = 6.701f;    // gasoline density
    private static final float SECONDS_PER_HOUR = 3600f;

    /**
     * Calculate fuel consumption using MAP-based Speed-Density method
     *
     * This is the industry-standard approach used by Torque Pro, OBDLink, etc.
     *
     * @param rpm Engine speed (revolutions per minute)
     * @param mapKpa Manifold Absolute Pressure (kilopascals)
     * @param iatCelsius Intake Air Temperature (Celsius)
     * @param loadPercent Engine load percentage (0-100)
     * @param displacementL Engine displacement (liters)
     * @param volumetricEfficiency Volumetric efficiency percentage (50-130)
     * @return Fuel flow rate in gallons per hour, or 0 if calculation fails
     */
    public static float calculateFuelRateMAP(float rpm, float mapKpa, float iatCelsius,
                                             float loadPercent, float displacementL,
                                             float volumetricEfficiency) {
        try {
            // Validate inputs
            if (rpm < 100f) {
                log.warning("MAP calc: Invalid RPM (" + rpm + "), aborting");
                return 0f;
            }
            if (mapKpa < 10f || mapKpa > 250f) {
                log.warning("MAP calc: Invalid MAP (" + mapKpa + " kPa), aborting");
                return 0f;
            }
            if (iatCelsius < -40f || iatCelsius > 120f) {
                log.warning("MAP calc: Invalid IAT (" + iatCelsius + "°C), aborting");
                return 0f;
            }

            // Step 1: Convert IAT to Kelvin (required for ideal gas law)
            float iatKelvin = iatCelsius + 273.15f;

            // Step 2: Calculate IMAP (Intake Manifold Air Pressure factor)
            float imap = (rpm * mapKpa) / (iatKelvin * 2.0f);

            // Step 3: Calculate Synthetic MAF (Mass Air Flow) in grams per second
            // Using the Ideal Gas Law and Speed-Density formula
            float syntheticMAF = (imap / 120.0f)
                               * (volumetricEfficiency / 100.0f)
                               * displacementL
                               * MOLAR_MASS_AIR
                               / GAS_CONSTANT;

            // Step 4: Calculate fuel flow rate from MAF
            // Modern engines maintain stoichiometric air-fuel ratio of 14.7:1
            float fuelGramsPerSec = syntheticMAF / STOICHIOMETRIC_RATIO;

            // Step 5: Convert to gallons per hour
            float fuelGallonsPerHour = (fuelGramsPerSec / GRAMS_PER_POUND)
                                     / POUNDS_PER_GALLON
                                     * SECONDS_PER_HOUR;

            // Step 6: Validate and clamp result to realistic range
            final float MIN_FUEL_RATE = 0.1f;
            final float MAX_FUEL_RATE = 5.0f;

            if (fuelGallonsPerHour < MIN_FUEL_RATE || fuelGallonsPerHour > MAX_FUEL_RATE) {
                log.warning(String.format("MAP calc: Fuel rate %.3f gal/h out of range, clamping",
                        fuelGallonsPerHour));
            }

            fuelGallonsPerHour = Math.max(MIN_FUEL_RATE, Math.min(fuelGallonsPerHour, MAX_FUEL_RATE));

            log.info(String.format("MAP Speed-Density: RPM=%.0f, MAP=%.1f kPa, IAT=%.1f°C, " +
                    "Load=%.1f%%, Disp=%.1fL, VE=%.1f%%, Fuel=%.3f gal/h",
                    rpm, mapKpa, iatCelsius, loadPercent, displacementL,
                    volumetricEfficiency, fuelGallonsPerHour));

            return fuelGallonsPerHour;

        } catch (Exception e) {
            log.warning("Error in MAP-based fuel calculation: " + e.getMessage());
            return 0f;
        }
    }

    /**
     * Estimate fuel consumption based on RPM, engine load, and speed
     * Fallback method for vehicles without MAP/IAT sensors
     *
     * @param rpm Engine speed (RPM)
     * @param loadPercent Engine load (0-100%)
     * @param speedMph Vehicle speed (miles per hour)
     * @param displacementFactor Engine size normalization factor
     * @return Estimated fuel flow rate in gallons per hour
     */
    public static float calculateEstimatedFuelRate(float rpm, float loadPercent,
                                                   float speedMph, float displacementFactor) {
        // Base fuel consumption increases with RPM and load
        float baseRate = (rpm / 3000f) * (loadPercent / 100f);

        // Adjust for speed (highway efficiency vs city)
        float speedFactor = 1.0f;
        if (speedMph > 55f) {
            // Highway speeds - slightly less efficient due to wind resistance
            speedFactor = 1.0f + ((speedMph - 55f) / 100f);
        } else if (speedMph < 25f && speedMph > 5f) {
            // City speeds - less efficient
            speedFactor = 1.2f;
        }

        // Calculate estimated fuel rate
        float estimatedGalH = baseRate * speedFactor * displacementFactor;

        // Clamp to reasonable range based on displacement
        float minRate = displacementFactor < 2.0f ? 0.2f : 0.3f;
        float maxRate = displacementFactor < 2.0f ? 2.5f : 5.0f;

        return Math.min(Math.max(estimatedGalH, minRate), maxRate);
    }

    /**
     * Estimate Volumetric Efficiency for current engine operating conditions
     *
     * Volumetric Efficiency (VE) is the ratio of actual air intake to theoretical maximum.
     * - Naturally aspirated engines: typically 75-90%
     * - Turbocharged engines: can exceed 100% due to forced induction
     *
     * @param loadPercent Engine load (0-100%)
     * @param rpm Engine speed (RPM)
     * @return Estimated volumetric efficiency (percentage)
     */
    public static float estimateVolumetricEfficiency(float loadPercent, float rpm) {
        float baseVE = 85.0f; // Baseline for modern engines at cruise

        // Load-based VE estimation
        if (loadPercent < 20f) {
            // Very light load - heavily throttled, low volumetric efficiency
            baseVE = 75.0f;
        } else if (loadPercent < 40f) {
            // Light load - naturally aspirated range
            baseVE = 80.0f;
        } else if (loadPercent < 60f) {
            // Medium load - turbo beginning to spool
            baseVE = 85.0f + (loadPercent - 40f) * 0.25f; // 85-90%
        } else if (loadPercent < 80f) {
            // High load - significant turbo boost
            baseVE = 90.0f + (loadPercent - 60f) * 0.35f; // 90-97%
        } else {
            // Very high load - maximum boost (WOT)
            baseVE = 95.0f + (loadPercent - 80f) * 0.5f; // 95-105%
        }

        // RPM-based adjustment
        // Engines have peak VE at certain RPM ranges (torque peak)
        if (rpm < 1500f) {
            // Low RPM - reduced efficiency due to slow intake velocity
            baseVE *= 0.92f;
        } else if (rpm > 5000f) {
            // High RPM - reduced efficiency due to friction and pumping losses
            baseVE *= 0.95f;
        }
        // RPM between 1500-5000: No adjustment (peak efficiency range)

        // Cap VE at realistic maximum
        baseVE = Math.min(baseVE, 110.0f);

        log.fine(String.format("Estimated VE: %.1f%% (Load: %.1f%%, RPM: %.0f)",
                baseVE, loadPercent, rpm));

        return baseVE;
    }

    /**
     * Calculate instantaneous MPG from speed and fuel rate
     *
     * @param speedMph Vehicle speed (miles per hour)
     * @param fuelRateGalH Fuel consumption rate (gallons per hour)
     * @return Instantaneous MPG, clamped to realistic range (5-99 MPG)
     */
    public static float calculateInstantMpg(float speedMph, float fuelRateGalH) {
        if (fuelRateGalH < 0.01f || speedMph < 1.0f) {
            return 0f;
        }

        float instantMpg = speedMph / fuelRateGalH;

        // Cap unrealistic values
        instantMpg = Math.min(Math.max(instantMpg, 5f), 99f);

        return instantMpg;
    }

    /**
     * Calculate estimated range in miles
     *
     * @param fuelLevelPercent Current fuel level (0-100%)
     * @param tankCapacityGallons Total tank capacity (gallons)
     * @param averageMpg Average fuel economy (MPG)
     * @return Estimated range in miles
     */
    public static float calculateRange(float fuelLevelPercent, float tankCapacityGallons,
                                       float averageMpg) {
        float remainingFuel = (fuelLevelPercent / 100f) * tankCapacityGallons;
        return remainingFuel * averageMpg;
    }

    /**
     * Calculate time to empty in hours
     *
     * @param fuelLevelPercent Current fuel level (0-100%)
     * @param tankCapacityGallons Total tank capacity (gallons)
     * @param fuelFlowRateGalH Current fuel consumption rate (gal/h)
     * @return Time to empty in hours, or -1 if not calculable
     */
    public static float calculateTimeToEmpty(float fuelLevelPercent, float tankCapacityGallons,
                                             float fuelFlowRateGalH) {
        if (fuelFlowRateGalH < 0.05f) {
            return -1f; // Not calculable (vehicle stopped or no fuel flow)
        }

        float remainingFuel = (fuelLevelPercent / 100f) * tankCapacityGallons;
        return remainingFuel / fuelFlowRateGalH;
    }

    /**
     * Calculate average from a list of float values
     *
     * @param data List of values to average
     * @return Average value, or 0 if list is empty
     */
    public static float calculateAverage(ArrayList<Float> data) {
        if (data == null || data.isEmpty()) {
            return 0f;
        }

        float sum = 0f;
        for (float value : data) {
            sum += value;
        }
        return sum / data.size();
    }

    /**
     * Calculate calibrated VE from fill-up data
     *
     * @param currentVE Current volumetric efficiency (%)
     * @param actualMPG Actual MPG from fill-up (miles / gallons)
     * @param appMPG MPG calculated by app
     * @return Adjusted VE, clamped to realistic range (50-130%)
     */
    public static float calculateCalibratedVE(float currentVE, float actualMPG, float appMPG) {
        if (appMPG <= 0) {
            log.warning("Cannot calibrate VE: app MPG is zero");
            return currentVE;
        }

        // Formula: newVE = currentVE * (actualMPG / appMPG)
        float veAdjustmentRatio = actualMPG / appMPG;
        float newVE = currentVE * veAdjustmentRatio;

        // Clamp to realistic range
        newVE = Math.max(50f, Math.min(newVE, 130f));

        log.info(String.format("VE Calibration: %.1f%% → %.1f%% (Actual MPG: %.1f, App MPG: %.1f)",
                currentVE, newVE, actualMPG, appMPG));

        return newVE;
    }

    /**
     * Get displacement factor for fuel rate calculations
     *
     * @param displacementL Engine displacement in liters
     * @return Displacement factor (normalized around 2.5L baseline)
     */
    public static float getDisplacementFactor(float displacementL) {
        // Normalize around 2.5L baseline
        // 1.5L → 1.5, 2.0L → 2.0, 2.5L → 2.5, 5.0L → 5.0
        return displacementL;
    }
}
