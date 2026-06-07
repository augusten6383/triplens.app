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
    private static final int RC_SIGN_IN = 9001;
    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;
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
    private static final int COLOR_BG = Color.parseColor("#0F172A"); // Deep slate
    private static final int COLOR_CARD = Color.parseColor("#1E293B"); // Elevated card
    private static final int COLOR_INPUT_BG = Color.parseColor("#334155"); // Input background
    private static final int COLOR_BORDER = Color.parseColor("#475569"); // Subtle borders
    private static final int COLOR_ACCENT = Color.parseColor("#0EA5E9"); // Vibrant Cyan
    private static final int COLOR_TEXT_PRIMARY = Color.parseColor("#F8FAFC"); // Pure white
    private static final int COLOR_TEXT_SECONDARY = Color.parseColor("#94A3B8"); // Muted slate
    private static final int COLOR_DANGER = Color.parseColor("#EF4444"); // Glowing Red
    private static final int COLOR_WARNING = Color.parseColor("#F59E0B"); // Gold/Amber

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(getResources().getIdentifier("default_web_client_id", "string", getPackageName())))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
        prefs = getSharedPreferences(AcceptPrefs.NAME, MODE_PRIVATE);
        AcceptPrefs.ensureDefaults(prefs);
        navigateToScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        FirebaseUser currentUser = mAuth != null ? mAuth.getCurrentUser() : null;
        if (currentUser != null) {
            proceedNavigation();
        }
    }

    private void checkPermissionsOnLaunch() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                showPermissionDialog(
                    "Display Over Other Apps",
                    "We need 'Display over other apps' permission to show the auto-clicker buttons on top of other apps. Please find 'Triplens' in the list and enable it.",
                    new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))
                );
                return;
            }
        }

        if (!isAccessibilityServiceEnabled()) {
            showPermissionDialog(
                "Accessibility Service",
                "To automatically accept rides, we need Accessibility Service permission. Please find 'Triplens' in the Accessibility settings and turn it on.",
                new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            );
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                showPermissionDialog(
                    "Battery Optimization",
                    "To prevent the system from killing our auto-clicker in the background, please allow the app to ignore battery optimizations.",
                    new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()))
                );
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

    private void showPermissionDialog(String title, String message, Intent intent) {
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Go to Settings", (dialog, which) -> {
                startActivity(intent);
            })
            .show();
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
        FirebaseUser currentUser = mAuth != null ? mAuth.getCurrentUser() : null;
        if (currentUser != null) {
            String loggedInUser = currentUser.getEmail() != null ? currentUser.getEmail() : currentUser.getUid();
            prefs.edit().putString(AcceptPrefs.KEY_LOGGED_IN_USER, loggedInUser).apply();
            
            String deviceId = android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);

            setContentView(buildLoadingView("Verifying device and subscription..."));
            TursoHelper.verifyDeviceAndSubscription(this, loggedInUser, deviceId, new TursoHelper.Callback() {
                @Override
                public void onSuccess(org.json.JSONArray rows) {
                    if (rows != null && rows.length() > 0) {
                        try {
                            org.json.JSONArray firstRow = rows.getJSONArray(0);
                            String status = TursoHelper.getValueAsString(firstRow.getJSONObject(1));
                            String freeClicksStr = TursoHelper.getValueAsString(firstRow.getJSONObject(2));
                            String subExpiresStr = TursoHelper.getValueAsString(firstRow.getJSONObject(3));

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

        private View buildDeviceErrorView(String message) {
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
                Toast.makeText(this, "Please fill in all the details to continue.", Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit()
                    .putString(AcceptPrefs.KEY_TURSO_URL, url)
                    .putString(AcceptPrefs.KEY_TURSO_TOKEN, token)
                    .apply();

            // removed technical toast
            TursoHelper.initDatabase(this, new TursoHelper.Callback() {
                @Override
                public void onSuccess(org.json.JSONArray rows) {
                    prefs.edit().putBoolean("db_initialized", true).apply();
                    // removed technical toast
                    navigateToScreen();
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(MainActivity.this, "Unable to connect. Please check your credentials and try again.", Toast.LENGTH_LONG).show();
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
        subtitle.setText("Continue to access your dashboard");
        subtitle.setTextColor(COLOR_TEXT_SECONDARY);
        subtitle.setTextSize(15);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(6), 0, dp(36));
        root.addView(subtitle, matchWrap());

        Button googleBtn = primaryButton("Continue with Google");
        googleBtn.setOnClickListener(v -> {
            Intent signInIntent = mGoogleSignInClient.getSignInIntent();
            startActivityForResult(signInIntent, RC_SIGN_IN);
        });
        root.addView(googleBtn, matchWrapWithTop(16));

        return scrollView;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                firebaseAuthWithGoogle(account.getIdToken());
            } catch (ApiException e) {
                Toast.makeText(this, "Sign-in cancelled. Please try again.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        // removed technical toast
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        proceedNavigation();
                    } else {
                        Toast.makeText(this, "We couldn't verify your account right now. Please try again.", Toast.LENGTH_LONG).show();
                    }
                });
    }


    private android.graphics.drawable.GradientDrawable gradientRect(int startColor, int endColor, float radiusDp) {
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            new int[]{startColor, endColor});
        shape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        shape.setCornerRadius(dp((int) radiusDp));
        return shape;
    }

    private View buildDashboardView() {
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
        Toast.makeText(this, "Settings saved successfully", Toast.LENGTH_SHORT).show();
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
            if (mAuth != null) mAuth.signOut();
            if (mGoogleSignInClient != null) mGoogleSignInClient.signOut();
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
            // removed redundant toast
            TursoHelper.demoActivateSubscription(this, loggedInUser, 1, new TursoHelper.Callback() {
                @Override
                public void onSuccess(org.json.JSONArray rows) {
                    Toast.makeText(MainActivity.this, "Welcome to Premium! Your trial is now active.", Toast.LENGTH_LONG).show();
                    proceedNavigation();
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(MainActivity.this, "We couldn't activate your trial right now. Please try again later.", Toast.LENGTH_LONG).show();
                }
            });
        });
        root.addView(demoBtn, matchWrapWithTop(28));

        Button logoutBtn = textLinkButton("Log Out");
        logoutBtn.setOnClickListener(v -> {
            if (mAuth != null) mAuth.signOut();
            if (mGoogleSignInClient != null) mGoogleSignInClient.signOut();
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
