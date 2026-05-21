# OpenShift Deployment Script (PowerShell)
# Deploys REST API connector(s) to OpenShift using pre-built Docker image

param(
    [switch]$Help
)

# ============================================
# Configuration & Global Variables
# ============================================

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$PropertiesFile = Join-Path $ScriptDir "deploy-openshift.properties"

# Hardcoded configuration
$DockerImage = "ghcr.io/marek-zuwala/connectors-forge:1.0.3"
$ConfigFilesPath = "sdk-gen/subprojects/connectors_forge_rest/src/main/resources"
$DeploymentYaml = Join-Path $ScriptDir "deployment.yaml"
$ServiceYaml = Join-Path $ScriptDir "service.yaml"
$ConfigMapName = "connectors-config"

# Variables from properties file
$OpenShiftClusterUrl = ""
$OpenShiftUsername = ""
$OpenShiftPassword = ""
$OpenShiftToken = ""
$OpenShiftProject = ""
$CreateProjectIfNotExists = "true"
$RecreateConfigMap = "true"

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

# ============================================
# Usage & Help
# ============================================

function Show-Usage {
    Write-Host @"
Usage: .\deploy-openshift.ps1

Deploy REST API connector(s) to OpenShift using pre-built Docker image.

Prerequisites:
  - OpenShift CLI (oc) must be installed
  - Configuration file must exist: $PropertiesFile
  - Connector configuration files must exist in: $ConfigFilesPath

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

"@
    exit 0
}

# ============================================
# Properties File Loading
# ============================================

function Load-Properties {
    Write-LogStep "1/7" "Loading configuration from $PropertiesFile..."
    
    if (-not (Test-Path $PropertiesFile)) {
        Write-LogError "Properties file not found: $PropertiesFile"
        Write-Log "Please copy deploy-openshift.properties.template to deploy-openshift.properties"
        Write-Log "and fill in your configuration."
        exit 1
    }
    
    # Load properties file (ignore comments and empty lines)
    Get-Content $PropertiesFile | ForEach-Object {
        $line = $_.Trim()
        
        # Skip comments and empty lines
        if ($line -match '^#' -or $line -eq '') {
            return
        }
        
        # Parse key=value
        if ($line -match '^([^=]+)=(.*)$') {
            $key = $matches[1].Trim()
            $value = $matches[2].Trim()
            
            switch ($key) {
                'OPENSHIFT_CLUSTER_URL' { $script:OpenShiftClusterUrl = $value }
                'OPENSHIFT_USERNAME' { $script:OpenShiftUsername = $value }
                'OPENSHIFT_PASSWORD' { $script:OpenShiftPassword = $value }
                'OPENSHIFT_TOKEN' { $script:OpenShiftToken = $value }
                'OPENSHIFT_PROJECT' { $script:OpenShiftProject = $value }
                'CREATE_PROJECT_IF_NOT_EXISTS' { $script:CreateProjectIfNotExists = $value }
                'RECREATE_CONFIGMAP' { $script:RecreateConfigMap = $value }
            }
        }
    }
    
    # Validate required properties
    if ([string]::IsNullOrWhiteSpace($OpenShiftClusterUrl)) {
        Write-LogError "OPENSHIFT_CLUSTER_URL is required in properties file"
        exit 1
    }
    
    if ([string]::IsNullOrWhiteSpace($OpenShiftProject)) {
        Write-LogError "OPENSHIFT_PROJECT is required in properties file"
        exit 1
    }
    
    # Check authentication method
    if ([string]::IsNullOrWhiteSpace($OpenShiftToken) -and 
        ([string]::IsNullOrWhiteSpace($OpenShiftUsername) -or [string]::IsNullOrWhiteSpace($OpenShiftPassword))) {
        Write-LogError "Either OPENSHIFT_TOKEN or both OPENSHIFT_USERNAME and OPENSHIFT_PASSWORD are required"
        exit 1
    }
    
    Write-Log "Configuration loaded successfully"
}

