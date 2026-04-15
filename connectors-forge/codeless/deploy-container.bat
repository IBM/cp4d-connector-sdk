@echo off
setlocal enabledelayedexpansion

REM Container Deployment Script
REM Deploys a container to a remote Docker server via REST API
REM with configuration file upload

REM ============================================
REM Configuration & Global Variables
REM ============================================

set "SCRIPT_NAME=%~nx0"
set "DOCKER_HOST="
set "CONFIG_FILES="
set "PORT="
set "REPLACE_MODE=false"
set "IMAGE=docker.io/marek02/connectors-forge:latest"
set "CONTAINER_ID="
set "CONTAINER_NAME="
set "TEMP_DIR="
set "TEMP_ARCHIVE="
set "FILE_COUNT=0"

REM ============================================
REM Check for curl
REM ============================================

where curl >nul 2>&1
if errorlevel 1 (
    echo ERROR: curl is not installed or not in PATH
    echo Please install curl from https://curl.se/windows/
    exit /b 1
)

REM ============================================
REM Parse Arguments
REM ============================================

:parse_args
if "%~1"=="" goto :check_required_args
if /i "%~1"=="--host" (
    set "DOCKER_HOST=%~2"
    shift
    shift
    goto :parse_args
)
if /i "%~1"=="--files" (
    shift
    :collect_files
    if "%~1"=="" goto :check_required_args
    if "%~1:~0,2%"=="--" goto :parse_args
    if defined CONFIG_FILES (
        set "CONFIG_FILES=!CONFIG_FILES!|%~1"
    ) else (
        set "CONFIG_FILES=%~1"
    )
    set /a FILE_COUNT+=1
    shift
    goto :collect_files
)
if /i "%~1"=="--port" (
    set "PORT=%~2"
    shift
    shift
    goto :parse_args
)
if /i "%~1"=="--replace" (
    set "REPLACE_MODE=true"
    shift
    goto :parse_args
)
if /i "%~1"=="-h" goto :usage
if /i "%~1"=="--help" goto :usage
echo ERROR: Unknown option: %~1
goto :usage

:check_required_args
if not defined DOCKER_HOST (
    echo ERROR: Missing required argument: --host
    goto :usage
)
if not defined CONFIG_FILES (
    echo ERROR: Missing required argument: --files
    goto :usage
)
if %FILE_COUNT% LEQ 0 (
    echo ERROR: No valid files provided
    goto :usage
)
if not defined PORT (
    echo ERROR: Missing required argument: --port
    goto :usage
)
goto :main

:usage
echo Usage: %SCRIPT_NAME% --host HOST --files FILE1 [FILE2 ...] --port PORT [OPTIONS]
echo.
echo Deploy a container to a remote Docker server via REST API.
echo.
echo Required Arguments:
echo   --host HOST          Remote Docker server address (e.g., remote-server.com:2375)
echo   --files FILE1 ...    Space-separated list of files to upload (e.g., api1.json api2.json)
echo   --port PORT          Port to expose (e.g., 9090)
echo.
echo Optional Arguments:
echo   --replace            Stop and remove existing container with same name or using the same port
echo   -h, --help           Show this help message
echo.
echo Examples:
echo   # Deploy with single file
echo   %SCRIPT_NAME% --host remote.example.com:2375 --files .\config-files\my-api.json --port 9090
echo.
echo   # Deploy with multiple files
echo   %SCRIPT_NAME% --host remote.example.com:2375 --files api1.json api2.json settings.json --port 9090
echo.
echo   # Replace existing container (by name or port conflict)
echo   %SCRIPT_NAME% --host remote.example.com:2375 --files my-api.json --port 9090 --replace
echo.
exit /b 1

REM ============================================
REM Main Execution
REM ============================================

:main
echo ==========================================
echo Container Deployment Script
echo ==========================================
echo Docker Host:    %DOCKER_HOST%
echo Files:          %FILE_COUNT% file(s)
for %%F in ("%CONFIG_FILES:|=" "%") do (
    echo                 - %%~nxF
)
echo Image:          %IMAGE%
if "%REPLACE_MODE%"=="true" (
    echo Replace Mode:   Enabled
)
echo ==========================================

call :validate_inputs
if errorlevel 1 exit /b 1

call :handle_existing_container
if errorlevel 1 exit /b 1

