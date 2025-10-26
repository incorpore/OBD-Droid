package com.obddroid.core.ecu.parser;

import com.obddroid.ecu.Conversion;
import com.obddroid.ecu.PidDefinition;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Lightweight CSV parser for PID definitions. This surfaces the implicit parsing logic that was
 * previously embedded inside {@code EcuDataItems.loadFromStream} so we can unit test behaviour
 * around malformed rows, optional fields, and conversion lookups ahead of the repository refactor.
 */
public final class PidDefinitionCsvParser {

    private static final int REQUIRED_FIELD_COUNT = Field.NUMBER_OF_FIELDS.ordinal();

    /**
     * Functional contract that supplies conversion arrays for a given formula name. Implementations
     * can delegate to {@code EcuConversions} or any other registry.
     */
    @FunctionalInterface
    public interface ConversionResolver {
        Conversion[] resolve(String formulaName);
    }

    /**
     * Structured result comprising successfully parsed records and any issues encountered.
     */
    public static final class ParseResult {
        private final List<PidRecord> records;
        private final List<ParseIssue> issues;
        private final Map<String, PidRecord> byMnemonic;

        private ParseResult(
            List<PidRecord> records,
            List<ParseIssue> issues,
            Map<String, PidRecord> byMnemonic
        ) {
            this.records = Collections.unmodifiableList(records);
            this.issues = Collections.unmodifiableList(issues);
            this.byMnemonic = Collections.unmodifiableMap(byMnemonic);
        }

        public List<PidRecord> getRecords() {
            return records;
        }

        public List<ParseIssue> getIssues() {
            return issues;
        }

        public Map<String, PidRecord> getByMnemonic() {
            return byMnemonic;
        }
    }

    /**
     * Captures a single parsed PID record along with auxiliary metadata required by legacy code.
     */
    public static final class PidRecord {
        private final PidDefinition definition;
        private final Set<Integer> serviceIds;
        private final Float minValue;
        private final Float maxValue;
        private final long updatePeriodMs;
        private final String description;

        private PidRecord(
            PidDefinition definition,
            Set<Integer> serviceIds,
            Float minValue,
            Float maxValue,
            long updatePeriodMs,
            String description
        ) {
            this.definition = definition;
            this.serviceIds = Collections.unmodifiableSet(serviceIds);
            this.minValue = minValue;
            this.maxValue = maxValue;
            this.updatePeriodMs = updatePeriodMs;
            this.description = description;
        }

        public PidDefinition getDefinition() {
            return definition;
        }

        public Set<Integer> getServiceIds() {
            return serviceIds;
        }

        public Float getMinValue() {
            return minValue;
        }

        public Float getMaxValue() {
            return maxValue;
        }

