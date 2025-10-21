package com.obddroid.core.platform;

import java.util.Locale;
import java.util.Map;

/**
 * Supplies localized strings and metadata to consumers without binding them directly to Android
 * resource bundles or {@code java.util.ResourceBundle}.
 */
public interface LocalizationProvider {

    String getString(String key);

    String getString(String key, String defaultValue);

    Map<String, String> getStringsForPrefix(String prefix);

    Locale getCurrentLocale();

    void setCurrentLocale(Locale locale);
}
