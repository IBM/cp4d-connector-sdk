/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.rest;

import com.ibm.connect.sdk.util.ResourceBundleHelper;

/**
 * Localized labels for a basic row-based connector.
 */
@SuppressWarnings({ "PMD.AvoidDollarSigns", "PMD.ClassNamingConventions" })
public enum RestLabels implements ResourceBundleHelper.MessageFormatter<RestLabels>
{
    /**
     * Connector Rest label
     */
    REST_CONNECTOR_LABEL,

    /**
     * Connector Rest description
     */
    REST_CONNECTOR_DESCRIPTION,
    /**
     * Label for connection property base url.
     */
    API_BASE_URL_LABEL,

    /**
     * Description for connection property base url.
     */
    API_BASE_URL_DESCRIPTION,

    /**
     * Label for ssl certificate
     */
    API_SSL_CERTIFICATE_LABEL,

    /**
     * Description for ssl certificate
     */
    API_SSL_CERTIFICATE_DESCRIPTION,

    /**
     * Label for connection property authentication types
     */
    API_AUTH_TYPE_LABEL,

    /**
     * Description for connection property authentication types
     */
    API_AUTH_TYPE_DESCRIPTION,

    /**
     * Label for basic authentication username
     */
    API_AUTH_USERNAME_LABEL,

    /**
     * Description for basic authentication username
     */
    API_AUTH_USERNAME_DESCRIPTION,

    /**
     * Label for basic authentication password
     */
    API_AUTH_PASSWORD_LABEL,

    /**
     * Description for basic authentication password
     */
    API_AUTH_PASSWORD_DESCRIPTION,

    /**
     * Label for oauth2 grant type
     */
    API_AUTH_OAUTH2_GRANT_TYPE_LABEL,

    /**
     * Description for oauth2 grant type
     */
    API_AUTH_OAUTH2_GRANT_TYPE_DESCRIPTION,

    /**
     * Label for oauth token url
     */
    API_AUTH_OAUTH2_TOKEN_URL_LABEL,

    /**
     * Description for oauth token url
     */
    API_AUTH_OAUTH2_TOKEN_URL_DESCRIPTION,

    /**
     * Label for oauth client ID
     */
    API_AUTH_OAUTH2_CLIENT_ID_LABEL,

    /**
     * Description for oauth client ID
     */
    API_AUTH_OAUTH2_CLIENT_ID_DESCRIPTION,

    /**
     * Label for oauth client secret
     */
    API_AUTH_OAUTH2_CLIENT_SECRET_LABEL,

    /**
     * Description for oauth client secret
     */
    API_AUTH_OAUTH2_CLIENT_SECRET_DESCRIPTION,

    /**
     * Label for oauth scopes
     */
    API_AUTH_OAUTH2_SCOPE_LABEL,

    /**
     * Description for oauth scopes
     */
    API_AUTH_OAUTH2_SCOPE_DESCRIPTION,

    /**
     * Label for oauth refresh token
     */
    API_AUTH_OAUTH2_REFRESH_TOKEN_LABEL,

    /**
     * Description for oauth refresh token
     */
    API_AUTH_OAUTH2_REFRESH_TOKEN_DESCRIPTION,


    /**
     * Label for oauth2.0 grant type - Client credentials
     */
    API_AUTH_OAUTH2_GRANT_TYPE_CLIENT_CREDENTIALS_LABEL,

    /**
     * Label for oauth2.0 grant type - Refresh token
     */
    API_AUTH_OAUTH2_GRANT_TYPE_REFRESH_TOKEN_LABEL,

    /**
     * Label for none authentication types
     */
    API_AUTH_TYPE_NONE_LABEL,

    /**
     * Label for basic authentication type
     */
    API_AUTH_TYPE_BASIC_LABEL,

    /**
     * Label for OAuth 2.0 authentication type
     */
    API_AUTH_TYPE_OAUTH2_LABEL,

    /**
     * Label for connection property rest_config_yaml
     */
    API_REST_CONFIG_YAML_LABEL,

    /**
     * Description for connection property rest_config_yaml
     */
    API_REST_CONFIG_YAML_DESCRIPTION,

    ;

    private static final ResourceBundleHelper<RestLabels> BUNDLE = new ResourceBundleHelper<>(RestLabels.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public String format(Object... args)
    {
        return BUNDLE.format(this, args);
    }

}
