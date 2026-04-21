$file = 'app/src/main/java/com/elv8/crisisos/ui/screens/chatv2/ChatListScreen.kt'
$content = Get-Content $file -Raw
$content = $content -replace '\}\n\n@Composable\nfun ThreadListItem\(', "}

}

@Composable
fun ThreadListItem("
Set-Content -Path $file -Value $content
