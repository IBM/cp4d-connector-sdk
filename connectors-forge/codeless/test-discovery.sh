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

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

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
    echo -e "${RED}ERROR: Properties file not found: $PROPERTIES_FILE${NC}"
    echo -e "${CYAN}Please copy test-discovery.properties.template to $PROPERTIES_FILE and configure it.${NC}"
    exit 1
fi

echo -e "${CYAN}Loading configuration from: $PROPERTIES_FILE${NC}"

# Function to read properties file
read_property() {
    local key=$1
    local value=$(grep "^${key}=" "$PROPERTIES_FILE" | cut -d'=' -f2- | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
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
    echo -e "${RED}ERROR: CPD_URL is not set in properties file${NC}"
    exit 1
fi

# Check authentication credentials
if [ -z "$APIKEY" ] && { [ -z "$USERNAME" ] || [ -z "$PASSWORD" ]; }; then
    echo -e "${RED}ERROR: Missing authentication credentials${NC}"
    echo -e "${RED}Provide either APIKEY OR both USERNAME and PASSWORD${NC}"
    exit 1
fi

if [ -n "$APIKEY" ] && { [ -n "$USERNAME" ] || [ -n "$PASSWORD" ]; }; then
    echo -e "${RED}ERROR: Conflicting authentication methods${NC}"
    echo -e "${RED}Provide either APIKEY OR USERNAME+PASSWORD, not both${NC}"
    exit 1
fi

if [ -z "$AUTH_URI" ]; then
    echo -e "${RED}ERROR: AUTH_URI is not set in properties file${NC}"
    exit 1
fi

if [ -z "$DATASOURCE_TYPES_STR" ]; then
    echo -e "${RED}ERROR: DATASOURCE_TYPES is not set in properties file${NC}"
    exit 1
fi

if [ -z "$DISCOVERY_PATHS_STR" ]; then
    echo -e "${RED}ERROR: DISCOVERY_PATHS is not set in properties file${NC}"
    exit 1
fi

if [ -z "$CONNECTION_PROPERTIES_STR" ]; then
    echo -e "${RED}ERROR: CONNECTION_PROPERTIES is not set in properties file${NC}"
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
    echo -e "${RED}ERROR: Number of datasource types, paths, and connection properties must match${NC}"
    echo -e "${RED}  Datasource types: ${#DATASOURCE_TYPES[@]}${NC}"
    echo -e "${RED}  Discovery paths: ${#DISCOVERY_PATHS[@]}${NC}"
    echo -e "${RED}  Connection properties: ${#CONNECTION_PROPERTIES[@]}${NC}"
    exit 1
fi

# Remove trailing slash from CPD URL
CPD_URL="${CPD_URL%/}"

echo -e "${CYAN}=========================================${NC}"
echo -e "${CYAN}Obtaining Bearer Token...${NC}"
echo -e "${CYAN}=========================================${NC}"

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
    echo -e "${RED}ERROR: Failed to obtain bearer token (HTTP $HTTP_CODE)${NC}"
    echo ""
    if [ -n "$APIKEY" ]; then
        echo -e "${RED}Please verify your API key is correct${NC}"
    else
        echo -e "${RED}Please verify your username and password are correct${NC}"
    fi
    exit 1
fi

CPD_TOKEN=$(echo "$TOKEN_BODY" | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)
if [ -z "$CPD_TOKEN" ] || [ "$CPD_TOKEN" = "null" ]; then
    # Try alternative token field name (for on-premises CP4D)
    CPD_TOKEN=$(echo "$TOKEN_BODY" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
fi

if [ -z "$CPD_TOKEN" ] || [ "$CPD_TOKEN" = "null" ]; then
    echo -e "${RED}ERROR: Failed to extract access token from response${NC}"
    echo -e "${RED}The authentication response did not contain a valid token${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Bearer token obtained successfully${NC}"

# Build the API endpoint
ENDPOINT="$CPD_URL/v2/connections/assets"

echo -e "${CYAN}"
echo "Configuration:"
echo "  CPD URL: $CPD_URL"
echo "  Auth Method: $([ -n "$APIKEY" ] && echo "API Key" || echo "Username/Password")"
echo "  Endpoint: $ENDPOINT"
echo "  Number of connectors to test: ${#DATASOURCE_TYPES[@]}"
echo -e "${NC}"

# Track results
SUCCESS_COUNT=0
FAILURE_COUNT=0
declare -a RESULTS

# Test each connector
for i in "${!DATASOURCE_TYPES[@]}"; do
    DATASOURCE_TYPE="${DATASOURCE_TYPES[$i]}"
    DISCOVERY_PATH="${DISCOVERY_PATHS[$i]}"
    CONN_PROPERTIES="${CONNECTION_PROPERTIES[$i]}"
    
    echo -e "${CYAN}==========================================${NC}"
    echo -e "${CYAN}Testing Connector $((i + 1)) of ${#DATASOURCE_TYPES[@]}${NC}"
    echo -e "${CYAN}==========================================${NC}"
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
        echo -e "${RED}ERROR: Invalid JSON in CONNECTION_PROPERTIES for $DATASOURCE_TYPE${NC}"
        echo -e "${RED}Please verify the JSON syntax in your properties file${NC}"
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
    
    echo -e "${CYAN}"
    echo "Request URL:"
    echo -e "${NC}"
    echo "$REQUEST_URL"
    echo ""
    
    echo -e "${CYAN}Sending request...${NC}"
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
        echo -e "${GREEN}✓ Discovery successful for $DATASOURCE_TYPE${NC}"
        
        # Count assets - check data array first, then resources, then assets
        ASSET_COUNT=0
        if command -v jq &> /dev/null; then
            ASSET_COUNT=$(cat /tmp/discovery_response_${i}.json | jq '.data // .resources // .assets | length' 2>/dev/null || echo "0")
        fi
        
        echo -e "${GREEN}Discovered $ASSET_COUNT asset(s)${NC}"
        
        # Display first asset if any were returned
        if [ "$ASSET_COUNT" -gt 0 ] && command -v jq &> /dev/null; then
            echo ""
            echo -e "${CYAN}First asset:${NC}"
            cat /tmp/discovery_response_${i}.json | jq '.data[0] // .resources[0] // .assets[0]' 2>/dev/null || echo "Unable to parse first asset"
        fi
        echo ""
        
        SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
        RESULTS+=("SUCCESS|$DATASOURCE_TYPE|$DISCOVERY_PATH|$ASSET_COUNT")
        
    else
        echo -e "${RED}✗ Discovery failed for $DATASOURCE_TYPE${NC}"
        echo -e "${RED}HTTP Status Code: $HTTP_CODE${NC}"
        echo -e "${RED}Response Body:${NC}"
        
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
echo -e "${CYAN}==========================================${NC}"
echo -e "${CYAN}Test Summary${NC}"
echo -e "${CYAN}==========================================${NC}"
echo "Total connectors tested: ${#DATASOURCE_TYPES[@]}"
echo -e "${GREEN}Successful: $SUCCESS_COUNT${NC}"
if [ $FAILURE_COUNT -gt 0 ]; then
    echo -e "${RED}Failed: $FAILURE_COUNT${NC}"
fi

echo -e "${CYAN}"
echo "Detailed Results:"
echo -e "${NC}"
for result in "${RESULTS[@]}"; do
    IFS='|' read -ra PARTS <<< "$result"
    STATUS="${PARTS[0]}"
    DATASOURCE="${PARTS[1]}"
    PATH="${PARTS[2]}"
    INFO="${PARTS[3]}"
    
    if [ "$STATUS" = "SUCCESS" ]; then
        echo -e "${GREEN}✓ $DATASOURCE - Path: $PATH - Assets: $INFO${NC}"
    else
        echo -e "${RED}✗ $DATASOURCE - Path: $PATH - Error: $INFO${NC}"
    fi
done

if [ $FAILURE_COUNT -gt 0 ]; then
    echo ""
    echo -e "${CYAN}Troubleshooting tips:${NC}"
    echo "  1. Verify CPD_URL is correct and accessible"
    echo "  2. Check authentication credentials (APIKEY or USERNAME/PASSWORD)"
    echo "  3. Verify AUTH_URI is correct for your deployment"
    echo "  4. Ensure DATASOURCE_TYPES match registered connectors"
    echo "  5. Verify CONNECTION_PROPERTIES are correct for each datasource type"
    echo "  6. Check that the connectors are deployed and running"
    exit 1
fi

echo ""
echo -e "${GREEN}✓ All connector tests passed!${NC}"
exit 0

# Made with Bob
