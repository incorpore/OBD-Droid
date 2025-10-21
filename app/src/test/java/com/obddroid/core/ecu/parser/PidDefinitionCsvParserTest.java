package com.obddroid.core.ecu.parser;

import com.obddroid.core.ecu.Conversion;
import com.obddroid.core.ecu.IntConversion;
import com.obddroid.core.ecu.PidDefinition;
import com.obddroid.core.ecu.parser.PidDefinitionCsvParser.ParseIssue;
import com.obddroid.core.ecu.parser.PidDefinitionCsvParser.ParseResult;
import com.obddroid.core.ecu.parser.PidDefinitionCsvParser.PidRecord;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PidDefinitionCsvParserTest {

    private static final String HEADER = String.join("\t", new String[]{
        "svc",
        "pid",
        "ofs",
        "len",
        "bit_offset",
        "bit_length",
        "bit_mask",
        "formula",
        "format",
        "min",
        "max",
        "update_cycle_ms",
        "mnemonic",
        "label",
        "description"
    });

    private final Map<String, Conversion[]> conversionMap = new HashMap<>();
    private final PidDefinitionCsvParser parser = new PidDefinitionCsvParser();

    @Before
    public void setUp() {
        conversionMap.clear();
    }

    @Test
    public void parsesValidLine() throws IOException {
        conversionMap.put("LINEAR_A", new Conversion[]{new IntConversion(), new IntConversion()});

        ParseResult result = parseLines(
            "0x01,0x02\t0x0C\t0\t2\t0\t16\t0xFFFF\tLINEAR_A\t%.0f\t0\t8000\t1000\teng_rpm\tEngine RPM\tEngine speed"
        );

        assertTrue(result.getIssues().isEmpty());
        assertEquals(1, result.getRecords().size());
        PidRecord record = result.getRecords().get(0);
        PidDefinition definition = record.getDefinition();
        assertEquals(0x0C, definition.getPid());
        assertEquals(2, definition.getLengthBytes());
        assertEquals(16, definition.getBitLength());
        assertEquals(0xFFFFL, definition.getBitMask());
        assertEquals("eng_rpm", definition.getMnemonic());
        assertEquals("Engine RPM", definition.getLabel());
        assertEquals(1000L, record.getUpdatePeriodMs());
        assertEquals(2, definition.getConversionAdapters().size());
        assertTrue(record.getServiceIds().contains(0x01));
        assertTrue(record.getServiceIds().contains(0x02));
    }

    @Test
    public void recordsIssueForMalformedLine() throws IOException {
        ParseResult result = parseLines(
            "0x01\t0x0C\t0" // Too few columns
        );

        assertTrue(result.getRecords().isEmpty());
        assertEquals(1, result.getIssues().size());
        ParseIssue issue = result.getIssues().get(0);
        assertEquals(PidDefinitionCsvParser.IssueType.MALFORMED_LINE, issue.getType());
        assertEquals(2, issue.getLineNumber());
    }

    @Test
    public void treatsEmptyOptionalFieldsAsNull() throws IOException {
        conversionMap.put("LINEAR_B", new Conversion[]{new IntConversion(), new IntConversion()});

        ParseResult result = parseLines(
            "0x01\t0x10\t0\t1\t0\t8\t0xFF\tLINEAR_B\t%.0f\t\t\t0\tintake_temp\tIntake Temp\t"
        );

        assertEquals(1, result.getRecords().size());
        PidRecord record = result.getRecords().get(0);
        assertEquals("intake_temp", record.getDefinition().getMnemonic());
        assertNull(record.getMinValue());
        assertNull(record.getMaxValue());
        assertEquals(0L, record.getUpdatePeriodMs());
    }

    @Test
    public void detectsDuplicateMnemonics() throws IOException {
        conversionMap.put("LINEAR_A", new Conversion[]{new IntConversion(), new IntConversion()});

        ParseResult result = parseLines(
            "0x01\t0x0C\t0\t2\t0\t16\t0xFFFF\tLINEAR_A\t%.0f\t0\t8000\t1000\teng_rpm\tEngine RPM\tEngine speed",
            "0x01\t0x0D\t0\t2\t0\t16\t0xFFFF\tLINEAR_A\t%.0f\t0\t8000\t1000\teng_rpm\tAnother Engine RPM\tDuplicate"
        );

        assertEquals(1, result.getRecords().size());
        assertEquals("Engine RPM", result.getRecords().get(0).getDefinition().getLabel());
        assertEquals(1, result.getIssues().size());
        ParseIssue issue = result.getIssues().get(0);
        assertEquals(PidDefinitionCsvParser.IssueType.DUPLICATE_MNEMONIC, issue.getType());
        assertTrue(issue.getMessage().contains("eng_rpm"));
    }

    @Test
    public void recordsMissingConversionIssue() throws IOException {
        ParseResult result = parseLines(
            "0x01\t0x0C\t0\t2\t0\t16\t0xFFFF\tUNKNOWN_FORMULA\t%.0f\t0\t8000\t1000\teng_rpm\tEngine RPM\tEngine speed"
        );

        assertEquals(1, result.getRecords().size());
        PidDefinition definition = result.getRecords().get(0).getDefinition();
        assertNotNull(definition);
        assertEquals(0, definition.getConversions().length);

        List<ParseIssue> issues = new ArrayList<>(result.getIssues());
        assertEquals(1, issues.size());
        assertEquals(PidDefinitionCsvParser.IssueType.MISSING_CONVERSION, issues.get(0).getType());
        assertTrue(issues.get(0).getMessage().contains("UNKNOWN_FORMULA"));
    }

    private ParseResult parseLines(String... dataLines) throws IOException {
        StringBuilder csv = new StringBuilder();
        csv.append(HEADER).append('\n');
        for (String line : dataLines) {
            csv.append(line).append('\n');
        }
        StringReader reader = new StringReader(csv.toString());
        return parser.parse(reader, conversionMap::get);
    }
}
