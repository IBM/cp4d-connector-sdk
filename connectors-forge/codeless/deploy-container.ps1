param(
    [Parameter(Mandatory=$true)]
    [string]$SshHost,
    
    [Parameter(Mandatory=$true)]
    [string]$SshUser,
    
    [Parameter(Mandatory=$true)]
    [string[]]$Files,
    
    [Parameter(Mandatory=$true)]
    [int]$Port,
    
    [int]$SshPort = 22,
    
    [switch]$Replace
)

# ============================================
# Configuration & Global Variables
# ============================================

$Image = "ghcr.io/marek-zuwala/connectors-forge:1.0.3"
$ContainerId = $null
$ContainerName = $null
$TempDir = $null

# ============================================
# Helper Functions
# ============================================

function Write-Log {
    param([string]$Message)
    Write-Host $Message
}

function Write-LogError {
    param([string]$Message)
    Write-Host "ERROR: $Message"
}

function Write-LogStep {
    param([string]$Step, [string]$Message)
    Write-Host ""
    Write-Host "[$Step] $Message"
}

# Execute command via SSH
function Invoke-SshCommand {
    param([string]$Command)
    
    $sshArgs = @(
        "-p", $SshPort,
        "-o", "StrictHostKeyChecking=no",
        "-o", "ConnectTimeout=10",
        "-o", "BatchMode=yes",
        "${SshUser}@${SshHost}",
        $Command
    )
    
    $result = & ssh $sshArgs 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "SSH command failed: $result"
    }
    return $result
}

# ============================================
# Cleanup Handler
# ============================================

function Cleanup {
    if ($TempDir -and (Test-Path $TempDir)) {
        Remove-Item -Recurse -Force $TempDir -ErrorAction SilentlyContinue
    }
}

# ============================================
# Error Handler
# ============================================

function Exit-WithError {
    param([string]$Message)
    
    Write-LogError $Message
    
    # If container was created but deployment failed, try to remove it
    if ($ContainerId) {
        Write-Log "Cleaning up failed deployment..."
        try {
            Invoke-SshCommand "docker rm -f $ContainerId" | Out-Null
        } catch {
            # Ignore cleanup errors
        }
    }
    
    Cleanup
    exit 1
}

# ============================================
# Validation Functions
# ============================================

function Test-Inputs {
    Write-LogStep "1/7" "Validating inputs..."
    
    # Test SSH connectivity and Docker availability
    Write-Log "Testing SSH connectivity and Docker availability..."
    try {
        Invoke-SshCommand "docker version" | Out-Null
        Write-Log "SSH connection and Docker verified"
    } catch {
        Exit-WithError "Cannot connect via SSH or Docker not available on ${SshUser}@${SshHost}:${SshPort}"
    }
    
    # Check if all config files exist and validate JSON
    $fileCount = 0
    foreach ($file in $Files) {
        if (-not (Test-Path $file)) {
            Exit-WithError "File not found: $file"
        }
        
        # Validate JSON syntax and metadata
        try {
            $jsonContent = Get-Content $file -Raw | ConvertFrom-Json
            
            # Check for required metadata fields
            if (-not $jsonContent.'$metadata') {
                Write-Host "WARNING: File $file is missing metadata section" -ForegroundColor Yellow
            } else {
                if (-not $jsonContent.'$metadata'.connector_source) {
                    Write-Host "WARNING: File $file is missing 'connector_source' metadata field" -ForegroundColor Yellow
                }
                if (-not $jsonContent.'$metadata'.target_service) {
                    Write-Host "WARNING: File $file is missing 'target_service' metadata field" -ForegroundColor Yellow
                }
                if (-not $jsonContent.'$metadata'.connector_type) {
                    Write-Host "WARNING: File $file is missing 'connector_type' metadata field" -ForegroundColor Yellow
                }
            }
        } catch {
            Exit-WithError "Invalid JSON in file: $file - $_"
        }
        
        $fileCount++
    }
    Write-Log "All $fileCount file(s) validated"
    
    # Validate port number
    if ($Port -lt 1 -or $Port -gt 65535) {
        Exit-WithError "Invalid port number: $Port (must be 1-65535)"
    }
    Write-Log "Port number valid"
    
    # Generate timestamp-based container name
    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $script:ContainerName = "connector-$timestamp"
    Write-Log "Container name: $ContainerName"
}

