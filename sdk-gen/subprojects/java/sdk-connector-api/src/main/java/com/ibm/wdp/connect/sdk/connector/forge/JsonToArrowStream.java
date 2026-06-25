/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.wdp.connect.sdk.connector.forge;

import static org.slf4j.LoggerFactory.getLogger;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ibm.wdp.connect.sdk.connector.RowWriter;

/**
 * Fetches data from a REST API endpoint and streams each JSON object to a {@link RowWriter}.
 *
 * <p>This is the forge REST engine's JSON-to-Arrow streaming component. It replaces the old
 * {@code JsonToRecordStream} — the only change is that instead of creating {@code Record} objects
 * it calls {@link RowWriter#startRow()}, {@link RowWriter#set(String, Object)},
 * and {@link RowWriter#endRow()} for each JSON object.
 *
 * <p>The HTTP connection is opened lazily. The JSON response is parsed using Jackson's streaming
 * API to avoid loading the entire response into memory.
 *
 * <p>Supports all pagination strategies in {@link PaginationType}.
 */
public class JsonToArrowStream implements Closeable
{
    private static final Logger LOGGER = getLogger(JsonToArrowStream.class);

    private static final int HTTP_TIMEOUT_SECONDS = 60;
    private static final int HTTP_OK = 200;
    private static final int MAX_PAGES = 10000;

    private final String baseUrl;
    private final String dataPath;
    private final List<RestFieldDefinition> fieldDefs;
    private final Map<String, String> authHeaders;
    private final PaginationConfig paginationConfig;
    private final ObjectMapper objectMapper;

    /**
     * Creates a streaming instance for the given URL and field definitions.
     *
     * @param url
     *            the full URL to fetch (e.g. "https://api.spacexdata.com/v4/rockets")
     * @param fieldDefs
     *            the field definitions that define the expected fields and their types
     */
    public JsonToArrowStream(String url, List<RestFieldDefinition> fieldDefs)
    {
        this(url, null, fieldDefs, null, null);
    }

    /**
     * Creates a streaming instance with authentication headers.
     *
     * @param url
     *            the full URL to fetch
     * @param fieldDefs
     *            the field definitions
     * @param authHeaders
     *            optional authentication headers (may be null)
     */
    public JsonToArrowStream(String url, List<RestFieldDefinition> fieldDefs, Map<String, String> authHeaders)
    {
        this(url, null, fieldDefs, authHeaders, null);
    }

    /**
     * Creates a streaming instance with data path and authentication headers.
     *
     * @param url
     *            the full URL to fetch
     * @param dataPath
     *            optional JSON path to the data array (e.g. "result" for {"result": [...]})
     * @param fieldDefs
     *            the field definitions
     * @param authHeaders
     *            optional authentication headers (may be null)
     */
    public JsonToArrowStream(String url, String dataPath, List<RestFieldDefinition> fieldDefs,
            Map<String, String> authHeaders)
    {
        this(url, dataPath, fieldDefs, authHeaders, null);
    }

