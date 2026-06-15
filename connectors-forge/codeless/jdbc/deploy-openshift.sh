#!/bin/bash

set -e

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Create temporary directory for generated YAML files
TEMP_DIR=$(mktemp -d)

# Cleanup function
cleanup() {
    if [ -n "$TEMP_DIR" ] && [ -d "$TEMP_DIR" ]; then
        rm -rf "$TEMP_DIR"
    fi
}

# Set trap to cleanup on exit
trap cleanup EXIT

# Default env file location
ENV_FILE="${SCRIPT_DIR}/deploy.env"

# ConfigMap configuration
CONNECTOR_PROPERTIES_FILE="${SCRIPT_DIR}/connector-config.env"
CONFIGMAP_NAME="jdbc-connector-config"

# Function to print colored messages
print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_step() {
    echo -e "\n${GREEN}==>${NC} $1"
}

# Function to check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Function to load environment file
load_env() {
    print_step "[1/8] Loading configuration"
    
    if [ ! -f "$ENV_FILE" ]; then
        print_error "Environment file not found: $ENV_FILE"
        print_info "Please copy deploy.env.template to deploy.env and configure it"
        exit 1
    fi

    print_info "Loading configuration from: $ENV_FILE"
    # shellcheck disable=SC1090
    source "$ENV_FILE"

    # Validate required variables
    local required_vars=(
        "OC_NAMESPACE"
        "PREBUILT_IMAGE"
        "IMAGE_NAME"
    )

    for var in "${required_vars[@]}"; do
        if [ -z "${!var}" ]; then
            print_error "Required variable $var is not set in $ENV_FILE"
            exit 1
        fi
    done
    
    # Set default for RECREATE_CONFIGMAP if not set
    RECREATE_CONFIGMAP="${RECREATE_CONFIGMAP:-true}"
    
    # Set default for CREATE_ROUTE if not set
    CREATE_ROUTE="${CREATE_ROUTE:-true}"
}

# Function to login to OpenShift cluster
login_to_openshift() {
    print_step "[2/8] Verifying OpenShift authentication"
    
    # Skip login if SKIP_OC_LOGIN is set to true
    if [ "${SKIP_OC_LOGIN}" = "true" ]; then
        print_info "Skipping OpenShift login (SKIP_OC_LOGIN=true)"
        return
    fi

    # Check if already logged in
    if oc whoami &>/dev/null; then
        local current_user=$(oc whoami)
        local current_server=$(oc whoami --show-server)
        
        # If OPENSHIFT_CLUSTER_URL is set, verify we're connected to the right cluster
        if [ -n "$OPENSHIFT_CLUSTER_URL" ]; then
            if [[ "$current_server" == *"$OPENSHIFT_CLUSTER_URL"* ]]; then
                print_info "Already logged in to correct cluster"
                print_info "User: $current_user"
                print_info "Server: $current_server"
                return
            else
                print_warn "Currently logged in to different cluster: $current_server"
                print_info "Will login to: $OPENSHIFT_CLUSTER_URL"
            fi
        else
            print_info "Already logged in to OpenShift"
            print_info "User: $current_user"
            print_info "Server: $current_server"
            return
        fi
    fi

    # Proceed with login if OPENSHIFT_CLUSTER_URL is provided
    if [ -z "$OPENSHIFT_CLUSTER_URL" ]; then
        print_error "Not logged in to OpenShift and OPENSHIFT_CLUSTER_URL is not set"
        print_info "Either login manually with 'oc login' or set OPENSHIFT_CLUSTER_URL in $ENV_FILE"
        exit 1
    fi

    print_info "Logging in to OpenShift cluster: $OPENSHIFT_CLUSTER_URL"

    # Check authentication method
    if [ -n "$OPENSHIFT_TOKEN" ]; then
        print_info "Using token authentication"
        if ! oc login "$OPENSHIFT_CLUSTER_URL" --token="$OPENSHIFT_TOKEN" --insecure-skip-tls-verify=true 2>&1; then
            print_error "Failed to login to OpenShift with token"
            exit 1
        fi
    elif [ -n "$OPENSHIFT_USERNAME" ] && [ -n "$OPENSHIFT_PASSWORD" ]; then
        print_info "Using username/password authentication"
        if ! oc login "$OPENSHIFT_CLUSTER_URL" -u "$OPENSHIFT_USERNAME" -p "$OPENSHIFT_PASSWORD" --insecure-skip-tls-verify=true 2>&1; then
            print_error "Failed to login to OpenShift with username/password"
            exit 1
        fi
    else
        print_error "No authentication credentials provided"
        print_info "Set either OPENSHIFT_TOKEN or both OPENSHIFT_USERNAME and OPENSHIFT_PASSWORD in $ENV_FILE"
        exit 1
    fi

    print_info "Successfully logged in to OpenShift"
    local current_user=$(oc whoami)
    print_info "Logged in as: $current_user"
}


