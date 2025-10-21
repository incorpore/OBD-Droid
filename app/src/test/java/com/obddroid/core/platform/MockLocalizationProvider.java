package com.obddroid.core.platform;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class MockLocalizationProvider implements LocalizationProvider {

    private final Map<String, String> values = new HashMap<>();
    private Locale locale = Locale.US;

    public MockLocalizationProvider add(String key, String value) {
        values.put(key, value);
        return this;
    }

    @Override
    public String getString(String key) {
        return values.get(key);
    }

    @Override
    public String getString(String key, String defaultValue) {
        return values.getOrDefault(key, defaultValue);
    }

    @Override
    public Map<String, String> getStringsForPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return Collections.unmodifiableMap(values);
        }
        Map<String, String> filtered = new HashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return Collections.unmodifiableMap(filtered);
    }

    @Override
    public Locale getCurrentLocale() {
        return locale;
    }

    @Override
    public void setCurrentLocale(Locale locale) {
        this.locale = locale;
    }
}
