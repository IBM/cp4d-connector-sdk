/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Loads and parses a JSON API configuration file into a {@link RestApiMapping}.
 *
 * <p>The JSON file has the following structure:
 * <pre>
 * {
 *   "$connector_name": "Connector's name",
 *   "$connector_label": "Connector's label",
 *   "$connector_description": "Connector's description",
 *   "$hostname": "https://api.spacexdata.com:443",
 *   "$tables": {
 *     "capsules": {
 *       "$path": ["/v4/capsules"],
 *       "id": "VARCHAR,$key",
 *       "reuse_count": "INTEGER",
 *       "headquarters": {
 *         "address": "VARCHAR",
 *         "city": "VARCHAR"
 *       },
 *       "launches[]": "VARCHAR"
 *     }
 *   }
 * }
 * </pre>
 */
public class RestApiMappingLoader
{
    private static final Logger LOGGER = getLogger(RestApiMappingLoader.class);

    private static final String CONNECTOR_NAME_KEY = "$connector_name";
    private static final String CONNECTOR_LABEL_KEY = "$connector_label";
    private static final String CONNECTOR_DESCRIPTION_KEY = "$connector_description";
    private static final String HOSTNAME_KEY = "$hostname";
    private static final String AUTHENTICATION_KEY = "$authentication";
    private static final String TABLES_KEY = "$tables";
    private static final String PATH_KEY = "$path";
    private static final String DATA_PATH_KEY = "$data_path";
    private static final String PAGINATION_KEY = "$pagination";
    private static final String KEY_MODIFIER = "$key";
    private static final String NOTNULL_MODIFIER = "$notnull";
    private static final String ARRAY_SUFFIX = "[]";

    private RestApiMappingLoader()
    {
        // prevent instantiation
    }

    /**
     * Loads and parses a JSON configuration file from the given filesystem path.
     *
     * @param filePath
     *            the absolute or relative path to the JSON configuration file
     * @return the parsed {@link RestApiMapping}
     * @throws IOException
     *             if the file cannot be read or parsed
     */
    public static RestApiMapping load(String filePath) throws IOException
    {
        LOGGER.info("Loading REST API configuration from: {}", filePath);
        final String content = new String(Files.readAllBytes(Paths.get(filePath)));
        return parse(content);
    }

