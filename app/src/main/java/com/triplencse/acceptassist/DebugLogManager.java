package com.triplencse.acceptassist;

import android.content.Context;
import android.content.SharedPreferences;
import android.app.AlertDialog;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DebugLogManager {
    private static final String PREF_NAME = "DebugLogsPref";
    private static final String KEY_LOGS = "logs";
    private static final int MAX_LOGS = 100;

    public static void log(Context context, String status, String reason) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String logsJson = prefs.getString(KEY_LOGS, "[]");
        try {
            JSONArray array = new JSONArray(logsJson);
            JSONObject newLog = new JSONObject();
            newLog.put("time", new SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
            newLog.put("status", status);
            newLog.put("reason", reason);

            JSONArray newArray = new JSONArray();
            newArray.put(newLog);
            for (int i = 0; i < Math.min(array.length(), MAX_LOGS - 1); i++) {
                newArray.put(array.getJSONObject(i));
            }
            prefs.edit().putString(KEY_LOGS, newArray.toString()).apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public static void showLogsDialog(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String logsJson = prefs.getString(KEY_LOGS, "[]");
        StringBuilder sb = new StringBuilder();
        try {
            JSONArray array = new JSONArray(logsJson);
            if (array.length() == 0) {
                sb.append("No logs available.");
            } else {
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    sb.append("[").append(obj.getString("time")).append("] ")
                      .append(obj.getString("status")).append("\n")
                      .append("Reason: ").append(obj.getString("reason")).append("\n\n");
                }
            }
        } catch (JSONException e) {
            sb.append("Error parsing logs.");
        }

        ScrollView scrollView = new ScrollView(context);
        TextView tv = new TextView(context);
        tv.setText(sb.toString());
        tv.setPadding(40, 40, 40, 40);
        tv.setTextSize(14);
        tv.setTextColor(android.graphics.Color.DKGRAY);
        scrollView.addView(tv);

        new AlertDialog.Builder(context)
            .setTitle("Debug Logs")
            .setView(scrollView)
            .setPositiveButton("Close", null)
            .setNegativeButton("Clear Logs", (dialog, which) -> {
                prefs.edit().putString(KEY_LOGS, "[]").apply();
            })
            .show();
    }
}
