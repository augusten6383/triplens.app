package com.triplencse.acceptassist;

import android.content.SharedPreferences;

final class AcceptPrefs {
    static final String NAME = "accept_assist_prefs";
    static final String KEY_ENABLED = "enabled";
    static final String KEY_TARGET_PACKAGE = "target_package";
    static final String KEY_TARGET_TEXT = "target_text";
    static final String KEY_DELAY_MS = "delay_ms";
    static final String KEY_LAST_CLICK_MS = "last_click_ms";

    static final String DEFAULT_TARGET_TEXT = "Accept";
    static final int DEFAULT_DELAY_MS = 75;
    static final int MIN_DELAY_MS = 50;
    static final int MAX_DELAY_MS = 100;

    private AcceptPrefs() {
    }

    static void ensureDefaults(SharedPreferences prefs) {
        if (!prefs.contains(KEY_TARGET_TEXT)) {
            prefs.edit()
                    .putBoolean(KEY_ENABLED, false)
                    .putString(KEY_TARGET_PACKAGE, "")
                    .putString(KEY_TARGET_TEXT, DEFAULT_TARGET_TEXT)
                    .putInt(KEY_DELAY_MS, DEFAULT_DELAY_MS)
                    .apply();
        }
    }

    static int clampDelay(int delayMs) {
        if (delayMs < MIN_DELAY_MS) {
            return MIN_DELAY_MS;
        }
        if (delayMs > MAX_DELAY_MS) {
            return MAX_DELAY_MS;
        }
        return delayMs;
    }
}
