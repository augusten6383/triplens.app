package com.triplencse.acceptassist;

import android.content.SharedPreferences;

final class AcceptPrefs {
    static final String NAME = "accept_assist_prefs";
    static final String KEY_ENABLED = "enabled";
    static final String KEY_MIN_PICKUP = "min_pickup";
    static final String KEY_MAX_PICKUP = "max_pickup";
    static final String KEY_MIN_DROP = "min_drop";
    static final String KEY_MAX_DROP = "max_drop";
    static final String KEY_LAST_CLICK_MS = "last_click_ms";
    static final String KEY_APP_MODE = "app_mode";
    static final String KEY_CUSTOM_PACKAGE = "custom_package";

    private AcceptPrefs() {
    }

    static void ensureDefaults(SharedPreferences prefs) {
        if (!prefs.contains(KEY_MIN_PICKUP)) {
            prefs.edit()
                    .putBoolean(KEY_ENABLED, false)
                    .putFloat(KEY_MIN_PICKUP, 0.0f)
                    .putFloat(KEY_MAX_PICKUP, 5.0f)
                    .putFloat(KEY_MIN_DROP, 0.0f)
                    .putFloat(KEY_MAX_DROP, 15.0f)
                    .apply();
        }
        if (!prefs.contains(KEY_APP_MODE)) {
            prefs.edit()
                    .putString(KEY_APP_MODE, "rapido")
                    .putString(KEY_CUSTOM_PACKAGE, "")
                    .apply();
        }
    }
}
