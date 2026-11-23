. (Join-Path $PSScriptRoot 'tools.ps1')
$config = Read-Config
Set-AppEnvironment $config
Start-ProjectMySql $config
$bin = Get-MySqlBin
$sql = "CREATE DATABASE IF NOT EXISTS guanxiang_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; GRANT ALL PRIVILEGES ON guanxiang_test.* TO 'guanxiang'@'127.0.0.1';"
$sql | & (Join-Path $bin 'mysql.exe') "--defaults-extra-file=$(Join-Path $LocalRoot 'mysql-client.ini')"
if ($LASTEXITCODE -ne 0) { throw 'Could not prepare isolated test database.' }
$env:DB_URL = "jdbc:mysql://127.0.0.1:$($config.mysqlPort)/guanxiang_test?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
$env:APP_PORT = '18180'; $env:ALLOWED_ORIGINS = 'http://127.0.0.1:5174'; $env:E2E_ADMIN_PASSWORD = $config.adminPassword
$jar = Join-Path $ProjectRoot 'backend\target\guanxiang-0.1.0.jar'
if (Get-NetTCPConnection -State Listen -LocalPort 18180 -ErrorAction SilentlyContinue) { throw 'Test port 18180 is occupied.' }
$process = Start-Process (Join-Path $env:JAVA_HOME 'bin\java.exe') -ArgumentList @('-Dfile.encoding=UTF-8', '-jar', $jar) -WorkingDirectory $ProjectRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $LocalRoot 'test-backend.log') -RedirectStandardError (Join-Path $LocalRoot 'test-backend-error.log')
try {
    $healthy = $false
    for ($i = 0; $i -lt 45; $i++) { try { Invoke-RestMethod 'http://127.0.0.1:18180/api/session' -TimeoutSec 2 | Out-Null; $healthy = $true; break } catch { if ($process.HasExited) { break }; Start-Sleep -Seconds 1 } }
    if (!$healthy) { throw 'Test backend did not start. See .local/test-backend.log.' }
    Push-Location (Join-Path $ProjectRoot 'frontend')
    try { & npx.cmd playwright install chromium; & npm.cmd run test:e2e; if ($LASTEXITCODE -ne 0) { throw 'Browser tests failed.' } } finally { Pop-Location }
    $check = "SELECT COUNT(*) AS unexpected_columns FROM information_schema.columns WHERE table_schema='guanxiang_test' AND (data_type LIKE '%blob%' OR column_name IN ('image','photo')); SELECT operation,status,COUNT(*) AS calls FROM guanxiang_test.run_logs GROUP BY operation,status;"
    $check | & (Join-Path $bin 'mysql.exe') "--defaults-extra-file=$(Join-Path $LocalRoot 'mysql-client.ini')"
} finally {
    $current = Get-CimInstance Win32_Process -Filter "ProcessId=$($process.Id)" -ErrorAction SilentlyContinue
    if ($current -and $current.CommandLine.Contains($jar)) { Stop-Process -Id $process.Id }
}
