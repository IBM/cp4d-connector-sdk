/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.rest;

import com.ibm.connect.sdk.util.ResourceBundleHelper;

/**
 * Localized messages for a basic row-based connector.
 */
@SuppressWarnings({ "PMD.AvoidDollarSigns", "PMD.ClassNamingConventions" })
public enum RestMsgs implements ResourceBundleHelper.MessageFormatter<RestMsgs>
{
    /**
     * Data source type not supported.
     */
    DATASOURCE_TYPE_NOT_SUPPORTED,

    /**
     * Connection properties are not valid
     */
    INVALID_CONNECTION_PROPERTIES,

    /**
     * `Http method` on Connection properties is not supported
     */
    INVALID_CONNECTION_PROPERTIES_HTTP_METHOD,

    /**
     * AuthType on connection properties is not valid.
     */
    INVALID_CONNECTION_PROPERTIES_AUTH_TYPE,

    /**
     * OAuth2 grant type is not valie
     */
    INVALID_CONNECTION_PROPERTIES_OAUTH2_GRANT_TYPE,

    /**
     * Missing username or password for basic auth type
     */
    MISSING_USERNAME_AND_PASSWORD_BASIC_AUTH,

    /**
     * Missing required properties
     */
    MISSING_REQUIRED_PROPERTY,

    /**
     * I/O exception while executing rest API URL
     */
    UNEXPECTED_RESPONSE_EXECUTING_REST_API,

    /**
     * Rest API URL not reachable
     */
    SERVER_NOT_REACHABLE,

    /**
     * SSL failed to initialise
     */
    SSL_INITIALISATION_FAILED,

    /**
     * Unsupported action to perform
     */
    UNSUPPORTED_ACTION,

    /**
     * Invalid yaml configuration
     */
    INVALID_YAML_CONFIG,

    /**
     * Internal error
     */
    INTERNAL_ERROR,

    INVALID_SELECTED_ENTITY_PARENT_VALUES,

    /**
     * OAuth 2.0 token acquisition failed
     */
    OAUTH2_TOKEN_ACQUISITION_FAILED,

    /**
     * Missing OAuth 2.0 configuration
     */
    MISSING_OAUTH2_CONFIGURATION,
    ;

    private static final ResourceBundleHelper<RestMsgs> BUNDLE = new ResourceBundleHelper<>(RestMsgs.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public String format(Object... args)
    {
        return BUNDLE.format(this, args);
    }

}
