package com.obddroid.utils;

/**
 * Vehicle data model - compatible with previous NHTSA decoder
 * Now populated from Corgi's offline vPIC database
 */
public class VehicleData {

    // Core fields
    public String make;
    public String model;
    public String modelYear;
    public String bodyClass;
    public String vehicleType;
    public String trim;
    public String series;

    // Additional fields
    public String manufacturer;
    public String plantCountry;
    public String plantCity;
    public String plantState;
    public String driveType;
    public String fuelType;
    public String engineConfiguration;
    public String transmission;
    public String transmissionStyle;
    public String transmissionSpeeds;
    public String bodyStyle;
    public String doors;
    public String wheelBase;

    // Engine specifications
    public String displacementL;        // Engine displacement in liters
    public String displacementCC;       // Engine displacement in cubic centimeters
    public String engineCylinders;      // Number of cylinders
    public String engineModel;          // Engine model name
    public String fuelTypePrimary;      // Primary fuel type
    public String electrificationLevel; // Hybrid/EV level

    // Weight specifications
    public String gvwr;                 // Gross Vehicle Weight Rating
    public String curbWeight;           // Curb weight

    // Metadata
    public String vin;
    private boolean valid;
    private String errorMessage;
    private String message;  // Decode message (e.g., "Decoded offline")

    public VehicleData() {
        this.valid = false;
    }

    // Getters (maintaining API compatibility)
    public String getMake() {
        return make;
    }

    public String getModel() {
        return model;
    }

    public String getModelYear() {
        return modelYear;
    }

    public String getBodyClass() {
        return bodyClass;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public String getPlantCountry() {
        return plantCountry;
    }

    public String getDriveType() {
        return driveType;
    }

    public String getFuelType() {
        return fuelType;
    }

    public String getEngineConfiguration() {
        return engineConfiguration;
    }

    public String getTransmission() {
        return transmission;
    }

    public boolean isValid() {
        return valid;
    }

    /**
     * Get display name (e.g., "2003 Honda Accord")
     */
    public String getDisplayName() {
        StringBuilder sb = new StringBuilder();
        if (modelYear != null) {
            sb.append(modelYear).append(" ");
        }
        if (make != null) {
            sb.append(make);
        }
        if (model != null) {
            sb.append(" ").append(model);
        }
        return sb.toString().trim();
    }

    /**
     * Get engine description combining available engine data
     */
    public String getEngineDescription() {
        StringBuilder sb = new StringBuilder();
        if (displacementL != null) {
            sb.append(displacementL).append("L");
        }
        if (engineCylinders != null) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(engineCylinders).append("-cyl");
        }
        if (engineConfiguration != null) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(engineConfiguration);
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getVin() {
        return vin;
    }

    // Setters
    public void setMake(String make) {
        this.make = make;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void setModelYear(String modelYear) {
        this.modelYear = modelYear;
    }

    public void setBodyClass(String bodyClass) {
        this.bodyClass = bodyClass;
    }

    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public void setPlantCountry(String plantCountry) {
        this.plantCountry = plantCountry;
    }

    public void setDriveType(String driveType) {
        this.driveType = driveType;
    }

    public void setFuelType(String fuelType) {
        this.fuelType = fuelType;
    }

    public void setEngineConfiguration(String engineConfiguration) {
        this.engineConfiguration = engineConfiguration;
    }

    public void setTransmission(String transmission) {
        this.transmission = transmission;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public void setVin(String vin) {
        this.vin = vin;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    /**
     * Create VehicleData from Corgi decoder result
     */
    public static VehicleData fromCorgiInfo(CorgiVINDecoder.VehicleInfo info) {
        VehicleData data = new VehicleData();
        data.vin = info.vin;
        data.make = info.make;
        data.model = info.model;
        data.modelYear = info.modelYear;
        data.series = info.series;
        data.trim = info.trim;
        data.bodyClass = info.bodyClass != null ? info.bodyClass : info.bodyStyle;
        data.bodyStyle = info.bodyStyle;
        data.vehicleType = info.vehicleType;
        data.manufacturer = info.manufacturer;
        data.plantCountry = info.plantCountry;
        data.driveType = info.driveType;
        data.fuelType = info.fuelType;
        data.fuelTypePrimary = info.fuelTypePrimary;
        data.engineConfiguration = info.engineConfiguration;
        data.transmission = info.transmission;
        data.transmissionStyle = info.transmissionStyle;
        data.transmissionSpeeds = info.transmissionSpeeds;
        data.displacementL = info.displacementL;
        data.displacementCC = info.displacementCC;
        data.engineCylinders = info.engineCylinders;
        data.engineModel = info.engineModel;
        data.doors = info.doors;
        data.wheelBase = info.wheelBase;
        data.gvwr = info.gvwr;
        data.curbWeight = info.curbWeight;
        data.valid = info.valid;
        data.errorMessage = info.errorMessage;
        return data;
    }

    @Override
    public String toString() {
        if (!valid) {
            return "Invalid: " + errorMessage;
        }
        return String.format("%s %s %s",
                modelYear != null ? modelYear : "Unknown",
                make != null ? make : "Unknown",
                model != null ? model : "");
    }
}
