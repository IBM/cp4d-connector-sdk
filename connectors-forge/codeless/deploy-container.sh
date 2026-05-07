#!/bin/bash

# Container Deployment Script (SSH-based)
# Deploys a container to a remote Docker server via SSH
# with configuration file upload using tar streaming

set -e  # Exit on error

# ============================================
# Configuration & Global Variables
# ============================================

SCRIPT_NAME=$(basename "$0")
SSH_HOST=""
SSH_USER=""
SSH_PORT="22"
CONFIG_FILES=()
PORT=""
REPLACE_MODE=false
IMAGE="ghcr.io/marek-zuwala/connectors-forge:1.0.1"
CONTAINER_ID=""
CONTAINER_NAME=""
TEMP_DIR=""

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

# Execute command via SSH
ssh_exec() {
    local command="$1"
    ssh -p "$SSH_PORT" -o StrictHostKeyChecking=no \
        -o ConnectTimeout=10 -o BatchMode=yes \
        "${SSH_USER}@${SSH_HOST}" "$command" 2>&1
}

# ============================================
# Usage & Help
# ============================================

usage() {
    cat << EOF
Usage: $SCRIPT_NAME --host HOST --files FILE1 [FILE2 ...] --port PORT [OPTIONS]

Deploy a container to a remote Docker server via SSH.

Required Arguments:
  --host HOST          Remote server hostname or IP (e.g., remote-server.com)
  --ssh-user USER      SSH username (e.g., ubuntu)
  --files FILE1 ...    Space-separated list of files to upload (e.g., api1.json api2.json)
  --port PORT          Port to expose (e.g., 9090)

Optional Arguments:
  --ssh-port PORT      SSH port (default: 22)
  --replace            Stop and remove existing container with same name or using the same port
  -h, --help           Show this help message

Note:
  SSH authentication uses your default SSH keys (~/.ssh/id_rsa, ~/.ssh/id_ed25519, etc.)
  or keys loaded in ssh-agent. Ensure your SSH keys are properly configured before running.

Examples:
  # Basic deployment
  $SCRIPT_NAME --host remote.example.com --ssh-user ubuntu \\
    --files ./config-files/my-api.json --port 9090

  # Multiple files
  $SCRIPT_NAME --host 192.168.1.100 --ssh-user docker \\
    --files api1.json api2.json --port 9090

  # Replace existing container
  $SCRIPT_NAME --host remote.example.com --ssh-user ubuntu \\
    --files my-api.json --port 9090 --replace

EOF
    exit 1
}

# ============================================
# Error Handler
# ============================================

cleanup() {
    if [ -n "$TEMP_DIR" ] && [ -d "$TEMP_DIR" ]; then
        rm -rf "$TEMP_DIR"
    fi
}

trap cleanup EXIT

error_exit() {
    local message="$1"
    log_error "$message"
    
    # If container was created but deployment failed, try to remove it
    if [ -n "$CONTAINER_ID" ]; then
        log "Cleaning up failed deployment..."
        ssh_exec "docker rm -f $CONTAINER_ID" 2>/dev/null || true
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
                SSH_HOST="$2"
                shift 2
                ;;
            --ssh-user)
                SSH_USER="$2"
                shift 2
                ;;
            --ssh-port)
                SSH_PORT="$2"
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
    if [ -z "$SSH_HOST" ]; then
        log_error "Missing required argument: --host"
        usage
    fi

    if [ -z "$SSH_USER" ]; then
        log_error "Missing required argument: --ssh-user"
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
    log_step "1/7" "Validating inputs..."

    # Test SSH connectivity and Docker availability in one go
    log "Testing SSH connectivity and Docker availability..."
    if ! ssh_exec "docker version" >/dev/null 2>&1; then
        error_exit "Cannot connect via SSH or Docker not available on ${SSH_USER}@${SSH_HOST}:${SSH_PORT}"
    fi
    log "SSH connection and Docker verified"

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
}

# ============================================
# Container Management Functions
# ============================================

check_container_exists() {
    local name="$1"
    ssh_exec "docker ps -aq --filter 'name=^${name}$'" | head -1
}

find_container_by_port() {
    local port="$1"
    ssh_exec "docker ps -a --filter 'publish=${port}' --format '{{.ID}}'" | head -1
}

get_container_name() {
    local id="$1"
    ssh_exec "docker inspect $id --format '{{.Name}}'" | sed 's/^\///'
}

stop_and_remove_container() {
    local id="$1"
    log "Stopping and removing container..."
    
    if ssh_exec "docker stop $id && docker rm $id" >/dev/null 2>&1; then
        log "Container removed"
        return 0
    else
        log "Failed to remove container"
        return 1
    fi
}

