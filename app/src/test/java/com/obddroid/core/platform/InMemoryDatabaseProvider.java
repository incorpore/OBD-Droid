package com.obddroid.core.platform;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryDatabaseProvider implements DatabaseProvider {

    private final Map<String, RecordingSession> sessions = new ConcurrentHashMap<>();

    @Override
    public DatabaseSession openSession(String name, OpenMode mode) {
        return sessions.computeIfAbsent(name + "#" + mode.name(), key -> new RecordingSession());
    }

    public List<String> getExecutedStatements(String key) {
        RecordingSession session = sessions.get(key);
        return session != null ? session.getExecutedStatements() : Collections.emptyList();
    }

    public static final class RecordingSession implements DatabaseSession {

        private final List<String> executedStatements = new ArrayList<>();

        @Override
        public void execute(String sql, Object... args) {
            executedStatements.add(compose(sql, args));
        }

        @Override
        public List<Map<String, Object>> query(String sql, Object... args) {
            executedStatements.add("QUERY:" + compose(sql, args));
            return Collections.emptyList();
        }

        @Override
        public void close() throws IOException {
            // no-op
        }

        List<String> getExecutedStatements() {
            return Collections.unmodifiableList(executedStatements);
        }

        private static String compose(String sql, Object... args) {
            StringBuilder builder = new StringBuilder(sql);
            if (args != null && args.length > 0) {
                builder.append(" -> ");
                for (int i = 0; i < args.length; i++) {
                    if (i > 0) {
                        builder.append(", ");
                    }
                    builder.append(String.valueOf(args[i]));
                }
            }
            return builder.toString();
        }
    }
}
