#!/bin/bash

set -eo pipefail

# Container Deployment Script (SSH-based) for JDBC connector
# Deploys a JDBC connector container to a remote Docker server via SSH,
# uploads connector configuration as an env file, and copies JDBC drivers.

SCRIPT_NAME=$(basename "$0")
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

SSH_HOST=""
SSH_USER=""
SSH_PORT="22"
PORT=""
REPLACE_MODE=false
ENV_FILE="${SCRIPT_DIR}/connector-config.env"
DRIVER_DIR="${SCRIPT_DIR}/driver"
IMAGE="ghcr.io/thomasgloria/wdp-connect-sdk-gen-jdbc-connectors-forge:latest"
CONTAINER_ID=""
CONTAINER_NAME=""
TEMP_DIR=""
REMOTE_DRIVER_DIR="/drivers"

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

ssh_exec() {
    local command="$1"
    ssh -p "$SSH_PORT" -o StrictHostKeyChecking=no \
        -o LogLevel=ERROR -o ConnectTimeout=10 -o BatchMode=yes \
        "${SSH_USER}@${SSH_HOST}" "$command"
}

# Like ssh_exec but reads stdin (for piping data to remote commands)
ssh_exec_pipe() {
    local command="$1"
    ssh -p "$SSH_PORT" -o StrictHostKeyChecking=no \
        -o LogLevel=ERROR -o ConnectTimeout=10 -o BatchMode=yes \
        "${SSH_USER}@${SSH_HOST}" "$command"
}

usage() {
    cat << EOF
Usage: $SCRIPT_NAME --host HOST --ssh-user USER --port PORT [OPTIONS]

Deploy a JDBC connector container to a remote Docker server via SSH.

Required Arguments:
  --host HOST              Remote server hostname or IP
  --ssh-user USER          SSH username
  --port PORT              Port to expose locally on remote Docker host

Optional Arguments:
  --ssh-port PORT          SSH port (default: 22)
  --env-file FILE          Connector env file to pass to container
                           (default: connectors-forge/codeless/jdbc/connector-config.env)
  --driver-dir DIR         Local directory containing JDBC driver files
                           (default: connectors-forge/codeless/jdbc/driver)
  --image IMAGE            Container image to deploy
                           (default: $IMAGE)
  --replace                Stop and remove existing container using the same port
  -h, --help               Show this help message

Notes:
  - SSH authentication uses your default SSH keys or ssh-agent.
  - The env file is passed with --env-file to docker create.
  - Files from the driver directory are copied into ${REMOTE_DRIVER_DIR}/ in the container.

Examples:
  $SCRIPT_NAME --host remote.example.com --ssh-user ubuntu --port 9443

  $SCRIPT_NAME --host remote.example.com --ssh-user ubuntu --port 9443 \
    --env-file ./connector-config.env --driver-dir ./driver --replace
EOF
    exit 1
}

cleanup() {
    if [ -n "$TEMP_DIR" ] && [ -d "$TEMP_DIR" ]; then
        rm -rf "$TEMP_DIR"
    fi
}

trap cleanup EXIT

error_exit() {
    local message="$1"
    log_error "$message"

    if [ -n "$CONTAINER_ID" ]; then
        log "Cleaning up failed deployment..."
        ssh_exec "docker rm -f $CONTAINER_ID" 2>/dev/null || true
    fi

    exit 1
}

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
            --port)
                PORT="$2"
                shift 2
                ;;
            --env-file)
                ENV_FILE="$2"
                shift 2
                ;;
            --driver-dir)
                DRIVER_DIR="$2"
                shift 2
                ;;
            --image)
                IMAGE="$2"
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

    if [ -z "$SSH_HOST" ]; then log_error "Missing required argument: --host"; usage; fi
    if [ -z "$SSH_USER" ]; then log_error "Missing required argument: --ssh-user"; usage; fi
    if [ -z "$PORT" ]; then log_error "Missing required argument: --port"; usage; fi

    return 0
}

validate_inputs() {
    log_step "1/7" "Validating inputs..."

    log "Testing SSH connectivity and Docker availability..."
    if ! ssh_exec "docker version" >/dev/null 2>&1; then
        error_exit "Cannot connect via SSH or Docker not available on ${SSH_USER}@${SSH_HOST}:${SSH_PORT}"
    fi
    log "SSH connection and Docker verified"

    if [ ! -f "$ENV_FILE" ]; then
        error_exit "Connector env file not found: $ENV_FILE"
    fi
    log "Connector env file found: $ENV_FILE"

    if [ -d "$DRIVER_DIR" ]; then
        if [ -z "$(find "$DRIVER_DIR" -type f -maxdepth 1 2>/dev/null)" ]; then
            log "WARNING: Driver directory is empty: $DRIVER_DIR"
        else
            log "Driver directory found: $DRIVER_DIR"
        fi
    else
        log "WARNING: Driver directory not found: $DRIVER_DIR"
    fi

    if ! [[ "$PORT" =~ ^[0-9]+$ ]] || [ "$PORT" -lt 1 ] || [ "$PORT" -gt 65535 ]; then
        error_exit "Invalid port number: $PORT (must be 1-65535)"
    fi
    log "Port number valid"

    local timestamp
    timestamp=$(date +%Y%m%d-%H%M%S)
    CONTAINER_NAME="jdbc-connector-${timestamp}"
    log "Container name: $CONTAINER_NAME"
}

