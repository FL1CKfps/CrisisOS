import sys

filepath = 'app/src/main/java/com/elv8/crisisos/ui/screens/home/HomeViewModel.kt'
with open(filepath, 'r') as f:
    content = f.read()

target_inject = """class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recoveryManager: com.elv8.crisisos.core.recovery.MeshRecoveryManager
) : ViewModel() {"""
replace_inject = """class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recoveryManager: com.elv8.crisisos.core.recovery.MeshRecoveryManager,
    private val identityRepository: com.elv8.crisisos.domain.repository.IdentityRepository
) : ViewModel() {"""
content = content.replace(target_inject, replace_inject)

target_bad_inject = """    @Inject lateinit var identityRepository: com.elv8.crisisos.domain.repository.IdentityRepository\n\n"""
content = content.replace(target_bad_inject, "")

with open(filepath, 'w') as f:
    f.write(content)
print("Updated HomeViewModel correctly with init injection")
