# IBM Cloud Code Engine Deployment Script (PowerShell)
# Deploys a connector application to IBM Cloud Code Engine
# with configuration file upload using ConfigMaps via REST API

param(
    [Parameter(Mandatory=$false)]
    [string[]]$Files,
    
    [switch]$Help
)

# ============================================
# Configuration & Global Variables
# ============================================

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$PropertiesFile = Join-Path $ScriptDir "deploy-codeengine.properties"

# Global variables populated from properties file
$script:APIKEY = ""
$script:REGION = ""
$script:CODE_ENGINE_PROJECT = ""
$script:RESOURCE_GROUP_ID = ""
$script:APP_NAME = ""
$script:CONFIG_FILES = @()
$script:MIN_SCALE = ""
$script:MAX_SCALE = ""

# Runtime variables
$script:BEARER_TOKEN = ""
$script:PROJECT_ID = ""
$script:CONFIGMAP_NAME = "connectors-config"
$script:CONFIGMAP_ETAG = ""
$script:IMAGE = "ghcr.io/marek-zuwala/connectors-forge:1.0.4"
$script:APP_EXISTS = $false
$script:CONFIGMAP_EXISTS = $false
$script:API_VERSION = "2025-07-10"
$script:IAM_TOKEN_URL = "https://iam.cloud.ibm.com/identity/token"

# ============================================
# Helper Functions
# ============================================

function Write-Log {
    param([string]$Message)
    Write-Host $Message
}

function Write-LogError {
    param([string]$Message)
    Write-Host "ERROR: $Message" -ForegroundColor Red
}

function Write-LogStep {
    param([string]$Step, [string]$Message)
    Write-Host ""
    Write-Host "[$Step] $Message"
}

