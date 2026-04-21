$file = 'app/src/main/java/com/elv8/crisisos/ui/screens/chatv2/ChatHubScreen.kt'
$content = Get-Content $file -Raw
$content = $content -replace 'onNavigateToChat = onNavigateToThread,', ''
Set-Content -Path $file -Value $content