# Function to generate YAML from template
generate_yaml_from_template() {
    local template_file="$1"
    local output_file="$2"

    if [ ! -f "$template_file" ]; then
        print_warn "Template not found: $template_file"
        return 1
    fi

    print_info "Generating $output_file from template"

    # Read template and replace placeholders
    sed -e "s|{{OC_NAMESPACE}}|${OC_NAMESPACE}|g" \
        -e "s|{{PREBUILT_IMAGE}}|${PREBUILT_IMAGE}|g" \
        -e "s|{{IMAGE_NAME}}|${IMAGE_NAME}|g" \
        "$template_file" > "$output_file"

    return 0
}

# Function to generate deployment YAMLs
generate_deployment_yamls() {
    print_step "[3/8] Generating deployment YAML files from templates"

    local generated_files=()

    # Generate pvc.yaml
    if [ -n "$PVC_YAML" ] && [ "${CREATE_PVC}" = "true" ]; then
        local template="${SCRIPT_DIR}/${PVC_YAML}.template"
        local output="${TEMP_DIR}/${PVC_YAML}"
        if generate_yaml_from_template "$template" "$output"; then
            generated_files+=("$PVC_YAML")
        fi
    fi

    # Generate deployment.yaml
    if [ -n "$DEPLOYMENT_YAML" ]; then
        local template="${SCRIPT_DIR}/${DEPLOYMENT_YAML}.template"
        local output="${TEMP_DIR}/${DEPLOYMENT_YAML}"
        if generate_yaml_from_template "$template" "$output"; then
            generated_files+=("$DEPLOYMENT_YAML")
        fi
    fi

    # Generate service.yaml
    if [ -n "$SERVICE_YAML" ]; then
        local template="${SCRIPT_DIR}/${SERVICE_YAML}.template"
        local output="${TEMP_DIR}/${SERVICE_YAML}"
        if generate_yaml_from_template "$template" "$output"; then
            generated_files+=("$SERVICE_YAML")
        fi
    fi

    # Generate route.yaml
    if [ -n "$ROUTE_YAML" ] && [ "${CREATE_ROUTE}" = "true" ]; then
        local template="${SCRIPT_DIR}/${ROUTE_YAML}.template"
        local output="${TEMP_DIR}/${ROUTE_YAML}"
        if generate_yaml_from_template "$template" "$output"; then
            generated_files+=("$ROUTE_YAML")
        fi
    fi

    # Generate project.yaml
    if [ -n "$PROJECT_YAML" ] && [ "${CREATE_NAMESPACE}" = "true" ]; then
        local template="${SCRIPT_DIR}/${PROJECT_YAML}.template"
        local output="${TEMP_DIR}/${PROJECT_YAML}"
        if generate_yaml_from_template "$template" "$output"; then
            generated_files+=("$PROJECT_YAML")
        fi
    fi

    if [ ${#generated_files[@]} -gt 0 ]; then
        print_info "Generated files: ${generated_files[*]}"
    else
        print_warn "No YAML files were generated"
    fi
}

# Function to check prerequisites
check_prerequisites() {
    if ! command_exists oc; then
        print_error "oc CLI not found. Please install OpenShift CLI"
        exit 1
    fi
}

# Function to create namespace
create_namespace() {
    if [ "${CREATE_NAMESPACE}" != "true" ]; then
        print_info "Skipping namespace creation (CREATE_NAMESPACE=false)"
        return
    fi

    print_step "[4/8] Creating namespace: $OC_NAMESPACE"

    if oc get project "$OC_NAMESPACE" &>/dev/null; then
        print_warn "Namespace $OC_NAMESPACE already exists"
    else
        if [ -n "$PROJECT_YAML" ] && [ -f "${TEMP_DIR}/${PROJECT_YAML}" ]; then
            print_info "Creating namespace from YAML: $PROJECT_YAML"
            oc apply -f "${TEMP_DIR}/${PROJECT_YAML}"
        else
            print_info "Creating namespace using oc new-project"
            oc new-project "$OC_NAMESPACE"
        fi
    fi

    # Switch to the namespace
    oc project "$OC_NAMESPACE"
    print_info "Switched to namespace: $OC_NAMESPACE"
}

# Function to create ConfigMap
create_configmap() {
    print_step "[5/8] Creating ConfigMap from connector properties"
    
    # Check if properties file exists
    if [ ! -f "$CONNECTOR_PROPERTIES_FILE" ]; then
        print_error "Connector properties file not found: $CONNECTOR_PROPERTIES_FILE"
        exit 1
    fi
    
    print_info "Found connector properties file: $CONNECTOR_PROPERTIES_FILE"
    
    # Check if ConfigMap already exists
    if oc get configmap "$CONFIGMAP_NAME" -n "$OC_NAMESPACE" &> /dev/null; then
        if [ "$RECREATE_CONFIGMAP" = "true" ]; then
            print_info "ConfigMap '$CONFIGMAP_NAME' already exists, deleting it..."
            oc delete configmap "$CONFIGMAP_NAME" -n "$OC_NAMESPACE"
            print_info "ConfigMap deleted"
        else
            print_error "ConfigMap '$CONFIGMAP_NAME' already exists and RECREATE_CONFIGMAP is false"
            exit 1
        fi
    fi
    
    # Create ConfigMap from the properties file
    print_info "Creating ConfigMap '$CONFIGMAP_NAME'..."
    if ! oc create configmap "$CONFIGMAP_NAME" \
        --from-env-file="$CONNECTOR_PROPERTIES_FILE" \
        -n "$OC_NAMESPACE" 2>&1; then
        print_error "Failed to create ConfigMap"
        exit 1
    fi
    
    print_info "ConfigMap '$CONFIGMAP_NAME' created successfully"
}

# Function to create PVC
create_pvc() {
    if [ "${CREATE_PVC}" != "true" ]; then
        print_info "Skipping PVC creation (CREATE_PVC=false)"
        return
    fi

    print_step "[6/8] Creating PersistentVolumeClaim for drivers"

    local pvc_name="${IMAGE_NAME}-drivers-pvc"

    if oc get pvc "$pvc_name" -n "$OC_NAMESPACE" &>/dev/null; then
        print_warn "PVC $pvc_name already exists"
    else
        if [ -n "$PVC_YAML" ] && [ -f "${TEMP_DIR}/${PVC_YAML}" ]; then
            print_info "Creating PVC from YAML: $PVC_YAML"
            if ! oc apply -f "${TEMP_DIR}/${PVC_YAML}"; then
                print_error "Failed to create PVC"
                exit 1
            fi
            print_info "PVC created successfully: $pvc_name"

            # Wait for PVC to be bound
            print_info "Waiting for PVC to be bound..."
            local timeout=60
            local elapsed=0
            while [ $elapsed -lt $timeout ]; do
                local status=$(oc get pvc "$pvc_name" -n "$OC_NAMESPACE" -o jsonpath='{.status.phase}' 2>/dev/null)
                if [ "$status" = "Bound" ]; then
                    print_info "PVC is now bound"
                    break
                fi
                sleep 2
                elapsed=$((elapsed + 2))
            done

            if [ $elapsed -ge $timeout ]; then
                print_warn "PVC binding timeout - continuing anyway"
            fi
        else
            print_error "PVC template not found: ${SCRIPT_DIR}/${PVC_YAML}.template"
            exit 1
        fi
    fi
}

# Function to deploy resources
deploy_resources() {
    print_step "[7/8] Deploying resources to OpenShift"

    local yaml_files=()

    # Collect YAML files to deploy (PVC already created separately)
    [ -n "$DEPLOYMENT_YAML" ] && [ -f "${TEMP_DIR}/${DEPLOYMENT_YAML}" ] && yaml_files+=("$DEPLOYMENT_YAML")
    [ -n "$SERVICE_YAML" ] && [ -f "${TEMP_DIR}/${SERVICE_YAML}" ] && yaml_files+=("$SERVICE_YAML")
    [ -n "$ROUTE_YAML" ] && [ -f "${TEMP_DIR}/${ROUTE_YAML}" ] && yaml_files+=("$ROUTE_YAML")

    if [ ${#yaml_files[@]} -eq 0 ]; then
        print_warn "No deployment YAML files found"
        return
    fi

    for yaml_file in "${yaml_files[@]}"; do
        print_info "Applying: $yaml_file"
        if ! oc apply -f "${TEMP_DIR}/${yaml_file}"; then
            print_error "Failed to apply $yaml_file"
            exit 1
        fi
    done

    print_info "All resources deployed successfully"
}

# Helper function to get pod name
get_pod_name() {
    oc get pods -n "$OC_NAMESPACE" -l "app=${IMAGE_NAME}" -o jsonpath='{.items[0].metadata.name}' 2>/dev/null
}

# Function to wait for pod to be ready
wait_for_pod_ready() {
    print_info "Waiting for pod to be ready..."

    local timeout=300
    local elapsed=0
    local pod_name=""

    while [ $elapsed -lt $timeout ]; do
        pod_name=$(get_pod_name)

        if [ -n "$pod_name" ]; then
            local pod_status=$(oc get pod "$pod_name" -n "$OC_NAMESPACE" -o jsonpath='{.status.phase}' 2>/dev/null)
            local ready=$(oc get pod "$pod_name" -n "$OC_NAMESPACE" -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}' 2>/dev/null)

            if [ "$pod_status" = "Running" ] && [ "$ready" = "True" ]; then
                print_info "Pod is ready: $pod_name"
                return 0
            fi

            print_info "Pod status: $pod_status (waiting for Ready...)"
        else
            print_info "Waiting for pod to be created..."
        fi

        sleep 5
        elapsed=$((elapsed + 5))
    done

    print_error "Timeout waiting for pod to be ready"
    return 1
}

# Function to copy driver files to pod
copy_drivers_to_pod() {
    local driver_dir="${SCRIPT_DIR}/driver"

    # Check if driver directory exists and has files
    if [ ! -d "$driver_dir" ]; then
        print_info "No driver directory found at: $driver_dir"
        print_info "Skipping driver copy"
        return 0
    fi

    # Check if directory has any files
    if [ -z "$(ls -A "$driver_dir" 2>/dev/null)" ]; then
        print_info "Driver directory is empty: $driver_dir"
        print_info "Skipping driver copy"
        return 0
    fi

    print_info "Copying driver files to pod..."

    # Wait for pod to be ready
    if ! wait_for_pod_ready; then
        print_error "Cannot copy drivers - pod not ready"
        return 1
    fi

    # Get pod name
    local pod_name=$(get_pod_name)

    # Copy driver files
    print_info "Copying files from: $driver_dir"
    print_info "Destination: $pod_name:/drivers/"

    if oc cp "$driver_dir/." "$OC_NAMESPACE/$pod_name:/drivers/" 2>&1; then
        print_info "Successfully copied driver files"

        # Verify copied files
        print_info "Verifying copied files:"
        oc exec "$pod_name" -n "$OC_NAMESPACE" -- ls -lh /drivers/

        return 0
    else
        print_error "Failed to copy driver files"
        return 1
    fi
}

# Function to verify deployment
verify_deployment() {
    print_step "[8/8] Verifying deployment"

    # Wait for deployment to be ready
    print_info "Waiting for deployment to be ready..."
    sleep 3

    # Check deployment
    if oc get deployment "$IMAGE_NAME" -n "$OC_NAMESPACE" &>/dev/null; then
        print_info "Deployment status:"
        oc get deployment "$IMAGE_NAME" -n "$OC_NAMESPACE"
    fi

    # Check pods
    print_info "Pod status:"
    oc get pods -l "app=${IMAGE_NAME}" -n "$OC_NAMESPACE"

    # Check service
    if oc get service "$IMAGE_NAME" -n "$OC_NAMESPACE" &>/dev/null; then
        print_info "Service status:"
        oc get service "$IMAGE_NAME" -n "$OC_NAMESPACE"
    fi
    
    # Check route
    if oc get route "$IMAGE_NAME" -n "$OC_NAMESPACE" &>/dev/null; then
        print_info "Route status:"
        oc get route "$IMAGE_NAME" -n "$OC_NAMESPACE"
    fi
}

# Function to show usage
usage() {
    cat << EOF
Usage: $0 [OPTIONS]

Deploy a pre-built connector image to OpenShift cluster.

OPTIONS:
    -e, --env-file FILE     Path to environment file (default: deploy.env)
    -h, --help              Show this help message
    --skip-namespace        Skip namespace creation
    --verify-only           Only verify existing deployment

EXAMPLES:
    # Deploy using default deploy.env
    $0

    # Deploy using custom env file
    $0 --env-file my-config.env

    # Deploy without creating namespace
    $0 --skip-namespace

    # Verify existing deployment
    $0 --verify-only

PREREQUISITES:
    - OpenShift CLI (oc) installed
    - Logged in to OpenShift cluster (oc login)
    - Environment file configured (copy from deploy.env.template)
    - Pre-built connector image available

NOTE:
    - Uses pre-built image specified in PREBUILT_IMAGE variable
    - No local image building or pushing required

EOF
}

# Parse command line arguments
parse_arguments() {
    local verify_only_var=false

    while [[ $# -gt 0 ]]; do
        case $1 in
            -e|--env-file)
                ENV_FILE="$2"
                shift 2
                ;;
            --skip-namespace)
                CREATE_NAMESPACE=false
                shift
                ;;
            --verify-only)
                verify_only_var=true
                shift
                ;;
            -h|--help)
                usage
                exit 0
                ;;
            *)
                print_error "Unknown option: $1"
                usage
                exit 1
                ;;
        esac
    done

    echo "$verify_only_var"
}

# Execute deployment orchestration
run_deployment() {
    print_info "OpenShift Image Deployment Script"
    print_info "=================================="

    # Check prerequisites
    check_prerequisites
    
    # Load configuration
    load_env

    # Login to OpenShift (if credentials provided)
    login_to_openshift

    # Generate YAML files from templates
    generate_deployment_yamls

    # Execute deployment steps
    create_namespace
    create_configmap
    create_pvc
    deploy_resources
    copy_drivers_to_pod
    verify_deployment
}

# Print deployment summary
print_deployment_summary() {
    echo ""
    echo "=========================================="
    echo "Deployment Complete!"
    echo "=========================================="
    echo ""
    echo "Your connector has been deployed to OpenShift."
    echo ""
    echo "Deployment details:"
    echo "  - Cluster: $(oc whoami --show-server 2>/dev/null || echo 'N/A')"
    echo "  - Project: $OC_NAMESPACE"
    echo "  - Deployment: $IMAGE_NAME"
    echo "  - Service: $IMAGE_NAME"
    echo "  - Image: ${PREBUILT_IMAGE}"
    echo ""
    echo "The connector is accessible within the cluster at:"
    echo "  ${IMAGE_NAME}.${OC_NAMESPACE}.svc.cluster.local:9443"
    echo ""
    
    # Display route information if available
    local route_host=$(oc get route "$IMAGE_NAME" -n "$OC_NAMESPACE" -o jsonpath='{.spec.host}' 2>/dev/null)
    if [ -n "$route_host" ]; then
        echo "Flight access via route:"
        echo "  grpc+tls://$route_host:443"
        echo ""
    fi
    echo "To monitor pod status:"
    echo "  oc get pods -n $OC_NAMESPACE -l app=${IMAGE_NAME}"
    echo ""
}

# Main function
main() {
    local verify_only
    verify_only=$(parse_arguments "$@")

    if [ "$verify_only" = true ]; then
        check_prerequisites
        load_env
        login_to_openshift
        generate_deployment_yamls
        verify_deployment
        exit 0
    fi

    run_deployment
    print_deployment_summary
}

# Run main function
main "$@"