# Extract a JSON field value from a JSON object
function Get-JsonField {
    param(
        [string]$Json,
        [string]$Field
    )
    
    try {
        $obj = $Json | ConvertFrom-Json
        $value = $obj.$Field
        if ($value) {
            return $value
        }
        return $null
    }
    catch {
        # Fallback to regex if JSON parsing fails
        if ($Json -match "`"$Field`":\s*`"([^`"]*)`"") {
            return $matches[1]
        }
        return $null
    }
}

# Make HTTP request with error handling
function Invoke-HttpRequest {
    param(
        [string]$Method,
        [string]$Uri,
        [hashtable]$Headers = @{},
        [string]$Body = $null,
        [string]$ContentType = "application/json"
    )
    
    try {
        $params = @{
            Method = $Method
            Uri = $Uri
            Headers = $Headers
            ContentType = $ContentType
            UseBasicParsing = $true
        }
        
        if ($Body) {
            $params.Body = $Body
        }
        
        # Disable SSL validation for self-signed certificates (PowerShell 5.1 compatible)
        if ($PSVersionTable.PSVersion.Major -le 5) {
            # For PowerShell 5.1 and earlier
            # Only add the type if it doesn't already exist
            if (-not ([System.Management.Automation.PSTypeName]'TrustAllCertsPolicy').Type) {
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
            }
            [System.Net.ServicePointManager]::CertificatePolicy = New-Object TrustAllCertsPolicy
            [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12
        }
        else {
            # For PowerShell 6+ (Core)
            $params.SkipCertificateCheck = $true
        }
        
        $response = Invoke-WebRequest @params
        
        return @{
            StatusCode = $response.StatusCode
            Content = $response.Content
        }
    }
    catch {
        $statusCode = 0
        $content = ""
        
        if ($_.Exception.Response) {
            $statusCode = [int]$_.Exception.Response.StatusCode
            try {
                $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
                $content = $reader.ReadToEnd()
                $reader.Close()
            }
            catch {
                $content = $_.Exception.Message
            }
        }
        else {
            $content = $_.Exception.Message
        }
        
        return @{
            StatusCode = $statusCode
            Content = $content
        }
    }
}

# ============================================
# Usage & Help
# ============================================

function Show-Usage {
    $scriptName = Split-Path -Leaf $MyInvocation.ScriptName
    Write-Host @"
Usage: .\$scriptName -Files FILE1[,FILE2,...] [OPTIONS]

Deploy a connector application to IBM Cloud Code Engine using REST API.
Reads configuration from deploy-codeengine.properties file.

REQUIRED ARGUMENTS:
    -Files FILE1[,FILE2,...]    Comma-separated list of connector JSON files (full paths)

OPTIONS:
    -Help                       Show this help message

REQUIRED PROPERTIES (in deploy-codeengine.properties):
    APIKEY                  IBM Cloud API key
    REGION                  Code Engine region (e.g., us-south, eu-de, eu-gb)
    CODE_ENGINE_PROJECT     Code Engine project name (created if doesn't exist)

OPTIONAL PROPERTIES:
    RESOURCE_GROUP_ID       IBM Cloud resource group ID for project creation
    APP_NAME                Application name (default: cf-flight-connector)
    MIN_SCALE               Minimum instances (default: 0)
    MAX_SCALE               Maximum instances (default: 2)

Prerequisites:
  - IBM Cloud API key with Code Engine permissions
  - Properties file must exist: $PropertiesFile

ConfigMap Merge Behavior:
  - When ConfigMap exists: Files are MERGED (not replaced)
    * Matching filenames: Content is updated
    * New filenames: Files are added
    * Existing files not specified: Remain unchanged
  - Files are never removed automatically (manage via Code Engine UI)
  - Application restarts automatically after ConfigMap updates

Examples:
  # Deploy single connector file (relative path)
  .\$scriptName -Files .\my-connector.json

  # Deploy single connector file (absolute path)
  .\$scriptName -Files C:\path\to\servicenow-api.json

  # Deploy multiple connector files
  .\$scriptName -Files .\connectors\api1.json,.\connectors\api2.json,.\connectors\api3.json

  # Deploy from SDK resources directory
  .\$scriptName -Files ..\sdk-gen\subprojects\connectors_forge_rest\src\main\resources\servicenow-api.json

  # Update existing file (merges with ConfigMap)
  .\$scriptName -Files .\connectors\updated-api.json

  # Add new file to existing ConfigMap
  .\$scriptName -Files .\connectors\new-connector.json

  # Show help
  .\$scriptName -Help

"@
    exit 0
}

# ============================================
# Parse Command Line Arguments
# ============================================

if ($Help) {
    Show-Usage
}

if (-not $Files -or $Files.Count -eq 0) {
    Write-LogError "Missing required argument: -Files"
    Write-Host ""
    Show-Usage
}

$script:CONFIG_FILES = $Files

# ============================================
# Properties File Functions
# ============================================

function Get-Property {
    param([string]$Key)
    
    $content = Get-Content $PropertiesFile
    foreach ($line in $content) {
        $line = $line.Trim()
        # Skip comments and empty lines
        if ($line -match '^#' -or $line -eq '') {
            continue
        }
        # Parse key=value
        if ($line -match "^$Key\s*=\s*(.*)$") {
            return $matches[1].Trim()
        }
    }
    return ""
}

function Load-Properties {
    Write-LogStep "1/8" "Loading configuration from properties file..."
    
    if (-not (Test-Path $PropertiesFile)) {
        Write-LogError "Properties file not found: $PropertiesFile"
        Write-Host ""
        Write-Host "Please create $PropertiesFile from the template:"
        Write-Host "  Copy-Item ${PropertiesFile}.template $PropertiesFile"
        Write-Host ""
        Write-Host "Then edit the file and fill in your configuration values."
        exit 1
    }
    
    Write-Log "Found properties file: $PropertiesFile"
    
    # Read required properties
    $script:APIKEY = Get-Property "APIKEY"
    $script:REGION = Get-Property "REGION"
    
    # Read optional properties
    $script:RESOURCE_GROUP_ID = Get-Property "RESOURCE_GROUP_ID"
    
    # Read optional properties with defaults
    $script:CODE_ENGINE_PROJECT = Get-Property "CODE_ENGINE_PROJECT"
    if ([string]::IsNullOrWhiteSpace($CODE_ENGINE_PROJECT)) {
        $script:CODE_ENGINE_PROJECT = "ce-connectors-forge"
    }
    
    $script:APP_NAME = Get-Property "APP_NAME"
    if ([string]::IsNullOrWhiteSpace($APP_NAME)) {
        $script:APP_NAME = "ce-connectors-forge"
    }
    
    $script:MIN_SCALE = Get-Property "MIN_SCALE"
    if ([string]::IsNullOrWhiteSpace($MIN_SCALE)) {
        $script:MIN_SCALE = "0"
    }
    
    $script:MAX_SCALE = Get-Property "MAX_SCALE"
    if ([string]::IsNullOrWhiteSpace($MAX_SCALE)) {
        $script:MAX_SCALE = "2"
    }
    
    Write-Log "Configuration loaded successfully"
}

# ============================================
# Validation Functions
# ============================================

function Test-Properties {
    Write-LogStep "2/8" "Validating configuration..."
    
    $validationFailed = $false
    
    # Check required properties
    if ([string]::IsNullOrWhiteSpace($APIKEY)) {
        Write-LogError "Missing required property: APIKEY"
        $validationFailed = $true
    }
    
    if ([string]::IsNullOrWhiteSpace($REGION)) {
        Write-LogError "Missing required property: REGION"
        $validationFailed = $true
    }
    
    # Validate that all specified files exist
    $fileCount = 0
    foreach ($file in $CONFIG_FILES) {
        if (-not (Test-Path $file)) {
            Write-LogError "File not found: $file"
            $validationFailed = $true
        }
        else {
            $fileCount++
        }
    }
    
    if ($validationFailed) {
        Write-Host ""
        Write-LogError "Validation failed. Please update $PropertiesFile with required values."
        exit 1
    }
    
    Write-Log "All validations passed"
    Write-Log "Region: $REGION"
    Write-Log "Project: $CODE_ENGINE_PROJECT"
    Write-Log "Config files: $fileCount file(s) specified"
}

# ============================================
# Authentication Functions
# ============================================

function Get-IamToken {
    Write-LogStep "3/8" "Obtaining IAM bearer token..."
    
    $body = "grant_type=urn:ibm:params:oauth:grant-type:apikey&apikey=$APIKEY"
    
    $response = Invoke-HttpRequest `
        -Method "POST" `
        -Uri $IAM_TOKEN_URL `
        -ContentType "application/x-www-form-urlencoded" `
        -Body $body
    
    if ($response.StatusCode -ne 200) {
        Write-LogError "Failed to obtain bearer token (HTTP $($response.StatusCode))"
        Write-LogError "Please verify your API key is correct in $PropertiesFile"
        exit 1
    }
    
    $script:BEARER_TOKEN = Get-JsonField -Json $response.Content -Field "access_token"
    
    if ([string]::IsNullOrWhiteSpace($BEARER_TOKEN) -or $BEARER_TOKEN -eq "null") {
        Write-LogError "Failed to extract access token from response"
        exit 1
    }
    
    Write-Log "Bearer token obtained successfully"
}

# ============================================
# Project Functions
# ============================================

function New-CodeEngineProject {
    Write-Log "Project '$CODE_ENGINE_PROJECT' not found - creating it..."
    
    $apiUrl = "https://api.$REGION.codeengine.cloud.ibm.com/v2/projects?version=$API_VERSION"
    
    # Build payload with optional resource_group_id
    $payload = @{
        name = $CODE_ENGINE_PROJECT
    }
    
    if (-not [string]::IsNullOrWhiteSpace($RESOURCE_GROUP_ID)) {
        $payload.resource_group_id = $RESOURCE_GROUP_ID
    }
    
    $payloadJson = $payload | ConvertTo-Json -Compress
    
    $headers = @{
        Authorization = "Bearer $BEARER_TOKEN"
    }
    
    $response = Invoke-HttpRequest `
        -Method "POST" `
        -Uri $apiUrl `
        -Headers $headers `
        -Body $payloadJson
    
    if ($response.StatusCode -notin @(200, 201, 202)) {
        Write-LogError "Failed to create project (HTTP $($response.StatusCode))"
        Write-LogError "Response: $($response.Content)"
        exit 1
    }
    
    # Extract project ID from response
    $script:PROJECT_ID = Get-JsonField -Json $response.Content -Field "id"
    
    if ([string]::IsNullOrWhiteSpace($PROJECT_ID)) {
        Write-LogError "Failed to extract project ID from create response"
        exit 1
    }
    
    Write-Log "Project created successfully with ID: $PROJECT_ID"
    
    # Wait for project to be ready
    $timeout = 120
    Write-Log "Waiting for project to be ready (timeout: ${timeout}s)..."
    $elapsed = 0
    
    while ($elapsed -lt $timeout) {
        $statusUrl = "https://api.$REGION.codeengine.cloud.ibm.com/v2/projects/${PROJECT_ID}?version=$API_VERSION"
        
        $statusResponse = Invoke-HttpRequest `
            -Method "GET" `
            -Uri $statusUrl `
            -Headers $headers
        
        if ($statusResponse.StatusCode -eq 200) {
            $status = Get-JsonField -Json $statusResponse.Content -Field "status"
            if ($status -eq "active") {
                Write-Log "Project is ready"
                return
            }
        }
        
        Start-Sleep -Seconds 5
        $elapsed += 5
    }
    
    Write-LogError "Project did not become ready within $timeout seconds"
    exit 1
}

