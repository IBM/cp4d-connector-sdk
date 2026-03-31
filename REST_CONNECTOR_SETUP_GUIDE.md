# REST API Connector Setup Guide for CP4D SDK

This guide provides step-by-step instructions for setting up and deploying a REST API connector using the IBM Cloud Pak for Data Connector SDK. The assumption is that you're starting from scratch with only IBM Bob (AI assistant) available.

## Table of Contents
1. [Prerequisites Installation](#prerequisites-installation)
2. [Repository Setup](#repository-setup)
3. [Opening the Project with IBM Bob](#opening-the-project-with-ibm-bob)
4. [Creating Connector Configuration](#creating-connector-configuration)
5. [Building the Connector](#building-the-connector)
6. [Running the Docker Container](#running-the-docker-container)
7. [Registering with CP4D](#registering-with-cp4d)
8. [Troubleshooting](#troubleshooting)

---

## Prerequisites Installation

Before you begin, you need to install the following software on your system:

### 1. Git
**Purpose:** Version control system to clone the repository

**Installation:**
- **Windows:** Download from https://git-scm.com/download/win
- **macOS:** `brew install git` or download from https://git-scm.com/download/mac
- **Linux:** `sudo apt-get install git` (Ubuntu/Debian) or `sudo yum install git` (RHEL/CentOS)

**Verify installation:**
```bash
git --version
```

### 2. Java Development Kit (JDK) 11 or higher
**Purpose:** Required to compile and run the Java-based connector

**Installation:**
- **Windows/macOS/Linux:** Download OpenJDK 11+ from https://adoptium.net/
- Or use package managers:
  - **Windows:** `winget install EclipseAdoptium.Temurin.11.JDK`
  - **macOS:** `brew install openjdk@11`
  - **Linux:** `sudo apt-get install openjdk-11-jdk` (Ubuntu/Debian)

**Verify installation:**
```bash
java -version
javac -version
```

**Set JAVA_HOME environment variable:**
- **Windows:** 
  ```powershell
  setx JAVA_HOME "C:\Program Files\Eclipse Adoptium\jdk-11.x.x.x-hotspot"
  ```
- **macOS/Linux:** Add to `~/.bashrc` or `~/.zshrc`:
  ```bash
  export JAVA_HOME=/path/to/jdk-11
  export PATH=$JAVA_HOME/bin:$PATH
  ```

### 3. Gradle (Optional - included in project)
**Purpose:** Build automation tool

**Note:** The project includes Gradle wrapper (`gradlew`/`gradlew.bat`), so separate installation is optional.

**If you want to install globally:**
- **Windows:** `winget install Gradle.Gradle`
- **macOS:** `brew install gradle`
- **Linux:** Download from https://gradle.org/install/

### 4. Docker
**Purpose:** Container platform to build and run the connector image

**Installation:**
- **Windows:** Download Docker Desktop from https://www.docker.com/products/docker-desktop
- **macOS:** Download Docker Desktop from https://www.docker.com/products/docker-desktop
- **Linux:** Follow instructions at https://docs.docker.com/engine/install/

**Verify installation:**
```bash
docker --version
docker ps
```

**Important:** Ensure Docker daemon is running before proceeding.

### 5. Visual Studio Code (VS Code)
**Purpose:** IDE to work with IBM Bob

**Installation:**
- Download from https://code.visualstudio.com/

**Required Extension:**
- Install the IBM Bob extension from VS Code marketplace

---

## Repository Setup

### Step 1: Clone the Repository

Open a terminal/command prompt and run:

```bash
git clone https://github.com/IBM/cp4d-connector-sdk.git
cd cp4d-connector-sdk
```

### Step 2: Checkout the REST Connector Branch

```bash
git checkout mzuw_rest
```

**Verify you're on the correct branch:**
```bash
git branch
```
You should see `* mzuw_rest` indicating you're on the correct branch.

---

## Opening the Project with IBM Bob

### Step 1: Open VS Code

```bash
code .
```

This opens the current directory (`cp4d-connector-sdk`) in VS Code.

### Step 2: Verify IBM Bob is Active

- Look for the IBM Bob icon in the VS Code sidebar
- Ensure the extension is enabled and connected

### Step 3: Verify Custom Mode is Available

The project includes a custom mode called **🔌 REST API Connector Creator** located in `.bob/custom_modes.yaml`. This mode is specifically designed to help you create REST API connector configuration files.

---

## Creating Connector Configuration

### Step 1: Switch to REST API Connector Creator Mode

In IBM Bob, switch to the **🔌 REST API Connector Creator** mode. This specialized mode will help you create the JSON configuration file for your REST API connector.

### Step 2: Provide API Information

Work with IBM Bob in the REST API Connector Creator mode to:

1. **Analyze the target REST API** you want to connect to
2. **Provide API documentation** or example responses
3. **Define authentication method** (none, api_key, oauth2, basic)
4. **Map API endpoints to tables**
5. **Define data types and schemas**
6. **Configure pagination** if needed

### Step 3: Generate Configuration File

IBM Bob will create a JSON configuration file based on the template structure. The file should be saved in:

```
sdk-gen/subprojects/rest_connector/src/main/resources/
```

**Example configuration file name:** `your-api-name.json`

**Configuration file structure** (based on `template.json`):
```json
{
  "$connector_name": "Your API Connector",
  "$connector_label": "Your API Display Label",
  "$connector_description": "Description of your API connector",
  "$hostname": "https://api.yourservice.com",
  "$authentication": "api_key",
  "$tables": {
    "TABLE_NAME": {
      "$path": ["/api/endpoint"],
      "column1": "VARCHAR,$key",
      "column2": "INTEGER",
      "column3": "TIMESTAMP"
    }
  }
}
```

### Step 4: Create Volume Mount Directory

Create a directory to store your configuration files that will be mounted to the Docker container:

```bash
mkdir -p config-files
cp sdk-gen/subprojects/rest_connector/src/main/resources/your-api-name.json config-files/
```

---

## Building the Connector

The build process involves several Gradle tasks that must be executed in sequence.

### Step 1: Navigate to SDK Directory

```bash
cd sdk-gen
```

### Step 2: Apply Code Formatting (spotlessApply)

This task formats the code according to project standards:

**Windows:**
```powershell
.\gradlew spotlessApply
```

**macOS/Linux:**
```bash
./gradlew spotlessApply
```

### Step 3: Build the Project

This compiles the Java code and runs tests:

**Windows:**
```powershell
.\gradlew build
```

**macOS/Linux:**
```bash
./gradlew build
```

**Note:** This may take several minutes on the first run as Gradle downloads dependencies.

### Step 4: Generate Flight Application

This task packages the connector as a Flight service:

**Windows:**
```powershell
.\gradlew generateFlightApp
```

**macOS/Linux:**
```bash
./gradlew generateFlightApp
```

**Note:** If prompted, select the `rest_connector` project to include in the Flight service.

### Step 5: Build Docker Image

This creates the Docker container image:

**Windows:**
```powershell
.\gradlew dockerBuild
```

**macOS/Linux:**
```bash
./gradlew dockerBuild
```

**Expected output:** Docker image created with name similar to `rest_connector:latest` or `flight:latest`

**Verify the image was created:**
```bash
docker images | grep -E "(rest_connector|flight)"
```

---

## Running the Docker Container

### Step 1: Prepare Configuration Volume

Ensure your configuration files are in the `config-files` directory:

```bash
ls config-files/
# Should show: your-api-name.json
```

### Step 2: Run the Container

Run the Docker container with the configuration files mounted as a volume:

**Windows (PowerShell):**
```powershell
docker run -d `
  --name rest-connector `
  -p 9090:9090 `
  -v ${PWD}/config-files:/config/mappings `
  -e CONFIG_FILE=/config/your-api-name.json
  rest_connector:latest
```

**macOS/Linux:**
```bash
docker run -d \
  --name rest-connector \
  -p 9090:9090 \
  -v $(pwd)/config-files:/config/mappings \
  -e CONFIG_FILE=/config/your-api-name.json \
  rest_connector:latest
```

**Parameters explained:**
- `-d`: Run in detached mode (background)
- `--name rest-connector`: Container name
- `-p 9090:9090`: Map port 9090 (Flight service port)
- `-v`: Mount local config directory to container
- `-e CONFIG_FILE`: Environment variable pointing to config file
- `rest_connector:latest`: Image name and tag

### Step 3: Verify Container is Running

```bash
docker ps
```

You should see the `rest-connector` container in the list.

**Check container logs:**
```bash
docker logs rest-connector
```

Look for messages indicating the Flight service started successfully.

### Step 4: Test the Connection

Test if the Flight service is accessible:

```bash
curl http://localhost:9090/health
```

Or use a Flight client to connect to `grpc://localhost:9090`

---

## Registering with CP4D

You can register the connector with Cloud Pak for Data either using Gradle or manually via REST API.

### Option 1: Using Gradle Register Task

#### Step 1: Create Registration Properties File

Create a properties file with your CP4D connection details:

**File:** `sdk-gen/build/resources/dist/payload/envs/my-cp4d.properties`

```properties
# CP4D Authentication
auth_uri=https://your-cp4d-instance.com/icp4d-api/v1/authorize
username=your-username
password=your-password

# Or for Cloud (use apikey instead)
# auth_uri=https://iam.cloud.ibm.com/identity/token
# apikey=your-api-key

# Flight Service URI (where your connector is running)
flight_uri=grpc://your-connector-host:9090

# CP4D Datasource Types API
datasource_types_uri=https://your-cp4d-instance.com/v2/datasource_types

# Optional: SSL Certificate
# ssl_certificate=/path/to/certificate.pem
# ssl_certificate_validation=false
```

#### Step 2: Run Register Task

**Windows:**
```powershell
.\gradlew register -Penv=my-cp4d
```

**macOS/Linux:**
```bash
./gradlew register -Penv=my-cp4d
```

**Or interactively (will prompt for properties file path):**
```bash
./gradlew register
```

### Option 2: Manual Registration via REST API

#### Step 1: Get Authentication Token

**For CP4D:**
```bash
curl -k -X POST https://your-cp4d-instance.com/icp4d-api/v1/authorize \
  -H "Content-Type: application/json" \
  -d '{
    "username": "your-username",
    "password": "your-password"
  }'
```

**Save the token from the response.**

#### Step 2: Register the Datasource Type

```bash
curl -k -X POST https://your-cp4d-instance.com/v2/datasource_types \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "origin_country": "us",
    "flight_info": {
      "flight_uri": "grpc://your-connector-host:9090",
      "ssl_certificate": "",
      "ssl_certificate_validation": false
    }
  }'
```

#### Step 3: Verify Registration

List registered datasource types:

```bash
curl -k -X GET https://your-cp4d-instance.com/v2/datasource_types \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### Unregistering a Connector

If you need to remove a registered connector:

**Using Gradle:**
```bash
./gradlew unregister -PdatasourceType=your-datasource-type-id
```

**Using REST API:**
```bash
curl -k -X DELETE https://your-cp4d-instance.com/v2/datasource_types/your-datasource-type-id \
  -H "Authorization: Bearer YOUR_TOKEN"
```

---

## Troubleshooting

### Build Issues

**Problem:** `JAVA_HOME not set`
- **Solution:** Set JAVA_HOME environment variable as described in Prerequisites

**Problem:** Gradle build fails with dependency errors
- **Solution:** Clear Gradle cache and retry:
  ```bash
  ./gradlew clean --refresh-dependencies
  ./gradlew build
  ```

**Problem:** `spotlessApply` fails
- **Solution:** Check Java version (must be 11+) and ensure all files are properly formatted

### Docker Issues

**Problem:** Docker daemon not running
- **Solution:** Start Docker Desktop or Docker service

**Problem:** Port 9090 already in use
- **Solution:** Stop the conflicting service or use a different port:
  ```bash
  docker run -p 9091:9090 ...
  ```

**Problem:** Container exits immediately
- **Solution:** Check logs for errors:
  ```bash
  docker logs rest-connector
  ```

**Problem:** Configuration file not found in container
- **Solution:** Verify volume mount path and CONFIG_FILE environment variable

### Registration Issues

**Problem:** Authentication fails
- **Solution:** Verify credentials and auth_uri in properties file

**Problem:** Cannot connect to Flight service
- **Solution:** 
  - Verify container is running: `docker ps`
  - Check network connectivity to the host
  - Verify firewall rules allow port 9090

**Problem:** SSL certificate validation errors
- **Solution:** Set `ssl_certificate_validation=false` in properties file (for testing only)

### Configuration Issues

**Problem:** Connector doesn't recognize API endpoints
- **Solution:** Review JSON configuration file structure, ensure `$path` is correct

**Problem:** Data type mapping errors
- **Solution:** Verify column types match supported types (VARCHAR, INTEGER, TIMESTAMP, BOOLEAN, etc.)

**Problem:** Pagination not working
- **Solution:** Check `$pagination` configuration matches API's pagination method

---

## Summary of Complete Workflow

1. **Install prerequisites:** Git, Java 11+, Docker, VS Code with IBM Bob
2. **Clone repository:** `git clone` and `git checkout mzuw_rest`
3. **Open in VS Code:** `code .`
4. **Create configuration:** Use IBM Bob's 🔌 REST API Connector Creator mode
5. **Build connector:**
   ```bash
   cd sdk-gen
   ./gradlew spotlessApply
   ./gradlew build
   ./gradlew generateFlightApp
   ./gradlew dockerBuild
   ```
6. **Run container:**
   ```bash
   docker run -d --name rest-connector -p 9090:9090 \
     -v $(pwd)/config-files:/config \
     -e CONFIG_FILE=/config/your-api-name.json \
     rest_connector:latest
   ```
7. **Register with CP4D:**
   ```bash
   ./gradlew register -Penv=my-cp4d
   ```
   Or manually via REST API to `/v2/datasource_types`

---

## Additional Resources

- **SDK Guide:** See `guide.md` in the repository root
- **Apache Arrow Flight:** https://arrow.apache.org/docs/format/Flight.html
- **CP4D Documentation:** https://www.ibm.com/docs/en/cloud-paks/cp-data/
- **Template Configuration:** `sdk-gen/subprojects/rest_connector/src/test/resources/template.json`
- **Example Configurations:** Check `sdk-gen/subprojects/rest_connector/src/test/resources/` for examples

---

## Support

For issues or questions:
1. Check the troubleshooting section above
2. Review the SDK guide (`guide.md`)
3. Consult IBM Bob for assistance
4. Check the GitHub repository issues: https://github.com/IBM/cp4d-connector-sdk/issues

---

**Document Version:** 1.0  
**Last Updated:** 2026-03-27  
**Branch:** mzuw_rest