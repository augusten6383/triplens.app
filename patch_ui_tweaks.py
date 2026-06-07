import sys

filepath = r"D:\Desktop\Augusten\triplencse\triplencse\app\src\main\java\com\triplencse\acceptassist\MainActivity.java"
with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Hide user email from header
target_header = """        TextView userText = new TextView(this);
        userText.setTextColor(COLOR_TEXT_SECONDARY);
        userText.setTextSize(13);
        userText.setText(loggedInUser);
        nameSec.addView(userText);"""
replacement_header = """        // Hidden user email as per request
        // TextView userText = new TextView(this);
        // userText.setTextColor(COLOR_TEXT_SECONDARY);
        // userText.setTextSize(13);
        // userText.setText(loggedInUser);
        // nameSec.addView(userText);"""
content = content.replace(target_header, replacement_header)


# 2. Add 'Go Back' option in payment screen
target_sub_view = """        // Demo Activation Button
        Button demoBtn = new Button(this);
        demoBtn.setText("Demo: Activate Trial (Add 1 Day Premium)");"""

replacement_sub_view = """        long subExpires = prefs.getLong(AcceptPrefs.KEY_SUB_EXPIRES, 0L);
        boolean isSubscribed = subExpires > (System.currentTimeMillis() / 1000L);
        if (freeClicks > 0 || isSubscribed) {
            Button goBackBtn = new Button(this);
            goBackBtn.setText("Go Back to Dashboard");
            goBackBtn.setBackground(gradientRect(Color.parseColor("#3B82F6"), Color.parseColor("#06B6D4"), 10));
            goBackBtn.setTextColor(Color.WHITE);
            goBackBtn.setAllCaps(false);
            goBackBtn.setTextSize(16);
            goBackBtn.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            goBackBtn.setPadding(0, dp(14), 0, dp(14));
            goBackBtn.setOnClickListener(v -> setContentView(buildDashboardView()));
            root.addView(goBackBtn, matchWrapWithTop(28));
        }

        // Demo Activation Button
        Button demoBtn = new Button(this);
        demoBtn.setText("Demo: Activate Trial (Add 1 Day Premium)");"""
content = content.replace(target_sub_view, replacement_sub_view)

# Also fix the top margin of demoBtn to 14 so it looks okay with or without the go back button
content = content.replace("root.addView(demoBtn, matchWrapWithTop(28));", "root.addView(demoBtn, matchWrapWithTop(14));")


# 3. Update Checkbox methods
target_checkbox_def = """    private View buildCheckboxRow(String label, String prefKey, boolean defVal) {"""
replacement_checkbox_def = """    private View buildCheckboxRow(String label, String prefKey, boolean defVal, boolean isEnabled) {"""
content = content.replace(target_checkbox_def, replacement_checkbox_def)

target_checkbox_toggle = """        android.widget.Switch toggle = new android.widget.Switch(this);
        toggle.setChecked(prefs.getBoolean(prefKey, defVal));
        toggle.setOnCheckedChangeListener((btn, isChecked) -> prefs.edit().putBoolean(prefKey, isChecked).apply());
        row.addView(toggle);

        return row;"""
replacement_checkbox_toggle = """        android.widget.Switch toggle = new android.widget.Switch(this);
        toggle.setChecked(isEnabled && prefs.getBoolean(prefKey, defVal));
        toggle.setEnabled(isEnabled);
        toggle.setOnCheckedChangeListener((btn, isChecked) -> prefs.edit().putBoolean(prefKey, isChecked).apply());
        row.addView(toggle);
        
        if (!isEnabled) {
            txt.setTextColor(COLOR_TEXT_SECONDARY);
            toggle.setAlpha(0.5f);
        }

        return row;"""
content = content.replace(target_checkbox_toggle, replacement_checkbox_toggle)

# 4. Update the calls in buildTargetedAppsCard
target_apps_calls = """        card.addView(buildCheckboxRow("Rapido Rider", "target_rapido", true));
        card.addView(buildCheckboxRow("Uber Driver", "target_uber", true));
        card.addView(buildCheckboxRow("Ola Driver", "target_ola", true));"""

replacement_apps_calls = """        card.addView(buildCheckboxRow("Rapido Rider", "target_rapido", true, true));
        card.addView(buildCheckboxRow("Uber Driver (Coming Soon)", "target_uber", false, false));
        card.addView(buildCheckboxRow("Ola Driver (Coming Soon)", "target_ola", false, false));"""
content = content.replace(target_apps_calls, replacement_apps_calls)

with open(filepath, 'w', encoding='utf-8') as f:
    f.write(content)
