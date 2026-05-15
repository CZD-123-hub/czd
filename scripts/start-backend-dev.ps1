param(
    [string]$Profile = "dev",
    [switch]$SkipDependencies
)

$ErrorActionPreference = "Stop"

# Keep terminal + JVM output on UTF-8 to avoid Chinese mojibake in logs.
[Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
try {
    & chcp 65001 | Out-Null
}
catch {
    Write-Warning "Failed to switch code page to 65001, continue with current code page."
}

$projectRoot = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $projectRoot "backend"
$envFile = Join-Path $projectRoot ".env"
$composeFile = Join-Path $projectRoot "docker-compose.yml"
$dbPatchFile = Join-Path $PSScriptRoot "db_patch_optional_tables.sql"

function Invoke-Compose {
    param([string[]]$ComposeArgs)

    & docker compose -f $composeFile @ComposeArgs
    if ($LASTEXITCODE -eq 0) {
        return
    }

    Write-Warning "docker compose failed, fallback to docker-compose ..."
    & docker-compose -f $composeFile @ComposeArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to run Docker Compose command."
    }
}

if (-not (Test-Path $envFile)) {
    Write-Error ".env not found at $envFile. Please copy .env.example to .env and fill values first."
}

if (-not (Test-Path $composeFile)) {
    Write-Error "docker-compose.yml not found at $composeFile."
}

if (-not $SkipDependencies) {
    Write-Host "[1/3] Starting dependencies: mysql, redis, neo4j, elasticsearch ..."
    Invoke-Compose -ComposeArgs @("up", "-d", "mysql", "redis", "neo4j", "elasticsearch")
}
else {
    Write-Host "[1/3] Skip dependencies startup (requested by caller)."
}

Write-Host "[2/3] Loading variables from .env ..."
Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq "" -or $line.StartsWith("#")) { return }

    $parts = $line.Split('=', 2)
    if ($parts.Count -ne 2) { return }

    $key = $parts[0].Trim()
    $value = $parts[1].Trim()

    if ($key -ne "") {
        [System.Environment]::SetEnvironmentVariable($key, $value, "Process")
    }
}

$env:SPRING_PROFILES_ACTIVE = $Profile
if (-not $env:JWT_SECRET -or [string]::IsNullOrWhiteSpace($env:JWT_SECRET)) {
    $env:JWT_SECRET = "mySecretKeyForJWTTokenGenerationThatIsLongEnough2024"
    Write-Warning "JWT_SECRET is empty in .env, using fallback dev key. Please set a real secret in .env."
}
if ($env:JWT_SECRET.Length -lt 32) {
    Write-Error "JWT_SECRET length must be at least 32 characters. Current length: $($env:JWT_SECRET.Length)"
}

if (-not $env:MYSQL_HOST -or $env:MYSQL_HOST -eq "mysql") {
    $env:MYSQL_HOST = "localhost"
}
if (-not $env:MYSQL_PORT) {
    $env:MYSQL_PORT = "3306"
}
if (-not $env:REDIS_HOST -or $env:REDIS_HOST -eq "redis") {
    $env:REDIS_HOST = "localhost"
}
if (-not $env:REDIS_PORT) {
    $env:REDIS_PORT = "6379"
}
if (-not $env:NEO4J_URI -or $env:NEO4J_URI -eq "bolt://neo4j:7687") {
    $env:NEO4J_URI = "bolt://localhost:17687"
}
if (-not $env:VECTOR_SEARCH_URIS -or $env:VECTOR_SEARCH_URIS -eq "http://elasticsearch:9200") {
    $env:VECTOR_SEARCH_URIS = "http://localhost:19200"
}

# If Docker MySQL is running with a different host port, align MYSQL_PORT automatically
# to avoid accidentally connecting to another local MySQL instance.
if ($env:MYSQL_HOST -in @("localhost", "127.0.0.1")) {
    try {
        $mysqlPorts = (& docker ps --filter "name=^/ica-mysql$" --format "{{.Ports}}" 2>$null | Select-Object -First 1)
        if ($mysqlPorts -and ($mysqlPorts -match ":(\d+)->3306/tcp")) {
            $dockerMysqlHostPort = $matches[1]
            if ($dockerMysqlHostPort -and $env:MYSQL_PORT -ne $dockerMysqlHostPort) {
                Write-Warning "Detected docker mysql port $dockerMysqlHostPort, overriding MYSQL_PORT=$($env:MYSQL_PORT) to avoid wrong database."
                $env:MYSQL_PORT = $dockerMysqlHostPort
            }
        }
    }
    catch {
        # Keep configured MYSQL_PORT when docker is unavailable.
    }
}
if (-not $env:LOG_CONSOLE_CHARSET) {
    $env:LOG_CONSOLE_CHARSET = "UTF-8"
}

$utf8JvmArgs = "-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"
if ([string]::IsNullOrWhiteSpace($env:JAVA_TOOL_OPTIONS)) {
    $env:JAVA_TOOL_OPTIONS = $utf8JvmArgs
}
elseif ($env:JAVA_TOOL_OPTIONS -notmatch "file\.encoding|sun\.stdout\.encoding|sun\.stderr\.encoding") {
    $env:JAVA_TOOL_OPTIONS = "$($env:JAVA_TOOL_OPTIONS) $utf8JvmArgs"
}

Write-Host "[INFO] Active profile: $($env:SPRING_PROFILES_ACTIVE)"
Write-Host "[INFO] MySQL: $($env:MYSQL_HOST):$($env:MYSQL_PORT)"
Write-Host "[INFO] Redis: $($env:REDIS_HOST):$($env:REDIS_PORT)"
Write-Host "[INFO] Neo4j: $($env:NEO4J_URI)"
Write-Host "[INFO] ES: $($env:VECTOR_SEARCH_URIS)"
Write-Host "[INFO] LOG_CONSOLE_CHARSET: $($env:LOG_CONSOLE_CHARSET)"
Write-Host "[INFO] JAVA_TOOL_OPTIONS: $($env:JAVA_TOOL_OPTIONS)"

[bool]$mysqlCliAvailable = $null -ne (Get-Command mysql -ErrorAction SilentlyContinue)
if ($mysqlCliAvailable -and (Test-Path $dbPatchFile)) {
    Write-Host "[INFO] Applying optional DB patch script: $dbPatchFile"
    try {
        & mysql --host=$env:MYSQL_HOST --port=$env:MYSQL_PORT --user=$env:MYSQL_USER --password=$env:MYSQL_PASSWORD --database=$env:MYSQL_DB --default-character-set=utf8mb4 --execute="source $dbPatchFile" | Out-Null
        if ($LASTEXITCODE -eq 0) {
            Write-Host "[INFO] Optional DB patch applied."
        }
        else {
            Write-Warning "Optional DB patch failed with exit code $LASTEXITCODE. Backend will continue to start."
        }
    }
    catch {
        Write-Warning "Optional DB patch failed: $($_.Exception.Message). Backend will continue to start."
    }
}
elseif (-not $mysqlCliAvailable) {
    Write-Warning "mysql CLI not found, skip optional DB patch."
}

Write-Host "[3/3] Starting backend with profile: $Profile ..."
Push-Location $backendDir
try {
    mvn spring-boot:run
}
finally {
    Pop-Location
}
