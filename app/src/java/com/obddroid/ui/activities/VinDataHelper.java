package com.obddroid.ui.activities;

import android.os.Handler;
import android.os.Looper;

import com.obddroid.ecu.EcuDataPv;
import com.obddroid.obd.ObdProt;
import com.obddroid.common.ProcessVariables.PvChange;
import com.obddroid.common.ProcessVariables.ProcessVar;
import com.obddroid.common.ProcessVariables.TypedPvList;
import com.obddroid.services.CommService;
import com.obddroid.services.VehicleManager;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Helper that encapsulates VIN related detection/retrieval logic.
 */
final class VinDataHelper
{
    private static final Logger log = Logger.getLogger(VinDataHelper.class.getName());

    // Timeout for VIN retrieval (15 seconds)
    private static final long VIN_RETRIEVAL_TIMEOUT_MS = 15000;

    // Handler and runnable for timeout management
    private static final Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private static Runnable vinTimeoutRunnable = null;

    private VinDataHelper()
    {
        // Utility class
    }

    /**
     * Cancel any pending VIN timeout
     */
    private static void cancelVinTimeout()
    {
        if (vinTimeoutRunnable != null)
        {
            timeoutHandler.removeCallbacks(vinTimeoutRunnable);
            vinTimeoutRunnable = null;
            log.info("VIN retrieval timeout cancelled");
        }
    }

    /**
     * Check if a PvChange contains VIN data and notify VehicleManager.
     * This allows VIN detection without switching to the Vehicle Info page.
     */
    static void checkForVinAndNotify(PvChange change)
    {
        try
        {
            Object eventValue = change.getValue();
            Object eventSource = change.getSource();
            Object eventKey = change.getKey();

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
                EcuDataPv item = null;
                if (eventKey instanceof Integer)
                {
                    item = ObdProt.VidPvs.getTyped((Integer) eventKey);
                }
                else
                {
                    try
                    {
                        int numericKey = Integer.parseInt(String.valueOf(eventKey));
                        item = ObdProt.VidPvs.getTyped(numericKey);
                    }
                    catch (NumberFormatException ignored)
                    {
                        // Ignore non-numeric keys
                    }
                }
                if (item != null)
                {
                    dataPv = item;
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
                        // Cancel timeout since VIN was successfully retrieved
                        cancelVinTimeout();
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
            log.log(Level.WARNING, "Error checking for VIN in PvChange", e);
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
        TypedPvList<Object, ProcessVar> vehicleInfoStore =
            ObdProt.getDataService().getTypedStoreForService(ObdProt.OBD_SVC_VEH_INFO);
        boolean hasMode9Data = vehicleInfoStore != null && !vehicleInfoStore.isEmpty();

        // Query if: no VIN, OR VIN exists but Mode 9 data is missing/stale
        if ((currentVin == null || currentVin.isEmpty()) || !hasMode9Data)
        {
            log.info("ECU Selected, requesting Mode 9 data (VIN cached: " +
                    (currentVin != null && !currentVin.isEmpty()) +
                    ", Mode 9 data present: " + hasMode9Data + ")");

            // Cancel any existing timeout
            cancelVinTimeout();

            // Schedule timeout for VIN retrieval
            vinTimeoutRunnable = new Runnable()
            {
                @Override
                public void run()
                {
                    log.warning("VIN retrieval timed out after " + VIN_RETRIEVAL_TIMEOUT_MS + "ms");
                    VehicleManager vm = VehicleManager.getInstance();
                    // Check if VIN was retrieved during timeout period
                    if (vm.getCurrentVIN() == null || vm.getCurrentVIN().isEmpty())
                    {
                        log.info("Calling handleVINTimeout - Mode 9 may not be supported by this vehicle");
                        vm.handleVINTimeout();
                    }
                    else
                    {
                        log.info("VIN was retrieved during timeout period, ignoring timeout");
                    }
                    vinTimeoutRunnable = null;
                }
            };

            new Handler(Looper.getMainLooper()).postDelayed(() ->
            {
                log.info("Clearing stale vehicle info cache before Mode 9 request");
                if (vehicleInfoStore != null) {
                    vehicleInfoStore.clear();
                }
                CommService.elm.getCachedVehicleInfo().clear();

                log.info("Requesting Mode 9 data (full scan - VIN + all vehicle info)");
                // Mode 9 will scan all supported PIDs and naturally complete when pidsWrapped=true
                // VIN decode happens async when VIN is detected
                // Footer receives all Mode 9 data as it arrives
                CommService.elm.setService(ObdProt.OBD_SVC_VEH_INFO, false);

                // Start timeout AFTER Mode 9 request is sent (3 second delay + 15 second timeout = 18 seconds total)
                log.info("Starting VIN retrieval timeout (" + VIN_RETRIEVAL_TIMEOUT_MS + "ms)");
                timeoutHandler.postDelayed(vinTimeoutRunnable, VIN_RETRIEVAL_TIMEOUT_MS);
            }, 3000); // Increased from 1000ms to 3000ms to allow ECU proper initialization time
        }
    }
}
