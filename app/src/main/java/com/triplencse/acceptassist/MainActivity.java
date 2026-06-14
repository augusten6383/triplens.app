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

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private EditText minPickupInput;
    private EditText maxPickupInput;
    private EditText minDropInput;
    private EditText maxDropInput;
    private EditText minPricePerKmInput;
    private EditText minPriceInput;
    private TextView serviceStatus;
    private RadioGroup appModeGroup;
    private RadioButton radioRapido;
    private RadioButton radioCustom;
    private EditText customPackageInput;
    private EditText customTargetTextInput;
    private LinearLayout distanceFiltersContainer;
    private LinearLayout customAppContainer;

    // Design Tokens - Premium Light Theme
    private static final int COLOR_BG = Color.rgb(248, 250, 252); // #F8FAFC
    private static final int COLOR_CARD = Color.rgb(255, 255, 255); // #FFFFFF
    private static final int COLOR_INPUT_BG = Color.rgb(241, 245, 249); // #F1F5F9
    private static final int COLOR_BORDER = Color.rgb(226, 232, 240); // #E2E8F0
    private static final int COLOR_ACCENT = Color.rgb(37, 99, 235); // Vibrant Blue for primary actions
    private static final int COLOR_SUCCESS = Color.rgb(16, 185, 129); // Emerald green for active states
    private static final int COLOR_TEXT_PRIMARY = Color.rgb(15, 23, 42); // Title / primary text
    private static final int COLOR_TEXT_SECONDARY = Color.rgb(100, 116, 139); // Subtitles / secondary text
    private static final int COLOR_DANGER = Color.rgb(239, 68, 68); // Soft red
    private static final int COLOR_WARNING = Color.rgb(245, 158, 11); // Soft orange

    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;
    private static final int RC_SIGN_IN = 9001;
    private boolean updateRequired = false;
    private boolean isCheckingUpdates = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        mAuth = FirebaseAuth.getInstance();
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        prefs = getSharedPreferences(AcceptPrefs.NAME, MODE_PRIVATE);
        AcceptPrefs.ensureDefaults(prefs);
        setContentView(buildLoadingView("Checking for updates..."));
        checkAppUpdate();
    }


    private void checkAppUpdate() {
        isCheckingUpdates = true;
        String updateUrl = "https://raw.githubusercontent.com/augusten6383/triplens.app/main/version.json";
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL(updateUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestMethod("GET");
                if (conn.getResponseCode() == 200) {
                    java.io.InputStream is = conn.getInputStream();
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is));
                    StringBuilder json = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) json.append(line);
                    reader.close();
                    
                    org.json.JSONObject obj = new org.json.JSONObject(json.toString());
                    int latestVersion = obj.optInt("latestVersion", 1);
                    int minimumSupportedVersion = obj.optInt("minimumSupportedVersion", 1);
                    String downloadUrl = obj.optString("downloadUrl", "https://github.com/augusten6383/triplens.app/releases/latest");
                    
                    int currentVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
                    
                    if (currentVersion < minimumSupportedVersion) {
                        updateRequired = true;
                        isCheckingUpdates = false;
                        runOnUiThread(() -> setContentView(buildForceUpdateView(downloadUrl)));
                        return;
                    } else if (currentVersion < latestVersion) {
                        isCheckingUpdates = false;
                        runOnUiThread(() -> {
                            new android.app.AlertDialog.Builder(MainActivity.this)
                                .setTitle("Update Available")
                                .setMessage("A new version of Triplens is available. Would you like to update now?")
                                .setPositiveButton("Update", (dialog, which) -> {
                                    startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(downloadUrl)));
                                })
                                .setNegativeButton("Later", (dialog, which) -> {
                                    proceedToNormalStartup();
                                })
                                .setCancelable(false)
                                .show();
                        });
                        return;
                    }
                }
            } catch (Exception e) {
                // Ignore update check failure
            }
            isCheckingUpdates = false;
            runOnUiThread(this::proceedToNormalStartup);
        }).start();
    }

    private void proceedToNormalStartup() {
        navigateToScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (updateRequired || isCheckingUpdates) return;
        
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

    private View circularIcon(String emoji, int bgColor, int sizeDp) {
        TextView tv = new TextView(this);
        tv.setText(emoji);
        tv.setTextSize(16);
        tv.setGravity(Gravity.CENTER);
        tv.setBackground(roundedRect(bgColor, sizeDp / 2f));
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp));
        tv.setLayoutParams(params);
        return tv;
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

    private View buildForceUpdateView(String downloadUrl) {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(40), dp(24), dp(40));
        root.setBackgroundColor(COLOR_BG);
        root.setGravity(Gravity.CENTER_VERTICAL);
        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText("Update Required");
        title.setTextColor(COLOR_DANGER);
        title.setTextSize(30);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView desc = new TextView(this);
        desc.setText("This version of the app is no longer supported. Please update to the latest version to continue using Triplens.");
        desc.setTextColor(COLOR_TEXT_SECONDARY);
        desc.setTextSize(16);
        desc.setGravity(Gravity.CENTER);
        desc.setPadding(0, dp(16), 0, dp(32));
        root.addView(desc, matchWrap());

        Button updateBtn = primaryButton("Update Now");
        updateBtn.setOnClickListener(v -> {
            startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(downloadUrl)));
        });
        root.addView(updateBtn, matchWrapWithTop(16));

        return scrollView;
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
        View view = getLayoutInflater().inflate(R.layout.activity_login, null);
        
        EditText emailEdit = view.findViewById(R.id.editEmail);
        EditText passEdit = view.findViewById(R.id.editPassword);
        com.google.android.material.button.MaterialButton loginBtn = view.findViewById(R.id.btnLogin);
        com.google.android.material.button.MaterialButton googleBtn = view.findViewById(R.id.btnGoogleLogin);
        TextView signUpBtn = view.findViewById(R.id.btnSignUp);
        TextView forgotPassBtn = view.findViewById(R.id.btnForgotPass);

        loginBtn.setOnClickListener(v -> {
            String email = emailEdit.getText().toString().trim();
            String pass = passEdit.getText().toString();
            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Please enter your email and password", Toast.LENGTH_SHORT).show();
                return;
            }

            Toast.makeText(this, "Logging in...", Toast.LENGTH_SHORT).show();
            mAuth.signInWithEmailAndPassword(email, pass)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            if (user.isEmailVerified()) {
                                TursoHelper.googleSignInUser(MainActivity.this, user.getEmail(), new TursoHelper.Callback() {
                                    @Override
                                    public void onSuccess(org.json.JSONArray rows) {
                                        try {
                                            prefs.edit().putString(AcceptPrefs.KEY_LOGGED_IN_USER, user.getEmail()).apply();
                                            Toast.makeText(MainActivity.this, "Welcome " + user.getEmail() + "!", Toast.LENGTH_SHORT).show();
                                            navigateToScreen();
                                        } catch (Exception ex) {
                                            Toast.makeText(MainActivity.this, "Login error", Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                    @Override
                                    public void onError(String message) {
                                        Toast.makeText(MainActivity.this, "Server sync failed: " + message, Toast.LENGTH_LONG).show();
                                    }
                                });
                            } else {
                                long lastResend = prefs.getLong("last_verification_resend", 0L);
                                long now = System.currentTimeMillis();
                                long cooldownMs = 60000; // 1 minute cooldown

                                new android.app.AlertDialog.Builder(MainActivity.this)
                                    .setTitle("Email Not Verified")
                                    .setMessage("Please verify your email address before logging in. Check your inbox or spam folder.")
                                    .setPositiveButton("Resend Email", (dialog, which) -> {
                                        if (now - lastResend < cooldownMs) {
                                            long waitSec = (cooldownMs - (now - lastResend)) / 1000;
                                            Toast.makeText(MainActivity.this, "Please wait " + waitSec + " seconds before resending.", Toast.LENGTH_SHORT).show();
                                            mAuth.signOut();
                                        } else {
                                            user.sendEmailVerification().addOnCompleteListener(t -> {
                                                Toast.makeText(MainActivity.this, "Verification email resent! Please check your inbox.", Toast.LENGTH_LONG).show();
                                                prefs.edit().putLong("last_verification_resend", System.currentTimeMillis()).apply();
                                                mAuth.signOut();
                                            });
                                        }
                                    })
                                    .setNegativeButton("Cancel", (dialog, which) -> mAuth.signOut())
                                    .setCancelable(false)
                                    .show();
                            }
                        }
                    } else {
                        Toast.makeText(this, "Login failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
        });

        signUpBtn.setOnClickListener(v -> setContentView(buildSignUpView()));
        forgotPassBtn.setOnClickListener(v -> setContentView(buildRecoveryView()));
        
        googleBtn.setOnClickListener(v -> {
            Intent signInIntent = mGoogleSignInClient.getSignInIntent();
            startActivityForResult(signInIntent, RC_SIGN_IN);
        });

        return view;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                firebaseAuthWithGoogle(account.getIdToken());
            } catch (ApiException e) {
                Toast.makeText(this, "Google sign in failed", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null && user.getEmail() != null) {
                            String email = user.getEmail();
                            Toast.makeText(MainActivity.this, "Google Sign-In success. Logging into Turso...", Toast.LENGTH_SHORT).show();
                            TursoHelper.googleSignInUser(MainActivity.this, email, new TursoHelper.Callback() {
                                @Override
                                public void onSuccess(org.json.JSONArray rows) {
                                    try {
                                        org.json.JSONArray firstRow = rows.getJSONArray(0);
                                        String username = TursoHelper.getValueAsString(firstRow.getJSONObject(0));
                                        prefs.edit().putString(AcceptPrefs.KEY_LOGGED_IN_USER, username).apply();
                                        Toast.makeText(MainActivity.this, "Welcome " + username + "!", Toast.LENGTH_SHORT).show();
                                        navigateToScreen();
                                    } catch (Exception ex) {
                                        Toast.makeText(MainActivity.this, "Error completing Google login: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
                                    }
                                }

                                @Override
                                public void onError(String message) {
                                    Toast.makeText(MainActivity.this, "Turso login failed: " + message, Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    } else {
                        Toast.makeText(MainActivity.this, "Firebase Authentication failed.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private View buildSignUpView() {
        View view = getLayoutInflater().inflate(R.layout.activity_signup, null);

        EditText emailEdit = view.findViewById(R.id.editSignUpEmail);
        EditText passEdit = view.findViewById(R.id.editSignUpPassword);
        com.google.android.material.button.MaterialButton submitBtn = view.findViewById(R.id.btnSignUpSubmit);
        TextView backBtn = view.findViewById(R.id.btnBackToLogin);

        submitBtn.setOnClickListener(v -> {
            String email = emailEdit.getText().toString().trim();
            String pass = passEdit.getText().toString();

            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Email and password are required", Toast.LENGTH_SHORT).show();
                return;
            }

            Toast.makeText(this, "Creating account...", Toast.LENGTH_SHORT).show();
            mAuth.createUserWithEmailAndPassword(email, pass)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            user.sendEmailVerification()
                                .addOnCompleteListener(t -> {
                                    new android.app.AlertDialog.Builder(MainActivity.this)
                                        .setTitle("Verification Email Sent")
                                        .setMessage("A verification link has been sent to your email.\n\nPlease check your spam folder if not found in your inbox.")
                                        .setPositiveButton("OK", (dialog, which) -> {
                                            mAuth.signOut();
                                            setContentView(buildLoginView());
                                        })
                                        .setCancelable(false)
                                        .show();
                                });
                        }
                    } else {
                        Toast.makeText(this, "Sign Up Failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
        });

        backBtn.setOnClickListener(v -> setContentView(buildLoginView()));

        return view;
    }

    private View buildRecoveryView() {
        View view = getLayoutInflater().inflate(R.layout.activity_recovery, null);

        EditText emailEdit = view.findViewById(R.id.editRecoveryEmail);
        com.google.android.material.button.MaterialButton submitBtn = view.findViewById(R.id.btnRecoverySubmit);
        TextView backBtn = view.findViewById(R.id.btnRecoveryBack);

        submitBtn.setOnClickListener(v -> {
            String email = emailEdit.getText().toString().trim();
            if (email.isEmpty()) {
                Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show();
                return;
            }

            Toast.makeText(this, "Sending reset link...", Toast.LENGTH_SHORT).show();
            mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(MainActivity.this, "Reset link sent to your email.", Toast.LENGTH_LONG).show();
                        setContentView(buildLoginView());
                    } else {
                        Toast.makeText(MainActivity.this, "Failed to send reset link: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
        });

        backBtn.setOnClickListener(v -> setContentView(buildLoginView()));

        return view;
    }

    private View buildDashboardView() {
        View view = getLayoutInflater().inflate(R.layout.activity_main, null);

        com.google.android.material.button.MaterialButton toggleBtn = view.findViewById(R.id.btnToggleClicker);
        TextView subStatus = view.findViewById(R.id.textSubStatus);
        TextView subExpires = view.findViewById(R.id.textSubExpires);
        TextView tabDefault = view.findViewById(R.id.tabDefaultApp);
        TextView tabOther = view.findViewById(R.id.tabOtherApp);
        LinearLayout filtersContainer = view.findViewById(R.id.filtersContainer);
        com.google.android.material.button.MaterialButton saveBtn = view.findViewById(R.id.btnSaveSettings);

        boolean isEnabled = prefs.getBoolean(AcceptPrefs.KEY_ENABLED, false);
        toggleBtn.setText(isEnabled ? "Stop Autoclicker" : "Start Autoclicker");
        toggleBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(isEnabled ? COLOR_DANGER : COLOR_ACCENT));

        toggleBtn.setOnClickListener(v -> {
            boolean current = prefs.getBoolean(AcceptPrefs.KEY_ENABLED, false);
            prefs.edit().putBoolean(AcceptPrefs.KEY_ENABLED, !current).apply();
            Toast.makeText(this, !current ? "Auto-Clicker Started" : "Auto-Clicker Stopped", Toast.LENGTH_SHORT).show();
            setContentView(buildDashboardView());
        });

        // App Mode logic
        String currentMode = prefs.getString(AcceptPrefs.KEY_APP_MODE, "rapido");
        if ("custom".equals(currentMode)) {
            tabOther.setBackgroundResource(R.drawable.bg_button_primary);
            tabOther.setBackgroundTintList(android.content.res.ColorStateList.valueOf(COLOR_SUCCESS));
            tabOther.setTextColor(COLOR_BG);
            tabDefault.setBackground(null);
            tabDefault.setTextColor(COLOR_TEXT_SECONDARY);
            filtersContainer.addView(buildCustomAppSettingsCard());
        } else {
            tabDefault.setBackgroundResource(R.drawable.bg_button_primary);
            tabDefault.setBackgroundTintList(android.content.res.ColorStateList.valueOf(COLOR_SUCCESS));
            tabDefault.setTextColor(COLOR_BG);
            tabOther.setBackground(null);
            tabOther.setTextColor(COLOR_TEXT_SECONDARY);
            filtersContainer.addView(buildFiltersCard());
        }

        tabDefault.setOnClickListener(v -> {
            prefs.edit().putString(AcceptPrefs.KEY_APP_MODE, "rapido").apply();
            setContentView(buildDashboardView());
        });

        tabOther.setOnClickListener(v -> {
            prefs.edit().putString(AcceptPrefs.KEY_APP_MODE, "custom").apply();
            setContentView(buildDashboardView());
        });

        saveBtn.setOnClickListener(v -> saveSettings());

        // Fill sub info
        long expires = prefs.getLong(AcceptPrefs.KEY_SUB_EXPIRES, 0L);
        subExpires.setText("Expires: " + formatExpiry(expires));

        LinearLayout navProfile = view.findViewById(R.id.navProfile);
        if (navProfile != null) {
            navProfile.setOnClickListener(v -> setContentView(buildProfileView()));
        }

        return view;
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
        
        // Glowing app icon
        View appIcon = circularIcon("🖱️", Color.rgb(8, 145, 178), 44);
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

        View crownIcon = circularIcon("👑", Color.rgb(217, 119, 6), 32);
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

        View logoutIcon = circularIcon("🚪", Color.rgb(6, 95, 70), 32);
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

        View infoIcon = circularIcon("ℹ️", Color.rgb(30, 58, 138), 32);
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

        View statusIcon = circularIcon(serviceEnabled ? "🔓" : "🔒", serviceEnabled ? Color.rgb(6, 95, 70) : Color.rgb(153, 27, 27), 36);
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
        toggle.setClickable(false); // Handle through row click
        row.addView(toggle);

        row.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });

        return row;
    }

    private View buildActionButtonsRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);

        boolean isEnabled = prefs.getBoolean(AcceptPrefs.KEY_ENABLED, false);

        LinearLayout toggleBtn = new LinearLayout(this);
        toggleBtn.setOrientation(LinearLayout.HORIZONTAL);
        toggleBtn.setGravity(Gravity.CENTER);
        toggleBtn.setBackground(roundedRect(isEnabled ? COLOR_DANGER : COLOR_ACCENT, 16));
        toggleBtn.setPadding(dp(20), dp(20), dp(20), dp(20));

        View playIcon = circularIcon(isEnabled ? "⏹️" : "▶️", Color.TRANSPARENT, 24);
        toggleBtn.addView(playIcon);

        TextView toggleText = new TextView(this);
        toggleText.setText(isEnabled ? "Stop Autoclicker" : "Start Autoclicker");
        toggleText.setTextColor(Color.WHITE);
        toggleText.setTextSize(18);
        toggleText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        toggleText.setPadding(dp(12), 0, 0, 0);
        toggleBtn.addView(toggleText);

        toggleBtn.setOnClickListener(v -> {
            boolean current = prefs.getBoolean(AcceptPrefs.KEY_ENABLED, false);
            prefs.edit().putBoolean(AcceptPrefs.KEY_ENABLED, !current).apply();
            Toast.makeText(this, !current ? "Auto-Clicker Started" : "Auto-Clicker Stopped", Toast.LENGTH_SHORT).show();
            setContentView(buildDashboardView());
        });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        row.addView(toggleBtn, params);
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

        TextView icon = new TextView(this);
        icon.setText("👤");
        icon.setTextSize(16);
        header.addView(icon);

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
        
        TextView radioDot1 = new TextView(this);
        radioDot1.setText(isRapidoSelected ? "🔘" : "⚪");
        radioDot1.setTextSize(16);
        rapidoOpt.addView(radioDot1);

        View rapidoIcon = circularIcon("🏍️", Color.rgb(6, 95, 70), 28);
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

        TextView radioDot2 = new TextView(this);
        radioDot2.setText(!isRapidoSelected ? "🔘" : "⚪");
        radioDot2.setTextSize(16);
        customOpt.addView(radioDot2);

        View customIcon = circularIcon("📱", Color.rgb(30, 41, 59), 28);
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

    private View buildFiltersCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundedRect(COLOR_CARD, 24));
        card.setPadding(dp(20), dp(20), dp(20), dp(20));

        TextView title = new TextView(this);
        title.setText("Smart Filters");
        title.setTextColor(COLOR_TEXT_SECONDARY);
        title.setTextSize(13);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, dp(16));
        card.addView(title);

        // Toggles
        card.addView(buildCheckboxRow("Require Distance AND Price", AcceptPrefs.KEY_TOGGLE_DIST_PRICE_AND, false, true));
        card.addView(buildCheckboxRow("Pass ALL Price Rules", AcceptPrefs.KEY_TOGGLE_PRICE_AND, false, true));

        // Separator
        View sep = new View(this);
        sep.setBackgroundColor(COLOR_BORDER);
        LinearLayout.LayoutParams sepParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        sepParams.topMargin = dp(16);
        sepParams.bottomMargin = dp(16);
        card.addView(sep, sepParams);

        boolean hasAny = false;
        
        minPickupInput = null;
        maxPickupInput = null;
        minDropInput = null;
        maxDropInput = null;
        minPricePerKmInput = null;
        minPriceInput = null;

        if (prefs.getBoolean(AcceptPrefs.KEY_FILTER_MAX_PICKUP_ACTIVE, false)) {
            hasAny = true;
            maxPickupInput = new EditText(this);
            maxPickupInput.setText(String.valueOf(prefs.getFloat(AcceptPrefs.KEY_MAX_PICKUP, 5.0f)));
            maxPickupInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            card.addView(buildRemovableSettingRow("Max Pickup (km)", maxPickupInput, AcceptPrefs.KEY_FILTER_MAX_PICKUP_ACTIVE));
        }

        if (prefs.getBoolean(AcceptPrefs.KEY_FILTER_DROP_ACTIVE, false)) {
            hasAny = true;
            minDropInput = new EditText(this);
            minDropInput.setText(String.valueOf(prefs.getFloat(AcceptPrefs.KEY_MIN_DROP, 0.0f)));
            minDropInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            card.addView(buildSettingRow("Min Drop (km)", "", minDropInput));

            maxDropInput = new EditText(this);
            maxDropInput.setText(String.valueOf(prefs.getFloat(AcceptPrefs.KEY_MAX_DROP, 15.0f)));
            maxDropInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            card.addView(buildRemovableSettingRow("Max Drop (km)", maxDropInput, AcceptPrefs.KEY_FILTER_DROP_ACTIVE));
        }

        if (prefs.getBoolean(AcceptPrefs.KEY_FILTER_PRICE_KM_ACTIVE, false)) {
            hasAny = true;
            minPricePerKmInput = new EditText(this);
            minPricePerKmInput.setText(String.valueOf(prefs.getFloat(AcceptPrefs.KEY_MIN_PRICE_PER_KM, 0.0f)));
            minPricePerKmInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            card.addView(buildRemovableSettingRow("Min Price/Km", minPricePerKmInput, AcceptPrefs.KEY_FILTER_PRICE_KM_ACTIVE));
        }

        if (prefs.getBoolean(AcceptPrefs.KEY_FILTER_TOTAL_PRICE_ACTIVE, false)) {
            hasAny = true;
            minPriceInput = new EditText(this);
            minPriceInput.setText(String.valueOf(prefs.getFloat(AcceptPrefs.KEY_MIN_PRICE, 0.0f)));
            minPriceInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            card.addView(buildRemovableSettingRow("Min Total Price", minPriceInput, AcceptPrefs.KEY_FILTER_TOTAL_PRICE_ACTIVE));
        }

        if (!hasAny) {
            TextView noFilters = new TextView(this);
            noFilters.setText("No filters added. Accepting all orders.");
            noFilters.setTextColor(Color.parseColor("#10B981"));
            noFilters.setTextSize(14);
            noFilters.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            noFilters.setPadding(0, 0, 0, dp(16));
            card.addView(noFilters);
        }

        // Add Filter Button
        Button addFilterBtn = new Button(this);
        addFilterBtn.setText("+ Add Filter");
        addFilterBtn.setBackground(roundedRect(COLOR_ACCENT, 12));
        addFilterBtn.setTextColor(Color.WHITE);
        addFilterBtn.setAllCaps(false);
        addFilterBtn.setOnClickListener(v -> showAddFilterDialog());
        
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnParams.topMargin = dp(8);
        card.addView(addFilterBtn, btnParams);

        return card;
    }

    private View buildRemovableSettingRow(String title, EditText inputField, String prefKey) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(COLOR_TEXT_PRIMARY);
        titleView.setTextSize(15);
        titleView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        titleParams.rightMargin = dp(8);
        row.addView(titleView, titleParams);

        inputField.setGravity(Gravity.CENTER);
        inputField.setTextSize(16);
        inputField.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        inputField.setTextColor(COLOR_ACCENT);
        inputField.setPadding(dp(16), dp(10), dp(16), dp(10));
        inputField.setBackground(roundedRectWithBorder(COLOR_INPUT_BG, 8, COLOR_BORDER, 1));
        
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(dp(90), LinearLayout.LayoutParams.WRAP_CONTENT);
        row.addView(inputField, inputParams);

        TextView removeBtn = new TextView(this);
        removeBtn.setText("✕");
        removeBtn.setTextColor(COLOR_DANGER);
        removeBtn.setTextSize(18);
        removeBtn.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        removeBtn.setPadding(dp(16), dp(8), dp(8), dp(8));
        removeBtn.setOnClickListener(v -> {
            saveSettings();
            prefs.edit().putBoolean(prefKey, false).apply();
            setContentView(buildDashboardView());
        });
        row.addView(removeBtn);

        return row;
    }

    private void showAddFilterDialog() {
        java.util.List<String> optionsList = new java.util.ArrayList<>();
        java.util.List<String> keysList = new java.util.ArrayList<>();

        if (!prefs.getBoolean(AcceptPrefs.KEY_FILTER_MAX_PICKUP_ACTIVE, false)) {
            optionsList.add("Max Pickup Distance");
            keysList.add(AcceptPrefs.KEY_FILTER_MAX_PICKUP_ACTIVE);
        }
        if (!prefs.getBoolean(AcceptPrefs.KEY_FILTER_DROP_ACTIVE, false)) {
            optionsList.add("Min & Max Drop Distance");
            keysList.add(AcceptPrefs.KEY_FILTER_DROP_ACTIVE);
        }
        if (!prefs.getBoolean(AcceptPrefs.KEY_FILTER_PRICE_KM_ACTIVE, false)) {
            optionsList.add("Min Price Per Km");
            keysList.add(AcceptPrefs.KEY_FILTER_PRICE_KM_ACTIVE);
        }
        if (!prefs.getBoolean(AcceptPrefs.KEY_FILTER_TOTAL_PRICE_ACTIVE, false)) {
            optionsList.add("Min Total Price");
            keysList.add(AcceptPrefs.KEY_FILTER_TOTAL_PRICE_ACTIVE);
        }

        if (optionsList.isEmpty()) {
            Toast.makeText(this, "All filters are already added!", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] options = optionsList.toArray(new String[0]);
        
        new android.app.AlertDialog.Builder(this)
            .setTitle("Add a Filter")
            .setItems(options, (dialog, which) -> {
                saveSettings();
                prefs.edit().putBoolean(keysList.get(which), true).apply();
                setContentView(buildDashboardView());
            })
            .show();
    }

    private View buildCheckboxRow(String label, String prefKey, boolean defVal, boolean isEnabled) {
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
        toggle.setChecked(isEnabled && prefs.getBoolean(prefKey, defVal));
        toggle.setEnabled(isEnabled);
        toggle.setOnCheckedChangeListener((btn, isChecked) -> prefs.edit().putBoolean(prefKey, isChecked).apply());
        row.addView(toggle);
        
        if (!isEnabled) {
            txt.setTextColor(COLOR_TEXT_SECONDARY);
            toggle.setAlpha(0.5f);
        }

        return row;
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

    private void saveSettings() {
        String customPkg = (customPackageInput != null) ? customPackageInput.getText().toString().trim() : prefs.getString(AcceptPrefs.KEY_CUSTOM_PACKAGE, "");
        String customTargetText = (customTargetTextInput != null) ? customTargetTextInput.getText().toString().trim() : prefs.getString(AcceptPrefs.KEY_CUSTOM_TARGET_TEXT, "Accept");

        float minP = (minPickupInput != null) ? parseFloatSafely(minPickupInput.getText().toString()) : prefs.getFloat(AcceptPrefs.KEY_MIN_PICKUP, 0.0f);
        float maxP = (maxPickupInput != null) ? parseFloatSafely(maxPickupInput.getText().toString()) : prefs.getFloat(AcceptPrefs.KEY_MAX_PICKUP, 5.0f);
        float minD = (minDropInput != null) ? parseFloatSafely(minDropInput.getText().toString()) : prefs.getFloat(AcceptPrefs.KEY_MIN_DROP, 0.0f);
        float maxD = (maxDropInput != null) ? parseFloatSafely(maxDropInput.getText().toString()) : prefs.getFloat(AcceptPrefs.KEY_MAX_DROP, 15.0f);
        float minPriceKm = (minPricePerKmInput != null) ? parseFloatSafely(minPricePerKmInput.getText().toString()) : prefs.getFloat(AcceptPrefs.KEY_MIN_PRICE_PER_KM, 0.0f);
        float minTotalP = (minPriceInput != null) ? parseFloatSafely(minPriceInput.getText().toString()) : prefs.getFloat(AcceptPrefs.KEY_MIN_PRICE, 0.0f);

        prefs.edit()
                .putString(AcceptPrefs.KEY_CUSTOM_PACKAGE, customPkg)
                .putString(AcceptPrefs.KEY_CUSTOM_TARGET_TEXT, customTargetText)
                .putFloat(AcceptPrefs.KEY_MIN_PICKUP, minP)
                .putFloat(AcceptPrefs.KEY_MAX_PICKUP, maxP)
                .putFloat(AcceptPrefs.KEY_MIN_DROP, minD)
                .putFloat(AcceptPrefs.KEY_MAX_DROP, maxD)
                .putFloat(AcceptPrefs.KEY_MIN_PRICE_PER_KM, minPriceKm)
                .putFloat(AcceptPrefs.KEY_MIN_PRICE, minTotalP)
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

    private View buildProfileView() {
        View view = getLayoutInflater().inflate(R.layout.activity_profile, null);

        TextView userTxt = view.findViewById(R.id.textProfileUser);
        TextView subStatus = view.findViewById(R.id.textProfileSubStatus);
        TextView accStatus = view.findViewById(R.id.textProfileAccStatus);
        
        String loggedInUser = prefs.getString(AcceptPrefs.KEY_LOGGED_IN_USER, "User");
        userTxt.setText(loggedInUser);

        long expires = prefs.getLong(AcceptPrefs.KEY_SUB_EXPIRES, 0L);
        if (expires > (System.currentTimeMillis() / 1000L)) {
            subStatus.setText("Active Premium");
        } else {
            subStatus.setText("Free / Expired");
        }

        if (isAccessibilityServiceEnabled()) {
            accStatus.setText("Service Active");
        } else {
            accStatus.setText("Tap to enable in Settings");
        }

        view.findViewById(R.id.cardSubscription).setOnClickListener(v -> setContentView(buildSubscriptionView()));
        view.findViewById(R.id.cardAccessibility).setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        view.findViewById(R.id.cardLogout).setOnClickListener(v -> {
            prefs.edit().putString(AcceptPrefs.KEY_LOGGED_IN_USER, "").apply();
            Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
            navigateToScreen();
        });

        LinearLayout navDash = view.findViewById(R.id.navDashboard);
        if (navDash != null) {
            navDash.setOnClickListener(v -> setContentView(buildDashboardView()));
        }

        return view;
    }

    private View buildSubscriptionView() {
        View view = getLayoutInflater().inflate(R.layout.activity_subscription, null);

        TextView daysLeft = view.findViewById(R.id.textDaysLeft);
        TextView subMsg = view.findViewById(R.id.textSubMsg);

        long expires = prefs.getLong(AcceptPrefs.KEY_SUB_EXPIRES, 0L);
        long now = System.currentTimeMillis() / 1000L;
        if (expires > now) {
            long diff = expires - now;
            long days = (long) Math.ceil((double) diff / (60 * 60 * 24));
            daysLeft.setText(String.valueOf(days));
            subMsg.setText("Your Pro subscription is active.\nAutomation logs are being processed in real-time.");
        } else {
            daysLeft.setText("0");
            subMsg.setText("Your subscription has expired.\nPlease renew to continue auto-accepting.");
        }

        View.OnClickListener planClickListener = v -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://qubesolutions.vercel.app/triplens/"));
            startActivity(browserIntent);
        };
        
        View btnPlanDaily = view.findViewById(R.id.btnPlanDaily);
        if (btnPlanDaily != null) btnPlanDaily.setOnClickListener(planClickListener);
        
        View btnPlanWeekly = view.findViewById(R.id.btnPlanWeekly);
        if (btnPlanWeekly != null) btnPlanWeekly.setOnClickListener(planClickListener);
        
        View btnPlanMonthly = view.findViewById(R.id.btnPlanMonthly);
        if (btnPlanMonthly != null) btnPlanMonthly.setOnClickListener(planClickListener);

        LinearLayout navDash = view.findViewById(R.id.navDashboard);
        if (navDash != null) {
            navDash.setOnClickListener(v -> setContentView(buildDashboardView()));
        }
        LinearLayout navProf = view.findViewById(R.id.navProfile);
        if (navProf != null) {
            navProf.setOnClickListener(v -> setContentView(buildProfileView()));
        }

        return view;
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
            android.content.Intent browserIntent = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://qubesolutions.vercel.app/triplens/"));
            startActivity(browserIntent);
        });

        return card;
    }
}
