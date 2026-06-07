import sys

filepath = r"D:\Desktop\Augusten\triplencse\triplencse\app\src\main\java\com\triplencse\acceptassist\TursoHelper.java"
with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Update initDatabase to include device_id
content = content.replace(
    """"subscription_expires_at INTEGER DEFAULT 0" +
                ");";""",
    """"subscription_expires_at INTEGER DEFAULT 0, " +
                "device_id TEXT" +
                ");";"""
)

# 2. Add device_id to migration
content = content.replace(
    """        executePipeline(ctx, "ALTER TABLE users ADD COLUMN subscription_expires_at INTEGER DEFAULT 0;", new ArrayList<>(), new Callback() {
            @Override public void onSuccess(JSONArray rows) {}
            @Override public void onError(String message) {}
        });""",
    """        executePipeline(ctx, "ALTER TABLE users ADD COLUMN subscription_expires_at INTEGER DEFAULT 0;", new ArrayList<>(), new Callback() {
            @Override public void onSuccess(JSONArray rows) {}
            @Override public void onError(String message) {}
        });
        executePipeline(ctx, "ALTER TABLE users ADD COLUMN device_id TEXT;", new ArrayList<>(), new Callback() {
            @Override public void onSuccess(JSONArray rows) {}
            @Override public void onError(String message) {}
        });"""
)

# 3. Add verifyDeviceAndSubscription
method_to_add = """
    public static void verifyDeviceAndSubscription(Context ctx, String username, String deviceId, Callback callback) {
        String checkDeviceSql = "SELECT username FROM users WHERE device_id = ? AND username != ?;";
        List<Object> args1 = new ArrayList<>();
        args1.add(deviceId);
        args1.add(username.trim().toLowerCase());
        
        executePipeline(ctx, checkDeviceSql, args1, new Callback() {
            @Override
            public void onSuccess(JSONArray rows) {
                if (rows != null && rows.length() > 0) {
                    callback.onError("DEVICE_USED_BY_OTHER_ACCOUNT");
                    return;
                }
                
                String checkUserSql = "SELECT device_id, status, free_clicks_remaining, subscription_expires_at FROM users WHERE username = ?;";
                List<Object> args2 = new ArrayList<>();
                args2.add(username.trim().toLowerCase());
                
                executePipeline(ctx, checkUserSql, args2, new Callback() {
                    @Override
                    public void onSuccess(JSONArray userRows) {
                        if (userRows != null && userRows.length() > 0) {
                            try {
                                JSONArray firstRow = userRows.getJSONArray(0);
                                String dbDeviceId = getValueAsString(firstRow.getJSONObject(0));
                                
                                if (dbDeviceId == null || dbDeviceId.isEmpty() || "null".equals(dbDeviceId)) {
                                    String updateSql = "UPDATE users SET device_id = ? WHERE username = ?;";
                                    List<Object> updateArgs = new ArrayList<>();
                                    updateArgs.add(deviceId);
                                    updateArgs.add(username.trim().toLowerCase());
                                    executePipeline(ctx, updateSql, updateArgs, new Callback() {
                                        @Override
                                        public void onSuccess(JSONArray ignore) {
                                            callback.onSuccess(userRows);
                                        }
                                        @Override
                                        public void onError(String message) {
                                            callback.onError(message);
                                        }
                                    });
                                } else if (!dbDeviceId.equals(deviceId)) {
                                    callback.onError("ACCOUNT_USED_ON_OTHER_DEVICE");
                                } else {
                                    callback.onSuccess(userRows);
                                }
                            } catch (Exception ex) {
                                callback.onError("Error parsing user data");
                            }
                        } else {
                            String insertSql = "INSERT INTO users (username, device_id, status, free_clicks_remaining, subscription_expires_at) VALUES (?, ?, 'active', 1, 0);";
                            List<Object> insertArgs = new ArrayList<>();
                            insertArgs.add(username.trim().toLowerCase());
                            insertArgs.add(deviceId);
                            executePipeline(ctx, insertSql, insertArgs, new Callback() {
                                @Override
                                public void onSuccess(JSONArray insertRows) {
                                    try {
                                        JSONArray mockRows = new JSONArray();
                                        JSONArray mockRow = new JSONArray();
                                        JSONObject devObj = new JSONObject(); devObj.put("type", "text"); devObj.put("value", deviceId);
                                        JSONObject activeObj = new JSONObject(); activeObj.put("type", "text"); activeObj.put("value", "active");
                                        JSONObject oneObj = new JSONObject(); oneObj.put("type", "integer"); oneObj.put("value", "1");
                                        JSONObject zeroObj = new JSONObject(); zeroObj.put("type", "integer"); zeroObj.put("value", "0");
                                        
                                        mockRow.put(devObj);
                                        mockRow.put(activeObj);
                                        mockRow.put(oneObj);
                                        mockRow.put(zeroObj);
                                        mockRows.put(mockRow);
                                        callback.onSuccess(mockRows);
                                    } catch (Exception e) {
                                        callback.onError("Error creating mock rows");
                                    }
                                }
                                @Override
                                public void onError(String message) {
                                    callback.onError(message);
                                }
                            });
                        }
                    }
                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                });
            }
            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }
"""

content = content.replace("public static void checkUserSubscription(", method_to_add + "\n    public static void checkUserSubscription(")

with open(filepath, 'w', encoding='utf-8') as f:
    f.write(content)
