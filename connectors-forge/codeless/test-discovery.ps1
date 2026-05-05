#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Test connector discovery using the discoverAssetsAnonymously endpoint
.DESCRIPTION
    This script tests one or more REST API connectors by calling the CPD Connection Service API
    to discover assets without creating a saved connection. Supports testing multiple connectors
    in a single run by reading comma-separated values from the properties file.
.PARAMETER PropertiesFile
    Path to the properties file (default: test-discovery.properties)
.EXAMPLE
    .\test-discovery.ps1
    .\test-discovery.ps1 -PropertiesFile "my-config.properties"
#>

param(
    [string]$PropertiesFile = "test-discovery.properties"
)

# Color output functions
function Write-Success { param($Message) Write-Host $Message -ForegroundColor Green }
function Write-Error-Custom { param($Message) Write-Host $Message -ForegroundColor Red }
function Write-Info { param($Message) Write-Host $Message -ForegroundColor Cyan }
function Write-Warning-Custom { param($Message) Write-Host $Message -ForegroundColor Yellow }

# Check if properties file exists
if (-not (Test-Path $PropertiesFile)) {
    Write-Error-Custom "ERROR: Properties file not found: $PropertiesFile"
    Write-Info "Please copy test-discovery.properties.template to $PropertiesFile and configure it."
    exit 1
}

Write-Info "Loading configuration from: $PropertiesFile"

# Function to read properties file
function Read-PropertiesFile {
    param([string]$FilePath)
    
    $properties = @{}
    Get-Content $FilePath | ForEach-Object {
        $line = $_.Trim()
        # Skip comments and empty lines
        if ($line -and -not $line.StartsWith('#')) {
            $parts = $line -split '=', 2
            if ($parts.Count -eq 2) {
                $key = $parts[0].Trim()
                $value = $parts[1].Trim()
                $properties[$key] = $value
            }
        }
    }
    return $properties
}

# Load properties
$config = Read-PropertiesFile -FilePath $PropertiesFile

# Get configuration values
$cpdUrl = $config['CPD_URL']
$apikey = $config['APIKEY']
$username = $config['USERNAME']
$password = $config['PASSWORD']
$authUri = $config['AUTH_URI']
$datasourceTypesStr = $config['DATASOURCE_TYPES']
$discoveryPathsStr = $config['DISCOVERY_PATHS']
$connectionPropertiesStr = $config['CONNECTION_PROPERTIES']

# Validate required parameters
if (-not $cpdUrl) {
    Write-Error-Custom "ERROR: CPD_URL is not set in properties file"
    exit 1
}

# Check authentication credentials
if (-not $apikey -and (-not $username -or -not $password)) {
    Write-Error-Custom "ERROR: Missing authentication credentials"
    Write-Error-Custom "Provide either APIKEY OR both USERNAME and PASSWORD"
    exit 1
}

if ($apikey -and ($username -or $password)) {
    Write-Error-Custom "ERROR: Conflicting authentication methods"
    Write-Error-Custom "Provide either APIKEY OR USERNAME+PASSWORD, not both"
    exit 1
}

if (-not $authUri) {
    Write-Error-Custom "ERROR: AUTH_URI is not set in properties file"
    exit 1
}
if (-not $datasourceTypesStr) {
    Write-Error-Custom "ERROR: DATASOURCE_TYPES is not set in properties file"
    exit 1
}
if (-not $discoveryPathsStr) {
    Write-Error-Custom "ERROR: DISCOVERY_PATHS is not set in properties file"
    exit 1
}
if (-not $connectionPropertiesStr) {
    Write-Error-Custom "ERROR: CONNECTION_PROPERTIES is not set in properties file"
    exit 1
}

# Parse arrays
$datasourceTypes = $datasourceTypesStr -split ',' | ForEach-Object { $_.Trim() }
$discoveryPaths = $discoveryPathsStr -split ',' | ForEach-Object { $_.Trim() }
$connectionPropertiesArray = $connectionPropertiesStr -split '\|' | ForEach-Object { $_.Trim() }

# Validate array lengths match
if ($datasourceTypes.Count -ne $discoveryPaths.Count -or $datasourceTypes.Count -ne $connectionPropertiesArray.Count) {
    Write-Error-Custom "ERROR: Number of datasource types, paths, and connection properties must match"
    Write-Error-Custom "  Datasource types: $($datasourceTypes.Count)"
    Write-Error-Custom "  Discovery paths: $($discoveryPaths.Count)"
    Write-Error-Custom "  Connection properties: $($connectionPropertiesArray.Count)"
    exit 1
}

# Remove trailing slash from CPD URL
$cpdUrl = $cpdUrl.TrimEnd('/')

Write-Info "`n=========================================="
Write-Info "Obtaining Bearer Token..."
Write-Info "=========================================="

# Build authentication request based on method
if ($apikey) {
    Write-Host "Using API Key authentication"
    $tokenBody = @{
        grant_type = "urn:ibm:params:oauth:grant-type:apikey"
        apikey     = $apikey
    }
} else {
    Write-Host "Using Username/Password authentication"
    $tokenBody = @{
        username = $username
        password = $password
    }
}

