param(
    [string]$PropertiesFile = "register-envs.properties",
    [switch]$Help
)

# ============================================
# Help
# ============================================

if ($Help) {
    Write-Host @"
Usage: register-connector.ps1

Registers one or more connectors with IBM Cloud Data Platform or on-premises CP4D.

Required properties (in properties file):
  Authentication (choose one):
    - apikey (for IBM Cloud)
    OR
    - username AND password (for on-premises CP4D)
  
  auth_uri
  datasource_types_uri
  flight_hostname
  flight_port

Optional:
  origin_country (default: us)
  ssl_certificate_path
  ssl_certificate_validation
"@
    exit 0
}

# ============================================
# Main
# ============================================

Write-Host ""
Write-Host "=========================================="
Write-Host "Connector Registration Script"
Write-Host "=========================================="
Write-Host ""

if (-not (Test-Path $PropertiesFile)) {
    throw "Properties file not found: $PropertiesFile"
}

Write-Host "Found properties file: $PropertiesFile"

# ============================================
# Read properties
# ============================================

Write-Host "Reading configuration..."

$props = @{}

Get-Content $PropertiesFile | ForEach-Object {
    if ($_ -match "^\s*([^#][^=]*)=(.*)$") {
        $key = $matches[1].Trim()
        $value = $matches[2].Trim()
        $props[$key] = $value
    }
}

$APIKEY = $props["apikey"]
$USERNAME = $props["username"]
$PASSWORD = $props["password"]
$AUTH_URI = $props["auth_uri"]
$DATASOURCE_TYPES_URI = $props["datasource_types_uri"]
$FLIGHT_HOSTNAME = $props["flight_hostname"]
$FLIGHT_PORT = $props["flight_port"]
$SSL_CERT_PATH = $props["ssl_certificate_path"]
$SSL_CERT_VALIDATION = $props["ssl_certificate_validation"]
$ORIGIN_COUNTRY = $props["origin_country"]

if (-not $SSL_CERT_VALIDATION) {
    $SSL_CERT_VALIDATION = "false"
}

if (-not $ORIGIN_COUNTRY) {
    $ORIGIN_COUNTRY = "us"
}

# ============================================
# Configure SSL Certificate Validation
# ============================================

