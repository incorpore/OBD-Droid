package com.obddroid.core.ecu;

import java.util.List;
import java.util.Objects;

/**
 * Immutable description of a PID definition, extracted from {@link EcuDataItem}.
 */
public final class PidDefinition {

    private final int pid;
    private final int offset;
    private final int lengthBytes;
    private final int bitOffset;
    private final int bitLength;
    private final long bitMask;
    private final Conversion[] conversions;
    private final String format;
    private final String label;
    private final String mnemonic;
    private final long minUpdatePeriodMs;

    private PidDefinition(Builder builder) {
        this.pid = builder.pid;
        this.offset = builder.offset;
        this.lengthBytes = builder.lengthBytes;
        this.bitOffset = builder.bitOffset;
        this.bitLength = builder.bitLength;
        this.bitMask = builder.bitMask;
        this.conversions = builder.conversions;
        this.format = builder.format;
        this.label = builder.label;
        this.mnemonic = builder.mnemonic;
        this.minUpdatePeriodMs = builder.minUpdatePeriodMs;
    }

    public int getPid() {
        return pid;
    }

    public int getOffset() {
        return offset;
    }

    public int getLengthBytes() {
        return lengthBytes;
    }

    public int getBitOffset() {
        return bitOffset;
    }

    public int getBitLength() {
        return bitLength;
    }

    public long getBitMask() {
        return bitMask;
    }

    public String getFormat() {
        return format;
    }

    public String getLabel() {
        return label;
    }

    public String getMnemonic() {
        return mnemonic;
    }

    public long getMinUpdatePeriodMs() {
        return minUpdatePeriodMs;
    }

    public Conversion[] getConversions() {
        return conversions;
    }

    /**
     * Returns adapters for each configured conversion entry.
     */
    public List<LegacyConversionAdapter> getConversionAdapters() {
        return LegacyConversionAdapter.wrapAll(conversions);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder from(EcuDataItem item) {
        return builder()
            .pid(item.getPid())
            .offset(item.getOffset())
            .lengthBytes(item.getLengthBytes())
            .bitOffset(item.getBitOffset())
            .bitLength(item.getNumBits())
            .bitMask(item.getBitMask())
            .format(item.getFormat())
            .label(item.getLabel())
            .mnemonic(item.getMnemonic())
            .minUpdatePeriodMs(item.updatePeriod_ms)
            .conversions(item.getConversions());
    }

    public static final class Builder {
        private int pid;
        private int offset;
        private int lengthBytes;
        private int bitOffset;
        private int bitLength;
        private long bitMask;
        private Conversion[] conversions = new Conversion[0];
        private String format = "";
        private String label = "";
        private String mnemonic = "";
        private long minUpdatePeriodMs = 0L;

        private Builder() {
        }

        public Builder pid(int value) {
            this.pid = value;
            return this;
        }

        public Builder offset(int value) {
            this.offset = value;
            return this;
        }

        public Builder lengthBytes(int value) {
            this.lengthBytes = value;
            return this;
        }

        public Builder bitOffset(int value) {
            this.bitOffset = value;
            return this;
        }

        public Builder bitLength(int value) {
            this.bitLength = value;
            return this;
        }

        public Builder bitMask(long value) {
            this.bitMask = value;
            return this;
        }

        public Builder conversions(Conversion[] value) {
            this.conversions = value != null ? value.clone() : new Conversion[0];
            return this;
        }

        public Builder format(String value) {
            this.format = value;
            return this;
        }

        public Builder label(String value) {
            this.label = value;
            return this;
        }

        public Builder mnemonic(String value) {
            this.mnemonic = value;
            return this;
        }

        public Builder minUpdatePeriodMs(long value) {
            this.minUpdatePeriodMs = value;
            return this;
        }

        public PidDefinition build() {
            Objects.requireNonNull(mnemonic, "mnemonic");
            return new PidDefinition(this);
        }
    }
}