function Get-ProjectId {
    Write-LogStep "4/8" "Looking up Code Engine project ID..."
    
    $apiUrl = "https://api.$REGION.codeengine.cloud.ibm.com/v2/projects?version=$API_VERSION"
    
    $headers = @{
        Authorization = "Bearer $BEARER_TOKEN"
    }
    
    $response = Invoke-HttpRequest `
        -Method "GET" `
        -Uri $apiUrl `
        -Headers $headers
    
    if ($response.StatusCode -ne 200) {
        Write-LogError "Failed to list projects (HTTP $($response.StatusCode))"
        Write-LogError "Response: $($response.Content)"
        exit 1
    }
    
    # Extract project ID by matching project name
    try {
        $projects = ($response.Content | ConvertFrom-Json).projects
        foreach ($project in $projects) {
            if ($project.name -eq $CODE_ENGINE_PROJECT) {
                $script:PROJECT_ID = $project.id
                break
            }
        }
    }
    catch {
        # Fallback parsing if JSON structure is different
        if ($response.Content -match "`"name`":\s*`"$CODE_ENGINE_PROJECT`"") {
            # Try to find the id field near the matched name
            $startPos = $response.Content.IndexOf($matches[0])
            $searchArea = $response.Content.Substring([Math]::Max(0, $startPos - 200), [Math]::Min(400, $response.Content.Length - [Math]::Max(0, $startPos - 200)))
            if ($searchArea -match "`"id`":\s*`"([^`"]+)`"") {
                $script:PROJECT_ID = $matches[1]
            }
        }
    }
    
    if ([string]::IsNullOrWhiteSpace($PROJECT_ID)) {
        # Project not found - create it
        New-CodeEngineProject
    }
    else {
        Write-Log "Project ID: $PROJECT_ID"
    }
}

# ============================================
# JSON Helper Functions
# ============================================

function Get-ExistingConfigMapData {
    $apiUrl = "https://api.$REGION.codeengine.cloud.ibm.com/v2/projects/$PROJECT_ID/config_maps/${CONFIGMAP_NAME}?version=$API_VERSION"
    
    $headers = @{
        Authorization = "Bearer $BEARER_TOKEN"
    }
    
    $response = Invoke-HttpRequest `
        -Method "GET" `
        -Uri $apiUrl `
        -Headers $headers
    
    if ($response.StatusCode -eq 200) {
        try {
            $configMap = $response.Content | ConvertFrom-Json
            if ($configMap.data) {
                return $configMap.data
            }
        }
        catch {
            # Fallback: extract data section manually
            if ($response.Content -match '"data":\s*\{([^}]+)\}') {
                return $matches[1]
            }
        }
    }
    
    return $null
}

