# Auto Generator for GitHub Actions iOS TestFlight Secrets
$desktop = [System.Environment]::GetFolderPath("Desktop")
$workspace = Split-Path -Parent $PSScriptRoot
$openssl = "C:\Program Files\Git\usr\bin\openssl.exe"

$p12Password = if ($env:P12_PASSWORD) { $env:P12_PASSWORD } else { "SyncSwiftP12Secret" }
$keychainPassword = if ($env:KEYCHAIN_PASSWORD) { $env:KEYCHAIN_PASSWORD } else { "SyncSwiftKeychainSecret" }


Write-Host "Checking required files..." -ForegroundColor Cyan

# Look for distribution.cer specifically
$cerFile = Get-ChildItem -Path $desktop -Filter "distribution.cer" | Select-Object -First 1
if (-not $cerFile) {
    $cerFile = Get-ChildItem -Path $desktop -File | Where-Object { $_.Extension -eq ".cer" -and $_.Name -ne "development.cer" } | Select-Object -First 1
}
if (-not $cerFile) {
    $cerFile = Get-ChildItem -Path $desktop -File | Where-Object { $_.Extension -eq ".cer" } | Select-Object -First 1
}

$mobileProvFile = Get-ChildItem -Path $desktop -Filter "*.mobileprovision" | Select-Object -First 1
if (-not $mobileProvFile) { $mobileProvFile = Get-ChildItem -Path $workspace -Filter "*.mobileprovision" | Select-Object -First 1 }

$keyFile = Join-Path $desktop "ios_distribution.key"
if (-not (Test-Path $keyFile)) { $keyFile = Join-Path $workspace "ios_distribution.key" }

if (-not (Test-Path $keyFile)) {
    Write-Host "Error: ios_distribution.key not found!" -ForegroundColor Red
    exit 1
}

$p12File = Join-Path $desktop "certificate.p12"
if ($null -ne $cerFile) {
    Write-Host "Found certificate: $($cerFile.FullName)" -ForegroundColor Green
    $cerPem = Join-Path $desktop "cert.pem"
    & $openssl x509 -in $cerFile.FullName -inform DER -out $cerPem -outform PEM
    & $openssl pkcs12 -export -inkey $keyFile -in $cerPem -out $p12File -password "pass:$p12Password"
    Remove-Item $cerPem -Force -ErrorAction SilentlyContinue
    Write-Host "Generated certificate.p12 successfully!" -ForegroundColor Green
}

$outputFile = Join-Path $desktop "GITHUB_SECRETS_READY.txt"
$sb = [System.Text.StringBuilder]::new()

[void]$sb.AppendLine("=======================================================")
[void]$sb.AppendLine("GITHUB ACTIONS SECRETS (Copy-paste each into GitHub)")
[void]$sb.AppendLine("Repo -> Settings -> Secrets and variables -> Actions")
[void]$sb.AppendLine("=======================================================")
[void]$sb.AppendLine("")

[void]$sb.AppendLine("KEYCHAIN_PASSWORD:")
[void]$sb.AppendLine($keychainPassword)
[void]$sb.AppendLine("")

[void]$sb.AppendLine("P12_PASSWORD:")
[void]$sb.AppendLine($p12Password)
[void]$sb.AppendLine("")

if (Test-Path $p12File) {
    $p12Base64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($p12File))
    [void]$sb.AppendLine("BUILD_CERTIFICATE_BASE64:")
    [void]$sb.AppendLine($p12Base64)
    [void]$sb.AppendLine("")
} else {
    [void]$sb.AppendLine("BUILD_CERTIFICATE_BASE64:")
    [void]$sb.AppendLine("(Apple Developer'dan indirdiğiniz .cer dosyasını Masaüstüne koyup bu scripti tekrar çalıştırın)")
    [void]$sb.AppendLine("")
}

if ($null -ne $mobileProvFile) {
    $ppBase64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($mobileProvFile.FullName))
    [void]$sb.AppendLine("BUILD_PROVISION_PROFILE_BASE64:")
    [void]$sb.AppendLine($ppBase64)
    [void]$sb.AppendLine("")
    [void]$sb.AppendLine("PROVISIONING_PROFILE_SPECIFIER:")
    [void]$sb.AppendLine($mobileProvFile.BaseName)
    [void]$sb.AppendLine("")
} else {
    [void]$sb.AppendLine("BUILD_PROVISION_PROFILE_BASE64:")
    [void]$sb.AppendLine("(Apple Developer'dan indirdiğiniz .mobileprovision dosyasını Masaüstüne koyup bu scripti tekrar çalıştırın)")
    [void]$sb.AppendLine("")
}

[IO.File]::WriteAllText($outputFile, $sb.ToString())
Write-Host "Saved secrets to: $outputFile" -ForegroundColor Cyan
