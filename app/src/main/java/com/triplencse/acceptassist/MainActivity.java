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
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.content.pm.PackageManager;

public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private CheckBox enabledBox;
    private EditText packageInput;
    private EditText textInput;
    private EditText delayInput;
    private TextView serviceStatus;
    private TextView packageHint;

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

        enabledBox = new CheckBox(this);
        enabledBox.setText("Enable auto-click");
        enabledBox.setTextSize(16);
        enabledBox.setChecked(prefs.getBoolean(AcceptPrefs.KEY_ENABLED, false));
        root.addView(enabledBox, matchWrapWithTop(18));

        packageHint = label("");
        root.addView(packageHint, matchWrapWithTop(14));
        packageInput = input(prefs.getString(AcceptPrefs.KEY_TARGET_PACKAGE, ""));
        packageInput.setSingleLine(true);
        root.addView(packageInput, matchWrapWithTop(6));

        root.addView(label("Button text or view id keywords, comma separated"), matchWrapWithTop(14));
        textInput = input(prefs.getString(AcceptPrefs.KEY_TARGET_TEXT, AcceptPrefs.DEFAULT_TARGET_TEXT));
        root.addView(textInput, matchWrapWithTop(6));

        root.addView(label("Click delay in milliseconds, 50 to 100"), matchWrapWithTop(14));
        delayInput = input(String.valueOf(prefs.getInt(AcceptPrefs.KEY_DELAY_MS, AcceptPrefs.DEFAULT_DELAY_MS)));
        delayInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        delayInput.setSingleLine(true);
        root.addView(delayInput, matchWrapWithTop(6));

        Button save = primaryButton("Save settings");
        save.setOnClickListener(v -> saveSettings());
        root.addView(save, matchWrapWithTop(16));

        Button fillSelf = secondaryButton("Use this app as test target");
        fillSelf.setOnClickListener(v -> {
            packageInput.setText(getPackageName());
            textInput.setText("Accept");
            saveSettings();
        });
        root.addView(fillSelf, matchWrapWithTop(10));

        Button testPopup = secondaryButton("Show test popup");
        testPopup.setOnClickListener(v -> showTestPopup());
        root.addView(testPopup, matchWrapWithTop(10));

        TextView footer = new TextView(this);
        footer.setText("Use the exact package name of your custom app, for example com.example.myapp. Android Accessibility must be enabled before clicking can work.");
        footer.setTextColor(Color.rgb(92, 101, 100));
        footer.setTextSize(13);
        footer.setPadding(0, dp(18), 0, 0);
        root.addView(footer, matchWrap());

        return scrollView;
    }

    private void saveSettings() {
        String packageName = packageInput.getText().toString().trim();
        String text = textInput.getText().toString().trim();
        int delay = parseDelay(delayInput.getText().toString());

        if (enabledBox.isChecked() && TextUtils.isEmpty(packageName)) {
            Toast.makeText(this, "Enter your custom app package name first", Toast.LENGTH_LONG).show();
            return;
        }
        if (TextUtils.isEmpty(text)) {
            text = AcceptPrefs.DEFAULT_TARGET_TEXT;
        }

        prefs.edit()
                .putBoolean(AcceptPrefs.KEY_ENABLED, enabledBox.isChecked())
                .putString(AcceptPrefs.KEY_TARGET_PACKAGE, packageName)
                .putString(AcceptPrefs.KEY_TARGET_TEXT, text)
                .putInt(AcceptPrefs.KEY_DELAY_MS, delay)
                .apply();
        delayInput.setText(String.valueOf(delay));
        refreshStatus();
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
    }

    private int parseDelay(String raw) {
        try {
            return AcceptPrefs.clampDelay(Integer.parseInt(raw.trim()));
        } catch (NumberFormatException ex) {
            return AcceptPrefs.DEFAULT_DELAY_MS;
        }
    }

    private void showTestPopup() {
        new AlertDialog.Builder(this)
                .setTitle("Test popup")
                .setMessage("If enabled and this package is configured, the service should click Accept after the configured delay.")
                .setPositiveButton("Accept", (dialog, which) -> Toast.makeText(this, "Accepted", Toast.LENGTH_SHORT).show())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void refreshStatus() {
        boolean serviceEnabled = isAccessibilityServiceEnabled();
        serviceStatus.setText(serviceEnabled ? "Accessibility service: enabled" : "Accessibility service: not enabled");
        serviceStatus.setTextColor(serviceEnabled ? Color.rgb(0, 106, 86) : Color.rgb(155, 72, 36));
        packageHint.setText("Target app package name");
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
