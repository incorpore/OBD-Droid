package com.obddroid.core.ecu;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class LegacyConversionAdapterTest {

    private static final class StubConversion implements Conversion {
        long lastRaw;
        Number lastPhysical;
        String units = "rpm";

        @Override
        public Number memToPhys(long value) {
            lastRaw = value;
            return value + 100;
        }

        @Override
        public Number physToMem(Number value) {
            lastPhysical = value;
            return value.longValue() - 50;
        }

        @Override
        public String physToPhysFmtString(Number physVal, String format) {
            return String.format(format, physVal);
        }

        @Override
        public String memToString(Number value, int numDecimals) {
            return String.format("%." + numDecimals + "f", value.doubleValue());
        }

        @Override
        public String getUnits() {
            return units;
        }
    }

    @Test
    public void adapterDelegatesConversionMethods() {
        StubConversion stub = new StubConversion();
        LegacyConversionAdapter adapter = LegacyConversionAdapter.wrap(stub);

        Number physical = adapter.convertRawToPhysical(25);
        assertEquals(125, physical.longValue());
        assertEquals(25, stub.lastRaw);

        Number raw = adapter.convertPhysicalToRaw(230);
        assertEquals(180, raw.longValue());
        assertEquals(230, stub.lastPhysical.longValue());

        assertEquals("123", adapter.formatPhysicalValue(123, "%d"));
        assertEquals("5.00", adapter.formatRawValue(5, 2));
        assertEquals("rpm", adapter.getUnits());
    }

    @Test
    public void wrapAllSkipsNullEntries() {
        StubConversion first = new StubConversion();
        List<LegacyConversionAdapter> adapters =
            LegacyConversionAdapter.wrapAll(new Conversion[]{first, null});

        assertEquals(1, adapters.size());
        assertNotNull(adapters.get(0));

        LegacyConversionAdapter adapter = adapters.get(0);
        assertTrue(adapter.getDelegate() instanceof StubConversion);
    }
}
