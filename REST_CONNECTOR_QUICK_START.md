# REST API Connector Quick Start Guide (Pre-built Image)

This guide provides a streamlined approach to deploying a REST API connector using a pre-built Docker image. This method is ideal for users who want to quickly deploy a connector without building from source.

## Overview

With this approach, you only need:
1. Docker installed
2. IBM Bob (AI assistant) in VS Code
3. A configuration file for your REST API

**No need for:** Git, Java, Gradle, or building from source.

**Time required:** ~5-10 minutes

---

## Table of Contents
1. [Prerequisites](#prerequisites)
2. [Creating Configuration with IBM Bob](#creating-configuration-with-ibm-bob)
3. [Pulling the Pre-built Docker Image](#pulling-the-pre-built-docker-image)
4. [Running the Container](#running-the-container)
5. [Registering with CP4D](#registering-with-cp4d)
6. [Troubleshooting](#troubleshooting)

---

## Prerequisites

### 1. Docker
**Purpose:** Container platform to run the pre-built connector image

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

### 2. Visual Studio Code with IBM Bob
**Purpose:** IDE to work with IBM Bob for configuration creation

**Installation:**
- Download VS Code from https://code.visualstudio.com/
- Install the IBM Bob extension from VS Code marketplace

### 3. Create a Working Directory

Create a directory for your connector configuration files:

**Windows (PowerShell):**
```powershell
mkdir rest-connector-config
cd rest-connector-config
```

**macOS/Linux:**
```bash
mkdir rest-connector-config
cd rest-connector-config
```

---

## Creating Configuration with IBM Bob

### Step 1: Open VS Code in Your Working Directory

```bash
code .
```

### Step 2: Switch to REST API Connector Creator Mode

In IBM Bob, switch to the **🔌 REST API Connector Creator** mode. This specialized mode helps you create JSON configuration files for REST APIs.

### Step 3: Create Configuration File

Work with IBM Bob to create your REST API connector configuration:

1. **Provide API Information:**
   - API base URL (e.g., `https://api.example.com`)
   - Authentication method (none, api_key, oauth2, basic)
   - API documentation or example responses

2. **Define Tables/Endpoints:**
   - Specify which API endpoints you want to expose as tables
   - Define column names and data types
   - Configure pagination if needed

3. **Save Configuration:**
   - IBM Bob will create a JSON file (e.g., `my-api-config.json`)
   - Save it in your working directory

**Example configuration structure:**
```json
{
  "$connector_name": "My API Connector",
  "$connector_label": "My API",
  "$connector_description": "Connector for My API",
  "$hostname": "https://api.example.com",
  "$authentication": "api_key",
  "$tables": {
    "USERS": {
      "$path": ["/api/users"],
      "id": "INTEGER,$key",
      "name": "VARCHAR",
      "email": "VARCHAR",
      "created_at": "TIMESTAMP"
    }
  }
}
```

### Step 4: Organize Configuration Files

Create a subdirectory for your configuration files:

```bash
mkdir config-files
mv my-api-config.json config-files/
```

Your directory structure should look like:
```
rest-connector-config/
├── config-files/
│   └── my-api-config.json
```

---

## Pulling the Pre-built Docker Image

### Step 1: Pull the Image from Repository

Pull the pre-built REST connector image:

```bash
docker pull <REPOSITORY_NAME>/rest-connector:latest
```

**Note:** Replace `<REPOSITORY_NAME>` with the actual repository name (to be updated).

### Step 2: Verify Image Downloaded

```bash
docker images | grep rest-connector
```

You should see the `rest-connector` image listed.

---

## Running the Container

### Step 1: Run the Container with Configuration Volume

Run the Docker container with your configuration files mounted:

**Windows (PowerShell):**
```powershell
docker run -d `
  --name rest-connector `
  -p 9090:9090 `
  -v ${PWD}/config-files:/config/mappings `
  -e CONFIG_FILE=/config/mappings/my-api-config.json `
  <REPOSITORY_NAME>/rest-connector:latest
```

**macOS/Linux:**
```bash
docker run -d \
  --name rest-connector \
  -p 9090:9090 \
  -v $(pwd)/config-files:/config/mappings \
  -e CONFIG_FILE=/config/mappings/my-api-config.json \
  <REPOSITORY_NAME>/rest-connector:latest
```

**Parameters explained:**
- `-d`: Run in detached mode (background)
- `--name rest-connector`: Container name
- `-p 9090:9090`: Map port 9090 (Flight service port)
- `-v`: Mount local config directory to `/config/mappings` in container
- `-e CONFIG_FILE`: Environment variable pointing to your config file
- `<REPOSITORY_NAME>/rest-connector:latest`: Image name and tag

### Step 2: Verify Container is Running

```bash
docker ps
```

You should see the `rest-connector` container with status "Up".

### Step 3: Check Container Logs

```bash
docker logs rest-connector
```

Look for messages indicating:
- Configuration file loaded successfully
- Flight service started
- Listening on port 9090

### Step 4: Test the Connection (Optional)

Test if the Flight service is accessible:

```bash
curl http://localhost:9090/health
```

Or check if the port is listening:

**Windows:**
```powershell
netstat -an | findstr 9090
```

**macOS/Linux:**
```bash
netstat -an | grep 9090
```

---

## Registering with CP4D

You can register the connector with Cloud Pak for Data using REST API calls.

### Step 1: Get Authentication Token

**For CP4D:**
```bash
curl -k -X POST https://your-cp4d-instance.com/icp4d-api/v1/authorize \
  -H "Content-Type: application/json" \
  -d '{
    "username": "your-username",
    "password": "your-password"
  }'
```

**For IBM Cloud:**
```bash
curl -X POST https://iam.cloud.ibm.com/identity/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=urn:ibm:params:oauth:grant-type:apikey&apikey=your-api-key"
```

**Save the token from the response** (look for `token` or `access_token` field).

### Step 2: Register the Datasource Type

Replace `YOUR_TOKEN` with the token from Step 1, and `your-connector-host` with the hostname/IP where your container is running:

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

**Important notes:**
- If running locally, use `localhost` or your machine's IP address
- If running on a server, use the server's hostname or IP
- For production, consider using SSL certificates and setting `ssl_certificate_validation` to `true`

### Step 3: Verify Registration

List registered datasource types to confirm:

```bash
curl -k -X GET https://your-cp4d-instance.com/v2/datasource_types \
  -H "Authorization: Bearer YOUR_TOKEN"
```

Look for your connector in the response.

### Unregistering a Connector

If you need to remove the connector:

```bash
curl -k -X DELETE https://your-cp4d-instance.com/v2/datasource_types/your-datasource-type-id \
  -H "Authorization: Bearer YOUR_TOKEN"
```

---

## Troubleshooting

### Docker Issues

**Problem:** Cannot pull Docker image
- **Solution:** Verify you have access to the repository and are logged in:
  ```bash
  docker login <REPOSITORY_NAME>
  ```

**Problem:** Port 9090 already in use
- **Solution:** Use a different port:
  ```bash
  docker run -p 9091:9090 ...
  ```
  Then update the `flight_uri` in registration to use port 9091.

**Problem:** Container exits immediately
- **Solution:** Check logs for errors:
  ```bash
  docker logs rest-connector
  ```
  Common issues:
  - Configuration file not found (check volume mount path)
  - Invalid JSON in configuration file
  - Missing required fields in configuration

**Problem:** Configuration file not found
- **Solution:** Verify:
  1. Configuration file exists in `config-files/` directory
  2. Volume mount path is correct: `-v $(pwd)/config-files:/config/mappings`
  3. CONFIG_FILE environment variable matches the file location: `-e CONFIG_FILE=/config/mappings/my-api-config.json`

### Configuration Issues

**Problem:** Connector doesn't recognize API endpoints
- **Solution:** 
  - Verify `$path` in configuration matches actual API endpoints
  - Check API base URL in `$hostname`
  - Test API endpoints manually with curl or Postman

**Problem:** Authentication fails
- **Solution:**
  - Verify `$authentication` type matches API requirements
  - Check API credentials are correct
  - Review API documentation for authentication method

**Problem:** Data type mapping errors
- **Solution:** 
  - Ensure column types are valid: VARCHAR, INTEGER, TIMESTAMP, BOOLEAN, DECIMAL, etc.
  - Use `$key` marker for primary key columns
  - Check for nested objects and arrays (use `[]` notation)

**Problem:** Pagination not working
- **Solution:**
  - Verify `$pagination` configuration matches API's pagination method
  - Supported types: offset, page, cursor, link_header, next_url
  - Check API documentation for pagination parameters

### Registration Issues

**Problem:** Authentication fails during registration
- **Solution:** 
  - Verify CP4D credentials are correct
  - Check auth endpoint URL
  - Ensure user has permissions to register datasource types

**Problem:** Cannot connect to Flight service
- **Solution:**
  - Verify container is running: `docker ps`
  - Check network connectivity to the host
  - Verify firewall rules allow port 9090
  - If using remote host, ensure it's accessible from CP4D

**Problem:** SSL certificate validation errors
- **Solution:** 
  - For testing: Set `ssl_certificate_validation: false`
  - For production: Provide valid SSL certificate in `ssl_certificate` field

---

## Managing the Container

### Stop the Container
```bash
docker stop rest-connector
```

### Start the Container
```bash
docker start rest-connector
```

### Restart the Container
```bash
docker restart rest-connector
```

### Remove the Container
```bash
docker stop rest-connector
docker rm rest-connector
```

### Update Configuration

To update the configuration without rebuilding:

1. Edit the configuration file in `config-files/`
2. Restart the container:
   ```bash
   docker restart rest-connector
   ```

### View Container Logs
```bash
docker logs rest-connector
```

**Follow logs in real-time:**
```bash
docker logs -f rest-connector
```

---

## Complete Quick Start Workflow

Here's the complete workflow in a nutshell:

1. **Install Docker** and verify it's running
2. **Create working directory:**
   ```bash
   mkdir rest-connector-config && cd rest-connector-config
   ```
3. **Create configuration** using IBM Bob's 🔌 REST API Connector Creator mode
4. **Save config** to `config-files/my-api-config.json`
5. **Pull Docker image:**
   ```bash
   docker pull <REPOSITORY_NAME>/rest-connector:latest
   ```
6. **Run container:**
   ```bash
   docker run -d --name rest-connector -p 9090:9090 \
     -v $(pwd)/config-files:/config/mappings \
     -e CONFIG_FILE=/config/mappings/my-api-config.json \
     <REPOSITORY_NAME>/rest-connector:latest
   ```
7. **Verify container:**
   ```bash
   docker ps
   docker logs rest-connector
   ```
8. **Register with CP4D:**
   - Get auth token
   - POST to `/v2/datasource_types` with flight_uri

---

## Next Steps

After successfully deploying your connector:

1. **Test the connection** in CP4D UI
2. **Create a connection** using your new datasource type
3. **Query data** from your REST API through CP4D
4. **Monitor logs** for any issues
5. **Update configuration** as needed

---

## Additional Resources

- **Template Configuration:** See examples in the full repository
- **CP4D Documentation:** https://www.ibm.com/docs/en/cloud-paks/cp-data/
- **Apache Arrow Flight:** https://arrow.apache.org/docs/format/Flight.html
- **Full Build Guide:** See `REST_CONNECTOR_SETUP_GUIDE.md` for building from source

---

## Comparison: Quick Start vs Build from Source

| Aspect | Quick Start (Pre-built) | Build from Source |
|--------|------------------------|-------------------|
| **Time** | 5-10 minutes | 30-60 minutes |
| **Prerequisites** | Docker, VS Code | Git, Java, Gradle, Docker, VS Code |
| **Customization** | Configuration only | Full code access |
| **Use Case** | Quick deployment, testing | Development, customization |
| **Updates** | Pull new image | Rebuild from source |
| **Complexity** | Low | Medium-High |

---

**Document Version:** 1.0  
**Last Updated:** 2026-03-27  
**Image Repository:** `<REPOSITORY_NAME>` (to be updated)