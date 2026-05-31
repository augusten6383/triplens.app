package com.triplencse.acceptassist;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.RadioGroup;
import android.widget.RadioButton;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.content.pm.PackageManager;

public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private EditText minPickupInput;
    private EditText maxPickupInput;
    private EditText minDropInput;
    private EditText maxDropInput;
    private TextView serviceStatus;
    private RadioGroup appModeGroup;
    private RadioButton radioRapido;
    private RadioButton radioCustom;
    private EditText customPackageInput;
    private EditText customTargetTextInput;
    private LinearLayout distanceFiltersContainer;
    private LinearLayout customAppContainer;

    // Design Tokens - Premium Dark Theme
    private static final int COLOR_BG = Color.rgb(15, 23, 30); // Deep background
    private static final int COLOR_CARD = Color.rgb(26, 36, 47); // Card surface
    private static final int COLOR_INPUT_BG = Color.rgb(33, 45, 59); // Input field background
    private static final int COLOR_BORDER = Color.rgb(52, 70, 92); // Input border
    private static final int COLOR_ACCENT = Color.rgb(16, 185, 129); // Vibrant emerald green
    private static final int COLOR_TEXT_PRIMARY = Color.rgb(243, 244, 246); // Title / primary text
    private static final int COLOR_TEXT_SECONDARY = Color.rgb(156, 163, 175); // Subtitles / secondary text
    private static final int COLOR_DANGER = Color.rgb(239, 68, 68); // Soft red
    private static final int COLOR_WARNING = Color.rgb(245, 158, 11); // Soft orange

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(AcceptPrefs.NAME, MODE_PRIVATE);
        AcceptPrefs.ensureDefaults(prefs);
        navigateToScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        String loggedInUser = prefs.getString(AcceptPrefs.KEY_LOGGED_IN_USER, "");
        if (!loggedInUser.isEmpty()) {
            proceedNavigation();
        }
    }

    private void checkPermissionsOnLaunch() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                return;
            }
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                return;
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
                return;
            }
        }
    }

    private void navigateToScreen() {
        String url = prefs.getString(AcceptPrefs.KEY_TURSO_URL, "");
        String token = prefs.getString(AcceptPrefs.KEY_TURSO_TOKEN, "");
        if (url.isEmpty() || token.isEmpty()) {
            setContentView(buildDbConfigView());
            return;
        }

        boolean dbInitialized = prefs.getBoolean("db_initialized", false);
        if (!dbInitialized) {
            setContentView(buildLoadingView("Connecting to database..."));
            TursoHelper.initDatabase(this, new TursoHelper.Callback() {
                @Override
                public void onSuccess(org.json.JSONArray rows) {
                    prefs.edit().putBoolean("db_initialized", true).apply();
                    proceedNavigation();
                }

                @Override
                public void onError(String message) {
                    setContentView(buildErrorView("Failed to connect to database:\n" + message));
                }
            });
        } else {
            proceedNavigation();
        }
    }

    private void proceedNavigation() {
        String loggedInUser = prefs.getString(AcceptPrefs.KEY_LOGGED_IN_USER, "");
        if (!loggedInUser.isEmpty()) {
            setContentView(buildLoadingView("Checking subscription status..."));
            TursoHelper.checkUserSubscription(this, loggedInUser, new TursoHelper.Callback() {
                @Override
                public void onSuccess(org.json.JSONArray rows) {
                    if (rows != null && rows.length() > 0) {
                        try {
                            org.json.JSONArray firstRow = rows.getJSONArray(0);
                            String status = TursoHelper.getValueAsString(firstRow.getJSONObject(0));
                            String freeClicksStr = TursoHelper.getValueAsString(firstRow.getJSONObject(1));
                            String subExpiresStr = TursoHelper.getValueAsString(firstRow.getJSONObject(2));

                            if (status == null) status = "active";
                            int freeClicks = 0;
                            try {
                                if (freeClicksStr != null) freeClicks = Integer.parseInt(freeClicksStr);
                            } catch (NumberFormatException ignored) {}
                            long subExpires = 0L;
                            try {
                                if (subExpiresStr != null) subExpires = Long.parseLong(subExpiresStr);
                            } catch (NumberFormatException ignored) {}

                            prefs.edit()
                                    .putString(AcceptPrefs.KEY_USER_STATUS, status)
                                    .putInt(AcceptPrefs.KEY_FREE_CLICKS, freeClicks)
                                    .putLong(AcceptPrefs.KEY_SUB_EXPIRES, subExpires)
                                    .apply();

                            routeBasedOnSubscription(status, freeClicks, subExpires);
                        } catch (Exception ex) {
                            useCachedSubscriptionAndRoute();
                        }
                    } else {
                        useCachedSubscriptionAndRoute();
                    }
                }

                @Override
                public void onError(String message) {
                    useCachedSubscriptionAndRoute();
                }
            });
        } else {
            setContentView(buildLoginView());
        }
    }

    private void useCachedSubscriptionAndRoute() {
        String status = prefs.getString(AcceptPrefs.KEY_USER_STATUS, "active");
        int freeClicks = prefs.getInt(AcceptPrefs.KEY_FREE_CLICKS, 1);
        long subExpires = prefs.getLong(AcceptPrefs.KEY_SUB_EXPIRES, 0L);
        routeBasedOnSubscription(status, freeClicks, subExpires);
    }

    private void routeBasedOnSubscription(String status, int freeClicks, long subExpires) {
        if ("blocked".equalsIgnoreCase(status)) {
            setContentView(buildBlockedView());
        } else if (subExpires > System.currentTimeMillis() / 1000L || freeClicks > 0) {
            setContentView(buildDashboardView());
            checkPermissionsOnLaunch();
            refreshStatus();
        } else {
            setContentView(buildSubscriptionView());
        }
    }

    // Dynamic UI Styling Helpers
    private android.graphics.drawable.GradientDrawable roundedRect(int color, float radiusDp) {
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        shape.setColor(color);
        shape.setCornerRadius(dp((int) radiusDp));
        return shape;
    }

    private android.graphics.drawable.GradientDrawable roundedRectWithBorder(int color, float radiusDp, int strokeColor, int strokeWidthDp) {
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        shape.setColor(color);
        shape.setCornerRadius(dp((int) radiusDp));
        shape.setStroke(dp(strokeWidthDp), strokeColor);
        return shape;
    }

    private View systemIcon(int drawableId, int bgColor, int sizeDp, int paddingDp) {
        android.widget.ImageView iv = new android.widget.ImageView(this);
        iv.setImageResource(drawableId);
        iv.setColorFilter(Color.WHITE);
        iv.setPadding(dp(paddingDp), dp(paddingDp), dp(paddingDp), dp(paddingDp));
        iv.setBackground(roundedRect(bgColor, sizeDp / 2f));
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp));
        iv.setLayoutParams(params);
        return iv;
    }

    private View headerIcon(int drawableId) {
        android.widget.ImageView iv = new android.widget.ImageView(this);
        iv.setImageResource(drawableId);
        iv.setColorFilter(COLOR_TEXT_PRIMARY);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(18), dp(18));
        iv.setLayoutParams(params);
        return iv;
    }

    private View radioRing(boolean selected) {
        View view = new View(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(16), dp(16));
        view.setLayoutParams(params);
        if (selected) {
            view.setBackground(roundedRectWithBorder(COLOR_INPUT_BG, 8, COLOR_ACCENT, 5));
        } else {
            view.setBackground(roundedRectWithBorder(COLOR_INPUT_BG, 8, COLOR_TEXT_SECONDARY, 1));
        }
        return view;
    }

    private Button primaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setTextColor(Color.WHITE);
        button.setTextSize(16);
        button.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        button.setBackground(roundedRect(COLOR_ACCENT, 10));
        button.setPadding(dp(16), dp(12), dp(16), dp(12));
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setTextColor(COLOR_TEXT_PRIMARY);
        button.setTextSize(14);
        button.setBackground(roundedRect(COLOR_INPUT_BG, 8));
        button.setPadding(dp(12), dp(8), dp(12), dp(8));
        return button;
    }

    private Button textLinkButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setTextColor(COLOR_ACCENT);
        button.setTextSize(14);
        button.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setPadding(dp(8), dp(8), dp(8), dp(8));
        return button;
    }

    private TextView label(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(COLOR_TEXT_PRIMARY);
        view.setTextSize(14);
        view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return view;
    }

    private EditText input(String value) {
        EditText editText = new EditText(this);
        editText.setText(value);
        editText.setTextSize(15);
        editText.setTextColor(COLOR_TEXT_PRIMARY);
        editText.setHintTextColor(COLOR_TEXT_SECONDARY);
        editText.setBackground(roundedRectWithBorder(COLOR_INPUT_BG, 8, COLOR_BORDER, 1));
        editText.setPadding(dp(14), dp(12), dp(14), dp(12));
        return editText;
    }

    private String formatExpiry(long timestampSec) {
        if (timestampSec <= 0) return "No active subscription";
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(timestampSec * 1000L));
    }

    // View builders
    private View buildLoadingView(String message) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        root.setBackgroundColor(COLOR_BG);

        TextView title = new TextView(this);
        title.setText("Triplens");
        title.setTextColor(COLOR_TEXT_PRIMARY);
        title.setTextSize(34);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView desc = new TextView(this);
        desc.setText(message);
        desc.setTextColor(COLOR_TEXT_SECONDARY);
        desc.setTextSize(16);
        desc.setGravity(Gravity.CENTER);
        desc.setPadding(0, dp(12), 0, dp(24));
        root.addView(desc, matchWrap());

        android.widget.ProgressBar progressBar = new android.widget.ProgressBar(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.CENTER;
        root.addView(progressBar, params);

        return root;
    }

    private View buildErrorView(String errorMessage) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        root.setBackgroundColor(COLOR_BG);

        TextView title = new TextView(this);
        title.setText("Connection Error");
        title.setTextColor(COLOR_DANGER);
        title.setTextSize(26);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView desc = new TextView(this);
        desc.setText(errorMessage);
        desc.setTextColor(COLOR_TEXT_SECONDARY);
        desc.setTextSize(15);
        desc.setGravity(Gravity.CENTER);
        desc.setPadding(0, dp(12), 0, dp(24));
        root.addView(desc, matchWrap());

        Button retryBtn = primaryButton("Retry Connection");
        retryBtn.setOnClickListener(v -> navigateToScreen());
        root.addView(retryBtn, matchWrapWithTop(12));

        return root;
    }

    private View buildDbConfigView() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(30), dp(20), dp(30));
        root.setBackgroundColor(COLOR_BG);
        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText("Database Configuration");
        title.setTextColor(COLOR_TEXT_PRIMARY);
        title.setTextSize(26);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(title, matchWrap());

        TextView desc = new TextView(this);
        desc.setText("Connect Triplens to your remote Turso / libSQL database.");
        desc.setTextColor(COLOR_TEXT_SECONDARY);
        desc.setTextSize(14);
        desc.setPadding(0, dp(6), 0, dp(24));
        root.addView(desc, matchWrap());

        root.addView(label("Database HTTP / libsql URL"), matchWrap());
        EditText urlInput = input(prefs.getString(AcceptPrefs.KEY_TURSO_URL, ""));
        urlInput.setHint("libsql://your-db-org.turso.io");
        root.addView(urlInput, matchWrapWithTop(6));

        root.addView(label("Bearer Access Token"), matchWrapWithTop(16));
        EditText tokenInput = input(prefs.getString(AcceptPrefs.KEY_TURSO_TOKEN, ""));
        tokenInput.setHint("ey...");
        tokenInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(tokenInput, matchWrapWithTop(6));

        Button connectBtn = primaryButton("Save & Initialize Database");
        connectBtn.setOnClickListener(v -> {
            String url = urlInput.getText().toString().trim();
            String token = tokenInput.getText().toString().trim();
            if (url.isEmpty() || token.isEmpty()) {
                Toast.makeText(this, "Please enter both URL and Token", Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit()
                    .putString(AcceptPrefs.KEY_TURSO_URL, url)
                    .putString(AcceptPrefs.KEY_TURSO_TOKEN, token)
                    .apply();

            Toast.makeText(this, "Connecting to Turso...", Toast.LENGTH_SHORT).show();
            TursoHelper.initDatabase(this, new TursoHelper.Callback() {
                @Override
                public void onSuccess(org.json.JSONArray rows) {
                    prefs.edit().putBoolean("db_initialized", true).apply();
                    Toast.makeText(MainActivity.this, "Database Initialized Successfully!", Toast.LENGTH_LONG).show();
                    navigateToScreen();
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(MainActivity.this, "Connection failed: " + message, Toast.LENGTH_LONG).show();
                }
            });
        });
        root.addView(connectBtn, matchWrapWithTop(24));

        return scrollView;
    }

    private View buildLoginView() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(40), dp(24), dp(40));
        root.setBackgroundColor(COLOR_BG);
        root.setGravity(Gravity.CENTER_VERTICAL);
        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText("Triplens");
        title.setTextColor(COLOR_TEXT_PRIMARY);
        title.setTextSize(40);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("Enter credentials to access settings");
        subtitle.setTextColor(COLOR_TEXT_SECONDARY);
        subtitle.setTextSize(15);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(6), 0, dp(36));
        root.addView(subtitle, matchWrap());

        root.addView(label("Username or Email"), matchWrap());
        EditText loginInput = input("");
        loginInput.setHint("Enter username or email");
        root.addView(loginInput, matchWrapWithTop(6));

        root.addView(label("Password"), matchWrapWithTop(18));
        EditText passInput = input("");
        passInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passInput.setHint("••••••••");
        root.addView(passInput, matchWrapWithTop(6));

        Button loginBtn = primaryButton("Log In");
        loginBtn.setOnClickListener(v -> {
            String user = loginInput.getText().toString().trim();
            String pass = passInput.getText().toString();
            if (user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Please enter your username and password", Toast.LENGTH_SHORT).show();
                return;
            }

            Toast.makeText(this, "Logging in...", Toast.LENGTH_SHORT).show();
            TursoHelper.loginUser(this, user, pass, new TursoHelper.Callback() {
                @Override
                public void onSuccess(org.json.JSONArray rows) {
                    try {
                        org.json.JSONArray firstRow = rows.getJSONArray(0);
                        String username = TursoHelper.getValueAsString(firstRow.getJSONObject(0));
                        prefs.edit().putString(AcceptPrefs.KEY_LOGGED_IN_USER, username).apply();
                        Toast.makeText(MainActivity.this, "Welcome " + username + "!", Toast.LENGTH_SHORT).show();
                        navigateToScreen();
                    } catch (Exception ex) {
                        Toast.makeText(MainActivity.this, "Login error: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(MainActivity.this, "Login failed: " + message, Toast.LENGTH_LONG).show();
                }
            });
        });
        root.addView(loginBtn, matchWrapWithTop(28));

        Button signUpLink = textLinkButton("Create New Account");
        signUpLink.setOnClickListener(v -> setContentView(buildSignUpView()));
        root.addView(signUpLink, matchWrapWithTop(16));

        Button forgotLink = textLinkButton("Forgot Password?");
        forgotLink.setOnClickListener(v -> setContentView(buildRecoveryView()));
        root.addView(forgotLink, matchWrapWithTop(8));

        return scrollView;
    }

    private View buildSignUpView() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(30), dp(24), dp(30));
        root.setBackgroundColor(COLOR_BG);
        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText("Create Account");
        title.setTextColor(COLOR_TEXT_PRIMARY);
        title.setTextSize(30);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(title, matchWrap());

        TextView desc = new TextView(this);
        desc.setText("Please register with unique details to continue");
        desc.setTextColor(COLOR_TEXT_SECONDARY);
        desc.setTextSize(14);
        desc.setPadding(0, dp(4), 0, dp(24));
        root.addView(desc, matchWrap());

        root.addView(label("Username"), matchWrap());
        EditText userEdit = input("");
        root.addView(userEdit, matchWrapWithTop(6));

        root.addView(label("Email Address"), matchWrapWithTop(14));
        EditText emailEdit = input("");
        emailEdit.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        root.addView(emailEdit, matchWrapWithTop(6));

        root.addView(label("Phone Number"), matchWrapWithTop(14));
        EditText phoneEdit = input("");
        phoneEdit.setInputType(InputType.TYPE_CLASS_PHONE);
        root.addView(phoneEdit, matchWrapWithTop(6));

        root.addView(label("Password"), matchWrapWithTop(14));
        EditText passEdit = input("");
        passEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(passEdit, matchWrapWithTop(6));

        root.addView(label("Security Question"), matchWrapWithTop(14));
        EditText questionEdit = input("");
        questionEdit.setHint("e.g. What is your pet's name?");
        root.addView(questionEdit, matchWrapWithTop(6));

        root.addView(label("Answer to Security Question"), matchWrapWithTop(14));
        EditText answerEdit = input("");
        answerEdit.setHint("Answer is case-insensitive");
        root.addView(answerEdit, matchWrapWithTop(6));

        Button registerBtn = primaryButton("Register Account");
        registerBtn.setOnClickListener(v -> {
            String user = userEdit.getText().toString().trim();
            String email = emailEdit.getText().toString().trim();
            String phone = phoneEdit.getText().toString().trim();
            String pass = passEdit.getText().toString();
            String question = questionEdit.getText().toString().trim();
            String answer = answerEdit.getText().toString().trim();

            if (user.isEmpty() || email.isEmpty() || phone.isEmpty() || pass.isEmpty() || question.isEmpty() || answer.isEmpty()) {
                Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show();
                return;
            }

            Toast.makeText(this, "Creating account...", Toast.LENGTH_SHORT).show();
            TursoHelper.signUpUser(this, user, email, phone, pass, question, answer, new TursoHelper.Callback() {
                @Override
                public void onSuccess(org.json.JSONArray rows) {
                    Toast.makeText(MainActivity.this, "Registration successful! Please log in.", Toast.LENGTH_LONG).show();
                    setContentView(buildLoginView());
                }

                @Override
                public void onError(String message) {
                    String userFriendlyMessage = message;
                    if (message.contains("UNIQUE constraint failed")) {
                        if (message.contains("users.username")) {
                            userFriendlyMessage = "Username is already registered";
                        } else if (message.contains("users.email")) {
                            userFriendlyMessage = "Email address is already registered";
                        } else if (message.contains("users.phone")) {
                            userFriendlyMessage = "Phone number is already registered";
                        } else {
                            userFriendlyMessage = "Details must be unique. One of your inputs is already taken.";
                        }
                    }
                    Toast.makeText(MainActivity.this, "Sign Up Failed: " + userFriendlyMessage, Toast.LENGTH_LONG).show();
                }
            });
        });
        root.addView(registerBtn, matchWrapWithTop(28));

        Button backBtn = textLinkButton("Back to Log In");
        backBtn.setOnClickListener(v -> setContentView(buildLoginView()));
        root.addView(backBtn, matchWrapWithTop(16));

        return scrollView;
    }

    private View buildRecoveryView() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(30), dp(24), dp(30));
        root.setBackgroundColor(COLOR_BG);
        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText("Recover Password");
        title.setTextColor(COLOR_TEXT_PRIMARY);
        title.setTextSize(30);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(title, matchWrap());

        TextView desc = new TextView(this);
        desc.setText("Verify your details to reset your password");
        desc.setTextColor(COLOR_TEXT_SECONDARY);
        desc.setTextSize(14);
        desc.setPadding(0, dp(4), 0, dp(24));
        root.addView(desc, matchWrap());

        root.addView(label("Username or Email"), matchWrap());
        EditText searchInput = input("");
        searchInput.setHint("Enter registered username or email");
        root.addView(searchInput, matchWrapWithTop(6));

        LinearLayout step2Container = new LinearLayout(this);
        step2Container.setOrientation(LinearLayout.VERTICAL);
        step2Container.setVisibility(View.GONE);

        TextView questionText = new TextView(this);
        questionText.setTextSize(16);
        questionText.setTextColor(COLOR_ACCENT);
        questionText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        questionText.setPadding(dp(12), dp(10), dp(12), dp(10));
        questionText.setBackground(roundedRect(COLOR_INPUT_BG, 8));
        
        step2Container.addView(label("Your Security Question:"), matchWrapWithTop(16));
        step2Container.addView(questionText, matchWrapWithTop(6));

        step2Container.addView(label("Security Answer"), matchWrapWithTop(16));
        EditText answerInput = input("");
        answerInput.setHint("Enter security question answer");
        step2Container.addView(answerInput, matchWrapWithTop(6));

        step2Container.addView(label("New Password"), matchWrapWithTop(16));
        EditText newPassInput = input("");
        newPassInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        newPassInput.setHint("••••••••");
        step2Container.addView(newPassInput, matchWrapWithTop(6));

        root.addView(step2Container, matchWrap());

        Button step1Btn = primaryButton("Fetch Security Question");
        step1Btn.setOnClickListener(v -> {
            String userStr = searchInput.getText().toString().trim();
            if (userStr.isEmpty()) {
                Toast.makeText(this, "Please enter your username or email", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(this, "Fetching question...", Toast.LENGTH_SHORT).show();
            TursoHelper.getRecoveryQuestion(this, userStr, new TursoHelper.Callback() {
                @Override
                public void onSuccess(org.json.JSONArray rows) {
                    if (rows == null || rows.length() == 0) {
                        Toast.makeText(MainActivity.this, "User not found", Toast.LENGTH_LONG).show();
                        return;
                    }
                    try {
                        org.json.JSONArray firstRow = rows.getJSONArray(0);
                        String question = TursoHelper.getValueAsString(firstRow.getJSONObject(0));
                        if (question == null || question.trim().isEmpty()) {
                            Toast.makeText(MainActivity.this, "This user does not have a security question configured.", Toast.LENGTH_LONG).show();
                        } else {
                            questionText.setText(question);
                            step2Container.setVisibility(View.VISIBLE);
                            step1Btn.setVisibility(View.GONE);
                            searchInput.setEnabled(false);
                        }
                    } catch (Exception ex) {
                        Toast.makeText(MainActivity.this, "Error: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(MainActivity.this, "Error fetching details: " + message, Toast.LENGTH_LONG).show();
                }
            });
        });
        root.addView(step1Btn, matchWrapWithTop(20));

        Button resetBtn = primaryButton("Reset Password");
        resetBtn.setOnClickListener(v -> {
            String userStr = searchInput.getText().toString().trim();
            String answer = answerInput.getText().toString().trim();
            String newPass = newPassInput.getText().toString();

            if (answer.isEmpty() || newPass.isEmpty()) {
                Toast.makeText(this, "Please fill in all recovery fields", Toast.LENGTH_SHORT).show();
                return;
            }

            Toast.makeText(this, "Resetting password...", Toast.LENGTH_SHORT).show();
            TursoHelper.resetPassword(this, userStr, answer, newPass, new TursoHelper.Callback() {
                @Override
                public void onSuccess(org.json.JSONArray rows) {
                    Toast.makeText(MainActivity.this, "Password reset successfully! Please log in.", Toast.LENGTH_LONG).show();
                    setContentView(buildLoginView());
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(MainActivity.this, "Reset failed: " + message, Toast.LENGTH_LONG).show();
                }
            });
        });
        step2Container.addView(resetBtn, matchWrapWithTop(28));

        Button backBtn = textLinkButton("Back to Log In");
        backBtn.setOnClickListener(v -> setContentView(buildLoginView()));
        root.addView(backBtn, matchWrapWithTop(16));

        return scrollView;
    }

    private View buildDashboardView() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(COLOR_BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(20), dp(18), dp(20));
        scrollView.addView(root);

        // Fetch sub data
        long subExpires = prefs.getLong(AcceptPrefs.KEY_SUB_EXPIRES, 0L);
        int freeClicks = prefs.getInt(AcceptPrefs.KEY_FREE_CLICKS, 0);
        String loggedInUser = prefs.getString(AcceptPrefs.KEY_LOGGED_IN_USER, "User");

        // 1. Header Row
        root.addView(buildHeaderView(loggedInUser, subExpires, freeClicks));

        // 2. Log Out Row
        root.addView(buildLogOutRow(), matchWrapWithTop(12));

        // 3. Info Description Card
        root.addView(buildDescriptionCard(), matchWrapWithTop(12));

        // 4. Accessibility Service Status Bar
        root.addView(buildAccessibilityStatusCard(), matchWrapWithTop(12));

        // 5. Actions Buttons (Side-by-Side)
        root.addView(buildActionButtonsRow(), matchWrapWithTop(12));

        // 6. App Mode Selection Card
        root.addView(buildAppModeSelectionCard(), matchWrapWithTop(12));

        // 7. Distance Settings / Custom Settings Card (Conditional)
        String currentMode = prefs.getString(AcceptPrefs.KEY_APP_MODE, "rapido");
        if ("custom".equals(currentMode)) {
            root.addView(buildCustomAppSettingsCard(), matchWrapWithTop(12));
        } else {
            root.addView(buildTargetedAppsCard(), matchWrapWithTop(12));
            root.addView(buildDistanceSettingsCard(), matchWrapWithTop(12));
        }

        // 8. Save Settings Button
        Button saveBtn = primaryButton("Save Settings");
        saveBtn.setOnClickListener(v -> saveSettings());
        root.addView(saveBtn, matchWrapWithTop(18));

        // 9. Footer
        TextView footer = new TextView(this);
        footer.setText("Auto-clicking for configured package. Distance limits are used only in default mode.");
        footer.setTextColor(COLOR_TEXT_SECONDARY);
        footer.setTextSize(12);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(20), 0, dp(10));
        root.addView(footer, matchWrap());

        return scrollView;
    }

    private View buildHeaderView(String loggedInUser, long subExpires, int freeClicks) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(16));

        // Left Section: App Logo + Name
        LinearLayout leftSec = new LinearLayout(this);
        leftSec.setOrientation(LinearLayout.HORIZONTAL);
        leftSec.setGravity(Gravity.CENTER_VERTICAL);
        
        // Glowing app icon (using system compass location)
        View appIcon = systemIcon(android.R.drawable.ic_menu_compass, Color.rgb(8, 145, 178), 44, 10);
        leftSec.addView(appIcon);

        LinearLayout nameSec = new LinearLayout(this);
        nameSec.setOrientation(LinearLayout.VERTICAL);
        nameSec.setPadding(dp(10), 0, 0, 0);

        TextView appTitle = new TextView(this);
        appTitle.setText("TripLens");
        appTitle.setTextColor(Color.WHITE);
        appTitle.setTextSize(22);
        appTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        nameSec.addView(appTitle);

        TextView userText = new TextView(this);
        userText.setTextColor(COLOR_TEXT_SECONDARY);
        userText.setTextSize(12);
        
        android.text.SpannableStringBuilder builder = new android.text.SpannableStringBuilder();
        builder.append("Logged in as: ");
        int start = builder.length();
        builder.append(loggedInUser);
        builder.setSpan(new android.text.style.ForegroundColorSpan(Color.rgb(6, 182, 212)), start, builder.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start, builder.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        userText.setText(builder);
        nameSec.addView(userText);

        leftSec.addView(nameSec);
        
        LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        header.addView(leftSec, leftParams);

        // Right Section: Subscription Card
        LinearLayout subCard = new LinearLayout(this);
        subCard.setOrientation(LinearLayout.HORIZONTAL);
        subCard.setGravity(Gravity.CENTER_VERTICAL);
        subCard.setBackground(roundedRect(COLOR_CARD, 12));
        subCard.setPadding(dp(12), dp(8), dp(12), dp(8));

        View crownIcon = systemIcon(android.R.drawable.btn_star_big_on, Color.rgb(217, 119, 6), 32, 6);
        subCard.addView(crownIcon);

        LinearLayout subTextSec = new LinearLayout(this);
        subTextSec.setOrientation(LinearLayout.VERTICAL);
        subTextSec.setPadding(dp(8), 0, dp(8), 0);

        TextView subTitle = new TextView(this);
        subTitle.setText("Subscription");
        subTitle.setTextColor(COLOR_TEXT_SECONDARY);
        subTitle.setTextSize(11);
        subTextSec.addView(subTitle);

        boolean isSubscribed = subExpires > (System.currentTimeMillis() / 1000L);
        TextView subStatus = new TextView(this);
        subStatus.setTextSize(13);
        subStatus.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        
        TextView subDesc = new TextView(this);
        subDesc.setTextColor(COLOR_TEXT_SECONDARY);
        subDesc.setTextSize(10);

        if (isSubscribed) {
            subStatus.setText("Active");
            subStatus.setTextColor(COLOR_ACCENT);
            subDesc.setText("Expires soon");
        } else if (freeClicks > 0) {
            subStatus.setText("Trial");
            subStatus.setTextColor(COLOR_WARNING);
            subDesc.setText(freeClicks + " Click" + (freeClicks > 1 ? "s" : "") + " remaining");
        } else {
            subStatus.setText("Expired");
            subStatus.setTextColor(COLOR_DANGER);
            subDesc.setText("Buy a pass");
        }
        subTextSec.addView(subStatus);
        subTextSec.addView(subDesc);

        subCard.addView(subTextSec);

        TextView chevron = new TextView(this);
        chevron.setText(">");
        chevron.setTextColor(COLOR_TEXT_SECONDARY);
        chevron.setTextSize(14);
        subCard.addView(chevron);

        subCard.setOnClickListener(v -> {
            setContentView(buildSubscriptionView());
        });

        header.addView(subCard);

        return header;
    }

    private View buildLogOutRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(roundedRect(COLOR_CARD, 12));
        row.setPadding(dp(16), dp(12), dp(16), dp(12));

        View logoutIcon = systemIcon(android.R.drawable.ic_lock_power_off, Color.rgb(6, 95, 70), 32, 8);
        row.addView(logoutIcon);

        TextView text = new TextView(this);
        text.setText("Log Out");
        text.setTextColor(COLOR_ACCENT);
        text.setTextSize(16);
        text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        text.setPadding(dp(12), 0, 0, 0);

        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        row.addView(text, textParams);

        TextView chevron = new TextView(this);
        chevron.setText(">");
        chevron.setTextColor(COLOR_TEXT_SECONDARY);
        chevron.setTextSize(16);
        row.addView(chevron);

        row.setOnClickListener(v -> {
            prefs.edit().putString(AcceptPrefs.KEY_LOGGED_IN_USER, "").apply();
            Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
            navigateToScreen();
        });

        return row;
    }

    private View buildDescriptionCard() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(roundedRect(COLOR_CARD, 12));
        row.setPadding(dp(16), dp(12), dp(16), dp(12));

        View infoIcon = systemIcon(android.R.drawable.ic_dialog_info, Color.rgb(30, 58, 138), 32, 8);
        row.addView(infoIcon);

        TextView text = new TextView(this);
        text.setText("Auto-clicks a matching button inside your configured custom app.");
        text.setTextColor(COLOR_TEXT_SECONDARY);
        text.setTextSize(13);
        text.setPadding(dp(12), 0, 0, 0);

        row.addView(text, matchWrap());
        return row;
    }

    private View buildAccessibilityStatusCard() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        
        boolean serviceEnabled = isAccessibilityServiceEnabled();
        row.setBackground(roundedRectWithBorder(COLOR_CARD, 12, serviceEnabled ? COLOR_ACCENT : COLOR_DANGER, 1));

        View statusIcon = systemIcon(android.R.drawable.ic_lock_lock, serviceEnabled ? Color.rgb(6, 95, 70) : Color.rgb(153, 27, 27), 36, 8);
        row.addView(statusIcon);

        LinearLayout textSec = new LinearLayout(this);
        textSec.setOrientation(LinearLayout.VERTICAL);
        textSec.setPadding(dp(12), 0, dp(12), 0);

        TextView statusText = new TextView(this);
        statusText.setTextSize(15);
        statusText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        
        android.text.SpannableStringBuilder builder = new android.text.SpannableStringBuilder();
        builder.append("Accessibility Service: ");
        int start = builder.length();
        if (serviceEnabled) {
            builder.append("ACTIVE");
            builder.setSpan(new android.text.style.ForegroundColorSpan(COLOR_ACCENT), start, builder.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            builder.append("DISABLED");
            builder.setSpan(new android.text.style.ForegroundColorSpan(COLOR_DANGER), start, builder.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        statusText.setText(builder);
        textSec.addView(statusText);

        TextView subtext = new TextView(this);
        subtext.setText(serviceEnabled ? "Service is ready to click" : "Enable accessibility to start auto-clicker");
        subtext.setTextColor(COLOR_TEXT_SECONDARY);
        subtext.setTextSize(12);
        textSec.addView(subtext);

        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        row.addView(textSec, textParams);

        android.widget.Switch toggle = new android.widget.Switch(this);
        toggle.setChecked(serviceEnabled);
        toggle.setClickable(false); // Handled through row click
        row.addView(toggle);

        row.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });

        return row;
    }

    private View buildActionButtonsRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        // Left button: Open Accessibility Settings
        LinearLayout openBtn = new LinearLayout(this);
        openBtn.setOrientation(LinearLayout.HORIZONTAL);
        openBtn.setGravity(Gravity.CENTER_VERTICAL);
        openBtn.setBackground(roundedRect(COLOR_CARD, 12));
        openBtn.setPadding(dp(12), dp(14), dp(12), dp(14));
        openBtn.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        View gearIcon = systemIcon(android.R.drawable.ic_menu_manage, Color.rgb(30, 41, 59), 32, 8);
        openBtn.addView(gearIcon);

        LinearLayout openTextSec = new LinearLayout(this);
        openTextSec.setOrientation(LinearLayout.VERTICAL);
        openTextSec.setPadding(dp(8), 0, dp(4), 0);

        TextView openText = new TextView(this);
        openText.setText("Open Accessibility");
        openText.setTextColor(Color.WHITE);
        openText.setTextSize(13);
        openText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        
        TextView openSubtext = new TextView(this);
        openSubtext.setText("Settings");
        openSubtext.setTextColor(COLOR_TEXT_SECONDARY);
        openSubtext.setTextSize(11);

        openTextSec.addView(openText);
        openTextSec.addView(openSubtext);

        LinearLayout.LayoutParams openTextParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        openBtn.addView(openTextSec, openTextParams);

        TextView openChevron = new TextView(this);
        openChevron.setText(">");
        openChevron.setTextColor(COLOR_TEXT_SECONDARY);
        openChevron.setTextSize(14);
        openBtn.addView(openChevron);

        LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f);
        leftParams.rightMargin = dp(8);
        row.addView(openBtn, leftParams);

        // Right button: Start/Stop Auto-Clicker
        LinearLayout toggleBtn = new LinearLayout(this);
        toggleBtn.setOrientation(LinearLayout.HORIZONTAL);
        toggleBtn.setGravity(Gravity.CENTER_VERTICAL);
        
        boolean isEnabled = prefs.getBoolean(AcceptPrefs.KEY_ENABLED, false);
        toggleBtn.setBackground(roundedRect(isEnabled ? COLOR_DANGER : COLOR_ACCENT, 12));
        toggleBtn.setPadding(dp(12), dp(14), dp(12), dp(14));

        View playIcon = systemIcon(isEnabled ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play, Color.WHITE, 32, 8);
        if (playIcon instanceof android.widget.ImageView) {
            ((android.widget.ImageView) playIcon).setColorFilter(isEnabled ? COLOR_DANGER : COLOR_ACCENT);
        }
        toggleBtn.addView(playIcon);

        LinearLayout toggleTextSec = new LinearLayout(this);
        toggleTextSec.setOrientation(LinearLayout.VERTICAL);
        toggleTextSec.setPadding(dp(8), 0, 0, 0);

        TextView toggleText = new TextView(this);
        toggleText.setText(isEnabled ? "STOP CLICKER" : "START CLICKER");
        toggleText.setTextColor(Color.WHITE);
        toggleText.setTextSize(12);
        toggleText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        TextView toggleSubtext = new TextView(this);
        toggleSubtext.setText(isEnabled ? "Tap to pause clicks" : "Tap to start auto clicking");
        toggleSubtext.setTextColor(Color.rgb(220, 252, 231));
        toggleSubtext.setTextSize(10);

        toggleTextSec.addView(toggleText);
        toggleTextSec.addView(toggleSubtext);

        toggleBtn.addView(toggleTextSec);

        toggleBtn.setOnClickListener(v -> {
            boolean current = prefs.getBoolean(AcceptPrefs.KEY_ENABLED, false);
            boolean next = !current;
            prefs.edit().putBoolean(AcceptPrefs.KEY_ENABLED, next).apply();
            
            Toast.makeText(this, next ? "Auto-Clicker Started" : "Auto-Clicker Stopped", Toast.LENGTH_SHORT).show();
            setContentView(buildDashboardView());
        });

        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f);
        rightParams.leftMargin = dp(8);
        row.addView(toggleBtn, rightParams);

        return row;
    }

    private View buildAppModeSelectionCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundedRect(COLOR_CARD, 12));
        card.setPadding(dp(16), dp(16), dp(16), dp(16));

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(12));

        header.addView(headerIcon(android.R.drawable.ic_menu_myplaces));

        TextView title = new TextView(this);
        title.setText("Select App Mode");
        title.setTextColor(COLOR_TEXT_PRIMARY);
        title.setTextSize(15);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setPadding(dp(8), 0, 0, 0);
        header.addView(title);

        card.addView(header);

        // Options Row
        LinearLayout optionsRow = new LinearLayout(this);
        optionsRow.setOrientation(LinearLayout.HORIZONTAL);

        String currentMode = prefs.getString(AcceptPrefs.KEY_APP_MODE, "rapido");
        boolean isRapidoSelected = "rapido".equals(currentMode);

        // Option 1: Rapido
        LinearLayout rapidoOpt = new LinearLayout(this);
        rapidoOpt.setOrientation(LinearLayout.HORIZONTAL);
        rapidoOpt.setGravity(Gravity.CENTER_VERTICAL);
        rapidoOpt.setPadding(dp(12), dp(12), dp(12), dp(12));
        rapidoOpt.setBackground(roundedRectWithBorder(COLOR_INPUT_BG, 10, isRapidoSelected ? COLOR_ACCENT : Color.TRANSPARENT, 1));
        
        rapidoOpt.addView(radioRing(isRapidoSelected));

        View rapidoIcon = systemIcon(android.R.drawable.ic_menu_directions, Color.rgb(6, 95, 70), 28, 6);
        LinearLayout.LayoutParams iconParams1 = new LinearLayout.LayoutParams(dp(28), dp(28));
        iconParams1.leftMargin = dp(8);
        rapidoIcon.setLayoutParams(iconParams1);
        rapidoOpt.addView(rapidoIcon);

        LinearLayout rapidoTextSec = new LinearLayout(this);
        rapidoTextSec.setOrientation(LinearLayout.VERTICAL);
        rapidoTextSec.setPadding(dp(8), 0, 0, 0);

        TextView rapidoTitle = new TextView(this);
        rapidoTitle.setText("Default App");
        rapidoTitle.setTextColor(COLOR_TEXT_PRIMARY);
        rapidoTitle.setTextSize(13);
        rapidoTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        TextView rapidoSub = new TextView(this);
        rapidoSub.setText("(Rapido Rider)");
        rapidoSub.setTextColor(COLOR_TEXT_SECONDARY);
        rapidoSub.setTextSize(11);

        rapidoTextSec.addView(rapidoTitle);
        rapidoTextSec.addView(rapidoSub);
        rapidoOpt.addView(rapidoTextSec);

        rapidoOpt.setOnClickListener(v -> {
            prefs.edit().putString(AcceptPrefs.KEY_APP_MODE, "rapido").apply();
            setContentView(buildDashboardView());
        });

        LinearLayout.LayoutParams param1 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        param1.rightMargin = dp(6);
        optionsRow.addView(rapidoOpt, param1);

        // Option 2: Custom
        LinearLayout customOpt = new LinearLayout(this);
        customOpt.setOrientation(LinearLayout.HORIZONTAL);
        customOpt.setGravity(Gravity.CENTER_VERTICAL);
        customOpt.setPadding(dp(12), dp(12), dp(12), dp(12));
        customOpt.setBackground(roundedRectWithBorder(COLOR_INPUT_BG, 10, !isRapidoSelected ? COLOR_ACCENT : Color.TRANSPARENT, 1));

        customOpt.addView(radioRing(!isRapidoSelected));

        View customIcon = systemIcon(android.R.drawable.ic_menu_apps, Color.rgb(30, 41, 59), 28, 6);
        LinearLayout.LayoutParams iconParams2 = new LinearLayout.LayoutParams(dp(28), dp(28));
        iconParams2.leftMargin = dp(8);
        customIcon.setLayoutParams(iconParams2);
        customOpt.addView(customIcon);

        LinearLayout customTextSec = new LinearLayout(this);
        customTextSec.setOrientation(LinearLayout.VERTICAL);
        customTextSec.setPadding(dp(8), 0, 0, 0);

        TextView customTitleText = new TextView(this);
        customTitleText.setText("Other Apps");
        customTitleText.setTextColor(COLOR_TEXT_PRIMARY);
        customTitleText.setTextSize(13);
        customTitleText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        TextView customSub = new TextView(this);
        customSub.setText("(Custom App)");
        customSub.setTextColor(COLOR_TEXT_SECONDARY);
        customSub.setTextSize(11);

        customTextSec.addView(customTitleText);
        customTextSec.addView(customSub);
        customOpt.addView(customTextSec);

        customOpt.setOnClickListener(v -> {
            prefs.edit().putString(AcceptPrefs.KEY_APP_MODE, "custom").apply();
            setContentView(buildDashboardView());
        });

        LinearLayout.LayoutParams param2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        param2.leftMargin = dp(6);
        optionsRow.addView(customOpt, param2);

        card.addView(optionsRow);
        return card;
    }

    private View buildSettingRow(String title, String subtitle, EditText inputField, int drawableId, int emojiBgColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));

        View squareIcon = systemIcon(drawableId, emojiBgColor, 36, 8);
        row.addView(squareIcon);

        LinearLayout textSec = new LinearLayout(this);
        textSec.setOrientation(LinearLayout.VERTICAL);
        textSec.setPadding(dp(12), 0, dp(12), 0);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(COLOR_TEXT_PRIMARY);
        titleView.setTextSize(14);
        titleView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        textSec.addView(titleView);

        TextView subView = new TextView(this);
        subView.setText(subtitle);
        subView.setTextColor(COLOR_TEXT_SECONDARY);
        subView.setTextSize(11);
        textSec.addView(subView);

        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        row.addView(textSec, textParams);

        inputField.setGravity(Gravity.CENTER);
        inputField.setTextSize(14);
        inputField.setTextColor(COLOR_TEXT_PRIMARY);
        inputField.setPadding(dp(12), dp(6), dp(12), dp(6));
        inputField.setBackground(roundedRect(COLOR_INPUT_BG, 8));
        
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT);
        row.addView(inputField, inputParams);

        return row;
    }

    private View buildDistanceSettingsCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundedRect(COLOR_CARD, 12));
        card.setPadding(dp(16), dp(16), dp(16), dp(16));

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(12));

        header.addView(headerIcon(android.R.drawable.ic_menu_manage));

        TextView title = new TextView(this);
        title.setText("Distance Settings (km)");
        title.setTextColor(COLOR_TEXT_PRIMARY);
        title.setTextSize(15);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setPadding(dp(8), 0, 0, 0);
        header.addView(title);

        card.addView(header);

        // Row 1: Min Pickup
        minPickupInput = new EditText(this);
        minPickupInput.setText(String.valueOf(prefs.getFloat(AcceptPrefs.KEY_MIN_PICKUP, 0.0f)));
        minPickupInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        card.addView(buildSettingRow("Minimum Pickup Distance", "Set the minimum distance (km)", minPickupInput, android.R.drawable.ic_menu_mylocation, Color.rgb(109, 40, 217)));

        // Row 2: Max Pickup
        maxPickupInput = new EditText(this);
        maxPickupInput.setText(String.valueOf(prefs.getFloat(AcceptPrefs.KEY_MAX_PICKUP, 5.0f)));
        maxPickupInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        card.addView(buildSettingRow("Maximum Pickup Distance", "Set the maximum distance (km)", maxPickupInput, android.R.drawable.ic_menu_compass, Color.rgb(29, 78, 216)));

        // Row 3: Min Drop
        minDropInput = new EditText(this);
        minDropInput.setText(String.valueOf(prefs.getFloat(AcceptPrefs.KEY_MIN_DROP, 0.0f)));
        minDropInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        card.addView(buildSettingRow("Minimum Drop Distance", "Set the minimum drop distance (km)", minDropInput, android.R.drawable.ic_input_get, Color.rgb(194, 65, 12)));

        // Row 4: Max Drop
        maxDropInput = new EditText(this);
        maxDropInput.setText(String.valueOf(prefs.getFloat(AcceptPrefs.KEY_MAX_DROP, 15.0f)));
        maxDropInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        card.addView(buildSettingRow("Maximum Drop Distance", "Set the maximum drop distance (km)", maxDropInput, android.R.drawable.ic_menu_share, Color.rgb(190, 24, 74)));

        return card;
    }

    private View buildCustomAppSettingsCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundedRect(COLOR_CARD, 12));
        card.setPadding(dp(16), dp(16), dp(16), dp(16));

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(12));

        header.addView(headerIcon(android.R.drawable.ic_menu_manage));

        TextView title = new TextView(this);
        title.setText("Custom App Settings");
        title.setTextColor(COLOR_TEXT_PRIMARY);
        title.setTextSize(15);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setPadding(dp(8), 0, 0, 0);
        header.addView(title);

        card.addView(header);

        // Row 1: Custom Package Name
        customPackageInput = new EditText(this);
        customPackageInput.setText(prefs.getString(AcceptPrefs.KEY_CUSTOM_PACKAGE, ""));
        customPackageInput.setHint("com.example");
        customPackageInput.setInputType(InputType.TYPE_CLASS_TEXT);
        card.addView(buildSettingRow("App Package Name", "Package name of target app", customPackageInput, android.R.drawable.ic_menu_apps, Color.rgb(29, 78, 216)));

        // Row 2: Custom Target Texts
        customTargetTextInput = new EditText(this);
        customTargetTextInput.setText(prefs.getString(AcceptPrefs.KEY_CUSTOM_TARGET_TEXT, "Accept"));
        customTargetTextInput.setHint("Accept,Click");
        customTargetTextInput.setInputType(InputType.TYPE_CLASS_TEXT);
        card.addView(buildSettingRow("Target Click Texts", "Comma-separated target values", customTargetTextInput, android.R.drawable.ic_menu_edit, Color.rgb(15, 118, 110)));

        return card;
    }

    private void saveSettings() {
        String customPkg = (customPackageInput != null) ? customPackageInput.getText().toString().trim() : prefs.getString(AcceptPrefs.KEY_CUSTOM_PACKAGE, "");
        String customTargetText = (customTargetTextInput != null) ? customTargetTextInput.getText().toString().trim() : prefs.getString(AcceptPrefs.KEY_CUSTOM_TARGET_TEXT, "Accept");

        float minP = (minPickupInput != null) ? parseFloatSafely(minPickupInput.getText().toString()) : prefs.getFloat(AcceptPrefs.KEY_MIN_PICKUP, 0.0f);
        float maxP = (maxPickupInput != null) ? parseFloatSafely(maxPickupInput.getText().toString()) : prefs.getFloat(AcceptPrefs.KEY_MAX_PICKUP, 5.0f);
        float minD = (minDropInput != null) ? parseFloatSafely(minDropInput.getText().toString()) : prefs.getFloat(AcceptPrefs.KEY_MIN_DROP, 0.0f);
        float maxD = (maxDropInput != null) ? parseFloatSafely(maxDropInput.getText().toString()) : prefs.getFloat(AcceptPrefs.KEY_MAX_DROP, 15.0f);

        prefs.edit()
                .putString(AcceptPrefs.KEY_CUSTOM_PACKAGE, customPkg)
                .putString(AcceptPrefs.KEY_CUSTOM_TARGET_TEXT, customTargetText)
                .putFloat(AcceptPrefs.KEY_MIN_PICKUP, minP)
                .putFloat(AcceptPrefs.KEY_MAX_PICKUP, maxP)
                .putFloat(AcceptPrefs.KEY_MIN_DROP, minD)
                .putFloat(AcceptPrefs.KEY_MAX_DROP, maxD)
                .apply();
        refreshStatus();
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
    }

    private float parseFloatSafely(String raw) {
        try {
            return Float.parseFloat(raw.trim());
        } catch (NumberFormatException ex) {
            return 0.0f;
        }
    }

    private void refreshStatus() {
        boolean serviceEnabled = isAccessibilityServiceEnabled();
        if (serviceStatus != null) {
            serviceStatus.setText(serviceEnabled ? "Accessibility Service: ACTIVE" : "Accessibility Service: DISABLED");
            serviceStatus.setTextColor(Color.WHITE);
            serviceStatus.setBackground(roundedRect(serviceEnabled ? Color.rgb(6, 95, 70) : Color.rgb(153, 27, 27), 8));
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        String enabledServices = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabledServices != null && enabledServices.toLowerCase().contains(getPackageName().toLowerCase());
    }

    private TextView statusText() {
        TextView view = new TextView(this);
        view.setTextSize(15);
        view.setPadding(0, dp(3), 0, dp(3));
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchWrapWithTop(int topDp) {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(topDp);
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private View buildBlockedView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        root.setBackgroundColor(COLOR_BG);

        TextView title = new TextView(this);
        title.setText("Account Blocked");
        title.setTextColor(COLOR_DANGER);
        title.setTextSize(28);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView desc = new TextView(this);
        desc.setText("Your account has been suspended or blocked by the administrator. Please contact support if you believe this is a mistake.");
        desc.setTextColor(COLOR_TEXT_SECONDARY);
        desc.setTextSize(16);
        desc.setGravity(Gravity.CENTER);
        desc.setPadding(0, dp(16), 0, dp(32));
        root.addView(desc, matchWrap());

        Button logoutBtn = primaryButton("Log Out");
        logoutBtn.setOnClickListener(v -> {
            prefs.edit().putString(AcceptPrefs.KEY_LOGGED_IN_USER, "").apply();
            navigateToScreen();
        });
        root.addView(logoutBtn, matchWrap());

        return root;
    }

    private View buildSubscriptionView() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));
        root.setBackgroundColor(COLOR_BG);
        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText("Premium Required");
        title.setTextColor(COLOR_TEXT_PRIMARY);
        title.setTextSize(28);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        String loggedInUser = prefs.getString(AcceptPrefs.KEY_LOGGED_IN_USER, "User");
        TextView userSessionText = new TextView(this);
        userSessionText.setText("Logged in as: " + loggedInUser);
        userSessionText.setTextColor(COLOR_TEXT_SECONDARY);
        userSessionText.setTextSize(14);
        userSessionText.setGravity(Gravity.CENTER);
        userSessionText.setPadding(0, dp(4), 0, dp(12));
        root.addView(userSessionText, matchWrap());

        int freeClicks = prefs.getInt(AcceptPrefs.KEY_FREE_CLICKS, 0);
        TextView trialStatusText = new TextView(this);
        if (freeClicks > 0) {
            trialStatusText.setText("Trial Status: " + freeClicks + " free click" + (freeClicks > 1 ? "s" : "") + " remaining");
            trialStatusText.setTextColor(COLOR_ACCENT);
        } else {
            trialStatusText.setText("Trial Status: Free clicks exhausted");
            trialStatusText.setTextColor(COLOR_DANGER);
        }
        trialStatusText.setTextSize(15);
        trialStatusText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        trialStatusText.setGravity(Gravity.CENTER);
        trialStatusText.setPadding(0, dp(6), 0, dp(20));
        root.addView(trialStatusText, matchWrap());

        // Plans Section
        TextView plansHeader = label("Available Subscription Plans");
        plansHeader.setGravity(Gravity.CENTER);
        plansHeader.setPadding(0, 0, 0, dp(10));
        root.addView(plansHeader, matchWrap());

        // Card 1: 20 / Day
        root.addView(buildPlanCard("Day Pass", "₹20 / day", "Great for quick trials or temporary usage"), matchWrapWithTop(10));
        
        // Card 2: 99 / Week
        root.addView(buildPlanCard("Week Pass", "₹99 / week", "Perfect for regular weekly work schedules"), matchWrapWithTop(10));
        
        // Card 3: 299 / Month
        root.addView(buildPlanCard("Month Pass", "₹299 / month", "Best value. Unrestricted access for a full month"), matchWrapWithTop(10));

        // Demo Activation Button
        Button demoBtn = new Button(this);
        demoBtn.setText("Demo: Activate Trial (Add 1 Day Premium)");
        demoBtn.setBackground(roundedRect(COLOR_ACCENT, 10));
        demoBtn.setTextColor(Color.WHITE);
        demoBtn.setAllCaps(false);
        demoBtn.setTextSize(16);
        demoBtn.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        demoBtn.setPadding(0, dp(14), 0, dp(14));
        demoBtn.setOnClickListener(v -> {
            Toast.makeText(this, "Activating 1-day demo subscription...", Toast.LENGTH_SHORT).show();
            TursoHelper.demoActivateSubscription(this, loggedInUser, 1, new TursoHelper.Callback() {
                @Override
                public void onSuccess(org.json.JSONArray rows) {
                    Toast.makeText(MainActivity.this, "Demo subscription activated successfully!", Toast.LENGTH_LONG).show();
                    proceedNavigation();
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(MainActivity.this, "Failed to activate demo: " + message, Toast.LENGTH_LONG).show();
                }
            });
        });
        root.addView(demoBtn, matchWrapWithTop(28));

        Button logoutBtn = textLinkButton("Log Out");
        logoutBtn.setOnClickListener(v -> {
            prefs.edit().putString(AcceptPrefs.KEY_LOGGED_IN_USER, "").apply();
            navigateToScreen();
        });
        root.addView(logoutBtn, matchWrapWithTop(16));

        return scrollView;
    }

    private View buildPlanCard(String name, String price, String desc) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(roundedRectWithBorder(COLOR_CARD, 12, COLOR_BORDER, 1));
        
        TextView nameTxt = new TextView(this);
        nameTxt.setText(name);
        nameTxt.setTextSize(16);
        nameTxt.setTextColor(COLOR_TEXT_PRIMARY);
        nameTxt.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        
        TextView priceTxt = new TextView(this);
        priceTxt.setText(price);
        priceTxt.setTextSize(18);
        priceTxt.setTextColor(COLOR_ACCENT);
        priceTxt.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        TextView descTxt = new TextView(this);
        descTxt.setText(desc);
        descTxt.setTextSize(13);
        descTxt.setTextColor(COLOR_TEXT_SECONDARY);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        header.addView(nameTxt, nameParams);
        header.addView(priceTxt);

        card.addView(header, matchWrap());
        card.addView(descTxt, matchWrapWithTop(6));

        card.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("Select " + name)
                .setMessage("In a production release, this would securely redirect to your Payment Gateway interface. For testing, please use the 'Demo: Activate Trial' button or the admin CLI tool.")
                .setPositiveButton("OK", null)
                .show();
        });

        return card;
    }

    private View buildTargetedAppsCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundedRect(COLOR_CARD, 12));
        card.setPadding(dp(16), dp(16), dp(16), dp(16));

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(12));

        header.addView(headerIcon(android.R.drawable.ic_menu_myplaces));

        TextView title = new TextView(this);
        title.setText("Targeted Ride/Delivery Apps");
        title.setTextColor(COLOR_TEXT_PRIMARY);
        title.setTextSize(15);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setPadding(dp(8), 0, 0, 0);
        header.addView(title);

        card.addView(header);

        // Checkbox 1: Rapido
        CheckBox rapidoCb = new CheckBox(this);
        rapidoCb.setText("Rapido Rider (com.rapido.rider)");
        rapidoCb.setTextColor(COLOR_TEXT_PRIMARY);
        rapidoCb.setTextSize(14);
        rapidoCb.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        rapidoCb.setChecked(prefs.getBoolean("target_rapido", true));
        rapidoCb.setPadding(dp(8), dp(8), dp(8), dp(8));
        rapidoCb.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("target_rapido", isChecked).apply();
        });
        card.addView(rapidoCb);

        // Checkbox 2: Uber
        CheckBox uberCb = new CheckBox(this);
        uberCb.setText("Uber Driver (com.ubercab.driver)");
        uberCb.setTextColor(COLOR_TEXT_PRIMARY);
        uberCb.setTextSize(14);
        uberCb.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        uberCb.setChecked(prefs.getBoolean("target_uber", true));
        uberCb.setPadding(dp(8), dp(8), dp(8), dp(8));
        uberCb.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("target_uber", isChecked).apply();
        });
        card.addView(uberCb);

        // Checkbox 3: Ola
        CheckBox olaCb = new CheckBox(this);
        olaCb.setText("Ola Driver (com.olacabs.oladriver)");
        olaCb.setTextColor(COLOR_TEXT_PRIMARY);
        olaCb.setTextSize(14);
        olaCb.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        olaCb.setChecked(prefs.getBoolean("target_ola", true));
        olaCb.setPadding(dp(8), dp(8), dp(8), dp(8));
        olaCb.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("target_ola", isChecked).apply();
        });
        card.addView(olaCb);

        return card;
    }
}
