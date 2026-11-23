$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path $PSScriptRoot -Parent
$LocalRoot = Join-Path $ProjectRoot '.local'

function Get-JavaHome {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) { return $env:JAVA_HOME }
    $candidate = Get-ChildItem (Join-Path $env:USERPROFILE '.jdks') -Directory -ErrorAction SilentlyContinue | Where-Object { Test-Path (Join-Path $_.FullName 'bin\javac.exe') } | Sort-Object Name -Descending | Select-Object -First 1
    if ($candidate) { return $candidate.FullName }
    throw 'Install JDK 21 and set JAVA_HOME before continuing.'
}
function Get-Maven {
    $command = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    $cached = Get-ChildItem (Join-Path $env:USERPROFILE '.m2\wrapper\dists') -Recurse -Filter mvn.cmd -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($cached) { return $cached.FullName }
    $target = Join-Path $LocalRoot 'tools'
    New-Item -ItemType Directory -Force $target | Out-Null
    $zip = Join-Path $target 'maven.zip'
    $url = 'https://archive.apache.org/dist/maven/maven-3/3.9.11/binaries/apache-maven-3.9.11-bin.zip'
    Invoke-WebRequest $url -OutFile $zip
    $expected = (Invoke-RestMethod ($url + '.sha512')).ToString().Trim().Split(' ')[0]
    if ((Get-FileHash $zip -Algorithm SHA512).Hash -ne $expected) { throw 'Maven archive checksum mismatch.' }
    Expand-Archive -LiteralPath $zip -DestinationPath $target -Force
    return Join-Path $target 'apache-maven-3.9.11\bin\mvn.cmd'
}
function Get-MySqlBin {
    $command = Get-Command mysqld.exe -ErrorAction SilentlyContinue
    if ($command) { return Split-Path $command.Source -Parent }
    $candidate = Get-ChildItem 'C:\Program Files\MySQL' -Recurse -Filter mysqld.exe -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($candidate) { return Split-Path $candidate.FullName -Parent }
    throw 'Install MySQL Server 8.4 first. Existing databases are never reused automatically.'
}
function New-RandomHex([int]$Length = 24) {
    $bytes = New-Object byte[] $Length
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return -join ($bytes | ForEach-Object { $_.ToString('x2') })
}
function Start-ProjectMySql($Config) {
    $bin = Get-MySqlBin
    $mysql = Join-Path $bin 'mysql.exe'
    $data = Join-Path $LocalRoot 'mysql'
    $clientFile = Join-Path $LocalRoot 'mysql-client.ini'
    $ready = Join-Path $LocalRoot 'mysql-ready'
    if (!(Test-Path (Join-Path $data 'mysql'))) {
        New-Item -ItemType Directory -Force $data | Out-Null
        & (Join-Path $bin 'mysqld.exe') --no-defaults --initialize-insecure "--datadir=$data" --console
        if ($LASTEXITCODE -ne 0) { throw 'MySQL initialization failed.' }
    }
    $listener = Get-NetTCPConnection -State Listen -LocalPort $Config.mysqlPort -ErrorAction SilentlyContinue
    if ($listener) {
        $process = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener[0].OwningProcess)"
        if (!$process.CommandLine -or !$process.CommandLine.Contains($data)) { throw "Port $($Config.mysqlPort) belongs to another process. Nothing was changed." }
    } else {
        $args = @('--no-defaults', "--datadir=$data", "--port=$($Config.mysqlPort)", '--bind-address=127.0.0.1', '--mysqlx=OFF', '--innodb-buffer-pool-size=64M', '--max-connections=40', '--performance-schema=OFF', "--log-error=$(Join-Path $LocalRoot 'mysql-error.log')", '--console')
        $process = Start-Process (Join-Path $bin 'mysqld.exe') -ArgumentList $args -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $LocalRoot 'mysql.log') -RedirectStandardError (Join-Path $LocalRoot 'mysql-stderr.log')
        $process.Id | Set-Content (Join-Path $LocalRoot 'mysql.pid')
    }
    $available = $false
    for ($i = 0; $i -lt 60; $i++) {
        try { $tcp = New-Object Net.Sockets.TcpClient; $tcp.Connect('127.0.0.1', $Config.mysqlPort); $tcp.Dispose(); $available = $true; break } catch { Start-Sleep -Seconds 1 }
    }
    if (!$available) { throw 'MySQL did not start. See .local/mysql-error.log.' }
    if (!(Test-Path $ready)) {
        $sql = "CREATE DATABASE IF NOT EXISTS guanxiang CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE USER IF NOT EXISTS 'guanxiang'@'127.0.0.1' IDENTIFIED BY '$($Config.dbPassword)'; GRANT ALL PRIVILEGES ON guanxiang.* TO 'guanxiang'@'127.0.0.1'; ALTER USER 'root'@'localhost' IDENTIFIED BY '$($Config.rootPassword)';"
        $sql | & $mysql --no-defaults --host=127.0.0.1 "--port=$($Config.mysqlPort)" --user=root --password= --default-character-set=utf8mb4
        if ($LASTEXITCODE -ne 0) { throw 'Could not create isolated project database.' }
        @('[client]', 'host=127.0.0.1', "port=$($Config.mysqlPort)", 'user=root', "password=$($Config.rootPassword)", 'default-character-set=utf8mb4') | Set-Content -Encoding ASCII $clientFile
        'ready' | Set-Content $ready
    }
}
function Read-Config {
    $path = Join-Path $LocalRoot 'config.json'
    if (!(Test-Path $path)) { throw 'Run scripts/setup.ps1 first.' }
    return Get-Content -Raw $path | ConvertFrom-Json
}
function Set-AppEnvironment($Config) {
    $env:JAVA_HOME = Get-JavaHome
    $env:DB_URL = "jdbc:mysql://127.0.0.1:$($Config.mysqlPort)/guanxiang?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
    $env:DB_USER = 'guanxiang'; $env:DB_PASSWORD = $Config.dbPassword
    $env:ADMIN_USER = 'admin'; $env:ADMIN_PASSWORD = $Config.adminPassword; $env:MASTER_KEY = $Config.masterKey
    $env:APP_PORT = '18080'; $env:APP_HOST = '127.0.0.1'
}
