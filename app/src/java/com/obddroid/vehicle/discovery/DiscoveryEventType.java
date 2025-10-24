package com.obddroid.vehicle.discovery;

/**
 * Types of discovery events emitted while scanning for vehicle ECUs.
 */
public enum DiscoveryEventType {
    SESSION_START,
    SESSION_END,
    CAPABILITY,
    STATUS,
    PASSIVE_ADDRESS,
    ECU_DISCOVERED,
    ECU_UPDATED,
    VEHICLE_ID,
    ERROR
}
