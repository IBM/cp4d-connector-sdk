/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

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

    PaginationType(String value) { this.value = value; }
    public String getValue() { return value; }

    public static PaginationType fromValue(String value)
    {
        if (value == null) { return null; }
        final String normalizedValue = value.toLowerCase(Locale.ENGLISH);
        for (final PaginationType type : values()) {
            if (type.value.equals(normalizedValue)) { return type; }
        }
        return null;
    }

    public static String validValues()
    {
        return Arrays.stream(values())
                .map(PaginationType::getValue)
                .collect(Collectors.joining(", "));
    }
}
