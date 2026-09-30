# Standalone Auth Mode — Testing Guide

This document covers how to verify the standalone (no-auth) mode introduced for the connector SDK's Flight server. The change allows the Flight server to accept the literal token `"standalone"` without any JWT signature verification when `ENVIRONMENT_NAME=standalone` is set, mirroring how `wdp-connect-service` behaves in its standalone deployment mode.

Testing uses the [`flight-standalone`](../../flight-standalone) Python client project as the Arrow Flight client. The client entrypoint is `Python/src/main/python/flight_client.py`.

---

## Files Changed

| File | What changed |
|---|---|
| `sdk-gen/subprojects/java/util/src/main/java/com/ibm/connect/sdk/util/AuthUtils.java` | Added `STANDALONE_TOKEN` constant, `isStandalone()` method, and short-circuit in `validateAuthToken()` |
| `sdk-gen/subprojects/java/util/src/main/java/com/ibm/connect/sdk/util/ServerTokenAuthHandler.java` | No new logic — standalone pass-through is handled entirely by `validateAuthToken()` |
| `sdk-gen/subprojects/java/flight-svc/src/main/java/com/ibm/wdp/connect/sdk/flight/FlightService.java` | Added `ENABLE_AUTH` env var toggle to conditionally attach `ServerTokenAuthHandler` |
| `sdk-gen/subprojects/java/flight-svc/src/dist/resources/payload/etc/server.env` | Made `ENABLE_AUTH=true` explicit |
| `sdk-gen/subprojects/java/util/build.gradle` | Added `mockito-core` and `mockito-inline` test dependencies |
| `sdk-gen/subprojects/java/util/src/test/java/com/ibm/connect/sdk/util/TestAuthUtils.java` | Added 3 new unit tests for standalone behaviour |

---

## Prerequisites

### Server side — SDK (one-time build)

Requires JDK 17.

```bash
cd cp4d-connector-sdk/sdk-gen
./gradlew :wdp-connect-sdk-gen-s3-connector-flight-standalone:installDist
```

This produces the launch script at:
```
sdk-gen/subprojects/s3-connector-flight-standalone/build/install/wdp-connect-sdk-gen-s3-connector-flight-standalone/bin/wdp-connect-sdk-gen-s3-connector-flight-standalone
```

### Client side — flight-standalone (one-time setup)

```bash
cd flight-standalone
python3 -m venv venv                    # creates the venv — only needed once
source venv/bin/activate
pip install -r Python/requirements.txt
deactivate                              # exit venv after setup; re-activate per test run below
```

---

## 1. Unit Tests (Automated)

Run the util module tests. All 6 tests must pass.

```bash
cd cp4d-connector-sdk/sdk-gen
./gradlew :wdp-connect-sdk-gen-java-util:test
```

Expected output:

```
BUILD SUCCESSFUL
```

### What the tests cover

| Test | Asserts |
|---|---|
| `testIsStandaloneDefaultFalse` | `isStandalone()` returns `false` when `ENVIRONMENT_NAME` is not set |
| `testValidateAuthTokenAcceptsStandaloneInStandaloneMode` | `validateAuthToken("standalone", null)` returns `"standalone"` when `isStandalone()` is `true` |
| `testValidateAuthTokenRejectsStandaloneOutsideStandaloneMode` | `validateAuthToken("standalone", null)` throws `"Invalid authentication token"` when `isStandalone()` is `false` |

---

## 2. No-Auth Mode (`ENABLE_AUTH=false`)

The auth handler is not attached at all. The server accepts any client regardless of what token (if any) is sent.

### Step 1 — Start the server

**Terminal 1:**

```bash
cd cp4d-connector-sdk/sdk-gen
ENABLE_AUTH=false ENABLE_SSL=true FLIGHT_PORT=9090 \
  subprojects/s3-connector-flight-standalone/build/install/wdp-connect-sdk-gen-s3-connector-flight-standalone/bin/wdp-connect-sdk-gen-s3-connector-flight-standalone
```

Expected log output:
```
ENABLE_AUTH = false
ENABLE_SSL = true
Authentication is disabled (standalone open-source mode)
Generating self-signed certificate
Starting server using SSL with self-signed certificate
Flight service ready for requests at grpc+tls://0.0.0.0:9090
```

> SSL must be **on** so the client's `grpc+tls://` connection matches the server. The self-signed cert is auto-generated; `flight.py` sets `disable_server_verification=True` so no cert import is needed.

### Step 2 — Create a request file

**Terminal 2** — create `flight-standalone/resources/noauth-test.json`:

```json
{
  "host": "localhost",
  "port": 9090,
  "token": "",
  "datasource_type_name": "custom_s3",
  "context": "source",
  "connection_properties": {
    "access_key_id": "YOUR_AWS_ACCESS_KEY_ID",
    "secret_access_key": "YOUR_AWS_SECRET_ACCESS_KEY",
    "bucket": "your-bucket-name",
    "region": "us-east-1"
  },
  "interaction_properties": {
    "file_name": "your-file.csv"
  },
  "setup_phase": "false"
}
```

> `"token"` must be empty (`""`). When the server has no auth handler (`ENABLE_AUTH=false`) it closes the auth stream immediately. If the client still calls `authenticate()`, gRPC aborts with `GRPC_CALL_ERROR_TOO_MANY_OPERATIONS`. The empty token causes `flight.py` to skip the `authenticate()` call entirely.

