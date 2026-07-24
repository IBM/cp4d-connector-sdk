#!/bin/bash

################################################################################
# AWS ECS Deployment Script for JDBC Custom Connector
#
# This script automates the deployment of a JDBC connector to AWS ECS Fargate
# by pulling a pre-built Docker image from GitHub Container Registry (GHCR)
# and deploying it to AWS ECS.
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
    
    if [[ ${#missing_params[@]} -gt 0 ]]; then
        log_error "Missing required parameters:"
        for param in "${missing_params[@]}"; do
            echo "  - $param"
        done
        exit 1
    fi
    
    # Set default values for optional parameters
    if [[ -z "$ECS_CLUSTER" ]]; then
        ECS_CLUSTER="jdbc-connector-cluster"
        log_info "Using default ECS_CLUSTER: $ECS_CLUSTER"
    fi
    
    if [[ -z "$ECR_IMAGE_URI" ]]; then
        # Auto-generate ECR image URI based on account and region
        ECR_REPOSITORY_NAME="jdbc-connector"
        ECR_IMAGE_URI="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${ECR_REPOSITORY_NAME}:latest"
        log_info "Using auto-generated ECR_IMAGE_URI: $ECR_IMAGE_URI"
    fi
    
    # Set default values for EFS if not provided
    if [[ -z "$ENABLE_EFS" ]]; then
        ENABLE_EFS="false"
    fi
    
    if [[ "$ENABLE_EFS" == "true" ]]; then
        if [[ -z "$EFS_NAME" ]]; then
            EFS_NAME="jdbc-connector-efs"
            log_info "Using default EFS_NAME: $EFS_NAME"
        fi
    fi
    
    # Set default values for other optional parameters
    if [[ -z "$SECURITY_GROUP_NAME" ]]; then
        SECURITY_GROUP_NAME="jdbc-connector-sg"
        log_info "Using default SECURITY_GROUP_NAME: $SECURITY_GROUP_NAME"
    fi
    
    if [[ -z "$LOG_GROUP_NAME" ]]; then
        LOG_GROUP_NAME="/ecs/jdbc-connector"
        log_info "Using default LOG_GROUP_NAME: $LOG_GROUP_NAME"
    fi
    
    if [[ -z "$IAM_ROLE_NAME" ]]; then
        IAM_ROLE_NAME="ecsTaskExecutionRole-jdbc-connector"
        log_info "Using default IAM_ROLE_NAME: $IAM_ROLE_NAME"
    fi
    
    if [[ -z "$TASK_FAMILY" ]]; then
        TASK_FAMILY="jdbc-connector-task"
        log_info "Using default TASK_FAMILY: $TASK_FAMILY"
    fi
    
    if [[ -z "$SERVICE_NAME" ]]; then
        SERVICE_NAME="jdbc-connector-service"
        log_info "Using default SERVICE_NAME: $SERVICE_NAME"
    fi
    
    if [[ -z "$CONTAINER_PORT" ]]; then
        CONTAINER_PORT="3443"
        log_info "Using default CONTAINER_PORT: $CONTAINER_PORT"
    fi
    
    if [[ -z "$TASK_CPU" ]]; then
        TASK_CPU="256"
        log_info "Using default TASK_CPU: $TASK_CPU"
    fi
    
    if [[ -z "$TASK_MEMORY" ]]; then
        TASK_MEMORY="512"
        log_info "Using default TASK_MEMORY: $TASK_MEMORY"
    fi
    
    if [[ -z "$DESIRED_COUNT" ]]; then
        DESIRED_COUNT="1"
        log_info "Using default DESIRED_COUNT: $DESIRED_COUNT"
    fi
    
    log_success "All required parameters are present"
}

# Configure AWS credentials if provided in properties file
configure_aws_credentials() {
    if [[ -n "$AWS_ACCESS_KEY_ID" && -n "$AWS_SECRET_ACCESS_KEY" ]]; then
        log_info "Configuring AWS credentials from properties file..."
        export AWS_ACCESS_KEY_ID="$AWS_ACCESS_KEY_ID"
        export AWS_SECRET_ACCESS_KEY="$AWS_SECRET_ACCESS_KEY"
        
        # Configure session token if provided (for temporary credentials)
        if [[ -n "$AWS_SESSION_TOKEN" ]]; then
            export AWS_SESSION_TOKEN="$AWS_SESSION_TOKEN"
            log_info "AWS session token configured"
        fi
        
        log_success "AWS credentials configured from properties file"
    else
        log_info "Using existing AWS credentials configuration"
    fi
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
    
    # Configure AWS credentials if provided
    configure_aws_credentials
    
    # Check AWS credentials
    if ! aws sts get-caller-identity --region "$AWS_REGION" &> /dev/null; then
        log_error "AWS credentials are not configured or invalid."
        log_error "Please either:"
        log_error "  1. Configure AWS CLI: aws configure"
        log_error "  2. Add credentials to properties file:"
        log_error "     AWS_ACCESS_KEY_ID=your-access-key"
        log_error "     AWS_SECRET_ACCESS_KEY=your-secret-key"
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
    
    # Check if jq is installed (optional but recommended)
    if ! command -v jq &> /dev/null; then
        log_warning "jq is not installed. JSON output will be less readable."
    fi
}

################################################################################
# Deployment Functions
################################################################################

# Step 0a: Pull Docker Image from GHCR
pull_docker_image() {
    log_step "Step 0a: Pulling Docker Image from GitHub Container Registry"
    
    # Define the source image from GHCR
    GHCR_IMAGE="ghcr.io/thomasgloria/wdp-connect-sdk-gen-jdbc-connectors-forge:latest"
    
    log_info "Pulling image from GHCR: ${GHCR_IMAGE}"
    
    # Pull the image
    if docker pull "${GHCR_IMAGE}"; then
        log_success "Docker image pulled successfully from GHCR"
    else
        log_error "Failed to pull Docker image from GHCR"
        log_error "Please verify:"
        log_error "  1. The image exists at: ${GHCR_IMAGE}"
        log_error "  2. You have access to the repository (may need: docker login ghcr.io)"
        log_error "  3. Your internet connection is working"
        exit 1
    fi
    
    # Set local image variables for use in subsequent steps
    LOCAL_IMAGE_NAME="${GHCR_IMAGE}"
    LOCAL_IMAGE_TAG="latest"
    
    log_success "Image ready: ${LOCAL_IMAGE_NAME}"
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
    log_info "Tagging image for ECR: ${LOCAL_IMAGE_NAME} -> ${ECR_IMAGE_URI}"
    docker tag "${LOCAL_IMAGE_NAME}" "${ECR_IMAGE_URI}"
    
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

# Step 1b: Create EFS File System (Optional - for JDBC driver)
create_efs_filesystem() {
    # Check if EFS is enabled
    if [[ "$ENABLE_EFS" != "true" ]]; then
        log_info "EFS support is disabled. Skipping EFS creation."
        return 0
    fi
    
    # Ensure EFS_NAME has a default value
    if [[ -z "$EFS_NAME" ]]; then
        EFS_NAME="jdbc-connector-efs"
        log_warning "EFS_NAME not set, using default: $EFS_NAME"
    fi
    
    log_step "Step 1b: Creating EFS File System for JDBC Driver"
    
    # Check if EFS file system already exists
    EFS_FILE_SYSTEM_ID=$(aws efs describe-file-systems \
        --region "$AWS_REGION" \
        --query "FileSystems[?Name=='$EFS_NAME'].FileSystemId" \
        --output text 2>/dev/null || echo "")
    
    if [[ -n "$EFS_FILE_SYSTEM_ID" && "$EFS_FILE_SYSTEM_ID" != "None" ]]; then
        log_warning "EFS file system '$EFS_NAME' already exists with ID: $EFS_FILE_SYSTEM_ID"
    else
        log_info "Creating EFS file system: $EFS_NAME"
        EFS_FILE_SYSTEM_ID=$(aws efs create-file-system \
            --region "$AWS_REGION" \
            --performance-mode generalPurpose \
            --throughput-mode bursting \
            --encrypted \
            --tags Key=Name,Value="$EFS_NAME" \
            --query 'FileSystemId' \
            --output text)
        
        log_success "EFS file system created: $EFS_FILE_SYSTEM_ID"
        
        # Wait for EFS to become available
        log_info "Waiting for EFS file system to become available..."
        aws efs wait file-system-available \
            --region "$AWS_REGION" \
            --file-system-id "$EFS_FILE_SYSTEM_ID"
        log_success "EFS file system is available"
    fi
    
    # Create mount target in the subnet
    log_info "Creating EFS mount target in subnet: $SUBNET_ID"
    
    # Check if mount target already exists
    MOUNT_TARGET_ID=$(aws efs describe-mount-targets \
        --region "$AWS_REGION" \
        --file-system-id "$EFS_FILE_SYSTEM_ID" \
        --query "MountTargets[?SubnetId=='$SUBNET_ID'].MountTargetId" \
        --output text 2>/dev/null || echo "")
    
    if [[ -n "$MOUNT_TARGET_ID" && "$MOUNT_TARGET_ID" != "None" ]]; then
        log_warning "EFS mount target already exists in subnet: $MOUNT_TARGET_ID"
    else
        # Create security group for EFS if it doesn't exist
        EFS_SG_ID=$(aws ec2 describe-security-groups \
            --region "$AWS_REGION" \
            --filters "Name=group-name,Values=${EFS_NAME}-sg" "Name=vpc-id,Values=$VPC_ID" \
            --query 'SecurityGroups[0].GroupId' \
            --output text 2>/dev/null || echo "")
        
        if [[ -z "$EFS_SG_ID" || "$EFS_SG_ID" == "None" ]]; then
            log_info "Creating security group for EFS"
            EFS_SG_ID=$(aws ec2 create-security-group \
                --group-name "${EFS_NAME}-sg" \
                --description "Security group for EFS mount targets" \
                --vpc-id "$VPC_ID" \
                --region "$AWS_REGION" \
                --query 'GroupId' \
                --output text)
            
            # Allow NFS traffic from the connector security group
            aws ec2 authorize-security-group-ingress \
                --region "$AWS_REGION" \
                --group-id "$EFS_SG_ID" \
                --protocol tcp \
                --port 2049 \
                --source-group "$SECURITY_GROUP_ID" 2>/dev/null || true
            
            log_success "EFS security group created: $EFS_SG_ID"
        fi
        
        MOUNT_TARGET_ID=$(aws efs create-mount-target \
            --region "$AWS_REGION" \
            --file-system-id "$EFS_FILE_SYSTEM_ID" \
            --subnet-id "$SUBNET_ID" \
            --security-groups "$EFS_SG_ID" \
            --query 'MountTargetId' \
            --output text)
        
        log_success "EFS mount target created: $MOUNT_TARGET_ID"
        
        # Wait for mount target to become available
        log_info "Waiting for EFS mount target to become available..."
        sleep 30  # EFS mount targets take time to become available
    fi
    
    # Create access point for the JDBC driver
    log_info "Creating EFS access point for JDBC driver"
    
    EFS_ACCESS_POINT_ID=$(aws efs describe-access-points \
        --region "$AWS_REGION" \
        --file-system-id "$EFS_FILE_SYSTEM_ID" \
        --query "AccessPoints[?Name=='jdbc-driver'].AccessPointId" \
        --output text 2>/dev/null || echo "")
    
    if [[ -n "$EFS_ACCESS_POINT_ID" && "$EFS_ACCESS_POINT_ID" != "None" ]]; then
        log_warning "EFS access point already exists: $EFS_ACCESS_POINT_ID"
    else
        EFS_ACCESS_POINT_ID=$(aws efs create-access-point \
            --region "$AWS_REGION" \
            --file-system-id "$EFS_FILE_SYSTEM_ID" \
            --posix-user Uid=1000,Gid=1000 \
            --root-directory "Path=/jdbc-drivers,CreationInfo={OwnerUid=1000,OwnerGid=1000,Permissions=755}" \
            --tags Key=Name,Value=jdbc-driver \
            --query 'AccessPointId' \
            --output text)
        
        log_success "EFS access point created: $EFS_ACCESS_POINT_ID"
    fi
    
    log_success "EFS file system configured: $EFS_FILE_SYSTEM_ID"
}

# Step 1c: Upload JDBC Driver to EFS (Automated via S3 and DataSync)
upload_driver_to_efs() {
    if [[ "$ENABLE_EFS" != "true" ]]; then
        return 0
    fi
    
    log_step "Step 1c: Uploading JDBC Driver to EFS (Automated)"
    
    # Check if driver file exists
    DRIVER_FILE="${SCRIPT_DIR}/../connectors-forge/codeless/lib/driver.jar"
    if [[ ! -f "$DRIVER_FILE" ]]; then
        log_error "JDBC driver not found at: $DRIVER_FILE"
        log_error "Please place your JDBC driver JAR file at this location"
        exit 1
    fi
    
    log_info "Found JDBC driver: $DRIVER_FILE"
    
    # Create S3 bucket for driver upload (temporary)
    S3_BUCKET_NAME="jdbc-driver-upload-${AWS_ACCOUNT_ID}-${AWS_REGION}"
    log_info "Creating temporary S3 bucket: $S3_BUCKET_NAME"
    
    if aws s3 ls "s3://${S3_BUCKET_NAME}" 2>/dev/null; then
        log_warning "S3 bucket already exists: $S3_BUCKET_NAME"
    else
        aws s3 mb "s3://${S3_BUCKET_NAME}" --region "$AWS_REGION"
        log_success "S3 bucket created: $S3_BUCKET_NAME"
    fi
    
    # Upload driver to S3
    log_info "Uploading driver to S3..."
    aws s3 cp "$DRIVER_FILE" "s3://${S3_BUCKET_NAME}/driver.jar" --region "$AWS_REGION"
    log_success "Driver uploaded to S3"
    
    # Create IAM role for DataSync if it doesn't exist
    DATASYNC_ROLE_NAME="DataSyncEFSRole-${EFS_NAME}"
    log_info "Creating IAM role for DataSync..."
    
    if aws iam get-role --role-name "$DATASYNC_ROLE_NAME" &>/dev/null; then
        log_warning "DataSync IAM role already exists"
        DATASYNC_ROLE_ARN=$(aws iam get-role --role-name "$DATASYNC_ROLE_NAME" --query 'Role.Arn' --output text)
    else
        # Create trust policy for DataSync
        cat > /tmp/datasync-trust-policy.json << EOF
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": {"Service": "datasync.amazonaws.com"},
    "Action": "sts:AssumeRole"
  }]
}
EOF
        
        DATASYNC_ROLE_ARN=$(aws iam create-role \
            --role-name "$DATASYNC_ROLE_NAME" \
            --assume-role-policy-document file:///tmp/datasync-trust-policy.json \
            --query 'Role.Arn' \
            --output text)
        
        # Attach policies for S3 and EFS access
        aws iam attach-role-policy \
            --role-name "$DATASYNC_ROLE_NAME" \
            --policy-arn arn:aws:iam::aws:policy/AmazonS3ReadOnlyAccess
        
        aws iam attach-role-policy \
            --role-name "$DATASYNC_ROLE_NAME" \
            --policy-arn arn:aws:iam::aws:policy/AmazonElasticFileSystemClientFullAccess
        
        sleep 10  # Wait for role propagation
        rm -f /tmp/datasync-trust-policy.json
        log_success "DataSync IAM role created"
    fi
    
    # Create DataSync locations
    log_info "Creating DataSync source location (S3)..."
    S3_LOCATION_ARN=$(aws datasync create-location-s3 \
        --region "$AWS_REGION" \
        --s3-bucket-arn "arn:aws:s3:::${S3_BUCKET_NAME}" \
        --s3-config "BucketAccessRoleArn=${DATASYNC_ROLE_ARN}" \
        --query 'LocationArn' \
        --output text 2>/dev/null || echo "")
    
    if [[ -z "$S3_LOCATION_ARN" ]]; then
        # Location might already exist, try to find it
        S3_LOCATION_ARN=$(aws datasync list-locations \
            --region "$AWS_REGION" \
            --query "Locations[?LocationUri=='s3://${S3_BUCKET_NAME}/'].LocationArn" \
            --output text | head -1)
    fi
    log_success "S3 location ready: $S3_LOCATION_ARN"
    
    log_info "Creating DataSync destination location (EFS)..."
    EFS_LOCATION_ARN=$(aws datasync create-location-efs \
        --region "$AWS_REGION" \
        --efs-filesystem-arn "arn:aws:elasticfilesystem:${AWS_REGION}:${AWS_ACCOUNT_ID}:file-system/${EFS_FILE_SYSTEM_ID}" \
        --ec2-config "SubnetArn=arn:aws:ec2:${AWS_REGION}:${AWS_ACCOUNT_ID}:subnet/${SUBNET_ID},SecurityGroupArns=[arn:aws:ec2:${AWS_REGION}:${AWS_ACCOUNT_ID}:security-group/${EFS_SG_ID}]" \
        --subdirectory "/jdbc-drivers" \
        --query 'LocationArn' \
        --output text 2>/dev/null || echo "")
    
    if [[ -z "$EFS_LOCATION_ARN" ]]; then
        # Location might already exist
        EFS_LOCATION_ARN=$(aws datasync list-locations \
            --region "$AWS_REGION" \
            --query "Locations[?contains(LocationUri, '${EFS_FILE_SYSTEM_ID}')].LocationArn" \
            --output text | head -1)
    fi
    log_success "EFS location ready: $EFS_LOCATION_ARN"
    
    # Create and run DataSync task
    log_info "Creating DataSync task..."
    TASK_ARN=$(aws datasync create-task \
        --region "$AWS_REGION" \
        --source-location-arn "$S3_LOCATION_ARN" \
        --destination-location-arn "$EFS_LOCATION_ARN" \
        --name "jdbc-driver-upload-${EFS_NAME}" \
        --query 'TaskArn' \
        --output text 2>/dev/null || echo "")
    
    if [[ -z "$TASK_ARN" ]]; then
        # Task might already exist
        TASK_ARN=$(aws datasync list-tasks \
            --region "$AWS_REGION" \
            --query "Tasks[?Name=='jdbc-driver-upload-${EFS_NAME}'].TaskArn" \
            --output text | head -1)
    fi
    log_success "DataSync task ready: $TASK_ARN"
    
    # Start the task
    log_info "Starting DataSync task to copy driver to EFS..."
    TASK_EXECUTION_ARN=$(aws datasync start-task-execution \
        --region "$AWS_REGION" \
        --task-arn "$TASK_ARN" \
        --query 'TaskExecutionArn' \
        --output text)
    
    log_info "Waiting for DataSync task to complete..."
    while true; do
        STATUS=$(aws datasync describe-task-execution \
            --region "$AWS_REGION" \
            --task-execution-arn "$TASK_EXECUTION_ARN" \
            --query 'Status' \
            --output text)
        
        if [[ "$STATUS" == "SUCCESS" ]]; then
            log_success "Driver successfully copied to EFS!"
            break
        elif [[ "$STATUS" == "ERROR" ]]; then
            log_error "DataSync task failed"
            exit 1
        else
            log_info "DataSync status: $STATUS (waiting...)"
            sleep 10
        fi
    done
    
    # Clean up S3 bucket (optional)
    log_info "Cleaning up temporary S3 bucket..."
    aws s3 rm "s3://${S3_BUCKET_NAME}/driver.jar" --region "$AWS_REGION" 2>/dev/null || true
    aws s3 rb "s3://${S3_BUCKET_NAME}" --region "$AWS_REGION" 2>/dev/null || true
    
    log_success "JDBC driver is now available in EFS at /jdbc-drivers/driver.jar"
}
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
    
    # Load connector configuration from connector-config.properties if it exists
    CONNECTOR_CONFIG_FILE="${SCRIPT_DIR}/connector-config.properties"
    if [[ -f "$CONNECTOR_CONFIG_FILE" ]]; then
        log_info "Loading connector configuration from: $CONNECTOR_CONFIG_FILE"
        
        # Read environment variables from the properties file
        while IFS='=' read -r key value; do
            # Skip comments and empty lines
            [[ "$key" =~ ^#.*$ ]] && continue
            [[ -z "$key" ]] && continue
            
            # Remove leading/trailing whitespace
            key=$(echo "$key" | xargs)
            value=$(echo "$value" | xargs)
            
            # Store in associative array
            case "$key" in
                CONNECTOR_DATASOURCE_TYPE) CONNECTOR_DATASOURCE_TYPE="$value" ;;
                CONNECTOR_LABEL) CONNECTOR_LABEL="$value" ;;
                CONNECTOR_DESCRIPTION) CONNECTOR_DESCRIPTION="$value" ;;
                JDBC_DRIVER_CLASS) JDBC_DRIVER_CLASS="$value" ;;
                JDBC_DRIVER_PATH) JDBC_DRIVER_PATH="$value" ;;
            esac
        done < "$CONNECTOR_CONFIG_FILE"
        
        log_success "Connector configuration loaded"
    else
        log_warning "connector-config.properties not found at: $CONNECTOR_CONFIG_FILE"
        log_warning "Container will start without connector-specific environment variables"
    fi
    
    # Build environment variables JSON array
    ENV_VARS=""
    if [[ -n "$CONNECTOR_DATASOURCE_TYPE" ]]; then
        ENV_VARS="$ENV_VARS{\"name\": \"CONNECTOR_DATASOURCE_TYPE\", \"value\": \"$CONNECTOR_DATASOURCE_TYPE\"},"
    fi
    if [[ -n "$CONNECTOR_LABEL" ]]; then
        ENV_VARS="$ENV_VARS{\"name\": \"CONNECTOR_LABEL\", \"value\": \"$CONNECTOR_LABEL\"},"
    fi
    if [[ -n "$CONNECTOR_DESCRIPTION" ]]; then
        ENV_VARS="$ENV_VARS{\"name\": \"CONNECTOR_DESCRIPTION\", \"value\": \"$CONNECTOR_DESCRIPTION\"},"
    fi
    if [[ -n "$JDBC_DRIVER_CLASS" ]]; then
        ENV_VARS="$ENV_VARS{\"name\": \"JDBC_DRIVER_CLASS\", \"value\": \"$JDBC_DRIVER_CLASS\"},"
    fi
    if [[ -n "$JDBC_DRIVER_PATH" ]]; then
        ENV_VARS="$ENV_VARS{\"name\": \"JDBC_DRIVER_PATH\", \"value\": \"$JDBC_DRIVER_PATH\"},"
    fi
    
    # Remove trailing comma if ENV_VARS is not empty
    if [[ -n "$ENV_VARS" ]]; then
        ENV_VARS="${ENV_VARS%,}"
        ENV_SECTION="\"environment\": [$ENV_VARS],"
        log_info "Adding ${ENV_VARS//,/ } environment variables to container"
    else
        ENV_SECTION=""
    fi
    
    # Build EFS volume configuration if enabled
    if [[ "$ENABLE_EFS" == "true" ]]; then
        MOUNT_POINTS_SECTION="\"mountPoints\": [{\"sourceVolume\": \"jdbc-driver-volume\",\"containerPath\": \"/mnt/efs\",\"readOnly\": false}],"
        VOLUMES_SECTION=",\"volumes\": [{\"name\": \"jdbc-driver-volume\",\"efsVolumeConfiguration\": {\"fileSystemId\": \"$EFS_FILE_SYSTEM_ID\",\"transitEncryption\": \"ENABLED\",\"authorizationConfig\": {\"accessPointId\": \"$EFS_ACCESS_POINT_ID\",\"iam\": \"DISABLED\"}}}]"
        log_info "Adding EFS volume configuration to task definition"
    else
        MOUNT_POINTS_SECTION=""
        VOLUMES_SECTION=""
    fi
    
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
    $ENV_SECTION
    $MOUNT_POINTS_SECTION
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
  }]$VOLUMES_SECTION
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

