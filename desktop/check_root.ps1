$c = (Get-ChildItem Cert:\CurrentUser\My -CodeSigningCert)[0]
$inRoot = Get-ChildItem Cert:\LocalMachine\Root | Where-Object { $_.Thumbprint -eq $c.Thumbprint }
if ($inRoot) {
    Write-Host "IN_ROOT"
} else {
    Write-Host "NOT_IN_ROOT"
}
