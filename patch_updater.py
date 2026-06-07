import sys
import re

filepath = r"D:\Desktop\Augusten\triplencse\triplencse\app\src\main\java\com\triplencse\acceptassist\MainActivity.java"
with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Add updateRequired flag
content = content.replace(
    "    private LinearLayout customAppContainer;",
    "    private LinearLayout customAppContainer;\n    private boolean updateRequired = false;"
)

# 2. Update onCreate
target_on_create = """    @Override
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
    }"""
    
replacement_on_create = """    @Override
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
        
        setContentView(buildLoadingView("Checking for updates..."));
        checkAppUpdate();
    }
    
    private void checkAppUpdate() {
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
                    String downloadUrl = obj.optString("downloadUrl", "https://github.com/augusten6383/triplens.app/releases/latest");
                    
                    int currentVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
                    
                    if (currentVersion < latestVersion) {
                        updateRequired = true;
                        runOnUiThread(() -> setContentView(buildForceUpdateView(downloadUrl)));
                        return;
                    }
                }
            } catch (Exception e) {
                // Ignore update check failure
            }
            runOnUiThread(this::proceedToNormalStartup);
        }).start();
    }
    
    private void proceedToNormalStartup() {
        navigateToScreen();
    }"""

content = content.replace(target_on_create, replacement_on_create)

# 3. Update onResume
target_on_resume = """    @Override
    protected void onResume() {
        super.onResume();
        FirebaseUser currentUser = mAuth != null ? mAuth.getCurrentUser() : null;
        if (currentUser != null) {
            proceedNavigation();
        }
    }"""
    
replacement_on_resume = """    @Override
    protected void onResume() {
        super.onResume();
        if (updateRequired) return;
        
        FirebaseUser currentUser = mAuth != null ? mAuth.getCurrentUser() : null;
        if (currentUser != null) {
            proceedNavigation();
        }
    }"""

content = content.replace(target_on_resume, replacement_on_resume)

# 4. Add buildForceUpdateView at the bottom (before saveSettings)
target_save_settings = "    private void saveSettings() {"
replacement_save_settings = """    private View buildForceUpdateView(String url) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        root.setBackgroundColor(COLOR_BG);

        View updateIcon = systemIcon(android.R.drawable.stat_sys_download, Color.TRANSPARENT, 64, 8);
        updateIcon.setBackground(gradientRect(Color.parseColor("#3B82F6"), Color.parseColor("#06B6D4"), 32));
        root.addView(updateIcon);

        TextView title = new TextView(this);
        title.setText("Update Required");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(24), 0, dp(8));
        root.addView(title, matchWrap());

        TextView desc = new TextView(this);
        desc.setText("A new version of Triplens is available. You must update the app to continue.");
        desc.setTextColor(COLOR_TEXT_SECONDARY);
        desc.setTextSize(16);
        desc.setGravity(Gravity.CENTER);
        desc.setPadding(0, 0, 0, dp(32));
        root.addView(desc, matchWrap());

        Button updateBtn = new Button(this);
        updateBtn.setText("Update Now");
        updateBtn.setBackground(gradientRect(Color.parseColor("#10B981"), Color.parseColor("#047857"), 12));
        updateBtn.setTextColor(Color.WHITE);
        updateBtn.setAllCaps(false);
        updateBtn.setTextSize(18);
        updateBtn.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        updateBtn.setPadding(0, dp(14), 0, dp(14));
        updateBtn.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        });
        root.addView(updateBtn, matchWrap());

        return root;
    }

    private void saveSettings() {"""

content = content.replace(target_save_settings, replacement_save_settings)

with open(filepath, 'w', encoding='utf-8') as f:
    f.write(content)
