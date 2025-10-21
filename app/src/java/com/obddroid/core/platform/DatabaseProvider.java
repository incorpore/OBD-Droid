package com.obddroid.core.platform;

import java.io.Closeable;
import java.util.List;
import java.util.Map;

/**
 * Abstracts data-layer interactions away from direct {@code SQLiteDatabase} or JDBC usage.
 */
public interface DatabaseProvider {

    DatabaseSession openSession(String name, OpenMode mode);

    enum OpenMode {
        READ_ONLY,
        READ_WRITE
    }

    interface DatabaseSession extends Closeable {
        void execute(String sql, Object... args);

        List<Map<String, Object>> query(String sql, Object... args);
    }
}
