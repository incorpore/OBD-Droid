package com.obddroid.core.ecu;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mutable runtime state for a PID, separate from its immutable definition.
 */
public final class PidRuntime {

    private final PidDefinition definition;
    private final EcuDataPv processVariable;
    private final AtomicInteger consecutiveErrorCount;

    public PidRuntime(PidDefinition definition, EcuDataPv processVariable, int initialErrorCount) {
        this.definition = definition;
        this.processVariable = processVariable;
        this.consecutiveErrorCount = new AtomicInteger(Math.max(0, initialErrorCount));
    }

    public PidDefinition getDefinition() {
        return definition;
    }

    public EcuDataPv getProcessVariable() {
        return processVariable;
    }

    public int getConsecutiveErrorCount() {
        return consecutiveErrorCount.get();
    }

    public int incrementErrorCount() {
        return consecutiveErrorCount.incrementAndGet();
    }

    public int resetErrorCount() {
        consecutiveErrorCount.set(0);
        return 0;
    }
}
