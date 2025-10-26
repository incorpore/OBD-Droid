package com.obddroid.core.ecu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Adapter that bridges the existing {@link Conversion} API onto the new
 * {@link RawToPhysicalConverter} / {@link PhysicalValueFormatter} contracts.
 */
public final class LegacyConversionAdapter implements RawToPhysicalConverter, PhysicalValueFormatter {

    private final Conversion delegate;

    private LegacyConversionAdapter(Conversion delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate conversion");
    }

    public static LegacyConversionAdapter wrap(Conversion conversion) {
        return new LegacyConversionAdapter(conversion);
    }

    public static List<LegacyConversionAdapter> wrapAll(Conversion[] conversions) {
        if (conversions == null || conversions.length == 0) {
            return Collections.emptyList();
        }
        List<LegacyConversionAdapter> adapters = new ArrayList<>(conversions.length);
        for (Conversion conversion : conversions) {
            if (conversion != null) {
                adapters.add(wrap(conversion));
            }
        }
        return adapters;
    }

    @Override
    public Number convertRawToPhysical(long value) {
        return delegate.memToPhys(value);
    }

    @Override
    public Number convertPhysicalToRaw(Number value) {
        return delegate.physToMem(value);
    }

    @Override
    public String formatPhysicalValue(Number physicalValue, String formatPattern) {
        return delegate.physToPhysFmtString(physicalValue, formatPattern);
    }

    @Override
    public String formatRawValue(Number rawValue, int fractionalDigits) {
        return delegate.memToString(rawValue, fractionalDigits);
    }

    @Override
    public String getUnits() {
        return delegate.getUnits();
    }

    public Conversion getDelegate() {
        return delegate;
    }
}
