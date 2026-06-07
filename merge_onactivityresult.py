import sys

filepath = r"D:\Desktop\Augusten\triplencse\triplencse\app\src\main\java\com\triplencse\acceptassist\MainActivity.java"
with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

target_duplicate = """    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001) {
            if ((RESULT_OK == resultCode) || (resultCode == 11)) {
                if (data != null) {
                    String trxt = data.getStringExtra("response");
                    if (trxt != null && trxt.toLowerCase().contains("status=success")) {
                        handlePaymentSuccess();
                    } else {
                        Toast.makeText(this, "Payment failed or cancelled.", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "Payment failed or cancelled.", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Payment failed or cancelled.", Toast.LENGTH_SHORT).show();
            }
        }
    }"""
content = content.replace(target_duplicate, "")

target_original = """    @Override
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
    }"""
    
replacement_original = """    @Override
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
        } else if (requestCode == 1001) {
            if ((RESULT_OK == resultCode) || (resultCode == 11)) {
                if (data != null) {
                    String trxt = data.getStringExtra("response");
                    if (trxt != null && trxt.toLowerCase().contains("status=success")) {
                        handlePaymentSuccess();
                    } else {
                        Toast.makeText(this, "Payment failed or cancelled.", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "Payment failed or cancelled.", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Payment failed or cancelled.", Toast.LENGTH_SHORT).show();
            }
        }
    }"""
content = content.replace(target_original, replacement_original)

with open(filepath, 'w', encoding='utf-8') as f:
    f.write(content)
