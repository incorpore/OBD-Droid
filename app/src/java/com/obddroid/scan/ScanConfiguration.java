package com.obddroid.scan;

/**
 * Configuration options for a full vehicle scan.
 */
public final class ScanConfiguration {

    private final boolean includeLiveData;
    private final boolean includeFreezeFrames;
    private final boolean includeMonitorTests;
    private final boolean includeComponentTests;
    private final int timeoutPerStageSeconds;

    private ScanConfiguration(Builder builder) {
        this.includeLiveData = builder.includeLiveData;
        this.includeFreezeFrames = builder.includeFreezeFrames;
        this.includeMonitorTests = builder.includeMonitorTests;
        this.includeComponentTests = builder.includeComponentTests;
        this.timeoutPerStageSeconds = builder.timeoutPerStageSeconds;
    }

    public static ScanConfiguration getDefault() {
        return new Builder()
            .includeLiveData(true)
            .includeFreezeFrames(true)
            .includeMonitorTests(true)
            .includeComponentTests(false) // Often not supported
            .timeoutPerStageSeconds(30)
            .build();
    }

    public boolean isIncludeLiveData() {
        return includeLiveData;
    }

    public boolean isIncludeFreezeFrames() {
        return includeFreezeFrames;
    }

    public boolean isIncludeMonitorTests() {
        return includeMonitorTests;
    }

    public boolean isIncludeComponentTests() {
        return includeComponentTests;
    }

    public int getTimeoutPerStageSeconds() {
        return timeoutPerStageSeconds;
    }

    public static final class Builder {
        private boolean includeLiveData = true;
        private boolean includeFreezeFrames = true;
        private boolean includeMonitorTests = true;
        private boolean includeComponentTests = false;
        private int timeoutPerStageSeconds = 30;

        public Builder includeLiveData(boolean include) {
            this.includeLiveData = include;
            return this;
        }

        public Builder includeFreezeFrames(boolean include) {
            this.includeFreezeFrames = include;
            return this;
        }

        public Builder includeMonitorTests(boolean include) {
            this.includeMonitorTests = include;
            return this;
        }

        public Builder includeComponentTests(boolean include) {
            this.includeComponentTests = include;
            return this;
        }

        public Builder timeoutPerStageSeconds(int seconds) {
            this.timeoutPerStageSeconds = seconds;
            return this;
        }

        public ScanConfiguration build() {
            return new ScanConfiguration(this);
        }
    }
}