# ============================================
# OpenShift CLI Check
# ============================================

function Test-OcCli {
    try {
        $null = Get-Command oc -ErrorAction Stop
    }
    catch {
        Write-LogError "OpenShift CLI (oc) is not installed or not in PATH"
        Write-Log "Please install the OpenShift CLI: https://docs.openshift.com/container-platform/latest/cli_reference/openshift_cli/getting-started-cli.html"
        exit 1
    }
}

# ============================================
# OpenShift Login
# ============================================

function Connect-OpenShift {
    Write-LogStep "2/7" "Logging in to OpenShift cluster..."
    
    try {
        if (-not [string]::IsNullOrWhiteSpace($OpenShiftToken)) {
            Write-Log "Using token authentication"
            $output = oc login $OpenShiftClusterUrl --token=$OpenShiftToken --insecure-skip-tls-verify=true 2>&1
            if ($LASTEXITCODE -ne 0) {
                throw "Login failed"
            }
        }
        else {
            Write-Log "Using username/password authentication"
            $output = oc login $OpenShiftClusterUrl -u $OpenShiftUsername -p $OpenShiftPassword --insecure-skip-tls-verify=true 2>&1
            if ($LASTEXITCODE -ne 0) {
                throw "Login failed"
            }
        }
        
        Write-Log "Successfully logged in to OpenShift"
    }
    catch {
        Write-LogError "Failed to login to OpenShift: $_"
        exit 1
    }
}

# ============================================
# Project/Namespace Management
# ============================================

function Initialize-Project {
    Write-LogStep "3/7" "Setting up project/namespace..."
    
    # Check if project exists (suppress all output)
    $ErrorActionPreference = "SilentlyContinue"
    $projectExists = $false
    $null = oc get project $OpenShiftProject 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        $projectExists = $true
    }
    $ErrorActionPreference = "Stop"
    
    if ($projectExists) {
        Write-Log "Project '$OpenShiftProject' exists"
        $null = oc project $OpenShiftProject 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) {
            Write-LogError "Failed to switch to project '$OpenShiftProject'"
            exit 1
        }
        Write-Log "Switched to project '$OpenShiftProject'"
    }
    else {
        if ($CreateProjectIfNotExists -eq "true") {
            Write-Log "Project '$OpenShiftProject' does not exist, creating it..."
            $output = oc new-project $OpenShiftProject 2>&1
            if ($LASTEXITCODE -ne 0) {
                Write-LogError "Failed to create project '$OpenShiftProject'"
                exit 1
            }
            Write-Log "Project '$OpenShiftProject' created successfully"
        }
        else {
            Write-LogError "Project '$OpenShiftProject' does not exist and CREATE_PROJECT_IF_NOT_EXISTS is false"
            exit 1
        }
    }
}

# ============================================
# ConfigMap Creation
# ============================================

function New-ConfigMap {
    Write-LogStep "4/7" "Creating ConfigMap from connector configuration files..."
    
    # Check if config files directory exists
    if (-not (Test-Path $ConfigFilesPath)) {
        Write-LogError "Configuration files directory not found: $ConfigFilesPath"
        exit 1
    }
    
    # Count JSON files
    $jsonFiles = Get-ChildItem -Path $ConfigFilesPath -Filter "*.json" -File
    $jsonCount = $jsonFiles.Count
    
    if ($jsonCount -eq 0) {
        Write-LogError "No JSON configuration files found in: $ConfigFilesPath"
        exit 1
    }
    
    Write-Log "Found $jsonCount connector configuration file(s)"
    
    # Check if ConfigMap already exists (suppress all output)
    $ErrorActionPreference = "SilentlyContinue"
    $configMapExists = $false
    $null = oc get configmap $ConfigMapName 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        $configMapExists = $true
    }
    $ErrorActionPreference = "Stop"
    
    if ($configMapExists) {
        if ($RecreateConfigMap -eq "true") {
            Write-Log "ConfigMap '$ConfigMapName' already exists, deleting it..."
            $null = oc delete configmap $ConfigMapName 2>&1
            if ($LASTEXITCODE -ne 0) {
                Write-LogError "Failed to delete ConfigMap '$ConfigMapName'"
                exit 1
            }
            Write-Log "ConfigMap deleted"
        }
        else {
            Write-LogError "ConfigMap '$ConfigMapName' already exists and RECREATE_CONFIGMAP is false"
            exit 1
        }
    }
    
    # Create ConfigMap
    Write-Log "Creating ConfigMap '$ConfigMapName'..."
    $output = oc create configmap $ConfigMapName --from-file=$ConfigFilesPath 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-LogError "Failed to create ConfigMap: $output"
        exit 1
    }
    
    Write-Log "ConfigMap '$ConfigMapName' created successfully"
}

