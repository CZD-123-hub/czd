param(
    [string]$Profile = "dev"
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $projectRoot "backend"
$envFile = Join-Path $projectRoot ".env"

if (-not (Test-Path $envFile)) {
    Write-Error ".env not found at $envFile. Please copy .env.example to .env and fill values first."
}

Write-Host "[1/3] Starting dependencies: mysql, neo4j, redis ..."
docker-compose -f (Join-Path $projectRoot "docker-compose.yml") up -d mysql neo4j redis

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
if (-not $env:REDIS_HOST -or $env:REDIS_HOST -eq "redis") {
    $env:REDIS_HOST = "localhost"
}
if (-not $env:REDIS_PORT) {
    $env:REDIS_PORT = "6379"
}
if (-not $env:MYSQL_HOST -or $env:MYSQL_HOST -eq "mysql") {
    $env:MYSQL_HOST = "localhost"
}
if (-not $env:MYSQL_PORT) {
    $env:MYSQL_PORT = "3306"
}
if (-not $env:NEO4J_URI -or $env:NEO4J_URI -eq "bolt://neo4j:7687") {
    $env:NEO4J_URI = "bolt://localhost:7687"
}

Write-Host "[INFO] Active profile: $($env:SPRING_PROFILES_ACTIVE)"
Write-Host "[INFO] JWT_SECRET length: $($env:JWT_SECRET.Length)"
Write-Host "[INFO] REDIS_HOST: $($env:REDIS_HOST):$($env:REDIS_PORT)"

Write-Host "[3/3] Starting backend with profile: $Profile ..."
Push-Location $backendDir
try {
    mvn spring-boot:run
}
finally {
    Pop-Location
}