# ============================================
# Container Management Functions
# ============================================

function Test-ContainerExists {
    param([string]$Name)
    
    try {
        $result = Invoke-SshCommand "docker ps -aq --filter 'name=^${Name}$'"
        if ($result) {
            return $result.Trim()
        }
        return $null
    } catch {
        return $null
    }
}

function Find-ContainerByPort {
    param([int]$PortNumber)
    
    try {
        $result = Invoke-SshCommand "docker ps -a --filter 'publish=${PortNumber}' --format '{{.ID}}'"
        if ($result) {
            return $result.Trim()
        }
        return $null
    } catch {
        return $null
    }
}

function Get-ContainerName {
    param([string]$Id)
    
    try {
        $result = Invoke-SshCommand "docker inspect $Id --format '{{.Name}}'"
        return $result.Trim().TrimStart('/')
    } catch {
        return "unknown"
    }
}

function Stop-AndRemoveContainer {
    param([string]$Id)
    
    Write-Log "Stopping and removing container..."
    try {
        Invoke-SshCommand "docker stop $Id && docker rm $Id" | Out-Null
        Write-Log "Container removed"
        return $true
    } catch {
        Write-Log "Failed to remove container"
        return $false
    }
}

function Test-ExistingContainer {
    Write-LogStep "2/7" "Checking for existing containers..."
    
    # First check by container name
    $existingId = Test-ContainerExists $ContainerName
    
    if ($existingId) {
        Write-Log "Container '$ContainerName' already exists (ID: $($existingId.Substring(0, [Math]::Min(12, $existingId.Length))))"
        
        if ($Replace) {
            if (-not (Stop-AndRemoveContainer $existingId)) {
                Exit-WithError "Failed to remove existing container"
            }
        } else {
            Exit-WithError "Container already exists. Use -Replace to replace it."
        }
    } else {
        # Check if any container is using the same port
        Write-Log "Checking for containers using port $Port..."
        $portConflictId = Find-ContainerByPort $Port
        
        if ($portConflictId) {
            $containerName = Get-ContainerName $portConflictId
            Write-Log "Found container '$containerName' using port $Port (ID: $($portConflictId.Substring(0, [Math]::Min(12, $portConflictId.Length))))"
            
            if ($Replace) {
                Write-Log "Replacing container using port $Port..."
                if (-not (Stop-AndRemoveContainer $portConflictId)) {
                    Exit-WithError "Failed to remove container using port $Port"
                }
            } else {
                Exit-WithError "Port $Port is already in use by container '$containerName'. Use different port or -Replace to replace existing container (WARNING: This will remove the container '$containerName')"
            }
        } else {
            Write-Log "No existing container found, port $Port is available"
        }
    }
}

# ============================================
# Container Creation
# ============================================

function New-Container {
    Write-LogStep "3/7" "Creating container..."
    
    # Create container with port mapping (Docker will pull image if needed)
    Write-Log "Creating container from image: $Image"
    try {
        $script:ContainerId = Invoke-SshCommand "docker create --name $ContainerName -p ${Port}:9443 $Image"
        $script:ContainerId = $ContainerId.Trim()
    } catch {
        Exit-WithError "Failed to create container: $_"
    }
    
    if (-not $ContainerId) {
        Exit-WithError "Failed to create container"
    }
    
    Write-Log "Container created: $($ContainerId.Substring(0, [Math]::Min(12, $ContainerId.Length)))"
}

# ============================================
# File Upload
# ============================================