find_container_by_port() {
    local port="$1"
    ssh_exec "docker ps -a --filter 'publish=${port}' --format '{{.ID}}'" | head -1
}

get_container_name() {
    local id="$1"
    ssh_exec "docker inspect $id --format '{{.Name}}'" | sed 's|^/||'
}

stop_and_remove_container() {
    local id="$1"
    log "Stopping and removing container..."

    if ssh_exec "docker stop $id && docker rm $id" >/dev/null 2>&1; then
        log "Container removed"
        return 0
    fi

    log "Failed to remove container"
    return 1
}

handle_existing_container() {
    log_step "2/7" "Checking for existing containers..."

    log "Checking for containers using port $PORT..."
    local port_conflict_id
    port_conflict_id=$(find_container_by_port "$PORT")

    if [ -n "$port_conflict_id" ]; then
        local existing_name
        existing_name=$(get_container_name "$port_conflict_id")
        log "Found container '$existing_name' using port $PORT (ID: ${port_conflict_id:0:12})"

        if [ "$REPLACE_MODE" = true ]; then
            stop_and_remove_container "$port_conflict_id" || error_exit "Failed to remove container using port $PORT"
        else
            error_exit "Port $PORT is already in use by container '$existing_name'. Use --replace to replace it."
        fi
    else
        log "No existing container found, port $PORT is available"
    fi
}

create_container() {
    log_step "3/7" "Creating container..."

    log "Creating container from image: $IMAGE"
    log "Uploading env file to remote host..."

    local remote_env_file="/tmp/${CONTAINER_NAME}.env"
    if ! ssh_exec_pipe "cat > '$remote_env_file'" < "$ENV_FILE"; then
        error_exit "Failed to upload env file"
    fi

    CONTAINER_ID=$(ssh_exec "docker create --name $CONTAINER_NAME --env-file '$remote_env_file' -p ${PORT}:9443 $IMAGE")
    ssh_exec "rm -f '$remote_env_file'" 2>/dev/null || true

    if [ -z "$CONTAINER_ID" ]; then
        error_exit "Failed to create container"
    fi

    log "Container created: ${CONTAINER_ID:0:12}"
}

copy_driver_files() {
    log_step "4/7" "Copying JDBC driver files..."

    if [ ! -d "$DRIVER_DIR" ]; then
        log "No driver directory found, skipping driver copy"
        return
    fi

    if [ -z "$(find "$DRIVER_DIR" -type f -maxdepth 1 2>/dev/null)" ]; then
        log "Driver directory is empty, skipping driver copy"
        return
    fi

    TEMP_DIR=$(mktemp -d)
    mkdir -p "$TEMP_DIR/drivers"

    log "Preparing driver files for upload..."
    find "$DRIVER_DIR" -maxdepth 1 -type f -exec cp {} "$TEMP_DIR/drivers/" \;

    if (
        cd "$TEMP_DIR" || exit 1

        if [[ "$OSTYPE" == "darwin"* ]]; then
            tar --no-mac-metadata --no-xattrs -cf - drivers 2>/dev/null || \
            COPYFILE_DISABLE=1 tar -cf - drivers
        else
            tar -cf - drivers
        fi
    ) | ssh_exec_pipe "docker cp - ${CONTAINER_ID}:/"; then
        log "Driver files uploaded to ${REMOTE_DRIVER_DIR}/"
    else
        error_exit "Failed to upload driver files"
    fi
}

start_container() {
    log_step "5/7" "Starting container..."

    if ssh_exec "docker start $CONTAINER_ID" >/dev/null 2>&1; then
        log "Container started"
    else
        error_exit "Failed to start container"
    fi
}

verify_container_running() {
    log_step "6/7" "Verifying container status..."

    sleep 2

    local running
    running=$(ssh_exec "docker inspect $CONTAINER_ID --format '{{.State.Running}}'")

    if [ "$running" = "true" ]; then
        log "Container is running"
        return
    fi

    local status
    status=$(ssh_exec "docker inspect $CONTAINER_ID --format '{{.State.Status}}'")
    [ -z "$status" ] && status="unknown"
    error_exit "Container is not running (Status: $status)"
}

show_container_details() {
    log_step "7/7" "Deployment summary"
    log "Container ID:   ${CONTAINER_ID:0:12}"
    log "Container Name: $CONTAINER_NAME"
    log "Status:         Running"
    log "Port Mapping:   ${PORT}:9443"
    log "Image:          $IMAGE"
    log "Env File:       $ENV_FILE"
    log "Driver Dir:     $DRIVER_DIR"
}

main() {
    validate_inputs
    handle_existing_container
    create_container
    copy_driver_files
    start_container
    verify_container_running
    show_container_details
}

parse_arguments "$@"

echo "=========================================="
echo "JDBC Container Deployment Script (SSH)"
echo "=========================================="
echo "SSH Host:       ${SSH_USER}@${SSH_HOST}:${SSH_PORT}"
echo "Image:          $IMAGE"
echo "Port:           $PORT"
echo "Env File:       $ENV_FILE"
echo "Driver Dir:     $DRIVER_DIR"
if [ "$REPLACE_MODE" = true ]; then
    echo "Replace Mode:   Enabled"
fi
echo "=========================================="

main
