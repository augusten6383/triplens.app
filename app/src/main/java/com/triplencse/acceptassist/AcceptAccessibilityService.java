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
import java.util.regex.Pattern;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;

import android.view.WindowManager;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.widget.ImageView;
import android.view.View;
import android.view.MotionEvent;
import android.graphics.drawable.GradientDrawable;
import android.view.ViewGroup;

public class AcceptAccessibilityService extends AccessibilityService {
    private static final long CLICK_COOLDOWN_MS = 650;
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "AcceptAssistServiceChannel";

    // Precompiled regex patterns to avoid recompiling them on every click evaluation
    private static final Pattern DIST_PATTERN = Pattern.compile("([0-9.]+)\\s*(km|m)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern BASE_PRICE_PATTERN = Pattern.compile("₹\\s*([0-9]+(?:\\.[0-9]{1,2})?)");
    private static final Pattern BONUS_PRICE_PATTERN = Pattern.compile("\\+\\s*([0-9]+(?:\\.[0-9]{1,2})?)");

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean clickScheduled;
    private String scheduledPackage = "";

    // Cache variables to avoid querying SharedPreferences in the 1ms polling loop
    private boolean cacheEnabled;
    private String cacheAppMode;
    private String cacheTargetPackage;
    private String cacheTargetText;
    private float cacheMinPickup;
    private float cacheMaxPickup;
    private float cacheMinDrop;
    private float cacheMaxDrop;
    private String cacheUserStatus;
    private int cacheFreeClicks;
    private long cacheSubExpires;
    private boolean cacheFilterMaxPickupActive;
    private boolean cacheFilterDropActive;
    private boolean cacheFilterPriceKmActive;
    private boolean cacheFilterTotalPriceActive;
    private float cacheMinPrice;
    private float cacheMinPricePerKm;
    private boolean cacheToggleDistPriceAnd;
    private boolean cacheTogglePriceAnd;
    private String cacheLoggedInUser;

    // WindowManager overlay fields for floating bubble
    private WindowManager windowManager;
    private ImageView bubbleView;
    private WindowManager.LayoutParams bubbleParams;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        applyDynamicServiceInfo();
        showRunningNotification();
        showBubble();
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
        removeBubble();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        removeBubble();
        super.onDestroy();
    }