# ============================================
# Deployment
# ============================================

function Deploy-Connector {
    Write-LogStep "5/7" "Deploying connector..."
    
    # Check if deployment.yaml exists
    if (-not (Test-Path $DeploymentYaml)) {
        Write-LogError "Deployment configuration not found: $DeploymentYaml"
        exit 1
    }
    
    try {
        # Apply deployment
        Write-Log "Applying deployment configuration..."
        $output = oc apply -f $DeploymentYaml 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to apply deployment"
        }
        
        Write-Log "Deployment configuration applied successfully"
    }
    catch {
        Write-LogError "Failed to deploy connector: $_"
        exit 1
    }
}

# ============================================
# Service Creation
# ============================================

function New-Service {
    Write-LogStep "6/7" "Creating service..."
    
    # Check if service.yaml exists
    if (-not (Test-Path $ServiceYaml)) {
        Write-LogError "Service configuration not found: $ServiceYaml"
        exit 1
    }
    
    try {
        # Apply service
        Write-Log "Applying service configuration..."
        $output = oc apply -f $ServiceYaml 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to apply service"
        }
        
        Write-Log "Service configuration applied successfully"
    }
    catch {
        Write-LogError "Failed to create service: $_"
        exit 1
    }
}

# ============================================
# Verification
# ============================================

function Test-Deployment {
    Write-LogStep "7/7" "Verifying deployment..."
    
    Write-Log "Waiting for pod to be ready (this may take a minute)..."
    Start-Sleep -Seconds 5
    
    # Check pod status
    Write-Log "Checking pod status..."
    oc get pods -l app=cf-flight-connector
    
    # Check service
    Write-Host ""
    Write-Log "Checking service..."
    oc get service cf-flight-connector-service
    
    Write-Host ""
    Write-Log "Deployment verification complete"
}

# ============================================
# Main Execution
# ============================================

function Main {
    Write-Host "=========================================="
    Write-Host "OpenShift Deployment Script"
    Write-Host "=========================================="
    
    if ($Help) {
        Show-Usage
    }
    
    Test-OcCli
    Load-Properties
    Connect-OpenShift
    Initialize-Project
    New-ConfigMap
    Deploy-Connector
    New-Service
    Test-Deployment
    
    Write-Host ""
    Write-Host "=========================================="
    Write-Host "Deployment Complete!"
    Write-Host "=========================================="
    Write-Host ""
    Write-Host "Your REST API connector(s) have been deployed to OpenShift."
    Write-Host ""
    Write-Host "Deployment details:"
    Write-Host "  - Cluster: $OpenShiftClusterUrl"
    Write-Host "  - Project: $OpenShiftProject"
    Write-Host "  - Deployment: cf-flight-connector"
    Write-Host "  - Service: cf-flight-connector-service"
    Write-Host "  - Port: 9443 (gRPC)"
    Write-Host ""
    Write-Host "The connector is accessible within the cluster at:"
    Write-Host "  cf-flight-connector-service.$OpenShiftProject.svc.cluster.local:9443"
    Write-Host ""
}

# Run main function
Main

# Made with Bob