### Step 3 — Run the client

If you have not done the one-time client setup yet, go back to the [Prerequisites](#prerequisites) section and run those steps first.

> Run all client commands from the `flight-standalone/` directory (repo root). Both the `venv/` and `resources/` directories are resolved relative to it.

```bash
cd flight-standalone                                          # must be repo root, not a subdirectory
source venv/bin/activate
python Python/src/main/python/flight_client.py --request resources/noauth-test.json
```

✅ Expected: authenticates and reads data successfully.  
❌ If auth is still enforced: `UNAUTHENTICATED` gRPC error on connect.

---

## 3. Standalone Token Mode (`ENVIRONMENT_NAME=standalone`)

The auth handler is attached but accepts the literal string `"standalone"` instead of a signed JWT. This is the mode used when running alongside `wdp-connect-service` in standalone deployment.

### Step 1 — Start the server

**Terminal 1:**

```bash
cd sdk-gen
ENVIRONMENT_NAME=standalone ENABLE_SSL=true FLIGHT_PORT=9090 \
  subprojects/s3-connector-flight-standalone/build/install/wdp-connect-sdk-gen-s3-connector-flight-standalone/bin/wdp-connect-sdk-gen-s3-connector-flight-standalone
```

Expected log output:
```
ENABLE_AUTH = true
ENABLE_SSL = true
Generating self-signed certificate
Starting server using SSL with self-signed certificate
Flight service ready for requests at grpc+tls://0.0.0.0:9090
```

### Step 2 — Create a request file

**Terminal 2** — create `flight-standalone/resources/standalone-test.json`:

```json
{
  "host": "localhost",
  "port": 9090,
  "token": "standalone",
  "datasource_type_name": "custom_s3",
  "context": "source",
  "connection_properties": {
    "access_key_id": "YOUR_AWS_ACCESS_KEY_ID",
    "secret_access_key": "YOUR_AWS_SECRET_ACCESS_KEY",
    "bucket": "your-bucket-name",
    "region": "us-east-1"
  },
  "interaction_properties": {
    "file_name": "your-file.csv"
  },
  "setup_phase": "false"
}
```

> The `"token": "standalone"` value is sent raw by `flight.py`'s `TokenClientAuthHandler` — no `Bearer ` prefix.  
> This matches exactly what `ServerTokenAuthHandler.authenticate()` checks against `AuthUtils.STANDALONE_TOKEN`.

### Step 3 — Run the client

```bash
cd flight-standalone                                          # must be repo root, not a subdirectory
source venv/bin/activate
python Python/src/main/python/flight_client.py --request resources/standalone-test.json
```

✅ Expected: auth handshake succeeds with the `"standalone"` token; data is read.  
❌ If `ENVIRONMENT_NAME` is not set: `UNAUTHENTICATED` — the `"standalone"` token is rejected as an invalid JWT.

---

## 4. Negative Tests

### 4a — Standalone token rejected in normal (non-standalone) mode

Start the server **without** `ENVIRONMENT_NAME`:

```bash
ENABLE_SSL=true FLIGHT_PORT=9090 \
  subprojects/s3-connector-flight-standalone/build/install/wdp-connect-sdk-gen-s3-connector-flight-standalone/bin/wdp-connect-sdk-gen-s3-connector-flight-standalone
```

Run the client with `resources/standalone-test.json` (token = `"standalone"`).

✅ Expected: `UNAUTHENTICATED` gRPC error — `"standalone"` is rejected because `isStandalone()` is `false`.

### 4b — Any token accepted in no-auth mode

Start with `ENABLE_AUTH=false ENABLE_SSL=true`. Change `"token"` in `resources/noauth-test.json` to `"Bearer some.jwt.token"` or any other value.

✅ Expected: still connects and reads data — the auth handler is not attached so the token value is never checked.

---

## 5. Environment Variable Reference

| Variable | Default | Description |
|---|---|---|
| `ENABLE_AUTH` | `true` | Set to `false` to disable the auth handler entirely (open-source / no-auth mode) |
| `ENVIRONMENT_NAME` | _(not set)_ | Set to `standalone` to accept the literal `"standalone"` token instead of a JWT |
| `ENABLE_SSL` | `true` | Must stay `true` when using `flight-standalone` client (which connects via `grpc+tls://`). Set to `false` only when using a plain gRPC client. |
| `FLIGHT_PORT` | `9443` | gRPC listening port |
| `WDP_PUBLIC_KEY_URLS` | `etc/wdp_public_key_urls.txt` | File containing URLs of public keys for JWT verification |
| `WDP_PUBLIC_KEYS_PATH` | `etc/wdp_public_keys` | Directory containing public key files for JWT verification |

---

## 6. Decision Tree

```
Is ENABLE_AUTH=false?
├── YES → Auth handler not attached. Any token (or no token) is accepted. ✅
└── NO  → Auth handler is attached (default).
          Is ENVIRONMENT_NAME=standalone?
          ├── YES → Token "standalone" accepted as-is (no JWT check). ✅
          └── NO  → Full JWT Bearer token required and verified against public keys. ✅
```
