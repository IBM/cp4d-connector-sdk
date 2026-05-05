#!/bin/bash

# Register Connector Script
# Reads configuration from PROPERTIES_FILE and registers one or more connectors
# with IBM Cloud Data Platform via REST API

set -e  # Exit on error

# ============================================
# Configuration
# ============================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROPERTIES_FILE="${SCRIPT_DIR}/register-envs.properties"

# ============================================
# Helper Functions
# ============================================

log() {
    echo "$1"
}

log_error() {
    echo "ERROR: $1" >&2
}

log_step() {
    echo ""
    echo "[$1] $2"
}

show_help() {
    cat << EOF
Usage: $0 [OPTIONS]

Registers one or more connectors with IBM Cloud Data Platform.
Reads all configuration from register-envs.properties file.

OPTIONS:
    --help, -h          Show this help message

REQUIRED PROPERTIES (in register-envs.properties):
    apikey              IBM Cloud API key
    auth_uri            IBM Cloud IAM authentication endpoint
    datasource_types_uri    Datasource types API endpoint
    flight_hostname     Flight service hostname
    flight_port         Flight service port

OPTIONAL PROPERTIES:
    origin_country      Origin country/region (default: us)
    ssl_certificate_path    Path to SSL certificate file
    ssl_certificate_validation    Enable/disable SSL validation (true/false)

EXAMPLE:
    $0

EOF
}

# ============================================
# Parse Command Line Arguments
# ============================================

if [[ "$1" == "--help" ]] || [[ "$1" == "-h" ]]; then
    show_help
    exit 0
fi

# ============================================
# Check Properties File Exists
# ============================================

echo ""
echo "=========================================="
echo "Connector Registration Script"
echo "=========================================="
echo ""

if [ ! -f "$PROPERTIES_FILE" ]; then
    log_error "Properties file not found: $PROPERTIES_FILE"
    echo ""
    echo "Please create $PROPERTIES_FILE with required configuration."
    echo "Run '$0 --help' for more information."
    exit 1
fi

log "Found properties file: $PROPERTIES_FILE"

# ============================================
# Parse Properties File
# ============================================

log "Reading configuration from properties file..."

