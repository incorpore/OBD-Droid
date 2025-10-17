package com.obddroid.ui.activities;

import android.os.Handler;
import android.os.Looper;

import com.obddroid.core.ecu.EcuDataPv;
import com.obddroid.core.obd.ObdProt;
import com.obddroid.core.pvs.PvChangeEvent;
import com.obddroid.services.CommService;
import com.obddroid.vehicle.VehicleManager;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Helper that encapsulates VIN related detection/retrieval logic.
 */
final class VinDataHelper
{
    private static final Logger log = Logger.getLogger(VinDataHelper.class.getName());

    private VinDataHelper()
    {
        // Utility class
    }

    /**
     * Check if a PvChangeEvent contains VIN data and notify VehicleManager.
     * This allows VIN detection without switching to the Vehicle Info page.
     */
    static void checkForVinAndNotify(PvChangeEvent event)
    {
        try
        {
            Object eventValue = event.getValue();
            Object eventSource = event.getSource();
            Object eventKey = event.getKey();

            log.info("checkForVinAndNotify called - eventValue type: " +
                    (eventValue != null ? eventValue.getClass().getName() : "null") +
                    ", eventSource type: " +
                    (eventSource != null ? eventSource.getClass().getName() : "null") +
                    ", eventKey: " + eventKey);

            EcuDataPv dataPv = null;

            if (eventValue instanceof EcuDataPv)
            {
                dataPv = (EcuDataPv) eventValue;
                log.info("VidPvs PV_ADDED - checking for VIN");
            }
            else if (eventValue instanceof Object[])
            {
                Object[] arr = (Object[]) eventValue;
                log.info("VidPvs PV_ADDED - array contains " + arr.length + " items");
                for (Object item : arr)
                {
                    if (item instanceof EcuDataPv)
                    {
                        EcuDataPv pv = (EcuDataPv) item;
                        String desc = String.valueOf(pv.get(EcuDataPv.FID_DESCRIPT));
                        Object val = pv.get(EcuDataPv.FID_VALUE);
                        log.info("  Checking item: " + desc + ", value: " + val +
                                ", len: " + (val != null ? val.toString().length() : 0));

                        if (desc != null && desc.toLowerCase().contains("vehicle identification") &&
                                val != null && val.toString().trim().length() == 17)
                        {
                            dataPv = pv;
                            log.info("  Found VIN in array!");
                            break;
                        }
                    }
                }
            }
            else if (eventSource instanceof EcuDataPv)
            {
                dataPv = (EcuDataPv) eventSource;
                log.info("VidPvs PV_MODIFIED - checking for VIN");
            }
            else if (eventKey != null)
            {
                log.info("Trying to get EcuDataPv from VidPvs using key: " + eventKey);
                Object item = ObdProt.VidPvs.get(eventKey);
                if (item instanceof EcuDataPv)
                {
                    dataPv = (EcuDataPv) item;
                    log.info("Successfully retrieved EcuDataPv from VidPvs");
                }
            }

            if (dataPv != null)
            {
                String description = String.valueOf(dataPv.get(EcuDataPv.FID_DESCRIPT));
                Object vinValue = dataPv.get(EcuDataPv.FID_VALUE);

                log.info("VidPvs item - desc: " + description + ", value: " + vinValue +
                        ", valueLen: " + (vinValue != null ? vinValue.toString().length() : 0));

                if (description != null && description.toLowerCase().contains("vehicle identification") &&
                        vinValue != null && vinValue.toString().trim().length() == 17)
                {
                    String vin = vinValue.toString().trim();
                    VehicleManager vm = VehicleManager.getInstance();
                    String currentVin = vm.getCurrentVIN();

                    if (currentVin == null || !currentVin.equals(vin))
                    {
                        log.info("VIN DETECTED from Mode 9: " + vin);
                        // Set VIN and trigger async decode (non-blocking)
                        // Mode 9 scan continues in background to populate footer with all data
                        vm.setVIN(vin);
                        log.info("Mode 9 scan continues - footer will receive all data");
                    }
                }
            }
        }
        catch (Exception e)
        {
            log.log(Level.WARNING, "Error checking for VIN in PvChangeEvent", e);
        }
    }

    /**
     * Trigger VIN retrieval after ECU is selected - requests Mode 9 data in background
     * without switching the UI to Vehicle Info page.
     */
    static void triggerVinRetrieval()
    {
        VehicleManager vm = VehicleManager.getInstance();
        String currentVin = vm.getCurrentVIN();

        // Check if Mode 9 data is present (even if VIN is cached)
        // This handles reconnection scenarios where VIN is cached but Mode 9 data is stale
        boolean hasMode9Data = ObdProt.VidPvs != null && !ObdProt.VidPvs.isEmpty();

        // Query if: no VIN, OR VIN exists but Mode 9 data is missing/stale
        if ((currentVin == null || currentVin.isEmpty()) || !hasMode9Data)
        {
            log.info("ECU Selected, requesting Mode 9 data (VIN cached: " +
                    (currentVin != null && !currentVin.isEmpty()) +
                    ", Mode 9 data present: " + hasMode9Data + ")");

            new Handler(Looper.getMainLooper()).postDelayed(() ->
            {
                log.info("Clearing stale vehicle info cache before Mode 9 request");
                ObdProt.VidPvs.clear();
                CommService.elm.getCachedVehicleInfo().clear();

                log.info("Requesting Mode 9 data (full scan - VIN + all vehicle info)");
                // Mode 9 will scan all supported PIDs and naturally complete when pidsWrapped=true
                // VIN decode happens async when VIN is detected
                // Footer receives all Mode 9 data as it arrives
                CommService.elm.setService(ObdProt.OBD_SVC_VEH_INFO, false);
            }, 1000);
        }
    }
}
