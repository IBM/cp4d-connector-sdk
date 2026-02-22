/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.rest.utils;

import java.util.Properties;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ibm.connect.sdk.rest.RestMsgs;
import com.ibm.connect.sdk.rest.utils.RestApiConstants.SupportedAuthType;
import com.ibm.connect.sdk.rest.utils.RestApiConstants.SupportedOAuth2GrantType;
import com.ibm.connect.sdk.rest.utils.models.RestConfiguration;
import com.ibm.connect.sdk.rest.utils.models.SupportedRestModels;

public class RestConnectionProperties extends PropertiesHelper {
    private static final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
    
    // Connection properties key
    public static final String PROPERTY_BASE_URL = "url";
    public static final String PROPERTY_SSL_CERTIFICATE = "ssl_certificate";
    public static final String PROPERTY_AUTH_TYPE = "auth_type";
    public static final String PROPERTY_AUTH_USERNAME = "username";
    public static final String PROPERTY_AUTH_PASSWORD = "password";
    public static final String PROPERTY_PREDEFINED_REST_CONFIG_IDENTIFIER = "predefined_rest_config_yaml_identifier";
    public static final String PROPERTY_CUSTOM_REST_CONFIG_YAML = "rest_config_yaml";
    
    // OAuth 2.0 properties
    public static final String PROPERTY_OAUTH2_TOKEN_URL = "oauth2_token_url";
    public static final String PROPERTY_OAUTH2_CLIENT_ID = "oauth2_client_id";
    public static final String PROPERTY_OAUTH2_CLIENT_SECRET = "oauth2_client_secret";
    public static final String PROPERTY_OAUTH2_SCOPE = "oauth2_scope";
    public static final String PROPERTY_OAUTH2_GRANT_TYPE = "oauth2_grant_type";
    public static final String PROPERTY_OAUTH2_REFRESH_TOKEN = "oauth2_refresh_token";

    public RestConnectionProperties(Properties... properties)
    {
        super(properties);
    }

    /**
     * @return the url.
     *
     * @throws IllegalArgumentException if the base URL property is not set or is empty.
     */
    public String getBasicUrl()
    {
        return getRequiredProperty(PROPERTY_BASE_URL);
    }

    /**
     * @return SSL certificate
     */
    public String getSslCertificate() {
        return getProperty(PROPERTY_SSL_CERTIFICATE);
    }

    /**
     * @return <code>true</code> if basic authentication is to be used.
     *
     * @throws IllegalArgumentException if the auth type property is not set or is empty.
     */
    public boolean isAuthenticationBasic()
    {
        return SupportedAuthType.BASIC.equals(SupportedAuthType.fromString(getRequiredProperty(PROPERTY_AUTH_TYPE)));
    }

    /**
     * @return <code>true</code> if OAuth 2.0 authentication is to be used.
     *
     * @throws IllegalArgumentException if the auth type property is not set or is empty.
     */
    public boolean isAuthenticationOAuth2()
    {
        return SupportedAuthType.OAUTH2.equals(SupportedAuthType.fromString(getRequiredProperty(PROPERTY_AUTH_TYPE)));
    }

    /**
     *
     * @return the authentication type
     *
     * @throws IllegalArgumentException if the auth type property is not set, is empty,
     *         or contains an unsupported value.
     */
    public SupportedAuthType getAuthenticationType()
    {
        final String authType = getRequiredProperty(PROPERTY_AUTH_TYPE);
        return SupportedAuthType.fromString(authType);
    }

    /**
     * @return the username.
     *
     * @throws IllegalArgumentException if the username property is not set or is empty.
     */
    public String getUsername()
    {
        return getRequiredProperty(PROPERTY_AUTH_USERNAME);
    }

    /**
     * @return the password
     *
     * @throws IllegalArgumentException if the password property is not set or is empty.
     */
    public char[] getPassword()
    {
        return getRequiredProperty(PROPERTY_AUTH_PASSWORD).toCharArray();
    }

    /**
     * @return the rest configuration yaml
     */
    public String getCustomRestConfigYaml()
    {
        return getRequiredProperty(PROPERTY_CUSTOM_REST_CONFIG_YAML);
    }

    public SupportedRestModels getPredefinedRestConfigIdentifier()
    {
        return SupportedRestModels.fromString(getRequiredProperty(PROPERTY_PREDEFINED_REST_CONFIG_IDENTIFIER));
    }

    public boolean isCustomRestModel() {
        return SupportedRestModels.CUSTOM_MODEL.equals(this.getPredefinedRestConfigIdentifier());
    }

    public RestConfiguration getRestConfiguration()
    {
        final SupportedRestModels selectedRestModel = this.getPredefinedRestConfigIdentifier();
        final String restConfigYaml = isCustomRestModel() ? getCustomRestConfigYaml() : selectedRestModel.getModelResourceFileAsString();
        try {
            return objectMapper.readValue(restConfigYaml, RestConfiguration.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(RestMsgs.INVALID_YAML_CONFIG.format(e.getMessage()), e);
        }
    }

    /**
     * @return the OAuth 2.0 token URL.
     *
     * @throws IllegalArgumentException if the token URL property is not set or is empty.
     */
    public String getOAuth2TokenUrl()
    {
        return getRequiredProperty(PROPERTY_OAUTH2_TOKEN_URL);
    }

    /**
     * @return the OAuth 2.0 client ID.
     *
     * @throws IllegalArgumentException if the client ID property is not set or is empty.
     */
    public String getOAuth2ClientId()
    {
        return getRequiredProperty(PROPERTY_OAUTH2_CLIENT_ID);
    }

    /**
     * @return the OAuth 2.0 client secret.
     *
     * @throws IllegalArgumentException if the client secret property is not set or is empty.
     */
    public String getOAuth2ClientSecret()
    {
        return getRequiredProperty(PROPERTY_OAUTH2_CLIENT_SECRET);
    }

    /**
     * @return the OAuth 2.0 scope (optional).
     */
    public String getOAuth2Scope()
    {
        return getProperty(PROPERTY_OAUTH2_SCOPE);
    }

    /**
     * @return the OAuth 2.0 grant type (defaults to "client_credentials").
     */
    public SupportedOAuth2GrantType getOAuth2GrantType()
    {
        final String grantType = getRequiredProperty(PROPERTY_OAUTH2_GRANT_TYPE);
        return SupportedOAuth2GrantType.fromString(grantType);
    }

    /**
     * @return the OAuth 2.0 refresh token (optional, required for refresh_token grant type).
     */
    public String getOAuth2RefreshToken()
    {
        return getProperty(PROPERTY_OAUTH2_REFRESH_TOKEN);
    }
}
