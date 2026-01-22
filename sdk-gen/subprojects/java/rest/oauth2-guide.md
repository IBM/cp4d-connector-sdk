# OAuth 2.0 Authentication Guide for REST Connector

## Overview

The REST connector now supports OAuth 2.0 authentication in addition to Basic Authentication and No Authentication. This guide explains how to configure and use OAuth 2.0 with the REST connector.

## Supported OAuth 2.0 Grant Types

Currently, the REST connector supports the **Client Credentials** grant type, which is commonly used for server-to-server authentication where no user interaction is required.

## Configuration Properties

To use OAuth 2.0 authentication, you need to configure the following connection properties:

### Required Properties

| Property | Description | Example |
|----------|-------------|---------|
| `auth_type` | Authentication type (must be set to `oauth2`) | `oauth2` |
| `oauth2_token_url` | OAuth 2.0 token endpoint URL | `https://auth.example.com/oauth/token` |
| `oauth2_client_id` | OAuth 2.0 client ID | `my-client-id` |
| `oauth2_client_secret` | OAuth 2.0 client secret | `my-client-secret` |

### Optional Properties

| Property | Description | Default Value |
|----------|-------------|---------------|
| `oauth2_scope` | OAuth 2.0 scope(s) for the access token | `null` (no scope) |
| `oauth2_grant_type` | OAuth 2.0 grant type | `client_credentials` |



## How It Works

### Token Management

1. **Automatic Token Acquisition**: When the connector is initialized, it automatically acquires an OAuth 2.0 access token from the configured token endpoint.

2. **Token Caching**: The access token is cached and reused for subsequent API requests until it expires.

3. **Automatic Token Refresh**: The token manager automatically refreshes the access token 60 seconds before it expires, ensuring uninterrupted API access.

4. **Thread-Safe**: The token manager uses read-write locks to ensure thread-safe token access and refresh in multi-threaded environments.


## Token Endpoint Requirements

The OAuth 2.0 token endpoint must:

1. Accept POST requests with `application/x-www-form-urlencoded` content type
2. Support Basic Authentication (client_id:client_secret in Authorization header)
3. Return a JSON response with the following structure:

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "read write"
}
```

### Required Response Fields

- `access_token` (string, required): The OAuth 2.0 access token
- `expires_in` (integer, optional): Token lifetime in seconds (defaults to 3600 if not provided)

## Error Handling

### Common Errors

1. **Missing Configuration**
   - Error: `Missing required OAuth 2.0 configuration: <property>`
   - Solution: Ensure all required OAuth 2.0 properties are configured

2. **Token Acquisition Failed**
   - Error: `Failed to acquire OAuth 2.0 access token from <url>. HTTP status: <code>`
   - Solution: Verify token URL, client credentials, and network connectivity

3. **Invalid Token Response**
   - Error: `Failed to parse OAuth 2.0 token response`
   - Solution: Ensure the token endpoint returns a valid JSON response with `access_token` field

## Security Best Practices

1. **Secure Storage**: Store client secrets securely using environment variables or secret management systems
2. **HTTPS Only**: Always use HTTPS for token endpoints to prevent credential interception
3. **Minimal Scope**: Request only the minimum required scopes for your application
4. **Token Rotation**: Implement proper token rotation and revocation procedures
5. **SSL Certificates**: Use the `ssl_certificate` property for custom SSL certificates if needed


## Future Enhancements

Planned enhancements for OAuth 2.0 support:

- [ ] Authorization Code grant type
- [ ] Refresh token support
