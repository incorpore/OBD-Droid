package com.obddroid.ecu;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Represents a single ECU scan session
 * Contains timestamp, VIN, and all discovered ECUs
 */
public class EcuScan implements Serializable {
    private static final long serialVersionUID = 1L;

    private long timestamp;
    private String vin;
    private String label;  // Optional user label like "Pre-tune baseline"
    private List<EcuInfo> ecus;

    public EcuScan() {
        this.timestamp = System.currentTimeMillis();
        this.ecus = new ArrayList<>();
    }

    public EcuScan(String vin, List<EcuInfo> ecus) {
        this.timestamp = System.currentTimeMillis();
        this.vin = vin;
        this.ecus = new ArrayList<>(ecus);
    }

    public EcuScan(long timestamp, String vin, List<EcuInfo> ecus, String label) {
        this.timestamp = timestamp;
        this.vin = vin;
        this.ecus = new ArrayList<>(ecus);
        this.label = label;
    }

    // Getters and setters
    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public Date getDate() {
        return new Date(timestamp);
    }

    public String getVin() {
        return vin;
    }

    public void setVin(String vin) {
        this.vin = vin;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public List<EcuInfo> getEcus() {
        return ecus;
    }

    public void setEcus(List<EcuInfo> ecus) {
        this.ecus = new ArrayList<>(ecus);
    }

    public int getEcuCount() {
        return ecus != null ? ecus.size() : 0;
    }

    /**
     * Find ECU by address
     */
    public EcuInfo getEcuByAddress(int address) {
        if (ecus == null) return null;
        for (EcuInfo ecu : ecus) {
            if (ecu.getAddress() == address) {
                return ecu;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "EcuScan{" +
                "timestamp=" + new Date(timestamp) +
                ", vin='" + vin + '\'' +
                ", label='" + label + '\'' +
                ", ecuCount=" + getEcuCount() +
                '}';
    }
}
