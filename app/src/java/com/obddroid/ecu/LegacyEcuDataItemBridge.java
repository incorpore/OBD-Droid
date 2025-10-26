package com.obddroid.core.ecu;

/**
 * Transitional facade that exposes the new PID abstractions while
 * retaining access to the underlying {@link EcuDataItem}.
 */
public final class LegacyEcuDataItemBridge {

    private final EcuDataItem legacyItem;
    private final PidDefinition definition;
    private final PidRuntime runtime;

    private LegacyEcuDataItemBridge(EcuDataItem legacyItem, PidDefinition definition, PidRuntime runtime) {
        this.legacyItem = legacyItem;
        this.definition = definition;
        this.runtime = runtime;
    }

    public static LegacyEcuDataItemBridge from(EcuDataItem item) {
        PidDefinition definition = PidDefinition.from(item).build();
        PidRuntime runtime = new PidRuntime(definition, item.getProcessVariable(), item.getCurrentErrorCount());
        return new LegacyEcuDataItemBridge(item, definition, runtime);
    }

    public EcuDataItem getLegacyItem() {
        return legacyItem;
    }

    public PidDefinition getDefinition() {
        return definition;
    }

    public PidRuntime getRuntime() {
        return runtime;
    }
}
