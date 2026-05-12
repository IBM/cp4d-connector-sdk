#!/bin/bash
#
# Test connector discovery using the discoverAssetsAnonymously endpoint
#
# This script tests one or more REST API connectors by calling the CPD Connection Service API
# to discover assets without creating a saved connection. Supports testing multiple connectors
# in a single run by reading comma-separated values from the properties file.
#
# Usage:
#   ./test-discovery.sh
#   ./test-discovery.sh -f my-config.properties
#

set -e


# Default values
PROPERTIES_FILE="test-discovery.properties"

# Parse command line arguments
while getopts "f:h" opt; do
    case $opt in
        f) PROPERTIES_FILE="$OPTARG" ;;
        h)
            echo "Usage: $0 [-f properties_file]"
            echo "  -f  Properties file path (default: test-discovery.properties)"
            echo "  -h  Show this help message"
            exit 0
            ;;
        \?)
            echo "Invalid option: -$OPTARG" >&2
            exit 1
            ;;
    esac
done

# Check if properties file exists
if [ ! -f "$PROPERTIES_FILE" ]; then
    echo "ERROR: Properties file not found: $PROPERTIES_FILE"
    echo "Please copy test-discovery.properties.template to $PROPERTIES_FILE and configure it."
    exit 1
fi

echo "Loading configuration from: $PROPERTIES_FILE"