if ($SSL_CERT_VALIDATION -eq "false") {
    Write-Host "Disabling SSL certificate validation..."
    
    # For PowerShell 5.1 and earlier - use .NET ServicePointManager
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

# ============================================
# Validate
# ============================================

Write-Host ""
Write-Host "[1/3] Validating required properties..."

$missing = @()

# Check authentication credentials
if (-not $APIKEY -and (-not $USERNAME -or -not $PASSWORD)) {
    Write-Host "ERROR: Missing authentication credentials" -ForegroundColor Red
    Write-Host "Provide either 'apikey' OR both 'username' and 'password'" -ForegroundColor Red
    throw "Authentication validation failed"
}

if ($APIKEY -and ($USERNAME -or $PASSWORD)) {
    Write-Host "ERROR: Conflicting authentication methods" -ForegroundColor Red
    Write-Host "Provide either 'apikey' OR 'username'+'password', not both" -ForegroundColor Red
    throw "Authentication validation failed"
}

# Check other required properties
foreach ($key in @("auth_uri","datasource_types_uri","flight_hostname","flight_port")) {
    if (-not $props[$key]) {
        $missing += $key
    }
}

if ($missing.Count -gt 0) {
    throw "Missing required properties: $($missing -join ', ')"
}

Write-Host "All required properties are present"

# ============================================
# Display config
# ============================================

Write-Host ""
Write-Host "Configuration:"
$authMethod = if ($APIKEY) { "API Key" } else { "Username/Password" }
Write-Host "  Auth Method:         $authMethod"
Write-Host "  Auth URI:            $AUTH_URI"
Write-Host "  Datasource API:      $DATASOURCE_TYPES_URI"
Write-Host "  Flight Hostname:     $FLIGHT_HOSTNAME"
Write-Host "  Flight Port:         $FLIGHT_PORT"
Write-Host "  Origin Country:      $ORIGIN_COUNTRY"
Write-Host "  SSL Validation:      $SSL_CERT_VALIDATION"

if ($SSL_CERT_PATH) {
    Write-Host "  SSL Certificate:     $SSL_CERT_PATH"
}

# ============================================
# Get Bearer Token
# ============================================

Write-Host ""
Write-Host "[2/3] Obtaining Bearer Token..."

# Build authentication request based on method
if ($APIKEY) {
    Write-Host "Using API Key authentication"
    $tokenBody = @{
        grant_type = "urn:ibm:params:oauth:grant-type:apikey"
        apikey     = $APIKEY
    }
    $contentType = "application/x-www-form-urlencoded"
} else {
    Write-Host "Using Username/Password authentication"
    $tokenBody = @{
        username = $USERNAME
        password = $PASSWORD
    } | ConvertTo-Json
    $contentType = "application/json"
}

try {
    $tokenResponse = Invoke-RestMethod `
        -Method Post `
        -Uri $AUTH_URI `
        -ContentType $contentType `
        -Body $tokenBody
} catch {
    Write-Host "ERROR: Failed to obtain bearer token" -ForegroundColor Red
    if ($APIKEY) {
        Write-Host "Please verify your API key is correct" -ForegroundColor Red
    } else {
        Write-Host "Please verify your username and password are correct" -ForegroundColor Red
    }
    throw $_
}

# Try to extract token (different field names for Cloud vs on-premises)
$BEARER_TOKEN = $tokenResponse.access_token
if (-not $BEARER_TOKEN) {
    $BEARER_TOKEN = $tokenResponse.token
}

if (-not $BEARER_TOKEN) {
    throw "Failed to extract access token from response"
}

Write-Host "Bearer token obtained"

# ============================================
# Read SSL certificate
# ============================================

$SSL_CERTIFICATE = $null

if ($SSL_CERT_PATH) {
    Write-Host "Reading SSL certificate..."

    if (-not (Test-Path $SSL_CERT_PATH)) {
        throw "SSL certificate not found: $SSL_CERT_PATH"
    }

    $SSL_CERTIFICATE = Get-Content $SSL_CERT_PATH -Raw
    Write-Host "SSL certificate loaded"
}

# ============================================
# Register connector
# ============================================

Write-Host ""
Write-Host "[3/3] Registering Connector..."

$FLIGHT_URI = "grpc+tls://$FLIGHT_HOSTNAME`:$FLIGHT_PORT"
Write-Host "Flight URI: $FLIGHT_URI"

$sslValidationBool = $SSL_CERT_VALIDATION -eq "true"

$payload = @{
    flight_info = @{
        flight_uri = $FLIGHT_URI
        ssl_certificate = $SSL_CERTIFICATE
        ssl_certificate_validation = $sslValidationBool
    }
    origin_country = $ORIGIN_COUNTRY
} | ConvertTo-Json -Depth 5

Write-Host "Sending registration request..."
Write-Host "Endpoint: $DATASOURCE_TYPES_URI"

try {
    $response = Invoke-WebRequest `
        -Method Post `
        -Uri $DATASOURCE_TYPES_URI `
        -Headers @{
            Authorization = "Bearer $BEARER_TOKEN"
            Accept        = "application/json"
        } `
        -ContentType "application/json" `
        -Body $payload `
        -UseBasicParsing

    $status = $response.StatusCode
} catch {
    if ($_.Exception.Response) {
        $status = $_.Exception.Response.StatusCode.value__
    } else {
        throw $_
    }
}

if ($status -in 200,201) {
    Write-Host ""
    Write-Host "=========================================="
    Write-Host "Registration Successful!"
    Write-Host "=========================================="
    Write-Host "HTTP $status"
}
else {
    throw "Registration failed (HTTP $status)"
}