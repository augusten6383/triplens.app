import sys

filepath = r"D:\Desktop\Augusten\triplencse\triplencse\app\src\main\java\com\triplencse\acceptassist\MainActivity.java"
with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Please enter both URL and Token
content = content.replace(
    'Toast.makeText(this, "Please enter both URL and Token", Toast.LENGTH_SHORT).show();',
    'Toast.makeText(this, "Please fill in all the details to continue.", Toast.LENGTH_SHORT).show();'
)

# 2. Connecting to Turso...
content = content.replace(
    'Toast.makeText(this, "Connecting to Turso...", Toast.LENGTH_SHORT).show();',
    '// removed technical toast'
)

# 3. Database Initialized Successfully!
content = content.replace(
    'Toast.makeText(MainActivity.this, "Database Initialized Successfully!", Toast.LENGTH_LONG).show();',
    '// removed technical toast'
)

# 4. Connection failed:
content = content.replace(
    'Toast.makeText(MainActivity.this, "Connection failed: " + message, Toast.LENGTH_LONG).show();',
    'Toast.makeText(MainActivity.this, "Unable to connect. Please check your credentials and try again.", Toast.LENGTH_LONG).show();'
)

# 5. Google sign in failed
content = content.replace(
    'Toast.makeText(this, "Google sign in failed", Toast.LENGTH_SHORT).show();',
    'Toast.makeText(this, "Sign-in cancelled. Please try again.", Toast.LENGTH_SHORT).show();'
)

# 6. Authenticating with Firebase...
content = content.replace(
    'Toast.makeText(this, "Authenticating with Firebase...", Toast.LENGTH_SHORT).show();',
    '// removed technical toast'
)

# 7. Authentication Failed.
content = content.replace(
    'Toast.makeText(this, "Authentication Failed.", Toast.LENGTH_SHORT).show();',
    'Toast.makeText(this, "We couldn\'t verify your account right now. Please try again.", Toast.LENGTH_LONG).show();'
)

# 8. Logged out
content = content.replace(
    'Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();',
    '// removed redundant toast'
)

# 9. Auto-Clicker Started / Stopped
content = content.replace(
    'Toast.makeText(this, next ? "Auto-Clicker Started" : "Auto-Clicker Stopped", Toast.LENGTH_SHORT).show();',
    'Toast.makeText(this, next ? "Auto-clicker is now active" : "Auto-clicker paused", Toast.LENGTH_SHORT).show();'
)

# 10. Saved
content = content.replace(
    'Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();',
    'Toast.makeText(this, "Settings saved successfully", Toast.LENGTH_SHORT).show();'
)

# 11. Activating 1-day demo subscription...
content = content.replace(
    'Toast.makeText(this, "Activating 1-day demo subscription...", Toast.LENGTH_SHORT).show();',
    '// removed redundant toast'
)

# 12. Demo subscription activated successfully!
content = content.replace(
    'Toast.makeText(MainActivity.this, "Demo subscription activated successfully!", Toast.LENGTH_LONG).show();',
    'Toast.makeText(MainActivity.this, "Welcome to Premium! Your trial is now active.", Toast.LENGTH_LONG).show();'
)

# 13. Failed to activate demo:
content = content.replace(
    'Toast.makeText(MainActivity.this, "Failed to activate demo: " + message, Toast.LENGTH_LONG).show();',
    'Toast.makeText(MainActivity.this, "We couldn\'t activate your trial right now. Please try again later.", Toast.LENGTH_LONG).show();'
)

with open(filepath, 'w', encoding='utf-8') as f:
    f.write(content)
