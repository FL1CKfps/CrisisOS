import os
import re

def refactor_logs(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    original = content
    
    if 'android.util.Log' in content or 'Log.' in content:
        if 'import com.elv8.crisisos.core.debug.MeshLogger' not in content:
            if 'package com.elv8' in content:
                content = re.sub(r'(package com.elv8.crisisos[^\n]*\n)', r'\1\nimport com.elv8.crisisos.core.debug.MeshLogger\n', content, 1)

    content = content.replace('Log.d("CrisisOS_Boot", ', 'MeshLogger.service(')
    content = content.replace('Log.d("CrisisOS_Service", ', 'MeshLogger.service(')
    content = content.replace('Log.i("CrisisOS_Service", ', 'MeshLogger.service(')
    content = content.replace('Log.w("CrisisOS_Service", ', 'MeshLogger.warn("Service", ')
    content = content.replace('Log.e("CrisisOS_Service", ', 'MeshLogger.error("Service", ')
    content = content.replace('android.util.Log.i("CrisisOS_Service", ', 'MeshLogger.service(')
    content = content.replace('android.util.Log.e("CrisisOS_Service", ', 'MeshLogger.error("Service", ')

    if original != content:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"Updated {filepath}")

for root, dirs, files in os.walk('app/src/main/java/com/elv8/crisisos'):
    for file in files:
        if file.endswith('.kt'):
            refactor_logs(os.path.join(root, file))