# Function to read properties file
read_property() {
    local key=$1
    local value=$(grep -v '^[[:space:]]*#' "$PROPERTIES_FILE" | grep "^${key}=" | cut -d'=' -f2- | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
    echo "$value"
}

# Load configuration
CPD_URL=$(read_property "CPD_URL")
APIKEY=$(read_property "APIKEY")
USERNAME=$(read_property "USERNAME")
PASSWORD=$(read_property "PASSWORD")
AUTH_URI=$(read_property "AUTH_URI")
DATASOURCE_TYPES_STR=$(read_property "DATASOURCE_TYPES")
DISCOVERY_PATHS_STR=$(read_property "DISCOVERY_PATHS")
CONNECTION_PROPERTIES_STR=$(read_property "CONNECTION_PROPERTIES")

# Validate required parameters
if [ -z "$CPD_URL" ]; then
    echo "ERROR: CPD_URL is not set in properties file"
    exit 1
fi

# Check authentication credentials
if [ -z "$APIKEY" ] && { [ -z "$USERNAME" ] || [ -z "$PASSWORD" ]; }; then
    echo "ERROR: Missing authentication credentials"
    echo "Provide either APIKEY OR both USERNAME and PASSWORD"
    exit 1
fi

if [ -n "$APIKEY" ] && { [ -n "$USERNAME" ] || [ -n "$PASSWORD" ]; }; then
    echo "ERROR: Conflicting authentication methods"
    echo "Provide either APIKEY OR USERNAME+PASSWORD, not both"
    exit 1
fi

if [ -z "$AUTH_URI" ]; then
    echo "ERROR: AUTH_URI is not set in properties file"
    exit 1
fi

if [ -z "$DATASOURCE_TYPES_STR" ]; then
    echo "ERROR: DATASOURCE_TYPES is not set in properties file"
    exit 1
fi

if [ -z "$DISCOVERY_PATHS_STR" ]; then
    echo "ERROR: DISCOVERY_PATHS is not set in properties file"
    exit 1
fi

if [ -z "$CONNECTION_PROPERTIES_STR" ]; then
    echo "ERROR: CONNECTION_PROPERTIES is not set in properties file"
    exit 1
fi

# Parse arrays
IFS=',' read -ra DATASOURCE_TYPES <<< "$DATASOURCE_TYPES_STR"
IFS=',' read -ra DISCOVERY_PATHS <<< "$DISCOVERY_PATHS_STR"
IFS='|' read -ra CONNECTION_PROPERTIES <<< "$CONNECTION_PROPERTIES_STR"

# Trim whitespace from array elements
for i in "${!DATASOURCE_TYPES[@]}"; do
    DATASOURCE_TYPES[$i]=$(echo "${DATASOURCE_TYPES[$i]}" | xargs)
done
for i in "${!DISCOVERY_PATHS[@]}"; do
    DISCOVERY_PATHS[$i]=$(echo "${DISCOVERY_PATHS[$i]}" | xargs)
done
# For CONNECTION_PROPERTIES, use sed instead of xargs to preserve JSON quotes
for i in "${!CONNECTION_PROPERTIES[@]}"; do
    CONNECTION_PROPERTIES[$i]=$(echo "${CONNECTION_PROPERTIES[$i]}" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
done

# Validate array lengths match
if [ ${#DATASOURCE_TYPES[@]} -ne ${#DISCOVERY_PATHS[@]} ] || [ ${#DATASOURCE_TYPES[@]} -ne ${#CONNECTION_PROPERTIES[@]} ]; then
    echo "ERROR: Number of datasource types, paths, and connection properties must match"
    echo "  Datasource types: ${#DATASOURCE_TYPES[@]}"
    echo "  Discovery paths: ${#DISCOVERY_PATHS[@]}"
    echo "  Connection properties: ${#CONNECTION_PROPERTIES[@]}"
    exit 1
fi

# Remove trailing slash from CPD URL
CPD_URL="${CPD_URL%/}"

echo "========================================="
echo "Obtaining Bearer Token..."
echo "========================================="

# Build authentication request based on method
if [ -n "$APIKEY" ]; then
    echo "Using API Key authentication"
    TOKEN_RESPONSE=$(curl -k -s -w "\n%{http_code}" -X POST "$AUTH_URI" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "grant_type=urn:ibm:params:oauth:grant-type:apikey" \
        -d "apikey=$APIKEY")
else
    echo "Using Username/Password authentication"
    # Create JSON payload for username/password
    JSON_PAYLOAD=$(cat <<EOF
{"username":"$USERNAME","password":"$PASSWORD"}
EOF
)
    TOKEN_RESPONSE=$(curl -k -s -w "\n%{http_code}" -X POST "$AUTH_URI" \
        -H "Content-Type: application/json" \
        -d "$JSON_PAYLOAD")
fi

HTTP_CODE=$(echo "$TOKEN_RESPONSE" | tail -n1)
TOKEN_BODY=$(echo "$TOKEN_RESPONSE" | sed '$d')

if [ "$HTTP_CODE" != "200" ]; then
    echo "ERROR: Failed to obtain bearer token (HTTP $HTTP_CODE)"
    echo ""
    if [ -n "$APIKEY" ]; then
        echo "Please verify your API key is correct"
    else
        echo "Please verify your username and password are correct"
    fi
    exit 1
fi

CPD_TOKEN=$(echo "$TOKEN_BODY" | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)
if [ -z "$CPD_TOKEN" ] || [ "$CPD_TOKEN" = "null" ]; then
    # Try alternative token field name (for on-premises CP4D)
    CPD_TOKEN=$(echo "$TOKEN_BODY" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
fi

if [ -z "$CPD_TOKEN" ] || [ "$CPD_TOKEN" = "null" ]; then
    echo "ERROR: Failed to extract access token from response"
    echo "The authentication response did not contain a valid token"
    exit 1
fi

echo "✓ Bearer token obtained successfully"

# Build the API endpoint
ENDPOINT="$CPD_URL/v2/connections/assets"

echo ""
echo "Configuration:"
echo "  CPD URL: $CPD_URL"
echo "  Auth Method: $([ -n "$APIKEY" ] && echo "API Key" || echo "Username/Password")"
echo "  Endpoint: $ENDPOINT"
echo "  Number of connectors to test: ${#DATASOURCE_TYPES[@]}"
echo ""

# Track results
SUCCESS_COUNT=0
FAILURE_COUNT=0
declare -a RESULTS

# Test each connector
for i in "${!DATASOURCE_TYPES[@]}"; do
    DATASOURCE_TYPE="${DATASOURCE_TYPES[$i]}"
    DISCOVERY_PATH="${DISCOVERY_PATHS[$i]}"
    CONN_PROPERTIES="${CONNECTION_PROPERTIES[$i]}"
    
    echo "=========================================="
    echo "Testing Connector $((i + 1)) of ${#DATASOURCE_TYPES[@]}"
    echo "=========================================="
    echo "  Datasource Type: $DATASOURCE_TYPE"
    echo "  Discovery Path: $DISCOVERY_PATH"
    
    # URL encode the discovery path
    ENCODED_PATH=$(echo -n "$DISCOVERY_PATH" | jq -sRr @uri)
    
    # Build request URL with query parameters
    REQUEST_URL="${ENDPOINT}?path=${ENCODED_PATH}&fetch=data"
    
    # Validate and clean the connection properties JSON
    # First, trim any whitespace/newlines and validate
    CLEAN_PROPERTIES=$(echo "$CONN_PROPERTIES" | tr -d '\n\r' | jq -c '.' 2>/dev/null)
    if [ $? -ne 0 ] || [ -z "$CLEAN_PROPERTIES" ]; then
        echo "ERROR: Invalid JSON in CONNECTION_PROPERTIES for $DATASOURCE_TYPE"
        echo "Please verify the JSON syntax in your properties file"
        FAILURE_COUNT=$((FAILURE_COUNT + 1))
        RESULTS+=("FAILED|$DATASOURCE_TYPE|$DISCOVERY_PATH|Invalid JSON in connection properties")
        continue
    fi
    
    # Build request body using jq to ensure proper JSON formatting
    REQUEST_BODY=$(jq -n \
        --arg datasource_type "$DATASOURCE_TYPE" \
        --argjson properties "$CLEAN_PROPERTIES" \
        '{
            datasource_type: $datasource_type,
            properties: $properties
        }')
    
    echo ""
    echo "Request URL:"
    echo "$REQUEST_URL"
    echo ""
    
    echo "Sending request..."
    echo ""
    
    # Make the API call
    HTTP_CODE=$(curl -k -s -w "%{http_code}" -o /tmp/discovery_response_${i}.json \
        -X POST "$REQUEST_URL" \
        -H "Authorization: Bearer $CPD_TOKEN" \
        -H "Content-Type: application/json" \
        -H "Accept: application/json" \
        -d "$REQUEST_BODY")
    
    # Check response
    if [ "$HTTP_CODE" -ge 200 ] && [ "$HTTP_CODE" -lt 300 ]; then
        echo "✓ Discovery successful for $DATASOURCE_TYPE"
        
        # Count assets - check data array first, then resources, then assets
        ASSET_COUNT=0
        if command -v jq &> /dev/null; then
            ASSET_COUNT=$(cat /tmp/discovery_response_${i}.json | jq '.data // .resources // .assets | length' 2>/dev/null || echo "0")
        fi
        
        echo "Discovered $ASSET_COUNT asset(s)"
        
        # Display first asset if any were returned
        if [ "$ASSET_COUNT" -gt 0 ] && command -v jq &> /dev/null; then
            echo ""
            echo "First asset:"
            cat /tmp/discovery_response_${i}.json | jq '.data[0] // .resources[0] // .assets[0]' 2>/dev/null || echo "Unable to parse first asset"
        fi
        echo ""
        
        SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
        RESULTS+=("SUCCESS|$DATASOURCE_TYPE|$DISCOVERY_PATH|$ASSET_COUNT")
        
    else
        echo "✗ Discovery failed for $DATASOURCE_TYPE"
        echo "HTTP Status Code: $HTTP_CODE"
        echo "Response Body:"
        
        if command -v jq &> /dev/null; then
            cat /tmp/discovery_response_${i}.json | jq '.' 2>/dev/null || cat /tmp/discovery_response_${i}.json
        else
            cat /tmp/discovery_response_${i}.json
        fi
        echo ""
        
        FAILURE_COUNT=$((FAILURE_COUNT + 1))
        ERROR_MSG=$(cat /tmp/discovery_response_${i}.json | jq -r '.message // .error // "Unknown error"' 2>/dev/null || echo "Unknown error")
        RESULTS+=("FAILED|$DATASOURCE_TYPE|$DISCOVERY_PATH|$ERROR_MSG")
    fi
    
    # Clean up temp file
    rm -f /tmp/discovery_response_${i}.json
done

# Print summary
echo "=========================================="
echo "Test Summary"
echo "=========================================="
echo "Total connectors tested: ${#DATASOURCE_TYPES[@]}"
echo "Successful: $SUCCESS_COUNT"
if [ $FAILURE_COUNT -gt 0 ]; then
    echo "Failed: $FAILURE_COUNT"
fi

echo ""
echo "Detailed Results:"
echo ""
for result in "${RESULTS[@]}"; do
    IFS='|' read -ra PARTS <<< "$result"
    STATUS="${PARTS[0]}"
    DATASOURCE="${PARTS[1]}"
    PATH="${PARTS[2]}"
    INFO="${PARTS[3]}"
    
    if [ "$STATUS" = "SUCCESS" ]; then
        echo "✓ $DATASOURCE - Path: $PATH - Assets: $INFO"
    else
        echo "✗ $DATASOURCE - Path: $PATH - Error: $INFO"
    fi
done

if [ $FAILURE_COUNT -gt 0 ]; then
    echo ""
    echo "Troubleshooting tips:"
    echo "  1. Verify CPD_URL is correct and accessible"
    echo "  2. Check authentication credentials (APIKEY or USERNAME/PASSWORD)"
    echo "  3. Verify AUTH_URI is correct for your deployment"
    echo "  4. Ensure DATASOURCE_TYPES match registered connectors"
    echo "  5. Verify CONNECTION_PROPERTIES are correct for each datasource type"
    echo "  6. Check that the connectors are deployed and running"
    exit 1
fi

echo ""
echo "✓ All connector tests passed!"
exit 0

# Made with Bob
