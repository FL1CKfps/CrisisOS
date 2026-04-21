import sys

filepath = 'app/src/main/java/com/elv8/crisisos/service/MeshForegroundService.kt'
with open(filepath, 'r') as f:
    content = f.read()

target = """import android.os.PowerManager
import android.provider.Settings"""
replace = """import android.os.PowerManager
import android.provider.Settings
import com.google.android.gms.nearby.Nearby"""
if 'com.google.android.gms.nearby.Nearby' not in content:
    content = content.replace(target, replace)

with open(filepath, 'w') as f:
    f.write(content)
