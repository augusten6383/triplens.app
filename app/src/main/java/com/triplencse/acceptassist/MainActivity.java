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
    private LinearLayout distanceFiltersContainer;
    private LinearLayout customAppContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(AcceptPrefs.NAME, MODE_PRIVATE);
        AcceptPrefs.ensureDefaults(prefs);
        setContentView(buildContent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkPermissionsOnLaunch();
        refreshStatus();
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

    private View buildContent() {
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

        TextView subtitle = new TextView(this);
        subtitle.setText("Auto-clicks a matching button inside your configured custom app.");
        subtitle.setTextColor(Color.rgb(81, 89, 88));
        subtitle.setTextSize(15);
        subtitle.setPadding(0, dp(6), 0, dp(18));
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

        // Add App Mode RadioGroup
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

        // Distance Filters Container
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

        // Custom App Container
        customAppContainer = new LinearLayout(this);
        customAppContainer.setOrientation(LinearLayout.VERTICAL);

        customAppContainer.addView(label("Custom App Package Name"), matchWrapWithTop(14));
        customPackageInput = input(prefs.getString(AcceptPrefs.KEY_CUSTOM_PACKAGE, ""));
        customPackageInput.setInputType(InputType.TYPE_CLASS_TEXT);
        customPackageInput.setHint("e.g. com.example.rider");
        customAppContainer.addView(customPackageInput, matchWrapWithTop(6));

        root.addView(customAppContainer, matchWrap());

        // Setup visibility and toggles
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

        float minP = parseFloatSafely(minPickupInput.getText().toString());
        float maxP = parseFloatSafely(maxPickupInput.getText().toString());
        float minD = parseFloatSafely(minDropInput.getText().toString());
        float maxD = parseFloatSafely(maxDropInput.getText().toString());

        prefs.edit()
                .putString(AcceptPrefs.KEY_APP_MODE, mode)
                .putString(AcceptPrefs.KEY_CUSTOM_PACKAGE, customPkg)
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