    private void removeRunningNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(NOTIFICATION_ID);
        }
    }

    private void showBubble() {
        if (bubbleView != null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this)) {
            return;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null) return;

        bubbleView = new ImageView(this);
        bubbleView.setImageResource(R.mipmap.ic_launcher);

        int size = dpToPx(60);
        bubbleView.setLayoutParams(new ViewGroup.LayoutParams(size, size));

        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(android.graphics.Color.WHITE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            bubbleView.setElevation(dpToPx(6));
        }
        bubbleView.setBackground(shape);
        int padding = dpToPx(8);
        bubbleView.setPadding(padding, padding, padding, padding);

        int layoutType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutType = WindowManager.LayoutParams.TYPE_PHONE;
        }

        bubbleParams = new WindowManager.LayoutParams(
                size,
                size,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );

        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = getResources().getDisplayMetrics().widthPixels - size - dpToPx(16);
        bubbleParams.y = dpToPx(150);

        bubbleView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private long clickStartTime;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = bubbleParams.x;
                        initialY = bubbleParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        clickStartTime = System.currentTimeMillis();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        bubbleParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        bubbleParams.y = initialY + (int) (event.getRawY() - initialTouchY);

                        int screenWidth = getResources().getDisplayMetrics().widthPixels;
                        int screenHeight = getResources().getDisplayMetrics().heightPixels;
                        if (bubbleParams.x < 0) bubbleParams.x = 0;
                        if (bubbleParams.x > screenWidth - size) bubbleParams.x = screenWidth - size;
                        if (bubbleParams.y < 0) bubbleParams.y = 0;
                        if (bubbleParams.y > screenHeight - size) bubbleParams.y = screenHeight - size;

                        windowManager.updateViewLayout(bubbleView, bubbleParams);
                        return true;
                    case MotionEvent.ACTION_UP:
                        long clickDuration = System.currentTimeMillis() - clickStartTime;
                        float deltaX = Math.abs(event.getRawX() - initialTouchX);
                        float deltaY = Math.abs(event.getRawY() - initialTouchY);

                        if (clickDuration < 250 && deltaX < 10 && deltaY < 10) {
                            openApp();
                        }
                        return true;
                }
                return false;
            }
        });

        try {
            windowManager.addView(bubbleView, bubbleParams);
        } catch (Exception e) {
            DebugLogManager.log(this, "BUBBLE", "Failed to add floating bubble: " + e.getMessage());
        }
    }

    private void removeBubble() {
        if (windowManager != null && bubbleView != null) {
            try {
                windowManager.removeView(bubbleView);
            } catch (Exception ignored) {}
            bubbleView = null;
        }
    }

    private void openApp() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) {
            return;
        }

        SharedPreferences prefs = getSharedPreferences(AcceptPrefs.NAME, MODE_PRIVATE);
        AcceptPrefs.ensureDefaults(prefs);

        // Cache preference configurations to prevent slow SharedPreferences reads in the loop
        cacheEnabled = prefs.getBoolean(AcceptPrefs.KEY_ENABLED, false);
        if (!cacheEnabled) {
            return;
        }

        // Subscription verification check
        cacheUserStatus = prefs.getString(AcceptPrefs.KEY_USER_STATUS, "active");
        cacheFreeClicks = prefs.getInt(AcceptPrefs.KEY_FREE_CLICKS, 0);
        cacheSubExpires = prefs.getLong(AcceptPrefs.KEY_SUB_EXPIRES, 0L);

        if ("blocked".equalsIgnoreCase(cacheUserStatus)) {
            return;
        }
        boolean isSubscribed = cacheSubExpires > (System.currentTimeMillis() / 1000L);
        if (!isSubscribed && cacheFreeClicks <= 0) {
            return;
        }

        String packageName = event.getPackageName().toString();
        cacheAppMode = prefs.getString(AcceptPrefs.KEY_APP_MODE, "rapido");
        cacheTargetPackage = "com.rapido.rider";
        if ("custom".equals(cacheAppMode)) {
            cacheTargetPackage = prefs.getString(AcceptPrefs.KEY_CUSTOM_PACKAGE, "");
        }

        if (TextUtils.isEmpty(cacheTargetPackage)) {
            return;
        }

        // Target matching text
        cacheTargetText = "Accept";
        if ("custom".equals(cacheAppMode)) {
            cacheTargetText = prefs.getString(AcceptPrefs.KEY_CUSTOM_TARGET_TEXT, "Accept");
            if (TextUtils.isEmpty(cacheTargetText)) {
                cacheTargetText = "Accept";
            }
        }

        cacheMinPickup = prefs.getFloat(AcceptPrefs.KEY_MIN_PICKUP, 0.0f);
        cacheMaxPickup = prefs.getFloat(AcceptPrefs.KEY_MAX_PICKUP, 5.0f);
        cacheMinDrop = prefs.getFloat(AcceptPrefs.KEY_MIN_DROP, 0.0f);
        cacheMaxDrop = prefs.getFloat(AcceptPrefs.KEY_MAX_DROP, 15.0f);

        cacheFilterMaxPickupActive = prefs.getBoolean(AcceptPrefs.KEY_FILTER_MAX_PICKUP_ACTIVE, false);
        cacheFilterDropActive = prefs.getBoolean(AcceptPrefs.KEY_FILTER_DROP_ACTIVE, false);
        cacheFilterPriceKmActive = prefs.getBoolean(AcceptPrefs.KEY_FILTER_PRICE_KM_ACTIVE, false);
        cacheFilterTotalPriceActive = prefs.getBoolean(AcceptPrefs.KEY_FILTER_TOTAL_PRICE_ACTIVE, false);

        cacheMinPrice = prefs.getFloat(AcceptPrefs.KEY_MIN_PRICE, 0.0f);
        cacheMinPricePerKm = prefs.getFloat(AcceptPrefs.KEY_MIN_PRICE_PER_KM, 0.0f);
        cacheToggleDistPriceAnd = prefs.getBoolean(AcceptPrefs.KEY_TOGGLE_DIST_PRICE_AND, true);
        cacheTogglePriceAnd = prefs.getBoolean(AcceptPrefs.KEY_TOGGLE_PRICE_AND, false);
        cacheLoggedInUser = prefs.getString(AcceptPrefs.KEY_LOGGED_IN_USER, "");

        boolean targetWindowFound = false;
        if (packageName.equals(cacheTargetPackage)) {
            targetWindowFound = true;
        } else {
            AccessibilityNodeInfo activeRoot = getRootInActiveWindow();
            if (activeRoot != null && activeRoot.getPackageName() != null && cacheTargetPackage.equals(activeRoot.getPackageName().toString())) {
                targetWindowFound = true;
            } else {
                java.util.List<android.view.accessibility.AccessibilityWindowInfo> windows = getWindows();
                if (windows != null) {
                    for (android.view.accessibility.AccessibilityWindowInfo window : windows) {
                        AccessibilityNodeInfo root = window.getRoot();
                        if (root != null && root.getPackageName() != null && cacheTargetPackage.equals(root.getPackageName().toString())) {
                            targetWindowFound = true;
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

        // Cancel any active polling sequences
        handler.removeCallbacksAndMessages(null);

        // Immediate check (0ms)
        if (clickIfMatched(cacheTargetPackage)) {
            return;
        }

        // If not matched immediately, start the high-frequency polling loop
        handler.post(new PollingRunnable(cacheTargetPackage, System.currentTimeMillis()));
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
        if (!cacheEnabled) {
            return false;
        }

        java.util.List<AccessibilityNodeInfo> allButtons = new java.util.ArrayList<>();
        java.util.List<AccessibilityNodeInfo> rootsToCheck = new java.util.ArrayList<>();

        AccessibilityNodeInfo activeRoot = getRootInActiveWindow();
        if (activeRoot != null && activeRoot.getPackageName() != null && packageName.equals(activeRoot.getPackageName().toString())) {
            rootsToCheck.add(activeRoot);
        } else {
            java.util.List<android.view.accessibility.AccessibilityWindowInfo> windows = getWindows();
            if (windows != null) {
                for (android.view.accessibility.AccessibilityWindowInfo window : windows) {
                    AccessibilityNodeInfo root = window.getRoot();
                    if (root != null) rootsToCheck.add(root);
                }
            }
        }

        for (AccessibilityNodeInfo root : rootsToCheck) {
            if (root.getPackageName() != null && packageName.equals(root.getPackageName().toString())) {
                java.util.List<AccessibilityNodeInfo> matches = new java.util.ArrayList<>();
                collectMatches(root, cacheTargetText, matches);

                // Get the actual clickable buttons from the matches
                for (AccessibilityNodeInfo match : matches) {
                    AccessibilityNodeInfo clickable = nearestClickable(match);
                    if (clickable != null && clickable.isClickable() && isExactMatch(match, cacheTargetText)) {
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

        android.os.Handler uiHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        AccessibilityNodeInfo targetNode = null;

        if ("custom".equals(cacheAppMode)) {
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

                java.util.regex.Matcher matcher = DIST_PATTERN.matcher(cardText.toString());
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
                    java.util.regex.Matcher baseMatcher = BASE_PRICE_PATTERN.matcher(cardText.toString());
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
                        java.util.regex.Matcher bonusMatcher = BONUS_PRICE_PATTERN.matcher(nearPrice);
                        if (bonusMatcher.find()) {
                            try {
                                bonusPrice = Float.parseFloat(bonusMatcher.group(1));
                            } catch (Exception ignored) {}
                        }
                    }

                    float totalPrice = basePrice + bonusPrice;
                    float totalDistance = pickupKm + dropKm;
                    float pricePerKm = (totalDistance > 0 && totalPrice > 0) ? (totalPrice / totalDistance) : 0f;

                    boolean hasDistFilters = cacheFilterMaxPickupActive || cacheFilterDropActive;
                    boolean hasPriceFilters = cacheFilterPriceKmActive || cacheFilterTotalPriceActive;

                    boolean distPass = true;
                    if (hasDistFilters) {
                        if (cacheFilterMaxPickupActive && pickupKm > cacheMaxPickup) distPass = false;
                        if (cacheFilterDropActive && (dropKm < cacheMinDrop || dropKm > cacheMaxDrop)) distPass = false;
                    }

                    boolean pricePass = false;
                    if (hasPriceFilters) {
                        if (cacheTogglePriceAnd) {
                            pricePass = (!cacheFilterPriceKmActive || pricePerKm >= cacheMinPricePerKm) &&
                                        (!cacheFilterTotalPriceActive || totalPrice >= cacheMinPrice);
                        } else {
                            boolean p1 = cacheFilterPriceKmActive && (pricePerKm >= cacheMinPricePerKm);
                            boolean p2 = cacheFilterTotalPriceActive && (totalPrice >= cacheMinPrice);
                            pricePass = p1 || p2;
                        }
                    }

                    boolean finalAccept = true;
                    if (hasDistFilters && hasPriceFilters) {
                        finalAccept = cacheToggleDistPriceAnd ? (distPass && pricePass) : (distPass || pricePass);
                    } else if (hasDistFilters) {
                        finalAccept = distPass;
                    } else if (hasPriceFilters) {
                        finalAccept = pricePass;
                    } else if (!hasDistFilters && !hasPriceFilters) {
                        // Fallback to old behavior if no toggles are active
                        finalAccept = (pickupKm >= cacheMinPickup && pickupKm <= cacheMaxPickup && dropKm >= cacheMinDrop && dropKm <= cacheMaxDrop);
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

        if ("blocked".equalsIgnoreCase(cacheUserStatus)) {
            DebugLogManager.log(this, "BLOCKED", "Account blocked by administrator.");
            uiHandler.post(() -> android.widget.Toast.makeText(this, "Click blocked: Account blocked by administrator", android.widget.Toast.LENGTH_LONG).show());
            return false;
        }

        boolean isSubscribed = cacheSubExpires > (System.currentTimeMillis() / 1000L);
        if (!isSubscribed && cacheFreeClicks <= 0) {
            DebugLogManager.log(this, "BLOCKED", "Subscription expired and no free clicks left.");
            uiHandler.post(() -> android.widget.Toast.makeText(this, "Click blocked: Subscription required", android.widget.Toast.LENGTH_LONG).show());
            return false;
        }

        SharedPreferences prefs = getSharedPreferences(AcceptPrefs.NAME, MODE_PRIVATE);
        if (!isSubscribed && cacheFreeClicks > 0) {
            // Decrement local free clicks
            int newClicks = cacheFreeClicks - 1;
            cacheFreeClicks = newClicks;
            prefs.edit().putInt(AcceptPrefs.KEY_FREE_CLICKS, newClicks).apply();

            // Notify server in the background
            if (!cacheLoggedInUser.isEmpty()) {
                TursoHelper.useFreeClick(this, cacheLoggedInUser, new TursoHelper.Callback() {
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

            boolean dispatched = dispatchGesture(clickBuilder.build(), new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                    prefs.edit().putLong(AcceptPrefs.KEY_LAST_CLICK_MS, System.currentTimeMillis()).apply();
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    // Fallback immediately if gesture is cancelled by system
                    uiHandler.post(() -> {
                        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                            prefs.edit().putLong(AcceptPrefs.KEY_LAST_CLICK_MS, System.currentTimeMillis()).apply();
                        }
                    });
                }
            }, null);

            // Staggered fallback: If physical gesture failed to start, click immediately.
            // Otherwise, wait 250ms (after the gesture finishes) to try the programmatic click as a backup.
            if (!dispatched) {
                if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    prefs.edit().putLong(AcceptPrefs.KEY_LAST_CLICK_MS, System.currentTimeMillis()).apply();
                    clicked = true;
                }
            } else {
                clicked = true;
                uiHandler.postDelayed(() -> {
                    if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        prefs.edit().putLong(AcceptPrefs.KEY_LAST_CLICK_MS, System.currentTimeMillis()).apply();
                    }
                }, 250);
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
