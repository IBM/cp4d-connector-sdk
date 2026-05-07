#!/usr/bin/env pwsh

<#
.SYNOPSIS
    Test connector discovery using the CPD Connection Service API.

.DESCRIPTION
    This script tests one or more REST API connectors by calling
    the CPD Connection Service discover assets endpoint
    without creating a saved connection.

    Supports:
    - API Key authentication
    - Username/Password authentication
    - Multiple connectors in one run
    - Optional SSL validation disable
    - Detailed success/failure reporting

.PARAMETER PropertiesFile
    Path to the properties file.

.EXAMPLE
    ./test-discovery.ps1

.EXAMPLE
    ./test-discovery.ps1 -PropertiesFile "my-config.properties"
#>

param(
    [string]$PropertiesFile = "test-discovery.properties"
)

# ============================================================
# Load required assemblies
# ============================================================

Add-Type -AssemblyName System.Web

# ============================================================
# Output helpers
# ============================================================

function Write-Info {
    param([string]$Message)
    Write-Host $Message
}

function Write-Success {
    param([string]$Message)
    Write-Host $Message
}

function Write-Warning-Custom {
    param([string]$Message)
    Write-Host $Message
}

function Write-Error-Custom {
    param([string]$Message)
    Write-Host $Message
}

# ============================================================
# Validate properties file
# ============================================================

if (-not (Test-Path $PropertiesFile)) {
    Write-Error-Custom "Properties file not found: $PropertiesFile"
    exit 1
}

Write-Info "Loading configuration from: $PropertiesFile"

# ============================================================
# Read properties file
# ============================================================

function Read-PropertiesFile {
    param(
        [string]$FilePath
    )

    $properties = @{}

    Get-Content $FilePath | ForEach-Object {
        if ($_ -match "^\s*([^#][^=]*)=(.*)$") {
            $key = $matches[1].Trim()
            $value = $matches[2].Trim()
            $properties[$key] = $value
        }
    }

    return $properties
}

$config = Read-PropertiesFile -FilePath $PropertiesFile

# ============================================================
# Configuration values
# ============================================================

$cpdUrl                   = $config["CPD_URL"]
$apikey                   = $config["APIKEY"]
$username                 = $config["USERNAME"]
$password                 = $config["PASSWORD"]
$authUri                  = $config["AUTH_URI"]
$datasourceTypesStr       = $config["DATASOURCE_TYPES"]
$discoveryPathsStr        = $config["DISCOVERY_PATHS"]
$connectionPropertiesStr  = $config["CONNECTION_PROPERTIES"]
$sslValidation            = $config["SSL_CERTIFICATE_VALIDATION"]

if (-not $sslValidation) {
    $sslValidation = "false"
}

# ============================================================
# Validation
# ============================================================

if (-not $cpdUrl) {
    Write-Error-Custom "CPD_URL is missing"
    exit 1
}

if (-not $authUri) {
    Write-Error-Custom "AUTH_URI is missing"
    exit 1
}

if (-not $datasourceTypesStr) {
    Write-Error-Custom "DATASOURCE_TYPES is missing"
    exit 1
}

if (-not $discoveryPathsStr) {
    Write-Error-Custom "DISCOVERY_PATHS is missing"
    exit 1
}

if (-not $connectionPropertiesStr) {
    Write-Error-Custom "CONNECTION_PROPERTIES is missing"
    exit 1
}

$usingApiKey = -not [string]::IsNullOrWhiteSpace($apikey)
$usingUserPass = (
    -not [string]::IsNullOrWhiteSpace($username) -and
    -not [string]::IsNullOrWhiteSpace($password)
)

if (-not $usingApiKey -and -not $usingUserPass) {
    Write-Error-Custom "Provide either APIKEY or USERNAME/PASSWORD"
    exit 1
}

if ($usingApiKey -and $usingUserPass) {
    Write-Error-Custom "Use only one authentication method"
    exit 1
}

# ============================================================
# Parse connector arrays
# ============================================================

$datasourceTypes = @($datasourceTypesStr -split "," | ForEach-Object { $_.Trim() })

