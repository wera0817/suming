. (Join-Path $PSScriptRoot 'tools.ps1')
foreach ($name in @('frontend', 'backend', 'mysql')) {
    $pidFile = Join-Path $LocalRoot "$name.pid"
    if (!(Test-Path $pidFile)) { continue }
    $processId = [int](Get-Content $pidFile)
    $process = Get-CimInstance Win32_Process -Filter "ProcessId=$processId" -ErrorAction SilentlyContinue
    if ($process -and $process.CommandLine -and $process.CommandLine.Contains($ProjectRoot)) {
        if ($name -eq 'mysql') {
            $bin = Get-MySqlBin
            & (Join-Path $bin 'mysqladmin.exe') "--defaults-extra-file=$(Join-Path $LocalRoot 'mysql-client.ini')" shutdown
        } else { Stop-Process -Id $processId }
    }
}
Write-Host 'Stopped project processes only.'
