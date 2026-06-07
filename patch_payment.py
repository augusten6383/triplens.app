import sys
import re

filepath = r"D:\Desktop\Augusten\triplencse\triplencse\app\src\main\java\com\triplencse\acceptassist\MainActivity.java"
with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Update buildSubscriptionView plans
target_plans = """        // Card 1: 20 / Day
        root.addView(buildPlanCard("Day Pass", "₹20 / day", "Great for quick trials or temporary usage"), matchWrapWithTop(10));
        
        // Card 2: 99 / Week
        root.addView(buildPlanCard("Week Pass", "₹99 / week", "Perfect for regular weekly work schedules"), matchWrapWithTop(10));
        
        // Card 3: 299 / Month
        root.addView(buildPlanCard("Month Pass", "₹299 / month", "Best value. Unrestricted access for a full month"), matchWrapWithTop(10));"""

replacement_plans = """        // Card 1: 19 / Day
        root.addView(buildPlanCard("Day Pass", "₹19 / day", "Great for quick trials or temporary usage", 19.0, 1), matchWrapWithTop(10));
        
        // Card 2: 99 / Week
        root.addView(buildPlanCard("Week Pass", "₹99 / week", "Perfect for regular weekly work schedules", 99.0, 7), matchWrapWithTop(10));
        
        // Card 3: 299 / Month
        root.addView(buildPlanCard("Month Pass", "₹299 / month", "Best value. Unrestricted access for a full month", 299.0, 30), matchWrapWithTop(10));"""

content = content.replace(target_plans, replacement_plans)


# 2. Update buildPlanCard definition and click listener
target_build_card = """    private View buildPlanCard(String name, String price, String desc) {"""
replacement_build_card = """    private View buildPlanCard(String name, String price, String desc, double amount, int durationDays) {"""
content = content.replace(target_build_card, replacement_build_card)

target_card_click = """        card.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("Select " + name)
                .setMessage("In a production release, this would securely redirect to your Payment Gateway interface. For testing, please use the 'Demo: Activate Trial' button or the admin CLI tool.")
                .setPositiveButton("OK", null)
                .show();
        });"""

replacement_card_click = """        card.setOnClickListener(v -> {
            startUpiPayment(name, amount, durationDays);
        });"""
content = content.replace(target_card_click, replacement_card_click)

# 3. Add UPI methods at the bottom of the file (before saveSettings or just anywhere safe, let's inject before buildBlockedView)
target_blocked_view = "    private View buildBlockedView() {"
upi_methods = """    private void startUpiPayment(String planName, double amount, int durationDays) {
        String upiId = "vinithkannan2412@oksbi";
        String payeeName = "Triplens";
        String transactionNote = "Triplens " + planName;
        String amountStr = String.format(java.util.Locale.US, "%.2f", amount);

        android.net.Uri uri = android.net.Uri.parse("upi://pay").buildUpon()
                .appendQueryParameter("pa", upiId)
                .appendQueryParameter("pn", payeeName)
                .appendQueryParameter("tn", transactionNote)
                .appendQueryParameter("am", amountStr)
                .appendQueryParameter("cu", "INR")
                .build();

        Intent upiIntent = new Intent(Intent.ACTION_VIEW);
        upiIntent.setData(uri);

        Intent chooser = Intent.createChooser(upiIntent, "Pay with UPI");
        
        if (chooser.resolveActivity(getPackageManager()) != null) {
            prefs.edit().putInt("pending_payment_duration", durationDays).apply();
            startActivityForResult(chooser, 1001);
        } else {
            Toast.makeText(this, "No UPI app found on this device.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
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
    }

    private void handlePaymentSuccess() {
        int days = prefs.getInt("pending_payment_duration", 0);
        if (days <= 0) return;
        
        prefs.edit().putInt("pending_payment_duration", 0).apply();
        String loggedInUser = prefs.getString(AcceptPrefs.KEY_LOGGED_IN_USER, "");
        
        Toast.makeText(this, "Payment Successful! Activating plan...", Toast.LENGTH_LONG).show();
        
        TursoHelper.demoActivateSubscription(this, loggedInUser, days, new TursoHelper.Callback() {
            @Override
            public void onSuccess(org.json.JSONArray rows) {
                Toast.makeText(MainActivity.this, "Welcome to Premium! Your trial is now active.", Toast.LENGTH_LONG).show();
                proceedNavigation();
            }
            @Override
            public void onError(String message) {
                Toast.makeText(MainActivity.this, "Activation failed. Contact support with Screenshot.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private View buildBlockedView() {"""
content = content.replace(target_blocked_view, upi_methods)

with open(filepath, 'w', encoding='utf-8') as f:
    f.write(content)
