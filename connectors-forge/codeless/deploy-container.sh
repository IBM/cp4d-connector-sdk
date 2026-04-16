#!/bin/bash

# Container Deployment Script
# Deploys a container to a remote Docker server via REST API
# with configuration file upload

set -e  # Exit on error

# ============================================
# Configuration & Global Variables
# ============================================

SCRIPT_NAME=$(basename "$0")
DOCKER_HOST=""
CONFIG_FILES=()
PORT=""
REPLACE_MODE=false
IMAGE="docker.io/marek02/connectors-forge:latest"
CONTAINER_ID=""
CONTAINER_NAME=""
TEMP_DIR=""
TEMP_ARCHIVE=""

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

json_value() {
    local json="$1"
    local key="$2"
    echo "$json" | grep -o "\"$key\"[[:space:]]*:[[:space:]]*\"[^\"]*\"" | sed 's/.*"\([^"]*\)"$/\1/' | head -1
}

url_encode() {
    local string="$1"
    local strlen=${#string}
    local encoded=""
    local pos c o
    
    for (( pos=0 ; pos<strlen ; pos++ )); do
        c=${string:$pos:1}
        case "$c" in
            [-_.~a-zA-Z0-9] ) o="${c}" ;;
            * ) printf -v o '%%%02x' "'$c"
        esac
        encoded+="${o}"
    done
    echo "${encoded}"
}

# ============================================
# Usage & Help
# ============================================

usage() {
    cat << EOF
Usage: $SCRIPT_NAME --host HOST --files FILE1 [FILE2 ...] --port PORT [OPTIONS]

Deploy a container to a remote Docker server via REST API.

Required Arguments:
  --host HOST          Remote Docker server address (e.g., remote-server.com:2375)
  --files FILE1 ...    Space-separated list of files to upload (e.g., api1.json api2.json)
  --port PORT          Port to expose (e.g., 9090)

Optional Arguments:
  --replace            Stop and remove existing container with same name or using the same port
  -h, --help           Show this help message

Examples:
  # Deploy with single file
  $SCRIPT_NAME --host remote.example.com:2375 --files ./config-files/my-api.json --port 9090

  # Deploy with multiple files
  $SCRIPT_NAME --host remote.example.com:2375 --files api1.json api2.json settings.json --port 9090

  # Replace existing container (by name or port conflict)
  $SCRIPT_NAME --host remote.example.com:2375 --files my-api.json --port 9090 --replace

EOF
    exit 1
}

# ============================================
# Cleanup Function
# ============================================

cleanup() {
    if [ -n "$TEMP_ARCHIVE" ] && [ -f "$TEMP_ARCHIVE" ]; then
        rm -f "$TEMP_ARCHIVE"
    fi
    if [ -n "$TEMP_DIR" ] && [ -d "$TEMP_DIR" ]; then
        rm -rf "$TEMP_DIR"
    fi
}

trap cleanup EXIT

# ============================================
# Error Handler
# ============================================

error_exit() {
    local message="$1"
    log_error "$message"
    
    # If container was created but deployment failed, try to remove it
    if [ -n "$CONTAINER_ID" ]; then
        log "Cleaning up failed deployment..."
        remove_container "$CONTAINER_ID" 2>/dev/null || true
    fi
    
    exit 1
}

# ============================================
# Argument Parsing
# ============================================

