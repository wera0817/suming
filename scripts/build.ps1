. (Join-Path $PSScriptRoot 'tools.ps1')
$env:JAVA_HOME = Get-JavaHome
Push-Location (Join-Path $ProjectRoot 'frontend')
try { & npm.cmd run build; if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' } } finally { Pop-Location }
Push-Location (Join-Path $ProjectRoot 'backend')
try { & (Get-Maven) -B -ntp test package; if ($LASTEXITCODE -ne 0) { throw 'Backend tests or build failed.' } } finally { Pop-Location }
