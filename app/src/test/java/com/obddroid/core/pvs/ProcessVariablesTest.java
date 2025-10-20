package com.obddroid.core.pvs;

import com.obddroid.core.pvs.ProcessVariables.ProcessVar;
import com.obddroid.core.pvs.ProcessVariables.PvChange;
import com.obddroid.core.pvs.ProcessVariables.PvChangeEvent;
import com.obddroid.core.pvs.ProcessVariables.PvChangeType;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ProcessVariablesTest {

    @Test
    public void pvChangeFromEventCapturesAddedEvent() {
        ProcessVar source = new ProcessVar();
        source.setKeyAttribute("id");
        source.put("id", 42);

        PvChangeEvent event = new PvChangeEvent(source, "id", "value", PvChangeEvent.PV_ADDED);
        PvChange change = PvChange.fromEvent(event);

        assertSame("Source should round-trip", source, change.getSource());
        assertEquals("id", change.getKey());
        assertEquals("value", change.getValue());
        assertEquals(PvChangeType.ADDED, change.getPrimaryType());
        assertTrue("Change should include ADDED type", change.includes(PvChangeType.ADDED));
        assertFalse("Initial event is not a child change", change.isChildChange());
        assertTrue("Legacy mask should include ADDED flag",
            (change.getLegacyMask() & PvChangeEvent.PV_ADDED) != 0);
    }

    @Test
    public void pvChangeTracksCombinedFlagsAndChildChanges() {
        ProcessVar source = new ProcessVar();
        PvChangeEvent event = new PvChangeEvent(
            source,
            "key",
            "value",
            PvChangeEvent.PV_MODIFIED
                | PvChangeEvent.PV_MANUAL_MOD
                | PvChangeEvent.PV_CHILDCHANGE
        );

        PvChange change = PvChange.fromEvent(event);

        assertEquals(PvChangeType.MODIFIED, change.getPrimaryType());
        assertTrue(change.includes(PvChangeType.MODIFIED));
        assertTrue(change.includes(PvChangeType.MANUAL_OVERRIDE));
        assertTrue(change.isChildChange());
        assertTrue("Legacy mask should propagate child flag",
            (change.getLegacyMask() & PvChangeEvent.PV_CHILDCHANGE) != 0);
    }

    @Test
    public void pvChangeRoundTripsToLegacyEvent() {
        ProcessVar source = new ProcessVar();
        source.setKeyAttribute("key");
        source.put("key", "vin");

        PvChangeEvent event = new PvChangeEvent(
            source,
            "key",
            "VIN1234567890123",
            PvChangeEvent.PV_MODIFIED
        );
        event.setTime(123L);

        PvChange change = PvChange.fromEvent(event);
        PvChangeEvent roundTrip = change.toLegacyEvent();

        assertNotNull(roundTrip);
        assertEquals("VIN1234567890123", roundTrip.getValue());
        assertEquals(123L, roundTrip.getTime());
        assertEquals(event.getType(), roundTrip.getType());
    }
}