parse_arguments() {
    if [ $# -eq 0 ]; then
        usage
    fi

    while [ $# -gt 0 ]; do
        case "$1" in
            --host)
                DOCKER_HOST="$2"
                shift 2
                ;;
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
            --port)
                PORT="$2"
                shift 2
                ;;
            --replace)
                REPLACE_MODE=true
                shift
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
    if [ -z "$DOCKER_HOST" ]; then
        log_error "Missing required argument: --host"
        usage
    fi

    if [ ${#CONFIG_FILES[@]} -eq 0 ]; then
        log_error "Missing required argument: --files"
        usage
    fi

    if [ -z "$PORT" ]; then
        log_error "Missing required argument: --port"
        usage
    fi
}

# ============================================
# Validation Functions
# ============================================

validate_inputs() {
    log_step "1/8" "Validating inputs..."

    # Check if all config files exist
    local file_count=0
    for file in "${CONFIG_FILES[@]}"; do
        if [ ! -f "$file" ]; then
            error_exit "File not found: $file"
        fi
        file_count=$((file_count + 1))
    done
    log "All $file_count file(s) exist"

    # Validate port number
    if ! [[ "$PORT" =~ ^[0-9]+$ ]] || [ "$PORT" -lt 1 ] || [ "$PORT" -gt 65535 ]; then
        error_exit "Invalid port number: $PORT (must be 1-65535)"
    fi
    log "Port number valid"

    # Generate timestamp-based container name
    local timestamp
    timestamp=$(date +%Y%m%d-%H%M%S)
    CONTAINER_NAME="connector-${timestamp}"
    log "Container name: $CONTAINER_NAME"

    # Test Docker API connectivity
    local response
    response=$(curl -s -o /dev/null -w "%{http_code}" "http://${DOCKER_HOST}/version" 2>/dev/null || echo "000")
    if [ "$response" != "200" ]; then
        error_exit "Cannot connect to Docker API at http://${DOCKER_HOST} (HTTP $response)"
    fi
    log "Docker API accessible"
}

# ============================================
# Container Management Functions
# ============================================

check_container_exists() {
    local name="$1"
    local filters="{\"name\":[\"^/${name}$\"]}"
    local encoded_filters=$(url_encode "$filters")
    
    local response=$(curl -s "http://${DOCKER_HOST}/containers/json?all=true&filters=${encoded_filters}")
    local container_id=$(json_value "$response" "Id")
    
    echo "$container_id"
}

find_container_by_port() {
    local port="$1"
    
    # Get all containers with port information already included and scan once
    curl -s "http://${DOCKER_HOST}/containers/json?all=true" | awk -v port="$port" '
        BEGIN {
            RS="\\},\\{"
        }
        $0 ~ "\"PublicPort\":" port {
            if (match($0, /"Id":"[^"]*"/)) {
                print substr($0, RSTART + 6, RLENGTH - 7)
                exit
            }
        }
    '
}

stop_container() {
    local id="$1"
    log "Stopping container..."
    
    local response=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
        "http://${DOCKER_HOST}/containers/${id}/stop")
    
    if [ "$response" = "204" ] || [ "$response" = "304" ]; then
        log "Container stopped"
        return 0
    else
        log "Failed to stop container (HTTP $response)"
        return 1
    fi
}

remove_container() {
    local id="$1"
    log "Removing container..."
    
    local response=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE \
        "http://${DOCKER_HOST}/containers/${id}")
    
    if [ "$response" = "204" ]; then
        log "Container removed"
        return 0
    else
        log "Failed to remove container (HTTP $response)"
        return 1
    fi
}

handle_existing_container() {
    log_step "2/8" "Checking for existing containers..."
    
    # First check by container name
    local existing_id=$(check_container_exists "$CONTAINER_NAME")
    
    if [ -n "$existing_id" ]; then
        log "Container '$CONTAINER_NAME' already exists (ID: ${existing_id:0:12})"
        
        if [ "$REPLACE_MODE" = true ]; then
            stop_container "$existing_id" || true
            remove_container "$existing_id" || error_exit "Failed to remove existing container"
        else
            error_exit "Container already exists. Use --replace to replace it."
        fi
    else
        # Check if any container is using the same port
        log "Checking for containers using port $PORT..."
        local port_conflict_id=$(find_container_by_port "$PORT")
        
        if [ -n "$port_conflict_id" ]; then
            # Get container name for logging
            local container_info=$(curl -s "http://${DOCKER_HOST}/containers/${port_conflict_id}/json")
            local container_name=$(json_value "$container_info" "Name" | sed 's/^\///')
            
            log "Found container '$container_name' using port $PORT (ID: ${port_conflict_id:0:12})"
            
            if [ "$REPLACE_MODE" = true ]; then
                log "Replacing container using port $PORT..."
                stop_container "$port_conflict_id" || true
                remove_container "$port_conflict_id" || error_exit "Failed to remove container using port $PORT"
            else
                error_exit "Port $PORT is already in use by container '$container_name'. Use different port or --replace to replace existing container (WARNING: This will remove the container '$container_name')"
            fi
        else
            log "No existing container found, port $PORT is available"
        fi
    fi
}

# ============================================
# Archive Creation
# ============================================

create_tar_archive() {
    log_step "3/8" "Creating tar archive..."
    
    # Create temporary directory
    TEMP_DIR=$(mktemp -d)
    TEMP_ARCHIVE="${TEMP_DIR}/config.tar.gz"
    
    # Create mappings subdirectory in temp directory
    mkdir -p "$TEMP_DIR/mappings"
    
    # Copy all config files to mappings subdirectory
    # Use COPYFILE_DISABLE to prevent macOS from creating ._* files during copy
    for file in "${CONFIG_FILES[@]}"; do
        if [[ "$OSTYPE" == "darwin"* ]]; then
            COPYFILE_DISABLE=1 cp "$file" "$TEMP_DIR/mappings/"
        else
            cp "$file" "$TEMP_DIR/mappings/"
        fi
    done
        
    log "Copying ${#CONFIG_FILES[@]} file(s) to archive in mappings/ directory"
    
    # Create tar archive with mappings directory structure
    # Always exclude macOS extended attributes and resource forks
    if [[ "$OSTYPE" == "darwin"* ]]; then
        COPYFILE_DISABLE=1 tar --no-xattrs -czf "$TEMP_ARCHIVE" -C "$TEMP_DIR" mappings 2>/dev/null
    else
        tar -czf "$TEMP_ARCHIVE" -C "$TEMP_DIR" mappings 2>/dev/null
    fi
    
    if [ ! -f "$TEMP_ARCHIVE" ]; then
        error_exit "Failed to create tar archive"
    fi
    
    log "Archive created with mappings/ directory structure"
}

