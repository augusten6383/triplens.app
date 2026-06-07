import sys

filepath = r"D:\Desktop\Augusten\triplencse\triplencse\app\src\main\java\com\triplencse\acceptassist\MainActivity.java"
with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Update proceedNavigation
target = """            setContentView(buildLoadingView("Checking subscription status..."));
            TursoHelper.checkUserSubscription(this, loggedInUser, new TursoHelper.Callback() {
                @Override
                public void onSuccess(org.json.JSONArray rows) {
                    if (rows != null && rows.length() > 0) {
                        try {
                            org.json.JSONArray firstRow = rows.getJSONArray(0);
                            String status = TursoHelper.getValueAsString(firstRow.getJSONObject(0));
                            String freeClicksStr = TursoHelper.getValueAsString(firstRow.getJSONObject(1));
                            String subExpiresStr = TursoHelper.getValueAsString(firstRow.getJSONObject(2));"""

replacement = """            String deviceId = android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);

            setContentView(buildLoadingView("Verifying device and subscription..."));
            TursoHelper.verifyDeviceAndSubscription(this, loggedInUser, deviceId, new TursoHelper.Callback() {
                @Override
                public void onSuccess(org.json.JSONArray rows) {
                    if (rows != null && rows.length() > 0) {
                        try {
                            org.json.JSONArray firstRow = rows.getJSONArray(0);
                            String status = TursoHelper.getValueAsString(firstRow.getJSONObject(1));
                            String freeClicksStr = TursoHelper.getValueAsString(firstRow.getJSONObject(2));
                            String subExpiresStr = TursoHelper.getValueAsString(firstRow.getJSONObject(3));"""

content = content.replace(target, replacement)

# 2. Update error handling
target_error = """                @Override
                public void onError(String message) {
                    setContentView(buildErrorView("Failed to retrieve subscription:\n" + message));
                }"""
replacement_error = """                @Override
                public void onError(String message) {
                    if ("DEVICE_USED_BY_OTHER_ACCOUNT".equals(message)) {
                        setContentView(buildDeviceErrorView("This device is already registered to another account. One account per device is allowed."));
                    } else if ("ACCOUNT_USED_ON_OTHER_DEVICE".equals(message)) {
                        setContentView(buildDeviceErrorView("This account is already registered to a different device. Multiple devices are not allowed."));
                    } else {
                        setContentView(buildErrorView("Failed to retrieve subscription:\n" + message));
                    }
                }"""
content = content.replace(target_error, replacement_error)

# 3. Add buildDeviceErrorView
method_to_add = """    private View buildDeviceErrorView(String message) {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(40), dp(24), dp(40));
        root.setBackgroundColor(COLOR_BG);
        root.setGravity(Gravity.CENTER_VERTICAL);
        scrollView.addView(root);

        TextView icon = new TextView(this);
        icon.setText("🔒");
        icon.setTextSize(48);
        icon.setGravity(Gravity.CENTER);
        root.addView(icon, matchWrap());

        TextView title = new TextView(this);
        title.setText("Access Denied");
        title.setTextColor(Color.parseColor("#EF4444"));
        title.setTextSize(24);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(16), 0, dp(8));
        root.addView(title, matchWrap());

        TextView msgText = new TextView(this);
        msgText.setText(message);
        msgText.setTextColor(COLOR_TEXT_SECONDARY);
        msgText.setTextSize(15);
        msgText.setGravity(Gravity.CENTER);
        msgText.setPadding(0, 0, 0, dp(32));
        root.addView(msgText, matchWrap());

        Button logOutBtn = primaryButton("Log Out");
        logOutBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#374151")));
        logOutBtn.setOnClickListener(v -> {
            if (mAuth != null) mAuth.signOut();
            if (mGoogleSignInClient != null) mGoogleSignInClient.signOut();
            prefs.edit().putString(AcceptPrefs.KEY_LOGGED_IN_USER, "").apply();
            setContentView(buildLoginView());
        });
        root.addView(logOutBtn, matchWrapWithTop(16));

        return scrollView;
    }
"""

content = content.replace("private View buildErrorView(", method_to_add + "\n    private View buildErrorView(")

with open(filepath, 'w', encoding='utf-8') as f:
    f.write(content)