        public long getUpdatePeriodMs() {
            return updatePeriodMs;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Enumerates the kinds of issues we surface during parsing so callers can decide whether to
     * treat them as warnings or hard failures.
     */
    public enum IssueType {
        MALFORMED_LINE,
        INVALID_NUMBER,
        MISSING_MNEMONIC,
        MISSING_CONVERSION,
        DUPLICATE_MNEMONIC,
        MISSING_SERVICE
    }

    /**
     * Structured warning/error emitted while scanning the CSV input.
     */
    public static final class ParseIssue {
        private final int lineNumber;
        private final IssueType type;
        private final String message;

        private ParseIssue(int lineNumber, IssueType type, String message) {
            this.lineNumber = lineNumber;
            this.type = type;
            this.message = message;
        }

        public static ParseIssue malformed(int line, String message) {
            return new ParseIssue(line, IssueType.MALFORMED_LINE, message);
        }

        public static ParseIssue invalidNumber(int line, String message) {
            return new ParseIssue(line, IssueType.INVALID_NUMBER, message);
        }

        public static ParseIssue missingMnemonic(int line) {
            return new ParseIssue(line, IssueType.MISSING_MNEMONIC, "Mnemonic column is blank");
        }

        public static ParseIssue missingConversion(int line, String formula) {
            return new ParseIssue(line, IssueType.MISSING_CONVERSION,
                "Conversion not found for formula '" + formula + "'");
        }

        public static ParseIssue duplicateMnemonic(int line, String mnemonic) {
            return new ParseIssue(line, IssueType.DUPLICATE_MNEMONIC,
                "Duplicate mnemonic encountered: " + mnemonic);
        }

        public static ParseIssue missingService(int line, String value) {
            return new ParseIssue(line, IssueType.MISSING_SERVICE,
                "Unable to parse service identifiers: '" + value + "'");
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public IssueType getType() {
            return type;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * Parse PID definitions from the provided input stream.
     *
     * @param stream  UTF-8 encoded CSV stream
     * @param resolver conversion resolver backing formula lookups
     */
    public ParseResult parse(InputStream stream, ConversionResolver resolver) throws IOException {
        Objects.requireNonNull(stream, "stream");
        return parse(new InputStreamReader(stream, StandardCharsets.UTF_8), resolver);
    }

    /**
     * Parse PID definitions from the provided reader.
     *
     * @param reader   CSV reader
     * @param resolver conversion resolver backing formula lookups
     */
    public ParseResult parse(Reader reader, ConversionResolver resolver) throws IOException {
        Objects.requireNonNull(reader, "reader");
        Objects.requireNonNull(resolver, "resolver");

        List<PidRecord> records = new ArrayList<>();
        List<ParseIssue> issues = new ArrayList<>();
        Map<String, PidRecord> byMnemonic = new LinkedHashMap<>();

        try (BufferedReader buffered = new BufferedReader(reader)) {
            String rawLine;
            int lineNumber = 0;
            while ((rawLine = buffered.readLine()) != null) {
                lineNumber++;
                if (lineNumber == 1 || rawLine.trim().isEmpty() || rawLine.startsWith("#")) {
                    continue; // Skip header/comments
                }

                String line = rawLine.replace("\"", "");
                String[] columns = line.split("\t", -1);
                if (columns.length < REQUIRED_FIELD_COUNT) {
                    issues.add(ParseIssue.malformed(
                        lineNumber,
                        "Expected at least " + REQUIRED_FIELD_COUNT + " columns but found " + columns.length
                    ));
                    continue;
                }

                String mnemonic = columns[Field.MNEMONIC.ordinal()].trim();
                if (mnemonic.isEmpty()) {
                    issues.add(ParseIssue.missingMnemonic(lineNumber));
                    continue;
                }

                if (byMnemonic.containsKey(mnemonic)) {
                    issues.add(ParseIssue.duplicateMnemonic(lineNumber, mnemonic));
                    continue;
                }

                Set<Integer> serviceIds = parseServices(columns[Field.SVC.ordinal()], lineNumber, issues);
                if (serviceIds.isEmpty()) {
                    continue;
                }

                Integer pid = parseInteger(columns[Field.PID.ordinal()], lineNumber, "pid", issues, true);
                Integer offset = parseInteger(columns[Field.OFS.ordinal()], lineNumber, "offset", issues, false);
                Integer lengthBytes = parseInteger(columns[Field.LEN.ordinal()], lineNumber, "length", issues, false);
                Integer bitOffset = parseInteger(columns[Field.BIT_OFS.ordinal()], lineNumber, "bitOffset", issues, false);
                Integer bitLength = parseInteger(columns[Field.BIT_LEN.ordinal()], lineNumber, "bitLength", issues, false);
                Long bitMask = parseLong(columns[Field.BIT_MASK.ordinal()], lineNumber, "bitMask", issues);

                if (pid == null || offset == null || lengthBytes == null
                    || bitOffset == null || bitLength == null || bitMask == null) {
                    continue;
                }

                String formula = columns[Field.FORMULA.ordinal()].trim();
                Conversion[] conversions = resolver.resolve(formula);
                if (conversions == null) {
                    issues.add(ParseIssue.missingConversion(lineNumber, formula));
                    conversions = new Conversion[0];
                }

                String format = columns[Field.FORMAT.ordinal()];
                Float minValue = parseFloat(columns[Field.MIN.ordinal()], lineNumber, "min", issues);
                Float maxValue = parseFloat(columns[Field.MAX.ordinal()], lineNumber, "max", issues);
                Long updatePeriod = parseLong(columns[Field.UPDATE_MIN.ordinal()], lineNumber, "updatePeriod", issues, true);
                long updatePeriodMs = updatePeriod != null ? updatePeriod : 0L;
                String label = columns[Field.LABEL.ordinal()];
                String description = columns[Field.DESCRIPTION.ordinal()];

                PidDefinition definition = PidDefinition.builder()
                    .pid(pid)
                    .offset(offset)
                    .lengthBytes(lengthBytes)
                    .bitOffset(bitOffset)
                    .bitLength(bitLength)
                    .bitMask(bitMask)
                    .conversions(conversions)
                    .format(format)
                    .label(label)
                    .mnemonic(mnemonic)
                    .minUpdatePeriodMs(updatePeriodMs)
                    .build();

                PidRecord record = new PidRecord(
                    definition,
                    serviceIds,
                    minValue,
                    maxValue,
                    updatePeriodMs,
                    description
                );
                records.add(record);
                byMnemonic.put(mnemonic, record);
            }
        }

        return new ParseResult(records, issues, byMnemonic);
    }

    private static Set<Integer> parseServices(String value, int lineNumber, List<ParseIssue> issues) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            issues.add(ParseIssue.missingService(lineNumber, value));
            return Collections.emptySet();
        }

        String[] parts = trimmed.split(",");
        Set<Integer> serviceIds = new LinkedHashSet<>();
        for (String part : parts) {
            Integer svc = parseInteger(part, lineNumber, "service", issues, true);
            if (svc == null) {
                return Collections.emptySet();
            }
            serviceIds.add(svc);
        }
        return serviceIds;
    }

    private static Integer parseInteger(
        String value,
        int lineNumber,
        String field,
        List<ParseIssue> issues,
        boolean allowHex
    ) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            issues.add(ParseIssue.invalidNumber(lineNumber, "Missing integer for " + field));
            return null;
        }

        try {
            return allowHex ? Integer.decode(trimmed) : Integer.parseInt(trimmed);
        } catch (NumberFormatException ex) {
            issues.add(ParseIssue.invalidNumber(
                lineNumber,
                "Invalid integer for " + field + ": '" + trimmed + "'"
            ));
            return null;
        }
    }

