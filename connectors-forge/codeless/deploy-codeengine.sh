#!/bin/bash

# IBM Cloud Code Engine Deployment Script
# Deploys a connector application to IBM Cloud Code Engine
# with configuration file upload using ConfigMaps via REST API

set -e  # Exit on error

# ============================================
# Configuration & Global Variables
# ============================================

SCRIPT_NAME=$(basename "$0")
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROPERTIES_FILE="${SCRIPT_DIR}/deploy-codeengine.properties"

# Global variables populated from properties file
APIKEY=""
REGION=""
CODE_ENGINE_PROJECT=""
RESOURCE_GROUP_ID=""
APP_NAME=""
CONFIG_FILES=()
MIN_SCALE=""
MAX_SCALE=""

# Runtime variables
BEARER_TOKEN=""
PROJECT_ID=""
CONFIGMAP_NAME="connectors-config"
CONFIGMAP_ETAG=""
IMAGE="ghcr.io/marek-zuwala/connectors-forge:1.0.4"
APP_EXISTS=false
CONFIGMAP_EXISTS=false
API_VERSION="2025-07-10"
IAM_TOKEN_URL="https://iam.cloud.ibm.com/identity/token"

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

# Extract a JSON field value from a JSON string
# Usage: extract_json_field "json_string" "field_name"
extract_json_field() {
    local json="$1"
    local field="$2"
    echo "$json" | grep -o "\"${field}\":\"[^\"]*\"" | head -1 | cut -d'"' -f4
}

# ============================================
# Usage & Help
# ============================================

usage() {
    cat << EOF
Usage: $SCRIPT_NAME --files FILE1 [FILE2 ...] [OPTIONS]

Deploy a connector application to IBM Cloud Code Engine using REST API.
Reads configuration from deploy-codeengine.properties file.

REQUIRED ARGUMENTS:
    --files FILE1 [FILE2 ...]   Space-separated list of connector JSON files (full paths)

OPTIONS:
    --help, -h                  Show this help message

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
  - curl command must be available
  - IBM Cloud API key with Code Engine permissions
  - Properties file must exist: $PROPERTIES_FILE

ConfigMap Merge Behavior:
  - When ConfigMap exists: Files are MERGED (not replaced)
    * Matching filenames: Content is updated
    * New filenames: Files are added
    * Existing files not specified: Remain unchanged
  - Files are never removed automatically (manage via Code Engine UI)
  - Application restarts automatically after ConfigMap updates

Examples:
  # Deploy single connector file (relative path)
  $SCRIPT_NAME --files ./my-connector.json

  # Deploy single connector file (absolute path)
  $SCRIPT_NAME --files /path/to/servicenow-api.json

  # Deploy multiple connector files
  $SCRIPT_NAME --files ./connectors/api1.json ./connectors/api2.json ./connectors/api3.json

  # Deploy from SDK resources directory
  $SCRIPT_NAME --files ../sdk-gen/subprojects/connectors_forge_rest/src/main/resources/servicenow-api.json

  # Update existing file (merges with ConfigMap)
  $SCRIPT_NAME --files ./connectors/updated-api.json

  # Add new file to existing ConfigMap
  $SCRIPT_NAME --files ./connectors/new-connector.json

  # Show help
  $SCRIPT_NAME --help

EOF
    exit 1
}

# ============================================
# Parse Command Line Arguments
# ============================================

