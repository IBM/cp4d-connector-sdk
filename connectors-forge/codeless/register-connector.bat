@echo off
setlocal enabledelayedexpansion

REM Register Connector Script
REM Reads configuration from PROPERTIES_FILE and registers one or more connectors
REM with IBM Cloud Data Platform via REST API

REM ============================================
REM Configuration
REM ============================================

set "PROPERTIES_FILE=register-envs.properties"

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
REM Parse Command Line Arguments
REM ============================================

if /i "%~1"=="--help" goto :show_help
if /i "%~1"=="-h" goto :show_help
goto :main

:show_help
echo Usage: %~nx0 [OPTIONS]
echo.
echo Registers one or more connectors with IBM Cloud Data Platform.
echo Reads all configuration from register-envs.properties file.
echo.
echo OPTIONS:
echo     --help, -h          Show this help message
echo.
echo REQUIRED PROPERTIES (in register-envs.properties):
echo     apikey              IBM Cloud API key
echo     auth_uri            IBM Cloud IAM authentication endpoint
echo     datasource_types_uri    Datasource types API endpoint
echo     flight_hostname     Flight service hostname
echo     flight_port         Flight service port
echo.
echo OPTIONAL PROPERTIES:
echo     ssl_certificate_path    Path to SSL certificate file
echo     ssl_certificate_validation    Enable/disable SSL validation (true/false)
echo.
echo EXAMPLE:
echo     %~nx0
echo.
exit /b 0

REM ============================================
REM Main Execution
REM ============================================

:main
echo.
echo ==========================================
echo Connector Registration Script
echo ==========================================
echo.

REM Check if properties file exists
if not exist "%PROPERTIES_FILE%" (
    echo ERROR: Properties file not found: %PROPERTIES_FILE%
    echo.
    echo Please create %PROPERTIES_FILE% with required configuration.
    echo Run '%~nx0 --help' for more information.
    exit /b 1
)

echo Found properties file: %PROPERTIES_FILE%

REM Read properties from file
call :read_properties
if errorlevel 1 exit /b 1

REM Validate required properties
call :validate_properties
if errorlevel 1 exit /b 1

REM Display configuration
call :display_config

REM Get bearer token
call :get_bearer_token
if errorlevel 1 exit /b 1

REM Read SSL certificate if provided
call :read_ssl_certificate

REM Register connector
call :register_connector
if errorlevel 1 exit /b 1

exit /b 0

REM ============================================
REM Property Reading Functions
REM ============================================

:read_properties
echo Reading configuration from properties file...

REM Function to read property value
:get_property
set "key=%~1"
set "value="
for /f "tokens=1,2 delims==" %%A in ('findstr /b /l "%key%=" "%PROPERTIES_FILE%"') do (
    if /i "%%A"=="%key%" (
        set "value=%%B"
    )
)
REM Remove leading/trailing spaces
set "value=!value: =!"
set "value=!value:	=!"
set "value=!value: =!"
set "value=!value:	=!"
exit /b 0

REM Read all properties
call :get_property "apikey"
set "APIKEY=!value!"

call :get_property "auth_uri"
set "AUTH_URI=!value!"

call :get_property "datasource_types_uri"
set "DATASOURCE_TYPES_URI=!value!"

call :get_property "flight_hostname"
set "FLIGHT_HOSTNAME=!value!"

call :get_property "flight_port"
set "FLIGHT_PORT=!value!"

call :get_property "ssl_certificate_path"
set "SSL_CERT_PATH=!value!"

call :get_property "ssl_certificate_validation"
set "SSL_CERT_VALIDATION=!value!"

REM Set defaults
set "ORIGIN_COUNTRY=us"

if not defined SSL_CERT_VALIDATION (
    set "SSL_CERT_VALIDATION=false"
)

exit /b 0

REM ============================================
REM Property Validation
REM ============================================

:validate_properties
echo.
echo [1/3] Validating required properties...

set "VALIDATION_FAILED=false"

if not defined APIKEY (
    echo ERROR: Missing required property: apikey
    set "VALIDATION_FAILED=true"
)

if not defined AUTH_URI (
    echo ERROR: Missing required property: auth_uri
    set "VALIDATION_FAILED=true"
)

if not defined DATASOURCE_TYPES_URI (
    echo ERROR: Missing required property: datasource_types_uri
    set "VALIDATION_FAILED=true"
)

if not defined FLIGHT_HOSTNAME (
    echo ERROR: Missing required property: flight_hostname
    set "VALIDATION_FAILED=true"
)

if not defined FLIGHT_PORT (
    echo ERROR: Missing required property: flight_port
    set "VALIDATION_FAILED=true"
)

if "%VALIDATION_FAILED%"=="true" (
    echo.
    echo ERROR: Validation failed. Please update %PROPERTIES_FILE% with required values.
    echo.
    echo Required properties:
    echo   - apikey=<your-ibm-cloud-api-key>
    echo   - flight_hostname=<hostname>
    echo   - flight_port=<port>
    exit /b 1
)