function Build-ConfigMapData {
    param($ExistingData)
    
    # Start with existing data as hashtable
    $dataHash = @{}
    
    # Parse existing data if it exists
    if ($ExistingData) {
        if ($ExistingData -is [PSCustomObject]) {
            # Convert PSCustomObject to hashtable
            $ExistingData.PSObject.Properties | ForEach-Object {
                $dataHash[$_.Name] = $_.Value
            }
        }
        elseif ($ExistingData -is [string]) {
            # Parse string format
            $pairs = $ExistingData -split ','
            foreach ($pair in $pairs) {
                if ($pair -match '"([^"]+)":"([^"]*(?:\\.[^"]*)*)"') {
                    $dataHash[$matches[1]] = $matches[2]
                }
            }
        }
    }
    
    # Process each file in CONFIG_FILES
    foreach ($file in $CONFIG_FILES) {
        if (-not (Test-Path $file)) {
            Write-LogError "File not found: $file"
            exit 1
        }
        
        $filename = Split-Path -Leaf $file
        
        # Validate filename contains only safe characters
        if ($filename -notmatch '^[a-zA-Z0-9._-]+$') {
            Write-LogError "Invalid filename: $filename. Filenames must contain only alphanumeric characters, dots, dashes, and underscores."
            exit 1
        }
        
        # Read file content with proper encoding
        $content = Get-Content $file -Raw -Encoding UTF8
        
        # Handle null or empty content
        if ($null -eq $content) {
            $content = ""
        }
        
        # Add content directly - ConvertTo-Json will handle all escaping
        $dataHash[$filename] = $content
    }
    
    # Convert hashtable to JSON object
    return $dataHash
}