# Function to read property value
get_property() {
    local key=$1
    local value=$(grep -v '^[[:space:]]*#' "$PROPERTIES_FILE" | grep "^${key}=" | cut -d'=' -f2- | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
    echo "$value"
}

# Read all properties
APIKEY=$(get_property "apikey")
USERNAME=$(get_property "username")
PASSWORD=$(get_property "password")
AUTH_URI=$(get_property "auth_uri")
DATASOURCE_TYPES_URI=$(get_property "datasource_types_uri")
FLIGHT_HOSTNAME=$(get_property "flight_hostname")
FLIGHT_PORT=$(get_property "flight_port")
SSL_CERT_PATH=$(get_property "ssl_certificate_path")
SSL_CERT_VALIDATION=$(get_property "ssl_certificate_validation")
ORIGIN_COUNTRY=$(get_property "origin_country")

# Set defaults
if [ -z "$ORIGIN_COUNTRY" ]; then
    ORIGIN_COUNTRY="us"
fi

if [ -z "$SSL_CERT_VALIDATION" ]; then
    SSL_CERT_VALIDATION="false"
fi

# ============================================
# Validate Required Properties
# ============================================

log_step "1/3" "Validating required properties..."

VALIDATION_FAILED=false

# Check that either apikey OR username+password is provided
if [ -z "$APIKEY" ] && { [ -z "$USERNAME" ] || [ -z "$PASSWORD" ]; }; then
    log_error "Missing authentication credentials"
    log_error "Provide either 'apikey' OR both 'username' and 'password'"
    VALIDATION_FAILED=true
fi

if [ -n "$APIKEY" ] && { [ -n "$USERNAME" ] || [ -n "$PASSWORD" ]; }; then
    log_error "Conflicting authentication methods"
    log_error "Provide either 'apikey' OR 'username'+'password', not both"
    VALIDATION_FAILED=true
fi

if [ -z "$AUTH_URI" ]; then
    log_error "Missing required property: auth_uri"
    VALIDATION_FAILED=true
fi

if [ -z "$DATASOURCE_TYPES_URI" ]; then
    log_error "Missing required property: datasource_types_uri"
    VALIDATION_FAILED=true
fi

if [ -z "$FLIGHT_HOSTNAME" ]; then
    log_error "Missing required property: flight_hostname"
    VALIDATION_FAILED=true
fi

if [ -z "$FLIGHT_PORT" ]; then
    log_error "Missing required property: flight_port"
    VALIDATION_FAILED=true
fi

if [ "$VALIDATION_FAILED" = true ]; then
    echo ""
    log_error "Validation failed. Please update $PROPERTIES_FILE with required values."
    echo ""
    echo "Required properties:"
    echo "  Authentication (choose one):"
    echo "    - apikey=<your-ibm-cloud-api-key>  (for IBM Cloud)"
    echo "    OR"
    echo "    - username=<your-username>  (for on-premises CP4D)"
    echo "    - password=<your-password>  (for on-premises CP4D)"
    echo "  Flight service:"
    echo "    - flight_hostname=<hostname>"
    echo "    - flight_port=<port>"
    exit 1
fi

log "All required properties are present"

# ============================================
# Display Configuration
# ============================================

echo ""
log "Configuration:"
echo "  Auth Method:         $([ -n "$APIKEY" ] && echo "API Key" || echo "Username/Password")"
echo "  Auth URI:            $AUTH_URI"
echo "  Datasource API:      $DATASOURCE_TYPES_URI"
echo "  Flight Hostname:     $FLIGHT_HOSTNAME"
echo "  Flight Port:         $FLIGHT_PORT"
echo "  Origin Country:      $ORIGIN_COUNTRY"
echo "  SSL Validation:      $SSL_CERT_VALIDATION"
if [ -n "$SSL_CERT_PATH" ]; then
    echo "  SSL Certificate:     $SSL_CERT_PATH"
fi
echo ""

# ============================================
# Step 1: Get Bearer Token
# ============================================

log_step "2/3" "Obtaining Bearer Token..."

# Build authentication request based on method
if [ -n "$APIKEY" ]; then
    log "Using API Key authentication"
    TOKEN_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URI" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "grant_type=urn:ibm:params:oauth:grant-type:apikey" \
        -d "apikey=$APIKEY")
else
    log "Using Username/Password authentication"
    # Create JSON payload for username/password
    JSON_PAYLOAD=$(cat <<EOF
{"username":"$USERNAME","password":"$PASSWORD"}
EOF
)
    TOKEN_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$AUTH_URI" \
        -H "Content-Type: application/json" \
        -d "$JSON_PAYLOAD")
fi

HTTP_CODE=$(echo "$TOKEN_RESPONSE" | tail -n1)
TOKEN_BODY=$(echo "$TOKEN_RESPONSE" | sed '$d')

if [ "$HTTP_CODE" != "200" ]; then
    log_error "Failed to obtain bearer token (HTTP $HTTP_CODE)"
    echo ""
    echo "Response:"
    echo "$TOKEN_BODY"
    echo ""
    if [ -n "$APIKEY" ]; then
        log_error "Please verify your API key is correct in $PROPERTIES_FILE"
    else
        log_error "Please verify your username and password are correct in $PROPERTIES_FILE"
    fi
    exit 1
fi

BEARER_TOKEN=$(echo "$TOKEN_BODY" | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)
if [ -z "$BEARER_TOKEN" ] || [ "$BEARER_TOKEN" = "null" ]; then
    # Try alternative token field name (for on-premises CP4D)
    BEARER_TOKEN=$(echo "$TOKEN_BODY" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
fi

if [ -z "$BEARER_TOKEN" ] || [ "$BEARER_TOKEN" = "null" ]; then
    log_error "Failed to extract access token from response"
    echo ""
    echo "Response:"
    echo "$TOKEN_BODY"
    exit 1
fi

log "Bearer token obtained successfully"

# ============================================
# Step 2: Read SSL Certificate (if provided)
# ============================================

SSL_CERTIFICATE=""

if [ -n "$SSL_CERT_PATH" ]; then
    log "Reading SSL certificate from: $SSL_CERT_PATH"
    
    if [ ! -f "$SSL_CERT_PATH" ]; then
        log_error "SSL certificate file not found: $SSL_CERT_PATH"
        exit 1
    fi
    
    SSL_CERTIFICATE=$(cat "$SSL_CERT_PATH")
    log "SSL certificate loaded successfully"
fi

# ============================================
# Step 3: Build Registration Payload
# ============================================

log_step "3/3" "Registering Connector with IBM Cloud..."

# Construct flight URI
FLIGHT_URI="grpc+tls://${FLIGHT_HOSTNAME}:${FLIGHT_PORT}"

log "Flight URI: $FLIGHT_URI"

# Build JSON payload
# Escape special characters in certificate for JSON
if [ -n "$SSL_CERTIFICATE" ]; then
    # Escape newlines and quotes for JSON
    SSL_CERT_JSON=$(echo "$SSL_CERTIFICATE" | sed 's/\\/\\\\/g' | sed 's/"/\\"/g' | awk '{printf "%s\\n", $0}' | sed 's/\\n$//')
else
    SSL_CERT_JSON=""
fi

# Convert boolean string to JSON boolean
if [ "$SSL_CERT_VALIDATION" = "true" ]; then
    SSL_VALIDATION_JSON="true"
else
    SSL_VALIDATION_JSON="false"
fi

# Create JSON payload
PAYLOAD=$(cat <<EOF
{
  "flight_info": {
    "flight_uri": "$FLIGHT_URI",
    "ssl_certificate": "$SSL_CERT_JSON",
    "ssl_certificate_validation": $SSL_VALIDATION_JSON
  },
  "origin_country": "$ORIGIN_COUNTRY"
}
EOF
)

log "Sending registration request..."
log "Endpoint: $DATASOURCE_TYPES_URI"

REGISTER_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$DATASOURCE_TYPES_URI" \
    -H "Accept: application/json" \
    -H "Authorization: Bearer $BEARER_TOKEN" \
    -H "Content-Type: application/json" \
    -d "$PAYLOAD")

HTTP_CODE=$(echo "$REGISTER_RESPONSE" | tail -n1)
RESPONSE_BODY=$(echo "$REGISTER_RESPONSE" | sed '$d')

# ============================================
# Handle Response
# ============================================

if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "201" ]; then
    echo ""
    echo "=========================================="
    echo "Registration Successful!"
    echo "=========================================="
    log "Connector registered successfully (HTTP $HTTP_CODE)"
    echo ""
    echo "=========================================="
    exit 0
else
    echo ""
    echo "=========================================="
    echo "Registration Failed"
    echo "=========================================="
    log_error "Registration failed (HTTP $HTTP_CODE)"
    echo ""
    echo "Response:"
    echo "$RESPONSE_BODY"
    exit 1
fi

# Made with Bob