echo All required properties are present
exit /b 0

REM ============================================
REM Display Configuration
REM ============================================

:display_config
echo.
echo Configuration:
echo   Auth URI:            %AUTH_URI%
echo   Datasource API:      %DATASOURCE_TYPES_URI%
echo   Flight Hostname:     %FLIGHT_HOSTNAME%
echo   Flight Port:         %FLIGHT_PORT%
echo   SSL Validation:      %SSL_CERT_VALIDATION%
if defined SSL_CERT_PATH (
    echo   SSL Certificate:     %SSL_CERT_PATH%
)
echo.
exit /b 0

REM ============================================
REM Get Bearer Token
REM ============================================

:get_bearer_token
echo.
echo [2/3] Obtaining Bearer Token from IBM Cloud IAM...

REM Get the full response body with token
for /f "delims=" %%R in ('curl -s -X POST "%AUTH_URI%" -H "Content-Type: application/x-www-form-urlencoded" -d "grant_type=urn:ibm:params:oauth:grant-type:apikey" -d "apikey=%APIKEY%"') do set "TOKEN_RESPONSE=%%R"

REM Extract access_token from JSON response using string manipulation
REM Look for "access_token":"..." pattern
for /f "tokens=2 delims=:," %%A in ('echo %TOKEN_RESPONSE% ^| findstr /C:"access_token"') do (
    set "BEARER_TOKEN=%%A"
)

REM Remove quotes from token
set "BEARER_TOKEN=%BEARER_TOKEN:"=%"

if not defined BEARER_TOKEN (
    echo ERROR: Failed to obtain bearer token
    echo ERROR: Please verify your API key is correct in %PROPERTIES_FILE%
    exit /b 1
)

echo Bearer token obtained successfully
exit /b 0

REM ============================================
REM Read SSL Certificate
REM ============================================

:read_ssl_certificate
set "SSL_CERTIFICATE="

if defined SSL_CERT_PATH (
    echo Reading SSL certificate from: %SSL_CERT_PATH%

    if not exist "%SSL_CERT_PATH%" (
        echo ERROR: SSL certificate file not found: %SSL_CERT_PATH%
        exit /b 1
    )

    REM Read certificate file
    set "SSL_CERTIFICATE="
    for /f "delims=" %%L in ('type "%SSL_CERT_PATH%"') do (
        if defined SSL_CERTIFICATE (
            set "SSL_CERTIFICATE=!SSL_CERTIFICATE!!LF!"
        )
        set "SSL_CERTIFICATE=!SSL_CERTIFICATE!%%L"
    )

    echo SSL certificate loaded successfully
)

exit /b 0

REM ============================================
REM Register Connector
REM ============================================

:register_connector
echo.
echo [3/3] Registering Connector with IBM Cloud...

REM Construct flight URI
set "FLIGHT_URI=grpc+tls://%FLIGHT_HOSTNAME%:%FLIGHT_PORT%"
echo Flight URI: %FLIGHT_URI%

REM Convert boolean to JSON
if "%SSL_CERT_VALIDATION%"=="true" (
    set "SSL_VALIDATION_JSON=true"
) else (
    set "SSL_VALIDATION_JSON=false"
)

REM Use SSL certificate from earlier read (if any)
set "SSL_CERT_JSON=%SSL_CERTIFICATE%"

REM Build JSON payload
set "PAYLOAD={\"flight_info\":{\"flight_uri\":\"%FLIGHT_URI%\",\"ssl_certificate\":\"%SSL_CERT_JSON%\",\"ssl_certificate_validation\":%SSL_VALIDATION_JSON%},\"origin_country\":\"%ORIGIN_COUNTRY%\"}"

echo Sending registration request...
echo Endpoint: %DATASOURCE_TYPES_URI%

REM Send registration request and capture HTTP code
for /f %%C in ('curl -s -o NUL -w "%%{http_code}" -X POST "%DATASOURCE_TYPES_URI%" -H "Accept: application/json" -H "Authorization: Bearer %BEARER_TOKEN%" -H "Content-Type: application/json" -d "%PAYLOAD%"') do set "HTTP_CODE=%%C"

if "%HTTP_CODE%"=="200" set "SUCCESS=true"
if "%HTTP_CODE%"=="201" set "SUCCESS=true"

if defined SUCCESS (
    echo.
    echo ==========================================
    echo Registration Successful!
    echo ==========================================
    echo Connector registered successfully (HTTP %HTTP_CODE%)
    echo.
    echo ==========================================
    exit /b 0
) else (
    echo.
    echo ==========================================
    echo Registration Failed
    echo ==========================================
    echo ERROR: Registration failed (HTTP %HTTP_CODE%)
    echo Please verify your configuration and try again
    exit /b 1
)

@REM Made with Bob
