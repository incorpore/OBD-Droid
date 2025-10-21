package com.obddroid.core.platform;

import org.junit.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PlatformAbstractionsTest {

    @Test
    public void mockLoggerFactoryRecordsEntries() {
        MockLoggerFactory factory = new MockLoggerFactory();
        Logger logger = factory.getLogger("TestTag");

        logger.debug("debug message");
        logger.error("error message");

        List<MockLoggerFactory.LogEntry> entries = factory.getEntries("TestTag");
        assertEquals(2, entries.size());
        assertEquals(LogLevel.DEBUG, entries.get(0).level());
        assertEquals("debug message", entries.get(0).message());
        assertEquals(LogLevel.ERROR, entries.get(1).level());
    }

    @Test
    public void mockLocalizationProviderHandlesFallbacks() {
        MockLocalizationProvider provider = new MockLocalizationProvider()
            .add("greeting.hello", "Hello")
            .add("greeting.goodbye", "Goodbye");

        assertEquals("Hello", provider.getString("greeting.hello"));
        assertEquals("fallback", provider.getString("unknown", "fallback"));

        Map<String, String> greetings = provider.getStringsForPrefix("greeting.");
        assertEquals(2, greetings.size());

        provider.setCurrentLocale(Locale.GERMANY);
        assertEquals(Locale.GERMANY, provider.getCurrentLocale());
    }

    @Test
    public void inMemoryDatabaseProviderTracksStatements() throws Exception {
        InMemoryDatabaseProvider provider = new InMemoryDatabaseProvider();
        try (DatabaseProvider.DatabaseSession session =
                 provider.openSession("test", DatabaseProvider.OpenMode.READ_WRITE)) {
            session.execute("CREATE TABLE foo(id INTEGER)");
            session.query("SELECT * FROM foo WHERE id = ?", 1);
        }

        List<String> executed = provider.getExecutedStatements("test#READ_WRITE");
        assertNotNull(executed);
        assertEquals(2, executed.size());
        assertTrue(executed.get(0).contains("CREATE TABLE"));
        assertTrue(executed.get(1).startsWith("QUERY:"));
    }
}
