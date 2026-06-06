#!/usr/bin/env python3
import json
import os
import re
import time
import urllib.request

CONFIG_FILE = ".admin_config"
PREFS_FILE = "app/src/main/java/com/triplencse/acceptassist/AcceptPrefs.java"

def load_credentials():
    # 1. Try reading from .admin_config
    if os.path.exists(CONFIG_FILE):
        try:
            with open(CONFIG_FILE, "r") as f:
                config = json.load(f)
                if config.get("url") and config.get("token"):
                    return config["url"], config["token"]
        except Exception:
            pass

    # 2. Try parsing AcceptPrefs.java
    if os.path.exists(PREFS_FILE):
        try:
            with open(PREFS_FILE, "r") as f:
                content = f.read()
                url_match = re.search(r'KEY_TURSO_URL\s*,\s*"([^"]+)"', content)
                token_match = re.search(r'KEY_TURSO_TOKEN\s*,\s*"([^"]+)"', content)
                if url_match and token_match:
                    return url_match.group(1), token_match.group(1)
        except Exception:
            pass

    return None, None

def save_credentials(url, token):
    with open(CONFIG_FILE, "w") as f:
        json.dump({"url": url, "token": token}, f)

def get_credentials():
    url, token = load_credentials()
    if url and token:
        print("Loaded Turso credentials automatically.")
        print(f"URL: {url}")
        print("-" * 50)
        return url, token

    print("=== Turso Database Setup ===")
    url = input("Enter Turso URL (libsql://... or https://...): ").strip()
    token = input("Enter Turso Bearer Token: ").strip()
    if url and token:
        save_credentials(url, token)
        return url, token
    else:
        print("Invalid input. Credentials required.")
        exit(1)

def execute_query(url, token, sql, args=[]):
    # Clean URL
    if url.startswith("libsql://"):
        url = "https://" + url[9:]
    if not url.endswith("/v2/pipeline"):
        url = url.rstrip("/") + "/v2/pipeline"

    req_body = {
        "requests": [
            {
                "type": "execute",
                "stmt": {
                    "sql": sql,
                    "args": [{"type": "text", "value": str(arg)} for arg in args]
                }
            },
            {
                "type": "close"
            }
        ]
    }

    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json"
    }

    req = urllib.request.Request(url, data=json.dumps(req_body).encode("utf-8"), headers=headers, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=10) as response:
            resp_body = json.loads(response.read().decode("utf-8"))
            results = resp_body.get("results", [])
            if not results:
                return {"success": False, "error": "No results returned from server"}
            
            first_result = results[0]
            if first_result.get("type") == "ok":
                result_data = first_result.get("result")
                if not result_data:
                    resp_data = first_result.get("response", {})
                    result_data = resp_data.get("result")

                if result_data:
                    return {"success": True, "cols": result_data.get("cols", []), "rows": result_data.get("rows", [])}
                return {"success": True, "cols": [], "rows": []}
            else:
                err_msg = first_result.get("error", {}).get("message", "SQL Execution failed")
                return {"success": False, "error": err_msg}
    except Exception as e:
        return {"success": False, "error": str(e)}

def extract_val(cell):
    if not cell:
        return ""
    return cell.get("value", "")

def format_date(timestamp_str):
    try:
        ts = int(timestamp_str)
        if ts <= 0:
            return "Expired (No Sub)"
        
        now = time.time()
        expiry_date = time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(ts))
        if ts < now:
            return f"Expired ({expiry_date})"
        return f"Active (Until {expiry_date})"
    except Exception:
        return "Unknown"

