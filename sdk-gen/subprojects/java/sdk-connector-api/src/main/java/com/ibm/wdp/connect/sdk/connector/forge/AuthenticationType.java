/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.wdp.connect.sdk.connector.forge;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Supported authentication types for REST connector mappings.
 */
public enum AuthenticationType
{
    NONE("none"),
    API_KEY("api_key"),
    OAUTH2("oauth2"),
    BASIC("basic");

    private final String value;

    AuthenticationType(String value)
    {
        this.value = value;
    }

    /**
     * Returns the string value of this authentication type.
     *
     * @return the string value (e.g. "none", "api_key")
     */
    public String getValue()
    {
        return value;
    }

    /**
     * Returns the authentication type for the given string value (case-insensitive).
     *
     * @param value
     *            the string value to look up
     * @return the matching {@link AuthenticationType}
     * @throws IllegalArgumentException
     *             if the value is null or not recognised
     */
    public static AuthenticationType fromValue(String value)
    {
        if (value == null) {
            throw new IllegalArgumentException(
                    "Authentication type cannot be null. Valid values are: " + validValues());
        }

        final String normalizedValue = value.toLowerCase(Locale.ENGLISH);
        for (final AuthenticationType type : values()) {
            if (type.value.equals(normalizedValue)) {
                return type;
            }
        }
        throw new IllegalArgumentException(
                "Invalid authentication type: '" + value + "'. Valid values are: " + validValues());
    }

    /**
     * Returns a comma-separated list of valid authentication type values.
     *
     * @return the valid values string
     */
    public static String validValues()
    {
        return Arrays.stream(values())
                .map(AuthenticationType::getValue)
                .collect(Collectors.joining(", "));
    }
}

// Made with Bob
