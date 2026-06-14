package com.triplencse.acceptassist;

import android.content.SharedPreferences;

final class AcceptPrefs {
    static final String NAME = "accept_assist_prefs";
    static final String KEY_ENABLED = "enabled";
    static final String KEY_MIN_PICKUP = "min_pickup";
    static final String KEY_MAX_PICKUP = "max_pickup";
    static final String KEY_MIN_DROP = "min_drop";
    static final String KEY_MAX_DROP = "max_drop";
    static final String KEY_MIN_PRICE_PER_KM = "min_price_per_km";
    static final String KEY_MIN_PRICE = "min_price";
    static final String KEY_TOGGLE_PRICE_AND = "toggle_price_and";
    static final String KEY_TOGGLE_DIST_PRICE_AND = "toggle_dist_price_and";
    static final String KEY_FILTER_MAX_PICKUP_ACTIVE = "filter_max_pickup_active";
    static final String KEY_FILTER_DROP_ACTIVE = "filter_drop_active";
    static final String KEY_FILTER_PRICE_KM_ACTIVE = "filter_price_km_active";
    static final String KEY_FILTER_TOTAL_PRICE_ACTIVE = "filter_total_price_active";
    static final String KEY_LAST_CLICK_MS = "last_click_ms";
    static final String KEY_APP_MODE = "app_mode";
    static final String KEY_CUSTOM_PACKAGE = "custom_package";
    static final String KEY_CUSTOM_TARGET_TEXT = "custom_target_text";
    static final String KEY_TURSO_URL = "turso_url";
    static final String KEY_TURSO_TOKEN = "turso_token";
    static final String KEY_LOGGED_IN_USER = "logged_in_user";
    static final String KEY_USER_STATUS = "user_status";
    static final String KEY_FREE_CLICKS = "free_clicks";
    static final String KEY_SUB_EXPIRES = "sub_expires";

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
                    .putFloat(KEY_MIN_PRICE_PER_KM, 0.0f)
                    .putFloat(KEY_MIN_PRICE, 0.0f)
                    .putBoolean(KEY_TOGGLE_PRICE_AND, false)
                    .putBoolean(KEY_TOGGLE_DIST_PRICE_AND, false)
                    .putBoolean(KEY_FILTER_MAX_PICKUP_ACTIVE, false)
                    .putBoolean(KEY_FILTER_DROP_ACTIVE, false)
                    .putBoolean(KEY_FILTER_PRICE_KM_ACTIVE, false)
                    .putBoolean(KEY_FILTER_TOTAL_PRICE_ACTIVE, false)
                    .apply();
        }
        if (!prefs.contains(KEY_APP_MODE)) {
            prefs.edit()
                    .putString(KEY_APP_MODE, "rapido")
                    .putString(KEY_CUSTOM_PACKAGE, "")
                    .putString(KEY_CUSTOM_TARGET_TEXT, "Accept")
                    .apply();
        }
        if (!prefs.contains(KEY_TURSO_URL) || prefs.getString(KEY_TURSO_URL, "").isEmpty()) {
            prefs.edit()
                    .putString(KEY_TURSO_URL, "https://accept-assist-augusten6383.aws-ap-south-1.turso.io")
                    .putString(KEY_TURSO_TOKEN, "eyJhbGciOiJFZERTQSIsInR5cCI6IkpXVCJ9.eyJhIjoicnciLCJpYXQiOjE3ODAyNTc1ODEsImlkIjoiMDE5ZTdmOWUtYjEwMS03MjMwLThjYWQtNTRmZjllNmI3ZWU4IiwicmlkIjoiYTEzZDg3YmYtOTdkOS00NjMwLWJkMGYtMjNjN2FkMWJhZmNjIn0.i4RsyjzDOZenpkfXj7HcCK8DCuE3usirajqfaAV3uxuKYfNK2GtzO6n9QBuq0SDwof9azXIQH68c535mM7_YDw")
                    .putString(KEY_LOGGED_IN_USER, "")
                    .putString(KEY_USER_STATUS, "active")
                    .putInt(KEY_FREE_CLICKS, 0)
                    .putLong(KEY_SUB_EXPIRES, 0L)
                    .apply();
        }
    }
}