call :create_tar_archive
if errorlevel 1 exit /b 1

call :pull_image
if errorlevel 1 exit /b 1

call :create_container
if errorlevel 1 exit /b 1

call :upload_config_files
if errorlevel 1 exit /b 1

call :start_container
if errorlevel 1 exit /b 1

call :verify_container_running
if errorlevel 1 exit /b 1

echo.
echo ==========================================
echo Deployment Successful!
echo ==========================================
echo Container ID:   %CONTAINER_ID:~0,12%
echo Container Name: %CONTAINER_NAME%
echo Status:         Running
echo ==========================================

call :cleanup
exit /b 0

REM ============================================
REM Validation Functions
REM ============================================

:validate_inputs
echo.
echo [1/8] Validating inputs...

REM Check if all config files exist
for %%F in ("%CONFIG_FILES:|=" "%") do (
    if not exist "%%~F" (
        echo ERROR: File not found: %%~F
        exit /b 1
    )
)
echo All %FILE_COUNT% file(s) exist

REM Validate port number
set /a "port_check=%PORT%" 2>nul
if !port_check! LSS 1 (
    echo ERROR: Invalid port number: %PORT% (must be 1-65535)
    exit /b 1
)
if !port_check! GTR 65535 (
    echo ERROR: Invalid port number: %PORT% (must be 1-65535)
    exit /b 1
)
echo Port number valid

REM Generate timestamp-based container name
for /f "tokens=2 delims==" %%I in ('wmic os get localdatetime /value') do set datetime=%%I
set "timestamp=!datetime:~0,8!-!datetime:~8,6!"
set "CONTAINER_NAME=connector-!timestamp!"
echo Container name: !CONTAINER_NAME!

REM Test Docker API connectivity
for /f %%C in ('curl -s -o NUL -w "%%{http_code}" "http://%DOCKER_HOST%/version"') do set "response=%%C"
if not "!response!"=="200" (
    echo ERROR: Cannot connect to Docker API at http://%DOCKER_HOST% (HTTP !response!)
    exit /b 1
)
echo Docker API accessible

exit /b 0

REM ============================================
REM Container Management Functions
REM ============================================

:check_container_exists
set "container_name=%~1"
set "filters={\"name\":[\"^/%container_name%$\"]}"
REM Simple URL encoding for the filter
set "encoded_filters=%filters%"
set "encoded_filters=%encoded_filters:{=%%7B%"
set "encoded_filters=%encoded_filters:}=%%7D%"
set "encoded_filters=%encoded_filters:[=%%5B%"
set "encoded_filters=%encoded_filters:]=%%5D%"
set "encoded_filters=%encoded_filters:"=%%22%"
set "encoded_filters=%encoded_filters::=%%3A%"
set "encoded_filters=%encoded_filters:/=%%2F%"
set "encoded_filters=%encoded_filters:^=%%5E%"

for /f "delims=" %%R in ('curl -s "http://%DOCKER_HOST%/containers/json?all=true&filters=%encoded_filters%"') do set "response=%%R"
for /f "tokens=2 delims=:," %%I in ('echo %response% ^| findstr /C:"\"Id\""') do (
    set "found_id=%%~I"
    set "found_id=!found_id:~1,-1!"
    goto :check_container_exists_done
)
set "found_id="
:check_container_exists_done
exit /b 0

:find_container_by_port
set "search_port=%~1"
REM Get all containers with port information already included
for /f "delims=" %%R in ('curl -s "http://%DOCKER_HOST%/containers/json?all=true"') do set "response=%%R"

REM Parse the response to find container using the specified host port
REM The Ports field in /containers/json already contains port mappings
echo %response% > "%TEMP%\containers_response.txt"
for /f "tokens=2 delims=:," %%I in ('findstr /C:"\"Id\"" "%TEMP%\containers_response.txt"') do (
    set "check_id=%%~I"
    set "check_id=!check_id:~1,-1!"
    REM Check if this container section contains our port
    for /f "delims=" %%L in ('findstr /C:"!check_id!" "%TEMP%\containers_response.txt"') do (
        echo %%L | findstr /C:"\"HostPort\":\"%search_port%\"" >nul
        if !errorlevel! EQU 0 (
            set "port_container_id=!check_id!"
            del "%TEMP%\containers_response.txt" 2>nul
            goto :find_container_by_port_done
        )
    )
)
set "port_container_id="
del "%TEMP%\containers_response.txt" 2>nul
:find_container_by_port_done
exit /b 0

