/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.rest.pagination;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Enum representing supported pagination types.
 */
public enum PaginationType {
    /**
     * Offset-based pagination using offset and limit parameters.
     */
    OFFSET,

    /**
     * Page-based pagination using page number and page size parameters.
     */
    PAGE,

    /**
     * Header contains the information about how to fetch next page
     */
    LINK_HEADER

    ;

    /**
     * Comma seperated supporting auth types
     */
    public static String getSupportedPaginationTypes() {
        return Arrays.stream(PaginationType.values())
                .map(val -> val.name().toLowerCase(Locale.ENGLISH))
                .collect(Collectors.joining(","));
    }

    /**
     * Converts a string to the corresponding PaginationType enum.
     * 
     * @param type the string representation of the pagination type
     * @return the corresponding PaginationType enum
     * @throws IllegalArgumentException if the type is not supported
     */
    public static PaginationType fromString(String type) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Pagination type cannot be null or empty");
        }

        try {
            return PaginationType.valueOf(type.trim().toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    String.format("Unsupported pagination type: '%s'. Supported types are: '%s'", type, getSupportedPaginationTypes()),
                    e);
        }
    }
}
