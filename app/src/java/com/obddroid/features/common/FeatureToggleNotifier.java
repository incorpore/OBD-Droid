package com.obddroid.features.common;

import androidx.annotation.StringRes;

/**
 * Simple callback interface used by feature coordinators to surface toggle
 * state changes (enabled/disabled) back to a host UI.
 */
public interface FeatureToggleNotifier {

    /**
     * Notify the host UI that a feature has changed state.
     *
     * @param enabled   {@code true} when enabled, {@code false} when disabled
     * @param messageRes String resource describing the state change
     */
    void showToggle(boolean enabled, @StringRes int messageRes);
}
