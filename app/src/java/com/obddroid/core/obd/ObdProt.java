
package com.obddroid.core.obd;

import android.util.Log;
import com.obddroid.core.ecu.Conversion;
import com.obddroid.core.ecu.EcuCodeItem;
import com.obddroid.core.ecu.EcuCodeList;
import com.obddroid.core.ecu.EcuConversions;
import com.obddroid.core.ecu.EcuDataItem;
import com.obddroid.core.ecu.EcuDataItems;
import com.obddroid.core.ecu.EcuDataPv;
import com.obddroid.core.ecu.ObdCodeItem;
import com.obddroid.core.ecu.ObdPid;
import com.obddroid.core.obd.ProtoHeader;
import com.obddroid.core.obd.TelegramListener;
import com.obddroid.core.obd.TelegramWriter;
import com.obddroid.core.pvs.PvChangeEvent;
import com.obddroid.core.pvs.PvList;
import com.obddroid.services.ObdDataService;
import com.obddroid.services.IDataManager;

import java.beans.PropertyChangeEvent;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OBD communication protocol layer
 *
 * The OBD protocol supports services which serve multiple PID's
 * A request of PID which is a multiple of 0x20 (0x00,0x20...0xE0) for each
 * service returns a bitmask of the next 32 PIDs which are suppoted by the vehicle

 */
