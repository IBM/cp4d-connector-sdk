/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.rest.utils;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

import org.apache.hc.core5.util.Timeout;

import com.ibm.connect.sdk.rest.RestMsgs;

public class RestApiConstants {
    //Rest Connector Constants
    public static final Timeout HTTP_CLIENT_DEFAULT_CONNECT_TIMEOUT = Timeout.ofMinutes(3);
    public static final int HTTP_POOL_DEFAULT_MAX_CONNECTION = 100;
    public static final int HTTP_POOL_DEFAULT_MAX_CONNECTION_PER_ROUTE = 10;

    public enum SupportedAuthType {
        NONE,
        BASIC,
        OAUTH2;

        /**
         * Comma seperated supporting auth types
         */
        public static String getSupportedAuthTypes() {
            return Arrays.stream(SupportedAuthType.values()).map(Enum::name).collect(Collectors.joining(","));
        }

        public static SupportedAuthType fromString(String value) {
            if (RestUtils.isNullOrEmpty(value)) {
                return NONE;
            }
            try {
                return SupportedAuthType.valueOf(value.toUpperCase(Locale.ENGLISH));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(RestMsgs.INVALID_CONNECTION_PROPERTIES_AUTH_TYPE.format(value, getSupportedAuthTypes()), e);
            }
        }
    }

    public enum SupportedOAuth2GrantType {
        CLIENT_CREDENTIALS("client_credentials"),
        REFRESH_TOKEN("refresh_token");

        private final String value;

        SupportedOAuth2GrantType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        /**
         * Comma seperated supporting oauth2 grant types
         */
        public static String getSupportedGrantTypes() {
            return Arrays.stream(SupportedOAuth2GrantType.values()).map(Enum::name).collect(Collectors.joining(","));
        }

        public static SupportedOAuth2GrantType fromString(String value) {
            if (RestUtils.isNullOrEmpty(value)) {
                return CLIENT_CREDENTIALS;
            }
            try {
                return SupportedOAuth2GrantType.valueOf(value.toUpperCase(Locale.ENGLISH));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(RestMsgs.INVALID_CONNECTION_PROPERTIES_OAUTH2_GRANT_TYPE.format(value, getSupportedGrantTypes()), e);
            }
        }
    }

    public enum SupportedSourceHttpMethod {
        GET,
        POST;

        /**
         * Comma seperated supporting auth types
         */
        public static String getSupportedSourceHttpMethods() {
            return Arrays.stream(SupportedSourceHttpMethod.values()).map(Enum::name).collect(Collectors.joining(","));
        }

        @SuppressWarnings("PMD.PreserveStackTrace")
        public static SupportedSourceHttpMethod fromString(String value) {
            if (RestUtils.isNullOrEmpty(value)) {
                return GET;
            }
            try {
                return SupportedSourceHttpMethod.valueOf(value.toUpperCase(Locale.ENGLISH));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(RestMsgs.INVALID_CONNECTION_PROPERTIES_HTTP_METHOD.format(value, getSupportedSourceHttpMethods()));
            }
        }
    }
}
