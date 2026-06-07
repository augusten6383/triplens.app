import sys

filepath = r"D:\Desktop\Augusten\triplencse\triplencse\app\src\main\java\com\triplencse\acceptassist\MainActivity.java"

with open(filepath, 'r', encoding='utf-8') as f:
    lines = f.readlines()

# We want to replace lines 437 to 747 (1-indexed, so index 436 to 746)
new_login_view = """    private View buildLoginView() {
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
                Toast.makeText(this, "Google sign in failed", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        Toast.makeText(this, "Authenticating with Firebase...", Toast.LENGTH_SHORT).show();
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        proceedNavigation();
                    } else {
                        Toast.makeText(this, "Authentication Failed.", Toast.LENGTH_SHORT).show();
                    }
                });
    }
"""

# Replace the lines
new_lines = lines[:436] + [new_login_view] + lines[747:]

with open(filepath, 'w', encoding='utf-8') as f:
    f.writelines(new_lines)
