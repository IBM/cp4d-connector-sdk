/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String CONNECTOR_NAME_KEY = "$connector_name";
    private static final String CONNECTOR_LABEL_KEY = "$connector_label";
    private static final String CONNECTOR_DESCRIPTION_KEY = "$connector_description";
    private static final String METADATA_KEY = "$metadata";
    private static final String HOSTNAME_KEY = "$hostname";
    private static final String AUTHENTICATION_KEY = "$authentication";
    private static final String TABLES_KEY = "$tables";
    private static final String PATH_KEY = "$path";
    private static final String DATA_PATH_KEY = "$data_path";
    private static final String PAGINATION_KEY = "$pagination";
    private static final String KEY_MODIFIER = "$key";
    private static final String NOTNULL_MODIFIER = "$notnull";
    private static final String ARRAY_SUFFIX = "[]";

    private static final String[] ALL_MODIFIERS = { KEY_MODIFIER, NOTNULL_MODIFIER };

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
        final String content = new String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8);
        return parse(content);
    }

    /**
     * Loads and parses a JSON configuration from an {@link InputStream}.
     *
     * <p>This overload is intended for configs bundled as classpath resources, e.g.:
     * <pre>
     *   InputStream is = getClass().getResourceAsStream("/forge/mappings/my-connector.json");
     *   RestApiMapping mapping = RestApiMappingLoader.load(is);
     * </pre>
     *
     * <p>The caller is responsible for closing the stream.
     *
     * @param inputStream
     *            the input stream to read the JSON configuration from; must not be null
     * @return the parsed {@link RestApiMapping}
     * @throws IOException
     *             if the stream cannot be read or the JSON cannot be parsed
     */
    public static RestApiMapping load(InputStream inputStream) throws IOException
    {
        LOGGER.info("Loading REST API configuration from InputStream");
        final String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
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
        final JsonNode root = MAPPER.readTree(jsonContent);
        final ConnectorMetadata metadata = parseConnectorMetadata(root);
        final String baseUrl = parseBaseUrl(root);
        final AuthenticationType authenticationType = parseAuthenticationType(root);
        final Map<String, RestTableDefinition> tables = parseTables(root);
        final Map<String, String> metadataMap = parseMetadata(root);

        LOGGER.info("Loaded REST API configuration: connectorName='{}', authenticationType='{}', {} tables, metadata={}",
                metadata.connectorName, authenticationType.getValue(), tables.size(),
                metadataMap.isEmpty() ? "none" : metadataMap.keySet());
        return new RestApiMapping(metadata.connectorName, metadata.connectorLabel, metadata.connectorDescription,
                baseUrl, authenticationType, tables, metadataMap);
    }

    private static ConnectorMetadata parseConnectorMetadata(JsonNode root)
    {
        final String connectorName = getTextOrDefault(root, CONNECTOR_NAME_KEY, "REST Connector");
        final String connectorLabel = getTextOrDefault(root, CONNECTOR_LABEL_KEY, "REST Connector");
        final String connectorDescription = getTextOrDefault(root, CONNECTOR_DESCRIPTION_KEY, "REST API Connector");
        return new ConnectorMetadata(connectorName, connectorLabel, connectorDescription);
    }

    private static Map<String, String> parseMetadata(JsonNode root)
    {
        final Map<String, String> metadata = new LinkedHashMap<>();
        final JsonNode metadataNode = root.get(METADATA_KEY);

        if (metadataNode != null && metadataNode.isObject()) {
            final Iterator<Map.Entry<String, JsonNode>> fields = metadataNode.fields();
            while (fields.hasNext()) {
                final Map.Entry<String, JsonNode> field = fields.next();
                final JsonNode value = field.getValue();
                if (value != null && !value.isNull()) {
                    metadata.put(field.getKey(), value.asText());
                }
            }
            LOGGER.debug("Parsed metadata: {}", metadata);
        } else {
            LOGGER.debug("No $metadata section found in configuration");
        }

        return metadata;
    }

    private static String parseBaseUrl(JsonNode root) throws IOException
    {
        final JsonNode hostnameNode = root.get(HOSTNAME_KEY);
        if (hostnameNode == null || hostnameNode.isNull()) {
            throw new IOException("Missing required '$hostname' key in JSON configuration file");
        }
        return hostnameNode.asText();
    }

    private static AuthenticationType parseAuthenticationType(JsonNode root) throws IOException
    {
        final String rawAuthenticationType = getTextOrDefault(root, AUTHENTICATION_KEY, AuthenticationType.NONE.getValue());
        try {
            return AuthenticationType.fromValue(rawAuthenticationType);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid authentication type in configuration: " + e.getMessage(), e);
        }
    }

    private static Map<String, RestTableDefinition> parseTables(JsonNode root) throws IOException
    {
        final JsonNode tablesNode = root.get(TABLES_KEY);
        if (tablesNode == null || !tablesNode.isObject()) {
            throw new IOException("Missing or invalid '$tables' key in JSON configuration file");
        }

        final Map<String, RestTableDefinition> tables = new LinkedHashMap<>();
        final Iterator<Map.Entry<String, JsonNode>> tableEntries = tablesNode.fields();
        while (tableEntries.hasNext()) {
            final Map.Entry<String, JsonNode> tableEntry = tableEntries.next();
            final RestTableEntry parsedTable = parseTable(tableEntry);
            if (parsedTable != null) {
                tables.put(parsedTable.tableName, parsedTable.tableDefinition);
            }
        }
        return tables;
    }

    private static RestTableEntry parseTable(Map.Entry<String, JsonNode> tableEntry)
    {
        final String tableName = tableEntry.getKey();
        final JsonNode tableNode = tableEntry.getValue();

        if (!tableNode.isObject()) {
            LOGGER.warn("Skipping non-object table entry: {}", tableName);
            return null;
        }

        final String path = parseTablePath(tableName, tableNode);
        if (path == null) {
            return null;
        }

        final String dataPath = parseOptionalText(tableNode, DATA_PATH_KEY);
        final PaginationConfig paginationConfig = parsePaginationConfig(tableNode);
        final List<RestFieldDefinition> fields = parseFields(tableNode, "");

        logParsedTable(tableName, dataPath, paginationConfig, fields.size());
        return new RestTableEntry(tableName, new RestTableDefinition(path, dataPath, paginationConfig, fields));
    }

    private static String parseTablePath(String tableName, JsonNode tableNode)
    {
        final JsonNode pathNode = tableNode.get(PATH_KEY);
        if (pathNode == null || !pathNode.isArray() || pathNode.size() == 0) {
            LOGGER.warn("Skipping table '{}': missing or empty '$path' array", tableName);
            return null;
        }
        return pathNode.get(0).asText();
    }

    private static void logParsedTable(String tableName, String dataPath, PaginationConfig paginationConfig,
            int fieldCount)
    {
        if (paginationConfig != null && dataPath != null) {
            LOGGER.debug("Loaded table '{}' with data path '{}', pagination type '{}', and {} fields",
                    tableName, dataPath, paginationConfig.getType(), fieldCount);
        } else if (paginationConfig != null) {
            LOGGER.debug("Loaded table '{}' with pagination type '{}' and {} fields",
                    tableName, paginationConfig.getType(), fieldCount);
        } else if (dataPath != null) {
            LOGGER.debug("Loaded table '{}' with data path '{}' and {} fields", tableName, dataPath, fieldCount);
        } else {
            LOGGER.debug("Loaded table '{}' with {} fields", tableName, fieldCount);
        }
    }

    private static String getTextOrDefault(JsonNode node, String key, String defaultValue)
    {
        final JsonNode childNode = node.get(key);
        if (childNode == null || childNode.isNull()) {
            return defaultValue;
        }
        return childNode.asText();
    }

    private static String parseOptionalText(JsonNode node, String key)
    {
        final JsonNode childNode = node.get(key);
        if (childNode == null || childNode.isNull()) {
            return null;
        }
        return childNode.asText();
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

            if (rawKey.startsWith("$")) {
                continue;
            }

            final boolean isNestedObject = rawKey.endsWith(ARRAY_SUFFIX);
            final String baseFieldName = isNestedObject
                    ? rawKey.substring(0, rawKey.length() - ARRAY_SUFFIX.length()) : rawKey;
            final String fieldName = prefix + baseFieldName;

            if (isNestedObject && fieldValue.isObject()) {
                fields.addAll(parseFields(fieldValue, fieldName + "."));
            } else {
                final String rawType = fieldValue.asText();
                final boolean isKey = rawType.contains(KEY_MODIFIER);
                final boolean isNotNull = rawType.contains(NOTNULL_MODIFIER);
                final String typeString = removeModifiers(rawType);
                fields.add(new RestFieldDefinition(fieldName, typeString, isKey, isNotNull));
            }
        }

        return fields;
    }

    private static String removeModifiers(String rawType)
    {
        String result = rawType;
        for (final String modifier : ALL_MODIFIERS) {
            result = result.replace("," + modifier, "");
            result = result.replace(modifier + ",", "");
            result = result.replace(modifier, "");
        }
        return result.trim();
    }

    private static PaginationConfig parsePaginationConfig(JsonNode tableNode)
    {
        final JsonNode paginationNode = tableNode.get(PAGINATION_KEY);
        if (paginationNode == null || !paginationNode.isObject()) {
            return null;
        }

        final PaginationType type = parsePaginationType(paginationNode);
        if (type == null) {
            return null;
        }

        final int pageSize = paginationNode.hasNonNull("page_size") ? paginationNode.get("page_size").asInt() : 100;
        final String limitParam = parseOptionalText(paginationNode, "limit_param");

        String offsetParam = null;
        String pageParam = null;
        int initialOffset = 0;
        int initialPage = 1;
        String cursorParam = null;
        String nextCursorPath = null;
        String nextUrlPath = null;

        switch (type) {
        case OFFSET:
            offsetParam = getTextOrDefault(paginationNode, "offset_param", "offset");
            initialOffset = paginationNode.hasNonNull("initial_offset") ? paginationNode.get("initial_offset").asInt() : 0;
            break;
        case PAGE:
            pageParam = getTextOrDefault(paginationNode, "page_param", "page");
            initialPage = paginationNode.hasNonNull("initial_page") ? paginationNode.get("initial_page").asInt() : 1;
            break;
        case CURSOR:
            cursorParam = getTextOrDefault(paginationNode, "cursor_param", "cursor");
            nextCursorPath = parseRequiredPaginationField(paginationNode, "next_cursor_path",
                    "Cursor pagination requires 'next_cursor_path' field, ignoring pagination");
            if (nextCursorPath == null) {
                return null;
            }
            break;
        case NEXT_URL:
            nextUrlPath = parseRequiredPaginationField(paginationNode, "next_url_path",
                    "Next URL pagination requires 'next_url_path' field, ignoring pagination");
            if (nextUrlPath == null) {
                return null;
            }
            break;
        case LINK_HEADER:
        default:
            break;
        }

        LOGGER.debug("Parsed pagination config: type={}, pageSize={}, limitParam={}", type.getValue(), pageSize, limitParam);
        return new PaginationConfig(type, offsetParam, pageParam, limitParam, pageSize,
                initialOffset, initialPage, cursorParam, nextCursorPath, nextUrlPath);
    }

    private static PaginationType parsePaginationType(JsonNode paginationNode)
    {
        final String rawType = parseOptionalText(paginationNode, "type");
        if (rawType == null) {
            LOGGER.warn("Pagination configuration missing 'type' field, ignoring pagination");
            return null;
        }

        final PaginationType paginationType = PaginationType.fromValue(rawType);
        if (paginationType == null) {
            LOGGER.warn("Invalid pagination type '{}', ignoring pagination. Valid types: {}", rawType,
                    PaginationType.validValues());
        }
        return paginationType;
    }

    private static String parseRequiredPaginationField(JsonNode paginationNode, String fieldName,
            String warningMessage)
    {
        final String fieldValue = parseOptionalText(paginationNode, fieldName);
        if (fieldValue == null) {
            LOGGER.warn(warningMessage);
        }
        return fieldValue;
    }

    private static final class ConnectorMetadata
    {
        private final String connectorName;
        private final String connectorLabel;
        private final String connectorDescription;

        private ConnectorMetadata(String connectorName, String connectorLabel, String connectorDescription)
        {
            this.connectorName = connectorName;
            this.connectorLabel = connectorLabel;
            this.connectorDescription = connectorDescription;
        }
    }

    private static final class RestTableEntry
    {
        private final String tableName;
        private final RestTableDefinition tableDefinition;

        private RestTableEntry(String tableName, RestTableDefinition tableDefinition)
        {
            this.tableName = tableName;
            this.tableDefinition = tableDefinition;
        }
    }
}