function Send-ConfigFiles {
    Write-LogStep "4/7" "Uploading config files..."
    
    # Create temporary directory for staging files
    $script:TempDir = Join-Path $env:TEMP ("connector-deploy-" + (Get-Random))
    New-Item -ItemType Directory -Path $TempDir | Out-Null
    New-Item -ItemType Directory -Path "$TempDir\mappings" | Out-Null
    
    # Copy all config files to temp mappings directory
    Write-Log "Preparing $($Files.Count) file(s) for upload..."
    foreach ($file in $Files) {
        Copy-Item $file -Destination "$TempDir\mappings\"
    }
    
    # Create tar archive and stream it via SSH to docker cp
    try {
        # Create tar archive
        $tarFile = "$TempDir\config.tar"
        
        # Use tar command (available in Windows 10+ and PowerShell 7+)
        Push-Location $TempDir
        try {
            & tar -cf config.tar mappings 2>&1 | Out-Null
            if ($LASTEXITCODE -ne 0) {
                throw "Failed to create tar archive"
            }
        } finally {
            Pop-Location
        }
        
        # Stream tar to remote docker cp via SSH
        $sshArgs = @(
            "-p", $SshPort,
            "-o", "StrictHostKeyChecking=no",
            "-o", "ConnectTimeout=10",
            "-o", "BatchMode=yes",
            "${SshUser}@${SshHost}",
            "docker cp - ${ContainerId}:/config"
        )
        
        Get-Content $tarFile -Raw -AsByteStream | & ssh $sshArgs
        
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to upload files"
        }
        
        Write-Log "Files uploaded to /config/mappings/"
    } catch {
        Exit-WithError "Failed to upload files. The /config directory may not exist in the container. Error: $_"
    }
}

# ============================================
# Container Start
# ============================================

function Start-Container {
    Write-LogStep "5/7" "Starting container..."
    
    try {
        Invoke-SshCommand "docker start $ContainerId" | Out-Null
        Write-Log "Container started"
    } catch {
        Exit-WithError "Failed to start container: $_"
    }
}

# ============================================
# Container Verification
# ============================================

function Test-ContainerRunning {
    Write-LogStep "6/7" "Verifying container status..."
    
    Start-Sleep -Seconds 2
    
    try {
        $running = Invoke-SshCommand "docker inspect $ContainerId --format '{{.State.Running}}'"
        $running = $running.Trim()
        
        if ($running -eq "true") {
            Write-Log "Container is running"
        } else {
            $status = Invoke-SshCommand "docker inspect $ContainerId --format '{{.State.Status}}'"
            $status = $status.Trim()
            if (-not $status) { $status = "unknown" }
            Exit-WithError "Container is not running (Status: $status)"
        }
    } catch {
        Exit-WithError "Failed to verify container status: $_"
    }
}

# ============================================
# Main Execution
# ============================================

function Main {
    Write-Host "=========================================="
    Write-Host "Container Deployment Script (SSH)"
    Write-Host "=========================================="
    Write-Host "SSH Host:       ${SshUser}@${SshHost}:${SshPort}"
    Write-Host "Files:          $($Files.Count) file(s)"
    foreach ($file in $Files) {
        Write-Host "                - $(Split-Path -Leaf $file)"
    }
    Write-Host "Image:          $Image"
    Write-Host "Port:           $Port"
    if ($Replace) {
        Write-Host "Replace Mode:   Enabled"
    }
    Write-Host "=========================================="
    
    try {
        Test-Inputs
        Test-ExistingContainer
        New-Container
        Send-ConfigFiles
        Start-Container
        Test-ContainerRunning
        
        Write-Host ""
        Write-Host "=========================================="
        Write-Host "Deployment Successful!"
        Write-Host "=========================================="
        Write-Host "Container ID:   $($ContainerId.Substring(0, [Math]::Min(12, $ContainerId.Length)))"
        Write-Host "Container Name: $ContainerName"
        Write-Host "Status:         Running"
        Write-Host "Port Mapping:   ${Port}:9443"
        Write-Host "=========================================="
    } finally {
        Cleanup
    }
}

# ============================================
# Script Entry Point
# ============================================

Main

# Made with Bob