#!/bin/bash

# OpenShift Deployment Script
# Deploys REST API connector(s) to OpenShift using pre-built Docker image

set -e  # Exit on error

# ============================================
# Configuration & Global Variables
# ============================================

SCRIPT_NAME=$(basename "$0")
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROPERTIES_FILE="${SCRIPT_DIR}/deploy-openshift.properties"

# Hardcoded configuration
DOCKER_IMAGE="ghcr.io/marek-zuwala/connectors-forge:1.0.2"
CONFIG_FILES_PATH="sdk-gen/subprojects/connectors_forge_rest/src/main/resources"
DEPLOYMENT_YAML="${SCRIPT_DIR}/deployment.yaml"
SERVICE_YAML="${SCRIPT_DIR}/service.yaml"
CONFIGMAP_NAME="connectors-config"

# Variables from properties file
OPENSHIFT_CLUSTER_URL=""
OPENSHIFT_USERNAME=""
OPENSHIFT_PASSWORD=""
OPENSHIFT_TOKEN=""
OPENSHIFT_PROJECT=""
CREATE_PROJECT_IF_NOT_EXISTS="true"
RECREATE_CONFIGMAP="true"

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

# ============================================
# Usage & Help
# ============================================

usage() {
    cat << EOF
Usage: $SCRIPT_NAME

Deploy REST API connector(s) to OpenShift using pre-built Docker image.

Prerequisites:
  - OpenShift CLI (oc) must be installed
  - Configuration file must exist: ${PROPERTIES_FILE}
  - Connector configuration files must exist in: ${CONFIG_FILES_PATH}

Configuration:
  Copy deploy-openshift.properties.template to deploy-openshift.properties
  and fill in your OpenShift cluster details and credentials.

The script will:
  1. Login to OpenShift cluster
  2. Select or create project/namespace
  3. Create ConfigMap from connector configuration files
  4. Deploy connector using deployment.yaml
  5. Create service using service.yaml
  6. Verify deployment status

EOF
    exit 1
}

# ============================================
# Properties File Loading
# ============================================

load_properties() {
    log_step "1/7" "Loading configuration from ${PROPERTIES_FILE}..."
    
    if [ ! -f "$PROPERTIES_FILE" ]; then
        log_error "Properties file not found: $PROPERTIES_FILE"
        log "Please copy deploy-openshift.properties.template to deploy-openshift.properties"
        log "and fill in your configuration."
        exit 1
    fi
    
    # Load properties file (ignore comments and empty lines)
    while IFS='=' read -r key value; do
        # Skip comments and empty lines
        [[ "$key" =~ ^#.*$ ]] && continue
        [[ -z "$key" ]] && continue
        
        # Trim whitespace
        key=$(echo "$key" | xargs)
        value=$(echo "$value" | xargs)
        
        case "$key" in
            OPENSHIFT_CLUSTER_URL) OPENSHIFT_CLUSTER_URL="$value" ;;
            OPENSHIFT_USERNAME) OPENSHIFT_USERNAME="$value" ;;
            OPENSHIFT_PASSWORD) OPENSHIFT_PASSWORD="$value" ;;
            OPENSHIFT_TOKEN) OPENSHIFT_TOKEN="$value" ;;
            OPENSHIFT_PROJECT) OPENSHIFT_PROJECT="$value" ;;
            CREATE_PROJECT_IF_NOT_EXISTS) CREATE_PROJECT_IF_NOT_EXISTS="$value" ;;
            RECREATE_CONFIGMAP) RECREATE_CONFIGMAP="$value" ;;
        esac
    done < "$PROPERTIES_FILE"
    
    # Validate required properties
    if [ -z "$OPENSHIFT_CLUSTER_URL" ]; then
        log_error "OPENSHIFT_CLUSTER_URL is required in properties file"
        exit 1
    fi
    
    if [ -z "$OPENSHIFT_PROJECT" ]; then
        log_error "OPENSHIFT_PROJECT is required in properties file"
        exit 1
    fi
    
    # Check authentication method
    if [ -z "$OPENSHIFT_TOKEN" ] && ([ -z "$OPENSHIFT_USERNAME" ] || [ -z "$OPENSHIFT_PASSWORD" ]); then
        log_error "Either OPENSHIFT_TOKEN or both OPENSHIFT_USERNAME and OPENSHIFT_PASSWORD are required"
        exit 1
    fi
    
    log "Configuration loaded successfully"
}

# ============================================
# OpenShift CLI Check
# ============================================

check_oc_cli() {
    if ! command -v oc &> /dev/null; then
        log_error "OpenShift CLI (oc) is not installed or not in PATH"
        log "Please install the OpenShift CLI: https://docs.openshift.com/container-platform/latest/cli_reference/openshift_cli/getting-started-cli.html"
        exit 1
    fi
}

# ============================================
# OpenShift Login
# ============================================

login_to_openshift() {
    log_step "2/7" "Logging in to OpenShift cluster..."
    
    if [ -n "$OPENSHIFT_TOKEN" ]; then
        log "Using token authentication"
        if ! oc login "$OPENSHIFT_CLUSTER_URL" --token="$OPENSHIFT_TOKEN" --insecure-skip-tls-verify=true 2>&1; then
            log_error "Failed to login to OpenShift with token"
            exit 1
        fi
    else
        log "Using username/password authentication"
        if ! oc login "$OPENSHIFT_CLUSTER_URL" -u "$OPENSHIFT_USERNAME" -p "$OPENSHIFT_PASSWORD" --insecure-skip-tls-verify=true 2>&1; then
            log_error "Failed to login to OpenShift with username/password"
            exit 1
        fi
    fi
    
    log "Successfully logged in to OpenShift"
}

