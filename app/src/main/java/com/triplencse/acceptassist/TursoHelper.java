package com.triplencse.acceptassist;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class TursoHelper {

    public interface Callback {
        void onSuccess(JSONArray rows);
        void onError(String message);
    }

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    private TursoHelper() {
    }

    private static String getPipelineUrl(String rawUrl) {
        if (rawUrl == null) return "";
        String url = rawUrl.trim();
        if (url.startsWith("libsql://")) {
            url = "https://" + url.substring(9);
        }
        if (!url.endsWith("/v2/pipeline")) {
            if (url.endsWith("/")) {
                url = url + "v2/pipeline";
            } else {
                url = url + "/v2/pipeline";
            }
        }
        return url;
    }

    public static String getValueAsString(JSONObject valObj) {
        if (valObj == null) return null;
        if (valObj.has("value") && !valObj.isNull("value")) {
            try {
                return valObj.getString("value");
            } catch (Exception ex) {
                return null;
            }
        }
        return null;
    }

    public static String hashPassword(String password) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception ex) {
            throw new RuntimeException("SHA-256 algorithm not found", ex);
        }
    }

    public static void executePipeline(Context ctx, String sql, List<Object> args, Callback callback) {
        SharedPreferences prefs = ctx.getSharedPreferences(AcceptPrefs.NAME, Context.MODE_PRIVATE);
        String urlStr = prefs.getString(AcceptPrefs.KEY_TURSO_URL, "");
        String token = prefs.getString(AcceptPrefs.KEY_TURSO_TOKEN, "");

        if (urlStr.isEmpty() || token.isEmpty()) {
            callback.onError("Turso DB URL or Token not configured");
            return;
        }

        final String targetUrl = getPipelineUrl(urlStr);
        final String bearerToken = token;
        final Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                JSONObject requestJson = new JSONObject();
                JSONArray requestsArray = new JSONArray();

                // 1. Execute SQL statement request
                JSONObject execObj = new JSONObject();
                execObj.put("type", "execute");

                JSONObject stmtObj = new JSONObject();
                stmtObj.put("sql", sql);

                JSONArray argsArray = new JSONArray();
                for (Object arg : args) {
                    JSONObject argObj = new JSONObject();
                    argObj.put("type", "text");
                    argObj.put("value", String.valueOf(arg));
                    argsArray.put(argObj);
                }
                stmtObj.put("args", argsArray);
                execObj.put("stmt", stmtObj);
                requestsArray.put(execObj);

                // 2. Close connection request
                JSONObject closeObj = new JSONObject();
                closeObj.put("type", "close");
                requestsArray.put(closeObj);

                requestJson.put("requests", requestsArray);

                URL url = new URL(targetUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + bearerToken);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                OutputStream os = conn.getOutputStream();
                os.write(requestJson.toString().getBytes("UTF-8"));
                os.close();

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    InputStream is = conn.getInputStream();
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is, "UTF-8"));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    JSONObject responseJson = new JSONObject(response.toString());
                    if (responseJson.has("results")) {
                        JSONArray results = responseJson.getJSONArray("results");
                        JSONObject firstResult = results.getJSONObject(0);
                        String resultType = firstResult.getString("type");
                        if ("ok".equals(resultType)) {
                            JSONObject resultObj = firstResult.getJSONObject("result");
                            JSONArray rows = resultObj.optJSONArray("rows");
                            if (rows == null) {
                                rows = new JSONArray(); // Return empty array on statements with no result rows
                            }
                            final JSONArray finalRows = rows;
                            handler.post(() -> callback.onSuccess(finalRows));
                        } else if ("error".equals(resultType)) {
                            JSONObject errObj = firstResult.getJSONObject("error");
                            final String errMsg = errObj.optString("message", "Database statement execution failed");
                            handler.post(() -> callback.onError(errMsg));
                        } else {
                            handler.post(() -> callback.onError("Unknown query response type: " + resultType));
                        }
                    } else {
                        handler.post(() -> callback.onError("Invalid JSON response: missing results"));
                    }
                } else {
                    InputStream errIs = conn.getErrorStream();
                    String errorMsg = "HTTP error code: " + responseCode;
                    if (errIs != null) {
                        java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(errIs, "UTF-8"));
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        reader.close();
                        try {
                            JSONObject errorJson = new JSONObject(response.toString());
                            if (errorJson.has("message")) {
                                errorMsg = errorJson.getString("message");
                            }
                        } catch (Exception ignored) {
                            errorMsg = response.toString();
                        }
                    }
                    final String finalError = errorMsg;
                    handler.post(() -> callback.onError(finalError));
                }
            } catch (Exception e) {
                final String errorMsg = e.getMessage() != null ? e.getMessage() : "Network error";
                handler.post(() -> callback.onError(errorMsg));
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    public static void initDatabase(Context ctx, Callback callback) {
        String sql = "CREATE TABLE IF NOT EXISTS users (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "username TEXT UNIQUE, " +
                "email TEXT UNIQUE, " +
                "phone TEXT UNIQUE, " +
                "password TEXT, " +
                "recovery_question TEXT, " +
                "recovery_answer TEXT" +
                ");";
        executePipeline(ctx, sql, new ArrayList<>(), callback);
    }

    public static void signUpUser(Context ctx, String username, String email, String phone, String password, String question, String answer, Callback callback) {
        String sql = "INSERT INTO users (username, email, phone, password, recovery_question, recovery_answer) VALUES (?, ?, ?, ?, ?, ?);";
        List<Object> args = new ArrayList<>();
        args.add(username.trim().toLowerCase());
        args.add(email.trim().toLowerCase());
        args.add(phone.trim());
        args.add(hashPassword(password));
        args.add(question.trim());
        args.add(answer.trim().toLowerCase());
        executePipeline(ctx, sql, args, callback);
    }

    public static void loginUser(Context ctx, String usernameOrEmail, String password, Callback callback) {
        String sql = "SELECT username, email, phone, password FROM users WHERE username = ? OR email = ?;";
        List<Object> args = new ArrayList<>();
        args.add(usernameOrEmail.trim().toLowerCase());
        args.add(usernameOrEmail.trim().toLowerCase());

        executePipeline(ctx, sql, args, new Callback() {
            @Override
            public void onSuccess(JSONArray rows) {
                if (rows == null || rows.length() == 0) {
                    callback.onError("Invalid username/email or password");
                    return;
                }
                try {
                    JSONArray firstRow = rows.getJSONArray(0);
                    String dbPasswordHash = getValueAsString(firstRow.getJSONObject(3));
                    String inputPasswordHash = hashPassword(password);
                    if (inputPasswordHash.equals(dbPasswordHash)) {
                        callback.onSuccess(rows);
                    } else {
                        callback.onError("Invalid username/email or password");
                    }
                } catch (Exception ex) {
                    callback.onError("Error parsing login response: " + ex.getMessage());
                }
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    public static void getRecoveryQuestion(Context ctx, String usernameOrEmail, Callback callback) {
        String sql = "SELECT recovery_question FROM users WHERE username = ? OR email = ?;";
        List<Object> args = new ArrayList<>();
        args.add(usernameOrEmail.trim().toLowerCase());
        args.add(usernameOrEmail.trim().toLowerCase());
        executePipeline(ctx, sql, args, callback);
    }

    public static void resetPassword(Context ctx, String usernameOrEmail, String answer, String newPassword, Callback callback) {
        String selectSql = "SELECT recovery_answer FROM users WHERE username = ? OR email = ?;";
        List<Object> selectArgs = new ArrayList<>();
        selectArgs.add(usernameOrEmail.trim().toLowerCase());
        selectArgs.add(usernameOrEmail.trim().toLowerCase());

        executePipeline(ctx, selectSql, selectArgs, new Callback() {
            @Override
            public void onSuccess(JSONArray rows) {
                if (rows == null || rows.length() == 0) {
                    callback.onError("User not found");
                    return;
                }
                try {
                    JSONArray firstRow = rows.getJSONArray(0);
                    String dbAnswer = getValueAsString(firstRow.getJSONObject(0));
                    if (dbAnswer != null && dbAnswer.equals(answer.trim().toLowerCase())) {
                        String updateSql = "UPDATE users SET password = ? WHERE username = ? OR email = ?;";
                        List<Object> updateArgs = new ArrayList<>();
                        updateArgs.add(hashPassword(newPassword));
                        updateArgs.add(usernameOrEmail.trim().toLowerCase());
                        updateArgs.add(usernameOrEmail.trim().toLowerCase());
                        executePipeline(ctx, updateSql, updateArgs, callback);
                    } else {
                        callback.onError("Incorrect answer to security question");
                    }
                } catch (Exception ex) {
                    callback.onError("Error resetting password: " + ex.getMessage());
                }
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }
}