    /**
     * Parses a JSON configuration string into a {@link RestApiMapping}.
     *
     * @param jsonContent
     *            the JSON content of the configuration file
     * @return the parsed {@link RestApiMapping}
     * @throws IOException
     *             if the JSON cannot be parsed
     */
    public static RestApiMapping parse(String jsonContent) throws IOException
    {
        final ObjectMapper mapper = new ObjectMapper();
        final JsonNode root = mapper.readTree(jsonContent);

        // Extract connector metadata
        final JsonNode connectorNameNode = root.get(CONNECTOR_NAME_KEY);
        final String connectorName = (connectorNameNode != null && !connectorNameNode.isNull())
                ? connectorNameNode.asText() : "REST Connector";

        final JsonNode connectorLabelNode = root.get(CONNECTOR_LABEL_KEY);
        final String connectorLabel = (connectorLabelNode != null && !connectorLabelNode.isNull())
                ? connectorLabelNode.asText() : "REST Connector";

        final JsonNode connectorDescriptionNode = root.get(CONNECTOR_DESCRIPTION_KEY);
        final String connectorDescription = (connectorDescriptionNode != null && !connectorDescriptionNode.isNull())
                ? connectorDescriptionNode.asText() : "REST API Connector";

        // Extract base URL
        final JsonNode hostnameNode = root.get(HOSTNAME_KEY);
        if (hostnameNode == null || hostnameNode.isNull()) {
            throw new IOException("Missing required '$hostname' key in JSON configuration file");
        }
        final String baseUrl = hostnameNode.asText();

        // Extract authentication type (optional, defaults to "none")
        final JsonNode authenticationNode = root.get(AUTHENTICATION_KEY);
        final String authenticationType = (authenticationNode != null && !authenticationNode.isNull())
                ? authenticationNode.asText().toLowerCase(java.util.Locale.ENGLISH) : "none";
        
        // Validate authentication type
        if (!"none".equals(authenticationType) && !"api_key".equals(authenticationType)
                && !"oauth2".equals(authenticationType) && !"basic".equals(authenticationType)) {
            LOGGER.warn("Invalid authentication type '{}', defaulting to 'none'. Valid values: none, api_key, oauth2, basic",
                    authenticationType);
        }

        // Extract tables
        final JsonNode tablesNode = root.get(TABLES_KEY);
        if (tablesNode == null || !tablesNode.isObject()) {
            throw new IOException("Missing or invalid '$tables' key in JSON configuration file");
        }

        // Parse each table definition
        final Map<String, RestTableDefinition> tables = new LinkedHashMap<>();
        final Iterator<Map.Entry<String, JsonNode>> tableEntries = tablesNode.fields();
        while (tableEntries.hasNext()) {
            final Map.Entry<String, JsonNode> tableEntry = tableEntries.next();
            final String tableName = tableEntry.getKey();
            final JsonNode tableNode = tableEntry.getValue();

            if (!tableNode.isObject()) {
                LOGGER.warn("Skipping non-object table entry: {}", tableName);
                continue;
            }

            // Extract path
            final JsonNode pathNode = tableNode.get(PATH_KEY);
            if (pathNode == null || !pathNode.isArray() || pathNode.size() == 0) {
                LOGGER.warn("Skipping table '{}': missing or empty '$path' array", tableName);
                continue;
            }
            final String path = pathNode.get(0).asText();

            // Extract optional data path for nested responses
            final JsonNode dataPathNode = tableNode.get(DATA_PATH_KEY);
            final String dataPath = (dataPathNode != null && !dataPathNode.isNull())
                    ? dataPathNode.asText() : null;

            // Extract optional pagination configuration
            final PaginationConfig paginationConfig = parsePaginationConfig(tableNode);

            // Parse fields (including nested objects)
            final List<RestFieldDefinition> fields = parseFields(tableNode, "");

            tables.put(tableName, new RestTableDefinition(path, dataPath, paginationConfig, fields));
            
            // Log table loading with appropriate details
            if (paginationConfig != null && dataPath != null) {
                LOGGER.debug("Loaded table '{}' with path '{}', data path '{}', pagination type '{}', and {} fields",
                        tableName, path, dataPath, paginationConfig.getType(), fields.size());
            } else if (paginationConfig != null) {
                LOGGER.debug("Loaded table '{}' with path '{}', pagination type '{}', and {} fields",
                        tableName, path, paginationConfig.getType(), fields.size());
            } else if (dataPath != null) {
                LOGGER.debug("Loaded table '{}' with path '{}', data path '{}', and {} fields",
                        tableName, path, dataPath, fields.size());
            } else {
                LOGGER.debug("Loaded table '{}' with path '{}' and {} fields", tableName, path, fields.size());
            }
        }

        LOGGER.info("Loaded REST API configuration: connectorName='{}', baseUrl='{}', authenticationType='{}', {} tables",
                connectorName, baseUrl, authenticationType, tables.size());
        return new RestApiMapping(connectorName, connectorLabel, connectorDescription, baseUrl, authenticationType, tables);
    }

    /**
     * Parses the field definitions from a table JSON node, recursively handling nested objects.
     *
     * @param tableNode
     *            the JSON object representing a table or nested object
     * @param prefix
     *            the prefix for nested field names (e.g., "headquarters." for nested fields)
     * @return the list of field definitions
     */
    private static List<RestFieldDefinition> parseFields(JsonNode tableNode, String prefix)
    {
        final List<RestFieldDefinition> fields = new ArrayList<>();
        final Iterator<Map.Entry<String, JsonNode>> fieldEntries = tableNode.fields();

        while (fieldEntries.hasNext()) {
            final Map.Entry<String, JsonNode> fieldEntry = fieldEntries.next();
            final String rawKey = fieldEntry.getKey();
            final JsonNode fieldValue = fieldEntry.getValue();

            // Skip special keys that start with $
            if (rawKey.startsWith("$")) {
                continue;
            }

            // Check if this is a nested object field (key ends with [])
            final boolean isNestedObject = rawKey.endsWith(ARRAY_SUFFIX);
            final String baseFieldName = isNestedObject ? rawKey.substring(0, rawKey.length() - ARRAY_SUFFIX.length()) : rawKey;
            final String fieldName = prefix + baseFieldName;

            if (isNestedObject && fieldValue.isObject()) {
                // Nested object with [] — flatten its fields with dot separator
                final List<RestFieldDefinition> nestedFields = parseFields(fieldValue, fieldName + ".");
                fields.addAll(nestedFields);
            } else {
                // Simple field with type string like "VARCHAR,$key,$notnull" or "INTEGER"
                final String rawType = fieldValue.asText();
                
                // Parse modifiers
                final boolean isKey = rawType.contains(KEY_MODIFIER);
                final boolean isNotNull = rawType.contains(NOTNULL_MODIFIER);
                
                // Remove modifiers from the type string
                String typeString = rawType;
                typeString = typeString.replace("," + KEY_MODIFIER, "");
                typeString = typeString.replace(KEY_MODIFIER + ",", "");
                typeString = typeString.replace(KEY_MODIFIER, "");
                typeString = typeString.replace("," + NOTNULL_MODIFIER, "");
                typeString = typeString.replace(NOTNULL_MODIFIER + ",", "");
                typeString = typeString.replace(NOTNULL_MODIFIER, "");
                typeString = typeString.trim();

                fields.add(new RestFieldDefinition(fieldName, typeString, isKey, isNotNull));
            }
        }

        return fields;
    }