# ============================================
# ConfigMap Management
# ============================================

function Test-ConfigMapExists {
    $apiUrl = "https://api.$REGION.codeengine.cloud.ibm.com/v2/projects/$PROJECT_ID/config_maps/${CONFIGMAP_NAME}?version=$API_VERSION"
    
    $headers = @{
        Authorization = "Bearer $BEARER_TOKEN"
    }
    
    $response = Invoke-HttpRequest `
        -Method "GET" `
        -Uri $apiUrl `
        -Headers $headers
    
    if ($response.StatusCode -eq 200) {
        $script:CONFIGMAP_EXISTS = $true
        # Extract ETag from response for If-Match header
        $script:CONFIGMAP_ETAG = Get-JsonField -Json $response.Content -Field "entity_tag"
    }
    else {
        $script:CONFIGMAP_EXISTS = $false
        $script:CONFIGMAP_ETAG = ""
    }
}

function New-OrUpdateConfigMap {
    Write-LogStep "5/8" "Managing ConfigMap..."
    
    Test-ConfigMapExists
    
    $existingData = $null
    $dataHash = $null
    
    if ($CONFIGMAP_EXISTS) {
        Write-Log "ConfigMap '$CONFIGMAP_NAME' already exists - merging files..."
        
        # Get existing ConfigMap data
        $existingData = Get-ExistingConfigMapData
        # Build merged data
        $dataHash = Build-ConfigMapData -ExistingData $existingData
        Write-Log "Updating ConfigMap '$CONFIGMAP_NAME'..."
    }
    else {
        Write-Log "Creating ConfigMap '$CONFIGMAP_NAME'..."
        
        # Build data for new ConfigMap
        $dataHash = Build-ConfigMapData -ExistingData $null
    }
    
    # Build payload based on operation
    $headers = @{
        Authorization = "Bearer $BEARER_TOKEN"
    }
    if ($CONFIGMAP_EXISTS) {
        # For updates, only send the data object
        $payload = @{
            data = $dataHash
        } | ConvertTo-Json -Compress -Depth 10
        
        
        # Update existing ConfigMap with If-Match header
        Write-Log "Updating ConfigMap with ETag: $CONFIGMAP_ETAG"
        $apiUrl = "https://api.$REGION.codeengine.cloud.ibm.com/v2/projects/$PROJECT_ID/config_maps/${CONFIGMAP_NAME}?version=$API_VERSION"
        $headers["If-Match"] = $CONFIGMAP_ETAG
        $headers["Content-Type"] = "application/merge-patch+json"
        
        $response = Invoke-HttpRequest `
            -Method "PUT" `
            -Uri $apiUrl `
            -Headers $headers `
            -Body $payload `
            -ContentType "application/merge-patch+json"
        
        if ($response.StatusCode -ne 200) {
            Write-LogError "Failed to update ConfigMap (HTTP $($response.StatusCode))"
            Write-LogError "Response: $($response.Content)"
            exit 1
        }
        
        Write-Log "ConfigMap '$CONFIGMAP_NAME' updated successfully"
    }
    else {
        # For creation, include name and data
        $payload = @{
            name = $CONFIGMAP_NAME
            data = $dataHash
        } | ConvertTo-Json -Compress -Depth 10
        
        # Create new ConfigMap
        $apiUrl = "https://api.$REGION.codeengine.cloud.ibm.com/v2/projects/$PROJECT_ID/config_maps?version=$API_VERSION"
        
        $response = Invoke-HttpRequest `
            -Method "POST" `
            -Uri $apiUrl `
            -Headers $headers `
            -Body $payload
        
        if ($response.StatusCode -notin @(200, 201)) {
            Write-LogError "Failed to create ConfigMap (HTTP $($response.StatusCode))"
            Write-LogError "Response: $($response.Content)"
            exit 1
        }
        
        Write-Log "ConfigMap '$CONFIGMAP_NAME' created successfully"
    }
}

