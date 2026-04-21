import os
import re

files_to_fix = [
    "app/src/main/java/com/elv8/crisisos/data/repository/ConnectionRequestRepositoryImpl.kt",
    "app/src/main/java/com/elv8/crisisos/data/repository/ContactRepositoryImpl.kt",
    "app/src/main/java/com/elv8/crisisos/data/repository/MessageRequestRepositoryImpl.kt",
    "app/src/main/java/com/elv8/crisisos/data/repository/ThreadChatRepositoryImpl.kt"
]

for file_path in files_to_fix:
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    # We need to revert: 
    #   override suspend fun foo(args) { withContext(Dispatchers.IO) {
    #     bar()
    #   }
    # which is missing a closing base. We'll find them and close them, or better, 
    # revert to: override suspend fun foo(args) { withContext(Dispatchers.IO) { bar() } }
    # Let's revert back to = withContext<Unit>(...){ ... } which solves the type and keeps the single block.

    # 1. Revert `{ withContext(Dispatchers.IO) {` back to `= withContext(Dispatchers.IO) {`
    # or `= withContext<Unit>(Dispatchers.IO) {`
    content = re.sub(
        r'override suspend fun (\w+)\((.*?)\)\s*\{\s*withContext(?:\<Unit\>)?\s*\(\s*Dispatchers\.IO\s*\)\s*\{',
        r'override suspend fun \1(\2) { withContext(Dispatchers.IO) {',
        content
    )

    # 2. But we need closing braces. Actually, another way is to just do:
    # override suspend fun name(args) { withContext(Dispatchers.IO) { ... } }
    # So if it was previously `override suspend fun name(args) = withContext(...) {`, 
    # it was one curly brace.
    # To fix this, let's just make it `= withContext<Unit>(Dispatchers.IO) {` since that suppresses the integer return type constraint (it forces the block to return Unit instead of inferring). Wait, the block might still complain if the last statement is Int and it expects Unit. We can just add `.let { }` at the end or change the DAO to Int and don't care. Wait, if a `withContext<Unit>` block has a last statement `Int`, it's an error.
    # The absolute safest is to just re-read the file, and wherever there's `override suspend fun ... { withContext(Dispatchers.IO) {` without a closing brace, close it.

    with open(file_path, "w", encoding="utf-8") as f:
        f.write(content)

print("Done")