def list_users(url, token):
    sql = "SELECT id, username, email, phone, status, free_clicks_remaining, subscription_expires_at FROM users;"
    res = execute_query(url, token, sql)
    if not res["success"]:
        print(f"Error listing users: {res['error']}")
        return

    cols = [c["name"] for c in res["cols"]]
    rows = res["rows"]

    if not rows:
        print("\nNo users registered yet.\n")
        return

    # Print Table
    print("\n" + "=" * 100)
    print(f"{'ID':<4} | {'Username':<15} | {'Email':<22} | {'Phone':<12} | {'Status':<8} | {'Free Clicks':<11} | {'Subscription Status'}")
    print("-" * 100)
    
    for row in rows:
        r_id = extract_val(row[0])
        r_user = extract_val(row[1])
        r_email = extract_val(row[2])
        r_phone = extract_val(row[3])
        r_status = extract_val(row[4])
        r_clicks = extract_val(row[5])
        r_expires = extract_val(row[6])

        # Default values fallback
        r_status = r_status if r_status else "active"
        r_clicks = r_clicks if r_clicks else "1"
        r_expires = r_expires if r_expires else "0"

        sub_status = format_date(r_expires)
        print(f"{r_id:<4} | {r_user:<15} | {r_email:<22} | {r_phone:<12} | {r_status:<8} | {r_clicks:<11} | {sub_status}")
    print("=" * 100 + "\n")

def block_user(url, token):
    username = input("Enter username to BLOCK: ").strip().lower()
    if not username:
        return
    sql = "UPDATE users SET status = 'blocked' WHERE username = ?;"
    res = execute_query(url, token, sql, [username])
    if res["success"]:
        print(f"\nUser '{username}' successfully BLOCKED.\n")
    else:
        print(f"\nError blocking user: {res['error']}\n")

def unblock_user(url, token):
    username = input("Enter username to UNBLOCK: ").strip().lower()
    if not username:
        return
    sql = "UPDATE users SET status = 'active' WHERE username = ?;"
    res = execute_query(url, token, sql, [username])
    if res["success"]:
        print(f"\nUser '{username}' successfully UNBLOCKED.\n")
    else:
        print(f"\nError unblocking user: {res['error']}\n")

def grant_subscription(url, token):
    username = input("Enter username: ").strip().lower()
    if not username:
        return
    
    try:
        days = int(input("Enter number of subscription days to add: "))
    except ValueError:
        print("Invalid days count.")
        return

    # Check current expiration
    check_sql = "SELECT subscription_expires_at FROM users WHERE username = ?;"
    res = execute_query(url, token, check_sql, [username])
    if not res["success"] or not res["rows"]:
        print(f"Could not find user '{username}' or error: {res.get('error', 'not found')}")
        return

    current_expires = int(extract_val(res["rows"][0][0]) or 0)
    now = int(time.time())

    # Add days
    seconds_to_add = days * 24 * 60 * 60
    if current_expires > now:
        new_expires = current_expires + seconds_to_add
    else:
        new_expires = now + seconds_to_add

    update_sql = "UPDATE users SET subscription_expires_at = ? WHERE username = ?;"
    update_res = execute_query(url, token, update_sql, [new_expires, username])
    if update_res["success"]:
        expiry_date = time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(new_expires))
        print(f"\nSubscription granted to '{username}'. Expiration updated to: {expiry_date}\n")
    else:
        print(f"\nError granting subscription: {update_res['error']}\n")

def set_free_clicks(url, token):
    username = input("Enter username: ").strip().lower()
    if not username:
        return
    try:
        clicks = int(input("Enter clicks count (e.g. 1): "))
    except ValueError:
        print("Invalid clicks count.")
        return

    sql = "UPDATE users SET free_clicks_remaining = ? WHERE username = ?;"
    res = execute_query(url, token, sql, [clicks, username])
    if res["success"]:
        print(f"\nFree clicks count updated to {clicks} for user '{username}'.\n")
    else:
        print(f"\nError setting free clicks: {res['error']}\n")

def main():
    url, token = get_credentials()
    
    while True:
        print("Triplens Admin CRUD Utility")
        print("1. List Registered Users")
        print("2. Block User Account")
        print("3. Unblock User Account")
        print("4. Grant/Extend Subscription Days")
        print("5. Adjust Free Trial Click Count")
        print("6. Exit")
        
        choice = input("Enter selection (1-6): ").strip()
        if choice == "1":
            list_users(url, token)
        elif choice == "2":
            block_user(url, token)
        elif choice == "3":
            unblock_user(url, token)
        elif choice == "4":
            grant_subscription(url, token)
        elif choice == "5":
            set_free_clicks(url, token)
        elif choice == "6":
            print("Exiting.")
            break
        else:
            print("Invalid choice. Please try again.")

if __name__ == "__main__":
    main()
