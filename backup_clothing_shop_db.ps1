<#
.SYNOPSIS
    Secure Pre-DML Backup & Static Validation for clothing_shop_db (Demo Readiness - v4.20).
.DESCRIPTION
    Creates a consistent logical backup using mysqldump with --result-file.
    Performs static validation (header, footer, 34-table verification, threshold warning).
    Path protection: normalized path confinement using [System.IO.Path]::GetFullPath()
    to ensure the backup directory is outside backend and frontend repositories, plus .gitignore guard.
    Guarantees original exception preservation and non-zero exit code on failure.
#>

[CmdletBinding()]
param (
    [string]$DbHost = "127.0.0.1",
    [int]$DbPort = 3306,
    [string]$DbUser = "shop",
    [string]$DbName = "clothing_shop_db",
    [string]$BackupDir = "D:\Website-shop\backups",
    [string]$MySqlDumpPath = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$expectedTableCount = 34
$backupSucceeded = $false
$originalBackupError = $null

# Layer 1: Strict Normalized Path Confinement
function Test-PathIsInsideOrEqual([string]$TargetPath, [string]$ParentRepoPath) {
    $normTarget = [System.IO.Path]::GetFullPath($TargetPath).TrimEnd('\', '/')
    $normParent = [System.IO.Path]::GetFullPath($ParentRepoPath).TrimEnd('\', '/')
    $parentWithSep = $normParent + [System.IO.Path]::DirectorySeparatorChar

    return ($normTarget -eq $normParent) -or `
           ($normTarget.StartsWith($parentWithSep, [System.StringComparison]::OrdinalIgnoreCase))
}

$backendPath  = "D:\Website-shop\backend"
$frontendPath = "D:\Website-shop\frontend"

if ((Test-PathIsInsideOrEqual $BackupDir $backendPath) -or `
    (Test-PathIsInsideOrEqual $BackupDir $frontendPath)) {
    throw "SECURITY_VIOLATION: BackupDir '$BackupDir' must not be inside backend or frontend repository."
}

# Layer 2: Ensure backup directory exists and has Git exclusion guard
if (-not (Test-Path $BackupDir)) {
    New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
}
$backupGitIgnore = Join-Path $BackupDir ".gitignore"
if (-not (Test-Path $backupGitIgnore)) {
    [System.IO.File]::WriteAllText($backupGitIgnore, "*`n!.gitignore`n", [System.Text.UTF8Encoding]::new($false))
}

$timestamp = (Get-Date).ToString("yyyyMMdd_HHmmss")
$backupFile = Join-Path $BackupDir "${DbName}_pre_tax_demo_${timestamp}.sql"

if (Test-Path $backupFile) {
    throw "BACKUP_FILE_EXISTS: Target file '$backupFile' already exists. Aborting to avoid overwrite."
}

if ([string]::IsNullOrWhiteSpace($env:DB_PASSWORD)) {
    throw "CREDENTIAL_MISSING: Environment variable `$env:DB_PASSWORD is not set."
}
$env:MYSQL_PWD = $env:DB_PASSWORD

try {
    Write-Host "[1/3] Running mysqldump for database '$DbName'..."
    $dumpArgs = @(
        "-u", $DbUser,
        "-h", $DbHost,
        "-P", $DbPort,
        "--single-transaction",
        "--quick",
        "--no-tablespaces",
        "--routines",
        "--triggers",
        "--events",
        "--result-file=$backupFile",
        $DbName
    )

    $dumpOutput = & $MySqlDumpPath $dumpArgs 2>&1
    $dumpExitCode = $LASTEXITCODE

    if ($dumpExitCode -ne 0) {
        throw "DUMP_COMMAND_FAILED: mysqldump exited with code $dumpExitCode. Output: $($dumpOutput -join "`n")"
    }

    if (-not (Test-Path $backupFile)) {
        throw "FILE_NOT_FOUND: Backup file was not created on disk."
    }

    $fileItem = Get-Item $backupFile
    if ($fileItem.Length -le 0) {
        throw "EMPTY_BACKUP_FILE: Created backup file has size 0 bytes."
    }
    Write-Host "      Dump completed. File size: $($fileItem.Length) bytes."

    # -------------------------------------------------------------
    # 2. STATIC VALIDATION
    # -------------------------------------------------------------
    Write-Host "[2/3] Performing Static Validation on backup file..."

    # 2.1 Header Check
    $headerLines = Get-Content -Path $backupFile -TotalCount 15
    $headerMatch = $headerLines | Where-Object { $_ -match "MySQL dump" -or $_ -match "Server version" }
    if (-not $headerMatch) {
        throw "HEADER_CHECK_FAILED: Backup header missing standard MySQL dump signature."
    }

    # 2.2 Footer Check
    $footerLines = Get-Content -Path $backupFile -Tail 10
    $footerMatch = $footerLines | Where-Object { $_ -match "-- Dump completed on" }
    if (-not $footerMatch) {
        throw "FOOTER_CHECK_FAILED: Backup file missing completion footer '-- Dump completed on'. Incomplete dump suspected."
    }

    # 2.3 Table Count & Inventory Verification (Exact 34 Base Tables)
    $content = Get-Content -Path $backupFile -Raw
    $tableMatches = [regex]::Matches($content, '(?m)^-- Table structure for table `([^`]+)`')
    $actualTableCount = $tableMatches.Count

    if ($actualTableCount -ne $expectedTableCount) {
        throw "TABLE_COUNT_MISMATCH: Expected $expectedTableCount tables, found $actualTableCount."
    }

    $dumpedTableNames = @($tableMatches | ForEach-Object { $_.Groups[1].Value })
    $criticalTables = @("orders", "order_details", "payments", "tax_configurations", "invoices", "invoice_outbox")
    foreach ($tbl in $criticalTables) {
        if ($dumpedTableNames -notcontains $tbl) {
            throw "CRITICAL_TABLE_MISSING: Table '$tbl' was not found in dump structure."
        }
    }
    Write-Host "      Static structure verified: all $actualTableCount base tables present and accounted for."

    # 2.4 Size Threshold Warning (Informational warning only)
    if ($fileItem.Length -lt 100KB) {
        Write-Warning "Backup file size ($($fileItem.Length) bytes) is below nominal threshold (100 KB). Static checks passed, but size should be monitored."
    } else {
        Write-Host "      Size check passed: nominal threshold met."
    }

    $backupSucceeded = $true
    Write-Host "[3/3] Backup and Static Validation COMPLETED successfully."
    Write-Host "      Backup File: $backupFile"

} catch {
    $originalBackupError = $_.Exception.Message
    Write-Host "[WARN] Backup operation encountered error: $originalBackupError" -ForegroundColor Yellow
} finally {
    Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
    if (-not $backupSucceeded -and (Test-Path $backupFile)) {
        try {
            Remove-Item -Path $backupFile -Force -ErrorAction Stop
            Write-Host "      Cleanup: Corrupted/incomplete backup file deleted."
        } catch {
            Write-Host "[WARN] Failed to delete corrupted backup file: $($_.Exception.Message)" -ForegroundColor Red
        }
    }
}

if (-not $backupSucceeded) {
    $host.SetShouldExit(1)
    throw "BACKUP_PROCESS_FAILED: $originalBackupError"
}
