# OBD-Droid Protocol & Flow Control Analysis
**Generated:** 2025-10-31
**Purpose:** Deep analysis of OBD-II/CAN protocols and concurrent access patterns before implementing ObdServiceCoordinator

---

## 📋 Table of Contents
1. [OBD-II Protocol Fundamentals](#1-obd-ii-protocol-fundamentals)
2. [CAN Bus & ISO-TP Flow Control](#2-can-bus--iso-tp-flow-control)
3. [ELM327 Adapter State Machine](#3-elm327-adapter-state-machine)
4. [Current Architecture Analysis](#4-current-architecture-analysis)
5. [Identified Race Conditions](#5-identified-race-conditions)
6. [ObdServiceCoordinator Design](#6-obdservicecoordinator-design)

---

## 1. OBD-II Protocol Fundamentals

### 1.1 OBD Services (Modes)
```
MODE 01 (0x01): Live Data - Real-time sensor readings (RPM, speed, temp, etc.)
MODE 02 (0x02): Freeze Frame - Snapshot of data when fault occurred
MODE 03 (0x03): Read Diagnostic Trouble Codes (DTCs) - Confirmed faults
MODE 04 (0x04): Clear DTCs - Erase fault codes
MODE 05 (0x05): O2 Sensor Test Results
MODE 06 (0x06): Monitor Test Results
MODE 07 (0x07): Pending DTCs - Faults not yet confirmed
MODE 08 (0x08): Control Test - Request vehicle self-tests
MODE 09 (0x09): Vehicle Information - VIN, Calibration ID, ECU Name
MODE 0A (0x0A): Permanent DTCs - Emissions codes that can't be cleared manually
```

### 1.2 Request/Response Pattern
```
REQUEST:  [MODE] [PID]
          Example: "010C" = Mode 01, PID 0x0C (Engine RPM)

RESPONSE: [MODE+0x40] [PID] [DATA...]
          Example: "410C1A3B" = Mode 41 (01+40), PID 0x0C, Data: 0x1A3B
                   RPM = ((0x1A << 8) | 0x3B) / 4 = 1678 RPM
```

### 1.3 PID Discovery
```
REQUEST:  "0100" = Request supported PIDs 0x01-0x20
RESPONSE: "4100FFFFFFFF" = All 32 PIDs supported (bitmask)

REQUEST:  "0120" = Request supported PIDs 0x21-0x40
RESPONSE: "4120FFFFFFFE" = PIDs supported, bit 0 = next block available

Repeat for 0x40, 0x60, 0x80, 0xA0, 0xC0, 0xE0 until bit 0 = 0
```

### 1.4 Implementation in ObdProt.java
```java
// Line 47-61: Service constants
public static final int OBD_SVC_NONE = 0x00;
public static final int OBD_SVC_DATA = 0x01;
public static final int OBD_SVC_FREEZEFRAME = 0x02;
// ... etc

// Line 236: Vector of supported PIDs (populated during discovery)
private static final Vector<ObdPid> pidSupported = new Vector<ObdPid>();

// Line 639-736: markSupportedPids() - Parses PID bitmask responses
// Line 779-845: getNextSupportedPid() - Round-robin polling of discovered PIDs
// Line 854-1267: handleTelegram() - Main response parser
```

---

## 2. CAN Bus & ISO-TP Flow Control

### 2.1 CAN Frame Structure
```
Standard CAN Frame (11-bit ID):
┌─────────┬────────┬─────────────────┐
│ CAN ID  │  DLC   │   Data (0-8 bytes) │
│ 11 bits │ 4 bits │   Variable         │
└─────────┴────────┴─────────────────┘

Example: "7E8 03 41 0C 1A 3B"
         └─┬─┘ └┬┘ └──────┬──────┘
          CAN   DLC   Data Payload
          ID   (3 bytes)
```

### 2.2 ISO-TP Multi-Frame Protocol
When data exceeds 7 bytes, ISO-TP splits it into multiple frames:

```
SINGLE FRAME (≤7 bytes data):
┌────┬─────────────────┐
│ 0N │ Data (N bytes)  │  N = length (0-7)
└────┴─────────────────┘
Example: "03 41 0C 1A 3B" = 3 bytes of data

FIRST FRAME (>7 bytes):
┌────┬────┬──────────┐
│ 10 │ LL │ Data(6B) │  10 = first frame, LL = total length
└────┴────┴──────────┘
Example: "10 14 49 02 01 34 4A 47" = 20 bytes total, first 6 bytes

CONSECUTIVE FRAMES:
┌────┬──────────┐
│ 2N │ Data(7B) │  N = sequence number (1-15)
└────┴──────────┘
Example: "21 44 41 35 48 42 37 4A" = 2nd frame
         "22 42 31 35 38 31 34 34" = 3rd frame
```

### 2.3 Flow Control Implementation
**Location:** `EcuDiscoveryService.java` (lines 226-258)

```java
// Line 226: Detect multiline start
if (isMultilineStart(response)) {
    multilineBuffers.put(ecuAddress, response);
    return response.length();
}

// Line 231: Consecutive frames
else if (isMultilineContinuation(response) && multilineBuffers.containsKey(ecuAddress)) {
    String existing = multilineBuffers.get(ecuAddress);
    multilineBuffers.put(ecuAddress, existing + response);  // Append
    return response.length();
}

// Line 276: Assembly logic
private String assembleMultilineMessage(String buffer, int ecuAddr) {
    // Extract data from each frame
    // Skip PCI bytes (10/2X)
    // Concatenate payloads
}
```

**Key Challenge:**
If **two services** send requests simultaneously, consecutive frames from different ECUs get **interleaved**:
```
Request A: 0904 (Cal ID from ECU 1)
Request B: 090A (ECU Name from ECU 2)

Response: 7E8 10 14 49 04...  [ECU 1 First Frame]
Response: 7E9 10 17 49 0A...  [ECU 2 First Frame] ← COLLISION!
Response: 7E8 21 12 30 30...  [ECU 1 Frame 2]
Response: 7E9 21 45 43 4D...  [ECU 2 Frame 2]

Parser for Request A gets frames from BOTH ECUs → Corruption!
```

### 2.4 CAN Header Management
**ELM327 AT Commands:**
```
ATH0 - Disable headers (default) → "41 0C 1A 3B"
ATH1 - Enable headers          → "7E8 03 41 0C 1A 3B"
                                   └─┬─┘
                                   ECU Address (11-bit CAN ID)
```

**Usage in EcuDiscoveryService:**
- Line 97: `sendRawCommand("ATH1");` - Enable headers to detect ECU addresses
- Line 147: `sendRawCommand("ATH0");` - **CRITICAL CLEANUP** in finally block
- Line 222: `parseEcuAddress()` - Extract CAN ID from response

**Problem:**
If another service sends commands while headers are enabled:
```java
T=0:   EcuDiscovery: ATH1  [Headers ON]
T=1:   LiveDataActivity: 010C [Expecting "410C1A3B"]
T=2:   Response arrives: "7E8 03 41 0C 1A 3B"  [Parser FAILS - unexpected format!]
```

---

## 3. ELM327 Adapter State Machine

### 3.1 State Diagram
```
                    ┌──────────────┐
                    │  UNDEFINED   │
                    └──────┬───────┘
                           │ ATZ (reset)
                    ┌──────▼───────┐
        ┌───────────┤ INITIALIZING │
        │           └──────┬───────┘
        │                  │ Init commands complete
        │           ┌──────▼───────┐
        │           │ INITIALIZED  │
        │           └──────┬───────┘
        │                  │ Request PID 0100
        │           ┌──────▼───────┐
        │   ┌───────┤ ECU_DETECT   ├───────┐
        │   │       └──────┬───────┘       │
        │   │              │ Responses      │
        │   │       ┌──────▼───────┐       │
        │   │       │ ECU_DETECTED │       │
        │   │       └──────┬───────┘       │
        │   │              │ Service started│
        │   │       ┌──────▼───────┐       │
        │   └──────▶│  CONNECTING  │◀──────┘
        │           └──────┬───────┘
        │                  │ Data received
        │           ┌──────▼───────┐
NO DATA │◀──────────┤  CONNECTED   │
timeout │           └──────┬───────┘
        │                  │ NO DATA / ERROR
        │           ┌──────▼───────┐
        └──────────▶│   NODATA     │
                    └──────────────┘
```

### 3.2 State Recovery Logic
**Location:** `ElmProt.java` (lines 1535-1728)

```java
// Line 1477-1511: CONNECTING timeout (5 seconds)
if (status == STAT.CONNECTING && duration > CONNECTING_TIMEOUT_MS) {
    log.warning("Stuck in CONNECTING - may need recovery");
}

// Line 1489-1661: NODATA recovery
if (oldStatus == STAT.NODATA && duration > NODATA_TIMEOUT_MS) {
    if (lastGoodState == STAT.CONNECTED) {
        this.status = lastGoodState;  // Restore last good state
        return;
    }
}

// Line 1663-1701: Rapid cycling detection
if (rapidCycleCount >= MAX_RAPID_CYCLES) {
    log.severe("EXCESSIVE CYCLING DETECTED - forcing disconnect");
    this.status = STAT.ERROR;  // Force error to stop loop
}
```

**Problem:** Multiple callers can trigger state transitions simultaneously:
```java
Thread A: elm.setService(OBD_SVC_DATA)    → status = CONNECTING
Thread B: elm.setService(OBD_SVC_VEH_INFO) → status = CONNECTING (again!)
          → Overwrites Thread A's operation
```

### 3.3 Command Queue
**Location:** `ObdProt.java` (line 350)

```java
static final Vector<String> cmdQueue = new Vector<String>();
```

**Queue Processing:** `ElmProt.java` (lines 784-792)
```java
case PROMPT:  // ">" received from ELM
    if (cmdQueue.size() > 0) {
        String cmd = cmdQueue.lastElement();
        cmdQueue.remove(cmd);
        sendTelegram(cmd.toCharArray());
    }
```

**Problem:** `Vector` is thread-safe for individual operations, but compound operations are NOT atomic:
```java
Thread A: cmdQueue.add("0100");  // Add PID discovery
Thread B: cmdQueue.add("0904");  // Add Cal ID request
Thread C: if (cmdQueue.size() > 0) {  // Check size
              String cmd = cmdQueue.lastElement();  // Get last
              cmdQueue.remove(cmd);  // Remove
          }

Race: Between .lastElement() and .remove(), another thread could modify queue!
```

---

## 4. Current Architecture Analysis

### 4.1 The Shared Resource
**Location:** `CommService.java` (line 49)
```java
public static final ElmProt elm = new ElmProt();
```

**Access Pattern:** Global static instance - **NO mutual exclusion!**

### 4.2 Current Access Points (21 files)
```
ACTIVITIES (Direct ELM Access):
├── LiveDataActivity.java       → Continuous polling (OBD_SVC_DATA)
├── FuelEconomyActivity.java    → Periodic polling (OBD_SVC_DATA)
├── EmissionsActivity.java      → Periodic polling (OBD_SVC_DATA)
├── EcuListActivity.java        → ECU discovery (temporary headers)
├── FaultCodeDetailsActivity.java → Read freeze frame

SERVICES (Isolated Operations):
├── EcuDiscoveryService.java    → Mode 09 with headers (ATH1/ATH0)
├── FaultCodeService.java       → Mode 03/07/0A (atomic scans)
├── StateManager.java            → Cleanup coordination
└── DiscoveryManager.java        → Cached ECU info

SCAN STAGES (Sequential via Orchestrator):
├── ScanOrchestrator.java       → Sequential stage execution
├── FreshEcuDiscoveryStage.java → Fresh ECU query
├── VehicleInfoStage.java       → Mode 09 data
├── LiveDataStage.java          → Mode 01 snapshot
├── FaultCodeStage.java         → Mode 03 DTCs
├── FreezeFrameStage.java       → Mode 02 snapshots
├── ComponentTestStage.java     → Mode 08 tests
├── PendingDtcStage.java        → Mode 07 DTCs
└── PermanentDtcStage.java      → Mode 0A permanent DTCs
```

### 4.3 Attempted Coordination Mechanisms

#### A. ScanOrchestrator (PARTIAL)
**Location:** `ScanOrchestrator.java` (line 55)
```java
private final ExecutorService executor = Executors.newSingleThreadExecutor();
```

**What it does:**
- ✅ Executes scan stages **sequentially** (line 151-186)
- ✅ Prevents internal stage overlap

**What it DOESN'T do:**
- ❌ Doesn't prevent LiveDataActivity from polling during scan
- ❌ Doesn't lock `CommService.elm` for exclusive use
- ❌ Other services can still access adapter freely

#### B. EcuDiscoveryService (INSUFFICIENT)
**Location:** `EcuDiscoveryService.java` (line 37)
```java
private final AtomicBoolean isDiscovering = new AtomicBoolean(false);

public CompletableFuture<Map<Integer, EcuDiscoveryInfo>> discoverEcus() {
    if (isDiscovering.get()) {
        Log.w(TAG, "Discovery already in progress");
        return currentDiscovery;  // Return existing future
    }
    // ... proceed with discovery
}
```

**What it does:**
- ✅ Prevents **internal** re-entry (multiple discoverEcus() calls)
- ✅ Uses RawTelegramListener for isolation
- ✅ Always restores ATH0 in finally block (line 147)

**What it DOESN'T do:**
- ❌ Doesn't prevent FaultCodeService from running concurrently
- ❌ Doesn't prevent LiveDataActivity from sending commands
- ❌ Only protects against re-entry of **same** service

#### C. FaultCodeService (INSUFFICIENT)
**Location:** `FaultCodeService.java` (line 55, 167)
```java
private final AtomicBoolean isScanning = new AtomicBoolean(false);

if (!isScanning.compareAndSet(false, true)) {
    return currentScan;  // Already scanning
}
```

**What it does:**
- ✅ Atomic check-and-set (no race condition internally)
- ✅ Single-threaded executor for sequential command processing
- ✅ Synchronized response buffer (line 59)

**What it DOESN'T do:**
- ❌ Doesn't prevent EcuDiscoveryService from running
- ❌ Doesn't prevent scan orchestrator from starting
- ❌ Only protects against re-entry of **same** service

### 4.4 The Core Problem
```
┌──────────────────────────────────────────────────────────┐
│                    CommService.elm                       │
│              (SHARED, NO MUTUAL EXCLUSION)               │
└──────────────┬───────────────────────────────────────────┘
               │
      ┌────────┼────────┬────────────┬──────────────┐
      │        │        │            │              │
  ┌───▼──┐ ┌──▼───┐ ┌──▼────┐  ┌────▼────┐  ┌─────▼─────┐
  │ Live │ │ Fuel │ │  ECU  │  │  Fault  │  │   Scan    │
  │ Data │ │ Econ │ │ Disc  │  │  Code   │  │Orchestrator│
  └──────┘ └──────┘ └───────┘  └─────────┘  └───────────┘
     ║        ║         ║            ║              ║
     ║        ║         ║            ║              ║
     ╚════════╩═════════╩════════════╩══════════════╝
              NO COORDINATION BETWEEN SERVICES!

Each has internal synchronization, but nothing prevents simultaneous access!
```

---

## 5. Identified Race Conditions

### 5.1 Race Condition #1: Activity + Scan Collision
**Scenario:** User in LiveDataActivity, then starts Full Scan

```java
// Timeline:
T=0.0s  LiveDataActivity.onResume()
        → CommService.elm.setService(OBD_SVC_DATA);  [Start polling]

T=2.0s  User taps "Full Scan" button
        → ScanOrchestrator.startScan(config);

T=2.1s  ScanOrchestrator.executeScan()
        → stages.get(0).execute(context);  [FreshEcuDiscoveryStage]

T=2.2s  FreshEcuDiscoveryStage calls EcuDiscoveryService
        → discoveryService.discoverEcus();
        → CommService.elm.sendTelegram("ATH1");  [ENABLE HEADERS]

T=2.3s  LiveDataActivity polling continues (background thread)
        → CommService.elm sends "010C" (read RPM)

T=2.4s  Response arrives: "7E8 03 41 0C 1A 3B"  [WITH HEADER!]
        → LiveDataActivity parser expects: "41 0C 1A 3B"
        → Parse FAILS - crashes or shows garbage data

T=2.5s  EcuDiscoveryService.discoverEcus() times out
        → No response received (LiveDataActivity consumed it)
```

**Impact:** Corruption, crashes, timeouts

### 5.2 Race Condition #2: Double Service Execution
**Scenario:** FaultCodeService and EcuDiscoveryService run simultaneously

```java
// Thread A:
FaultCodeService.scanAllCodes()
→ isScanning.compareAndSet(false, true);  ✅ SUCCESS
→ elm.addRawTelegramListener(this);
→ sendAndAwait("03", SCAN_TIMEOUT_MS);
→ elm.sendTelegram("03");  [TX at T=0.0s]

// Thread B:
EcuDiscoveryService.discoverEcus()
→ isDiscovering.compareAndSet(false, true);  ✅ SUCCESS (different flag!)
→ elm.addRawTelegramListener(this);  [BOTH listeners active!]
→ sendRawCommand("ATH1");  [TX at T=0.5s - overlaps with "03" response]

// T=0.6s: Response to "03" arrives
// Both FaultCodeService AND EcuDiscoveryService receive it via handleRawTelegram()
// FaultCodeService: Processes as DTC data
// EcuDiscoveryService: Tries to parse as ECU address → Confusion!
```

**Impact:** Both services receive wrong responses, timeouts, incorrect data

### 5.3 Race Condition #3: State Corruption
**Scenario:** Two activities change service mode simultaneously

```java
// Thread A (LiveDataActivity):
CommService.elm.setService(OBD_SVC_DATA, true);
→ clearDataLists(OBD_SVC_DATA);  [Clears pidSupported]
→ writeTelegram(emptyBuffer, OBD_SVC_DATA, 0);  [Sends "0100" for PID discovery]

// Thread B (EcuListActivity):
CommService.elm.setService(OBD_SVC_VEH_INFO, true);  [SIMULTANEOUS!]
→ clearDataLists(OBD_SVC_VEH_INFO);  [Clears pidSupported AGAIN]
→ writeTelegram(emptyBuffer, OBD_SVC_VEH_INFO, 0);  [Sends "0900" for VID discovery]

// State corruption:
// - pidSupported cleared twice (race on clear operation)
// - ObdProt.service = OBD_SVC_VEH_INFO (last write wins)
// - But cmdQueue may contain both "0100" and "0900"
// - Responses arrive but parser uses wrong service context

// T=0.5s: Response "41 00 FF FF FF FF" arrives (PID support from Thread A)
// ObdProt.handleTelegram() checks msgService:
// - Expects OBD_SVC_VEH_INFO (Thread B overwrote it)
// - Receives OBD_SVC_DATA response (Thread A's request)
// - Mismatch causes parse failure or incorrect handling
```

**Impact:** Incorrect PID/VID lists, parser confusion, wrong data associations

### 5.4 Race Condition #4: Command Queue Interleaving
**Scenario:** Multiple threads add to cmdQueue without coordination

```java
// Thread A (ScanOrchestrator - VehicleInfoStage):
cmdQueue.add("0900");  // Discover VIDs
cmdQueue.add("0902");  // Request VIN
cmdQueue.add("0904");  // Request Cal ID

// Thread B (LiveDataActivity - continuous polling):
// Runs in background, adds PID requests
cmdQueue.add("010C");  // RPM
cmdQueue.add("010D");  // Speed
cmdQueue.add("0105");  // Coolant temp

// Thread C (FaultCodeService):
cmdQueue.add("03");    // Read DTCs

// Queue state (order undefined due to concurrent access):
["0900", "010C", "0902", "010D", "0904", "03", "0105"]
         └──┬──┘        └──┬──┘        └┬┘  └──┬──┘
        PID interrupts VID sequence    DTC   More PIDs

// Execution:
// 1. "0900" → Response: "49 00 55 40 10 00" (VIDs supported)
// 2. "010C" → Response: "41 0C 1A 3B" (RPM = 1678)  ← WRONG CONTEXT!
//    VehicleInfoStage is waiting for Mode 09 data, gets Mode 01 instead!
// 3. "0902" → Response: "49 02 01 ... [VIN]"
//    But pidSupported list wasn't populated correctly due to interruption
```

**Impact:** Stages receive out-of-order responses, timeouts, incomplete data

### 5.5 Race Condition #5: Freeze Frame Corruption
**Scenario:** FaultCodeService and FreezeFrameStage access freeze frames

```java
// Thread A (FaultCodeService):
scanFaultCodes()
→ Response: "43 05 01 33 01 71 04 20 04 42"  [5 DTCs detected]
→ Calls: ObdProt.handleTelegram()
→ dataService.initializeFreezeFrameData();  [Creates freeze frame structure]

// Thread B (FreezeFrameStage - running in scan):
execute(scanContext)
→ CommService.elm.setService(OBD_SVC_FREEZEFRAME, true);
→ clearDataLists(OBD_SVC_FREEZEFRAME);  [CLEARS freeze frame data!]
→ writeTelegram(..., OBD_SVC_FREEZEFRAME, 0);  [Sends "0200" for discovery]

// Result:
// - Thread A populated freeze frame data based on DTCs
// - Thread B immediately cleared it
// - Freeze frame data lost, user sees "No data available"
```

**Impact:** Missing freeze frame data, user confusion

---

## 6. ObdServiceCoordinator Design

### 6.1 Design Goals
1. ✅ **Mutual Exclusion:** Only ONE caller can access CommService.elm at a time
2. ✅ **Request Queuing:** Queue requests when adapter is busy
3. ✅ **Priority Support:** Critical operations (clear codes) jump the queue
4. ✅ **Owner Tracking:** Know which service currently owns the adapter
5. ✅ **Timeout Safety:** Prevent indefinite locks if owner crashes/hangs
6. ✅ **Cleanup Guarantee:** Always restore adapter state (like ATH0) even on exception
7. ✅ **Backward Compatible:** Minimal changes to existing code

### 6.2 Architecture

```
┌─────────────────────────────────────────────────────────┐
│              ObdServiceCoordinator                      │
│  ┌───────────────────────────────────────────────────┐ │
│  │         ReentrantLock (Mutual Exclusion)          │ │
│  └───────────────────────────────────────────────────┘ │
│  ┌───────────────────────────────────────────────────┐ │
│  │    PriorityBlockingQueue<CoordinatedRequest>     │ │
│  │    Priority: CRITICAL > HIGH > NORMAL > LOW       │ │
│  └───────────────────────────────────────────────────┘ │
│  ┌───────────────────────────────────────────────────┐ │
│  │         Owner Tracking & Timeout Monitor          │ │
│  │   currentOwner, ownerStartTime, MAX_HOLD_TIME     │ │
│  └───────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────┘
                          │
        ┌─────────────────┼─────────────────┐
        │                 │                 │
  ┌─────▼─────┐   ┌───────▼────┐   ┌───────▼────┐
  │ Activity  │   │  Service   │   │    Scan    │
  │ Requests  │   │  Requests  │   │   Stages   │
  └───────────┘   └────────────┘   └────────────┘
```

### 6.3 Core Components

#### A. Priority Levels
```java
public enum RequestPriority {
    CRITICAL(4),  // Clear codes, reset adapter - must run immediately
    HIGH(3),      // Fault code scan, ECU discovery - user-initiated
    NORMAL(2),    // Full scan stages - sequential operation
    LOW(1);       // Background polling (live data, fuel economy)

    final int value;
    RequestPriority(int value) { this.value = value; }
}
```

#### B. Coordinated Request
```java
public class CoordinatedRequest implements Comparable<CoordinatedRequest> {
    final String owner;                  // Caller identification
    final RequestPriority priority;      // Priority level
    final Callable<T> task;              // Actual work
    final CompletableFuture<T> result;   // Return value
    final long submitTime;               // For timeout tracking

    @Override
    public int compareTo(CoordinatedRequest other) {
        return Integer.compare(other.priority.value, this.priority.value);
    }
}
```

#### C. Main API
```java
public class ObdServiceCoordinator {
    private final ReentrantLock elmLock = new ReentrantLock(true);  // Fair lock
    private final PriorityBlockingQueue<CoordinatedRequest> requestQueue;

    private String currentOwner = null;
    private long ownerStartTime = 0;
    private static final long MAX_HOLD_TIME_MS = 30_000;  // 30 seconds max

    /**
     * Execute a task with exclusive ELM access
     */
    public <T> CompletableFuture<T> executeExclusive(
        String owner,
        RequestPriority priority,
        Callable<T> task
    ) {
        CoordinatedRequest<T> request = new CoordinatedRequest<>(owner, priority, task);
        requestQueue.offer(request);

        CompletableFuture.runAsync(() -> {
            try {
                processRequest(request);
            } catch (Exception e) {
                request.result.completeExceptionally(e);
            }
        }, executorService);

        return request.result;
    }

    /**
     * Process request with lock acquisition
     */
    private <T> void processRequest(CoordinatedRequest<T> request) {
        try {
            // Acquire lock with timeout
            if (!elmLock.tryLock(request.priority.getTimeout(), TimeUnit.MILLISECONDS)) {
                throw new TimeoutException("Could not acquire ELM lock within timeout");
            }

            try {
                // Set owner tracking
                currentOwner = request.owner;
                ownerStartTime = System.currentTimeMillis();

                log.info(String.format("ELM acquired by: %s (priority: %s)",
                    request.owner, request.priority));

                // Execute task
                T result = request.task.call();
                request.result.complete(result);

            } finally {
                // Always release lock
                currentOwner = null;
                ownerStartTime = 0;
                elmLock.unlock();

                log.info("ELM released by: " + request.owner);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            request.result.completeExceptionally(e);
        } catch (Exception e) {
            request.result.completeExceptionally(e);
        }
    }

    /**
     * Force unlock if owner exceeds max hold time
     */
    private void monitorTimeout() {
        if (elmLock.isLocked() && currentOwner != null) {
            long holdTime = System.currentTimeMillis() - ownerStartTime;
            if (holdTime > MAX_HOLD_TIME_MS) {
                log.severe(String.format(
                    "Owner %s exceeded max hold time (%dms) - forcing unlock",
                    currentOwner, holdTime));
                elmLock.unlock();
                currentOwner = null;
            }
        }
    }
}
```

### 6.4 Usage Examples

#### Example 1: LiveDataActivity (Background Polling)
```java
// BEFORE (direct access):
@Override
protected void onResume() {
    super.onResume();
    CommService.elm.setService(ObdProt.OBD_SVC_DATA);  // UNSAFE!
}

// AFTER (coordinated access):
@Override
protected void onResume() {
    super.onResume();
    coordinator.executeExclusive(
        "LiveDataActivity",
        RequestPriority.LOW,  // Background polling = low priority
        () -> {
            CommService.elm.setService(ObdProt.OBD_SVC_DATA);
            return null;
        }
    );
}
```

#### Example 2: EcuDiscoveryService (Isolated Operation)
```java
// BEFORE (AtomicBoolean only):
if (isDiscovering.get()) {
    return currentDiscovery;  // Only prevents internal re-entry
}

// AFTER (coordinated access):
return coordinator.executeExclusive(
    "EcuDiscoveryService",
    RequestPriority.HIGH,  // User-initiated
    () -> {
        try {
            elm.addRawTelegramListener(this);
            sendRawCommand("ATH1");  // Enable headers
            Thread.sleep(1000);
            // ... discovery logic ...
            return discoveredEcus;
        } finally {
            sendRawCommand("ATH0");  // ALWAYS restore
            elm.removeRawTelegramListener(this);
        }
    }
);
```

#### Example 3: FaultCodeService (Atomic Scan)
```java
// BEFORE (separate flag):
if (!isScanning.compareAndSet(false, true)) {
    return currentScan;
}

// AFTER (coordinated access):
return coordinator.executeExclusive(
    "FaultCodeService",
    RequestPriority.HIGH,
    () -> {
        List<FaultCodeInfo> results = new ArrayList<>();
        elm.addRawTelegramListener(this);
        try {
            // Mode 03
            String response = sendAndAwait("03", SCAN_TIMEOUT_MS);
            results.addAll(parseFaultCodes(response, CodeType.CONFIRMED));

            // Mode 07
            response = sendAndAwait("07", SCAN_TIMEOUT_MS);
            results.addAll(parseFaultCodes(response, CodeType.PENDING));

            return results;
        } finally {
            elm.removeRawTelegramListener(this);
        }
    }
);
```

#### Example 4: ScanOrchestrator (Multi-Stage)
```java
// BEFORE (sequential but unprotected):
for (ScanStage stage : stages) {
    StageResult result = stage.execute(scanContext);  // Each stage accesses elm directly
}

// AFTER (coordinated access with elevated priority):
for (ScanStage stage : stages) {
    CompletableFuture<StageResult> future = coordinator.executeExclusive(
        "ScanOrchestrator:" + stage.getId(),
        RequestPriority.NORMAL,  // Scan stages = normal priority
        () -> {
            return stage.execute(scanContext);
        }
    );

    StageResult result = future.join();  // Wait for completion
}
```

### 6.5 Edge Case Handling

#### A. Deadlock Prevention
```java
// ReentrantLock allows same thread to re-acquire
if (elmLock.isHeldByCurrentThread()) {
    log.warning("Thread already holds lock - allowing re-entry");
    return task.call();  // Execute directly without lock attempt
}
```

#### B. Queue Overflow Protection
```java
private static final int MAX_QUEUE_SIZE = 100;

if (requestQueue.size() >= MAX_QUEUE_SIZE) {
    throw new IllegalStateException("Request queue full - adapter overwhelmed");
}
```

#### C. Stuck Owner Detection
```java
// Background monitor thread
scheduledExecutor.scheduleAtFixedRate(() -> {
    monitorTimeout();
}, 1, 1, TimeUnit.SECONDS);
```

#### D. Graceful Degradation
```java
// If coordinator fails, fallback to direct access with warning
try {
    return coordinator.executeExclusive(...);
} catch (Exception e) {
    log.severe("Coordinator failed - falling back to direct access (UNSAFE): " + e);
    return task.call();  // Direct execution as last resort
}
```

### 6.6 Migration Strategy

#### Phase 1: Install Coordinator (No Behavior Change)
1. Create `ObdServiceCoordinator` class
2. Add instance to `CommService`: `public static final ObdServiceCoordinator coordinator`
3. Initialize in constructor
4. All existing code continues to work (direct `CommService.elm` access)

#### Phase 2: Migrate Critical Services
1. `EcuDiscoveryService` - Uses ATH1/ATH0 (most critical)
2. `FaultCodeService` - Atomic scans
3. `StateManager` - Cleanup operations

#### Phase 3: Migrate Activities
1. `LiveDataActivity` - Most frequent caller
2. `FuelEconomyActivity` - Background polling
3. `EmissionsActivity` - Background polling

#### Phase 4: Migrate Scan Stages
1. `ScanOrchestrator` - Wrap entire scan
2. Individual stages if needed

#### Phase 5: Enforcement
1. Make `CommService.elm` package-private
2. Force all access through coordinator
3. Add deprecation warnings to direct access methods

### 6.7 Testing Strategy

#### Unit Tests
```java
@Test
public void testMutualExclusion() {
    CountDownLatch latch = new CountDownLatch(2);
    AtomicInteger counter = new AtomicInteger(0);

    // Start two concurrent tasks
    coordinator.executeExclusive("Task1", NORMAL, () -> {
        int value = counter.incrementAndGet();
        Thread.sleep(100);  // Simulate work
        assertEquals(1, value);  // Should always be 1 (no concurrent access)
        latch.countDown();
        return null;
    });

    coordinator.executeExclusive("Task2", NORMAL, () -> {
        int value = counter.decrementAndGet();
        Thread.sleep(100);
        assertEquals(0, value);  // Should always be 0
        latch.countDown();
        return null;
    });

    latch.await();
}

@Test
public void testPriority() {
    List<String> executionOrder = Collections.synchronizedList(new ArrayList<>());

    // Submit in reverse priority order
    coordinator.executeExclusive("Low", LOW, () -> {
        executionOrder.add("Low");
        return null;
    });
    coordinator.executeExclusive("High", HIGH, () -> {
        executionOrder.add("High");
        return null;
    });
    coordinator.executeExclusive("Critical", CRITICAL, () -> {
        executionOrder.add("Critical");
        return null;
    });

    Thread.sleep(1000);
    assertEquals(Arrays.asList("Critical", "High", "Low"), executionOrder);
}
```

#### Integration Tests
```java
@Test
public void testLiveDataDuringEcuDiscovery() {
    // Simulate LiveDataActivity polling
    CompletableFuture<?> liveData = coordinator.executeExclusive(
        "LiveData", LOW,
        () -> {
            for (int i = 0; i < 10; i++) {
                elm.sendTelegram("010C".toCharArray());
                Thread.sleep(100);
            }
            return null;
        }
    );

    Thread.sleep(200);  // Let polling start

    // Start ECU discovery (should take priority)
    CompletableFuture<Map<Integer, EcuInfo>> discovery =
        ecuDiscoveryService.discoverEcus();

    Map<Integer, EcuInfo> result = discovery.join();

    // Discovery should complete successfully despite live data polling
    assertFalse(result.isEmpty());

    liveData.join();  // Wait for live data to finish
}
```

---

## 7. Implementation Checklist

### 7.1 Code Changes Required

**New Files:**
- [ ] `ObdServiceCoordinator.java` - Main coordinator class
- [ ] `CoordinatedRequest.java` - Request wrapper
- [ ] `RequestPriority.java` - Priority enum

**Modified Files:**
- [ ] `CommService.java` - Add `public static final ObdServiceCoordinator coordinator`
- [ ] `EcuDiscoveryService.java` - Wrap discoverEcus() with coordinator
- [ ] `FaultCodeService.java` - Wrap scan methods with coordinator
- [ ] `LiveDataActivity.java` - Wrap setService() calls with coordinator
- [ ] `FuelEconomyActivity.java` - Wrap setService() calls with coordinator
- [ ] `ScanOrchestrator.java` - Wrap stage execution with coordinator
- [ ] All scan stages - Minimal changes (access through coordinator)

**Test Files:**
- [ ] `ObdServiceCoordinatorTest.java` - Unit tests
- [ ] `CoordinatorIntegrationTest.java` - Integration tests

### 7.2 Validation Criteria

After implementation, verify:
1. ✅ No crashes when LiveDataActivity runs during full scan
2. ✅ ECU discovery completes successfully even with background polling
3. ✅ Fault code scan doesn't interfere with vehicle info stage
4. ✅ ATH1/ATH0 state always restored after ECU discovery
5. ✅ No "NO DATA" timeouts due to response stealing
6. ✅ No parser errors from unexpected response formats
7. ✅ Performance: Minimal overhead (<10ms per request)
8. ✅ No deadlocks under stress testing (100+ concurrent requests)

---

## 8. Conclusion

### Current State
- ✅ Well-structured isolated services
- ✅ Good internal synchronization (AtomicBoolean, synchronized methods)
- ✅ Proper cleanup (finally blocks)
- ❌ **NO mutual exclusion on shared ELM adapter**
- ❌ Services can interfere with each other
- ❌ Race conditions cause timeouts, crashes, and data corruption

### Recommended Solution
**ObdServiceCoordinator with ReentrantLock**
- Provides mutual exclusion
- Supports request queuing with priorities
- Tracks ownership for debugging
- Timeout protection prevents indefinite locks
- Backward compatible - gradual migration

### Next Steps
1. Implement `ObdServiceCoordinator` base class
2. Add unit tests for mutual exclusion and priority
3. Migrate `EcuDiscoveryService` first (highest risk)
4. Validate with integration tests
5. Migrate remaining services incrementally
6. Monitor production logs for any issues

---

**End of Analysis**
