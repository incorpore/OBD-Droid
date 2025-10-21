package com.obddroid.core.platform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MockLoggerFactory implements LoggerFactory {

    private final Map<String, MockLogger> loggers = new HashMap<>();

    @Override
    public Logger getLogger(String tag) {
        return loggers.computeIfAbsent(tag, MockLogger::new);
    }

    public List<LogEntry> getEntries(String tag) {
        MockLogger logger = loggers.get(tag);
        return logger != null ? new ArrayList<>(logger.entries) : new ArrayList<>();
    }

    public static final class MockLogger implements Logger {
        private final String tag;
        private final List<LogEntry> entries = new ArrayList<>();

        MockLogger(String tag) {
            this.tag = tag;
        }

        @Override
        public void log(LogLevel level, String message, Throwable error) {
            entries.add(new LogEntry(tag, level, message, error));
        }
    }

    public record LogEntry(String tag, LogLevel level, String message, Throwable error) {}
}