handle_existing_container() {
    log_step "2/7" "Checking for existing containers..."
    
    # First check by container name
    local existing_id=$(check_container_exists "$CONTAINER_NAME")
    
    if [ -n "$existing_id" ]; then
        log "Container '$CONTAINER_NAME' already exists (ID: ${existing_id:0:12})"
        
        if [ "$REPLACE_MODE" = true ]; then
            stop_and_remove_container "$existing_id" || error_exit "Failed to remove existing container"
        else
            error_exit "Container already exists. Use --replace to replace it."
        fi
    else
        # Check if any container is using the same port
        log "Checking for containers using port $PORT..."
        local port_conflict_id=$(find_container_by_port "$PORT")
        
        if [ -n "$port_conflict_id" ]; then
            # Get container name for logging
            local container_name=$(get_container_name "$port_conflict_id")
            
            log "Found container '$container_name' using port $PORT (ID: ${port_conflict_id:0:12})"
            
            if [ "$REPLACE_MODE" = true ]; then
                log "Replacing container using port $PORT..."
                stop_and_remove_container "$port_conflict_id" || error_exit "Failed to remove container using port $PORT"
            else
                error_exit "Port $PORT is already in use by container '$container_name'. Use different port or --replace to replace existing container (WARNING: This will remove the container '$container_name')"
            fi
        else
            log "No existing container found, port $PORT is available"
        fi
    fi
}

# ============================================
# Container Creation
# ============================================

create_container() {
    log_step "3/7" "Creating container..."
    
    # Create container with port mapping (Docker will pull image if needed)
    log "Creating container from image: $IMAGE"
    local create_output=$(ssh_exec "docker create --name $CONTAINER_NAME -p ${PORT}:9443 $IMAGE")
    
    # Extract only the container ID (last line of output)
    CONTAINER_ID=$(echo "$create_output" | tail -n 1)
    
    if [ -z "$CONTAINER_ID" ]; then
        error_exit "Failed to create container"
    fi
    
    log "Container created: ${CONTAINER_ID:0:12}"
}

# ============================================
# File Upload
# ============================================

upload_config_files() {
    log_step "4/7" "Uploading config files..."
    
    # Create temporary directory for staging files
    TEMP_DIR=$(mktemp -d)
    mkdir -p "$TEMP_DIR/mappings"
    
    # Copy all config files to temp mappings directory
    log "Preparing ${#CONFIG_FILES[@]} file(s) for upload..."
    for file in "${CONFIG_FILES[@]}"; do
        cp "$file" "$TEMP_DIR/mappings/"
    done
    
    # Stream tar directly to docker cp via SSH
    # The tar is created on-the-fly and piped, no archive file is created
    (
        cd "$TEMP_DIR" || exit 1
        
        # Stream tar without extended attributes (critical for macOS -> Linux)
        if [[ "$OSTYPE" == "darwin"* ]]; then
            # macOS: Try modern flags first, fallback to COPYFILE_DISABLE for older versions
            tar --no-mac-metadata --no-xattrs -cf - mappings 2>/dev/null || \
            COPYFILE_DISABLE=1 tar -cf - mappings
        else
            tar -cf - mappings
        fi
    ) | ssh -p "$SSH_PORT" -o StrictHostKeyChecking=no \
        -o ConnectTimeout=10 -o BatchMode=yes \
        "${SSH_USER}@${SSH_HOST}" "docker cp - ${CONTAINER_ID}:/config"
    
    if [ $? -eq 0 ]; then
        log "Files uploaded to /config/mappings/"
    else
        error_exit "Failed to upload files. The /config directory may not exist in the container."
    fi
}

# ============================================
# Container Start
# ============================================

start_container() {
    log_step "5/7" "Starting container..."
    
    if ssh_exec "docker start $CONTAINER_ID" >/dev/null 2>&1; then
        log "Container started"
    else
        error_exit "Failed to start container"
    fi
}

# ============================================
# Container Verification
# ============================================

verify_container_running() {
    log_step "6/7" "Verifying container status..."
    
    sleep 2
    
    local running=$(ssh_exec "docker inspect $CONTAINER_ID --format '{{.State.Running}}'")
    
    if [ "$running" = "true" ]; then
        log "Container is running"
    else
        local status=$(ssh_exec "docker inspect $CONTAINER_ID --format '{{.State.Status}}'")
        [ -z "$status" ] && status="unknown"
        error_exit "Container is not running (Status: $status)"
    fi
}

# ============================================
# Main Execution
# ============================================

main() {
    echo "=========================================="
    echo "Container Deployment Script (SSH)"
    echo "=========================================="
    echo "SSH Host:       ${SSH_USER}@${SSH_HOST}:${SSH_PORT}"
    echo "Files:          ${#CONFIG_FILES[@]} file(s)"
    for file in "${CONFIG_FILES[@]}"; do
        echo "                - $(basename "$file")"
    done
    echo "Image:          $IMAGE"
    echo "Port:           $PORT"
    if [ "$REPLACE_MODE" = true ]; then
        echo "Replace Mode:   Enabled"
    fi
    echo "=========================================="
    
    validate_inputs
    handle_existing_container
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
    echo "Port Mapping:   ${PORT}:9443"
    echo "=========================================="
}

# ============================================
# Script Entry Point
# ============================================

parse_arguments "$@"
main

# Made with Bob