    private static Long parseLong(
        String value,
        int lineNumber,
        String field,
        List<ParseIssue> issues
    ) {
        return parseLong(value, lineNumber, field, issues, false);
    }

    private static Long parseLong(
        String value,
        int lineNumber,
        String field,
        List<ParseIssue> issues,
        boolean optional
    ) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            if (optional) {
                return null;
            }
            issues.add(ParseIssue.invalidNumber(lineNumber, "Missing long for " + field));
            return null;
        }

        try {
            return trimmed.startsWith("0x") || trimmed.startsWith("0X")
                ? Long.decode(trimmed)
                : Long.parseLong(trimmed);
        } catch (NumberFormatException ex) {
            issues.add(ParseIssue.invalidNumber(
                lineNumber,
                "Invalid long for " + field + ": '" + trimmed + "'"
            ));
            return null;
        }
    }

    private static Float parseFloat(
        String value,
        int lineNumber,
        String field,
        List<ParseIssue> issues
    ) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return Float.parseFloat(trimmed);
        } catch (NumberFormatException ex) {
            issues.add(ParseIssue.invalidNumber(
                lineNumber,
                "Invalid float for " + field + ": '" + trimmed + "'"
            ));
            return null;
        }
    }

    private enum Field {
        SVC,
        PID,
        OFS,
        LEN,
        BIT_OFS,
        BIT_LEN,
        BIT_MASK,
        FORMULA,
        FORMAT,
        MIN,
        MAX,
        UPDATE_MIN,
        MNEMONIC,
        LABEL,
        DESCRIPTION,
        NUMBER_OF_FIELDS
    }
}
