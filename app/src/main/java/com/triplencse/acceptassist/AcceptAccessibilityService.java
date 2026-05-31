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
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;

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
        if (TextUtils.isEmpty(targetPackage)) {
            return;
        }

        boolean targetWindowFound = false;
        if (packageName.equals(targetPackage)) {
            targetWindowFound = true;
        } else {
            java.util.List<android.view.accessibility.AccessibilityWindowInfo> windows = getWindows();
            if (windows != null) {
                for (android.view.accessibility.AccessibilityWindowInfo window : windows) {
                    AccessibilityNodeInfo root = window.getRoot();
                    if (root != null && root.getPackageName() != null && targetPackage.equals(root.getPackageName().toString())) {
                        targetWindowFound = true;
                        break;
                    }
                }
            }
        }

        if (!targetWindowFound) {
            return;
        }

        applyDynamicServiceInfo();

        long now = System.currentTimeMillis();
        if (now - prefs.getLong(AcceptPrefs.KEY_LAST_CLICK_MS, 0L) < CLICK_COOLDOWN_MS) {
            return;
        }

        int delayMs = AcceptPrefs.clampDelay(prefs.getInt(AcceptPrefs.KEY_DELAY_MS, AcceptPrefs.DEFAULT_DELAY_MS));
        
        // Remove any previously scheduled checks and reschedule for [delayMs] after the LATEST event
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(() -> {
            clickIfMatched(targetPackage);
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

        String targetText = prefs.getString(AcceptPrefs.KEY_TARGET_TEXT, AcceptPrefs.DEFAULT_TARGET_TEXT);
        AccessibilityNodeInfo targetNode = null;

        java.util.List<android.view.accessibility.AccessibilityWindowInfo> windows = getWindows();
        for (android.view.accessibility.AccessibilityWindowInfo window : windows) {
            AccessibilityNodeInfo root = window.getRoot();
            if (root != null && root.getPackageName() != null && packageName.equals(root.getPackageName().toString())) {
                AccessibilityNodeInfo match = findClickableMatch(root, targetText);
                if (match != null) {
                    targetNode = match;
                    break;
                }
            }
        }

        if (targetNode == null) {
            return;
        }

        AccessibilityNodeInfo node = targetNode;
        
        android.os.Handler uiHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        uiHandler.post(() -> android.widget.Toast.makeText(this, "Accept Assist: Button found! Clicking...", android.widget.Toast.LENGTH_SHORT).show());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            int x = bounds.centerX();
            int y = bounds.centerY();

            Path clickPath = new Path();
            clickPath.moveTo(x, y);
            clickPath.lineTo(x + 1, y + 1); // Tiny movement to ensure Android registers it as a valid touch
            
            // Increased duration to 150ms to ensure it's not ignored as a phantom touch
            GestureDescription.StrokeDescription clickStroke = new GestureDescription.StrokeDescription(clickPath, 0, 150);
            GestureDescription.Builder clickBuilder = new GestureDescription.Builder();
            clickBuilder.addStroke(clickStroke);

            dispatchGesture(clickBuilder.build(), new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                    prefs.edit().putLong(AcceptPrefs.KEY_LAST_CLICK_MS, System.currentTimeMillis()).apply();
                }
            }, null);

            // Also try normal click simultaneously just in case
            if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                prefs.edit().putLong(AcceptPrefs.KEY_LAST_CLICK_MS, System.currentTimeMillis()).apply();
            }
        } else {
            // Fallback for very old Android versions
            if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                prefs.edit().putLong(AcceptPrefs.KEY_LAST_CLICK_MS, System.currentTimeMillis()).apply();
            }
        }
    }

    private AccessibilityNodeInfo findClickableMatch(AccessibilityNodeInfo root, String targetText) {
        java.util.List<AccessibilityNodeInfo> matches = new java.util.ArrayList<>();
        collectMatches(root, targetText, matches);

        if (matches.isEmpty()) return null;

        // Priority 1: Exact text match & Clickable
        for (AccessibilityNodeInfo node : matches) {
            AccessibilityNodeInfo clickable = nearestClickable(node);
            if (clickable != null && clickable.isClickable()) {
                if (isExactMatch(node, targetText)) return clickable;
            }
        }

        // Priority 2: Contains text & Clickable
        for (AccessibilityNodeInfo node : matches) {
            AccessibilityNodeInfo clickable = nearestClickable(node);
            if (clickable != null && clickable.isClickable()) {
                return clickable;
            }
        }

        // Priority 3: Exact match & Not clickable
        for (AccessibilityNodeInfo node : matches) {
            if (isExactMatch(node, targetText)) return node;
        }

        // Priority 4: Contains text & Not clickable
        return matches.get(matches.size() - 1); // Get the deepest node
    }

    private void collectMatches(AccessibilityNodeInfo node, String targetText, java.util.List<AccessibilityNodeInfo> matches) {
        if (node == null) return;
        if (isTarget(node, targetText)) {
            matches.add(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectMatches(node.getChild(i), targetText, matches);
        }
    }

    private boolean isExactMatch(AccessibilityNodeInfo node, String targetText) {
        String text = asString(node.getText()).toLowerCase(Locale.ROOT).trim();
        String desc = asString(node.getContentDescription()).toLowerCase(Locale.ROOT).trim();
        String[] needles = targetText.toLowerCase(Locale.ROOT).split(",");
        for (String needle : needles) {
            String trimmed = needle.trim();
            if (!trimmed.isEmpty() && (text.equals(trimmed) || desc.equals(trimmed))) {
                return true;
            }
        }
        return false;
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
                | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        info.packageNames = null;
        setServiceInfo(info);
    }
}
