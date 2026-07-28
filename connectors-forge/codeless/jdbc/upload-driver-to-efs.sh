#!/bin/bash

################################################################################
# Upload JDBC Driver to EFS - SCP-based Approach
#
# This script launches a temporary EC2 instance, mounts EFS, uploads the driver
# via SCP, and cleans up automatically.
#
# Usage: ./upload-driver-to-efs.sh [path-to-properties-file]
################################################################################

set -e
set -o pipefail

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROPERTIES_FILE="${1:-${SCRIPT_DIR}/aws-deployment.properties}"

log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
log_warning() { echo -e "${YELLOW}[WARNING]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_step() { echo -e "\n${GREEN}==>${NC} ${BLUE}$1${NC}\n"; }

# Load properties
load_properties() {
    if [[ ! -f "$PROPERTIES_FILE" ]]; then
        log_error "Properties file not found: $PROPERTIES_FILE"
        exit 1
    fi
    
    log_info "Loading configuration from: $PROPERTIES_FILE"
    
    while IFS='=' read -r key value; do
        [[ "$key" =~ ^#.*$ ]] && continue
        [[ -z "$key" ]] && continue
        key=$(echo "$key" | xargs)
        value=$(echo "$value" | xargs)
        export "$key=$value"
    done < "$PROPERTIES_FILE"
    
    log_success "Configuration loaded"
}

# Set defaults
set_defaults() {
    EFS_NAME="${EFS_NAME:-jdbc-connector-efs}"
    INSTANCE_TYPE="${INSTANCE_TYPE:-t2.micro}"
    KEY_PAIR_NAME="${KEY_PAIR_NAME:-temp-efs-upload-key}"
    
    # Get latest Amazon Linux 2023 AMI
    AMI_ID=$(aws ec2 describe-images \
        --region "$AWS_REGION" \
        --owners amazon \
        --filters "Name=name,Values=al2023-ami-2023.*-x86_64" \
        --query 'Images | sort_by(@, &CreationDate) | [-1].ImageId' \
        --output text)
    
    log_info "Using AMI: $AMI_ID"
}

# Create temporary SSH key pair
create_key_pair() {
    log_info "Creating temporary SSH key pair..."
    
    TEMP_KEY_FILE="/tmp/${KEY_PAIR_NAME}-${RANDOM}.pem"
    
    # Delete key pair if it exists
    aws ec2 delete-key-pair \
        --region "$AWS_REGION" \
        --key-name "$KEY_PAIR_NAME" 2>/dev/null || true
    
    # Create new key pair
    aws ec2 create-key-pair \
        --region "$AWS_REGION" \
        --key-name "$KEY_PAIR_NAME" \
        --query 'KeyMaterial' \
        --output text > "$TEMP_KEY_FILE"
    
    chmod 400 "$TEMP_KEY_FILE"
    log_success "SSH key pair created: $TEMP_KEY_FILE"
}

# Main upload function
upload_driver() {
    log_step "Uploading JDBC Driver to EFS via SCP"
    
    # Check driver exists
    DRIVER_FILE="${SCRIPT_DIR}/driver/driver.jar"
    if [[ ! -f "$DRIVER_FILE" ]]; then
        log_error "JDBC driver not found at: $DRIVER_FILE"
        exit 1
    fi
    log_info "Found driver: $DRIVER_FILE"
    
    # Get EFS details
    log_info "Retrieving EFS file system details..."
    EFS_FILE_SYSTEM_ID=$(aws efs describe-file-systems \
        --region "$AWS_REGION" \
        --query "FileSystems[?Name=='$EFS_NAME'].FileSystemId" \
        --output text)
    
    if [[ -z "$EFS_FILE_SYSTEM_ID" || "$EFS_FILE_SYSTEM_ID" == "None" ]]; then
        log_error "EFS file system '$EFS_NAME' not found"
        exit 1
    fi
    log_success "Found EFS: $EFS_FILE_SYSTEM_ID"
    
    # Get EFS security group
    EFS_SG_ID=$(aws ec2 describe-security-groups \
        --region "$AWS_REGION" \
        --filters "Name=group-name,Values=${EFS_NAME}-sg" "Name=vpc-id,Values=$VPC_ID" \
        --query 'SecurityGroups[0].GroupId' \
        --output text)
    
    if [[ -z "$EFS_SG_ID" || "$EFS_SG_ID" == "None" ]]; then
        log_error "EFS security group not found"
        exit 1
    fi
    log_success "Found EFS security group: $EFS_SG_ID"
    
    # Create temporary security group for EC2 instance
    log_info "Creating temporary security group for EC2..."
    TEMP_SG_NAME="temp-efs-upload-$(date +%s)"
    TEMP_SG_ID=$(aws ec2 create-security-group \
        --region "$AWS_REGION" \
        --group-name "$TEMP_SG_NAME" \
        --description "Temporary SG for EFS driver upload" \
        --vpc-id "$VPC_ID" \
        --query 'GroupId' \
        --output text)
    log_success "Created temporary security group: $TEMP_SG_ID"
    
    # Allow SSH from your IP
    MY_IP=$(curl -s https://checkip.amazonaws.com)
    log_info "Allowing SSH access from your IP: $MY_IP"
    aws ec2 authorize-security-group-ingress \
        --region "$AWS_REGION" \
        --group-id "$TEMP_SG_ID" \
        --protocol tcp \
        --port 22 \
        --cidr "${MY_IP}/32"
    
    # Update EFS security group to allow NFS from temp SG
    log_info "Updating EFS security group to allow NFS access..."
    aws ec2 authorize-security-group-ingress \
        --region "$AWS_REGION" \
        --group-id "$EFS_SG_ID" \
        --protocol tcp \
        --port 2049 \
        --source-group "$TEMP_SG_ID" 2>/dev/null || log_warning "Rule may already exist"
    
    # Create user data script to mount EFS
    cat > /tmp/user-data.sh << 'USERDATA'
#!/bin/bash
set -e

# Install required packages
yum install -y amazon-efs-utils nfs-utils

# Create mount point
mkdir -p /mnt/efs

# Mount EFS (EFS_FILE_SYSTEM_ID will be substituted)
mount -t efs -o tls EFS_FILE_SYSTEM_ID:/ /mnt/efs

# Signal mount completion
touch /tmp/mount-complete
echo "EFS mounted successfully"
USERDATA

# Substitute EFS_FILE_SYSTEM_ID in user data (cross-platform compatible)
sed -i.bak "s/EFS_FILE_SYSTEM_ID/${EFS_FILE_SYSTEM_ID}/g" /tmp/user-data.sh
rm -f /tmp/user-data.sh.bak
    
    # Create SSH key pair
    create_key_pair
    
    # Launch EC2 instance
    log_info "Launching temporary EC2 instance..."
    INSTANCE_ID=$(aws ec2 run-instances \
        --region "$AWS_REGION" \
        --image-id "$AMI_ID" \
        --instance-type "$INSTANCE_TYPE" \
        --subnet-id "$SUBNET_ID" \
        --security-group-ids "$TEMP_SG_ID" \
        --key-name "$KEY_PAIR_NAME" \
        --user-data file:///tmp/user-data.sh \
        --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=temp-efs-upload}]" \
        --associate-public-ip-address \
        --query 'Instances[0].InstanceId' \
        --output text)
    
    log_success "Instance launched: $INSTANCE_ID"
    
    # Wait for instance to be running
    log_info "Waiting for instance to be running..."
    aws ec2 wait instance-running --region "$AWS_REGION" --instance-ids "$INSTANCE_ID"
    log_success "Instance is running"
    
    # Get public IP
    log_info "Getting instance public IP..."
    PUBLIC_IP=$(aws ec2 describe-instances \
        --region "$AWS_REGION" \
        --instance-ids "$INSTANCE_ID" \
        --query 'Reservations[0].Instances[0].PublicIpAddress' \
        --output text)
    
    if [[ -z "$PUBLIC_IP" || "$PUBLIC_IP" == "None" ]]; then
        log_error "Could not get public IP for instance"
        cleanup_resources
        exit 1
    fi
    log_success "Instance public IP: $PUBLIC_IP"
    
    # Wait for SSH to be ready and EFS to mount (with polling)
    log_info "Waiting for SSH to be ready and EFS to mount..."
    SSH_OPTIONS="-i $TEMP_KEY_FILE -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o LogLevel=ERROR -o ConnectTimeout=5"
    
    MAX_ATTEMPTS=30
    ATTEMPT=0
    while [[ $ATTEMPT -lt $MAX_ATTEMPTS ]]; do
        ATTEMPT=$((ATTEMPT + 1))
        
        # Check if SSH is ready and EFS is mounted
        if ssh $SSH_OPTIONS "ec2-user@${PUBLIC_IP}" "test -f /tmp/mount-complete" 2>/dev/null; then
            log_success "SSH ready and EFS mounted (took $((ATTEMPT * 2)) seconds)"
            break
        fi
        
        if [[ $ATTEMPT -eq $MAX_ATTEMPTS ]]; then
            log_error "Timeout waiting for SSH/EFS mount after $((MAX_ATTEMPTS * 2)) seconds"
            cleanup_resources
            exit 1
        fi
        
        echo -n "."
        sleep 2
    done
    echo ""
    
    # Upload driver via SCP
    log_info "Uploading driver via SCP to $PUBLIC_IP..."
    
    # SCP options (reuse SSH_OPTIONS for consistency)
    SCP_OPTIONS="-i $TEMP_KEY_FILE -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o LogLevel=ERROR"
    
    # Copy driver to EC2 instance with retry logic
    MAX_RETRIES=3
    RETRY=0
    while [[ $RETRY -lt $MAX_RETRIES ]]; do
        RETRY=$((RETRY + 1))
        
        if scp $SCP_OPTIONS "$DRIVER_FILE" "ec2-user@${PUBLIC_IP}:/tmp/driver.jar"; then
            log_success "Driver copied to EC2 instance"
            break
        else
            if [[ $RETRY -lt $MAX_RETRIES ]]; then
                log_warning "Upload attempt $RETRY failed, retrying in 5 seconds..."
                sleep 5
            else
                log_error "Failed to copy driver via SCP after $MAX_RETRIES attempts"
                cleanup_resources
                exit 1
            fi
        fi
    done
    
    # Move driver to EFS via SSH with verification
    log_info "Moving driver to EFS mount point..."
    
    if ssh $SSH_OPTIONS "ec2-user@${PUBLIC_IP}" "sudo cp /tmp/driver.jar /mnt/efs/ && sudo chmod 644 /mnt/efs/driver.jar && ls -lh /mnt/efs/driver.jar"; then
        # Verify file size matches
        REMOTE_SIZE=$(ssh $SSH_OPTIONS "ec2-user@${PUBLIC_IP}" "stat -c%s /mnt/efs/driver.jar" 2>/dev/null || echo "0")
        LOCAL_SIZE=$(stat -f%z "$DRIVER_FILE" 2>/dev/null || stat -c%s "$DRIVER_FILE" 2>/dev/null || echo "0")
        
        if [[ "$REMOTE_SIZE" == "$LOCAL_SIZE" ]]; then
            log_success "Driver successfully uploaded to EFS at /driver.jar ($(numfmt --to=iec-i --suffix=B $LOCAL_SIZE 2>/dev/null || echo "${LOCAL_SIZE} bytes"))"
        else
            log_warning "File uploaded but size mismatch (local: $LOCAL_SIZE, remote: $REMOTE_SIZE)"
        fi
    else
        log_error "Failed to move driver to EFS"
        cleanup_resources
        exit 1
    fi
    
    # Cleanup
    cleanup_resources
}

# Cleanup function
cleanup_resources() {
    log_info "Cleaning up temporary resources..."
    
    # Terminate instance and wait asynchronously
    if [[ -n "$INSTANCE_ID" ]]; then
        aws ec2 terminate-instances --region "$AWS_REGION" --instance-ids "$INSTANCE_ID" > /dev/null 2>&1 || true
        log_info "Instance termination initiated: $INSTANCE_ID"
        
        # Start async wait for termination (don't block cleanup)
        (aws ec2 wait instance-terminated --region "$AWS_REGION" --instance-ids "$INSTANCE_ID" 2>/dev/null &)
    fi
    
    # Brief wait for instance to start terminating
    sleep 5
    
    # Remove security group rule from EFS SG
    if [[ -n "$EFS_SG_ID" && -n "$TEMP_SG_ID" ]]; then
        aws ec2 revoke-security-group-ingress \
            --region "$AWS_REGION" \
            --group-id "$EFS_SG_ID" \
            --protocol tcp \
            --port 2049 \
            --source-group "$TEMP_SG_ID" 2>/dev/null || true
    fi
    
    # Delete temporary security group
    if [[ -n "$TEMP_SG_ID" ]]; then
        aws ec2 delete-security-group --region "$AWS_REGION" --group-id "$TEMP_SG_ID" 2>/dev/null || \
            log_warning "Security group will be deleted after instance termination completes"
    fi
    
    # Delete SSH key pair
    if [[ -n "$KEY_PAIR_NAME" ]]; then
        aws ec2 delete-key-pair --region "$AWS_REGION" --key-name "$KEY_PAIR_NAME" 2>/dev/null || true
        log_info "SSH key pair deleted"
    fi
    
    # Delete temporary key file
    if [[ -n "$TEMP_KEY_FILE" && -f "$TEMP_KEY_FILE" ]]; then
        rm -f "$TEMP_KEY_FILE"
        log_info "Temporary key file deleted"
    fi
    
    # Cleanup temp files
    rm -f /tmp/user-data.sh
    
    log_success "Cleanup completed"
}

# Main
main() {
    echo -e "${BLUE}"
    echo "╔════════════════════════════════════════════════════════════════╗"
    echo "║        Upload JDBC Driver to EFS (SCP-based Method)           ║"
    echo "╚════════════════════════════════════════════════════════════════╝"
    echo -e "${NC}"
    
    load_properties
    set_defaults
    upload_driver
    
    log_success "Upload completed successfully!"
}

# Trap errors and cleanup
trap 'cleanup_resources' EXIT ERR

main "$@"

# Made with Bob
