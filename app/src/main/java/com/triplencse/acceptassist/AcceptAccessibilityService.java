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

        String packageName = event.getPackageName().toString();
        String appMode = prefs.getString(AcceptPrefs.KEY_APP_MODE, "rapido");
        String targetPackage = "com.rapido.rider";
        if ("custom".equals(appMode)) {
            targetPackage = prefs.getString(AcceptPrefs.KEY_CUSTOM_PACKAGE, "");
        }

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

        // Cancel any active polling sequences
        handler.removeCallbacksAndMessages(null);

        // Immediate check (0ms)
        if (clickIfMatched(targetPackage)) {
            return;
        }

        // If not matched immediately, start the high-frequency polling loop
        handler.post(new PollingRunnable(targetPackage, System.currentTimeMillis()));
    }

    @Override
    public void onInterrupt() {
        clickScheduled = false;
        handler.removeCallbacksAndMessages(null);
    }

    private class PollingRunnable implements Runnable {
        private final String targetPackage;
        private final long startTime;

        PollingRunnable(String targetPackage, long startTime) {
            this.targetPackage = targetPackage;
            this.startTime = startTime;
        }

        @Override
        public void run() {
            SharedPreferences prefs = getSharedPreferences(AcceptPrefs.NAME, MODE_PRIVATE);
            long now = System.currentTimeMillis();
            if (now - prefs.getLong(AcceptPrefs.KEY_LAST_CLICK_MS, 0L) < CLICK_COOLDOWN_MS) {
                return;
            }

            if (clickIfMatched(targetPackage)) {
                return; // Clicked successfully, stop polling
            }

            long elapsed = System.currentTimeMillis() - startTime;
            long nextDelay = -1;

            if (elapsed < 8) {
                nextDelay = 8 - elapsed;
            } else if (elapsed >= 8 && elapsed < 12) {
                nextDelay = 1; // Poll every 1ms in the 8-12ms window
            } else if (elapsed >= 12 && elapsed < 16) {
                nextDelay = 16 - elapsed;
            } else if (elapsed >= 16 && elapsed < 20) {
                nextDelay = 1; // Poll every 1ms in the 16-20ms window
            } else if (elapsed >= 20 && elapsed < 120) {
                nextDelay = 10; // Poll every 10ms thereafter until 120ms
            }

            if (nextDelay > 0 && (elapsed + nextDelay) <= 120) {
                handler.postDelayed(this, nextDelay);
            }
        }
    }

    private boolean clickIfMatched(String packageName) {
        SharedPreferences prefs = getSharedPreferences(AcceptPrefs.NAME, MODE_PRIVATE);
        if (!prefs.getBoolean(AcceptPrefs.KEY_ENABLED, false)) {
            return false;
        }

        String targetText = "Accept";
        String appMode = prefs.getString(AcceptPrefs.KEY_APP_MODE, "rapido");
        if ("custom".equals(appMode)) {
            targetText = prefs.getString(AcceptPrefs.KEY_CUSTOM_TARGET_TEXT, "Accept");
            if (TextUtils.isEmpty(targetText)) {
                targetText = "Accept";
            }
        }

        java.util.List<AccessibilityNodeInfo> allButtons = new java.util.ArrayList<>();
        java.util.List<android.view.accessibility.AccessibilityWindowInfo> windows = getWindows();
        for (android.view.accessibility.AccessibilityWindowInfo window : windows) {
            AccessibilityNodeInfo root = window.getRoot();
            if (root != null && root.getPackageName() != null && packageName.equals(root.getPackageName().toString())) {
                java.util.List<AccessibilityNodeInfo> matches = new java.util.ArrayList<>();
                collectMatches(root, targetText, matches);
                
                // Get the actual clickable buttons from the matches
                for (AccessibilityNodeInfo match : matches) {
                    AccessibilityNodeInfo clickable = nearestClickable(match);
                    if (clickable != null && clickable.isClickable() && isExactMatch(match, targetText)) {
                        if (!allButtons.contains(clickable)) {
                            allButtons.add(clickable);
                        }
                    }
                }
            }
        }

        if (allButtons.isEmpty()) {
            return false;
        }

        // --- Distance Filtering Logic for Multiple Orders ---
        float minPickup = prefs.getFloat(AcceptPrefs.KEY_MIN_PICKUP, 0.0f);
        float maxPickup = prefs.getFloat(AcceptPrefs.KEY_MAX_PICKUP, 5.0f);
        float minDrop = prefs.getFloat(AcceptPrefs.KEY_MIN_DROP, 0.0f);
        float maxDrop = prefs.getFloat(AcceptPrefs.KEY_MAX_DROP, 15.0f);

        android.os.Handler uiHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        AccessibilityNodeInfo targetNode = null;

        if ("custom".equals(appMode)) {
            // For custom app mode, bypass distance filters and accept the first found matching button
            targetNode = allButtons.get(0);
        } else {
            for (AccessibilityNodeInfo button : allButtons) {
                // Find the container card for this specific order by going up 5 levels
                AccessibilityNodeInfo card = button;
                for (int i = 0; i < 5; i++) {
                    if (card.getParent() != null) {
                        card = card.getParent();
                    }
                }

                StringBuilder cardText = new StringBuilder();
                collectAllText(card, cardText);

                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("([0-9.]+)\\s*(km|m)\\b", java.util.regex.Pattern.CASE_INSENSITIVE);
                java.util.regex.Matcher matcher = pattern.matcher(cardText.toString());
                java.util.List<Float> distances = new java.util.ArrayList<>();
                
                while (matcher.find()) {
                    try {
                        float val = Float.parseFloat(matcher.group(1));
                        if (matcher.group(2).toLowerCase(Locale.ROOT).equals("m")) {
                            val = val / 1000f; // Convert meters to km
                        }
                        distances.add(val);
                    } catch (Exception ignored) {}
                }

                java.util.List<Float> uniqueDistances = new java.util.ArrayList<>();
                for (float d : distances) {
                    if (uniqueDistances.isEmpty() || uniqueDistances.get(uniqueDistances.size() - 1) != d) {
                        uniqueDistances.add(d);
                    }
                }

                if (uniqueDistances.size() >= 2) {
                    float pickupKm = uniqueDistances.get(0);
                    float dropKm = uniqueDistances.get(1);

                    // --- Extract Total Price ---
                    float basePrice = 0f;
                    float bonusPrice = 0f;
                    
                    // First look for the main price with the ₹ symbol
                    java.util.regex.Pattern basePattern = java.util.regex.Pattern.compile("₹\\s*([0-9]+(?:\\.[0-9]{1,2})?)");
                    java.util.regex.Matcher baseMatcher = basePattern.matcher(cardText.toString());
                    if (baseMatcher.find()) {
                        try {
                            basePrice = Float.parseFloat(baseMatcher.group(1));
                        } catch (Exception ignored) {}
                    }
                    
                    // Look for +XX bonus near the price
                    int priceIdx = cardText.toString().indexOf("₹");
                    if (priceIdx != -1) {
                        int endIdx = Math.min(priceIdx + 100, cardText.length());
                        String nearPrice = cardText.substring(priceIdx, endIdx);
                        java.util.regex.Pattern bonusPattern = java.util.regex.Pattern.compile("\\+\\s*([0-9]+(?:\\.[0-9]{1,2})?)");
                        java.util.regex.Matcher bonusMatcher = bonusPattern.matcher(nearPrice);
                        if (bonusMatcher.find()) {
                            try {
                                bonusPrice = Float.parseFloat(bonusMatcher.group(1));
                            } catch (Exception ignored) {}
                        }
                    }
                    
                    float totalPrice = basePrice + bonusPrice;
                    float totalDistance = pickupKm + dropKm;
                    float pricePerKm = (totalDistance > 0 && totalPrice > 0) ? (totalPrice / totalDistance) : 0f;

                    // --- Apply Smart Filters ---
                    boolean filterMaxPickupActive = prefs.getBoolean(AcceptPrefs.KEY_FILTER_MAX_PICKUP_ACTIVE, false);
                    boolean filterDropActive = prefs.getBoolean(AcceptPrefs.KEY_FILTER_DROP_ACTIVE, false);
                    boolean filterPriceKmActive = prefs.getBoolean(AcceptPrefs.KEY_FILTER_PRICE_KM_ACTIVE, false);
                    boolean filterTotalPriceActive = prefs.getBoolean(AcceptPrefs.KEY_FILTER_TOTAL_PRICE_ACTIVE, false);

                    float minPrice = prefs.getFloat(AcceptPrefs.KEY_MIN_PRICE, 0.0f);
                    float minPricePerKm = prefs.getFloat(AcceptPrefs.KEY_MIN_PRICE_PER_KM, 0.0f);
                    boolean toggleDistPriceAnd = prefs.getBoolean(AcceptPrefs.KEY_TOGGLE_DIST_PRICE_AND, false);
                    boolean togglePriceAnd = prefs.getBoolean(AcceptPrefs.KEY_TOGGLE_PRICE_AND, false);

                    boolean hasDistFilters = filterMaxPickupActive || filterDropActive;
                    boolean hasPriceFilters = filterPriceKmActive || filterTotalPriceActive;

                    boolean distPass = true;
                    if (hasDistFilters) {
                        if (filterMaxPickupActive && pickupKm > maxPickup) distPass = false;
                        if (filterDropActive && (dropKm < minDrop || dropKm > maxDrop)) distPass = false;
                    }

                    boolean pricePass = false;
                    if (hasPriceFilters) {
                        boolean kmPass = !filterPriceKmActive || (pricePerKm >= minPricePerKm);
                        boolean totalPass = !filterTotalPriceActive || (totalPrice >= minPrice);
                        if (togglePriceAnd) {
                            pricePass = kmPass && totalPass;
                        } else {
                            pricePass = kmPass || totalPass;
                        }
                    }

                    boolean finalAccept = true;
                    if (hasDistFilters && hasPriceFilters) {
                        finalAccept = toggleDistPriceAnd ? (distPass && pricePass) : (distPass || pricePass);
                    } else if (hasDistFilters) {
                        finalAccept = distPass;
                    } else if (hasPriceFilters) {
                        finalAccept = pricePass;
                    } else if (!hasDistFilters && !hasPriceFilters) {
                        // Fallback to old behavior if no toggles are active
                        finalAccept = (pickupKm >= minPickup && pickupKm <= maxPickup && dropKm >= minDrop && dropKm <= maxDrop);
                    }

                    if (finalAccept) {
                        targetNode = button;
                        DebugLogManager.log(this, "ACCEPTED", String.format(Locale.US, "Pickup: %.1fkm, Drop: %.1fkm, ₹%.1f", pickupKm, dropKm, totalPrice));
                        break; // Found a valid order!
                    } else {
                        String reason = "Distance or Price limits exceeded.";
                        if (hasDistFilters && !distPass) reason = "Distance limits exceeded.";
                        if (hasPriceFilters && !pricePass) reason = "Price limits not met.";
                        DebugLogManager.log(this, "REJECTED", reason + String.format(Locale.US, " (Pickup: %.1fkm, Drop: %.1fkm, ₹%.1f)", pickupKm, dropKm, totalPrice));
                        uiHandler.post(() -> android.widget.Toast.makeText(this, "Ignored: Filter limits exceeded", android.widget.Toast.LENGTH_SHORT).show());
                    }
                } else {
                    // If it can't find distances on the card, we accept it by default so we don't break
                    targetNode = button;
                    break;
                }
            }
        }

        if (targetNode == null) {
            return false;
        }

        // Perform status / subscription checks right before the click is made
        String status = prefs.getString(AcceptPrefs.KEY_USER_STATUS, "active");
        int freeClicks = prefs.getInt(AcceptPrefs.KEY_FREE_CLICKS, 0);
        long subExpires = prefs.getLong(AcceptPrefs.KEY_SUB_EXPIRES, 0L);

        if ("blocked".equalsIgnoreCase(status)) {
            DebugLogManager.log(this, "BLOCKED", "Account blocked by administrator.");
            uiHandler.post(() -> android.widget.Toast.makeText(this, "Click blocked: Account blocked by administrator", android.widget.Toast.LENGTH_LONG).show());
            return false;
        }

        boolean isSubscribed = subExpires > (System.currentTimeMillis() / 1000L);
        if (!isSubscribed && freeClicks <= 0) {
            DebugLogManager.log(this, "BLOCKED", "Subscription expired and no free clicks left.");
            uiHandler.post(() -> android.widget.Toast.makeText(this, "Click blocked: Subscription required", android.widget.Toast.LENGTH_LONG).show());
            return false;
        }

        if (!isSubscribed && freeClicks > 0) {
            // Decrement local free clicks
            int newClicks = freeClicks - 1;
            prefs.edit().putInt(AcceptPrefs.KEY_FREE_CLICKS, newClicks).apply();

            // Notify server in the background
            String username = prefs.getString(AcceptPrefs.KEY_LOGGED_IN_USER, "");
            if (!username.isEmpty()) {
                TursoHelper.useFreeClick(this, username, new TursoHelper.Callback() {
                    @Override public void onSuccess(org.json.JSONArray rows) {}
                    @Override public void onError(String message) {}
                });
            }
        }

        AccessibilityNodeInfo node = targetNode;
        uiHandler.post(() -> android.widget.Toast.makeText(this, "Triplens: Distance valid! Clicking...", android.widget.Toast.LENGTH_SHORT).show());

        boolean clicked = false;
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

            clicked = true;
            // Also try normal click simultaneously just in case
            if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                prefs.edit().putLong(AcceptPrefs.KEY_LAST_CLICK_MS, System.currentTimeMillis()).apply();
            }
        } else {
            // Fallback for very old Android versions
            if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                prefs.edit().putLong(AcceptPrefs.KEY_LAST_CLICK_MS, System.currentTimeMillis()).apply();
                clicked = true;
            }
        }
        return clicked;
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
