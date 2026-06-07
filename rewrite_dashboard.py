import sys
import re

filepath = r"D:\Desktop\Augusten\triplencse\triplencse\app\src\main\java\com\triplencse\acceptassist\MainActivity.java"
with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Update Design Tokens
tokens_old = """    // Design Tokens - Premium Dark Theme
    private static final int COLOR_BG = Color.rgb(15, 23, 30); // Deep background
    private static final int COLOR_CARD = Color.rgb(26, 36, 47); // Card surface
    private static final int COLOR_INPUT_BG = Color.rgb(33, 45, 59); // Input field background
    private static final int COLOR_BORDER = Color.rgb(52, 70, 92); // Input border
    private static final int COLOR_ACCENT = Color.rgb(16, 185, 129); // Vibrant emerald green
    private static final int COLOR_TEXT_PRIMARY = Color.rgb(243, 244, 246); // Title / primary text
    private static final int COLOR_TEXT_SECONDARY = Color.rgb(156, 163, 175); // Subtitles / secondary text
    private static final int COLOR_DANGER = Color.rgb(239, 68, 68); // Soft red
    private static final int COLOR_WARNING = Color.rgb(245, 158, 11); // Soft orange"""

tokens_new = """    // Design Tokens - Premium Dark Theme
    private static final int COLOR_BG = Color.parseColor("#0F172A"); // Deep slate
    private static final int COLOR_CARD = Color.parseColor("#1E293B"); // Elevated card
    private static final int COLOR_INPUT_BG = Color.parseColor("#334155"); // Input background
    private static final int COLOR_BORDER = Color.parseColor("#475569"); // Subtle borders
    private static final int COLOR_ACCENT = Color.parseColor("#0EA5E9"); // Vibrant Cyan
    private static final int COLOR_TEXT_PRIMARY = Color.parseColor("#F8FAFC"); // Pure white
    private static final int COLOR_TEXT_SECONDARY = Color.parseColor("#94A3B8"); // Muted slate
    private static final int COLOR_DANGER = Color.parseColor("#EF4444"); // Glowing Red
    private static final int COLOR_WARNING = Color.parseColor("#F59E0B"); // Gold/Amber"""

content = content.replace(tokens_old, tokens_new)

# Add gradient rect helper right before buildDashboardView
helper_str = """
    private android.graphics.drawable.GradientDrawable gradientRect(int startColor, int endColor, float radiusDp) {
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            new int[]{startColor, endColor});
        shape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        shape.setCornerRadius(dp((int) radiusDp));
        return shape;
    }

    private View buildDashboardView() {"""
content = content.replace("    private View buildDashboardView() {", helper_str)

