Stop-Process -Name syncswift -Force -ErrorAction SilentlyContinue
Start-Sleep -Milliseconds 800
$c = (Get-ChildItem Cert:\CurrentUser\My -CodeSigningCert)[0]
if ($c) {
    Set-AuthenticodeSignature -FilePath ".\src-tauri\target\debug\syncswift.exe" -Certificate $c
    Copy-Item ".\src-tauri\target\debug\syncswift.exe" -Destination ".\msix_package\SyncSwift.exe" -Force
    Set-AuthenticodeSignature -FilePath ".\msix_package\SyncSwift.exe" -Certificate $c
    Write-Host "SUCCESSFULLY_SIGNED"
} else {
    Write-Host "NO_CERT"
}
