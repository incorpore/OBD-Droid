package com.obddroid.copilot;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

final class CoPilotSettings {

    private static final String PREF_COPILOT_ENABLED = "copilot_enabled";
    private static final String PREF_WAKE_WORD_ENABLED = "copilot_wake_word";
    private static final String PREF_AGENT_API_ENABLED = "copilot_agent_api";

    private CoPilotSettings() {
    }

    static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(PREF_COPILOT_ENABLED, false);
    }

    static boolean isWakeWordEnabled(Context context) {
        return prefs(context).getBoolean(PREF_WAKE_WORD_ENABLED, false);
    }

    static boolean isAgentApiEnabled(Context context) {
        return prefs(context).getBoolean(PREF_AGENT_API_ENABLED, false);
    }

    private static SharedPreferences prefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
    }
}
