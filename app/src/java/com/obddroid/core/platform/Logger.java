package com.obddroid.core.platform;

import java.util.Objects;

/**
 * Platform logger abstraction that decouples core modules from direct Android or
 * {@code java.util.logging} dependencies.
 */
public interface Logger {

    void log(LogLevel level, String message, Throwable error);

    default void log(LogLevel level, String message) {
        log(level, message, null);
    }

    default void trace(String message) {
        log(LogLevel.TRACE, message);
    }

    default void debug(String message) {
        log(LogLevel.DEBUG, message);
    }

    default void info(String message) {
        log(LogLevel.INFO, message);
    }

    default void warn(String message) {
        log(LogLevel.WARN, message);
    }

    default void warn(String message, Throwable error) {
        log(LogLevel.WARN, message, Objects.requireNonNull(error, "error"));
    }

    default void error(String message) {
        log(LogLevel.ERROR, message);
    }

    default void error(String message, Throwable error) {
        log(LogLevel.ERROR, message, Objects.requireNonNull(error, "error"));
    }

    default void wtf(String message) {
        log(LogLevel.ASSERT, message);
    }

    default void wtf(String message, Throwable error) {
        log(LogLevel.ASSERT, message, Objects.requireNonNull(error, "error"));
    }
}