# ============================================
# Application Management
# ============================================

function Test-AppExists {
    Write-LogStep "6/8" "Checking for existing application..."
    
    $apiUrl = "https://api.$REGION.codeengine.cloud.ibm.com/v2/projects/$PROJECT_ID/apps/${APP_NAME}?version=$API_VERSION"
    
    $headers = @{
        Authorization = "Bearer $BEARER_TOKEN"
    }
    
    $response = Invoke-HttpRequest `
        -Method "GET" `
        -Uri $apiUrl `
        -Headers $headers
    
    if ($response.StatusCode -eq 200) {
        $script:APP_EXISTS = $true
        Write-Log "Application '$APP_NAME' already exists"
    }
    else {
        $script:APP_EXISTS = $false
        Write-Log "No existing application found"
    }
}

function Get-EnvVariables {
    $timestamp = [int][double]::Parse((Get-Date -UFormat %s))
    
    return @(
        @{
            key = "ENABLE_SSL"
            name = "ENABLE_SSL"
            type = "literal"
            value = "false"
        },
        @{
            key = "LAST_UPDATED"
            name = "LAST_UPDATED"
            type = "literal"
            value = $timestamp.ToString()
        }
    )
}

function Update-AppWithTimestamp {
    Write-Log "Updating application with timestamp to force new revision..."
    
    $apiUrl = "https://api.$REGION.codeengine.cloud.ibm.com/v2/projects/$PROJECT_ID/apps/${APP_NAME}?version=$API_VERSION"
    $envVariables = Get-EnvVariables
    
    # Build payload with environment variables
    $payload = @{
        run_env_variables = $envVariables
    } | ConvertTo-Json -Compress -Depth 10
    
    $headers = @{
        Authorization = "Bearer $BEARER_TOKEN"
        "If-Match" = "*"
    }
    
    $response = Invoke-HttpRequest `
        -Method "PATCH" `
        -Uri $apiUrl `
        -Headers $headers `
        -Body $payload
    
    if ($response.StatusCode -ne 200) {
        Write-LogError "Failed to update application with timestamp (HTTP $($response.StatusCode))"
        Write-LogError "Response: $($response.Content)"
        exit 1
    }
    
    Write-Log "Application updated with new timestamp"
    Write-Log "New revision will be created automatically"
}

function Deploy-Application {
    Write-LogStep "7/8" "Deploying application..."
    
    if ($APP_EXISTS) {
        Write-Log "Application exists and ConfigMap was merged"
        Update-AppWithTimestamp
        return
    }
    
    Write-Log "Creating application '$APP_NAME'..."
    
    $envVariables = Get-EnvVariables
    
    # Build application payload
    $payload = @{
        name = $APP_NAME
        image_reference = $IMAGE
        image_port = 9443
        image_protocol = "h2c"
        scale_min_instances = [int]$MIN_SCALE
        scale_max_instances = [int]$MAX_SCALE
        run_env_variables = $envVariables
        run_volume_mounts = @(
            @{
                type = "config_map"
                mount_path = "/config/mappings"
                reference = $CONFIGMAP_NAME
                read_only = $true
            }
        )
    } | ConvertTo-Json -Compress -Depth 10
    
    $apiUrl = "https://api.$REGION.codeengine.cloud.ibm.com/v2/projects/$PROJECT_ID/apps?version=$API_VERSION"
    
    $headers = @{
        Authorization = "Bearer $BEARER_TOKEN"
    }
    
    $response = Invoke-HttpRequest `
        -Method "POST" `
        -Uri $apiUrl `
        -Headers $headers `
        -Body $payload
    
    if ($response.StatusCode -notin @(200, 201)) {
        Write-LogError "Failed to create application (HTTP $($response.StatusCode))"
        Write-LogError "Response: $($response.Content)"
        exit 1
    }
    
    Write-Log "Application '$APP_NAME' created successfully"
}

# ============================================
# Application Verification
# ============================================

function Test-ApplicationReady {
    Write-LogStep "8/8" "Verifying application status..."
    
    $timeout = 120
    Write-Log "Waiting for application to be ready (timeout: ${timeout}s)..."
    $elapsed = 0
    $status = ""
    
    $apiUrl = "https://api.$REGION.codeengine.cloud.ibm.com/v2/projects/$PROJECT_ID/apps/${APP_NAME}?version=$API_VERSION"
    
    $headers = @{
        Authorization = "Bearer $BEARER_TOKEN"
    }
    
    while ($elapsed -lt $timeout) {
        $response = Invoke-HttpRequest `
            -Method "GET" `
            -Uri $apiUrl `
            -Headers $headers
        
        if ($response.StatusCode -eq 200) {
            # Extract status from response
            $status = Get-JsonField -Json $response.Content -Field "status"
            
            if ($status -eq "ready") {
                Write-Log "Application is ready"
                return
            }
            
            Write-Log "Current status: $status (${elapsed}s elapsed, waiting...)"
        }
        
        Start-Sleep -Seconds 5
        $elapsed += 5
    }
    
    Write-LogError "Application did not become ready within $timeout seconds (Status: $status)"
    exit 1
}

