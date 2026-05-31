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
            checkPermissionsOnLaunch();
            refreshStatus();
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
            Toast.makeText(this, "Initializing database...", Toast.LENGTH_SHORT).show();
            TursoHelper.initDatabase(this, new TursoHelper.Callback() {
                @Override
                public void onSuccess(org.json.JSONArray rows) {
                    prefs.edit().putBoolean("db_initialized", true).apply();
                    Toast.makeText(MainActivity.this, "Database Initialized!", Toast.LENGTH_SHORT).show();
                    proceedNavigation();
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(MainActivity.this, "DB Initialization error: " + message, Toast.LENGTH_LONG).show();
                    setContentView(buildDbConfigView());
                }
            });
        } else {
            proceedNavigation();
        }
    }

    private void proceedNavigation() {
        String loggedInUser = prefs.getString(AcceptPrefs.KEY_LOGGED_IN_USER, "");
        if (!loggedInUser.isEmpty()) {
            setContentView(buildDashboardView());
            checkPermissionsOnLaunch();
            refreshStatus();
        } else {
            setContentView(buildLoginView());
        }
    }

    private View buildDbConfigView() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(30), dp(20), dp(30));
        root.setBackgroundColor(Color.rgb(245, 247, 246));
        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText("Database Configuration");
        title.setTextColor(Color.rgb(18, 23, 23));
        title.setTextSize(26);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(title, matchWrap());

        TextView desc = new TextView(this);
        desc.setText("Connect Accept Assist to your remote Turso / libSQL database.");
        desc.setTextColor(Color.rgb(81, 89, 88));
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
        root.setPadding(dp(20), dp(35), dp(20), dp(35));
        root.setBackgroundColor(Color.rgb(245, 247, 246));
        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText("Accept Assist");
        title.setTextColor(Color.rgb(18, 23, 23));
        title.setTextSize(30);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("Enter credentials to access auto-clicker settings");
        subtitle.setTextColor(Color.rgb(81, 89, 88));
        subtitle.setTextSize(14);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(4), 0, dp(30));
        root.addView(subtitle, matchWrap());

        root.addView(label("Username or Email"), matchWrap());
        EditText loginInput = input("");
        loginInput.setHint("Enter username or email");
        root.addView(loginInput, matchWrapWithTop(6));

        root.addView(label("Password"), matchWrapWithTop(16));
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
        root.addView(loginBtn, matchWrapWithTop(24));

        Button signUpLink = secondaryButton("Create New Account");
        signUpLink.setOnClickListener(v -> setContentView(buildSignUpView()));
        root.addView(signUpLink, matchWrapWithTop(12));

        Button forgotLink = secondaryButton("Forgot Password?");
        forgotLink.setOnClickListener(v -> setContentView(buildRecoveryView()));
        root.addView(forgotLink, matchWrapWithTop(6));

        Button dbSettingsBtn = secondaryButton("Database Connection Settings");
        dbSettingsBtn.setOnClickListener(v -> setContentView(buildDbConfigView()));
        root.addView(dbSettingsBtn, matchWrapWithTop(30));

        return scrollView;
    }

    private View buildSignUpView() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(25), dp(20), dp(25));
        root.setBackgroundColor(Color.rgb(245, 247, 246));
        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText("Create Account");
        title.setTextColor(Color.rgb(18, 23, 23));
        title.setTextSize(26);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(title, matchWrap());

        TextView desc = new TextView(this);
        desc.setText("Please register with unique details to continue");
        desc.setTextColor(Color.rgb(81, 89, 88));
        desc.setTextSize(14);
        desc.setPadding(0, dp(4), 0, dp(20));
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
        root.addView(registerBtn, matchWrapWithTop(24));

        Button backBtn = secondaryButton("Back to Log In");
        backBtn.setOnClickListener(v -> setContentView(buildLoginView()));
        root.addView(backBtn, matchWrapWithTop(12));

        return scrollView;
    }

    private View buildRecoveryView() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(30), dp(20), dp(30));
        root.setBackgroundColor(Color.rgb(245, 247, 246));
        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText("Recover Password");
        title.setTextColor(Color.rgb(18, 23, 23));
        title.setTextSize(26);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(title, matchWrap());

        TextView desc = new TextView(this);
        desc.setText("Verify your details to reset your password");
        desc.setTextColor(Color.rgb(81, 89, 88));
        desc.setTextSize(14);
        desc.setPadding(0, dp(4), 0, dp(20));
        root.addView(desc, matchWrap());

        root.addView(label("Username or Email"), matchWrap());
        EditText searchInput = input("");
        searchInput.setHint("Enter registered username or email");
        root.addView(searchInput, matchWrapWithTop(6));

        LinearLayout step2Container = new LinearLayout(this);
        step2Container.setOrientation(LinearLayout.VERTICAL);
        step2Container.setVisibility(View.GONE);

        TextView questionText = new TextView(this);
        questionText.setTextSize(15);
        questionText.setTextColor(Color.rgb(0, 106, 86));
        questionText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        
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
        step2Container.addView(resetBtn, matchWrapWithTop(24));

        Button backBtn = secondaryButton("Back to Log In");
        backBtn.setOnClickListener(v -> setContentView(buildLoginView()));
        root.addView(backBtn, matchWrapWithTop(12));

        return scrollView;
    }

    private View buildDashboardView() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(22), dp(20), dp(22));
        root.setBackgroundColor(Color.rgb(245, 247, 246));
        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText("Accept Assist");
        title.setTextColor(Color.rgb(18, 23, 23));
        title.setTextSize(28);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(title, matchWrap());

        String loggedInUser = prefs.getString(AcceptPrefs.KEY_LOGGED_IN_USER, "User");
        TextView userSessionText = new TextView(this);
        userSessionText.setText("Logged in as: " + loggedInUser);
        userSessionText.setTextColor(Color.rgb(0, 106, 86));
        userSessionText.setTextSize(14);
        userSessionText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        userSessionText.setPadding(0, dp(4), 0, dp(4));
        root.addView(userSessionText, matchWrap());

        Button logoutBtn = secondaryButton("Log Out");
        logoutBtn.setOnClickListener(v -> {
            prefs.edit().putString(AcceptPrefs.KEY_LOGGED_IN_USER, "").apply();
            Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
            navigateToScreen();
        });
        root.addView(logoutBtn, matchWrapWithTop(4));

        TextView subtitle = new TextView(this);
        subtitle.setText("Auto-clicks a matching button inside your configured custom app.");
        subtitle.setTextColor(Color.rgb(81, 89, 88));
        subtitle.setTextSize(15);
        subtitle.setPadding(0, dp(6), 0, dp(14));
        root.addView(subtitle, matchWrap());

        serviceStatus = statusText();
        root.addView(serviceStatus, matchWrap());

        Button accessibility = primaryButton("Open accessibility settings");
        accessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(accessibility, matchWrapWithTop(14));

        Button toggleServiceBtn = new Button(this);
        boolean isEnabled = prefs.getBoolean(AcceptPrefs.KEY_ENABLED, false);
        toggleServiceBtn.setText(isEnabled ? "Stop Auto-Clicker" : "Start Auto-Clicker");
        toggleServiceBtn.setBackgroundColor(isEnabled ? Color.rgb(200, 50, 50) : Color.rgb(11, 122, 117));
        toggleServiceBtn.setTextColor(Color.WHITE);
        toggleServiceBtn.setTextSize(18);
        toggleServiceBtn.setPadding(0, dp(16), 0, dp(16));
        toggleServiceBtn.setOnClickListener(v -> {
            boolean current = prefs.getBoolean(AcceptPrefs.KEY_ENABLED, false);
            boolean next = !current;
            prefs.edit().putBoolean(AcceptPrefs.KEY_ENABLED, next).apply();
            toggleServiceBtn.setText(next ? "Stop Auto-Clicker" : "Start Auto-Clicker");
            toggleServiceBtn.setBackgroundColor(next ? Color.rgb(200, 50, 50) : Color.rgb(11, 122, 117));
            Toast.makeText(this, next ? "Auto-Clicker Started" : "Auto-Clicker Stopped", Toast.LENGTH_SHORT).show();
        });
        root.addView(toggleServiceBtn, matchWrapWithTop(18));

        root.addView(label("Select App Mode"), matchWrapWithTop(18));
        
        appModeGroup = new RadioGroup(this);
        appModeGroup.setOrientation(RadioGroup.VERTICAL);
        
        radioRapido = new RadioButton(this);
        radioRapido.setText("Default App (Rapido Rider)");
        radioRapido.setTextSize(15);
        
        radioCustom = new RadioButton(this);
        radioCustom.setText("Other Apps");
        radioCustom.setTextSize(15);
        
        appModeGroup.addView(radioRapido);
        appModeGroup.addView(radioCustom);
        root.addView(appModeGroup, matchWrapWithTop(6));

        String currentMode = prefs.getString(AcceptPrefs.KEY_APP_MODE, "rapido");
        if ("custom".equals(currentMode)) {
            radioCustom.setChecked(true);
        } else {
            radioRapido.setChecked(true);
        }

        distanceFiltersContainer = new LinearLayout(this);
        distanceFiltersContainer.setOrientation(LinearLayout.VERTICAL);

        distanceFiltersContainer.addView(label("Minimum Pickup Distance (km)"), matchWrapWithTop(14));
        minPickupInput = input(String.valueOf(prefs.getFloat(AcceptPrefs.KEY_MIN_PICKUP, 0.0f)));
        minPickupInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        distanceFiltersContainer.addView(minPickupInput, matchWrapWithTop(6));

        distanceFiltersContainer.addView(label("Maximum Pickup Distance (km)"), matchWrapWithTop(14));
        maxPickupInput = input(String.valueOf(prefs.getFloat(AcceptPrefs.KEY_MAX_PICKUP, 5.0f)));
        maxPickupInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        distanceFiltersContainer.addView(maxPickupInput, matchWrapWithTop(6));

        distanceFiltersContainer.addView(label("Minimum Drop Distance (km)"), matchWrapWithTop(14));
        minDropInput = input(String.valueOf(prefs.getFloat(AcceptPrefs.KEY_MIN_DROP, 0.0f)));
        minDropInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        distanceFiltersContainer.addView(minDropInput, matchWrapWithTop(6));

        distanceFiltersContainer.addView(label("Maximum Drop Distance (km)"), matchWrapWithTop(14));
        maxDropInput = input(String.valueOf(prefs.getFloat(AcceptPrefs.KEY_MAX_DROP, 15.0f)));
        maxDropInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        distanceFiltersContainer.addView(maxDropInput, matchWrapWithTop(6));

        root.addView(distanceFiltersContainer, matchWrap());

        customAppContainer = new LinearLayout(this);
        customAppContainer.setOrientation(LinearLayout.VERTICAL);

        customAppContainer.addView(label("Custom App Package Name"), matchWrapWithTop(14));
        customPackageInput = input(prefs.getString(AcceptPrefs.KEY_CUSTOM_PACKAGE, ""));
        customPackageInput.setInputType(InputType.TYPE_CLASS_TEXT);
        customPackageInput.setHint("e.g. com.example.rider");
        customAppContainer.addView(customPackageInput, matchWrapWithTop(6));

        customAppContainer.addView(label("Custom Target Texts (comma separated)"), matchWrapWithTop(14));
        customTargetTextInput = input(prefs.getString(AcceptPrefs.KEY_CUSTOM_TARGET_TEXT, "Accept"));
        customTargetTextInput.setInputType(InputType.TYPE_CLASS_TEXT);
        customTargetTextInput.setHint("e.g. Accept,Click here,Go");
        customAppContainer.addView(customTargetTextInput, matchWrapWithTop(6));

        root.addView(customAppContainer, matchWrap());

        if ("custom".equals(currentMode)) {
            distanceFiltersContainer.setVisibility(View.GONE);
            customAppContainer.setVisibility(View.VISIBLE);
        } else {
            distanceFiltersContainer.setVisibility(View.VISIBLE);
            customAppContainer.setVisibility(View.GONE);
        }

        radioRapido.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                distanceFiltersContainer.setVisibility(View.VISIBLE);
                customAppContainer.setVisibility(View.GONE);
            }
        });

        radioCustom.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                distanceFiltersContainer.setVisibility(View.GONE);
                customAppContainer.setVisibility(View.VISIBLE);
            }
        });

        Button save = primaryButton("Save settings");
        save.setOnClickListener(v -> saveSettings());
        root.addView(save, matchWrapWithTop(16));

        Button dbSettingsBtn = secondaryButton("Database Configuration Settings");
        dbSettingsBtn.setOnClickListener(v -> setContentView(buildDbConfigView()));
        root.addView(dbSettingsBtn, matchWrapWithTop(12));

        TextView footer = new TextView(this);
        footer.setText("Auto-accepting for configured package. Distance limits are used only in default mode.");
        footer.setTextColor(Color.rgb(92, 101, 100));
        footer.setTextSize(13);
        footer.setPadding(0, dp(18), 0, 0);
        root.addView(footer, matchWrap());

        return scrollView;
    }

    private void saveSettings() {
        String mode = radioCustom.isChecked() ? "custom" : "rapido";
        String customPkg = customPackageInput.getText().toString().trim();
        String customTargetText = customTargetTextInput.getText().toString().trim();

        float minP = parseFloatSafely(minPickupInput.getText().toString());
        float maxP = parseFloatSafely(maxPickupInput.getText().toString());
        float minD = parseFloatSafely(minDropInput.getText().toString());
        float maxD = parseFloatSafely(maxDropInput.getText().toString());

        prefs.edit()
                .putString(AcceptPrefs.KEY_APP_MODE, mode)
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
        serviceStatus.setText(serviceEnabled ? "Accessibility service: enabled" : "Accessibility service: not enabled");
        serviceStatus.setTextColor(serviceEnabled ? Color.rgb(0, 106, 86) : Color.rgb(155, 72, 36));
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

    private TextView label(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.rgb(31, 38, 38));
        view.setTextSize(14);
        view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return view;
    }

    private EditText input(String value) {
        EditText editText = new EditText(this);
        editText.setText(value);
        editText.setTextSize(15);
        editText.setPadding(dp(12), dp(8), dp(12), dp(8));
        return editText;
    }

    private Button primaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(Color.rgb(11, 122, 117));
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        return button;
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
}
