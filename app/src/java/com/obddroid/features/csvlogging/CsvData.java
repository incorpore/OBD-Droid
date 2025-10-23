package com.obddroid.features.csvlogging;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Append-only in-memory table that aggregates the most recent snapshot of PID data.
 * Rows are flushed to disk by {@link CsvWriterThread}.
 */
final class CsvData {

    private List<String> columns;
    private Map<String, String> columnInstances;
    private Map<String, String> latestValues;
    private Map<Long, Map<String, String>> rows;
    private boolean hasNewColumns;
    private boolean hasPendingData;

    CsvData() {
        this.columns = new ArrayList<>();
        this.columnInstances = new HashMap<>();
        this.latestValues = new HashMap<>();
        this.rows = new HashMap<>();
        this.hasNewColumns = false;
        this.hasPendingData = false;
    }

    CsvData(List<String> columns) {
        this();
        setColumns(columns);
    }

    CsvData(CsvData previous) {
        this.columns = new ArrayList<>(previous.columns);
        this.columnInstances = new HashMap<>(previous.columnInstances);
        this.latestValues = new HashMap<>(previous.latestValues);
        this.rows = new HashMap<>();
        this.hasNewColumns = false;
        this.hasPendingData = false;
    }

    void setColumns(List<String> columns) {
        this.columns = new ArrayList<>(columns);
        this.columnInstances = new HashMap<>(this.columns.size());
        for (String key : this.columns) {
            this.columnInstances.put(key, key);
        }
        this.hasNewColumns = false;
    }

    void setData(String key, String value) {
        if (key == null) {
            return;
        }
        String resolvedKey = columnInstances.get(key);
        if (resolvedKey == null) {
            columns.add(key);
            columnInstances.put(key, key);
            resolvedKey = key;
            hasNewColumns = true;
        }
        latestValues.put(resolvedKey, value);
        hasPendingData = true;
    }

    void saveRow() {
        if (!hasPendingData) {
            return;
        }
        long timestamp = System.currentTimeMillis();
        Map<String, String> row = new HashMap<>(latestValues);
        rows.put(timestamp, row);
        hasPendingData = false;
    }

    long getStartTime() {
        if (rows.isEmpty()) {
            return System.currentTimeMillis();
        }
        Long[] timestamps = rows.keySet().toArray(new Long[0]);
        Arrays.sort(timestamps);
        return timestamps[0];
    }

    long getEndTime() {
        if (rows.isEmpty()) {
            return System.currentTimeMillis();
        }
        Long[] timestamps = rows.keySet().toArray(new Long[0]);
        Arrays.sort(timestamps);
        return timestamps[timestamps.length - 1];
    }

    int size() {
        return rows.size();
    }

    boolean hasNewColumns() {
        return hasNewColumns;
    }

    boolean hasPendingData() {
        return hasPendingData;
    }

    void writeOutput(OutputStreamWriter writer, boolean includeHeader) throws IOException {
        if (includeHeader) {
            StringBuilder header = new StringBuilder();
            header.append("timestamp");
            for (String column : columns) {
                header.append(',');
                header.append(quoteCell(column));
            }
            header.append("\r\n");
            writer.write(header.toString());
        }

        Long[] timestamps = rows.keySet().toArray(new Long[0]);
        Arrays.sort(timestamps);
        for (Long timestamp : timestamps) {
            Map<String, String> row = rows.get(timestamp);
            if (row == null) {
                continue;
            }
            StringBuilder dataRow = new StringBuilder();
            dataRow.append(timestamp);
            for (String column : columns) {
                dataRow.append(',');
                dataRow.append(quoteCell(row.get(column)));
            }
            dataRow.append("\r\n");
            writer.write(dataRow.toString());
        }
    }

    private static String quoteCell(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuote = value.contains(",") || value.contains("\"") || value.contains("\n");
        String escaped = value.replace("\"", "\"\"");
        if (needsQuote) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }
}
