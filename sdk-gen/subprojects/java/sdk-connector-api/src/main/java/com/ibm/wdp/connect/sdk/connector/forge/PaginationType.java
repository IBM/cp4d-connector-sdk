/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.wdp.connect.sdk.connector.forge;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Supported pagination strategies for REST connector mappings.
 */
public enum PaginationType
{
    OFFSET("offset"),
    PAGE("page"),
    CURSOR("cursor"),
    LINK_HEADER("link_header"),
    NEXT_URL("next_url");

    private final String value;

    PaginationType(String value)
    {
        this.value = value;
    }

    /**
     * Returns the string value of this pagination type.
     *
     * @return the string value (e.g. "offset", "page")
     */
    public String getValue()
    {
        return value;
    }

    /**
     * Returns the pagination type for the given string value (case-insensitive), or null if not recognised.
     *
     * @param value
     *            the string value to look up; null returns null
     * @return the matching {@link PaginationType}, or null
     */
    public static PaginationType fromValue(String value)
    {
        if (value == null) {
            return null;
        }

        final String normalizedValue = value.toLowerCase(Locale.ENGLISH);
        for (final PaginationType type : values()) {
            if (type.value.equals(normalizedValue)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Returns a comma-separated list of valid pagination type values.
     *
     * @return the valid values string
     */
    public static String validValues()
    {
        return Arrays.stream(values())
                .map(PaginationType::getValue)
                .collect(Collectors.joining(", "));
    }
}

// Made with Bob
