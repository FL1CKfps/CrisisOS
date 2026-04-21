import os
def refactor_logs(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    original = content
    if 'import com.elv8.crisisos.core.debug.MeshLogger' not in content and 'Log.' in content:
        content = content.replace('import android.util.Log', 'import android.util.Log\nimport com.elv8.crisisos.core.debug.MeshLogger')
    
    content = content.replace('Log.d("CrisisOS_Mesh", ', 'MeshLogger.connection(')
    content = content.replace('Log.v("CrisisOS_Mesh", ', 'MeshLogger.payload(')
    content = content.replace('Log.w("CrisisOS_Mesh", ', 'MeshLogger.warn("Mesh", ')
    content = content.replace('Log.e("CrisisOS_Mesh", ', 'MeshLogger.error("Mesh", ')
    
    content = content.replace('Log.d("CrisisOS_Messenger", ', 'MeshLogger.connection(')
    content = content.replace('Log.e("CrisisOS_Messenger", ', 'MeshLogger.error("Messenger", ')
    
    content = content.replace('Log.d("CrisisOS_Health", ', 'MeshLogger.heartbeat(')

    if original != content:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"Updated {filepath}")

for root, dirs, files in os.walk('app/src/main/java/com/elv8/crisisos'):
    for file in files:
        if file.endswith('.kt'):
            refactor_logs(os.path.join(root, file))