# ============================================
# Project/Namespace Management
# ============================================

setup_project() {
    log_step "3/7" "Setting up project/namespace..."
    
    # Check if project exists
    if oc get project "$OPENSHIFT_PROJECT" &> /dev/null; then
        log "Project '$OPENSHIFT_PROJECT' exists"
        oc project "$OPENSHIFT_PROJECT" > /dev/null
        log "Switched to project '$OPENSHIFT_PROJECT'"
    else
        if [ "$CREATE_PROJECT_IF_NOT_EXISTS" = "true" ]; then
            log "Project '$OPENSHIFT_PROJECT' does not exist, creating it..."
            if ! oc new-project "$OPENSHIFT_PROJECT" 2>&1; then
                log_error "Failed to create project '$OPENSHIFT_PROJECT'"
                exit 1
            fi
            log "Project '$OPENSHIFT_PROJECT' created successfully"
        else
            log_error "Project '$OPENSHIFT_PROJECT' does not exist and CREATE_PROJECT_IF_NOT_EXISTS is false"
            exit 1
        fi
    fi
}

# ============================================
# ConfigMap Creation
# ============================================

create_configmap() {
    log_step "4/7" "Creating ConfigMap from connector configuration files..."
    
    # Check if config files directory exists
    if [ ! -d "$CONFIG_FILES_PATH" ]; then
        log_error "Configuration files directory not found: $CONFIG_FILES_PATH"
        exit 1
    fi
    
    # Count JSON files
    json_count=$(find "$CONFIG_FILES_PATH" -maxdepth 1 -name "*.json" -type f | wc -l)
    if [ "$json_count" -eq 0 ]; then
        log_error "No JSON configuration files found in: $CONFIG_FILES_PATH"
        exit 1
    fi
    
    log "Found $json_count connector configuration file(s)"
    
    # Check if ConfigMap already exists
    if oc get configmap "$CONFIGMAP_NAME" &> /dev/null; then
        if [ "$RECREATE_CONFIGMAP" = "true" ]; then
            log "ConfigMap '$CONFIGMAP_NAME' already exists, deleting it..."
            oc delete configmap "$CONFIGMAP_NAME"
            log "ConfigMap deleted"
        else
            log_error "ConfigMap '$CONFIGMAP_NAME' already exists and RECREATE_CONFIGMAP is false"
            exit 1
        fi
    fi
    
    # Create ConfigMap
    log "Creating ConfigMap '$CONFIGMAP_NAME'..."
    if ! oc create configmap "$CONFIGMAP_NAME" --from-file="$CONFIG_FILES_PATH" 2>&1; then
        log_error "Failed to create ConfigMap"
        exit 1
    fi
    
    log "ConfigMap '$CONFIGMAP_NAME' created successfully"
}

# ============================================
# Deployment
# ============================================

deploy_connector() {
    log_step "5/7" "Deploying connector..."
    
    # Check if deployment.yaml exists
    if [ ! -f "$DEPLOYMENT_YAML" ]; then
        log_error "Deployment configuration not found: $DEPLOYMENT_YAML"
        exit 1
    fi
    
    # Apply deployment
    log "Applying deployment configuration..."
    if ! oc apply -f "$DEPLOYMENT_YAML" 2>&1; then
        log_error "Failed to apply deployment configuration"
        exit 1
    fi
    
    log "Deployment configuration applied successfully"
}

# ============================================
# Service Creation
# ============================================

create_service() {
    log_step "6/7" "Creating service..."
    
    # Check if service.yaml exists
    if [ ! -f "$SERVICE_YAML" ]; then
        log_error "Service configuration not found: $SERVICE_YAML"
        exit 1
    fi
    
    # Apply service
    log "Applying service configuration..."
    if ! oc apply -f "$SERVICE_YAML" 2>&1; then
        log_error "Failed to apply service configuration"
        exit 1
    fi
    
    log "Service configuration applied successfully"
}

# ============================================
# Verification
# ============================================

verify_deployment() {
    log_step "7/7" "Verifying deployment..."
    
    log "Waiting for pod to be ready (this may take a minute)..."
    sleep 5
    
    # Check pod status
    log "Checking pod status..."
    oc get pods -l app=cf-flight-connector
    
    # Check service
    log ""
    log "Checking service..."
    oc get service cf-flight-connector-service
    
    log ""
    log "Deployment verification complete"
}

# ============================================
# Main Execution
# ============================================

main() {
    echo "=========================================="
    echo "OpenShift Deployment Script"
    echo "=========================================="
    
    # Check for help flag
    if [ "$1" = "-h" ] || [ "$1" = "--help" ]; then
        usage
    fi
    
    check_oc_cli
    load_properties
    login_to_openshift
    setup_project
    create_configmap
    deploy_connector
    create_service
    verify_deployment
    
    echo ""
    echo "=========================================="
    echo "Deployment Complete!"
    echo "=========================================="
    echo ""
    echo "Your REST API connector(s) have been deployed to OpenShift."
    echo ""
    echo "Deployment details:"
    echo "  - Cluster: $OPENSHIFT_CLUSTER_URL"
    echo "  - Project: $OPENSHIFT_PROJECT"
    echo "  - Deployment: cf-flight-connector"
    echo "  - Service: cf-flight-connector-service"
    echo "  - Port: 9443 (gRPC)"
    echo ""
    echo "The connector is accessible within the cluster at:"
    echo "  cf-flight-connector-service.${OPENSHIFT_PROJECT}.svc.cluster.local:9443"
    echo ""
}

main "$@"

# Made with Bob