parse_arguments() {
    if [ $# -eq 0 ]; then
        usage
    fi

    while [ $# -gt 0 ]; do
        case "$1" in
            --files)
                shift
                # Collect all files until we hit another option or end of args
                while [ $# -gt 0 ] && [[ ! "$1" =~ ^-- ]]; do
                    CONFIG_FILES+=("$1")
                    shift
                done
                # Validate that at least one file was collected
                if [ ${#CONFIG_FILES[@]} -eq 0 ]; then
                    log_error "No files provided after --files argument"
                    usage
                fi
                ;;
            -h|--help)
                usage
                ;;
            *)
                log_error "Unknown option: $1"
                usage
                ;;
        esac
    done

    # Validate required arguments
    if [ ${#CONFIG_FILES[@]} -eq 0 ]; then
        log_error "Missing required argument: --files"
        usage
    fi
}

parse_arguments "$@"

# ============================================
# Properties File Functions
# ============================================

get_property() {
    local key=$1
    local value=$(grep -v '^[[:space:]]*#' "$PROPERTIES_FILE" | grep "^${key}=" | cut -d'=' -f2- | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
    echo "$value"
}

load_properties() {
    log_step "1/8" "Loading configuration from properties file..."
    
    if [ ! -f "$PROPERTIES_FILE" ]; then
        log_error "Properties file not found: $PROPERTIES_FILE"
        echo ""
        echo "Please create $PROPERTIES_FILE from the template:"
        echo "  cp ${PROPERTIES_FILE}.template $PROPERTIES_FILE"
        echo ""
        echo "Then edit the file and fill in your configuration values."
        exit 1
    fi
    
    log "Found properties file: $PROPERTIES_FILE"
    
    # Read required properties
    APIKEY=$(get_property "APIKEY")
    REGION=$(get_property "REGION")
    
    # Read optional properties
    RESOURCE_GROUP_ID=$(get_property "RESOURCE_GROUP_ID")
    
    # Read optional properties with defaults
    CODE_ENGINE_PROJECT=$(get_property "CODE_ENGINE_PROJECT")
    if [ -z "$CODE_ENGINE_PROJECT" ]; then
        CODE_ENGINE_PROJECT="ce-connectors-forge"
    fi
    
    APP_NAME=$(get_property "APP_NAME")
    if [ -z "$APP_NAME" ]; then
        APP_NAME="ce-connectors-forge"
    fi
    
    MIN_SCALE=$(get_property "MIN_SCALE")
    if [ -z "$MIN_SCALE" ]; then
        MIN_SCALE="0"
    fi
    
    MAX_SCALE=$(get_property "MAX_SCALE")
    if [ -z "$MAX_SCALE" ]; then
        MAX_SCALE="2"
    fi
    
    log "Configuration loaded successfully"
}

# ============================================
# Validation Functions
# ============================================

validate_properties() {
    log_step "2/8" "Validating configuration..."
    
    local validation_failed=false
    
    # Check required properties
    if [ -z "$APIKEY" ]; then
        log_error "Missing required property: APIKEY"
        validation_failed=true
    fi
    
    if [ -z "$REGION" ]; then
        log_error "Missing required property: REGION"
        validation_failed=true
    fi
    
    
    # Check if curl is available
    if ! command -v curl &> /dev/null; then
        log_error "curl command not found. Please install curl."
        validation_failed=true
    fi
    
    # Validate that all specified files exist
    local file_count=0
    for file in "${CONFIG_FILES[@]}"; do
        if [ ! -f "$file" ]; then
            log_error "File not found: $file"
            validation_failed=true
        else
            file_count=$((file_count + 1))
        fi
    done
    
    if [ "$validation_failed" = true ]; then
        echo ""
        log_error "Validation failed. Please update $PROPERTIES_FILE with required values."
        exit 1
    fi
    
    log "All validations passed"
    log "Region: $REGION"
    log "Project: $CODE_ENGINE_PROJECT"
    log "Config files: $file_count file(s) specified"
}

# ============================================
# Authentication Functions
# ============================================

get_iam_token() {
    log_step "3/8" "Obtaining IAM bearer token..."
    
    local token_response=$(curl -k -s -w "\n%{http_code}" -X POST "$IAM_TOKEN_URL" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "grant_type=urn:ibm:params:oauth:grant-type:apikey" \
        -d "apikey=$APIKEY")
    
    local http_code=$(echo "$token_response" | tail -n1)
    local token_body=$(echo "$token_response" | sed '$d')
    
    if [ "$http_code" != "200" ]; then
        log_error "Failed to obtain bearer token (HTTP $http_code)"
        log_error "Please verify your API key is correct in $PROPERTIES_FILE"
        exit 1
    fi
    
    BEARER_TOKEN=$(extract_json_field "$token_body" "access_token")
    
    if [ -z "$BEARER_TOKEN" ] || [ "$BEARER_TOKEN" = "null" ]; then
        log_error "Failed to extract access token from response"
        exit 1
    fi
    
    log "Bearer token obtained successfully"
}

# ============================================
# Project Functions
# ============================================

create_project() {
    log "Project '${CODE_ENGINE_PROJECT}' not found - creating it..."
    
    local api_url="https://api.${REGION}.codeengine.cloud.ibm.com/v2/projects?version=${API_VERSION}"
    
    # Build payload with optional resource_group_id
    local payload="{\"name\":\"${CODE_ENGINE_PROJECT}\""
    if [ -n "$RESOURCE_GROUP_ID" ]; then
        payload="${payload},\"resource_group_id\":\"${RESOURCE_GROUP_ID}\""
    fi
    payload="${payload}}"
    
    local response=$(curl -k -s -w "\n%{http_code}" -X POST "$api_url" \
        -H "Authorization: Bearer ${BEARER_TOKEN}" \
        -H "Content-Type: application/json" \
        -d "$payload")
    
    local http_code=$(echo "$response" | tail -n1)
    local response_body=$(echo "$response" | sed '$d')
    
    if [ "$http_code" != "200" ] && [ "$http_code" != "201" ] && [ "$http_code" != "202" ]; then
        log_error "Failed to create project (HTTP $http_code)"
        log_error "Response: $response_body"
        exit 1
    fi
    
    # Extract project ID from response
    PROJECT_ID=$(extract_json_field "$response_body" "id")
    
    if [ -z "$PROJECT_ID" ]; then
        log_error "Failed to extract project ID from create response"
        exit 1
    fi
    
    log "Project created successfully with ID: $PROJECT_ID"
    
    # Wait for project to be ready
    local timeout=120
    log "Waiting for project to be ready (timeout: ${timeout}s)..."
    local elapsed=0
    
    while [ $elapsed -lt $timeout ]; do
        local status_response=$(curl -k -s -w "\n%{http_code}" -X GET \
            "https://api.${REGION}.codeengine.cloud.ibm.com/v2/projects/${PROJECT_ID}?version=${API_VERSION}" \
            -H "Authorization: Bearer ${BEARER_TOKEN}")
        
        local status_code=$(echo "$status_response" | tail -n1)
        local status_body=$(echo "$status_response" | sed '$d')
        
        if [ "$status_code" = "200" ]; then
            local status=$(extract_json_field "$status_body" "status")
            if [ "$status" = "active" ]; then
                log "Project is ready"
                return 0
            fi
        fi
        
        sleep 5
        elapsed=$((elapsed + 5))
    done
    
    log_error "Project did not become ready within ${timeout} seconds"
    exit 1
}

get_project_id() {
    log_step "4/8" "Looking up Code Engine project ID..."
    
    local api_url="https://api.${REGION}.codeengine.cloud.ibm.com/v2/projects?version=${API_VERSION}&limit=1"
    local next_start=""
    
    # Loop through all pages until project is found or no more pages
    while true; do
        local request_url="$api_url"
        if [ -n "$next_start" ]; then
            request_url="${api_url}&start=${next_start}"
        fi
        
        local response=$(curl -k -s -w "\n%{http_code}" -X GET "$request_url" \
            -H "Authorization: Bearer ${BEARER_TOKEN}")
        
        local http_code=$(echo "$response" | tail -n1)
        local response_body=$(echo "$response" | sed '$d')
        
        if [ "$http_code" != "200" ]; then
            log_error "Failed to list projects (HTTP $http_code)"
            log_error "Response: $response_body"
            exit 1
        fi
        
        # Extract project ID by matching project name
        # This approach finds the project object and extracts the id field from it
        PROJECT_ID=$(echo "$response_body" | awk -v project="$CODE_ENGINE_PROJECT" '
            BEGIN { RS="{"; FS="\""; found=0 }
            /"name"/ && /"id"/ {
                name=""; id=""
                for (i=1; i<=NF; i++) {
                    if ($i == "name") name=$(i+2)
                    if ($i == "id")   id=$(i+2)
                }
                if (name == project && id != "") {
                    print id
                    exit
                }
            }
        ')
        
        if [ -n "$PROJECT_ID" ]; then
            log "Project ID: $PROJECT_ID"
            return 0
        fi
        
        # Check if there's a next page by extracting the "start" token from "next" object
        next_start=$(echo "$response_body" | grep -o '"next"[[:space:]]*:[[:space:]]*{[^}]*"start"[[:space:]]*:[[:space:]]*"[^"]*"' | grep -o '"start"[[:space:]]*:[[:space:]]*"[^"]*"' | cut -d'"' -f4)
        
        # If no next page, break out of loop
        if [ -z "$next_start" ]; then
            break
        fi
        
    done
    
    # Project not found in any page - create it
    log "Project '${CODE_ENGINE_PROJECT}' not found"
    create_project
}

# ============================================
# JSON Helper Functions
# ============================================

escape_json_string() {
    local input="$1"
    # Escape backslashes first, then quotes, then convert newlines
    echo "$input" | sed 's/\\/\\\\/g' | sed 's/"/\\"/g' | awk '{printf "%s\\n", $0}' | sed 's/\\n$//'
}

get_existing_configmap_data() {
    local api_url="https://api.${REGION}.codeengine.cloud.ibm.com/v2/projects/${PROJECT_ID}/config_maps/${CONFIGMAP_NAME}?version=${API_VERSION}"
    
    local response=$(curl -k -s -w "\n%{http_code}" -X GET "$api_url" \
        -H "Authorization: Bearer ${BEARER_TOKEN}")
    
    local http_code=$(echo "$response" | tail -n1)
    local response_body=$(echo "$response" | sed '$d')
    
    if [ "$http_code" = "200" ]; then
        # Extract the data section from the response
        echo "$response_body" | awk '
        {
        start = index($0, "\"data\":{")
        if (start) {
            i = start + length("\"data\":")
            brace = 0
            for (; i <= length($0); i++) {
            c = substr($0, i, 1)
            if (c == "{") {
                if (brace == 0) begin = i + 1
                brace++
            } else if (c == "}") {
                brace--
                if (brace == 0) {
                print substr($0, begin, i - begin)
                exit
                }
            }
            }
        }
        }'
        ``
    else
        echo ""
    fi
}

build_configmap_data() {
    local existing_data="$1"
    local data_json="$existing_data"
    
    
    # Process each file in CONFIG_FILES
    for file in "${CONFIG_FILES[@]}"; do
        if [ ! -f "$file" ]; then
            log_error "File not found: $file"
            exit 1
        fi
        
        local filename=$(basename "$file")
        
        # Validate filename contains only safe characters (alphanumeric, dash, underscore, dot)
        if ! [[ "$filename" =~ ^[a-zA-Z0-9._-]+$ ]]; then
            log_error "Invalid filename: $filename. Filenames must contain only alphanumeric characters, dots, dashes, and underscores."
            exit 1
        fi
        
        local content=$(cat "$file")
        local escaped=$(escape_json_string "$content")
        
        # Check if this filename already exists in the data
        if echo "$data_json" | grep -q "\"${filename}\":"; then
            # Remove the old entry using sed
            # We need to match from "filename":" to the next unescaped "
            data_json=$(echo "$data_json" | sed "s/\"${filename}\":\"[^\"]*\(\\\\\"[^\"]*\)*\"//g")
            # Remove any resulting double commas or leading/trailing commas
            data_json=$(echo "$data_json" | sed 's/,,/,/g' | sed 's/^,//' | sed 's/,$//')
        fi
        
        # Add the new/updated entry
        if [ -n "$data_json" ] && [ "$data_json" != "" ]; then
            data_json="${data_json},\"${filename}\":\"${escaped}\""
        else
            data_json="\"${filename}\":\"${escaped}\""
        fi
    done
    
    echo "$data_json"
}
# ============================================
# ConfigMap Management
# ============================================

check_configmap_exists() {
    local api_url="https://api.${REGION}.codeengine.cloud.ibm.com/v2/projects/${PROJECT_ID}/config_maps/${CONFIGMAP_NAME}?version=${API_VERSION}"
    
    local response=$(curl -k -s -w "\n%{http_code}" -X GET "$api_url" \
        -H "Authorization: Bearer ${BEARER_TOKEN}")
    
    local http_code=$(echo "$response" | tail -n1)
    local response_body=$(echo "$response" | sed '$d')
    
    if [ "$http_code" = "200" ]; then
        CONFIGMAP_EXISTS=true
        # Extract ETag from response for If-Match header
        CONFIGMAP_ETAG=$(extract_json_field "$response_body" "entity_tag")
    else
        CONFIGMAP_EXISTS=false
        CONFIGMAP_ETAG=""
    fi
}

create_or_update_configmap() {
    log_step "5/8" "Managing ConfigMap..."
    
    check_configmap_exists
    
    local existing_data=""
    local data_json=""
    
    if [ "$CONFIGMAP_EXISTS" = true ]; then
        log "ConfigMap '$CONFIGMAP_NAME' already exists - merging files..."
        
        # Get existing ConfigMap data
        existing_data=$(get_existing_configmap_data)
        # Build merged data
        data_json=$(build_configmap_data "$existing_data")
        log "Updating ConfigMap '$CONFIGMAP_NAME'..."
    else
        log "Creating ConfigMap '$CONFIGMAP_NAME'..."
        
        # Build data for new ConfigMap
        data_json=$(build_configmap_data "")
    fi
    
    # Build payload based on operation
    local payload
    if [ "$CONFIGMAP_EXISTS" = true ]; then
        # For updates, only send the data object
        payload="{\"data\":{${data_json}}}"
        
        # Update existing ConfigMap with If-Match header
        log "Updating ConfigMap with ETag: $CONFIGMAP_ETAG"
        local api_url="https://api.${REGION}.codeengine.cloud.ibm.com/v2/projects/${PROJECT_ID}/config_maps/${CONFIGMAP_NAME}?version=${API_VERSION}"
        local response=$(curl -k -s -w "\n%{http_code}" -X PUT "$api_url" \
            -H "Authorization: Bearer ${BEARER_TOKEN}" \
            -H "Content-Type: application/merge-patch+json" \
            -H "If-Match: ${CONFIGMAP_ETAG}" \
            -d "$payload")
        
        local http_code=$(echo "$response" | tail -n1)
        
        if [ "$http_code" != "200" ]; then
            local response_body=$(echo "$response" | sed '$d')
            log_error "Failed to update ConfigMap (HTTP $http_code)"
            log_error "Response: $response_body"
            exit 1
        fi
        
        log "ConfigMap '$CONFIGMAP_NAME' updated successfully"
    else
        # For creation, include name and data
        payload="{\"name\":\"${CONFIGMAP_NAME}\",\"data\":{${data_json}}}"
        
        # Create new ConfigMap
        local api_url="https://api.${REGION}.codeengine.cloud.ibm.com/v2/projects/${PROJECT_ID}/config_maps?version=${API_VERSION}"
        
        local response=$(curl -k -s -w "\n%{http_code}" -X POST "$api_url" \
            -H "Authorization: Bearer ${BEARER_TOKEN}" \
            -H "Content-Type: application/json" \
            -d "$payload")
        
        local http_code=$(echo "$response" | tail -n1)
        
        if [ "$http_code" != "200" ] && [ "$http_code" != "201" ]; then
            local response_body=$(echo "$response" | sed '$d')
            log_error "Failed to create ConfigMap (HTTP $http_code)"
            log_error "Response: $response_body"
            exit 1
        fi
        
        log "ConfigMap '$CONFIGMAP_NAME' created successfully"
    fi
}

# ============================================
# Application Management
# ============================================

check_app_exists() {
    log_step "6/8" "Checking for existing application..."
    
    local api_url="https://api.${REGION}.codeengine.cloud.ibm.com/v2/projects/${PROJECT_ID}/apps/${APP_NAME}?version=${API_VERSION}"
    
    local response=$(curl -k -s -w "\n%{http_code}" -X GET "$api_url" \
        -H "Authorization: Bearer ${BEARER_TOKEN}")
    
    local http_code=$(echo "$response" | tail -n1)
    
    if [ "$http_code" = "200" ]; then
        APP_EXISTS=true
        log "Application '$APP_NAME' already exists"
    else
        APP_EXISTS=false
        log "No existing application found"
    fi
}

build_env_variables() {
    local timestamp=$(date +%s)
    cat <<EOF
[
  {
    "key": "ENABLE_SSL",
    "name": "ENABLE_SSL",
    "type": "literal",
    "value": "false"
  },
  {
    "key": "LAST_UPDATED",
    "name": "LAST_UPDATED",
    "type": "literal",
    "value": "${timestamp}"
  }
]
EOF
}

update_app_with_timestamp() {
    log "Updating application with timestamp to force new revision..."
    
    local api_url="https://api.${REGION}.codeengine.cloud.ibm.com/v2/projects/${PROJECT_ID}/apps/${APP_NAME}?version=${API_VERSION}"
    local env_variables=$(build_env_variables)
    
    # Build payload with environment variables
    local payload=$(cat <<EOF
{
  "run_env_variables": ${env_variables}
}
EOF
)
    
    local response=$(curl -k -s -w "\n%{http_code}" -X PATCH "$api_url" \
        -H "Authorization: Bearer ${BEARER_TOKEN}" \
        -H "Content-Type: application/json" \
        -H "If-Match: *" \
        -d "$payload")
    
    local http_code=$(echo "$response" | tail -n1)
    
    if [ "$http_code" != "200" ]; then
        local response_body=$(echo "$response" | sed '$d')
        log_error "Failed to update application with timestamp (HTTP $http_code)"
        log_error "Response: $response_body"
        exit 1
    fi
    
    log "Application updated with new timestamp"
    log "New revision will be created automatically"
}

deploy_application() {
    log_step "7/8" "Deploying application..."
    
    if [ "$APP_EXISTS" = true ]; then
        log "Application exists and ConfigMap was merged"
        update_app_with_timestamp
        return 0
    fi
    
    log "Creating application '$APP_NAME'..."
    
    local env_variables=$(build_env_variables)
    
    # Build application payload
    local payload=$(cat <<EOF
{
  "name": "${APP_NAME}",
  "image_reference": "${IMAGE}",
  "image_port": 9443,
  "image_protocol": "h2c",
  "scale_min_instances": ${MIN_SCALE},
  "scale_max_instances": ${MAX_SCALE},
  "run_env_variables": ${env_variables},
  "run_volume_mounts": [
    {
      "type": "config_map",
      "mount_path": "/config/mappings",
      "reference": "${CONFIGMAP_NAME}",
      "read_only": true
    }
  ]
}
EOF
)
        
    local api_url="https://api.${REGION}.codeengine.cloud.ibm.com/v2/projects/${PROJECT_ID}/apps?version=${API_VERSION}"
    
    local response=$(curl -k -s -w "\n%{http_code}" -X POST "$api_url" \
        -H "Authorization: Bearer ${BEARER_TOKEN}" \
        -H "Content-Type: application/json" \
        -d "$payload")
    
    local http_code=$(echo "$response" | tail -n1)
    
    if [ "$http_code" != "200" ] && [ "$http_code" != "201" ]; then
        local response_body=$(echo "$response" | sed '$d')
        log_error "Failed to create application (HTTP $http_code)"
        log_error "Response: $response_body"
        exit 1
    fi
    
    log "Application '$APP_NAME' created successfully"
}

# ============================================
# Application Verification
# ============================================

verify_application() {
    log_step "8/8" "Verifying application status..."
    
    local timeout=120
    log "Waiting for application to be ready (timeout: ${timeout}s)..."
    local elapsed=0
    local status=""
    
    local api_url="https://api.${REGION}.codeengine.cloud.ibm.com/v2/projects/${PROJECT_ID}/apps/${APP_NAME}?version=${API_VERSION}"
    
    while [ $elapsed -lt $timeout ]; do
        local response=$(curl -k -s -w "\n%{http_code}" -X GET "$api_url" \
            -H "Authorization: Bearer ${BEARER_TOKEN}")
        
        local http_code=$(echo "$response" | tail -n1)
        local response_body=$(echo "$response" | sed '$d')
        
        if [ "$http_code" = "200" ]; then
            # Extract status from response
            status=$(extract_json_field "$response_body" "status")
            
            if [ "$status" = "ready" ]; then
                log "Application is ready"
                return 0
            fi
            
            log "Current status: $status (${elapsed}s elapsed, waiting...)"
        fi
        
        sleep 5
        elapsed=$((elapsed + 5))
    done
    
    log_error "Application did not become ready within ${timeout} seconds (Status: $status)"
    exit 1
}

# ============================================
# Display Information
# ============================================

display_info() {
    echo ""
    echo "=========================================="
    echo "Deployment Successful!"
    echo "=========================================="
    
    # Get application details
    local api_url="https://api.${REGION}.codeengine.cloud.ibm.com/v2/projects/${PROJECT_ID}/apps/${APP_NAME}?version=${API_VERSION}"
    
    local response=$(curl -k -s -X GET "$api_url" \
        -H "Authorization: Bearer ${BEARER_TOKEN}")
    
    # Extract URL from response
    local app_url=$(extract_json_field "$response" "endpoint")
    
    if [ -n "$app_url" ]; then
        # Convert https:// to grpc+tls:// for gRPC endpoint
        local grpc_url=$(echo "$app_url" | sed 's|^https://|grpc+tls://|')
        
        echo ""
        echo "Public Application URL:"
        echo "  $app_url"
        echo ""
        echo "gRPC Endpoint:"
        echo "  ${grpc_url}:443"
        echo ""
    fi
    
    echo "Application Name:  $APP_NAME"
    echo "Project:           $CODE_ENGINE_PROJECT"
    echo "Region:            $REGION"
    echo "ConfigMap:         $CONFIGMAP_NAME"
    echo ""
    echo "Resources:"
    echo "  Min Scale:       $MIN_SCALE"
    echo "  Max Scale:       $MAX_SCALE"
    echo ""
    echo "To update connector files:"
    echo "  1. Update or add JSON files"
    echo "  2. Run this script again with --files <file-path(s)>"
    echo "  3. Files will be merged into ConfigMap (existing files updated, new files added)"
    echo "  4. Application will restart automatically with new configuration"
    echo ""
    echo "Note: Files are never removed automatically. To remove files, use the Code Engine UI."
    echo "=========================================="
}

# ============================================
# Main Execution
# ============================================

main() {
    echo ""
    echo "=========================================="
    echo "IBM Cloud Code Engine Deployment"
    echo "=========================================="
    echo ""
    
    load_properties
    validate_properties
    get_iam_token
    get_project_id
    create_or_update_configmap
    check_app_exists
    deploy_application
    verify_application
    display_info
}

# ============================================
# Script Entry Point
# ============================================

main

# Made with Bob