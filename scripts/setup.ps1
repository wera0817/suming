. (Join-Path $PSScriptRoot 'tools.ps1')
New-Item -ItemType Directory -Force $LocalRoot | Out-Null
$configPath = Join-Path $LocalRoot 'config.json'
if (!(Test-Path $configPath)) {
    $key = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($key) } finally { $rng.Dispose() }
    @{ mysqlPort = 13306; dbPassword = (New-RandomHex); rootPassword = (New-RandomHex); adminPassword = (New-RandomHex 12); masterKey = [Convert]::ToBase64String($key) } | ConvertTo-Json | Set-Content -Encoding UTF8 $configPath
    # Credentials belong only to the current Windows user, not to repository history.
    & icacls.exe $LocalRoot /inheritance:r /grant:r "${env:USERNAME}:(OI)(CI)F" 'SYSTEM:(OI)(CI)F' | Out-Null
}
$config = Read-Config
Set-AppEnvironment $config
Start-ProjectMySql $config
Push-Location (Join-Path $ProjectRoot 'frontend')
try { & npm.cmd install; if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed.' } } finally { Pop-Location }
Get-Maven | Out-Null
Write-Host 'Setup complete. Admin account: admin. The generated password is adminPassword in .local/config.json.'
Write-Host 'Run scripts/start.ps1 next. Do not commit or share .local.'