$discoveryPaths = @($discoveryPathsStr -split "," | ForEach-Object { $_.Trim() })

$connectionPropertiesArray = @($connectionPropertiesStr -split "\|" | ForEach-Object { $_.Trim() })

if (
    $datasourceTypes.Count -ne $discoveryPaths.Count -or
    $datasourceTypes.Count -ne $connectionPropertiesArray.Count
) {
    Write-Error-Custom "Mismatch in connector configuration counts"
    Write-Host "Datasource types: $($datasourceTypes.Count)"
    Write-Host "Discovery paths: $($discoveryPaths.Count)"
    Write-Host "Connection properties: $($connectionPropertiesArray.Count)"
    exit 1
}

# ============================================================
# SSL validation
# ============================================================

if ($sslValidation.ToLower() -eq "false") {

    Write-Warning-Custom "SSL certificate validation disabled"

    add-type @"
        using System.Net;
        using System.Security.Cryptography.X509Certificates;
        public class TrustAllCertsPolicy : ICertificatePolicy {
            public bool CheckValidationResult(
                ServicePoint svcPoint, X509Certificate certificate,
                WebRequest request, int certificateProblem) {
                return true;
            }
        }
"@
    [System.Net.ServicePointManager]::CertificatePolicy = New-Object TrustAllCertsPolicy
    [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12 -bor [System.Net.SecurityProtocolType]::Tls11 -bor [System.Net.SecurityProtocolType]::Tls
}

# ============================================================
# Normalize URL
# ============================================================

$cpdUrl = $cpdUrl.TrimEnd("/")

# ============================================================
# Authentication
# ============================================================

Write-Info ""
Write-Info "========================================"
Write-Info "Obtaining bearer token"
Write-Info "========================================"

try {

    if ($usingApiKey) {

        Write-Info "Using API Key authentication"

        $tokenBody = @{
            grant_type = "urn:ibm:params:oauth:grant-type:apikey"
            apikey     = $apikey
        }

        $tokenResponse = Invoke-RestMethod `
            -Method POST `
            -Uri $authUri `
            -ContentType "application/x-www-form-urlencoded" `
            -Body $tokenBody
    }
    else {

        Write-Info "Using Username/Password authentication"

        $tokenBody = @{
            username = $username
            password = $password
        } | ConvertTo-Json

        $tokenResponse = Invoke-RestMethod `
            -Method POST `
            -Uri $authUri `
            -ContentType "application/json" `
            -Body $tokenBody
    }
}
catch {

    Write-Error-Custom "Failed to obtain bearer token"
    Write-Error-Custom $_.Exception.Message
    exit 1
}

$cpdToken = $tokenResponse.access_token

if (-not $cpdToken) {
    $cpdToken = $tokenResponse.token
}

if (-not $cpdToken) {
    Write-Error-Custom "Access token not found in response"
    exit 1
}

Write-Success "Bearer token obtained successfully"

# ============================================================
# API setup
# ============================================================

$endpoint = "$cpdUrl/v2/connections/assets"

$headers = @{
    Authorization = "Bearer $cpdToken"
    Accept        = "application/json"
    "Content-Type" = "application/json"
}

Write-Info ""
Write-Info "Endpoint: $endpoint"
Write-Info "Connectors to test: $($datasourceTypes.Count)"

# ============================================================
# Results tracking
# ============================================================

$successCount = 0
$failureCount = 0
$results = @()

# ============================================================
# Test connectors
# ============================================================

for ($i = 0; $i -lt $datasourceTypes.Count; $i++) {

    $datasourceType = $datasourceTypes[$i]
    $discoveryPath = $discoveryPaths[$i]
    $connectionPropertiesRaw = $connectionPropertiesArray[$i]

    Write-Info ""
    Write-Info "========================================"
    Write-Info "Testing connector $($i + 1)"
    Write-Info "========================================"

    Write-Host "Datasource Type : $datasourceType"
    Write-Host "Discovery Path  : $discoveryPath"

    try {

        $connectionProperties = $connectionPropertiesRaw | ConvertFrom-Json

        # Build URL with query parameters
        $encodedPath = [System.Web.HttpUtility]::UrlEncode($discoveryPath)
        $requestUrl = "$endpoint`?path=$encodedPath&fetch=data"

        $requestBody = @{
            datasource_type = $datasourceType
            properties      = $connectionProperties
        }

        $requestJson = $requestBody | ConvertTo-Json -Depth 20

        Write-Info ""
        Write-Info "Request URL:"
        Write-Host $requestUrl

        $response = Invoke-RestMethod `
            -Method POST `
            -Uri $requestUrl `
            -Headers $headers `
            -Body $requestJson

        $assetCount = 0

        if ($response.data) {
            $assetCount = $response.data.Count
        }
        elseif ($response.resources) {
            $assetCount = $response.resources.Count
        }
        elseif ($response.assets) {
            $assetCount = $response.assets.Count
        }

        Write-Success ""
        Write-Success "Discovery successful"
        Write-Success "Assets discovered: $assetCount"

        # Display first asset if any were returned
        if ($assetCount -gt 0) {
            Write-Info ""
            Write-Info "First asset:"
            $firstAsset = $null
            if ($response.data) {
                $firstAsset = $response.data[0]
            }
            elseif ($response.resources) {
                $firstAsset = $response.resources[0]
            }
            elseif ($response.assets) {
                $firstAsset = $response.assets[0]
            }
            
            if ($firstAsset) {
                $firstAssetJson = $firstAsset | ConvertTo-Json -Depth 10
                Write-Host $firstAssetJson
            }
        }

        $successCount++

        $results += [PSCustomObject]@{
            DatasourceType = $datasourceType
            Path           = $discoveryPath
            Status         = "SUCCESS"
            AssetCount     = $assetCount
            Error          = ""
        }
    }
    catch {

        Write-Error-Custom ""
        Write-Error-Custom "Discovery failed"
        Write-Error-Custom $_.Exception.Message

        $responseBody = ""

        try {

            if ($_.Exception.Response) {

                $reader = New-Object System.IO.StreamReader(
                    $_.Exception.Response.GetResponseStream()
                )

                $responseBody = $reader.ReadToEnd()

                if ($responseBody) {
                    Write-Error-Custom "Response body:"
                    Write-Host $responseBody
                }
            }
        }
        catch {
        }

        $failureCount++

        $results += [PSCustomObject]@{
            DatasourceType = $datasourceType
            Path           = $discoveryPath
            Status         = "FAILED"
            AssetCount     = 0
            Error          = $_.Exception.Message
        }
    }
}

# ============================================================
# Summary
# ============================================================

Write-Info ""
Write-Info "========================================"
Write-Info "Test Summary"
Write-Info "========================================"

Write-Host "Total tested : $($datasourceTypes.Count)"
Write-Success "Successful   : $successCount"

if ($failureCount -gt 0) {
    Write-Error-Custom "Failed       : $failureCount"
}

Write-Info ""
Write-Info "Detailed Results"

foreach ($result in $results) {

    if ($result.Status -eq "SUCCESS") {

        Write-Success (
            "SUCCESS | Type: {0} | Path: {1} | Assets: {2}" -f `
            $result.DatasourceType,
            $result.Path,
            $result.AssetCount
        )
    }
    else {

        Write-Error-Custom (
            "FAILED | Type: {0} | Path: {1} | Error: {2}" -f `
            $result.DatasourceType,
            $result.Path,
            $result.Error
        )
    }
}

# ============================================================
# Exit code
# ============================================================

if ($failureCount -gt 0) {

    Write-Info ""
    Write-Info "Troubleshooting Tips"

    Write-Host "1. Verify CPD_URL"
    Write-Host "2. Verify authentication credentials"
    Write-Host "3. Verify AUTH_URI"
    Write-Host "4. Verify datasource types"
    Write-Host "5. Verify connection properties JSON"
    Write-Host "6. Verify connector deployment"

    exit 1
}

Write-Success ""
Write-Success "All connector tests passed"

exit 0