# Step 5: Create ECS Cluster
create_ecs_cluster() {
    log_step "Step 5: Creating ECS Cluster"
    
    # Check if ECS service-linked role exists, create if not
    log_info "Checking ECS service-linked role..."
    if ! aws iam get-role --role-name AWSServiceRoleForECS &>/dev/null; then
        log_info "Creating ECS service-linked role..."
        aws iam create-service-linked-role --aws-service-name ecs.amazonaws.com 2>/dev/null || \
            log_warning "Service-linked role may already exist or creation failed (this is usually okay)"
    fi
    
    # Check if cluster already exists
    if aws ecs describe-clusters \
        --region "$AWS_REGION" \
        --clusters "$ECS_CLUSTER" \
        --query 'clusters[0].status' \
        --output text 2>/dev/null | grep -q "ACTIVE"; then
        log_warning "ECS cluster '$ECS_CLUSTER' already exists"
    else
        log_info "Creating ECS cluster: $ECS_CLUSTER"
        aws ecs create-cluster \
            --region "$AWS_REGION" \
            --cluster-name "$ECS_CLUSTER" \
            > /dev/null
        
        log_success "ECS cluster created: $ECS_CLUSTER"
    fi
}

# Step 6: Deploy ECS Service
deploy_service() {
    log_step "Step 6: Deploying ECS Service"
    
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

# Step 7: Get Public IP
get_public_ip() {
    log_step "Step 7: Retrieving Public IP Address"
    
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
    
    # Execute deployment steps (including image pull and push)
    pull_docker_image
    create_ecr_repository
    push_image_to_ecr
    create_security_group
    create_efs_filesystem
    upload_driver_to_efs
    create_log_group
    create_iam_role
    register_task_definition
    create_ecs_cluster
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
