import sys

filepath = 'app/src/main/java/com/elv8/crisisos/data/local/CrisisDatabase.kt'
with open(filepath, 'r') as f:
    content = f.read()

target = """    companion object {"""
replace = """    companion object {
        // Entities checklist — if any of these fail, the entity is missing from @Database:
        // PeerEntity, ContactEntity, GroupEntity, ConnectionRequestEntity,
        // MessageRequestEntity, ChatThreadEntity, ChatMessageEntity,
        // OutboxMessageEntity, UserIdentityEntity, DangerZoneEntity, CheckpointEntity

        val assertEntities = listOf(
            com.elv8.crisisos.data.local.entity.PeerEntity::class.java,
            com.elv8.crisisos.data.local.entity.ContactEntity::class.java,
            com.elv8.crisisos.data.local.entity.GroupEntity::class.java,
            com.elv8.crisisos.data.local.entity.ConnectionRequestEntity::class.java,
            com.elv8.crisisos.data.local.entity.MessageRequestEntity::class.java,
            com.elv8.crisisos.data.local.entity.ChatThreadEntity::class.java,
            com.elv8.crisisos.data.local.entity.ChatMessageEntity::class.java,
            com.elv8.crisisos.data.local.entity.OutboxMessageEntity::class.java,
            com.elv8.crisisos.data.local.entity.UserIdentityEntity::class.java,
            com.elv8.crisisos.data.local.entity.DangerZoneEntity::class.java,
            com.elv8.crisisos.data.local.entity.CheckpointEntity::class.java
        )
"""
if "Entities checklist" not in content:
    content = content.replace(target, replace)

with open(filepath, 'w') as f:
    f.write(content)
print("Updated CrisisDatabase compile-time check")
