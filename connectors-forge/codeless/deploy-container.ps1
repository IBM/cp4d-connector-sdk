param(
    [string]$DockerHost,
    [string[]]$Files,
    [int]$Port,
    [switch]$Replace
)

# ============================================
# Configuration
# ============================================

$Image = "docker.io/marek02/connectors-forge:latest"
$ContainerId = $null
$ContainerName = $null
$TempDir = $null
$TempArchive = $null

# ============================================
# Validation
# ============================================

Write-Host ""
Write-Host "[1/8] Validating inputs..."

if (-not $DockerHost) {
    throw "Missing --host"
}
if (-not $Files -or $Files.Count -eq 0) {
    throw "Missing --files"
}
if (-not $Port) {
    throw "Missing --port"
}

foreach ($file in $Files) {
    if (-not (Test-Path $file)) {
        throw "File not found: $file"
    }
}

if ($Port -lt 1 -or $Port -gt 65535) {
    throw "Invalid port: $Port"
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$ContainerName = "connector-$timestamp"

Write-Host "Container name: $ContainerName"

# Test Docker API
try {
    $resp = Invoke-RestMethod "http://$DockerHost/version"
} catch {
    throw "Cannot connect to Docker API at $DockerHost"
}

Write-Host "Docker API accessible"

# ============================================
# [2/8] Existing container
# ============================================

Write-Host ""
Write-Host "[2/8] Checking for existing containers..."

$containers = Invoke-RestMethod "http://$DockerHost/containers/json?all=true"

# Check by name
$existing = $containers | Where-Object {
    $_.Names -contains "/$ContainerName"
}

# Check by port
$portConflict = $containers | Where-Object {
    $_.Ports | Where-Object { $_.PublicPort -eq $Port }
}

function Stop-And-Remove($id) {
    Write-Host "Stopping container..."
    Invoke-RestMethod -Method Post "http://$DockerHost/containers/$id/stop" -ErrorAction SilentlyContinue
    Write-Host "Removing container..."
    Invoke-RestMethod -Method Delete "http://$DockerHost/containers/$id"
}

if ($existing) {
    Write-Host "Container exists: $($existing.Id)"
    if ($Replace) {
        Stop-And-Remove $existing.Id
    } else {
        throw "Container exists. Use --Replace"
    }
}
elseif ($portConflict) {
    Write-Host "Port $Port already in use by $($portConflict.Id)"
    if ($Replace) {
        Stop-And-Remove $portConflict.Id
    } else {
        throw "Port in use. Use --Replace"
    }
}
else {
    Write-Host "No conflicts"
}

# ============================================
# [3/8] Create archive
# ============================================

Write-Host ""
Write-Host "[3/8] Creating tar archive..."

$TempDir = Join-Path $env:TEMP ("connector-deploy-" + (Get-Random))
New-Item -ItemType Directory -Path $TempDir | Out-Null
New-Item -ItemType Directory -Path "$TempDir/mappings" | Out-Null

foreach ($file in $Files) {
    Copy-Item $file -Destination "$TempDir/mappings/"
}

$TempArchive = "$TempDir/config.tar"

# tar in PowerShell (Windows 10+)
tar -cf $TempArchive -C $TempDir mappings

if (-not (Test-Path $TempArchive)) {
    throw "Failed to create archive"
}

Write-Host "Archive created"

# ============================================
# [4/8] Pull image
# ============================================

Write-Host ""
Write-Host "[4/8] Pulling image..."

Invoke-RestMethod -Method Post "http://$DockerHost/images/create?fromImage=$Image" -ErrorAction SilentlyContinue

Write-Host "Image ready"

# ============================================
# [5/8] Create container
# ============================================

Write-Host ""
Write-Host "[5/8] Creating container..."

$body = @{
    Image = $Image
    ExposedPorts = @{
        "9443/tcp" = @{}
    }
    HostConfig = @{
        PortBindings = @{
            "9443/tcp" = @(
                @{ HostPort = "$Port" }
            )
        }
    }
} | ConvertTo-Json -Depth 5

$response = Invoke-RestMethod -Method Post `
    -Uri "http://$DockerHost/containers/create?name=$ContainerName" `
    -ContentType "application/json" `
    -Body $body

$ContainerId = $response.Id

if (-not $ContainerId) {
    throw "Failed to create container"
}

Write-Host "Container created: $($ContainerId.Substring(0,12))"

# ============================================
# [6/8] Upload files
# ============================================

Write-Host ""
Write-Host "[6/8] Uploading config files..."

$bytes = [System.IO.File]::ReadAllBytes($TempArchive)

Invoke-RestMethod -Method Put `
    -Uri "http://$DockerHost/containers/$ContainerId/archive?path=/config" `
    -ContentType "application/x-tar" `
    -Body $bytes

Write-Host "Files uploaded"

# ============================================
# [7/8] Start container
# ============================================

Write-Host ""
Write-Host "[7/8] Starting container..."

Invoke-RestMethod -Method Post "http://$DockerHost/containers/$ContainerId/start"

Write-Host "Container started"

# ============================================
# [8/8] Verify
# ============================================

Write-Host ""
Write-Host "[8/8] Verifying..."

Start-Sleep -Seconds 2

$status = Invoke-RestMethod "http://$DockerHost/containers/$ContainerId/json"

if (-not $status.State.Running) {
    throw "Container not running"
}

Write-Host "Container is running"

# ============================================
# SUCCESS
# ============================================

Write-Host ""
Write-Host "=========================================="
Write-Host "Deployment Successful!"
Write-Host "=========================================="
Write-Host "Container ID:   $($ContainerId.Substring(0,12))"
Write-Host "Container Name: $ContainerName"
Write-Host "Status:         Running"
Write-Host "=========================================="

# ============================================
# Cleanup
# ============================================

if ($TempDir -and (Test-Path $TempDir)) {
    Remove-Item -Recurse -Force $TempDir
}