# ============================================
# Display Information
# ============================================

function Show-DeploymentInfo {
    Write-Host ""
    Write-Host "=========================================="
    Write-Host "Deployment Successful!"
    Write-Host "=========================================="
    
    # Get application details
    $apiUrl = "https://api.$REGION.codeengine.cloud.ibm.com/v2/projects/$PROJECT_ID/apps/${APP_NAME}?version=$API_VERSION"
    
    $headers = @{
        Authorization = "Bearer $BEARER_TOKEN"
    }
    
    $response = Invoke-HttpRequest `
        -Method "GET" `
        -Uri $apiUrl `
        -Headers $headers
    
    # Extract URL from response
    $appUrl = Get-JsonField -Json $response.Content -Field "endpoint"
    
    if (-not [string]::IsNullOrWhiteSpace($appUrl)) {
        # Convert https:// to grpc+tls:// for gRPC endpoint
        $grpcUrl = $appUrl -replace '^https://', 'grpc+tls://'
        
        Write-Host ""
        Write-Host "Public Application URL:"
        Write-Host "  $appUrl"
        Write-Host ""
        Write-Host "gRPC Endpoint:"
        Write-Host "  ${grpcUrl}:443"
        Write-Host ""
    }
    
    Write-Host "Application Name:  $APP_NAME"
    Write-Host "Project:           $CODE_ENGINE_PROJECT"
    Write-Host "Region:            $REGION"
    Write-Host "ConfigMap:         $CONFIGMAP_NAME"
    Write-Host ""
    Write-Host "Resources:"
    Write-Host "  Min Scale:       $MIN_SCALE"
    Write-Host "  Max Scale:       $MAX_SCALE"
    Write-Host ""
    Write-Host "To update connector files:"
    Write-Host "  1. Update or add JSON files"
    Write-Host "  2. Run this script again with -Files <file-path(s)>"
    Write-Host "  3. Files will be merged into ConfigMap (existing files updated, new files added)"
    Write-Host "  4. Application will restart automatically with new configuration"
    Write-Host ""
    Write-Host "Note: Files are never removed automatically. To remove files, use the Code Engine UI."
    Write-Host "=========================================="
}

# ============================================
# Main Execution
# ============================================

function Main {
    Write-Host ""
    Write-Host "=========================================="
    Write-Host "IBM Cloud Code Engine Deployment"
    Write-Host "=========================================="
    Write-Host ""
    
    Load-Properties
    Test-Properties
    Get-IamToken
    Get-ProjectId
    New-OrUpdateConfigMap
    Test-AppExists
    Deploy-Application
    Test-ApplicationReady
    Show-DeploymentInfo
}

# ============================================
# Script Entry Point
# ============================================

Main

# Made with Bob