public class ObdProt extends ProtoHeader
        implements TelegramListener, TelegramWriter
{
    private static final String TAG = "ObdProt";
    public static final int OBD_SVC_NONE = 0x00;
    public static final int OBD_SVC_DATA = 0x01;
    public static final int OBD_SVC_FREEZEFRAME = 0x02;
    public static final int OBD_SVC_READ_CODES = 0x03;
    public static final int OBD_SVC_CLEAR_CODES = 0x04;
    public static final int OBD_SVC_O2_RESULT = 0x05;
    public static final int OBD_SVC_MON_RESULT = 0x06;
    public static final int OBD_SVC_PENDINGCODES = 0x07;
    public static final int OBD_SVC_CTRL_MODE = 0x08;
    public static final int OBD_SVC_VEH_INFO = 0x09;
    public static final int OBD_SVC_PERMACODES = 0x0A;

    /** negative response ID */
    private static final int OBD_ID_NRC = 0x7F;

    /** perform immediate reset on NRC reception? */
    private boolean isResetOnNrc()
    {
        return resetOnNrc;
    }

    /** Set protocol parameter
     * @param resetOnNrc  perform immediate reset on NRC reception?
     */
    public void setResetOnNrc(boolean resetOnNrc)
    {
        log.info(String.format("Reset on NRC = %b", resetOnNrc));
        this.resetOnNrc = resetOnNrc;
    }

    /**
     * Get human-readable name for OBD service
     * @param service OBD service code
     * @return Human-readable service name
     */
    public static String getServiceName(int service) {
        switch (service) {
            case OBD_SVC_NONE:
                return "No Service";
            case OBD_SVC_DATA:
                return "Live Data (Mode 1)";
            case OBD_SVC_FREEZEFRAME:
                return "Freeze Frame Data (Mode 2)";
            case OBD_SVC_READ_CODES:
                return "Fault Codes (Mode 3)";
            case OBD_SVC_CLEAR_CODES:
                return "Clear Fault Codes (Mode 4)";
            case OBD_SVC_O2_RESULT:
                return "O2 Sensor Test (Mode 5)";
            case OBD_SVC_MON_RESULT:
                return "Monitor Test Results (Mode 6)";
            case OBD_SVC_PENDINGCODES:
                return "Pending Codes (Mode 7)";
            case OBD_SVC_CTRL_MODE:
                return "Control Test (Mode 8)";
            case OBD_SVC_VEH_INFO:
                return "Vehicle Info (Mode 9)";
            case OBD_SVC_PERMACODES:
                return "Permanent Codes (Mode A)";
            default:
                return String.format("Service 0x%02X", service);
        }
    }

    /** negative response codes */
    public enum NRC
    {
	    GR(0x10, "General reject",DISP.ERROR, REACT.RESET),
        SNS(0x11, "Not supported", DISP.ERROR, REACT.CANCEL),
        SFNS(0x12, "Feature not available", DISP.NOTIFY, REACT.SKIP),
        IMLOIF(0x13, "Incorrect message length or invalid format", DISP.NOTIFY, REACT.SKIP),
        RTL(0x14, "Response too long", DISP.NOTIFY, REACT.SKIP),
        BRR(0x21, "Busy repeat request", DISP.NOTIFY, REACT.REPEAT),
        CNC(0x22, "Conditions not correct", DISP.ERROR, REACT.CANCEL),
        RSE(0x24, "Request sequence error", DISP.ERROR, REACT.CANCEL),
        NRFSC(0x25, "No response from sub-net component", DISP.ERROR, REACT.RESET),
        FPEORA(0x26, "Failure prevents execution of requested action", DISP.ERROR, REACT.CANCEL),
        ROOR(0x31, "Request out of range", DISP.NOTIFY, REACT.SKIP),
        SAD(0x33, "Security access denied", DISP.ERROR, REACT.CANCEL),
        IK(0x35, "Invalid key", DISP.ERROR, REACT.RESET),
        ENOA(0x36, "Exceeded number of attempts", DISP.ERROR, REACT.RESET),
        RTDNE(0x37, "Required time delay not expired", DISP.NOTIFY, REACT.REPEAT),
        UDNA(0x70, "Upload/Download not accepted", DISP.ERROR, REACT.CANCEL),
        TDS(0x71, "Transfer data suspended", DISP.NOTIFY, REACT.REPEAT),
        GPF(0x72, "General programming failure", DISP.ERROR, REACT.CANCEL),
        WBSC(0x73, "Wrong Block Sequence Counter", DISP.ERROR, REACT.CANCEL),
        RCRRP(0x78, "Request correctly received but response is pending", DISP.NOTIFY, REACT.IGNORE),
        SFNSIAS(0x7E, "Not supported in active session", DISP.NOTIFY, REACT.SKIP),
        SNSIAS(0x7F, "Not supported in active session", DISP.ERROR, REACT.CANCEL);
    
        /** NRC display classifiers */
        public enum DISP
        {
            HIDE,       /**< Do NOT display error */
            NOTIFY,     /**< Display notification (w/o confirmation) */
            WARN,       /**< Display warning (w/ confirmation) */
            ERROR       /**< Display error (w/ confirmation) */
        }
    
        /** NRC immediate protocol reaction classifiers */
        public enum REACT
        {
            IGNORE,     /**< Ignore NRC */
            SKIP,       /**< Skip last command */
            REPEAT,     /**< Repeat last command */
            CANCEL,     /**< Cancel command sequence / loop */
            RESET       /**< Reset adapter */
        }
    
        public final int code;
        public final String description;
        public final DISP disp;
        public final REACT react;

        NRC(int _code, String _description, DISP _DISPClass, REACT _REACTClass)
        {
            code = _code;
            description = _description;
            disp = _DISPClass;
            react = _REACTClass;
        }
    
        /**
         * Get NRC with specified ID (NRC-code)
         * @param id ID (NRC-code) to search
         * @return specified NRC, or null if not found
         */
        static NRC get(int id)
        {
            NRC result = null;
            for (NRC nrc : values())
            {
                if (nrc.code == id)
                {
                    result = nrc;
                    break;
                }
            }
            return result;
        }

        /** return String representative */
        public String toString(int service)
        {
            String serviceName = getServiceName(service);
            String baseMessage = String.format(description, service);

            // Provide more helpful messages for common cases
            if (code == 0x12 && service == OBD_SVC_VEH_INFO) {
                // Special case for Vehicle Info not supported (common with emulators)
                return String.format("%s not available - Common with emulators or basic OBD adapters", serviceName);
            } else if (code == 0x11) {
                // Service not supported
                return String.format("%s not supported by this ECU", serviceName);
            } else if (code == 0x12) {
                // Sub-function not supported
                return String.format("%s: Feature not available", serviceName);
            } else if (code == 0x7E || code == 0x7F) {
                // Not supported in active session
                return String.format("%s not available in current mode", serviceName);
            }

            // Default format with service name
            return String.format("%s: %s", serviceName, baseMessage);
        }
    }

    /** property name "number of codes" */
    public static final String PROP_NUM_CODES = "numCodes";
    public static final String PROP_NRC = "NRC";

    // current supported PID
    private static int currSupportedPid = 0;
    static boolean pidsWrapped = false;

    /** content of last sent message */
    static String lastTxMsg = "";
    /** content of last received message */
    static String lastRxMsg = "";
    /** Holds value of property service. */
    int service = OBD_SVC_NONE;
    /** service of last incoming message */
    private int msgService = OBD_SVC_NONE;

    /** List of PIDs supported by the vehicle */
    private static final Vector<ObdPid> pidSupported = new Vector<ObdPid>();

    /** positive response fields */
    private static final int ID_OBD_SVC = 0;
    private static final int ID_OBD_PID = 1;
    private static final int ID_OBD_FRAMEID = 2;

    /** negative response fields */
    public static final int ID_NR_ID = 0;
    private static final int ID_NR_SVC = 1;
    private static final int ID_NR_CODE = 2;

    /**
     * Negative response parameters
     * List of telegram parameters in order of appearance
     */
    private static final int[][] NR_PARAMETERS =
    /*  START,  LEN,     PARAM-TYPE     // REMARKS */
    /* ------------------------------------------- */
	{{0, 2, PT_HEX},     // ID_NR_ID
	 {2, 2, PT_HEX},     // ID_NR_SVC
	 {4, 2, PT_HEX},     // ID_NR_CODE
	};

    /**
     * List of telegram parameters in order of appearance
     */
    private static final int[][] SVC_PARAMETERS =
    /*  START,  LEN,     PARAM-TYPE     // REMARKS */
    /* ------------------------------------------- */
	{{0, 2, PT_HEX},     // ID_OBD_SVC
	};

    /**
     * List of telegram parameters in order of appearance
     */
    private static final int[][] OBD_PARAMETERS =
    /*  START,  LEN,     PARAM-TYPE     // REMARKS */
    /* ------------------------------------------- */
	{{0, 2, PT_HEX},     // ID_OBD_SVC
	 {2, 2, PT_HEX},     // ID_OBD_PID
	};

    /**
     * List of telegram parameters in order of appearance
     */
    private static final int[][] FRZFRM_PARAMETERS =
    /*  START,  LEN,     PARAM-TYPE     // REMARKS */
    /* ------------------------------------------- */
	{{0, 2, PT_HEX},     // ID_OBD_SVC
	 {2, 2, PT_HEX},     // ID_OBD_PID
	 {2, 2, PT_HEX},     // ID_OBD_FRAMEID
	};

    private static final int ID_NUM_CODES = 0;
    public static final int ID_MSK_CODES = 1;
    /**
     * List of telegram parameters in order of appearance
     */
    private static final int[][] NUMCODE_PARAMETERS =
    /*  START,  LEN,     PARAM-TYPE     // REMARKS */
    /* ------------------------------------------- */
	{{4, 2, PT_HEX},     // ID_NUM_CODES
	 {6, 6, PT_HEX},     // ID_MSK_CODES
	};

    private static final String[] OBD_DESCRIPTORS =
	{
		"OBD Service",
		"OBD PID",
	};

    /** new style data items */
    public static final EcuDataItems dataItems = new EcuDataItems();

    // Data service for managing all OBD data
    private static final ObdDataService dataService = ObdDataService.getInstance();

    /**
     * OBD data items
     * @deprecated Use getDataService().getDataForService(OBD_SVC_DATA) instead
     */
    @Deprecated
    public static PvList PidPvs = createServiceBackedPvList(OBD_SVC_DATA);

    /**
     * OBD vehicle identification items
     * @deprecated Use getDataService().getDataForService(OBD_SVC_VEH_INFO) instead
     */
    @Deprecated
    public static PvList VidPvs = createServiceBackedPvList(OBD_SVC_VEH_INFO);

    /**
     * current fault codes
     * @deprecated Use getDataService().getDataForService(OBD_SVC_READ_CODES) instead
     */
    @Deprecated
    public static PvList tCodes = createServiceBackedPvList(OBD_SVC_READ_CODES);
    /** list of known fault codes */
    private static final EcuCodeList knownCodes = EcuConversions.codeList;
    /** queue of ELM commands to be sent */
    static final Vector<String> cmdQueue = new Vector<String>();

    /**
     * Get the data service instance
     * @return ObdDataService instance
     */
    public static ObdDataService getDataService() {
        return dataService;
    }

    /**
     * Create a PvList that is backed by the data service
     * This maintains backward compatibility while routing data through the service
     */
    private static PvList createServiceBackedPvList(final int service) {
        return new PvList() {
            @Override
            public void clear() {
                super.clear();
                dataService.clearService(service);
            }

            @Override
            public Object put(Object key, Object value) {
                Object result = super.put(key, value);
                // Also update data service
                PvList serviceData = dataService.getDataForService(service);
                if (serviceData != null && serviceData != this) {
                    serviceData.put(key, value);
                }
                return result;
            }

            @Override
            public void putAll(Map m) {
                super.putAll(m);
                // Also update data service
                PvList serviceData = dataService.getDataForService(service);
                if (serviceData != null && serviceData != this) {
                    serviceData.putAll(m);
                }
            }
        };
    }
    /** freeze frame ID to request */
    private int freezeFrame_Id = 0;
    /** perform reset on NRC reception */
    private boolean resetOnNrc = false;

    // ======== ENHANCED DATA MANAGEMENT ========
    /** Permanent cache for supported PIDs - thread-safe */
    private final ConcurrentHashMap<Integer, Boolean> permanentlySupportedPIDs = new ConcurrentHashMap<>();
    /** Cache for live data PIDs - thread-safe */
    private final ConcurrentHashMap<Integer, EcuDataPv> cachedLiveDataPIDs = new ConcurrentHashMap<>();
    /** Cache for vehicle info - thread-safe */
    private final ConcurrentHashMap<Integer, EcuDataPv> cachedVehicleInfo = new ConcurrentHashMap<>();
    /** Cache for freeze frames by frame ID - bounded with LRU eviction */
    private final Map<Integer, PvList> cachedFreezeFrames = Collections.synchronizedMap(
        new LinkedHashMap<Integer, PvList>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, PvList> eldest) {
                return size() > 10; // Keep max 10 freeze frames to prevent memory leak
            }
        }
    );
    /** Cache for fault codes - thread-safe */
    private final ConcurrentHashMap<Integer, EcuCodeItem> cachedFaultCodes = new ConcurrentHashMap<>();
    /** Track if PIDs have been discovered */
    private final AtomicBoolean pidsDiscoveryComplete = new AtomicBoolean(false);
    /** Track if comprehensive init has run */
    private final AtomicBoolean comprehensiveInitComplete = new AtomicBoolean(false);
    /** Progress listener for init phases */
    private InitProgressListener initProgressListener;

    // ======== ENHANCED MANAGERS ========
    /** Freeze frame manager for proper DTC correlation */
    private final FreezeFrameManager freezeFrameManager = new FreezeFrameManager();
    /** Error handler for robust error recovery */
    private final ErrorHandler errorHandler = new ErrorHandler();

    /** Creates a new instance of ObdProt */
    ObdProt()
    {
        paddingChr = '0';
        // prepare PID PV list
        PidPvs.put(0, new EcuDataPv());
        // VidPvs doesn't need a placeholder - vehicle info items are added as discovered
        tCodes.put(0, new ObdCodeItem(0, "No trouble codes set"));
    }

    /**
     * set Freeze frame id to be requested
     * @param freezeFrame_Id ID of freeze frame to be requested
     */
    public void setFreezeFrame_Id(int freezeFrame_Id)
    {
        log.info(String.format("FreezeFrame ID: %d", freezeFrame_Id));
        this.freezeFrame_Id = freezeFrame_Id;
        setService(OBD_SVC_FREEZEFRAME, true);
    }

    /**
     * list of parameters for specific protocol
     * @return complete set of protocol parameters
     */
    public int[][] getTelegramParams()
    {
        return (getTelegramParams(msgService));
    }

    /**
     * list of parameters for specific protocol
     * @param service Service which this header is requested for
     * @return complete set of protocol parameters
     */
    private int[][] getTelegramParams(int service)
    {
        int fldMap[][];
        switch (service)
        {
            // negative response
            case OBD_ID_NRC:
                fldMap = NR_PARAMETERS;
                break;

            case OBD_SVC_FREEZEFRAME:
                fldMap = FRZFRM_PARAMETERS;
                break;

            case OBD_SVC_READ_CODES:
            case OBD_SVC_PENDINGCODES:
            case OBD_SVC_PERMACODES:
            case OBD_SVC_CLEAR_CODES:
                fldMap = SVC_PARAMETERS;
                break;

            default:
                fldMap = OBD_PARAMETERS;
        }
        return (fldMap);
    }


    /**
     * return message footer for protocol payload
     * @return buffer of message footer
     */
    public char[] getFooter()
    {
        return (emptyBuffer);
    }

    /**
     * create a new telegram header for selected payload data buffer
     * inclunding setting all ID's, sizes and validity issues
     * @param buffer buffer of payload data
     * @return buffer of new telegram header
     */
    protected char[] getNewHeader(char[] buffer)
    {
        return (getNewHeader(buffer, OBD_SVC_DATA, Integer.valueOf(0)));
    }

    /**
     * create a new telegram header inclunding setting all ID's, sizes and
     * validity issues
     * @param buffer buffer of payload data
     * @param type type of telegram content
     * @param id identifier for telegram (may be null)
     * @return buffer of new telegram header
     */
    @SuppressWarnings("fallthrough")
    protected char[] getNewHeader(char[] buffer, int type, Object id)
    {
        int[][] fldMap = getTelegramParams(type);
        char[] header = createEmptyBuffer(fldMap, '0');
        setParamValue(ID_OBD_SVC, fldMap, header, Integer.valueOf(type));
        switch (type)
        {
            // these commands do not require parametrs
            case OBD_SVC_READ_CODES:
            case OBD_SVC_PENDINGCODES:
            case OBD_SVC_PERMACODES:
            case OBD_SVC_CLEAR_CODES:
                break;

            // freezeframes require additional frame id
            case OBD_SVC_FREEZEFRAME:
                setParamValue(ID_OBD_FRAMEID, fldMap, header, freezeFrame_Id);
                // NO break here

                // all other commands require PID to be set
            default:
                setParamValue(ID_OBD_PID, fldMap, header, id);
        }
        return (header);
    }

    /**
     * list of parameter descriptions for specific protocol
     * @return complete set of protocol parameter description strings
     */
    protected String[] getParamDescriptors()
    {
        return (OBD_DESCRIPTORS);
    }

    /**
     * prepare process variables for each PID
     * @param pvList list of process vars
     */
    private void preparePidPvs(int obdService, PvList pvList)
    {
        // reset fixed PIDs
        resetFixedPid();

        HashMap<String, EcuDataPv> newList = new HashMap<String, EcuDataPv>();
        for (ObdPid currPid : pidSupported)
        {
            Vector<EcuDataItem> items = dataItems.getPidDataItems(obdService, currPid.intValue());
            // if no items defined, create dummy item
            if (items == null)
            {
                log.warning(String.format("unknown PID %02X for service %02X", currPid.intValue(), obdService));

                // create new dummy item / OneToOne conversion
                Conversion[] dummyCnvs = {EcuConversions.dfltCnv, EcuConversions.dfltCnv};
                EcuDataItem newItem = new EcuDataItem(currPid.intValue(), 0, 0, 0, 32, 0xFFFFFFFF, dummyCnvs,
                                                      "%#08x", null, null, 0,
                                                      String.format("PID %02X", currPid.intValue()),
                                                      String.format("PID_%02X", currPid.intValue())
                                                     );
                dataItems.appendItemToService(obdService, newItem);

                // re-load data items for this PID
                items = dataItems.getPidDataItems(obdService, currPid.intValue());
            }
            // loop through all items found ...
            for (EcuDataItem pidPv : items)
            {
                if (pidPv != null)
                {
                    newList.put(pidPv.toString(), pidPv.pv);
                }
            }
        }
        pvList.putAll(newList, PvChangeEvent.PV_ADDED, false);
    }

    /**
     * mark all PIDs supported by the vehicle
     * @param start Start PID (multiple of 0x20) to process bitmask for
     * @param bitmask 32-Bit bitmask which indicates support for the next 32 PIDs
     */
    private synchronized void markSupportedPids(int obdService, int start, long bitmask,
                                                PvList pvList)
    {
        currSupportedPid = 0;

        // Clear PID list on initial bitmask (offset 0)
        if( start == 0)
        {
            pidSupported.clear();
        }

        // loop through bits and mark corresponding PIDs as supported
        for (int i = 0; i < 0x1F; i++)
        {
            if ((bitmask & (0x80000000L >> i)) != 0)
            {
                pidSupported.add(new ObdPid(i + start + 1));
            }
        }

        log.fine(Long.toHexString(bitmask).toUpperCase()
                     + "(" + Long.toHexString(start) + "):"
                     + pidSupported);

        // if next block may be requested
        if ((bitmask & 1) != 0)
        {
            // request next block
            cmdQueue.add(String.format("%02X%02X", obdService, start + 0x20));
        }
        else
        {
            // For Mode 09, queue all supported PIDs at once for batch processing
            if (obdService == OBD_SVC_VEH_INFO && start == 0) {
                log.info("Queueing all Mode 09 PIDs for batch processing");
                // Queue all supported PIDs for rapid sequential processing
                for (ObdPid pid : pidSupported) {
                    cmdQueue.add(String.format("%02X%02X", obdService, pid.intValue()));
                }
            }

            // setup PID PVs
            preparePidPvs(obdService, pvList);
        }
    }

    /** Holds value of property numCodes. */
    private int numCodes;

    /** fixed PIDs to limit PID loop to single access */
    private static final Vector<ObdPid> fixedPids = new Vector<ObdPid>();

    /**
     * Set fixed PID for faster data update
     * @param pidCodes the fixedPid to set
     */
    public static synchronized void setFixedPid(int[] pidCodes)
    {
        for (ObdPid currPid : pidSupported)
        {
            if (Arrays.binarySearch(pidCodes, currPid.intValue()) >= 0)
            {
                fixedPids.add(currPid);
            }
        }
    }

    public static synchronized void resetFixedPid()
    {
        fixedPids.clear();
    }

    /**
     * get the next available supported PID
     * @return next available supported PID
     */
    synchronized Integer getNextSupportedPid()
    {
        Integer result = 0;
        /* get corresponding PID list */
        Vector<ObdPid> pidsToCheck = (fixedPids.size() > 0) ? fixedPids : pidSupported;
        try
        {
            // For Mode 09, prioritize important PIDs on first pass
            if (service == OBD_SVC_VEH_INFO && !pidsWrapped) {
                // Check for unprioritized critical PIDs
                ObdPid vinPid = null;
                ObdPid ecuNamePid = null;
                ObdPid calIdPid = null;

                for (ObdPid p : pidsToCheck) {
                    if (p.intValue() == 0x02 && p.getNextRequest() == 0) vinPid = p;
                    else if (p.intValue() == 0x0A && p.getNextRequest() == 0) ecuNamePid = p;
                    else if (p.intValue() == 0x04 && p.getNextRequest() == 0) calIdPid = p;
                }

                // Request VIN first if not yet requested
                if (vinPid != null) {
                    vinPid.setNextRequest(System.currentTimeMillis());
                    return vinPid.intValue();
                }
                // Then ECU name
                if (ecuNamePid != null) {
                    ecuNamePid.setNextRequest(System.currentTimeMillis());
                    return ecuNamePid.intValue();
                }
                // Then Calibration ID
                if (calIdPid != null) {
                    calIdPid.setNextRequest(System.currentTimeMillis());
                    return calIdPid.intValue();
                }
            }

            /* sort by next expected request */
            Collections.sort(pidsToCheck, ObdPid.requestSorter);
            ObdPid pid = pidsToCheck.firstElement();
            /* detect wrap around in PID list */
            pidsWrapped = pid.getNextRequest() != 0;
            /* mark PID as handled */
            pid.setNextRequest(System.currentTimeMillis());
            /* and return first list element */
            result = pid.intValue();
        }
        catch(Exception e)
        {
            /* ignore */
        }

        return result;
    }

    /**
     * handle OBD response telegram
     * @param buffer - telegram buffer
     * @return number of listeners notified
     */
    @Override
    @SuppressWarnings("fallthrough")
    public synchronized int handleTelegram(char[] buffer)
    {
        int result = 0;
        int msgPid;

        if (checkTelegram(buffer))
        {
            try
            {
                msgService = (Integer) getParamValue(ID_OBD_SVC, buffer);
                // check for negative result
                if (msgService == OBD_ID_NRC)
                {
                    // get NR service
                    int svc = (Integer) getParamValue(ID_NR_SVC, buffer);
                    // get NRC code
                    int nrcCode = (Integer) getParamValue(ID_NR_CODE, buffer);
                    // get NRC object
                    NRC nrc = NRC.get(nrcCode);
                    // create NRC error message
                    String error = nrc.toString(svc);
                    // log error
                    log.severe(error);
                    // notify change listeners
                    firePropertyChange(new PropertyChangeEvent(this, PROP_NRC, nrc, error));
                    // handle NRC reaction
                    switch(nrc.react)
                    {
                        case RESET:
                            if (isResetOnNrc())
                            {
                                // perform immediate reset because NRC reception
                                reset();
                            } else
                            {
                                // otherwise just switch off any active service
                                setService(OBD_SVC_NONE, true);
                            }
                            break;
    
                        case CANCEL:
                            // switch off any active service
                            setService(OBD_SVC_NONE, true);
                            break;
    
                        case REPEAT:
                            // Repeat last TX message
                            sendTelegram(lastTxMsg.toCharArray());
                            break;
                            
                        case SKIP:
                        case IGNORE:
                        default:
                            // Intentionally do noting
                    }
                    // handling finished
                    return result;
                }

                // positive response -> mask service ID
                msgService &= ~0x40;
                // check service of message
                switch (msgService)
                {
                    // OBD Data frame
                    case OBD_SVC_FREEZEFRAME:
                    case OBD_SVC_DATA:
                        msgPid = (Integer) getParamValue(ID_OBD_PID, buffer);

                        // Special handling for freeze frame to populate data service
                        if (msgService == OBD_SVC_FREEZEFRAME) {
                            // Get frame ID for freeze frame data
                            Integer frameId = (Integer) getParamValue(ID_OBD_FRAMEID, buffer);
                            if (frameId == null) frameId = 0;

                            // Notify data service about freeze frame data
                            char[] charPayload = getPayLoad(buffer);
                            byte[] payload = new byte[charPayload.length];
                            for (int i = 0; i < charPayload.length; i++) {
                                payload[i] = (byte) charPayload[i];
                            }
                            dataService.onDataReceived(msgService, msgPid, payload);

                            // Store in freeze frame specific storage
                            PvList freezeData = dataService.getFreezeFrameData(frameId);
                            if (msgPid > 0 && !freezeData.containsKey(msgPid)) {
                                EcuDataPv pv = new EcuDataPv();
                                pv.put(EcuDataPv.FID_PID, Integer.valueOf(msgPid));
                                freezeData.put(msgPid, pv);
                            }
                        }
                        switch (msgPid)
                        {
                            case 0x00:
                            case 0x20:
                            case 0x40:
                            case 0x60:
                            case 0x80:
                            case 0xA0:
                            case 0xC0:
                            case 0xE0:
                                // Check for optional message count byte, find offset to payload
                                int offset = (buffer.length % 4 == 0) ? 4 : 6;
                                // get payload data and mark the indicated supported PIDs
                                long msgPayload = Long.valueOf(new String(buffer, offset, 8), 16);
                                // For freeze frame, use the right PvList
                                if (msgService == OBD_SVC_FREEZEFRAME) {
                                    // Mark PIDs and also populate the data service
                                    markSupportedPids(msgService, msgPid, msgPayload, PidPvs);
                                    // Ensure freeze frame data is initialized
                                    dataService.initializeFreezeFrameData();
                                } else {
                                    markSupportedPids(msgService, msgPid, msgPayload, PidPvs);
                                }
                                break;

                            // OBD number of fault codes
                            case 1:
                                msgPayload = ((Integer) getParamValue(ID_NUM_CODES,
                                                                      NUMCODE_PARAMETERS,
                                                                      buffer)).longValue();
                                setNumCodes(Long.valueOf(msgPayload).intValue());
                                // no break here ...
                            default:
                                long updatePeriod =
                                    dataItems.updateDataItems(msgService,
                                                            msgPid,
                                                            hexToBytes(String.valueOf(
                                                                    getPayLoad(buffer))));

                                // Special handling for freeze frame - ensure data is properly stored
                                if (msgService == OBD_SVC_FREEZEFRAME) {
                                    // Get the frame ID (usually 0)
                                    Integer frameId = (Integer) getParamValue(ID_OBD_FRAMEID, buffer);
                                    if (frameId == null) frameId = 0;

                                    // Get the freeze frame store from data service
                                    PvList freezeStore = dataService.getFreezeFrameData(frameId);

                                    // Get the data items for this PID
                                    Vector<EcuDataItem> items = dataItems.getPidDataItems(OBD_SVC_DATA, msgPid);
                                    if (items != null && !items.isEmpty()) {
                                        for (EcuDataItem item : items) {
                                            // Create or update the PV for this item
                                            EcuDataPv pv = (EcuDataPv) freezeStore.get(item);
                                            if (pv == null) {
                                                pv = new EcuDataPv();
                                                pv.put(EcuDataPv.FID_PID, Integer.valueOf(msgPid));
                                                freezeStore.put(item, pv);
                                            }
                                            // Update the PV with the actual data from the item
                                            pv.put(EcuDataPv.FID_DESCRIPT, item.label);
                                            if (item.pv != null) {
                                                Object value = item.pv.get(EcuDataPv.FID_VALUE);
                                                // Check if value is a byte array and convert to hex string
                                                if (value instanceof byte[]) {
                                                    byte[] bytes = (byte[]) value;
                                                    StringBuilder hex = new StringBuilder();
                                                    for (byte b : bytes) {
                                                        hex.append(String.format("%02X", b & 0xFF));
                                                    }
                                                    pv.put(EcuDataPv.FID_VALUE, hex.toString());
                                                } else if (value != null) {
                                                    pv.put(EcuDataPv.FID_VALUE, value);
                                                }
                                                Object units = item.pv.get(EcuDataPv.FID_UNITS);
                                                if (units != null) {
                                                    pv.put(EcuDataPv.FID_UNITS, units);
                                                }
                                            }
                                        }
                                    } else {
                                        // No data item definition - create basic PID entry with hex value
                                        EcuDataPv pv = (EcuDataPv) freezeStore.get(msgPid);
                                        if (pv == null) {
                                            pv = new EcuDataPv();
                                            pv.put(EcuDataPv.FID_PID, Integer.valueOf(msgPid));
                                            freezeStore.put(msgPid, pv);
                                        }
                                        // Set description based on known PIDs
                                        String description = getPidDescription(msgPid);
                                        pv.put(EcuDataPv.FID_DESCRIPT, description);
                                        // Convert payload to hex string
                                        char[] payload = getPayLoad(buffer);
                                        pv.put(EcuDataPv.FID_VALUE, String.valueOf(payload));
                                    }

                                    // Store the updated freeze frame data
                                    dataService.storeFreezeFrameData(frameId, freezeStore);
                                }

                                /* Update expected request timestamp for PID */
                                for( ObdPid pid : pidSupported)
                                {
                                    if(pid.intValue()==msgPid)
                                    {
                                        pid.setNextRequest(System.currentTimeMillis()+updatePeriod);
                                    }
                                }
                                break;
                        }
                        break;

                    case OBD_SVC_CTRL_MODE: // Test control mode
                    case OBD_SVC_VEH_INFO:  // get vehicle information (mode 9)
                        Log.i(TAG, "Mode 09 response received, service: " + msgService);
                        msgPid = (Integer) getParamValue(ID_OBD_PID, buffer);
                        Log.i(TAG, "Mode 09 PID: 0x" + Integer.toHexString(msgPid));
                        switch (msgPid)
                        {
                            case 0x00:
                            case 0x20:
                            case 0x40:
                            case 0x60:
                            case 0x80:
                            case 0xA0:
                            case 0xC0:
                            case 0xE0:
                                // Check for optional message count byte, find offset to payload
                                int offset = (buffer.length % 4 == 0) ? 4 : 6;
                                // get payload data and mark the indicated supported PIDs
                                long msgPayload = Long.valueOf(new String(buffer, offset, 8), 16);
                                markSupportedPids(msgService, msgPid, msgPayload, VidPvs);
                                break;

                            default:
                                // Special handling for VIN (Mode 09, PID 02)
                                if (msgService == OBD_SVC_VEH_INFO && msgPid == 0x02)
                                {
                                    try {
                                        // Get the raw payload
                                        String payload = String.valueOf(getPayLoad(buffer));
                                        char[] charData = hexToBytes(payload);
                                        byte[] data = new byte[charData.length];
                                        for (int i = 0; i < charData.length; i++) {
                                            data[i] = (byte) charData[i];
                                        }

                                        // Parse VIN (skip first byte, convert remaining to ASCII)
                                        if (data != null && data.length >= 17) {
                                            StringBuilder vinBuilder = new StringBuilder();
                                            for (int i = 1; i <= 17 && i < data.length; i++) {
                                                vinBuilder.append((char) data[i]);
                                            }
                                            String vin = vinBuilder.toString();
                                            Log.i(TAG, "VIN parsed from Mode 09: " + vin);

                                            // Send VIN to VehicleManager for caching and distribution
                                            com.obddroid.vehicle.VehicleManager.getInstance().setVIN(vin);
                                            Log.i(TAG, "Sent VIN to VehicleManager for caching");

                                            // Find and update the VIN PV in VidPvs
                                            for (Object obj : VidPvs.values()) {
                                                if (!(obj instanceof EcuDataPv)) continue;
                                                EcuDataPv pv = (EcuDataPv) obj;
                                                String desc = String.valueOf(pv.get(EcuDataPv.FID_DESCRIPT));
                                                if (desc != null && desc.toLowerCase().contains("vehicle identification")) {
                                                    // Update the PV with the real VIN
                                                    pv.put(EcuDataPv.FID_VALUE, vin);
                                                    // Fire a change event to update the UI
                                                    VidPvs.put(pv.getAsInt(EcuDataPv.FID_PID), pv, PvChangeEvent.PV_MODIFIED);
                                                    Log.i(TAG, "Updated VIN PV with: " + vin);
                                                    break;
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error parsing VIN: " + e.getMessage());
                                    }
                                }

                                // Still do the normal update for other data items
                                long updatePeriod =
                                    dataItems.updateDataItems(msgService,
                                                                msgPid,
                                                                hexToBytes(String.valueOf(
                                                                        getPayLoad(buffer))));
                                /* Update expected request timestamp for PID */
                                for( ObdPid pid : pidSupported)
                                {
                                    if(pid.intValue()==msgPid)
                                    {
                                        pid.setNextRequest(System.currentTimeMillis()+updatePeriod);
                                    }
                                }
                                break;
                        }
                        break;


                    // fault code response
                    case OBD_SVC_READ_CODES:
                    case OBD_SVC_PENDINGCODES:
                    case OBD_SVC_PERMACODES:
                        int currCode;
                        Integer key;
                        EcuCodeItem code;
                        int nCodes = 0;
                        // default DTC data to start at offset 2 (Byte 1)
                        int DTCOffs = 2;
                        
                        // If message contains optional number of codes (1 Byte) then set it ...
                        boolean hasNumCodes = ((buffer.length % 4) == 0);
                        if (hasNumCodes)
                        {
                            nCodes = Integer.valueOf(new String(buffer, 2, 2), 16);
                            setNumCodes(nCodes);
                            // DTC data starts at offset 4 (byte 2)
                            DTCOffs = 4;
                        }

                        // read in all trouble codes
                        for (int i = DTCOffs; i < buffer.length; i += 4)
                        {
                            key = Integer.valueOf(new String(buffer, i, 4), 16);
                            currCode = key.intValue();
                            if (currCode != 0)
                            {
                                if ((code = knownCodes.get(key)) == null)
                                {
                                    code = new ObdCodeItem(key.intValue(),
                                                           Messages.getString(
                                                           "customer.specific.trouble.code.see.manual"));
                                }
                                log.fine(String.format("+DFC: %04x: %s", key, code.toString()));
                                // Remember received message service to know code status
                                code.put(EcuCodeItem.FID_STATUS, Integer.valueOf(msgService));
                                tCodes.put(key, code);
                                // if number of codes hasn't been delivered yet ...
                                if(!hasNumCodes)
                                {
                                    // increment number of detected codes
                                    nCodes++;
                                }
                            }
                        }
                        if (nCodes == 0)
                        {
                            tCodes.put(0, new ObdCodeItem(0, Messages.getString(
                                    "no.trouble.codes.set")));
                        }
                        // When fault codes are detected, pre-populate freeze frame data
                        if (nCodes > 0) {
                            dataService.initializeFreezeFrameData();
                        }
                        break;

                    // clear code response
                    case OBD_SVC_CLEAR_CODES:
                        break;

                    default:
                        log.warning("Service not (yet) supported: " + msgService);
                }
            } catch (Exception e)
            {
                log.warning("'" + Arrays.toString(buffer) + "':" + e.getMessage());
            }
        }
        return (result);
    }

    /**
     * Notify all telegram Writers about new telegram
     * @param buffer - telegram buffer
     */
    @Override
    public void sendTelegram(char[] buffer)
    {
        // remember last sent message
        lastTxMsg = new String(buffer);
        super.sendTelegram(buffer);
    }

    /**
     * Getter for property numCodes.
     * @return Value of property numCodes.
     */
    public int getNumCodes()
    {
        return this.numCodes;
    }

    /**
     * Setter for property numCodes.
     * @param numCodes New value of property numCodes.
     */
    private void setNumCodes(int numCodes)
    {
        int old = this.numCodes;
        this.numCodes = numCodes;
        firePropertyChange(new PropertyChangeEvent(this,
                                                   PROP_NUM_CODES,
                                                   Integer.valueOf(old),
                                                   Integer.valueOf(numCodes)));
    }

    /**
     * Get human-readable description for a PID
     *
     * @param pid The PID number
     * @return Description string for the PID
     */
    private static String getPidDescription(int pid) {
        switch (pid) {
            case 0x01: return "Monitor status";
            case 0x02: return "Freeze DTC";
            case 0x03: return "Fuel system status";
            case 0x04: return "Calculated engine load";
            case 0x05: return "Engine coolant temperature";
            case 0x06: return "Short term fuel trim—Bank 1";
            case 0x07: return "Long term fuel trim—Bank 1";
            case 0x08: return "Short term fuel trim—Bank 2";
            case 0x09: return "Long term fuel trim—Bank 2";
            case 0x0A: return "Fuel pressure";
            case 0x0B: return "Intake manifold pressure";
            case 0x0C: return "Engine speed";
            case 0x0D: return "Vehicle speed";
            case 0x0E: return "Timing advance";
            case 0x0F: return "Intake air temperature";
            case 0x10: return "Mass air flow rate";
            case 0x11: return "Throttle position";
            case 0x12: return "Commanded secondary air status";
            case 0x13: return "Oxygen sensors present";
            case 0x14: return "Oxygen sensor 1";
            case 0x15: return "Oxygen sensor 2";
            case 0x16: return "Oxygen sensor 3";
            case 0x17: return "Oxygen sensor 4";
            case 0x18: return "Oxygen sensor 5";
            case 0x19: return "Oxygen sensor 6";
            case 0x1A: return "Oxygen sensor 7";
            case 0x1B: return "Oxygen sensor 8";
            case 0x1C: return "OBD standards";
            case 0x1D: return "Oxygen sensors present (4 banks)";
            case 0x1E: return "Auxiliary input status";
            case 0x1F: return "Run time since engine start";
            case 0x21: return "Distance traveled with MIL";
            case 0x22: return "Fuel rail pressure";
            case 0x23: return "Fuel rail gauge pressure";
            case 0x2C: return "Commanded EGR";
            case 0x2D: return "EGR Error";
            case 0x2E: return "Commanded evaporative purge";
            case 0x2F: return "Fuel tank level input";
            case 0x30: return "Warm-ups since codes cleared";
            case 0x31: return "Distance since codes cleared";
            case 0x33: return "Absolute barometric pressure";
            case 0x42: return "Control module voltage";
            case 0x43: return "Absolute load value";
            case 0x44: return "Commanded fuel-air ratio";
            case 0x45: return "Relative throttle position";
            case 0x46: return "Ambient air temperature";
            case 0x47: return "Absolute throttle position B";
            case 0x48: return "Absolute throttle position C";
            case 0x49: return "Accelerator pedal position D";
            case 0x4A: return "Accelerator pedal position E";
            case 0x4B: return "Accelerator pedal position F";
            case 0x4C: return "Commanded throttle actuator";
            case 0x4D: return "Time run with MIL on";
            case 0x4E: return "Time since trouble codes cleared";
            case 0x51: return "Fuel Type";
            case 0x52: return "Ethanol fuel %";
            case 0x53: return "Absolute evap vapor pressure";
            case 0x54: return "Evap vapor pressure";
            case 0x59: return "Fuel rail absolute pressure";
            case 0x5A: return "Relative accelerator position";
            case 0x5B: return "Hybrid battery remaining life";
            case 0x5C: return "Engine oil temperature";
            case 0x5D: return "Fuel injection timing";
            case 0x5E: return "Engine fuel rate";
            default: return String.format("PID 0x%02X", pid);
        }
    }

    /**
     * Getter for property service.
     * @return Value of property service.
     */
    public int getService()
    {
        return this.service;
    }

    /**
     * reset all protocol settings
     */
    public void reset()
    {
        // switch off any active service
        setService(OBD_SVC_NONE, true);
        // clear command queue
        cmdQueue.clear();
        // clear supported PIDs
        pidSupported.clear();
        // reset fixed PIDs
        resetFixedPid();
        // Clear data items
        PidPvs.clear();
        tCodes.clear();
        VidPvs.clear();
    }

    /**
     * clear data lists for selected service
     * @param obdService OBD service to clear lists for
     */
    private void clearDataLists(int obdService)
    {
        // Don't clear freeze frame data when switching services
        if (obdService == OBD_SVC_FREEZEFRAME) {
            log.fine("Not clearing freeze frame data to preserve it");
            return;
        }

        // ENHANCED: Cache data before clearing
        cacheDataBeforeClearing(obdService);

        // clean up data lists
        switch (obdService)
        {
            case OBD_SVC_DATA:
            case OBD_SVC_FREEZEFRAME:
                // Clear data items
                pidSupported.clear();
                PidPvs.clear();
                break;

            case OBD_SVC_READ_CODES:
            case OBD_SVC_PENDINGCODES:
            case OBD_SVC_PERMACODES:
                tCodes.clear();
                break;

            case OBD_SVC_VEH_INFO:
            case OBD_SVC_CTRL_MODE:
                // Clear data items
                pidSupported.clear();
                VidPvs.clear();
                break;
        }
    }

    /**
     * Setter for property service.
     *  This includes initialisation of the requested service to the vehicle
     * @param obdService New OBD service to be requested.
     * @param clearLists clear data list for this service
     */
    public void setService(int obdService, boolean clearLists)
    {
        int previousService = this.service;
        this.service = obdService;
        pidsWrapped = false;
        // if lists shall be cleared
        if (clearLists)
        {
            // then do it
            clearDataLists(obdService);
        }
        // ENHANCED: Restore cached data for the new service
        restoreCachedData(obdService);

        // set specified OBD service
        switch (obdService)
        {
            case OBD_SVC_NONE:
                // sendCommand(CMD_RESET,0);
                break;

            case OBD_SVC_DATA:
            case OBD_SVC_FREEZEFRAME:
            case OBD_SVC_CTRL_MODE:
            case OBD_SVC_VEH_INFO:
                // read vehicle information
                // request for PID/TID's supported
                writeTelegram(emptyBuffer, obdService, 0);
                break;

            case OBD_SVC_READ_CODES:
            case OBD_SVC_PENDINGCODES:
            case OBD_SVC_PERMACODES:
                numCodes = 0;
                // Queue requests for reading all trouble codes
                cmdQueue.add(String.format("%02X", OBD_SVC_READ_CODES, 0));
                cmdQueue.add(String.format("%02X", OBD_SVC_PENDINGCODES, 0));
                cmdQueue.add(String.format("%02X", OBD_SVC_PERMACODES, 0));
                // read PID number of codes ...
                writeTelegram(emptyBuffer, OBD_SVC_DATA, 1);
                break;

            case OBD_SVC_CLEAR_CODES:
                // clear trouble codes
                writeTelegram(emptyBuffer, obdService, 0);
                // wait for codes to be cleared
                try
                {
                    Thread.sleep(500);
                } catch (InterruptedException e)
                {
                    // Intentionally do nothing
                }
                break;

            case OBD_SVC_O2_RESULT:
            case OBD_SVC_MON_RESULT:
            default:
                log.warning("Service not supported: " + obdService);
        }
    }

    // ================== ENHANCED CACHING METHODS ==================

    /**
     * Cache current data before clearing - thread-safe
     */
    private synchronized void cacheDataBeforeClearing(int fromService) {
        log.info("ENHANCED: Caching data before clearing service " + fromService);

        switch (fromService) {
            case OBD_SVC_DATA:
                // Cache live data PIDs - thread-safe
                cachedLiveDataPIDs.clear();
                for (Object key : PidPvs.keySet()) {
                    if (key instanceof Integer) {
                        Integer pid = (Integer) key;
                        Object value = PidPvs.get(pid);
                        if (value instanceof EcuDataPv) {
                            cachedLiveDataPIDs.put(pid, (EcuDataPv) value);
                        }
                    }
                }

                // First time seeing PIDs? Save them permanently
                if (!pidsDiscoveryComplete.get() && PidPvs.size() > 0) {
                    for (Object key : PidPvs.keySet()) {
                        if (key instanceof Integer) {
                            permanentlySupportedPIDs.put((Integer) key, true);
                        }
                    }
                    pidsDiscoveryComplete.set(true);
                    log.info("ENHANCED: Discovered " + permanentlySupportedPIDs.size() + " supported PIDs (cached permanently)");
                }
                break;

            case OBD_SVC_FREEZEFRAME:
                // Cache freeze frame with proper DTC correlation
                if (PidPvs.size() > 0) {
                    PvList frameData = new PvList();
                    frameData.putAll(PidPvs);
                    cachedFreezeFrames.put(freezeFrame_Id, frameData);

                    // Also process with FreezeFrameManager for DTC correlation
                    freezeFrameManager.processFreezeFrame(freezeFrame_Id, frameData, cachedFaultCodes);

                    log.info("ENHANCED: Cached freeze frame " + freezeFrame_Id);
                }
                break;

            case OBD_SVC_VEH_INFO:
                // Cache vehicle info - thread-safe
                cachedVehicleInfo.clear();
                for (Object key : VidPvs.keySet()) {
                    if (key instanceof Integer) {
                        Integer vid = (Integer) key;
                        Object value = VidPvs.get(vid);
                        if (value instanceof EcuDataPv) {
                            cachedVehicleInfo.put(vid, (EcuDataPv) value);
                        }
                    }
                }
                break;

            case OBD_SVC_READ_CODES:
            case OBD_SVC_PENDINGCODES:
            case OBD_SVC_PERMACODES:
                // Cache fault codes - thread-safe
                cachedFaultCodes.clear();
                for (Object key : tCodes.keySet()) {
                    if (key instanceof Integer) {
                        Integer code = (Integer) key;
                        Object value = tCodes.get(code);
                        if (value instanceof EcuCodeItem) {
                            cachedFaultCodes.put(code, (EcuCodeItem) value);
                        }
                    }
                }
                break;
        }
    }

    /**
     * Restore cached data for a service - thread-safe
     */
    private synchronized void restoreCachedData(int toService) {
        try {
            switch (toService) {
                case OBD_SVC_DATA:
                    // Restore live data if empty and we have cache
                    if (PidPvs.isEmpty() && !cachedLiveDataPIDs.isEmpty()) {
                        for (Map.Entry<Integer, EcuDataPv> entry : cachedLiveDataPIDs.entrySet()) {
                            PidPvs.put(entry.getKey(), entry.getValue());
                        }
                        log.info("ENHANCED: Restored " + PidPvs.size() + " cached live data PIDs");
                    }
                    break;

                case OBD_SVC_FREEZEFRAME:
                    // Initialize with known PID structure if we have discovered PIDs
                    if (pidsDiscoveryComplete.get() && PidPvs.isEmpty()) {
                        for (Integer pid : permanentlySupportedPIDs.keySet()) {
                            // Get the data item for this PID
                            Vector<EcuDataItem> items = dataItems.getPidDataItems(OBD_SVC_DATA, pid);
                            if (items != null && items.size() > 0) {
                                for (EcuDataItem item : items) {
                                    // Create EcuDataPv from the item's existing pv
                                    if (item.pv != null) {
                                        PidPvs.put((Integer) item.pv.get(EcuDataPv.FID_PID), item.pv);
                                    }
                                }
                            }
                        }
                        log.info("ENHANCED: Initialized freeze frame with " + PidPvs.size() + " PID slots");
                    }
                    break;

                case OBD_SVC_VEH_INFO:
                    // Restore vehicle info if empty and we have cache
                    if (VidPvs.isEmpty() && !cachedVehicleInfo.isEmpty()) {
                        for (Map.Entry<Integer, EcuDataPv> entry : cachedVehicleInfo.entrySet()) {
                            VidPvs.put(entry.getKey(), entry.getValue());
                        }
                        log.info("ENHANCED: Restored " + VidPvs.size() + " cached vehicle info items");
                    }
                break;

                case OBD_SVC_READ_CODES:
                case OBD_SVC_PENDINGCODES:
                case OBD_SVC_PERMACODES:
                    // Restore fault codes if empty and we have cache
                    if (tCodes.isEmpty() && !cachedFaultCodes.isEmpty()) {
                        for (Map.Entry<Integer, EcuCodeItem> entry : cachedFaultCodes.entrySet()) {
                            tCodes.put(entry.getKey(), entry.getValue());
                        }
                        log.info("ENHANCED: Restored " + tCodes.size() + " cached fault codes");
                    }
                    break;
            }
        } catch (Exception e) {
            log.severe("ENHANCED: Error restoring cached data: " + e.getMessage());
        }
    }

    // ================== ENHANCED INITIALIZATION SYSTEM ==================

    private InitializationManager initManager;

    /**
     * Get or create the initialization manager
     */
    private synchronized InitializationManager getInitManager() {
        if (initManager == null) {
            initManager = new InitializationManager(this);
        }
        return initManager;
    }

    /**
     * Run comprehensive initialization with progress tracking
     * @param listener Optional progress listener
     * @return CompletableFuture that completes when init is done
     */
    public CompletableFuture<Boolean> runComprehensiveInit(InitProgressListener listener) {
        if (comprehensiveInitComplete.get()) {
            log.info("ENHANCED: Comprehensive init already complete");
            return CompletableFuture.completedFuture(true);
        }

        InitializationManager manager = getInitManager();
        if (listener != null) {
            manager.setProgressListener(listener);
        }

        return manager.initialize().thenApply(success -> {
            if (success) {
                comprehensiveInitComplete.set(true);
            }
            return success;
        });
    }

    /**
     * Convenience method for initialization without listener
     */
    public CompletableFuture<Boolean> runComprehensiveInit() {
        return runComprehensiveInit(null);
    }

    /**
     * Cancel ongoing initialization
     */
    public void cancelInit() {
        if (initManager != null) {
            initManager.cancel();
        }
    }

    // ================== ENHANCED GETTERS ==================

    /**
     * Get cached freeze frame data - thread-safe
     * @param frameId The freeze frame ID (0 = most recent)
     * @return PvList of freeze frame data, or null if not cached
     */
    public synchronized PvList getCachedFreezeFrame(int frameId) {
        PvList frame = cachedFreezeFrames.get(frameId);
        if (frame != null) {
            // Return a defensive copy
            PvList copy = new PvList();
            copy.putAll(frame);
            return copy;
        }
        return null;
    }

    /**
     * Check if PIDs have been discovered
     */
    public boolean isPidsDiscoveryComplete() {
        return pidsDiscoveryComplete.get();
    }

    /**
     * Get permanently cached PIDs - returns defensive copy
     */
    public ConcurrentHashMap<Integer, Boolean> getPermanentlySupportedPIDs() {
        return new ConcurrentHashMap<>(permanentlySupportedPIDs);
    }

    /**
     * Get cached fault codes - thread-safe
     */
    public ConcurrentHashMap<Integer, EcuCodeItem> getCachedFaultCodes() {
        return new ConcurrentHashMap<>(cachedFaultCodes);
    }

    /**
     * Get cached live data PIDs - thread-safe
     */
    public ConcurrentHashMap<Integer, EcuDataPv> getCachedLiveDataPIDs() {
        return new ConcurrentHashMap<>(cachedLiveDataPIDs);
    }

    /**
     * Get cached vehicle info - thread-safe
     */
    public ConcurrentHashMap<Integer, EcuDataPv> getCachedVehicleInfo() {
        return new ConcurrentHashMap<>(cachedVehicleInfo);
    }

    /**
     * Check if comprehensive init is complete
     */
    public boolean isComprehensiveInitComplete() {
        return comprehensiveInitComplete.get();
    }

    /**
     * Clear all cached data (for testing or reset)
     */
    public synchronized void clearAllCaches() {
        cachedLiveDataPIDs.clear();
        cachedVehicleInfo.clear();
        cachedFreezeFrames.clear();
        cachedFaultCodes.clear();
        freezeFrameManager.clear();
        errorHandler.clearHistory();
        // Note: We DON'T clear permanentlySupportedPIDs as they should remain
        log.info("ENHANCED: All caches cleared (except permanent PIDs)");
    }

    /**
     * Get the freeze frame manager
     */
    public FreezeFrameManager getFreezeFrameManager() {
        return freezeFrameManager;
    }

    /**
     * Get the error handler
     */
    public ErrorHandler getErrorHandler() {
        return errorHandler;
    }

    /**
     * Get system health status
     */
    public boolean isSystemHealthy() {
        return errorHandler.isHealthy();
    }

    /**
     * Get comprehensive system status
     */
    public String getSystemStatus() {
        StringBuilder status = new StringBuilder();
        status.append("=== OBD SYSTEM STATUS ===\n");
        status.append("Init Complete: ").append(comprehensiveInitComplete.get()).append("\n");
        status.append("PIDs Discovered: ").append(permanentlySupportedPIDs.size()).append("\n");
        status.append("Fault Codes: ").append(cachedFaultCodes.size()).append("\n");
        status.append("Freeze Frames: ").append(freezeFrameManager.size()).append("\n");
        status.append("Error Statistics: ").append(errorHandler.getStatistics()).append("\n");
        status.append("System Health: ").append(isSystemHealthy() ? "HEALTHY" : "DEGRADED").append("\n");
        return status.toString();
    }
}
