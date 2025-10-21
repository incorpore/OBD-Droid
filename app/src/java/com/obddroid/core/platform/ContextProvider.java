package com.obddroid.core.platform;

/**
 * Provides access to an application-wide context or environment object. On Android this will wrap
 * {@code android.content.Context}; on pure JVM targets it can expose a lightweight surrogate.
 */
public interface ContextProvider<T> {

    T getContext();
}