    /**
     * Parses the pagination configuration from a table JSON node.
     *
     * @param tableNode
     *            the JSON object representing a table
     * @return the pagination configuration, or null if no pagination is configured
     */
    private static PaginationConfig parsePaginationConfig(JsonNode tableNode)
    {
        final JsonNode paginationNode = tableNode.get(PAGINATION_KEY);
        if (paginationNode == null || !paginationNode.isObject()) {
            return null;
        }

        // Extract pagination type (required)
        final JsonNode typeNode = paginationNode.get("type");
        if (typeNode == null || typeNode.isNull()) {
            LOGGER.warn("Pagination configuration missing 'type' field, ignoring pagination");
            return null;
        }
        final String type = typeNode.asText().toLowerCase(java.util.Locale.ENGLISH);

        // Validate pagination type
        if (!"offset".equals(type) && !"page".equals(type) && !"cursor".equals(type)
                && !"link_header".equals(type) && !"next_url".equals(type)) {
            LOGGER.warn("Invalid pagination type '{}', ignoring pagination. Valid types: offset, page, cursor, link_header, next_url", type);
            return null;
        }

        // Extract common fields
        final JsonNode pageSizeNode = paginationNode.get("page_size");
        final int pageSize = (pageSizeNode != null && !pageSizeNode.isNull()) ? pageSizeNode.asInt() : 100;

        final JsonNode limitParamNode = paginationNode.get("limit_param");
        final String limitParam = (limitParamNode != null && !limitParamNode.isNull()) ? limitParamNode.asText() : null;

        // Extract type-specific fields
        String offsetParam = null;
        String pageParam = null;
        int initialOffset = 0;
        int initialPage = 1;
        String cursorParam = null;
        String nextCursorPath = null;
        String nextUrlPath = null;

        if ("offset".equals(type)) {
            final JsonNode offsetParamNode = paginationNode.get("offset_param");
            offsetParam = (offsetParamNode != null && !offsetParamNode.isNull()) ? offsetParamNode.asText() : "offset";

            final JsonNode initialOffsetNode = paginationNode.get("initial_offset");
            initialOffset = (initialOffsetNode != null && !initialOffsetNode.isNull()) ? initialOffsetNode.asInt() : 0;
        }
        else if ("page".equals(type)) {
            final JsonNode pageParamNode = paginationNode.get("page_param");
            pageParam = (pageParamNode != null && !pageParamNode.isNull()) ? pageParamNode.asText() : "page";

            final JsonNode initialPageNode = paginationNode.get("initial_page");
            initialPage = (initialPageNode != null && !initialPageNode.isNull()) ? initialPageNode.asInt() : 1;
        }
        else if ("cursor".equals(type)) {
            final JsonNode cursorParamNode = paginationNode.get("cursor_param");
            cursorParam = (cursorParamNode != null && !cursorParamNode.isNull()) ? cursorParamNode.asText() : "cursor";

            final JsonNode nextCursorPathNode = paginationNode.get("next_cursor_path");
            if (nextCursorPathNode == null || nextCursorPathNode.isNull()) {
                LOGGER.warn("Cursor pagination requires 'next_cursor_path' field, ignoring pagination");
                return null;
            }
            nextCursorPath = nextCursorPathNode.asText();
        }
        else if ("next_url".equals(type)) {
            final JsonNode nextUrlPathNode = paginationNode.get("next_url_path");
            if (nextUrlPathNode == null || nextUrlPathNode.isNull()) {
                LOGGER.warn("Next URL pagination requires 'next_url_path' field, ignoring pagination");
                return null;
            }
            nextUrlPath = nextUrlPathNode.asText();
        }

        LOGGER.debug("Parsed pagination config: type={}, pageSize={}, limitParam={}", type, pageSize, limitParam);
        return new PaginationConfig(type, offsetParam, pageParam, limitParam, pageSize,
                initialOffset, initialPage, cursorParam, nextCursorPath, nextUrlPath);
    }
}

// Made with Bob
