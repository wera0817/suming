param([switch]$SkipBuild, [switch]$Lan = $true)
. (Join-Path $PSScriptRoot 'tools.ps1')
$config = Read-Config
Set-AppEnvironment $config
Start-ProjectMySql $config
if (!$SkipBuild) { & (Join-Path $PSScriptRoot 'build.ps1') }
$jar = Join-Path $ProjectRoot 'backend\target\guanxiang-0.1.0.jar'
if (!(Test-Path $jar)) { throw 'Build the application first.' }
$backendPort = Get-NetTCPConnection -State Listen -LocalPort 18080 -ErrorAction SilentlyContinue
if ($backendPort) { throw 'Port 18080 is occupied. No existing process was changed.' }
$frontendPort = Get-NetTCPConnection -State Listen -LocalPort 9099 -ErrorAction SilentlyContinue
if ($frontendPort) { throw 'Port 9099 is occupied. No existing process was changed.' }
if ($Lan) {
    $addresses = Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -ne '127.0.0.1' -and $_.IPAddress -notlike '169.254.*' }
    $origins = @('http://localhost:9099', 'http://127.0.0.1:9099', 'http://localhost:18080', 'http://127.0.0.1:18080')
    $origins += @('http://yun.byca.cn:9099', 'https://yun.byca.cn:9099', 'http://yun.byca.cn', 'https://yun.byca.cn')
    foreach ($address in $addresses) { $origins += "http://$($address.IPAddress):9099" }
    $env:ALLOWED_ORIGINS = $origins -join ','
}
$backend = Start-Process (Join-Path $env:JAVA_HOME 'bin\java.exe') -ArgumentList @('-Dfile.encoding=UTF-8', '-jar', $jar) -WorkingDirectory (Join-Path $ProjectRoot 'backend') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $LocalRoot 'backend.log') -RedirectStandardError (Join-Path $LocalRoot 'backend-error.log')
$backend.Id | Set-Content (Join-Path $LocalRoot 'backend.pid')
$healthy = $false
for ($i = 0; $i -lt 45; $i++) { try { Invoke-RestMethod 'http://127.0.0.1:18080/api/session' -TimeoutSec 2 | Out-Null; $healthy = $true; break } catch { if ($backend.HasExited) { break }; Start-Sleep -Seconds 1 } }
if (!$healthy) { throw 'Backend did not start. See .local/backend.log and backend-error.log.' }
$hostAddress = if ($Lan) { '0.0.0.0' } else { '127.0.0.1' }
$vite = Join-Path $ProjectRoot 'frontend\node_modules\vite\bin\vite.js'
$frontend = Start-Process (Get-Command node.exe).Source -ArgumentList @($vite, '--host', $hostAddress) -WorkingDirectory (Join-Path $ProjectRoot 'frontend') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $LocalRoot 'frontend.log') -RedirectStandardError (Join-Path $LocalRoot 'frontend-error.log')
$frontend.Id | Set-Content (Join-Path $LocalRoot 'frontend.pid')
Write-Host 'Guanxiang is running: http://127.0.0.1:9099  Admin: http://127.0.0.1:9099/#/admin'
if ($Lan) { Write-Host 'LAN mode enabled. Use your computer IPv4 address on port 9099. Do not expose these ports to the public internet.' }
