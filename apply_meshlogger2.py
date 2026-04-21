import os
def refactor_logs(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    original = content
    
    content = content.replace('Log.i("CrisisOS_Mesh", ', 'MeshLogger.connection(')
    content = content.replace('Log.d("CrisisOS_MeshHealth", ', 'MeshLogger.heartbeat(')
    content = content.replace('Log.d("CrisisOS_Service", ', 'MeshLogger.service(')
    content = content.replace('Log.i("CrisisOS_Service", ', 'MeshLogger.service(')
    content = content.replace('Log.w("CrisisOS_Service", ', 'MeshLogger.warn("Service", ')
    content = content.replace('Log.e("CrisisOS_Service", ', 'MeshLogger.error("Service", ')
    
    # room
    content = content.replace('Log.d("CrisisOS_Room", ', 'MeshLogger.room(')
    # perms
    content = content.replace('Log.d("CrisisOS_Perms", ', 'MeshLogger.permission(')

    if original != content:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"Updated {filepath}")

for root, dirs, files in os.walk('app/src/main/java/com/elv8/crisisos'):
    for file in files:
        if file.endswith('.kt'):
            refactor_logs(os.path.join(root, file))
