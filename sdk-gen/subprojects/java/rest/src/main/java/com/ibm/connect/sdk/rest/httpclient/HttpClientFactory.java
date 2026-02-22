/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.rest.httpclient;

import static com.ibm.connect.sdk.rest.RestMsgs.SSL_INITIALISATION_FAILED;
import static com.ibm.connect.sdk.rest.utils.RestApiConstants.HTTP_CLIENT_DEFAULT_CONNECT_TIMEOUT;
import static com.ibm.connect.sdk.rest.utils.RestApiConstants.HTTP_POOL_DEFAULT_MAX_CONNECTION;
import static com.ibm.connect.sdk.rest.utils.RestApiConstants.HTTP_POOL_DEFAULT_MAX_CONNECTION_PER_ROUTE;

import javax.net.ssl.SSLContext;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

import com.ibm.connect.sdk.rest.RestMsgs;
import com.ibm.connect.sdk.rest.utils.RestApiConstants.SupportedAuthType;
import com.ibm.connect.sdk.util.SSLUtils;

public class HttpClientFactory {
    private final Timeout timeout;
    private final String sslCertificate;
    private final SupportedAuthType authType;
    private final String username;
    private final char[] password;
    private final OAuth2TokenManager oauth2TokenManager;

    private HttpClientFactory(Builder builder) {
        this.timeout = builder.timeout;
        this.sslCertificate = builder.sslCertificate;
        this.authType = builder.authType;
        this.username = builder.username;
        this.password = builder.password;
        this.oauth2TokenManager = builder.oauth2TokenManager;
    }

    public CloseableHttpClient build() {
        // Configure per-connection timeouts
        final ConnectionConfig connectionConfig = ConnectionConfig.custom()
                                                            .setSocketTimeout(Optional.ofNullable(timeout).orElse(HTTP_CLIENT_DEFAULT_CONNECT_TIMEOUT))
                                                            .setConnectTimeout(Optional.ofNullable(timeout).orElse(HTTP_CLIENT_DEFAULT_CONNECT_TIMEOUT))
                                                            .build();
        // Configure SSL
        final SSLContext sslContext = getSslContext();

        // Connection pool manager
        final PoolingHttpClientConnectionManagerBuilder connectionManagerBuilder = PoolingHttpClientConnectionManagerBuilder.create()
                .setDefaultConnectionConfig(connectionConfig)
                .setTlsSocketStrategy(ClientTlsStrategyBuilder.create()
                        .setSslContext(sslContext)
                        .buildClassic())
                .setMaxConnTotal(HTTP_POOL_DEFAULT_MAX_CONNECTION)
                .setMaxConnPerRoute(HTTP_POOL_DEFAULT_MAX_CONNECTION_PER_ROUTE);

        // Create basic http builder
        final HttpClientBuilder clientBuilder = HttpClients.custom()
                .setConnectionManager(connectionManagerBuilder.build())
                .evictExpiredConnections()
                .evictIdleConnections(TimeValue.ofMinutes(1));

        // Add Basic Auth
        if (SupportedAuthType.BASIC.equals(authType)) {
            if(username != null && password != null) {
                final BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
                credsProvider.setCredentials(
                        new AuthScope(null, -1), //matched any host/port that using later to hit the API
                        new UsernamePasswordCredentials(username, password)
                );
                clientBuilder.setDefaultCredentialsProvider(credsProvider);
                clientBuilder.addRequestInterceptorLast((request, entity, context) -> {
                    final String auth = username + ":" + new String(password);
                    final String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
                    request.addHeader("Authorization", "Basic " + encodedAuth);
                });
            } else {
                throw new IllegalArgumentException(RestMsgs.MISSING_USERNAME_AND_PASSWORD_BASIC_AUTH.format());
            }
        } else if (SupportedAuthType.OAUTH2.equals(authType)) {
            // Add OAuth 2.0 Bearer token interceptor
            if (oauth2TokenManager != null) {
                clientBuilder.addRequestInterceptorLast((request, entity, context) -> {
                    try {
                        final String token = oauth2TokenManager.getAccessToken();
                        request.addHeader("Authorization", "Bearer " + token);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to obtain OAuth 2.0 access token", e);
                    }
                });
            } else {
                throw new IllegalArgumentException(RestMsgs.MISSING_OAUTH2_CONFIGURATION.format("OAuth2TokenManager"));
            }
        }
        return clientBuilder.build();
    }

    private SSLContext getSslContext() {
        SSLContext sslContext;
        try {
                sslContext = SSLUtils.buildSSLContext(sslCertificate);
        } catch (Exception e) {
            throw new RuntimeException(SSL_INITIALISATION_FAILED.format(), e);
        }
        return sslContext;
    }

    public static class Builder {
        private Timeout timeout = HTTP_CLIENT_DEFAULT_CONNECT_TIMEOUT;
        private String sslCertificate;
        private SupportedAuthType authType = SupportedAuthType.NONE;
        private String username;
        private char[] password;
        private OAuth2TokenManager oauth2TokenManager;

        public Builder withTimeoutSeconds(Timeout timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder withSslCertificate(String sslCertificate) {
            this.sslCertificate = sslCertificate;
            return this;
        }

        public Builder withBasicAuth(String username, char[] password) {
            this.authType = SupportedAuthType.BASIC;
            this.username = username;
            this.password = password != null ? password.clone() : null;
            return this;
        }

        public Builder withOAuth2(OAuth2TokenManager tokenManager) {
            this.authType = SupportedAuthType.OAUTH2;
            this.oauth2TokenManager = tokenManager;
            return this;
        }

        public HttpClientFactory buildFactory() {
            return new HttpClientFactory(this);
        }
    }
}
