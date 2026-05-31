package com.triplencse.acceptassist;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Locale;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class AcceptAccessibilityService extends AccessibilityService {
    private static final long CLICK_COOLDOWN_MS = 650;
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "AcceptAssistServiceChannel";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean clickScheduled;
    private String scheduledPackage = "";

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        applyDynamicServiceInfo();
        showRunningNotification();
    }

    private void showRunningNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Service Status",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows when the auto-accept service is running");
            manager.createNotificationChannel(channel);
        }

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        Notification notification = builder
                .setContentTitle("Accept Assist Running")
                .setContentText("The accessibility service is active and running in the background.")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .build();

        manager.notify(NOTIFICATION_ID, notification);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        removeRunningNotification();
        return super.onUnbind(intent);
    }

    private void removeRunningNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(NOTIFICATION_ID);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) {
            return;
        }

        SharedPreferences prefs = getSharedPreferences(AcceptPrefs.NAME, MODE_PRIVATE);
        AcceptPrefs.ensureDefaults(prefs);
        if (!prefs.getBoolean(AcceptPrefs.KEY_ENABLED, false)) {
            return;
        }

        String packageName = event.getPackageName().toString();
        String targetPackage = prefs.getString(AcceptPrefs.KEY_TARGET_PACKAGE, "").trim();
        if (TextUtils.isEmpty(targetPackage) || !packageName.equals(targetPackage)) {
            return;
        }

        applyDynamicServiceInfo();

        long now = System.currentTimeMillis();
        if (now - prefs.getLong(AcceptPrefs.KEY_LAST_CLICK_MS, 0L) < CLICK_COOLDOWN_MS) {
            return;
        }

        if (clickScheduled && packageName.equals(scheduledPackage)) {
            return;
        }

        int delayMs = AcceptPrefs.clampDelay(prefs.getInt(AcceptPrefs.KEY_DELAY_MS, AcceptPrefs.DEFAULT_DELAY_MS));
        clickScheduled = true;
        scheduledPackage = packageName;
        handler.postDelayed(() -> {
            clickScheduled = false;
            clickIfMatched(packageName);
        }, delayMs);
    }

    @Override
    public void onInterrupt() {
        clickScheduled = false;
        handler.removeCallbacksAndMessages(null);
    }

    private void clickIfMatched(String packageName) {
        SharedPreferences prefs = getSharedPreferences(AcceptPrefs.NAME, MODE_PRIVATE);
        if (!prefs.getBoolean(AcceptPrefs.KEY_ENABLED, false)) {
            return;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return;
        }
        if (root.getPackageName() == null || !packageName.equals(root.getPackageName().toString())) {
            return;
        }

        String targetText = prefs.getString(AcceptPrefs.KEY_TARGET_TEXT, AcceptPrefs.DEFAULT_TARGET_TEXT);
        AccessibilityNodeInfo node = findClickableMatch(root, targetText);
        if (node == null) {
            return;
        }

        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            prefs.edit()
                    .putLong(AcceptPrefs.KEY_LAST_CLICK_MS, System.currentTimeMillis())
                    .apply();
        }
    }

    private AccessibilityNodeInfo findClickableMatch(AccessibilityNodeInfo root, String targetText) {
        AccessibilityNodeInfo direct = findMatch(root, targetText);
        return direct == null ? null : nearestClickable(direct);
    }

    private AccessibilityNodeInfo findMatch(AccessibilityNodeInfo node, String targetText) {
        if (node == null) {
            return null;
        }

        if (isTarget(node, targetText)) {
            return node;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo match = findMatch(child, targetText);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private boolean isTarget(AccessibilityNodeInfo node, String targetText) {
        if (!node.isVisibleToUser() || !node.isEnabled()) {
            return false;
        }

        String joined = asString(node.getText()) + " " + asString(node.getContentDescription()) + " " + asString(node.getViewIdResourceName());
        String[] needles = targetText.toLowerCase(Locale.ROOT).split(",");
        String haystack = joined.toLowerCase(Locale.ROOT).trim();
        if (TextUtils.isEmpty(haystack)) {
            return false;
        }

        for (String needle : needles) {
            String trimmed = needle.trim();
            if (!trimmed.isEmpty() && haystack.contains(trimmed)) {
                return true;
            }
        }
        return false;
    }

    private AccessibilityNodeInfo nearestClickable(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        while (current != null) {
            if (current.isClickable() && current.isEnabled() && current.isVisibleToUser()) {
                return current;
            }
            current = current.getParent();
        }
        return node.isEnabled() && node.isVisibleToUser() ? node : null;
    }

    private String asString(CharSequence value) {
        return value == null ? "" : value.toString();
    }

    private void applyDynamicServiceInfo() {
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) {
            info = new AccessibilityServiceInfo();
        }
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                | AccessibilityEvent.TYPE_VIEW_CLICKED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 0;
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        info.packageNames = null;
        setServiceInfo(info);
    }
}
