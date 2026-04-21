import re
import glob

repo_files = glob.glob("app/src/main/java/com/elv8/crisisos/data/repository/*.kt")

for path in repo_files:
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    # Match explicitly the ones we ruined:
    content = re.sub(
        r'override suspend fun (\w+)\((.*?)\)\s*\{\s*withContext(?:\<Unit\>)?\s*\(\s*Dispatchers\.IO\s*\)\s*\{',
        r'override suspend fun \1(\2) = withContext<Unit>(Dispatchers.IO) {',
        content
    )
    
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)

print('Restored = withContext<Unit>')
