$WshShell = New-Object -ComObject WScript.Shell
$Shortcut = $WshShell.CreateShortcut("C:\Users\btc\Desktop\SyncSwift.lnk")
$Shortcut.TargetPath = "C:\Users\btc\Desktop\SyncSwift\desktop\src-tauri\target\debug\syncswift.exe"
$Shortcut.WorkingDirectory = "C:\Users\btc\Desktop\SyncSwift\desktop\src-tauri\target\debug"
$Shortcut.IconLocation = "C:\Users\btc\Desktop\SyncSwift\desktop\src-tauri\icons\icon.ico"
$Shortcut.Description = "SyncSwift Desktop"
$Shortcut.Save()
Write-Host "SHORTCUT_UPDATED_TO_TARGET"
