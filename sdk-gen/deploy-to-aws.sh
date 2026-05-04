#!/bin/bash

################################################################################
# AWS ECS Deployment Script for JDBC Custom Connector
# 
# This script automates the deployment of a JDBC connector to AWS ECS Fargate
# based on the guide-for-aws.md documentation.
#
# Usage: ./deploy-to-aws.sh [path-to-properties-file]
# Default properties file: ./aws-deployment.properties
################################################################################

set -e  # Exit on error
set -o pipefail  # Exit on pipe failure

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Default properties file
PROPERTIES_FILE="${1:-${SCRIPT_DIR}/aws-deployment.properties}"

################################################################################
# Helper Functions
################################################################################

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_step() {
    echo -e "\n${GREEN}==>${NC} ${BLUE}$1${NC}\n"
}

# Load properties file
load_properties() {
    if [[ ! -f "$PROPERTIES_FILE" ]]; then
        log_error "Properties file not found: $PROPERTIES_FILE"
        log_info "Please create a properties file or specify the path as an argument."
        exit 1
    fi
    
    log_info "Loading configuration from: $PROPERTIES_FILE"
    
    # Source the properties file, ignoring comments and empty lines
    while IFS='=' read -r key value; do
        # Skip comments and empty lines
        [[ "$key" =~ ^#.*$ ]] && continue
        [[ -z "$key" ]] && continue
        
        # Remove leading/trailing whitespace
        key=$(echo "$key" | xargs)
        value=$(echo "$value" | xargs)
        
        # Export the variable
        export "$key=$value"
    done < "$PROPERTIES_FILE"
    
    log_success "Configuration loaded successfully"
}

# Validate required parameters
validate_parameters() {
    log_step "Validating configuration parameters"
    
    local missing_params=()
    
    # Check required parameters
    [[ -z "$AWS_ACCOUNT_ID" ]] && missing_params+=("AWS_ACCOUNT_ID")
    [[ -z "$AWS_REGION" ]] && missing_params+=("AWS_REGION")
    [[ -z "$VPC_ID" ]] && missing_params+=("VPC_ID")
    [[ -z "$SUBNET_ID" ]] && missing_params+=("SUBNET_ID")
    [[ -z "$ECR_IMAGE_URI" ]] && missing_params+=("ECR_IMAGE_URI")
    [[ -z "$ECS_CLUSTER" ]] && missing_params+=("ECS_CLUSTER")
    
    if [[ ${#missing_params[@]} -gt 0 ]]; then
        log_error "Missing required parameters:"
        for param in "${missing_params[@]}"; do
            echo "  - $param"
        done
        exit 1
    fi
    
    log_success "All required parameters are present"
}

# Check prerequisites
check_prerequisites() {
    log_step "Checking prerequisites"
    
    # Check if AWS CLI is installed
    if ! command -v aws &> /dev/null; then
        log_error "AWS CLI is not installed. Please install it first."
        exit 1
    fi
    log_success "AWS CLI is installed"
    
    # Check AWS credentials
    if ! aws sts get-caller-identity --region "$AWS_REGION" &> /dev/null; then
        log_error "AWS credentials are not configured or invalid."
        exit 1
    fi
    log_success "AWS credentials are valid"
    
    # Check if Docker is installed
    if ! command -v docker &> /dev/null; then
        log_error "Docker is not installed. Please install it first."
        exit 1
    fi
    log_success "Docker is installed"
    
    # Check if Docker daemon is running
    if ! docker info &> /dev/null; then
        log_error "Docker daemon is not running. Please start Docker."
        exit 1
    fi
    log_success "Docker daemon is running"
    
    # Check if gradlew exists
    if [[ ! -f "${SCRIPT_DIR}/gradlew" ]]; then
        log_error "gradlew not found in ${SCRIPT_DIR}"
        exit 1
    fi
    log_success "gradlew found"
    
    # Check if jq is installed (optional but recommended)
    if ! command -v jq &> /dev/null; then
        log_warning "jq is not installed. JSON output will be less readable."
    fi
}

################################################################################
# Deployment Functions
################################################################################

# Step 0a: Build Docker Image
build_docker_image() {
    log_step "Step 0a: Building Docker Image with Gradle"
    
    log_info "Building Docker image for linux/amd64 platform (AWS ECS Fargate compatible)..."
    cd "${SCRIPT_DIR}"
    
    # Run gradlew dockerBuild with platform specification for AWS ECS Fargate
    # AWS ECS Fargate requires linux/amd64 architecture
    if ./gradlew dockerBuild -Pdocker.platform=linux/amd64 --no-daemon; then
        log_success "Docker image built successfully for linux/amd64"
    else
        log_error "Failed to build Docker image"
        exit 1
    fi
    
    # Extract the image name from the build
    # The Gradle build creates an image with the project name (e.g., "flight")
    # We need to find the actual image name that was built
    LOCAL_IMAGE_TAG="latest"
    
    # Try to find the image by listing recent Docker images
    log_info "Searching for built Docker image..."
    
    # First, try common project names
    for possible_name in "flight" "${TASK_FAMILY}" "wdp-connect-sdk-gen-flight"; do
        if docker images "${possible_name}:${LOCAL_IMAGE_TAG}" --format "{{.Repository}}:{{.Tag}}" | grep -q "${possible_name}:${LOCAL_IMAGE_TAG}"; then
            LOCAL_IMAGE_NAME="${possible_name}"
            log_success "Found Docker image: ${LOCAL_IMAGE_NAME}:${LOCAL_IMAGE_TAG}"
            return 0
        fi
    done
    
    # If not found, list all images with 'latest' tag created in the last 5 minutes
    log_warning "Could not find image with expected names. Listing recent images..."
    RECENT_IMAGE=$(docker images --format "{{.Repository}}:{{.Tag}}" --filter "since=5m" | grep ":latest" | head -1)
    
    if [[ -n "$RECENT_IMAGE" ]]; then
        LOCAL_IMAGE_NAME=$(echo "$RECENT_IMAGE" | cut -d':' -f1)
        log_warning "Using recently built image: ${LOCAL_IMAGE_NAME}:${LOCAL_IMAGE_TAG}"
        log_warning "If this is incorrect, please check your Gradle build configuration"
    else
        log_error "Docker image not found after build"
        log_error "Please verify the image was built successfully with: docker images"
        exit 1
    fi
}

# Step 0b: Create ECR Repository
create_ecr_repository() {
    log_step "Step 0b: Creating ECR Repository"
    
    # Extract repository name from ECR_IMAGE_URI
    # Format: account-id.dkr.ecr.region.amazonaws.com/repository-name:tag
    ECR_REPOSITORY_NAME=$(echo "$ECR_IMAGE_URI" | sed -E 's|.*/([^:]+):.*|\1|')
    
    log_info "ECR Repository name: $ECR_REPOSITORY_NAME"
    
    # Check if repository exists
    if aws ecr describe-repositories \
        --region "$AWS_REGION" \
        --repository-names "$ECR_REPOSITORY_NAME" &>/dev/null; then
        log_warning "ECR repository '$ECR_REPOSITORY_NAME' already exists"
    else
        log_info "Creating ECR repository: $ECR_REPOSITORY_NAME"
        aws ecr create-repository \
            --region "$AWS_REGION" \
            --repository-name "$ECR_REPOSITORY_NAME" \
            --image-scanning-configuration scanOnPush=true \
            --encryption-configuration encryptionType=AES256 > /dev/null
        log_success "ECR repository created: $ECR_REPOSITORY_NAME"
    fi
}

# Step 0c: Push Docker Image to ECR
push_image_to_ecr() {
    log_step "Step 0c: Pushing Docker Image to ECR"
    
    # Login to ECR
    log_info "Logging into ECR..."
    aws ecr get-login-password --region "$AWS_REGION" | \
        docker login --username AWS --password-stdin "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
    
    if [[ $? -eq 0 ]]; then
        log_success "Successfully logged into ECR"
    else
        log_error "Failed to login to ECR"
        exit 1
    fi
    
    # Tag the local image for ECR
    log_info "Tagging image for ECR: ${LOCAL_IMAGE_NAME}:${LOCAL_IMAGE_TAG} -> ${ECR_IMAGE_URI}"
    docker tag "${LOCAL_IMAGE_NAME}:${LOCAL_IMAGE_TAG}" "${ECR_IMAGE_URI}"
    
    if [[ $? -eq 0 ]]; then
        log_success "Image tagged successfully"
    else
        log_error "Failed to tag image"
        exit 1
    fi
    
    # Push the image to ECR
    log_info "Pushing image to ECR: ${ECR_IMAGE_URI}"
    docker push "${ECR_IMAGE_URI}"
    
    if [[ $? -eq 0 ]]; then
        log_success "Image pushed successfully to ECR"
    else
        log_error "Failed to push image to ECR"
        exit 1
    fi
    
    # Verify the image in ECR
    log_info "Verifying image in ECR..."
    ECR_REPOSITORY_NAME=$(echo "$ECR_IMAGE_URI" | sed -E 's|.*/([^:]+):.*|\1|')
    if aws ecr describe-images \
        --region "$AWS_REGION" \
        --repository-name "$ECR_REPOSITORY_NAME" \
        --image-ids imageTag=latest &>/dev/null; then
        log_success "Image verified in ECR"
    else
        log_warning "Could not verify image in ECR (may still be uploading)"
    fi
}

# Step 1: Create Security Group
create_security_group() {
    log_step "Step 1: Creating Security Group"
    
    # Check if security group already exists
    SECURITY_GROUP_ID=$(aws ec2 describe-security-groups \
        --region "$AWS_REGION" \
        --filters "Name=group-name,Values=$SECURITY_GROUP_NAME" "Name=vpc-id,Values=$VPC_ID" \
        --query 'SecurityGroups[0].GroupId' \
        --output text 2>/dev/null || echo "")
    
    if [[ "$SECURITY_GROUP_ID" != "" && "$SECURITY_GROUP_ID" != "None" ]]; then
        log_warning "Security group '$SECURITY_GROUP_NAME' already exists with ID: $SECURITY_GROUP_ID"
    else
        log_info "Creating security group: $SECURITY_GROUP_NAME"
        SECURITY_GROUP_ID=$(aws ec2 create-security-group \
            --region "$AWS_REGION" \
            --group-name "$SECURITY_GROUP_NAME" \
            --description "JDBC gRPC connector security group" \
            --vpc-id "$VPC_ID" \
            --query 'GroupId' \
            --output text)
        log_success "Security group created: $SECURITY_GROUP_ID"
    fi
    
    # Add ingress rule for the container port
    log_info "Adding ingress rule for port $CONTAINER_PORT"
    aws ec2 authorize-security-group-ingress \
        --region "$AWS_REGION" \
        --group-id "$SECURITY_GROUP_ID" \
        --protocol tcp \
        --port "$CONTAINER_PORT" \
        --cidr 0.0.0.0/0 2>/dev/null || log_warning "Ingress rule may already exist"
    
    log_success "Security group configured: $SECURITY_GROUP_ID"
}

# Step 2: Create CloudWatch Log Group
create_log_group() {
    log_step "Step 2: Creating CloudWatch Log Group"
    
    # Check if log group already exists
    if aws logs describe-log-groups \
        --region "$AWS_REGION" \
        --log-group-name-prefix "$LOG_GROUP_NAME" \
        --query "logGroups[?logGroupName=='$LOG_GROUP_NAME']" \
        --output text | grep -q "$LOG_GROUP_NAME"; then
        log_warning "Log group '$LOG_GROUP_NAME' already exists"
    else
        log_info "Creating log group: $LOG_GROUP_NAME"
        aws logs create-log-group \
            --region "$AWS_REGION" \
            --log-group-name "$LOG_GROUP_NAME"
        log_success "Log group created: $LOG_GROUP_NAME"
    fi
}

# Step 3: Create IAM Execution Role
create_iam_role() {
    log_step "Step 3: Creating IAM Execution Role"
    
    # Check if role already exists
    if aws iam get-role --role-name "$IAM_ROLE_NAME" &>/dev/null; then
        log_warning "IAM role '$IAM_ROLE_NAME' already exists"
        IAM_ROLE_ARN=$(aws iam get-role --role-name "$IAM_ROLE_NAME" --query 'Role.Arn' --output text)
    else
        log_info "Creating IAM role: $IAM_ROLE_NAME"
        
        # Create trust policy
        cat > /tmp/trust-policy.json << EOF
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": {"Service": "ecs-tasks.amazonaws.com"},
    "Action": "sts:AssumeRole"
  }]
}
EOF
        
        # Create role
        IAM_ROLE_ARN=$(aws iam create-role \
            --role-name "$IAM_ROLE_NAME" \
            --assume-role-policy-document file:///tmp/trust-policy.json \
            --query 'Role.Arn' \
            --output text)
        
        log_success "IAM role created: $IAM_ROLE_ARN"
        
        # Attach policy
        log_info "Attaching execution policy to role"
        aws iam attach-role-policy \
            --role-name "$IAM_ROLE_NAME" \
            --policy-arn arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy
        
        # Wait for role to be available
        log_info "Waiting for IAM role to propagate..."
        sleep 10
        
        # Clean up temp file
        rm -f /tmp/trust-policy.json
    fi
    
    log_success "IAM role ready: $IAM_ROLE_ARN"
}

# Step 4: Register Task Definition
register_task_definition() {
    log_step "Step 4: Registering Task Definition"
    
    log_info "Creating task definition: $TASK_FAMILY"
    
    # Create task definition JSON
    cat > /tmp/task-definition.json << EOF
{
  "family": "$TASK_FAMILY",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "$TASK_CPU",
  "memory": "$TASK_MEMORY",
  "executionRoleArn": "$IAM_ROLE_ARN",
  "containerDefinitions": [{
    "name": "jdbc-connector",
    "image": "$ECR_IMAGE_URI",
    "portMappings": [{
      "containerPort": $CONTAINER_PORT,
      "protocol": "tcp"
    }],
    "essential": true,
    "logConfiguration": {
      "logDriver": "awslogs",
      "options": {
        "awslogs-group": "$LOG_GROUP_NAME",
        "awslogs-region": "$AWS_REGION",
        "awslogs-stream-prefix": "ecs"
      }
    }
  }]
}
EOF
    
    # Register task definition
    TASK_DEFINITION_ARN=$(aws ecs register-task-definition \
        --region "$AWS_REGION" \
        --cli-input-json file:///tmp/task-definition.json \
        --query 'taskDefinition.taskDefinitionArn' \
        --output text)
    
    log_success "Task definition registered: $TASK_DEFINITION_ARN"
    
    # Clean up temp file
    rm -f /tmp/task-definition.json
}

# Step 5: Deploy ECS Service
deploy_service() {
    log_step "Step 5: Deploying ECS Service"
    
    # Check if service already exists
    if aws ecs describe-services \
        --region "$AWS_REGION" \
        --cluster "$ECS_CLUSTER" \
        --services "$SERVICE_NAME" \
        --query 'services[0].status' \
        --output text 2>/dev/null | grep -q "ACTIVE"; then
        log_warning "Service '$SERVICE_NAME' already exists. Updating..."
        
        # Update existing service
        aws ecs update-service \
            --region "$AWS_REGION" \
            --cluster "$ECS_CLUSTER" \
            --service "$SERVICE_NAME" \
            --task-definition "$TASK_FAMILY" \
            --desired-count "$DESIRED_COUNT" \
            --force-new-deployment > /dev/null
        
        log_success "Service updated: $SERVICE_NAME"
    else
        log_info "Creating ECS service: $SERVICE_NAME"
        
        # Create new service
        aws ecs create-service \
            --region "$AWS_REGION" \
            --cluster "$ECS_CLUSTER" \
            --service-name "$SERVICE_NAME" \
            --task-definition "$TASK_FAMILY" \
            --desired-count "$DESIRED_COUNT" \
            --launch-type FARGATE \
            --network-configuration "awsvpcConfiguration={subnets=[$SUBNET_ID],securityGroups=[$SECURITY_GROUP_ID],assignPublicIp=ENABLED}" \
            > /dev/null
        
        log_success "Service created: $SERVICE_NAME"
    fi
    
    log_info "Waiting for service to stabilize..."
    aws ecs wait services-stable \
        --region "$AWS_REGION" \
        --cluster "$ECS_CLUSTER" \
        --services "$SERVICE_NAME" || log_warning "Service may still be starting up"
}

# Step 6: Get Public IP
get_public_ip() {
    log_step "Step 6: Retrieving Public IP Address"
    
    log_info "Getting task ARN..."
    TASK_ARN=$(aws ecs list-tasks \
        --region "$AWS_REGION" \
        --cluster "$ECS_CLUSTER" \
        --service-name "$SERVICE_NAME" \
        --query 'taskArns[0]' \
        --output text)
    
    if [[ -z "$TASK_ARN" || "$TASK_ARN" == "None" ]]; then
        log_error "No tasks found for service $SERVICE_NAME"
        return 1
    fi
    
    log_info "Getting network interface ID..."
    ENI_ID=$(aws ecs describe-tasks \
        --region "$AWS_REGION" \
        --cluster "$ECS_CLUSTER" \
        --tasks "$TASK_ARN" \
        --query 'tasks[0].attachments[0].details[?name==`networkInterfaceId`].value' \
        --output text)
    
    log_info "Getting public IP address..."
    PUBLIC_IP=$(aws ec2 describe-network-interfaces \
        --region "$AWS_REGION" \
        --network-interface-ids "$ENI_ID" \
        --query 'NetworkInterfaces[0].Association.PublicIp' \
        --output text)
    
    if [[ -z "$PUBLIC_IP" || "$PUBLIC_IP" == "None" ]]; then
        log_error "Could not retrieve public IP address"
        return 1
    fi
    
    log_success "Public IP: $PUBLIC_IP"
    echo "$PUBLIC_IP" > /tmp/ecs-public-ip.txt
}

# Step 7: Verify Deployment
verify_deployment() {
    log_step "Step 7: Verifying Deployment"
    
    log_info "Checking service status..."
    SERVICE_STATUS=$(aws ecs describe-services \
        --region "$AWS_REGION" \
        --cluster "$ECS_CLUSTER" \
        --services "$SERVICE_NAME" \
        --query 'services[0].status' \
        --output text)
    
    log_info "Service status: $SERVICE_STATUS"
    
    if [[ "$SERVICE_STATUS" == "ACTIVE" ]]; then
        log_success "Service is active"
    else
        log_warning "Service status is not ACTIVE"
    fi
    
    log_info "Recent logs (last 10 lines):"
    aws logs tail "$LOG_GROUP_NAME" \
        --region "$AWS_REGION" \
        --since 5m \
        --format short 2>/dev/null | tail -10 || log_warning "Could not retrieve logs"
}

# Step 8: Test Connectivity (Optional)
test_connectivity() {
    if [[ "$ENABLE_GRPC_TESTING" != "true" ]]; then
        log_info "gRPC testing is disabled. Skipping connectivity tests."
        return 0
    fi
    
    log_step "Step 8: Testing Connectivity"
    
    if [[ -z "$PUBLIC_IP" ]]; then
        log_warning "Public IP not available. Skipping connectivity test."
        return 0
    fi
    
    log_info "Testing basic connectivity to $PUBLIC_IP:$CONTAINER_PORT"
    
    # Test with timeout
    if timeout 5 bash -c "cat < /dev/null > /dev/tcp/$PUBLIC_IP/$CONTAINER_PORT" 2>/dev/null; then
        log_success "Port $CONTAINER_PORT is reachable"
    else
        log_warning "Could not connect to port $CONTAINER_PORT (this may be normal if the service is still starting)"
    fi
    
    # Test TLS if openssl is available
    if command -v openssl &> /dev/null; then
        log_info "Testing TLS handshake..."
        if echo | timeout 5 openssl s_client -connect "$PUBLIC_IP:$CONTAINER_PORT" -servername localhost 2>/dev/null | grep -q "Verify return code"; then
            log_success "TLS handshake successful"
        else
            log_warning "TLS handshake failed or timed out"
        fi
    fi
}

# Cleanup function
cleanup_resources() {
    if [[ "$ENABLE_CLEANUP" != "true" ]]; then
        log_info "Cleanup is disabled. Resources will remain deployed."
        return 0
    fi
    
    log_step "Cleanup: Removing Deployed Resources"
    
    log_warning "This will delete all resources created by this script!"
    read -p "Are you sure you want to continue? (yes/no): " -r
    if [[ ! $REPLY =~ ^[Yy][Ee][Ss]$ ]]; then
        log_info "Cleanup cancelled"
        return 0
    fi
    
    # Delete service
    log_info "Deleting ECS service..."
    aws ecs delete-service \
        --region "$AWS_REGION" \
        --cluster "$ECS_CLUSTER" \
        --service "$SERVICE_NAME" \
        --force > /dev/null 2>&1 || log_warning "Service may not exist"
    
    log_info "Waiting for service deletion..."
    sleep 30
    
    # Delete security group
    log_info "Deleting security group..."
    aws ec2 delete-security-group \
        --region "$AWS_REGION" \
        --group-id "$SECURITY_GROUP_ID" > /dev/null 2>&1 || log_warning "Security group may not exist or still in use"
    
    # Delete log group
    log_info "Deleting log group..."
    aws logs delete-log-group \
        --region "$AWS_REGION" \
        --log-group-name "$LOG_GROUP_NAME" > /dev/null 2>&1 || log_warning "Log group may not exist"
    
    # Detach and delete IAM role
    log_info "Deleting IAM role..."
    aws iam detach-role-policy \
        --role-name "$IAM_ROLE_NAME" \
        --policy-arn arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy > /dev/null 2>&1 || true
    
    aws iam delete-role \
        --role-name "$IAM_ROLE_NAME" > /dev/null 2>&1 || log_warning "IAM role may not exist"
    
    log_success "Cleanup completed"
}

################################################################################
# Main Execution
################################################################################

main() {
    echo -e "${BLUE}"
    echo "╔════════════════════════════════════════════════════════════════╗"
    echo "║   AWS ECS Deployment Script - JDBC Custom Connector           ║"
    echo "╚════════════════════════════════════════════════════════════════╝"
    echo -e "${NC}"
    
    # Load and validate configuration
    load_properties
    validate_parameters
    check_prerequisites
    
    # Execute deployment steps (including image build and push)
    build_docker_image
    create_ecr_repository
    push_image_to_ecr
    create_security_group
    create_log_group
    create_iam_role
    register_task_definition
    deploy_service
    get_public_ip
    verify_deployment
    test_connectivity
    
    # Display summary
    echo -e "\n${GREEN}╔════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║                  DEPLOYMENT SUMMARY                            ║${NC}"
    echo -e "${GREEN}╚════════════════════════════════════════════════════════════════╝${NC}"
    echo -e "${BLUE}Region:${NC}              $AWS_REGION"
    echo -e "${BLUE}Cluster:${NC}             $ECS_CLUSTER"
    echo -e "${BLUE}Service:${NC}             $SERVICE_NAME"
    echo -e "${BLUE}Task Definition:${NC}     $TASK_FAMILY"
    echo -e "${BLUE}Security Group:${NC}      $SECURITY_GROUP_ID"
    echo -e "${BLUE}Public IP:${NC}           ${GREEN}$PUBLIC_IP${NC}"
    echo -e "${BLUE}gRPC Endpoint:${NC}       ${GREEN}$PUBLIC_IP:$CONTAINER_PORT${NC}"
    echo -e "${BLUE}Log Group:${NC}           $LOG_GROUP_NAME"
    echo ""
    echo -e "${YELLOW}Next Steps:${NC}"
    echo "  1. Test connectivity: telnet $PUBLIC_IP $CONTAINER_PORT"
    echo "  2. View logs: aws logs tail $LOG_GROUP_NAME --follow --region $AWS_REGION"
    echo "  3. Test gRPC: grpcurl -insecure -proto Flight.proto $PUBLIC_IP:$CONTAINER_PORT list"
    echo ""
    
    # Optional cleanup
    if [[ "$ENABLE_CLEANUP" == "true" ]]; then
        cleanup_resources
    fi
    
    log_success "Deployment completed successfully!"
}

# Run main function
main "$@"

# Made with Bob
