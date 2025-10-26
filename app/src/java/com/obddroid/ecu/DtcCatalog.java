package com.obddroid.core.ecu;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;

/**
 * Shared contract for diagnostic trouble code catalogues, regardless of
 * whether they are resource-backed or database-backed.
 */
public interface DtcCatalog {

    /**
     * Lookup a code entry given its numeric identifier.
     *
     * @param value numeric representation of the code
     * @return corresponding {@link EcuCodeItem}, or {@code null} when not found
     */
    EcuCodeItem lookup(Number value);

    /**
     * Enumerate known code descriptions. Implementations may return an
     * empty set when enumeration is not practical.
     */
    Set<String> listCodes();

    /**
     * Update the locale tag for lookups where supported.
     */
    default void setLocaleTag(String localeTag) {
        // no-op by default
    }

    /**
     * Retrieve the active locale tag used by the catalogue.
     */
    default String getLocaleTag() {
        return Locale.getDefault().toLanguageTag();
    }

    /**
     * Access to the underlying storage, when needed for advanced operations.
     */
    default Object getBackingStore() {
        return Collections.emptyMap();
    }
}