# Now we slice the content from buildDashboardView to saveSettings
parts = content.split("    private View buildDashboardView() {")
if len(parts) == 2:
    part1 = parts[0]
    subparts = parts[1].split("    private void saveSettings() {")
    part3 = subparts[1]
    
    new_dashboard = """    private View buildDashboardView() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(24), dp(16), dp(32));
        root.setBackgroundColor(COLOR_BG);
        scrollView.addView(root);

        String loggedInUser = prefs.getString(AcceptPrefs.KEY_LOGGED_IN_USER, "User");
        long subExpires = prefs.getLong(AcceptPrefs.KEY_SUB_EXPIRES, 0L);
        int freeClicks = prefs.getInt(AcceptPrefs.KEY_FREE_CLICKS, 0);

        // 1. Sleek Header
        root.addView(buildHeaderView(loggedInUser, subExpires, freeClicks));

        // 2. Action Buttons (Massive Gradients)
        root.addView(buildActionButtonsRow(), matchWrapWithTop(20));

        // 3. App Mode Selection (Tabs)
        root.addView(buildAppModeSelectionCard(), matchWrapWithTop(24));

        // 4. Distance / Custom Settings based on Mode
        String currentMode = prefs.getString(AcceptPrefs.KEY_APP_MODE, "rapido");
        if ("custom".equals(currentMode)) {
            root.addView(buildCustomAppSettingsCard(), matchWrapWithTop(16));
        } else {
            root.addView(buildTargetedAppsCard(), matchWrapWithTop(16));
            root.addView(buildDistanceSettingsCard(), matchWrapWithTop(16));
        }

        // 5. Save Button
        Button saveBtn = new Button(this);
        saveBtn.setText("Save Configuration");
        saveBtn.setBackground(gradientRect(Color.parseColor("#3B82F6"), Color.parseColor("#06B6D4"), 12));
        saveBtn.setTextColor(Color.WHITE);
        saveBtn.setAllCaps(false);
        saveBtn.setTextSize(16);
        saveBtn.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        saveBtn.setPadding(0, dp(14), 0, dp(14));
        saveBtn.setOnClickListener(v -> saveSettings());
        root.addView(saveBtn, matchWrapWithTop(24));

        // 6. Log Out Row
        root.addView(buildLogOutRow(), matchWrapWithTop(24));

        return scrollView;
    }

    private View buildHeaderView(String loggedInUser, long subExpires, int freeClicks) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(8));

        // Left Section: App Logo + Name
        LinearLayout leftSec = new LinearLayout(this);
        leftSec.setOrientation(LinearLayout.HORIZONTAL);
        leftSec.setGravity(Gravity.CENTER_VERTICAL);
        
        // Glowing app icon
        View appIcon = systemIcon(android.R.drawable.ic_menu_compass, Color.TRANSPARENT, 48, 8);
        appIcon.setBackground(gradientRect(Color.parseColor("#0EA5E9"), Color.parseColor("#3B82F6"), 24));
        leftSec.addView(appIcon);

        LinearLayout nameSec = new LinearLayout(this);
        nameSec.setOrientation(LinearLayout.VERTICAL);
        nameSec.setPadding(dp(12), 0, 0, 0);

        TextView appTitle = new TextView(this);
        appTitle.setText("Triplens");
        appTitle.setTextColor(Color.WHITE);
        appTitle.setTextSize(24);
        appTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        nameSec.addView(appTitle);

        TextView userText = new TextView(this);
        userText.setTextColor(COLOR_TEXT_SECONDARY);
        userText.setTextSize(13);
        userText.setText(loggedInUser);
        nameSec.addView(userText);

        leftSec.addView(nameSec);
        
        LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        header.addView(leftSec, leftParams);

        // Right Section: Subscription Status
        LinearLayout subCard = new LinearLayout(this);
        subCard.setOrientation(LinearLayout.VERTICAL);
        subCard.setGravity(Gravity.CENTER);
        subCard.setBackground(roundedRectWithBorder(COLOR_CARD, 12, COLOR_BORDER, 1));
        subCard.setPadding(dp(16), dp(8), dp(16), dp(8));

        boolean isSubscribed = subExpires > (System.currentTimeMillis() / 1000L);
        TextView subStatus = new TextView(this);
        subStatus.setTextSize(14);
        subStatus.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        
        TextView subDesc = new TextView(this);
        subDesc.setTextColor(COLOR_TEXT_SECONDARY);
        subDesc.setTextSize(11);

        if (isSubscribed) {
            subStatus.setText("PREMIUM");
            subStatus.setTextColor(Color.parseColor("#FBBF24")); // Gold
            subDesc.setText("Active");
        } else if (freeClicks > 0) {
            subStatus.setText("TRIAL");
            subStatus.setTextColor(COLOR_ACCENT);
            subDesc.setText(freeClicks + " Click" + (freeClicks > 1 ? "s" : ""));
        } else {
            subStatus.setText("EXPIRED");
            subStatus.setTextColor(COLOR_DANGER);
            subDesc.setText("Upgrade");
        }
        subCard.addView(subStatus);
        subCard.addView(subDesc);

        subCard.setOnClickListener(v -> {
            setContentView(buildSubscriptionView());
        });

        header.addView(subCard);

        return header;
    }

    private View buildActionButtonsRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        boolean isEnabled = prefs.getBoolean(AcceptPrefs.KEY_ENABLED, false);

        // Left button: Start/Stop Auto-Clicker
        LinearLayout toggleBtn = new LinearLayout(this);
        toggleBtn.setOrientation(LinearLayout.VERTICAL);
        toggleBtn.setGravity(Gravity.CENTER);
        
        if (isEnabled) {
            toggleBtn.setBackground(gradientRect(Color.parseColor("#F43F5E"), Color.parseColor("#BE123C"), 16));
        } else {
            toggleBtn.setBackground(gradientRect(Color.parseColor("#10B981"), Color.parseColor("#047857"), 16));
        }
        toggleBtn.setPadding(dp(16), dp(20), dp(16), dp(20));

        View playIcon = systemIcon(isEnabled ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play, Color.TRANSPARENT, 36, 0);
        toggleBtn.addView(playIcon);

        TextView toggleText = new TextView(this);
        toggleText.setText(isEnabled ? "STOP" : "START");
        toggleText.setTextColor(Color.WHITE);
        toggleText.setTextSize(16);
        toggleText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        toggleText.setPadding(0, dp(8), 0, 0);
        toggleBtn.addView(toggleText);

        toggleBtn.setOnClickListener(v -> {
            boolean next = !isEnabled;
            prefs.edit().putBoolean(AcceptPrefs.KEY_ENABLED, next).apply();
            Toast.makeText(this, next ? "Auto-clicker is now active" : "Auto-clicker paused", Toast.LENGTH_SHORT).show();
            setContentView(buildDashboardView());
        });

        LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        leftParams.rightMargin = dp(8);
        row.addView(toggleBtn, leftParams);

        // Right button: Open Accessibility
        LinearLayout openBtn = new LinearLayout(this);
        openBtn.setOrientation(LinearLayout.VERTICAL);
        openBtn.setGravity(Gravity.CENTER);
        openBtn.setBackground(gradientRect(Color.parseColor("#1E293B"), Color.parseColor("#0F172A"), 16));
        android.graphics.drawable.GradientDrawable border = roundedRectWithBorder(Color.TRANSPARENT, 16, COLOR_BORDER, 1);
        openBtn.setForeground(border); // requires API 23+, we will use setBackground with border, wait, let's just use background
        openBtn.setBackground(roundedRectWithBorder(COLOR_CARD, 16, COLOR_BORDER, 1));
        openBtn.setPadding(dp(16), dp(20), dp(16), dp(20));
        openBtn.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        View gearIcon = systemIcon(android.R.drawable.ic_menu_manage, Color.TRANSPARENT, 36, 0);
        if (gearIcon instanceof android.widget.ImageView) {
            ((android.widget.ImageView) gearIcon).setColorFilter(COLOR_ACCENT);
        }
        openBtn.addView(gearIcon);

        TextView openText = new TextView(this);
        openText.setText("SETTINGS");
        openText.setTextColor(Color.WHITE);
        openText.setTextSize(16);
        openText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        openText.setPadding(0, dp(8), 0, 0);
        openBtn.addView(openText);

        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        rightParams.leftMargin = dp(8);
        row.addView(openBtn, rightParams);

        return row;
    }

    private View buildAppModeSelectionCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundedRect(COLOR_CARD, 16));
        card.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView title = new TextView(this);
        title.setText("App Mode");
        title.setTextColor(COLOR_TEXT_SECONDARY);
        title.setTextSize(13);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, dp(12));
        card.addView(title);

        // Sleek Tab Bar
        LinearLayout tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setBackground(roundedRect(COLOR_INPUT_BG, 12));
        tabBar.setPadding(dp(4), dp(4), dp(4), dp(4));

        String currentMode = prefs.getString(AcceptPrefs.KEY_APP_MODE, "rapido");
        boolean isRapidoSelected = "rapido".equals(currentMode);

        // Tab 1
        TextView rapidoTab = new TextView(this);
        rapidoTab.setText("Default (Rapido)");
        rapidoTab.setGravity(Gravity.CENTER);
        rapidoTab.setTextSize(14);
        rapidoTab.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        rapidoTab.setPadding(0, dp(12), 0, dp(12));
        rapidoTab.setTextColor(isRapidoSelected ? Color.WHITE : COLOR_TEXT_SECONDARY);
        if (isRapidoSelected) rapidoTab.setBackground(roundedRect(COLOR_CARD, 10));
        rapidoTab.setOnClickListener(v -> {
            prefs.edit().putString(AcceptPrefs.KEY_APP_MODE, "rapido").apply();
            setContentView(buildDashboardView());
        });
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        tabBar.addView(rapidoTab, p1);

        // Tab 2
        TextView customTab = new TextView(this);
        customTab.setText("Custom App");
        customTab.setGravity(Gravity.CENTER);
        customTab.setTextSize(14);
        customTab.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        customTab.setPadding(0, dp(12), 0, dp(12));
        customTab.setTextColor(!isRapidoSelected ? Color.WHITE : COLOR_TEXT_SECONDARY);
        if (!isRapidoSelected) customTab.setBackground(roundedRect(COLOR_CARD, 10));
        customTab.setOnClickListener(v -> {
            prefs.edit().putString(AcceptPrefs.KEY_APP_MODE, "custom").apply();
            setContentView(buildDashboardView());
        });
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        tabBar.addView(customTab, p2);

        card.addView(tabBar);
        return card;
    }

    private View buildSettingRow(String title, String subtitle, EditText inputField) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));

        LinearLayout textSec = new LinearLayout(this);
        textSec.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(COLOR_TEXT_PRIMARY);
        titleView.setTextSize(15);
        titleView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        textSec.addView(titleView);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView subView = new TextView(this);
            subView.setText(subtitle);
            subView.setTextColor(COLOR_TEXT_SECONDARY);
            subView.setTextSize(12);
            subView.setPadding(0, dp(2), 0, 0);
            textSec.addView(subView);
        }

        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        textParams.rightMargin = dp(16);
        row.addView(textSec, textParams);

        inputField.setGravity(Gravity.CENTER);
        inputField.setTextSize(16);
        inputField.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        inputField.setTextColor(COLOR_ACCENT);
        inputField.setPadding(dp(16), dp(10), dp(16), dp(10));
        inputField.setBackground(roundedRectWithBorder(COLOR_INPUT_BG, 8, COLOR_BORDER, 1));
        
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(dp(90), LinearLayout.LayoutParams.WRAP_CONTENT);
        row.addView(inputField, inputParams);

        return row;
    }

    private View buildDistanceSettingsCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundedRect(COLOR_CARD, 16));
        card.setPadding(dp(20), dp(20), dp(20), dp(20));

        TextView title = new TextView(this);
        title.setText("Distance Limits (km)");
        title.setTextColor(COLOR_TEXT_SECONDARY);
        title.setTextSize(13);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, dp(16));
        card.addView(title);

        minPickupInput = new EditText(this);
        minPickupInput.setText(String.valueOf(prefs.getFloat(AcceptPrefs.KEY_MIN_PICKUP, 0.0f)));
        minPickupInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        card.addView(buildSettingRow("Min Pickup", "", minPickupInput));

        maxPickupInput = new EditText(this);
        maxPickupInput.setText(String.valueOf(prefs.getFloat(AcceptPrefs.KEY_MAX_PICKUP, 5.0f)));
        maxPickupInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        card.addView(buildSettingRow("Max Pickup", "", maxPickupInput));

        minDropInput = new EditText(this);
        minDropInput.setText(String.valueOf(prefs.getFloat(AcceptPrefs.KEY_MIN_DROP, 0.0f)));
        minDropInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        card.addView(buildSettingRow("Min Drop", "", minDropInput));

        maxDropInput = new EditText(this);
        maxDropInput.setText(String.valueOf(prefs.getFloat(AcceptPrefs.KEY_MAX_DROP, 15.0f)));
        maxDropInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        card.addView(buildSettingRow("Max Drop", "", maxDropInput));

        return card;
    }

    private View buildCustomAppSettingsCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundedRect(COLOR_CARD, 16));
        card.setPadding(dp(20), dp(20), dp(20), dp(20));

        TextView title = new TextView(this);
        title.setText("Custom App Targeting");
        title.setTextColor(COLOR_TEXT_SECONDARY);
        title.setTextSize(13);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, dp(16));
        card.addView(title);

        customPackageInput = new EditText(this);
        customPackageInput.setText(prefs.getString(AcceptPrefs.KEY_CUSTOM_PACKAGE, ""));
        customPackageInput.setHint("com.example");
        customPackageInput.setInputType(InputType.TYPE_CLASS_TEXT);
        card.addView(buildSettingRow("Package Name", "e.g. com.uber.driver", customPackageInput));

        customTargetTextInput = new EditText(this);
        customTargetTextInput.setText(prefs.getString(AcceptPrefs.KEY_CUSTOM_TARGET_TEXT, "Accept"));
        customTargetTextInput.setHint("Accept");
        customTargetTextInput.setInputType(InputType.TYPE_CLASS_TEXT);
        card.addView(buildSettingRow("Target Text", "Comma-separated", customTargetTextInput));

        return card;
    }

    private View buildTargetedAppsCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundedRect(COLOR_CARD, 16));
        card.setPadding(dp(20), dp(20), dp(20), dp(20));

        TextView title = new TextView(this);
        title.setText("Targeted Apps");
        title.setTextColor(COLOR_TEXT_SECONDARY);
        title.setTextSize(13);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, dp(12));
        card.addView(title);

        card.addView(buildCheckboxRow("Rapido Rider", "target_rapido", true));
        card.addView(buildCheckboxRow("Uber Driver", "target_uber", true));
        card.addView(buildCheckboxRow("Ola Driver", "target_ola", true));

        return card;
    }

    private View buildCheckboxRow(String label, String prefKey, boolean defVal) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));

        TextView txt = new TextView(this);
        txt.setText(label);
        txt.setTextColor(COLOR_TEXT_PRIMARY);
        txt.setTextSize(15);
        txt.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        row.addView(txt, params);

        android.widget.Switch toggle = new android.widget.Switch(this);
        toggle.setChecked(prefs.getBoolean(prefKey, defVal));
        toggle.setOnCheckedChangeListener((btn, isChecked) -> prefs.edit().putBoolean(prefKey, isChecked).apply());
        row.addView(toggle);

        return row;
    }

    private View buildLogOutRow() {
        TextView logoutBtn = new TextView(this);
        logoutBtn.setText("Log Out of Account");
        logoutBtn.setTextColor(COLOR_TEXT_SECONDARY);
        logoutBtn.setTextSize(14);
        logoutBtn.setGravity(Gravity.CENTER);
        logoutBtn.setPadding(0, dp(16), 0, dp(16));
        logoutBtn.setOnClickListener(v -> {
            if (mAuth != null) mAuth.signOut();
            if (mGoogleSignInClient != null) mGoogleSignInClient.signOut();
            prefs.edit().putString(AcceptPrefs.KEY_LOGGED_IN_USER, "").apply();
            navigateToScreen();
        });
        return logoutBtn;
    }

    private void saveSettings() {"""
    
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(part1 + new_dashboard + part3)
    print("Dashboard UI successfully rewritten!")
else:
    print("Error: Could not find buildDashboardView in MainActivity.java")