# ============================================
# Image Pull
# ============================================

pull_image() {
    log_step "4/8" "Pulling Docker image..."
    
    log "Pulling image: $IMAGE"
    
    local response=$(curl -s -X POST \
        "http://${DOCKER_HOST}/images/create?fromImage=${IMAGE}" \
        -w "\n%{http_code}")
    
    local http_code=$(echo "$response" | tail -1)
    
    if [ "$http_code" = "200" ]; then
        log "Image pulled successfully"
    else
        log "Image pull completed (may already exist)"
    fi
}

# ============================================
# Container Creation
# ============================================

create_container() {
    log_step "5/8" "Creating container..."
    
    local json_payload=$(cat <<EOF
{
  "Image": "$IMAGE",
  "ExposedPorts": {
    "9443/tcp": {}
  },
  "HostConfig": {
    "PortBindings": {
      "9443/tcp": [
        {
          "HostPort": "$PORT"
        }
      ]
    }
  }
}
EOF
)
    
    # Create container
    local response=$(curl -s -X POST \
        "http://${DOCKER_HOST}/containers/create?name=${CONTAINER_NAME}" \
        -H "Content-Type: application/json" \
        -d "$json_payload")
    
    # Extract container ID
    CONTAINER_ID=$(json_value "$response" "Id")
    
    if [ -z "$CONTAINER_ID" ]; then
        local error_msg=$(json_value "$response" "message")
        [ -z "$error_msg" ] && error_msg="Unknown error"
        error_exit "Failed to create container: $error_msg"
    fi
    
    log "Container created: ${CONTAINER_ID:0:12}"
}

# ============================================
# File Upload
# ============================================

upload_config_files() {
    log_step "6/8" "Uploading config files..."
    
    # Upload to /config path - the tar contains mappings/ directory
    local http_code=$(curl -s -w "%{http_code}" -o /dev/null -X PUT \
        "http://${DOCKER_HOST}/containers/${CONTAINER_ID}/archive?path=/config" \
        -H "Content-Type: application/x-tar" \
        --data-binary @"$TEMP_ARCHIVE")
    
    if [ "$http_code" = "200" ]; then
        log "Files uploaded to /config/mappings/"
    else
        error_exit "Failed to upload files (HTTP $http_code). The /config directory may not exist in the container."
    fi
}

# ============================================
# Container Start
# ============================================

start_container() {
    log_step "7/8" "Starting container..."
    
    local http_code=$(curl -s -w "%{http_code}" -o /dev/null -X POST \
        "http://${DOCKER_HOST}/containers/${CONTAINER_ID}/start")
    
    if [ "$http_code" = "204" ]; then
        log "Container started"
    else
        error_exit "Failed to start container (HTTP $http_code)"
    fi
}

# ============================================
# Container Verification
# ============================================

verify_container_running() {
    log_step "8/8" "Verifying container status..."
    
    sleep 2
    
    local response=$(curl -s "http://${DOCKER_HOST}/containers/${CONTAINER_ID}/json")
    
    # Check if "Running":true exists in the response
    if echo "$response" | grep -q '"Running"[[:space:]]*:[[:space:]]*true'; then
        log "Container is running"
    else
        local status=$(json_value "$response" "Status")
        [ -z "$status" ] && status="unknown"
        error_exit "Container is not running (Status: $status)"
    fi
}

# ============================================
# Main Execution
# ============================================

main() {
    echo "=========================================="
    echo "Container Deployment Script"
    echo "=========================================="
    echo "Docker Host:    $DOCKER_HOST"
    echo "Files:          ${#CONFIG_FILES[@]} file(s)"
    for file in "${CONFIG_FILES[@]}"; do
        echo "                - $(basename "$file")"
    done
    echo "Image:          $IMAGE"
    if [ "$REPLACE_MODE" = true ]; then
        echo "Replace Mode:   Enabled"
    fi
    echo "=========================================="
    
    validate_inputs
    handle_existing_container
    create_tar_archive
    pull_image
    create_container
    upload_config_files
    start_container
    verify_container_running
    
    echo ""
    echo "=========================================="
    echo "Deployment Successful!"
    echo "=========================================="
    echo "Container ID:   ${CONTAINER_ID:0:12}"
    echo "Container Name: $CONTAINER_NAME"
    echo "Status:         Running"
    echo "=========================================="
}

# ============================================
# Script Entry Point
# ============================================

parse_arguments "$@"
main

# Made with Bob