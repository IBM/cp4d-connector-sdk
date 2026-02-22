/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.rest.httpclient;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.ibm.connect.sdk.rest.RestMsgs;
import com.ibm.connect.sdk.rest.utils.ObjectMapperUtils;
import com.ibm.connect.sdk.rest.utils.RestApiConstants.SupportedOAuth2GrantType;

/**
 * Manages OAuth 2.0 access tokens with automatic refresh.
 * Thread-safe implementation using read-write locks.
 */
@SuppressWarnings("PMD")
public class OAuth2TokenManager {
    private static final Logger logger = LoggerFactory.getLogger(OAuth2TokenManager.class);
    private static final int TOKEN_EXPIRY_BUFFER_SECONDS = 60; // Refresh 60 seconds before expiry
    private static final String PARAM_GRANT_TYPE  = "grant_type";
    private static final String PARAM_SCOPE  = "scope";
    private static final String PARAM_REFRESH_TOKEN  = "refresh_token";

    private final String tokenUrl;
    private final String clientId;
    private final String clientSecret;
    private final String scope;
    private final SupportedOAuth2GrantType grantType;
    private final CloseableHttpClient httpClient;
    private final String refreshToken;

    private String accessToken;
    private String currentRefreshToken; // Store refresh token from response
    private Instant tokenExpiryTime;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * Creates an OAuth2TokenManager.
     *
     * @param tokenUrl     OAuth 2.0 token endpoint URL
     * @param clientId     OAuth 2.0 client ID
     * @param clientSecret OAuth 2.0 client secret
     * @param scope        OAuth 2.0 scope (optional, can be null)
     * @param grantType    OAuth 2.0 grant type (e.g., "client_credentials", "refresh_token")  {@link SupportedOAuth2GrantType}
     * @param httpClient   HTTP client for making token requests
     */
    public OAuth2TokenManager(String tokenUrl, String clientId, String clientSecret,
                             String scope, SupportedOAuth2GrantType grantType, CloseableHttpClient httpClient) {
        this(tokenUrl, clientId, clientSecret, scope, grantType, null, httpClient);
    }

    /**
     * Creates an OAuth2TokenManager with refresh token support.
     *
     * @param tokenUrl     OAuth 2.0 token endpoint URL
     * @param clientId     OAuth 2.0 client ID
     * @param clientSecret OAuth 2.0 client secret
     * @param scope        OAuth 2.0 scope (optional, can be null)
     * @param grantType    OAuth 2.0 grant type (e.g., "client_credentials", "refresh_token") {@link SupportedOAuth2GrantType}
     * @param refreshToken OAuth 2.0 refresh token (required for "refresh_token" grant type)
     * @param httpClient   HTTP client for making token requests
     */
    public OAuth2TokenManager(String tokenUrl, String clientId, String clientSecret,
                              String scope, SupportedOAuth2GrantType grantType, String refreshToken,
                              CloseableHttpClient httpClient) {
        this.tokenUrl = tokenUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scope = scope;
        this.grantType = grantType;
        this.refreshToken = refreshToken;
        this.currentRefreshToken = refreshToken; // Initialize with provided refresh token
        this.httpClient = httpClient;
    }

