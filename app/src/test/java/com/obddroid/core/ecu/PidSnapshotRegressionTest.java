package com.obddroid.core.ecu;

import com.obddroid.core.ecu.parser.PidDefinitionCsvParser;
import com.obddroid.core.ecu.parser.PidDefinitionCsvParser.ParseResult;
import com.obddroid.core.ecu.parser.PidDefinitionCsvParser.PidRecord;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

/**
 * Snapshot regression test that verifies representative PID conversions continue to match
 * pre-recorded expectations. This protects against accidental regressions while the conversion
 * and PID definition refactors proceed.
 */
public class PidSnapshotRegressionTest {

    private static Locale originalLocale;
    private static Map<String, PidRecord> pidRecordsByMnemonic;

    @BeforeClass
    public static void setUpClass() throws IOException {
        originalLocale = Locale.getDefault();
        Locale.setDefault(Locale.US);

        EcuConversions conversions = new EcuConversions("/standard/conversions.csv");
        PidDefinitionCsvParser parser = new PidDefinitionCsvParser();

        try (InputStream stream = PidSnapshotRegressionTest.class.getResourceAsStream("/standard/pids.csv")) {
            assertNotNull("Unable to locate standard PID definitions", stream);
            ParseResult parseResult = parser.parse(stream, conversions::get);
            pidRecordsByMnemonic = parseResult.getByMnemonic();
        }
    }

    @AfterClass
    public static void tearDownClass() {
        if (originalLocale != null) {
            Locale.setDefault(originalLocale);
        }
    }

    @Test
    public void pidSnapshotsMatchBaseline() throws IOException {
        List<SnapshotEntry> entries = loadBaseline();
        assertFalse("No snapshot entries loaded", entries.isEmpty());

        for (SnapshotEntry entry : entries) {
            PidRecord record = pidRecordsByMnemonic.get(entry.mnemonic);
            assertNotNull("Missing PID definition for mnemonic " + entry.mnemonic, record);

            PidDefinition definition = record.getDefinition();
            List<LegacyConversionAdapter> adapters = definition.getConversionAdapters();
            assertFalse("No conversion adapters for mnemonic " + entry.mnemonic, adapters.isEmpty());

            LegacyConversionAdapter adapter = adapters.get(0);
            long rawValue = extractRawValue(entry.payloadBytes, definition);

            Number physical = adapter.convertRawToPhysical(rawValue);
            assertEquals("Physical value mismatch for " + entry.mnemonic,
                entry.expectedPhysical,
                physical.doubleValue(),
                entry.tolerance);

            String format = definition.getFormat();
            if (format != null && !format.isEmpty()) {
                String formatted = adapter.formatPhysicalValue(physical, format);
                assertEquals("Formatted value mismatch for " + entry.mnemonic,
                    entry.expectedFormatted,
                    formatted);
            }

            if (entry.expectedUnits != null) {
                assertEquals("Units mismatch for " + entry.mnemonic,
                    entry.expectedUnits,
                    adapter.getUnits());
            }
        }
    }

    private static List<SnapshotEntry> loadBaseline() throws IOException {
        try (InputStream stream = PidSnapshotRegressionTest.class.getResourceAsStream("/pid_snapshot_baseline.tsv")) {
            assertNotNull("Snapshot baseline file missing", stream);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                List<SnapshotEntry> entries = new ArrayList<>();
                String line;
                boolean isHeader = true;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    if (isHeader) {
                        isHeader = false;
                        continue;
                    }
                    String[] parts = line.split("\t");
                    if (parts.length < 6) {
                        continue;
                    }
                    entries.add(SnapshotEntry.from(parts));
                }
                return entries;
            }
        }
    }

    private static long extractRawValue(int[] payload, PidDefinition definition) {
        int offset = definition.getOffset();
        int length = definition.getLengthBytes();
        if (length == 0) {
            return 0L;
        }
        if (payload.length < offset + length) {
            throw new IllegalArgumentException("Payload too short for " + definition.getMnemonic());
        }
        long value = 0L;
        for (int i = 0; i < length; i++) {
            value = (value << 8) | (payload[offset + i] & 0xFFL);
        }

        int bitOffset = definition.getBitOffset();
        int bitLength = definition.getBitLength();
        long bitMask = definition.getBitMask();

        if (bitOffset > 0) {
            value >>= bitOffset;
        }

        if (bitLength > 0 && bitLength < Long.SIZE) {
            long lengthMask = (1L << bitLength) - 1;
            value &= lengthMask;
        }

        return value & bitMask;
    }

    private static final class SnapshotEntry {
        final String mnemonic;
        final int[] payloadBytes;
        final double expectedPhysical;
        final String expectedFormatted;
        final String expectedUnits;
        final double tolerance;

        private SnapshotEntry(
            String mnemonic,
            int[] payloadBytes,
            double expectedPhysical,
            String expectedFormatted,
            String expectedUnits,
            double tolerance
        ) {
            this.mnemonic = mnemonic;
            this.payloadBytes = payloadBytes;
            this.expectedPhysical = expectedPhysical;
            this.expectedFormatted = expectedFormatted;
            this.expectedUnits = expectedUnits;
            this.tolerance = tolerance;
        }

        static SnapshotEntry from(String[] columns) {
            String mnemonic = columns[0].trim();
            String payloadString = columns[1].trim();
            int[] payloadBytes = parsePayload(payloadString);
            double physical = Double.parseDouble(columns[2].trim());
            String formatted = columns[3].trim();
            String units = normalizeUnits(columns[4]);
            double tolerance = Double.parseDouble(columns[5].trim());
            return new SnapshotEntry(mnemonic, payloadBytes, physical, formatted, units, tolerance);
        }

        private static int[] parsePayload(String payload) {
            if (payload.isEmpty()) {
                return new int[0];
            }
            String[] parts = payload.split(" ");
            List<Integer> bytes = new ArrayList<>(parts.length);
            for (String part : parts) {
                String trimmed = part.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                bytes.add(Integer.parseInt(trimmed, 16));
            }
            int[] result = new int[bytes.size()];
            for (int i = 0; i < bytes.size(); i++) {
                result[i] = bytes.get(i);
            }
            return result;
        }

        private static String normalizeUnits(String rawUnits) {
            String trimmed = Objects.requireNonNull(rawUnits, "units").trim();
            return trimmed.equals("-") ? null : trimmed;
        }
    }
}
