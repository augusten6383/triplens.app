import sys

filepath = r"D:\Desktop\Augusten\triplencse\triplencse\app\src\main\java\com\triplencse\acceptassist\MainActivity.java"

with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Imports
content = content.replace(
    "import android.content.pm.PackageManager;",
    """import android.content.pm.PackageManager;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;"""
)

# 2. Variables
content = content.replace(
    "public class MainActivity extends Activity {\n    private SharedPreferences prefs;",
    """public class MainActivity extends Activity {
    private static final int RC_SIGN_IN = 9001;
    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;
    private SharedPreferences prefs;"""
)

# 3. onCreate
content = content.replace(
    """    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(AcceptPrefs.NAME, MODE_PRIVATE);
        AcceptPrefs.ensureDefaults(prefs);
        navigateToScreen();
    }""",
    """    @Override
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
)

# 4. onResume
content = content.replace(
    """    @Override
    protected void onResume() {
        super.onResume();
        String loggedInUser = prefs.getString(AcceptPrefs.KEY_LOGGED_IN_USER, "");
        if (!loggedInUser.isEmpty()) {
            proceedNavigation();
        }
    }""",
    """    @Override
    protected void onResume() {
        super.onResume();
        FirebaseUser currentUser = mAuth != null ? mAuth.getCurrentUser() : null;
        if (currentUser != null) {
            proceedNavigation();
        }
    }"""
)

# 5. proceedNavigation
content = content.replace(
    """    private void proceedNavigation() {
        String loggedInUser = prefs.getString(AcceptPrefs.KEY_LOGGED_IN_USER, "");
        if (!loggedInUser.isEmpty()) {
            setContentView(buildLoadingView("Checking subscription status..."));""",
    """    private void proceedNavigation() {
        FirebaseUser currentUser = mAuth != null ? mAuth.getCurrentUser() : null;
        if (currentUser != null) {
            String loggedInUser = currentUser.getEmail() != null ? currentUser.getEmail() : currentUser.getUid();
            prefs.edit().putString(AcceptPrefs.KEY_LOGGED_IN_USER, loggedInUser).apply();
            
            setContentView(buildLoadingView("Checking subscription status..."));"""
)

# 6. Logout logics
content = content.replace(
    """prefs.edit().putString(AcceptPrefs.KEY_LOGGED_IN_USER, "").apply();""",
    """if (mAuth != null) mAuth.signOut();
            if (mGoogleSignInClient != null) mGoogleSignInClient.signOut();
            prefs.edit().putString(AcceptPrefs.KEY_LOGGED_IN_USER, "").apply();"""
)

with open(filepath, 'w', encoding='utf-8') as f:
    f.write(content)
