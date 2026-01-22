/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.rest.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.ibm.connect.sdk.rest.pagination.PaginationType;
import com.ibm.connect.sdk.rest.utils.models.FieldDefinition;
import com.ibm.connect.sdk.rest.utils.models.Pagination;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetField;

public class RestUtils {
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
    private static final int DEFAULT_NUMERIC_PRECISION = 38;
    private static final int DEFAULT_NUMERIC_SCALE = 2;

    public static boolean isNullOrEmpty(String str) {
        return str == null || str.isBlank();
    }

    public static int determineNextLevelToDiscover(String path) {
        if(isNullOrEmpty(path) || "/".equals(path)) {
            return 1; //Root level
        }
        // Remove leading and trailing slashes
        final String normalizedPath = path.replaceAll("^/+", "").replaceAll("/+$", "");
        if (normalizedPath.isEmpty()) {
            return 1; // root
        }
        // Split by slash and count segments
        final String[] segments = normalizedPath.split("/");
        return segments.length + 1; // +1 because root is level 1
    }

    /**
     * Extract all placeholders from a string.
     * Example: "/orgs/${organization}/repos/${repository}" -> ["organization", "repository"]
     */
    public static Set<String> extractPlaceholders(String input) {
        final Set<String> placeholders = new HashSet<>();
        if(!isNullOrEmpty(input)) {
            final Matcher matcher = PLACEHOLDER_PATTERN.matcher(input);
            while (matcher.find()) {
                placeholders.add(matcher.group(1));
            }
        }
        return placeholders;
    }

    public static String buildUrl(String baseUrl, String endpointPath) {
        // 1 Basic validations
        if (isNullOrEmpty(baseUrl)) {
            throw new IllegalArgumentException("Base URL must not be null or empty");
        }

        // 2 Normalize slashes
        final String normalizedBase = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        final String normalizedEndpoint = Optional.ofNullable(endpointPath).map(path -> path.startsWith("/")
                ? endpointPath.substring(1)
                : endpointPath).orElse("");

        // 3 Join and normalize
        final String fullUrl = normalizedBase + normalizedEndpoint;

        // 4 Validate structure
        try {
            final URI uri = new URI(fullUrl);
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new IllegalArgumentException("Invalid URL constructed: " + fullUrl);
            }
            return uri.toString();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Malformed URL: " + fullUrl, e);
        }
    }

    /**
     * Map Json types into flight specific field types. Refer {@link com.ibm.connect.sdk.api.ArrowConversions} for supporting types in SDK
     *
     * @param jsonType Input Json type for the field
     * @return Support Flight field type
     */
    public static String mapToFlightAssetFieldType(String jsonType) {
        final String flightAssetType;
        switch (jsonType.toLowerCase(Locale.ENGLISH)) {
            case "boolean":
                flightAssetType = JsonAssetType.BOOLEAN.name().toLowerCase(Locale.ENGLISH);
                break;
            case "number":
                flightAssetType =  JsonAssetType.NUMERIC.name().toLowerCase(Locale.ENGLISH);
                break;
            case "binary":
                flightAssetType =  JsonAssetType.BINARY.name().toLowerCase(Locale.ENGLISH);
                break;
            default: //Array, Null
                flightAssetType =  JsonAssetType.VARCHAR.name().toLowerCase(Locale.ENGLISH);
                break;
        }

        return flightAssetType;
    }

    /**
     * Checks whether the pagination object has any meaningful value.
     * Detailed validation is already enforced by the JSON Schema (rest-configuration-schema.json).
     *
     * @return true if pagination is non-null and the type field is non-empty
     */
    public static boolean isPaginationSupported(Pagination pagination) {
        return pagination != null && !isNullOrEmpty(pagination.getType());
    }


    public static boolean isLinkHeaderPagination(Pagination pagination) {
        return isPaginationSupported(pagination) && PaginationType.LINK_HEADER.name().equalsIgnoreCase(pagination.getType());
    }

    public enum JsonAssetType {
       BOOLEAN, NUMERIC, BINARY, VARCHAR
    }

    public static List<CustomFlightAssetField> mapToFlightAssetFields(List<FieldDefinition> fieldDefinitions) {
        return Optional.ofNullable(fieldDefinitions).orElse(new ArrayList<>()).stream()
                .map(fieldDefinition -> {
                    final CustomFlightAssetField flightAssetField = new CustomFlightAssetField();
                    final String type = mapToFlightAssetFieldType(fieldDefinition.getType());
                    flightAssetField.setName(fieldDefinition.getName());
                    flightAssetField.setType(type);
                    if(JsonAssetType.NUMERIC.name().toLowerCase(Locale.ROOT).equals(type)) {
                        flightAssetField.setLength(DEFAULT_NUMERIC_PRECISION);
                        flightAssetField.setScale(DEFAULT_NUMERIC_SCALE);
                    }
                    return flightAssetField;
                }).collect(Collectors.toList());
    }


    public static String readResourceFile(String resourceFilePath) throws IOException {
        if (isNullOrEmpty(resourceFilePath)) {
            throw new IllegalArgumentException("Resource file path must not be null or empty");
        }

        final String normalizedPath = resourceFilePath.startsWith("/")
            ? resourceFilePath.substring(1)
            : resourceFilePath;
        try (InputStream inputStream = RestUtils.class.getClassLoader().getResourceAsStream(normalizedPath)) {
            if (inputStream == null) {
                throw new IOException("Resource file not found in classpath: " + resourceFilePath);
            }
            try(BufferedReader reader = new BufferedReader( new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining(System.lineSeparator()));
            }
        }
    }
}
