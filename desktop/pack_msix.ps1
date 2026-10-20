$makeappx = "C:\Program Files (x86)\Windows Kits\10\bin\10.0.26100.0\x64\makeappx.exe"
$signtool = "C:\Program Files (x86)\Windows Kits\10\bin\10.0.26100.0\x64\signtool.exe"
$outputMsix = "C:\Users\btc\Desktop\SyncSwift_1.0.13.0_x64.msix"

Write-Host "Unregistering older net.syncswift.desktop package if installed..."
Get-AppxPackage net.syncswift.desktop | Remove-AppxPackage -ErrorAction SilentlyContinue

Write-Host "Copying fresh release binary to msix_package..."
Copy-Item ".\src-tauri\target\release\syncswift.exe" ".\msix_package\SyncSwift.exe" -Force

Write-Host "Signing executable..."
& $signtool sign /fd SHA256 /a /n "SyncSwiftDev" /tr http://timestamp.digicert.com /td SHA256 ".\msix_package\SyncSwift.exe"

Remove-Item "C:\Users\btc\Desktop\SyncSwift_*.msix" -Force -ErrorAction SilentlyContinue

Write-Host "Packing MSIX..."
& $makeappx pack /d ".\msix_package" /p $outputMsix /o

Write-Host "Signing MSIX package..."
& $signtool sign /fd SHA256 /a /n "SyncSwiftDev" /tr http://timestamp.digicert.com /td SHA256 $outputMsix

Write-Host "MSIX Packaging Complete: $outputMsix"