:stop_container
set "stop_id=%~1"
echo Stopping container...
for /f %%C in ('curl -s -o NUL -w "%%{http_code}" -X POST "http://%DOCKER_HOST%/containers/%stop_id%/stop"') do set "http_code=%%C"
if "%http_code%"=="204" (
    echo Container stopped
    exit /b 0
)
if "%http_code%"=="304" (
    echo Container already stopped
    exit /b 0
)
echo Failed to stop container (HTTP %http_code%)
exit /b 1

:remove_container
set "remove_id=%~1"
echo Removing container...
for /f %%C in ('curl -s -o NUL -w "%%{http_code}" -X DELETE "http://%DOCKER_HOST%/containers/%remove_id%"') do set "http_code=%%C"
if "%http_code%"=="204" (
    echo Container removed
    exit /b 0
)
echo Failed to remove container (HTTP %http_code%)
exit /b 1

:handle_existing_container
echo.
echo [2/8] Checking for existing containers...

REM First check by container name
call :check_container_exists "%CONTAINER_NAME%"
if defined found_id (
    echo Container '%CONTAINER_NAME%' already exists (ID: !found_id:~0,12!)
    if "%REPLACE_MODE%"=="true" (
        call :stop_container "!found_id!"
        call :remove_container "!found_id!"
        if errorlevel 1 (
            echo ERROR: Failed to remove existing container
            call :cleanup
            exit /b 1
        )
    ) else (
        echo ERROR: Container already exists. Use --replace to replace it.
        call :cleanup
        exit /b 1
    )
) else (
    REM Check if any container is using the same port
    echo Checking for containers using port %PORT%...
    call :find_container_by_port "%PORT%"
    if defined port_container_id (
        REM Get container name for logging
        for /f "delims=" %%R in ('curl -s "http://%DOCKER_HOST%/containers/!port_container_id!/json"') do set "container_info=%%R"
        for /f "tokens=2 delims=:," %%N in ('echo !container_info! ^| findstr /C:"\"Name\""') do (
            set "conflict_name=%%~N"
            set "conflict_name=!conflict_name:~1,-1!"
            set "conflict_name=!conflict_name:/=!"
        )
        echo Found container '!conflict_name!' using port %PORT% (ID: !port_container_id:~0,12!)
        if "%REPLACE_MODE%"=="true" (
            echo Replacing container using port %PORT%...
            call :stop_container "!port_container_id!"
            call :remove_container "!port_container_id!"
            if errorlevel 1 (
                echo ERROR: Failed to remove container using port %PORT%
                call :cleanup
                exit /b 1
            )
        ) else (
            echo ERROR: Port %PORT% is already in use by container '!conflict_name!'. Use different port or --replace to replace existing container (WARNING: This will remove the container '!conflict_name!')
            call :cleanup
            exit /b 1
        )
    ) else (
        echo No existing container found, port %PORT% is available
    )
)
exit /b 0

REM ============================================
REM Archive Creation
REM ============================================

:create_tar_archive
echo.
echo [3/8] Creating tar archive...

REM Create temporary directory
set "TEMP_DIR=%TEMP%\connector-deploy-%RANDOM%"
mkdir "!TEMP_DIR!" 2>nul
mkdir "!TEMP_DIR!\mappings" 2>nul
set "TEMP_ARCHIVE=!TEMP_DIR!\config.tar.gz"

REM Copy all config files to mappings subdirectory
for %%F in ("%CONFIG_FILES:|=" "%") do (
    copy /Y "%%~F" "!TEMP_DIR!\mappings\" >nul
)
echo Copying %FILE_COUNT% file(s) to archive in mappings/ directory

REM Create tar archive with mappings directory structure
REM Check if tar is available
where tar >nul 2>&1
if errorlevel 1 (
    echo ERROR: tar is not available. Please install tar or use Windows 10/11 which includes it.
    call :cleanup
    exit /b 1
)

cd /d "!TEMP_DIR!"
tar -czf "config.tar.gz" mappings 2>nul
cd /d "%~dp0"

if not exist "!TEMP_ARCHIVE!" (
    echo ERROR: Failed to create tar archive
    call :cleanup
    exit /b 1
)