try {
    $tokenResponse = Invoke-RestMethod `
        -Method Post `
        -Uri $authUri `
        -ContentType "application/x-www-form-urlencoded" `
        -Body $tokenBody
} catch {
    Write-Error-Custom "ERROR: Failed to obtain bearer token"
    if ($apikey) {
        Write-Error-Custom "Please verify your API key is correct"
    } else {
        Write-Error-Custom "Please verify your username and password are correct"
    }
    throw $_
}

# Try to extract token (different field names for Cloud vs on-premises)
$cpdToken = $tokenResponse.access_token
if (-not $cpdToken) {
    $cpdToken = $tokenResponse.token
}

if (-not $cpdToken) {
    Write-Error-Custom "ERROR: Failed to extract access token from response"
    exit 1
}

Write-Success "✓ Bearer token obtained successfully"

# Build the API endpoint
$endpoint = "$cpdUrl/v2/connections/assets"

Write-Info "`nConfiguration:"
Write-Host "  CPD URL: $cpdUrl"
$authMethod = if ($apikey) { "API Key" } else { "Username/Password" }
Write-Host "  Auth Method: $authMethod"
Write-Host "  Endpoint: $endpoint"
Write-Host "  Number of connectors to test: $($datasourceTypes.Count)"

# Prepare headers
$headers = @{
    "Authorization" = "Bearer $cpdToken"
    "Content-Type" = "application/json"
    "Accept" = "application/json"
}

# Track results
$successCount = 0
$failureCount = 0
$results = @()

# Test each connector
for ($i = 0; $i -lt $datasourceTypes.Count; $i++) {
    $datasourceType = $datasourceTypes[$i]
    $discoveryPath = $discoveryPaths[$i]
    $connectionProperties = $connectionPropertiesArray[$i]
    
    Write-Info "`n=========================================="
    Write-Info "Testing Connector $($i + 1) of $($datasourceTypes.Count)"
    Write-Info "=========================================="
    Write-Host "  Datasource Type: $datasourceType"
    Write-Host "  Discovery Path: $discoveryPath"
    
    # Build request body
    $requestBody = @{
        datasource_type = $datasourceType
        properties = $connectionProperties | ConvertFrom-Json
        path = $discoveryPath
        fetch = "data"
    }
    
    $requestBodyJson = $requestBody | ConvertTo-Json -Depth 10
    
    Write-Info "`nRequest Body:"
    Write-Host $requestBodyJson
    
    Write-Info "`nSending request..."
    
    try {
        # Make the API call
        $response = Invoke-RestMethod -Uri $endpoint -Method Post -Headers $headers -Body $requestBodyJson -ErrorAction Stop
        
        Write-Success "`n✓ Discovery successful for $datasourceType"
        
        # Count assets if available
        $assetCount = 0
        if ($response.resources) {
            $assetCount = $response.resources.Count
        } elseif ($response.assets) {
            $assetCount = $response.assets.Count
        }
        
        Write-Success "Discovered $assetCount asset(s)"
        
        $successCount++
        $results += @{
            DatasourceType = $datasourceType
            Path = $discoveryPath
            Status = "SUCCESS"
            AssetCount = $assetCount
        }
        
    } catch {
        Write-Error-Custom "`n✗ Discovery failed for $datasourceType"
        Write-Error-Custom "Error: $($_.Exception.Message)"
        
        if ($_.Exception.Response) {
            $statusCode = $_.Exception.Response.StatusCode.value__
            Write-Error-Custom "Status Code: $statusCode"
            
            try {
                $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
                $responseBody = $reader.ReadToEnd()
                Write-Error-Custom "Response Body:"
                Write-Host $responseBody
            } catch {
                # Ignore errors reading response body
            }
        }
        
        $failureCount++
        $results += @{
            DatasourceType = $datasourceType
            Path = $discoveryPath
            Status = "FAILED"
            Error = $_.Exception.Message
        }
    }
}

# Print summary
Write-Info "`n=========================================="
Write-Info "Test Summary"
Write-Info "=========================================="
Write-Host "Total connectors tested: $($datasourceTypes.Count)"
Write-Success "Successful: $successCount"
if ($failureCount -gt 0) {
    Write-Error-Custom "Failed: $failureCount"
}

Write-Info "`nDetailed Results:"
foreach ($result in $results) {
    if ($result.Status -eq "SUCCESS") {
        Write-Success "✓ $($result.DatasourceType) - Path: $($result.Path) - Assets: $($result.AssetCount)"
    } else {
        Write-Error-Custom "✗ $($result.DatasourceType) - Path: $($result.Path) - Error: $($result.Error)"
    }
}

if ($failureCount -gt 0) {
    Write-Info "`nTroubleshooting tips:"
    Write-Host "  1. Verify CPD_URL is correct and accessible"
    Write-Host "  2. Check authentication credentials (APIKEY or USERNAME/PASSWORD)"
    Write-Host "  3. Verify AUTH_URI is correct for your deployment"
    Write-Host "  4. Ensure DATASOURCE_TYPES match registered connectors"
    Write-Host "  5. Verify CONNECTION_PROPERTIES are correct for each datasource type"
    Write-Host "  6. Check that the connectors are deployed and running"
    exit 1
}

Write-Success "`n✓ All connector tests passed!"
exit 0

# Made with Bob
