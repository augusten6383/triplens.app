package com.triplencse.acceptassist;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AcceptAccessibilityService extends AccessibilityService {
    private static final long CLICK_COOLDOWN_MS = 650;
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "AcceptAssistServiceChannel";

    private final Handler handler = new Handler(Looper.getMainLooper());

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
                .setContentTitle("Triplens Running")
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

        // Subscription verification check
        String status = prefs.getString(AcceptPrefs.KEY_USER_STATUS, "active");
        int freeClicks = prefs.getInt(AcceptPrefs.KEY_FREE_CLICKS, 0);
        long subExpires = prefs.getLong(AcceptPrefs.KEY_SUB_EXPIRES, 0L);

        if ("blocked".equalsIgnoreCase(status)) {
            return;
        }
        boolean isSubscribed = subExpires > (System.currentTimeMillis() / 1000L);
        if (!isSubscribed && freeClicks <= 0) {
            return;
        }

        String appMode = prefs.getString(AcceptPrefs.KEY_APP_MODE, "rapido");
        List<String> targetPackages = new ArrayList<>();

        if ("custom".equals(appMode)) {
            String customPkg = prefs.getString(AcceptPrefs.KEY_CUSTOM_PACKAGE, "");
            if (!TextUtils.isEmpty(customPkg)) {
                targetPackages.add(customPkg);
            }
        } else {
            if (prefs.getBoolean("target_rapido", true)) {
                targetPackages.add("com.rapido.rider");
            }
            if (prefs.getBoolean("target_uber", true)) {
                targetPackages.add("com.ubercab.driver");
            }
        }

        if (targetPackages.isEmpty()) {
            return;
        }

        String packageName = event.getPackageName().toString();
        boolean targetWindowFound = false;
        String matchedPackage = "";

        if (targetPackages.contains(packageName)) {
            targetWindowFound = true;
            matchedPackage = packageName;
        } else {
            List<AccessibilityWindowInfo> windows = getWindows();
            if (windows != null) {
                for (AccessibilityWindowInfo window : windows) {
                    AccessibilityNodeInfo root = window.getRoot();
                    if (root != null && root.getPackageName() != null) {
                        String rootPkg = root.getPackageName().toString();
                        if (targetPackages.contains(rootPkg)) {
                            targetWindowFound = true;
                            matchedPackage = rootPkg;
                            break;
                        }
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

        // INCREASED DELAY: Uber has massive UI animations. We must wait for the card to settle.
        int delayMs = 250; 

        handler.removeCallbacksAndMessages(null);
        final String finalMatchedPackage = matchedPackage;
        handler.postDelayed(() -> clickIfMatched(finalMatchedPackage), delayMs);
    }

    @Override
    public void onInterrupt() {
        handler.removeCallbacksAndMessages(null);
    }

    private void clickIfMatched(String packageName) {
        SharedPreferences prefs = getSharedPreferences(AcceptPrefs.NAME, MODE_PRIVATE);
        if (!prefs.getBoolean(AcceptPrefs.KEY_ENABLED, false)) {
            return;
        }

        String targetText = "Accept";
        String appMode = prefs.getString(AcceptPrefs.KEY_APP_MODE, "rapido");
        if ("custom".equals(appMode)) {
            targetText = prefs.getString(AcceptPrefs.KEY_CUSTOM_TARGET_TEXT, "Accept");
            if (TextUtils.isEmpty(targetText)) {
                targetText = "Accept";
            }
        } else {
            if ("com.ubercab.driver".equals(packageName)) {
                targetText = "accept,match"; 
            } else {
                targetText = "Accept"; 
            }
        }

        List<AccessibilityNodeInfo> allButtons = new ArrayList<>();
        List<AccessibilityWindowInfo> windows = getWindows();
        AccessibilityNodeInfo activeWindowRoot = null;
        
        for (AccessibilityWindowInfo window : windows) {
            AccessibilityNodeInfo root = window.getRoot();
            if (root != null && root.getPackageName() != null && packageName.equals(root.getPackageName().toString())) {
                activeWindowRoot = root; 
                List<AccessibilityNodeInfo> matches = new ArrayList<>();
                collectMatches(root, targetText, matches);
                
                for (AccessibilityNodeInfo match : matches) {
                    // AGGRESSIVE TARGETING: We no longer care if Android thinks it is isClickable(). 
                    // If the node contains our text, we add it and force a hardware gesture on its coordinates.
                    if (!allButtons.contains(match)) {
                        allButtons.add(match);
                    }
                }
            }
        }

        if (allButtons.isEmpty()) {
            return;
        }

        // --- Distance Filtering Logic for Multiple Orders ---
        float minPickup = prefs.getFloat(AcceptPrefs.KEY_MIN_PICKUP, 0.0f);
        float maxPickup = prefs.getFloat(AcceptPrefs.KEY_MAX_PICKUP, 5.0f);
        float minDrop = prefs.getFloat(AcceptPrefs.KEY_MIN_DROP, 0.0f);
        float maxDrop = prefs.getFloat(AcceptPrefs.KEY_MAX_DROP, 15.0f);

        Handler uiHandler = new Handler(Looper.getMainLooper());
        AccessibilityNodeInfo targetNode = null;

        if ("custom".equals(appMode)) {
            targetNode = allButtons.get(0);
        } else {
            for (AccessibilityNodeInfo button : allButtons) {
                StringBuilder cardText = new StringBuilder();
                
                AccessibilityNodeInfo currentParent = button.getParent();
                int levelsClimbed = 0;
                while (currentParent != null && levelsClimbed < 12) {
                    collectAllText(currentParent, cardText);
                    if (cardText.toString().toLowerCase(Locale.ROOT).contains("mi") || cardText.toString().toLowerCase(Locale.ROOT).contains("km")) {
                        break; 
                    }
                    currentParent = currentParent.getParent();
                    levelsClimbed++;
                }

                if (!cardText.toString().toLowerCase(Locale.ROOT).contains("mi") && !cardText.toString().toLowerCase(Locale.ROOT).contains("km") && activeWindowRoot != null) {
                    cardText.setLength(0);
                    collectAllText(activeWindowRoot, cardText);
                }

                Pattern pattern = Pattern.compile("([0-9.]+)\\s*(km|mi|m)\\b", Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(cardText.toString());
                List<Float> distances = new ArrayList<>();
                
                while (matcher.find()) {
                    try {
                        float val = Float.parseFloat(matcher.group(1));
                        String unit = matcher.group(2).toLowerCase(Locale.ROOT);
                        if (unit.equals("m")) {
                            val = val / 1000f; 
                        }
                        distances.add(val);
                    } catch (Exception ignored) {}
                }

                List<Float> uniqueDistances = new ArrayList<>();
                for (float d : distances) {
                    if (uniqueDistances.isEmpty() || uniqueDistances.get(uniqueDistances.size() - 1) != d) {
                        uniqueDistances.add(d);
                    }
                }

                if (uniqueDistances.size() >= 2) {
                    float pickupDistance = uniqueDistances.get(0);
                    float dropDistance = uniqueDistances.get(1);

                    if (pickupDistance >= minPickup && pickupDistance <= maxPickup && dropDistance >= minDrop && dropDistance <= maxDrop) {
                        targetNode = button;
                        break;
                    } else {
                        uiHandler.post(() -> Toast.makeText(this, "Ignored an order: Distances out of bounds", Toast.LENGTH_SHORT).show());
                    }
                } else {
                    targetNode = button;
                    break;
                }
            }
        }

        if (targetNode == null) {
            return;
        }

        String status = prefs.getString(AcceptPrefs.KEY_USER_STATUS, "active");
        int freeClicks = prefs.getInt(AcceptPrefs.KEY_FREE_CLICKS, 0);
        long subExpires = prefs.getLong(AcceptPrefs.KEY_SUB_EXPIRES, 0L);

        if ("blocked".equalsIgnoreCase(status)) {
            uiHandler.post(() -> Toast.makeText(this, "Click blocked: Account blocked by administrator", Toast.LENGTH_LONG).show());
            return;
        }

        boolean isSubscribed = subExpires > (System.currentTimeMillis() / 1000L);
        if (!isSubscribed && freeClicks <= 0) {
            uiHandler.post(() -> Toast.makeText(this, "Click blocked: Subscription required", Toast.LENGTH_LONG).show());
            return;
        }

        if (!isSubscribed && freeClicks > 0) {
            int newClicks = freeClicks - 1;
            prefs.edit().putInt(AcceptPrefs.KEY_FREE_CLICKS, newClicks).apply();

            String username = prefs.getString(AcceptPrefs.KEY_LOGGED_IN_USER, "");
            if (!username.isEmpty()) {
                TursoHelper.useFreeClick(this, username, new TursoHelper.Callback() {
                    @Override public void onSuccess(org.json.JSONArray rows) {}
                    @Override public void onError(String message) {}
                });
            }
        }

        final AccessibilityNodeInfo finalTargetNode = targetNode;
        uiHandler.post(() -> Toast.makeText(this, "Triplens: Executing Accept Input...", Toast.LENGTH_SHORT).show());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Rect bounds = new Rect();
            finalTargetNode.getBoundsInScreen(bounds);
            
            // Generate tap directly on the bounds of the text we found
            int x = bounds.centerX();
            int y = bounds.centerY();

            Path clickPath = new Path();
            clickPath.moveTo(x, y);
            clickPath.lineTo(x + 1, y + 1);
            
            GestureDescription.StrokeDescription clickStroke = new GestureDescription.StrokeDescription(clickPath, 0, 120);
            GestureDescription.Builder clickBuilder = new GestureDescription.Builder();
            clickBuilder.addStroke(clickStroke);

            dispatchGesture(clickBuilder.build(), new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                    prefs.edit().putLong(AcceptPrefs.KEY_LAST_CLICK_MS, System.currentTimeMillis()).apply();
                }
                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    finalTargetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    prefs.edit().putLong(AcceptPrefs.KEY_LAST_CLICK_MS, System.currentTimeMillis()).apply();
                }
            }, null);
        } else {
            if (finalTargetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                prefs.edit().putLong(AcceptPrefs.KEY_LAST_CLICK_MS, System.currentTimeMillis()).apply();
            }
        }
    }

    private void collectMatches(AccessibilityNodeInfo node, String targetText, List<AccessibilityNodeInfo> matches) {
        if (node == null) return;
        if (isTarget(node, targetText)) {
            matches.add(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectMatches(node.getChild(i), targetText, matches);
        }
    }

    private void collectAllText(AccessibilityNodeInfo node, StringBuilder sb) {
        if (node == null) return;
        if (node.getText() != null) {
            sb.append(node.getText().toString()).append(" ");
        }
        if (node.getContentDescription() != null) {
            sb.append(node.getContentDescription().toString()).append(" ");
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectAllText(node.getChild(i), sb);
        }
    }

    private boolean isTarget(AccessibilityNodeInfo node, String targetText) {
        // AGGRESSIVE TARGETING: Removed visibility checks because Uber's overlay layers 
        // often report as "invisible" to standard Android Accessibility services.
        if (node == null) {
            return false;
        }

        String joined = asString(node.getText()) + " " + asString(node.getContentDescription());
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
