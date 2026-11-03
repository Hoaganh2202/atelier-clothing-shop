<#
.SYNOPSIS
    Phase A: Secure Deployment of Demo Tax Configurations (Demo Readiness - v4.20).
.DESCRIPTION
    Applies controlled demo tax rates for Order #90034 (clothing: 8%, shipping: 8%).
    Uses a temporary UTF-8 No-BOM SQL file to safely deliver DELIMITER $$ statements.
    Integrates GET_LOCK advisory lock and in-transaction atomic checking & insertion.
    Enforces strict lowercase enums, BINARY comparisons, comprehensive conflict detection,
    exact 9-field equality and date coverage post-verification, isolated cleanup, and non-swallowed exit codes.
    Requires Single-Runner Operational Protocol for Demo.
#>

[CmdletBinding()]
param (
    [string]$DbHost = "127.0.0.1",
    [int]$DbPort = 3306,
    [string]$DbUser = "shop",
    [string]$DbName = "clothing_shop_db",
    [string]$MySqlPath = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$routineName = "sp_apply_demo_tax_configurations"
$routineCreatedByThisRun = $false
$tempSqlFile = $null

$executionStatus    = "NOT_ATTEMPTED"
$cleanupStatus      = "NOT_ATTEMPTED"
$verificationStatus = "NOT_ATTEMPTED"
$originalError      = $null
$cleanupError       = $null

function Invoke-MySqlCmd {
    param (
        [string]$Query,
        [switch]$SkipColumnNames,
        [switch]$Batch
    )
    $argsList = @("-u", $DbUser, "-h", $DbHost, "-P", $DbPort, "-D", $DbName, "--default-character-set=utf8mb4")
    if ($SkipColumnNames) { $argsList += "--skip-column-names" }
    if ($Batch) { $argsList += "--batch" }
    $argsList += @("-e", $Query)

    $output = & $MySqlPath $argsList 2>&1
    $exitCode = $LASTEXITCODE

    return [PSCustomObject]@{
        ExitCode = $exitCode
        Output   = ($output -join "`n").Trim()
    }
}

try {
    # -------------------------------------------------------------
    # 0. CREDENTIAL VALIDATION
    # -------------------------------------------------------------
    if ([string]::IsNullOrWhiteSpace($env:DB_PASSWORD)) {
        throw "CREDENTIAL_MISSING: Environment variable `$env:DB_PASSWORD is not set."
    }
    $env:MYSQL_PWD = $env:DB_PASSWORD

    # -------------------------------------------------------------
    # 1. PRE-CHECK: CHECK EXISTING STORED PROCEDURE
    # -------------------------------------------------------------
    Write-Host "[1/5] Checking information_schema for pre-existing routine '$routineName'..."
    $preCheckQuery = "SELECT COUNT(*) FROM information_schema.routines WHERE routine_schema = '$DbName' AND routine_name = '$routineName';"
    $preCheckRes = Invoke-MySqlCmd -Query $preCheckQuery -SkipColumnNames -Batch
    if ($preCheckRes.ExitCode -ne 0) {
        throw "PRECHECK_QUERY_FAILED: Exit code $($preCheckRes.ExitCode). Output: $($preCheckRes.Output)"
    }

    $existingCount = 0
    if (-not [int]::TryParse($preCheckRes.Output, [ref]$existingCount)) {
        throw "PRECHECK_PARSE_FAILED: Invalid output format: $($preCheckRes.Output)"
    }

    if ($existingCount -gt 0) {
        throw "ROUTINE_PREEXISTS_CONFLICT: Routine '$routineName' already exists in '$DbName'. Manual inspection required. Aborting."
    }
    Write-Host "      Routine does not exist. Safe to proceed."

    # -------------------------------------------------------------
    # 2. CREATE STORED PROCEDURE VIA TEMPORARY SQL FILE (NO BOM)
    # -------------------------------------------------------------
    Write-Host "[2/5] Preparing and deploying stored procedure '$routineName'..."

    $procedureSqlContent = @"
DELIMITER `$`$

CREATE PROCEDURE $routineName()
proc_body: BEGIN
    DECLARE v_lock_acquired INT DEFAULT 0;
    DECLARE v_intersecting_count INT DEFAULT 0;
    DECLARE v_clothing_exact_count INT DEFAULT 0;
    DECLARE v_shipping_exact_count INT DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        DO RELEASE_LOCK('clothing_shop_db_tax_deployment_lock');
        RESIGNAL;
    END;

    -- Concurrency Guard: Advisory Named Lock (5s timeout)
    SELECT GET_LOCK('clothing_shop_db_tax_deployment_lock', 5) INTO v_lock_acquired;
    IF v_lock_acquired != 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Deployment lock busy: another deployment process is active.';
    END IF;

    -- Start transaction before reading/checking configurations
    START TRANSACTION;

    -- Count any intersecting clothing or shipping records in 2026 (including NULL dates)
    SELECT COUNT(*) INTO v_intersecting_count
    FROM tax_configurations
    WHERE tax_category IN ('clothing', 'shipping')
      AND (
          (effective_from <= '2026-12-31 23:59:59' AND effective_to >= '2026-01-01 00:00:00')
          OR effective_from IS NULL
          OR effective_to IS NULL
      );

    -- Count exact match for clothing (taxable, 0.0800, VAT8, inclusive, active, covering 2026)
    SELECT COUNT(*) INTO v_clothing_exact_count
    FROM tax_configurations
    WHERE BINARY tax_category = 'clothing'
      AND BINARY tax_treatment = 'taxable'
      AND tax_rate <=> 0.0800
      AND BINARY tax_code = 'VAT8'
      AND BINARY legal_reference = 'Demo: NQ 204/2025/QH15 & ND 174/2025/ND-CP'
      AND BINARY pricing_basis = 'inclusive'
      AND is_active = 1
      AND effective_from <= '2026-01-01 00:00:00'
      AND effective_to >= '2026-12-31 23:59:59';

    -- Count exact match for shipping (taxable, 0.0800, VAT8, inclusive, active, covering 2026)
    SELECT COUNT(*) INTO v_shipping_exact_count
    FROM tax_configurations
    WHERE BINARY tax_category = 'shipping'
      AND BINARY tax_treatment = 'taxable'
      AND tax_rate <=> 0.0800
      AND BINARY tax_code = 'VAT8'
      AND BINARY legal_reference = 'Demo assumption: Transport fee VAT 8%'
      AND BINARY pricing_basis = 'inclusive'
      AND is_active = 1
      AND effective_from <= '2026-01-01 00:00:00'
      AND effective_to >= '2026-12-31 23:59:59';

    -- Case 1: Both exact matches already exist and no extraneous intersecting records -> NO-OP
    IF v_clothing_exact_count = 1 AND v_shipping_exact_count = 1 AND v_intersecting_count = 2 THEN
        COMMIT;
        DO RELEASE_LOCK('clothing_shop_db_tax_deployment_lock');
        SELECT 'NO-OP: Exact demo tax configurations already present and verified.' AS result_message;
        LEAVE proc_body;
    END IF;

    -- Case 2: Clean slate -> Insert both in atomic transaction
    IF v_intersecting_count = 0 THEN
        INSERT INTO tax_configurations (
            tax_category, tax_treatment, tax_rate, tax_code,
            legal_reference, pricing_basis, effective_from, effective_to, is_active
        ) VALUES (
            'clothing', 'taxable', 0.0800, 'VAT8',
            'Demo: NQ 204/2025/QH15 & ND 174/2025/ND-CP',
            'inclusive', '2026-01-01 00:00:00', '2026-12-31 23:59:59', 1
        ), (
            'shipping', 'taxable', 0.0800, 'VAT8',
            'Demo assumption: Transport fee VAT 8%',
            'inclusive', '2026-01-01 00:00:00', '2026-12-31 23:59:59', 1
        );

        -- In-transaction post-verification
        SELECT COUNT(*) INTO v_clothing_exact_count
        FROM tax_configurations
        WHERE BINARY tax_category = 'clothing'
          AND BINARY tax_treatment = 'taxable'
          AND tax_rate <=> 0.0800
          AND BINARY tax_code = 'VAT8'
          AND BINARY legal_reference = 'Demo: NQ 204/2025/QH15 & ND 174/2025/ND-CP'
          AND BINARY pricing_basis = 'inclusive'
          AND is_active = 1
          AND effective_from <= '2026-01-01 00:00:00'
          AND effective_to >= '2026-12-31 23:59:59';

        SELECT COUNT(*) INTO v_shipping_exact_count
        FROM tax_configurations
        WHERE BINARY tax_category = 'shipping'
          AND BINARY tax_treatment = 'taxable'
          AND tax_rate <=> 0.0800
          AND BINARY tax_code = 'VAT8'
          AND BINARY legal_reference = 'Demo assumption: Transport fee VAT 8%'
          AND BINARY pricing_basis = 'inclusive'
          AND is_active = 1
          AND effective_from <= '2026-01-01 00:00:00'
          AND effective_to >= '2026-12-31 23:59:59';

        SELECT COUNT(*) INTO v_intersecting_count
        FROM tax_configurations
        WHERE tax_category IN ('clothing', 'shipping')
          AND (
              (effective_from <= '2026-12-31 23:59:59' AND effective_to >= '2026-01-01 00:00:00')
              OR effective_from IS NULL
              OR effective_to IS NULL
          );

        IF v_clothing_exact_count = 1 AND v_shipping_exact_count = 1 AND v_intersecting_count = 2 THEN
            COMMIT;
            DO RELEASE_LOCK('clothing_shop_db_tax_deployment_lock');
            SELECT 'SUCCESS: Demo tax configurations successfully applied and committed.' AS result_message;
        ELSE
            ROLLBACK;
            DO RELEASE_LOCK('clothing_shop_db_tax_deployment_lock');
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Post-verification failed: inserted records do not match specification.';
        END IF;
    ELSE
        -- Case 3: Conflicting, partial, or anomalous records exist -> Fail closed
        ROLLBACK;
        DO RELEASE_LOCK('clothing_shop_db_tax_deployment_lock');
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Conflict detected: incompatible or partial tax configurations exist for clothing/shipping in 2026.';
    END IF;
END proc_body`$`$

DELIMITER ;
"@

    $tempSqlFile = [System.IO.Path]::Combine([System.IO.Path]::GetTempPath(), "deploy_routine_$([System.Guid]::NewGuid().ToString('N')).sql")
    $utf8NoBom = [System.Text.UTF8Encoding]::new($false)
    [System.IO.File]::WriteAllText($tempSqlFile, $procedureSqlContent, $utf8NoBom)

    $normalizedPath = $tempSqlFile.Replace('\', '/')
    $createRes = Invoke-MySqlCmd -Query "source $normalizedPath"
    if ($createRes.ExitCode -ne 0) {
        throw "CREATE_PROCEDURE_FAILED: Exit code $($createRes.ExitCode). Output: $($createRes.Output)"
    }

    $routineCreatedByThisRun = $true
    Write-Host "      Routine '$routineName' deployed successfully."

    # -------------------------------------------------------------
    # 3. CALL STORED PROCEDURE
    # -------------------------------------------------------------
    Write-Host "[3/5] Executing stored procedure 'CALL $routineName();'..."
    $callRes = Invoke-MySqlCmd -Query "CALL $routineName();" -Batch
    if ($callRes.ExitCode -ne 0) {
        throw "CALL_PROCEDURE_FAILED: Exit code $($callRes.ExitCode). Output: $($callRes.Output)"
    }
    Write-Host "      Procedure execution output: $($callRes.Output)"

    # -------------------------------------------------------------
    # 4. DEEP DATABASE POST-VERIFICATION (9 FIELDS)
    # -------------------------------------------------------------
    Write-Host "[4/5] Performing deep post-verification (9 fields) on table tax_configurations..."
    $verifyQuery = @"
SELECT tax_category, tax_treatment, tax_rate, tax_code, legal_reference, pricing_basis, is_active,
       DATE_FORMAT(effective_from, '%Y-%m-%d %H:%i:%s'),
       DATE_FORMAT(effective_to, '%Y-%m-%d %H:%i:%s')
FROM tax_configurations
WHERE tax_category IN ('clothing', 'shipping')
ORDER BY tax_category;
"@
    $verifyRes = Invoke-MySqlCmd -Query $verifyQuery -Batch -SkipColumnNames
    if ($verifyRes.ExitCode -ne 0) {
        throw "VERIFICATION_QUERY_FAILED: Exit code $($verifyRes.ExitCode). Output: $($verifyRes.Output)"
    }

    $rows = $verifyRes.Output -split "`r?`n" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    if ($rows.Count -ne 2) {
        throw "VERIFICATION_COUNT_MISMATCH: Expected exactly 2 records, found $($rows.Count)."
    }

    $verifiedCategories = @{}
    foreach ($row in $rows) {
        $cols = $row -split "`t"
        if ($cols.Count -lt 9) {
            throw "VERIFICATION_COLUMN_MISMATCH: Expected 9 columns, got $($cols.Count) in row: $row"
        }
        $category  = $cols[0].Trim()
        $treatment = $cols[1].Trim()
        $rate      = $cols[2].Trim()
        $code      = $cols[3].Trim()
        $legalRef  = $cols[4].Trim()
        $basis     = $cols[5].Trim()
        $active    = $cols[6].Trim()
        $effFrom   = $cols[7].Trim()
        $effTo     = $cols[8].Trim()

        if ($category -notin @("clothing", "shipping")) {
            throw "VERIFICATION_INVALID_CATEGORY: Unexpected category '$category'."
        }
        if ($treatment -ne "taxable") {
            throw "VERIFICATION_INVALID_TREATMENT: Category '$category' has treatment '$treatment' (expected 'taxable')."
        }
        if ($rate -ne "0.0800") {
            throw "VERIFICATION_INVALID_RATE: Category '$category' has rate '$rate' (expected '0.0800')."
        }
        if ($code -ne "VAT8") {
            throw "VERIFICATION_INVALID_CODE: Category '$category' has code '$code' (expected 'VAT8')."
        }
        if ($basis -ne "inclusive") {
            throw "VERIFICATION_INVALID_BASIS: Category '$category' has basis '$basis' (expected 'inclusive')."
        }
        if ($active -ne "1") {
            throw "VERIFICATION_INVALID_ACTIVE: Category '$category' has is_active '$active' (expected '1')."
        }
        if ($effFrom -gt "2026-01-01 00:00:00") {
            throw "VERIFICATION_INVALID_EFF_FROM: Category '$category' effective_from '$effFrom' starts after 2026-01-01."
        }
        if ($effTo -lt "2026-12-31 23:59:59") {
            throw "VERIFICATION_INVALID_EFF_TO: Category '$category' effective_to '$effTo' ends before 2026-12-31."
        }

        # Exact legal_reference matching (zero false-positives)
        if ($category -eq "clothing") {
            $expectedClothingRef = "Demo: NQ 204/2025/QH15 & ND 174/2025/ND-CP"
            if ($legalRef -ne $expectedClothingRef) {
                throw "VERIFICATION_INVALID_LEGAL_REF: Category 'clothing' legal_reference mismatch. Expected exactly '$expectedClothingRef', got '$legalRef'."
            }
        } elseif ($category -eq "shipping") {
            $expectedShippingRef = "Demo assumption: Transport fee VAT 8%"
            if ($legalRef -ne $expectedShippingRef) {
                throw "VERIFICATION_INVALID_LEGAL_REF: Category 'shipping' legal_reference mismatch. Expected exactly '$expectedShippingRef', got '$legalRef'."
            }
        }

        $verifiedCategories[$category] = $true
    }

    if (-not ($verifiedCategories.ContainsKey("clothing") -and $verifiedCategories.ContainsKey("shipping"))) {
        throw "VERIFICATION_CATEGORY_MISSING: Missing verified records for clothing or shipping."
    }

    $verificationStatus = "VERIFICATION_PASSED"
    $executionStatus    = "SUCCESS"
    Write-Host "      Deep verification passed: 7 exact-equality fields and 2 date coverage conditions fully asserted."

} catch {
    $executionStatus = "FAILED"
    $originalError   = $_.Exception.Message
    if ($verificationStatus -eq "NOT_ATTEMPTED") {
        $verificationStatus = "VERIFICATION_FAILED"
    }
    Write-Host "[WARN] Execution block encountered error: $originalError" -ForegroundColor Yellow
} finally {
    # -------------------------------------------------------------
    # 5. SAFE ISOLATED CLEANUP BLOCK
    # -------------------------------------------------------------
    Write-Host "[5/5] Executing isolated cleanup block..."
    $cleanupErrors = @()

    # 5.1 Delete temporary SQL file
    $tempFileCleaned = $true
    if ($tempSqlFile -and (Test-Path $tempSqlFile)) {
        try {
            Remove-Item -Path $tempSqlFile -Force -ErrorAction Stop
            Write-Host "      Temporary SQL file removed."
        } catch {
            $tempFileCleaned = $false
            $cleanupErrors += "TEMP_FILE_CLEANUP_FAILED: $($_.Exception.Message)"
        }
    }

    # 5.2 Drop Procedure only if created by this run
    $routineCleaned = $false
    if ($routineCreatedByThisRun) {
        try {
            Write-Host "      Dropping procedure '$routineName' (created by this run)..."
            $dropRes = Invoke-MySqlCmd -Query "DROP PROCEDURE $routineName;"
            if ($dropRes.ExitCode -ne 0) {
                $cleanupErrors += "DROP_COMMAND_FAILED: Exit code $($dropRes.ExitCode). Output: $($dropRes.Output)"
            } else {
                $checkDropRes = Invoke-MySqlCmd -Query $preCheckQuery -SkipColumnNames -Batch
                if ($checkDropRes.ExitCode -ne 0) {
                    $cleanupErrors += "ROUTINE_VERIFY_QUERY_FAILED: Exit code $($checkDropRes.ExitCode). Output: $($checkDropRes.Output)"
                } else {
                    $remainingCount = 0
                    if (-not [int]::TryParse($checkDropRes.Output, [ref]$remainingCount)) {
                        $cleanupErrors += "ROUTINE_PARSE_FAILED_AFTER_DROP: Output: $($checkDropRes.Output)"
                    } elseif ($remainingCount -ne 0) {
                        $cleanupErrors += "ROUTINE_STILL_EXISTS: Found $remainingCount instances in information_schema after DROP."
                    } else {
                        $routineCleaned = $true
                        Write-Host "      Cleanup verified: routine successfully removed from information_schema."
                    }
                }
            }
        } catch {
            $cleanupErrors += "ROUTINE_CLEANUP_EXCEPTION: $($_.Exception.Message)"
        }
    } else {
        $cleanupStatus = "SKIPPED_NOT_CREATED"
        Write-Host "      Cleanup skipped: routine was not created by this run."
    }

    # Determine cleanupStatus strictly
    if ($routineCreatedByThisRun) {
        if ($routineCleaned -and $tempFileCleaned -and ($cleanupErrors.Count -eq 0)) {
            $cleanupStatus = "CLEANUP_SUCCESS"
        } else {
            $cleanupStatus = "CLEANUP_FAILED"
            $cleanupError  = $cleanupErrors -join " | "
        }
    } elseif ($cleanupErrors.Count -gt 0) {
        $cleanupStatus = "CLEANUP_FAILED"
        $cleanupError  = $cleanupErrors -join " | "
    }

    # 5.3 Revoke process credential
    Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue

    # 5.4 Output Structured Execution & Cleanup Summary
    Write-Host "`n========================================================"
    Write-Host "           PHASE A EXECUTION & CLEANUP SUMMARY          "
    Write-Host "========================================================"
    [PSCustomObject]@{
        EXECUTION_STATUS    = $executionStatus
        CLEANUP_STATUS      = $cleanupStatus
        VERIFICATION_STATUS = $verificationStatus
        ORIGINAL_ERROR      = if ($originalError) { $originalError } else { "None" }
        CLEANUP_ERROR       = if ($cleanupError) { $cleanupError } else { "None" }
    } | Format-List
}

# -------------------------------------------------------------
# 6. TERMINATING STATUS EVALUATION (EXPLICIT EXIT CODE)
# -------------------------------------------------------------
$isOverallSuccess = ($executionStatus -eq "SUCCESS") -and `
                    ($cleanupStatus -eq "CLEANUP_SUCCESS") -and `
                    ($verificationStatus -eq "VERIFICATION_PASSED") -and `
                    [string]::IsNullOrEmpty($originalError) -and `
                    [string]::IsNullOrEmpty($cleanupError)

if (-not $isOverallSuccess) {
    $errList = @()
    if (-not [string]::IsNullOrEmpty($originalError)) { $errList += "Execution Error: [$originalError]" }
    if (-not [string]::IsNullOrEmpty($cleanupError))  { $errList += "Cleanup Error: [$cleanupError]" }
    $combined = if ($errList.Count -gt 0) { $errList -join " | " } else { "Deployment verification failed." }

    $host.SetShouldExit(1)
    throw "PHASE_A_DEPLOYMENT_FAILED: $combined"
}