echo Archive created with mappings/ directory structure
exit /b 0

REM ============================================
REM Image Pull
REM ============================================

:pull_image
echo.
echo [4/8] Pulling Docker image...
echo Pulling image: %IMAGE%

for /f %%C in ('curl -s -o NUL -w "%%{http_code}" -X POST "http://%DOCKER_HOST%/images/create?fromImage=%IMAGE%"') do set "http_code=%%C"

if "!http_code!"=="200" (
    echo Image pulled successfully
) else (
    echo Image pull completed (may already exist)
)
exit /b 0

REM ============================================
REM Container Creation
REM ============================================

:create_container
echo.
echo [5/8] Creating container...

REM Create JSON payload
set "json_payload={\"Image\":\"%IMAGE%\",\"ExposedPorts\":{\"9443/tcp\":{}},\"HostConfig\":{\"PortBindings\":{\"9443/tcp\":[{\"HostPort\":\"%PORT%\"}]}}}"

REM Create container and capture response
for /f "delims=" %%R in ('curl -s -X POST "http://%DOCKER_HOST%/containers/create?name=%CONTAINER_NAME%" -H "Content-Type: application/json" -d "%json_payload%"') do set "CREATE_RESPONSE=%%R"

REM Extract container ID using findstr
for /f "tokens=2 delims=:," %%I in ('echo %CREATE_RESPONSE% ^| findstr /C:"\"Id\""') do (
    set "CONTAINER_ID=%%~I"
    set "CONTAINER_ID=!CONTAINER_ID:~1,-1!"
)

if not defined CONTAINER_ID (
    echo ERROR: Failed to create container
    call :cleanup
    exit /b 1
)

echo Container created: !CONTAINER_ID:~0,12!
exit /b 0

REM ============================================
REM File Upload
REM ============================================

:upload_config_files
echo.
echo [6/8] Uploading config files...

for /f %%C in ('curl -s -o NUL -w "%%{http_code}" -X PUT "http://%DOCKER_HOST%/containers/%CONTAINER_ID%/archive?path=/config" -H "Content-Type: application/x-tar" --data-binary "@%TEMP_ARCHIVE%"') do set "http_code=%%C"

if not "!http_code!"=="200" (
    echo ERROR: Failed to upload files (HTTP !http_code!). The /config directory may not exist in the container.
    call :cleanup_container
    call :cleanup
    exit /b 1
)

echo Files uploaded to /config/mappings/
exit /b 0

REM ============================================
REM Container Start
REM ============================================

:start_container
echo.
echo [7/8] Starting container...

for /f %%C in ('curl -s -o NUL -w "%%{http_code}" -X POST "http://%DOCKER_HOST%/containers/%CONTAINER_ID%/start"') do set "http_code=%%C"

if not "!http_code!"=="204" (
    echo ERROR: Failed to start container (HTTP !http_code!)
    call :cleanup_container
    call :cleanup
    exit /b 1
)

echo Container started
exit /b 0

REM ============================================
REM Container Verification
REM ============================================

:verify_container_running
echo.
echo [8/8] Verifying container status...

timeout /t 2 /nobreak >nul

curl -s "http://%DOCKER_HOST%/containers/%CONTAINER_ID%/json" > "%TEMP%\status_response.txt" 2>nul
findstr /C:"\"Running\":true" "%TEMP%\status_response.txt" >nul
set "running_status=!errorlevel!"
del "%TEMP%\status_response.txt" 2>nul

if !running_status! NEQ 0 (
    echo ERROR: Container is not running
    call :cleanup_container
    call :cleanup
    exit /b 1
)

echo Container is running
exit /b 0

REM ============================================
REM Cleanup Functions
REM ============================================

:cleanup_container
if defined CONTAINER_ID (
    echo Cleaning up failed deployment...
    curl -s -o nul -X DELETE "http://%DOCKER_HOST%/containers/%CONTAINER_ID%" 2>nul
)
exit /b 0

:cleanup
if defined TEMP_ARCHIVE (
    if exist "!TEMP_ARCHIVE!" del /f /q "!TEMP_ARCHIVE!" 2>nul
)
if defined TEMP_DIR (
    if exist "!TEMP_DIR!" rd /s /q "!TEMP_DIR!" 2>nul
)
exit /b 0

@REM Made with Bob
