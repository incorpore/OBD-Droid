package com.obddroid.core.platform;

/**
 * Factory abstraction supplying {@link Logger} instances keyed by tag or class name.
 */
public interface LoggerFactory {

    Logger getLogger(String tag);

    default Logger getLogger(Class<?> clazz) {
        return getLogger(clazz.getSimpleName());
    }
}
