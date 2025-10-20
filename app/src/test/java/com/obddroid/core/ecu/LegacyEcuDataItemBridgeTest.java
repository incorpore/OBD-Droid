package com.obddroid.core.ecu;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

public class LegacyEcuDataItemBridgeTest {

    @Test
    public void bridgeCapturesDefinitionAndRuntime() {
        Conversion[] conversions = new Conversion[]{
            new IntConversion(),
            new IntConversion()
        };

        EcuDataItem item = new EcuDataItem(
            0x0C,
            0,
            2,
            0,
            16,
            0xFFFF,
            conversions,
            "%d",
            null,
            null,
            1000L,
            "Engine RPM",
            "ENG_RPM"
        );

        LegacyEcuDataItemBridge bridge = LegacyEcuDataItemBridge.from(item);

        assertSame(item, bridge.getLegacyItem());

        PidDefinition definition = bridge.getDefinition();
        assertNotNull(definition);
        assertEquals(0x0C, definition.getPid());
        assertEquals(2, definition.getLengthBytes());
        assertEquals(16, definition.getBitLength());
        assertEquals("ENG_RPM", definition.getMnemonic());
        assertEquals(1000L, definition.getMinUpdatePeriodMs());
        assertEquals(2, definition.getConversionAdapters().size());

        PidRuntime runtime = bridge.getRuntime();
        assertNotNull(runtime);
        assertSame(definition, runtime.getDefinition());
        assertSame(item.getProcessVariable(), runtime.getProcessVariable());
    }
}
