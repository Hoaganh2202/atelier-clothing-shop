# ==============================================================================
# run-local.ps1 — Local Onboarding & Backend Startup Script
# Safely loads environment variables from .env without echoing secrets to console,
# then launches Spring Boot with Maven.
# ==============================================================================
[CmdletBinding()]
param()

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$EnvFile = Join-Path $ScriptDir ".env"

if (-not (Test-Path $EnvFile)) {
    Write-Host "[WARNING] File .env not found in $ScriptDir" -ForegroundColor Yellow
    Write-Host "Please copy .env.example to .env and fill in your DB_PASSWORD and JWT_SECRET." -ForegroundColor Yellow
    exit 1
}

Write-Host "[INFO] Loading environment variables from .env..." -ForegroundColor Cyan

Get-Content -Path $EnvFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
        $parts = $line.Split("=", 2)
        $key = $parts[0].Trim()
        $value = $parts[1].Trim()
        if ($key) {
            # Strip enclosing single/double quotes if present
            if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
                $value = $value.Substring(1, $value.Length - 2)
            }
            [System.Environment]::SetEnvironmentVariable($key, $value, "Process")

            # Map Spring Boot relaxed binding aliases for maximum compatibility
            switch ($key) {
                "SEED_DEV_ENABLED"    { [System.Environment]::SetEnvironmentVariable("APP_SEED_DEV_ENABLED", $value, "Process") }
                "SEED_ADMIN_EMAIL"    { [System.Environment]::SetEnvironmentVariable("APP_SEED_DEV_ADMIN_EMAIL", $value, "Process") }
                "SEED_ADMIN_PASSWORD" { [System.Environment]::SetEnvironmentVariable("APP_SEED_DEV_ADMIN_PASSWORD", $value, "Process") }
                "DB_PASSWORD"         { [System.Environment]::SetEnvironmentVariable("SPRING_DATASOURCE_PASSWORD", $value, "Process") }
                "JWT_SECRET"          { [System.Environment]::SetEnvironmentVariable("APP_JWT_SECRET", $value, "Process") }
            }
        }
    }
}

# Validate required variables without printing their actual values
$requiredVars = @("DB_PASSWORD", "JWT_SECRET")
$missingVars = @()
foreach ($var in $requiredVars) {
    $val = [System.Environment]::GetEnvironmentVariable($var, "Process")
    if (-not $val) {
        $missingVars += $var
    }
}

if ($missingVars.Count -gt 0) {
    Write-Host "[ERROR] Missing required environment variable(s) in .env: $($missingVars -join ', ')" -ForegroundColor Red
    exit 1
}

Write-Host "[INFO] Environment variables loaded successfully (credentials masked)." -ForegroundColor Green
Write-Host "[INFO] Starting Spring Boot application..." -ForegroundColor Cyan

Push-Location $ScriptDir
try {
    mvn spring-boot:run
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
} finally {
    Pop-Location
}