    /**
     * Creates a streaming instance with full configuration including pagination support.
     *
     * @param url
     *            the base URL to fetch
     * @param dataPath
     *            optional JSON path to the data array
     * @param fieldDefs
     *            the field definitions
     * @param authHeaders
     *            optional authentication headers (may be null)
     * @param paginationConfig
     *            optional pagination configuration (may be null for non-paginated APIs)
     */
    public JsonToArrowStream(String url, String dataPath, List<RestFieldDefinition> fieldDefs,
            Map<String, String> authHeaders, PaginationConfig paginationConfig)
    {
        this.baseUrl = url;
        this.dataPath = dataPath;
        this.fieldDefs = fieldDefs;
        this.authHeaders = authHeaders;
        this.paginationConfig = paginationConfig;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Fetches all data from the configured URL and streams it into the provided {@link RowWriter}.
     *
     * <p>For each JSON object in the response, calls:
     * <ol>
     *   <li>{@link RowWriter#startRow()}</li>
     *   <li>{@link RowWriter#set(String, Object)} for each field</li>
     *   <li>{@link RowWriter#endRow()}</li>
     * </ol>
     *
     * @param writer
     *            the row writer to receive the data
     * @throws IOException
     *             if an HTTP or JSON error occurs
     * @throws InterruptedException
     *             if the thread is interrupted during an HTTP request
     */
    public void streamTo(RowWriter writer) throws IOException, InterruptedException
    {
        final HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                .build();

        // Pagination state
        int currentOffset = paginationConfig != null ? paginationConfig.getInitialOffset() : 0;
        int currentPage = paginationConfig != null ? paginationConfig.getInitialPage() : 1;
        String nextCursor = null;
        String nextPageUrl = null;
        int totalPagesFetched = 0;

        do {
            if (totalPagesFetched >= MAX_PAGES) {
                LOGGER.warn("Reached maximum page limit of {}", MAX_PAGES);
                break;
            }

            final String url = buildCurrentPageUrl(currentOffset, currentPage, nextCursor, nextPageUrl);
            LOGGER.debug("Fetching page {}: {}", totalPagesFetched + 1, url);

            final HttpRequest request = buildRequest(url);
            final HttpResponse<InputStream> response = httpClient.send(request, BodyHandlers.ofInputStream());

            if (response.statusCode() != HTTP_OK) {
                throw new IOException("HTTP request failed with status " + response.statusCode());
            }

            final java.net.http.HttpHeaders headers = response.headers();

            // For link_header pagination, extract next URL from response headers
            if (paginationConfig != null && "link_header".equals(paginationConfig.getType())) {
                nextPageUrl = extractLinkHeader(headers);
            }

            totalPagesFetched++;
            int recordsInPage = 0;

            // Cursor and next_url need full parse to extract metadata
            if (paginationConfig != null &&
                ("cursor".equals(paginationConfig.getType()) || "next_url".equals(paginationConfig.getType()))) {

                final JsonNode rootNode = objectMapper.readTree(response.body());

                if ("cursor".equals(paginationConfig.getType())) {
                    final JsonNode cursorNode = extractJsonPath(rootNode, paginationConfig.getNextCursorPath());
                    nextCursor = (cursorNode != null && !cursorNode.isNull()) ? cursorNode.asText() : null;
                } else {
                    final JsonNode urlNode = extractJsonPath(rootNode, paginationConfig.getNextUrlPath());
                    nextPageUrl = (urlNode != null && !urlNode.isNull()) ? urlNode.asText() : null;
                }

                JsonNode dataNode = rootNode;
                if (dataPath != null && !dataPath.isEmpty()) {
                    dataNode = extractJsonPath(rootNode, dataPath);
                    if (dataNode == null) {
                        throw new IOException("Data path '" + dataPath + "' not found in JSON response");
                    }
                }

                recordsInPage = streamNodeToWriter(dataNode, writer);

            } else {
                // Streaming parse for offset/page/link_header/no-pagination
                final JsonFactory factory = new JsonFactory();
                try (JsonParser jsonParser = factory.createParser(response.body())) {
                    recordsInPage = streamParserToWriter(jsonParser, writer);
                }
            }

            // Advance pagination state
            if (paginationConfig != null) {
                if ("offset".equals(paginationConfig.getType())) {
                    currentOffset += paginationConfig.getPageSize();
                } else if ("page".equals(paginationConfig.getType())) {
                    currentPage++;
                }
            }

            // Determine if there are more pages
            if (!hasMorePages(paginationConfig, recordsInPage, nextCursor, nextPageUrl, totalPagesFetched)) {
                break;
            }

        } while (true);
    }

    /** {@inheritDoc} */
    @Override
    public void close()
    {
        // No persistent resources to close in this stateless implementation
    }

    // ---- private helpers ----

    private int streamNodeToWriter(JsonNode dataNode, RowWriter writer) throws IOException
    {
        int count = 0;
        if (dataNode.isArray()) {
            for (final JsonNode item : dataNode) {
                if (item.isObject()) {
                    writeObjectToWriter((ObjectNode) item, writer);
                    count++;
                }
            }
        } else if (dataNode.isObject()) {
            writeObjectToWriter((ObjectNode) dataNode, writer);
            count = 1;
        }
        return count;
    }

    private int streamParserToWriter(JsonParser jsonParser, RowWriter writer) throws IOException
    {
        int count = 0;
        JsonToken firstToken = jsonParser.nextToken();

        if (dataPath != null && !dataPath.isEmpty()) {
            if (firstToken == JsonToken.START_ARRAY) {
                // Root is an array (e.g. NBP API returns [{...}]) — fall back to tree-mode
                // so that extractJsonPath can handle numeric index segments like "0.rates".
                // JsonParser is already positioned AT START_ARRAY so readTree reads the full array.
                final JsonNode rootNode = objectMapper.readTree(jsonParser);
                final JsonNode dataNode = extractJsonPath(rootNode, dataPath);
                if (dataNode == null) {
                    throw new IOException("Data path '" + dataPath + "' not found in JSON response");
                }
                return streamNodeToWriter(dataNode, writer);
            }
            if (firstToken != JsonToken.START_OBJECT) {
                throw new IOException("Expected START_OBJECT or START_ARRAY at root when dataPath is specified, got: " + firstToken);
            }
            boolean found = false;
            while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
                if (dataPath.equals(jsonParser.currentName())) {
                    firstToken = jsonParser.nextToken();
                    found = true;
                    break;
                }
                jsonParser.skipChildren();
            }
            if (!found) {
                throw new IOException("Data path '" + dataPath + "' not found in JSON response");
            }
        }

        if (firstToken == JsonToken.START_ARRAY) {
            JsonToken token;
            while ((token = jsonParser.nextToken()) != JsonToken.END_ARRAY && token != null) {
                if (token == JsonToken.START_OBJECT) {
                    final ObjectNode objectNode = objectMapper.readTree(jsonParser);
                    writeObjectToWriter(objectNode, writer);
                    count++;
                }
            }
        } else if (firstToken == JsonToken.START_OBJECT) {
            final ObjectNode objectNode = objectMapper.readTree(jsonParser);
            writeObjectToWriter(objectNode, writer);
            count = 1;
        }

        return count;
    }

    private void writeObjectToWriter(ObjectNode objectNode, RowWriter writer) throws IOException
    {
        writer.startRow();
        for (final RestFieldDefinition fieldDef : fieldDefs) {
            final String fieldName = fieldDef.getName();
            final JsonNode valueNode = fieldName.contains(".")
                    ? getNestedValue(objectNode, fieldName)
                    : objectNode.get(fieldName);
            writer.set(fieldName, convertValue(valueNode, fieldDef));
        }
        writer.endRow();
    }

    private static JsonNode getNestedValue(ObjectNode objectNode, String flattenedName)
    {
        final int dotIndex = flattenedName.indexOf('.');
        if (dotIndex < 0) {
            return objectNode.get(flattenedName);
        }
        final String parentKey = flattenedName.substring(0, dotIndex);
        final String remainingPath = flattenedName.substring(dotIndex + 1);
        final JsonNode parentNode = objectNode.get(parentKey);
        if (parentNode == null || !parentNode.isObject()) {
            return null;
        }
        if (remainingPath.contains(".")) {
            return getNestedValue((ObjectNode) parentNode, remainingPath);
        }
        return parentNode.get(remainingPath);
    }

    private static Object convertValue(JsonNode node, RestFieldDefinition fieldDef)
    {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject() || node.isArray()) {
            return node.toString();
        }

        final String baseType = extractBaseType(fieldDef.getTypeString().toLowerCase(Locale.ENGLISH));

        switch (baseType) {
        case "integer":
        case "int":
        case "smallint":
        case "tinyint":
            return node.isNumber() ? node.intValue() : parseIntSafe(node.asText());
        case "bigint":
            return node.isNumber() ? node.longValue() : parseLongSafe(node.asText());
        case "boolean":
        case "bool":
        case "bit":
            return node.isBoolean() ? node.booleanValue() : Boolean.parseBoolean(node.asText());
        case "double":
        case "float8":
        case "float":
        case "real":
        case "float4":
            return node.isNumber() ? node.doubleValue() : parseDoubleSafe(node.asText());
        case "date":
            return parseDateSafe(node.asText());
        case "timestamp":
        case "datetime":
            return parseTimestampSafe(node.asText());
        case "json":
        case "jsonb":
        case "array":
        case "object":
            return node.toString();
        default:
            return node.asText();
        }
    }

    private static String extractBaseType(String typeLower)
    {
        final int parenIdx = typeLower.indexOf('(');
        return parenIdx >= 0 ? typeLower.substring(0, parenIdx).trim() : typeLower.trim();
    }

    private static Integer parseIntSafe(String text)
    {
        if (text == null || text.isEmpty()) return null;
        try { return Integer.parseInt(text.trim()); }
        catch (NumberFormatException e) { LOGGER.warn("Cannot parse integer: '{}'", text); return null; }
    }

    private static Long parseLongSafe(String text)
    {
        if (text == null || text.isEmpty()) return null;
        try { return Long.parseLong(text.trim()); }
        catch (NumberFormatException e) { LOGGER.warn("Cannot parse long: '{}'", text); return null; }
    }

    private static Double parseDoubleSafe(String text)
    {
        if (text == null || text.isEmpty()) return null;
        try { return Double.parseDouble(text.trim()); }
        catch (NumberFormatException e) { LOGGER.warn("Cannot parse double: '{}'", text); return null; }
    }

    private static Date parseDateSafe(String text)
    {
        if (text == null || text.isEmpty()) return null;
        try { return Date.valueOf(LocalDate.parse(text.trim())); }
        catch (DateTimeParseException e) { LOGGER.warn("Cannot parse date: '{}'", text); return null; }
    }

    private static Timestamp parseTimestampSafe(String text)
    {
        if (text == null || text.isEmpty()) return null;
        try { return Timestamp.from(Instant.parse(text.trim())); }
        catch (DateTimeParseException e) {
            try { return Timestamp.valueOf(text.trim().replace("T", " ").replaceAll("\\.\\d+Z?$", "")); }
            catch (Exception ex) { LOGGER.warn("Cannot parse timestamp: '{}'", text); return null; }
        }
    }

    private HttpRequest buildRequest(String url)
    {
        final HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                .header("Accept", "application/json")
                .header("User-Agent", "CP4D-REST-Connector/1.0")
                .GET();
        if (authHeaders != null) {
            authHeaders.forEach(builder::header);
        }
        return builder.build();
    }

    private String buildCurrentPageUrl(int currentOffset, int currentPage, String nextCursor, String nextPageUrl)
    {
        if (paginationConfig == null) {
            return baseUrl;
        }

        final String type = paginationConfig.getType();
        final boolean hasParams = baseUrl.contains("?");
        final String sep = hasParams ? "&" : "?";
        final StringBuilder sb = new StringBuilder(baseUrl);

        switch (type) {
        case "offset":
            sb.append(sep).append(paginationConfig.getOffsetParam()).append('=').append(currentOffset);
            if (paginationConfig.getLimitParam() != null) {
                sb.append('&').append(paginationConfig.getLimitParam()).append('=').append(paginationConfig.getPageSize());
            }
            break;
        case "page":
            sb.append(sep).append(paginationConfig.getPageParam()).append('=').append(currentPage);
            if (paginationConfig.getLimitParam() != null) {
                sb.append('&').append(paginationConfig.getLimitParam()).append('=').append(paginationConfig.getPageSize());
            }
            break;
        case "cursor":
            if (nextCursor != null && !nextCursor.isEmpty()) {
                sb.append(sep).append(paginationConfig.getCursorParam()).append('=').append(nextCursor);
                if (paginationConfig.getLimitParam() != null) {
                    sb.append('&').append(paginationConfig.getLimitParam()).append('=').append(paginationConfig.getPageSize());
                }
            } else if (paginationConfig.getLimitParam() != null) {
                sb.append(sep).append(paginationConfig.getLimitParam()).append('=').append(paginationConfig.getPageSize());
            }
            break;
        case "link_header":
        case "next_url":
            if (nextPageUrl != null && !nextPageUrl.isEmpty()) {
                return nextPageUrl;
            }
            if (paginationConfig.getLimitParam() != null) {
                sb.append(sep).append(paginationConfig.getLimitParam()).append('=').append(paginationConfig.getPageSize());
            }
            break;
        default:
            break;
        }

        return sb.toString();
    }

    private static boolean hasMorePages(PaginationConfig config, int recordsInPage, String nextCursor,
            String nextPageUrl, int totalPagesFetched)
    {
        if (config == null || totalPagesFetched >= MAX_PAGES) {
            return false;
        }
        switch (config.getType()) {
        case "offset":
        case "page":
            return recordsInPage >= config.getPageSize();
        case "cursor":
            return nextCursor != null && !nextCursor.isEmpty();
        case "link_header":
        case "next_url":
            return nextPageUrl != null && !nextPageUrl.isEmpty();
        default:
            return false;
        }
    }

    private static String extractLinkHeader(java.net.http.HttpHeaders headers)
    {
        final java.util.Optional<String> linkHeader = headers.firstValue("Link");
        if (!linkHeader.isPresent()) {
            return null;
        }
        for (final String link : linkHeader.get().split(",")) {
            if (link.contains("rel=\"next\"") || link.contains("rel='next'")) {
                final int start = link.indexOf('<') + 1;
                final int end = link.indexOf('>');
                if (start > 0 && end > start) {
                    return link.substring(start, end);
                }
            }
        }
        return null;
    }

    private static JsonNode extractJsonPath(JsonNode node, String path)
    {
        if (path == null || path.isEmpty()) {
            return node;
        }
        JsonNode current = node;
        for (final String segment : path.split("\\.")) {
            if (current == null || current.isNull()) {
                return null;
            }
            if (current.isArray()) {
                // ArrayNode.get(String) returns null — must use get(int)
                try {
                    current = current.get(Integer.parseInt(segment));
                } catch (NumberFormatException e) {
                    return null; // non-numeric key on an array — path not found
                }
            } else {
                current = current.get(segment);
            }
        }
        return current;
    }
}

// Made with Bob
