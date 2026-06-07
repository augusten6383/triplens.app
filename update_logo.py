import sys

filepath = r"D:\Desktop\Augusten\triplencse\triplencse\app\src\main\java\com\triplencse\acceptassist\MainActivity.java"
with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

target = "View appIcon = systemIcon(android.R.drawable.ic_menu_compass, Color.TRANSPARENT, 48, 8);"
replacement = "View appIcon = systemIcon(R.drawable.triplens_logo, Color.TRANSPARENT, 48, 8);"

content = content.replace(target, replacement)

with open(filepath, 'w', encoding='utf-8') as f:
    f.write(content)