    /**
     * Gets a valid access token, refreshing if necessary.
     *
     * @return valid OAuth 2.0 access token
     * @throws IOException if token acquisition fails
     */
    public String getAccessToken() throws IOException {
        // Try read lock first (most common case - token is valid)
        lock.readLock().lock();
        try {
            if (isTokenValid()) {
                return accessToken;
            }
        } finally {
            lock.readLock().unlock();
        }

        // Token needs refresh - acquire write lock
        lock.writeLock().lock();
        try {
            // Double-check after acquiring write lock (another thread might have refreshed)
            if (isTokenValid()) {
                return accessToken;
            }
            
            // Refresh the token
            refreshToken();
            return accessToken;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Checks if the current token is valid (exists and not expired).
     *
     * @return true if token is valid, false otherwise
     */
    private boolean isTokenValid() {
        return accessToken != null 
            && tokenExpiryTime != null 
            && Instant.now().isBefore(tokenExpiryTime);
    }

    /**
     * Refreshes the OAuth 2.0 access token by calling the token endpoint.
     *
     * @throws IOException if token refresh fails
     */
    private void refreshToken() throws IOException {
        logger.debug("Refreshing OAuth 2.0 access token from: {}", tokenUrl);

        HttpPost tokenRequest = new HttpPost(tokenUrl);
        
        // Add Basic Auth header for client credentials
        String auth = clientId + ":" + clientSecret;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        tokenRequest.addHeader("Authorization", "Basic " + encodedAuth);
        tokenRequest.addHeader("Content-Type", ContentType.APPLICATION_FORM_URLENCODED.getMimeType());

        // Build request body
        Map<String, String> params = new HashMap<>();
        params.put(PARAM_GRANT_TYPE, grantType.getValue());
        
        // Add grant-type specific parameters
        if (SupportedOAuth2GrantType.REFRESH_TOKEN.equals(grantType)) {
            // For refresh_token grant type, use the current refresh token
            String tokenToUse = currentRefreshToken != null ? currentRefreshToken : refreshToken;
            if (tokenToUse == null || tokenToUse.isEmpty()) {
                throw new IOException("Refresh token is required for refresh_token grant type");
            }
            params.put(PARAM_REFRESH_TOKEN, tokenToUse);
        }
        
        if (scope != null && !scope.isEmpty()) {
            params.put(PARAM_SCOPE, scope);
        }

        String body = params.entrySet().stream()
            .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" +
                      URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
            .reduce((a, b) -> a + "&" + b)
            .orElse("");

        tokenRequest.setEntity(new StringEntity(body, ContentType.APPLICATION_FORM_URLENCODED));

        // Execute request and parse response
        String responseBody = httpClient.execute(tokenRequest, response -> {
            int status = response.getCode();
            if (status < 200 || status >= 300) {
                throw new IOException(RestMsgs.OAUTH2_TOKEN_ACQUISITION_FAILED.format(tokenUrl, status));
            }
            return org.apache.hc.core5.http.io.entity.EntityUtils.toString(
                response.getEntity(), StandardCharsets.UTF_8);
        });

        parseTokenResponse(responseBody);
        logger.debug("OAuth 2.0 access token refreshed successfully. Expires at: {}", tokenExpiryTime);
    }

    /**
     * Parses the OAuth 2.0 token response and updates internal state.
     *
     * @param responseBody JSON response from token endpoint
     * @throws IOException if parsing fails
     */
    private void parseTokenResponse(String responseBody) throws IOException {
        try {
            JsonNode jsonResponse = ObjectMapperUtils.parse(responseBody);
            
            // Extract access token (required)
            if (!jsonResponse.has("access_token")) {
                throw new IOException("OAuth 2.0 token response missing 'access_token' field");
            }
            this.accessToken = jsonResponse.get("access_token").asText();

            // Extract refresh token if present (for refresh_token grant type)
            if (jsonResponse.has("refresh_token")) {
                this.currentRefreshToken = jsonResponse.get("refresh_token").asText();
                logger.debug("Updated refresh token from response");
            }

            // Extract expires_in (optional, defaults to 3600 seconds)
            int expiresIn = jsonResponse.has("expires_in")
                ? jsonResponse.get("expires_in").asInt()
                : 3600;

            // Calculate expiry time with buffer
            this.tokenExpiryTime = Instant.now().plusSeconds(expiresIn - TOKEN_EXPIRY_BUFFER_SECONDS);

        } catch (Exception e) {
            throw new IOException("Failed to parse OAuth 2.0 token response: " + e.getMessage(), e);
        }
    }

    /**
     * Forces a token refresh on the next getAccessToken() call.
     * Useful for handling 401 responses.
     */
    public void invalidateToken() {
        lock.writeLock().lock();
        try {
            this.accessToken = null;
            this.tokenExpiryTime = null;
            logger.debug("OAuth 2.0 token invalidated");
        } finally {
            lock.writeLock().unlock();
        }
    }
}
