import sys

filepath = 'app/src/main/java/com/elv8/crisisos/data/repository/IdentityRepositoryImpl.kt'
with open(filepath, 'r') as f:
    content = f.read()

inject_target = """class IdentityRepositoryImpl @Inject constructor(
    private val userIdentityDao: UserIdentityDao
) : IdentityRepository {"""

inject_replacement = """class IdentityRepositoryImpl @Inject constructor(
    private val userIdentityDao: UserIdentityDao,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : IdentityRepository {"""
content = content.replace(inject_target, inject_replacement)

method_target = """        userIdentityDao.insert(newEntity)
        return newEntity.toDomain()
    }"""
method_replacement = """        userIdentityDao.insert(newEntity)
        val domain = newEntity.toDomain()
        
        val prefs = context.getSharedPreferences("crisisos_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .putString("user_alias", domain.alias)
            .putString("local_crs_id", domain.crsId)
            .putString("local_device_id", domain.deviceId)
            .apply()
        android.util.Log.i("CrisisOS_Identity", "Identity persisted to prefs — alias= crsId=")
        
        return domain
    }"""
content = content.replace(method_target, method_replacement)

# also persist when existing is found
method2_target = """        if (existing != null) {
            return existing.toDomain()
        }"""
method2_replacement = """        if (existing != null) {
            val domain = existing.toDomain()
            val prefs = context.getSharedPreferences("crisisos_prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit()
                .putString("user_alias", domain.alias)
                .putString("local_crs_id", domain.crsId)
                .putString("local_device_id", domain.deviceId)
                .apply()
            android.util.Log.i("CrisisOS_Identity", "Identity persisted to prefs — alias= crsId=")
            return domain
        }"""
content = content.replace(method2_target, method2_replacement)

with open(filepath, 'w') as f:
    f.write(content)
print("Updated IdentityRepositoryImpl")
