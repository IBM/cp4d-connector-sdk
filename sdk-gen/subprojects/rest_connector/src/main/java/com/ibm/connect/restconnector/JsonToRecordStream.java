/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

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
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

import org.slf4j.Logger;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ibm.connect.sdk.api.Record;

/**
 * A streaming iterator that fetches data from a REST API endpoint and converts
 * each JSON object in the response array into a {@link Record}.
 *
 * <p>The HTTP connection is opened lazily on the first call to {@link #hasNext()}.
 * The JSON response is parsed using Jackson's streaming API to avoid loading
 * the entire response into memory.
 *
 * <p>The response is expected to be a JSON array of objects. Each object is
 * converted to a {@link Record} with values in the order defined by the
 * field definitions.
 */
public class JsonToRecordStream implements Iterator<Record>, Closeable
{
    private static final Logger LOGGER = getLogger(JsonToRecordStream.class);

    private static final int HTTP_TIMEOUT_SECONDS = 60;
    private static final int HTTP_OK = 200;
    private static final int MAX_PAGES = 10000; // Safety limit to prevent infinite loops

    private final String baseUrl;
    private final String dataPath;
    private final List<RestFieldDefinition> fieldDefs;
    private final java.util.Map<String, String> authHeaders;
    private final PaginationConfig paginationConfig;
    private final ObjectMapper objectMapper;

    private HttpClient httpClient;
    private InputStream responseStream;
    private JsonParser jsonParser;
    private java.net.http.HttpHeaders lastResponseHeaders;

    private Record nextRecord;
    private boolean initialized;
    private boolean done;

    // Pagination state
    private int currentOffset;
    private int currentPage;
    private int recordsInCurrentPage;
    private String nextCursor;
    private String nextPageUrl;
    private int totalPagesFetched;

    /**
     * Creates a streaming record iterator for the given URL and field definitions.
     *
     * @param url
     *            the full URL to fetch (e.g. "https://api.spacexdata.com/v4/rockets")
     * @param fieldDefs
     *            the field definitions that define the expected fields and their types
     */
    public JsonToRecordStream(String url, List<RestFieldDefinition> fieldDefs)
    {
        this(url, null, fieldDefs, null, null);
    }

    /**
     * Creates a streaming record iterator for the given URL, field definitions, and authentication headers.
     *
     * @param url
     *            the full URL to fetch (e.g. "https://api.spacexdata.com/v4/rockets")
     * @param fieldDefs
     *            the field definitions that define the expected fields and their types
     * @param authHeaders
     *            optional authentication headers to include in the HTTP request (may be null)
     */
    public JsonToRecordStream(String url, List<RestFieldDefinition> fieldDefs, java.util.Map<String, String> authHeaders)
    {
        this(url, null, fieldDefs, authHeaders, null);
    }

    /**
     * Creates a streaming record iterator for the given URL, data path, field definitions, and authentication headers.
     *
     * @param url
     *            the full URL to fetch (e.g. "https://api.spacexdata.com/v4/rockets")
     * @param dataPath
     *            optional JSON path to the data array (e.g. "result" for {"result": [...]})
     * @param fieldDefs
     *            the field definitions that define the expected fields and their types
     * @param authHeaders
     *            optional authentication headers to include in the HTTP request (may be null)
     */
    public JsonToRecordStream(String url, String dataPath, List<RestFieldDefinition> fieldDefs, java.util.Map<String, String> authHeaders)
    {
        this(url, dataPath, fieldDefs, authHeaders, null);
    }

    /**
     * Creates a streaming record iterator with full configuration including pagination support.
     *
     * @param url
     *            the base URL to fetch (e.g. "https://api.spacexdata.com/v4/rockets")
     * @param dataPath
     *            optional JSON path to the data array (e.g. "result" for {"result": [...]})
     * @param fieldDefs
     *            the field definitions that define the expected fields and their types
     * @param authHeaders
     *            optional authentication headers to include in the HTTP request (may be null)
     * @param paginationConfig
     *            optional pagination configuration (may be null for non-paginated APIs)
     */
    public JsonToRecordStream(String url, String dataPath, List<RestFieldDefinition> fieldDefs,
            java.util.Map<String, String> authHeaders, PaginationConfig paginationConfig)
    {
        this.baseUrl = url;
        this.dataPath = dataPath;
        this.fieldDefs = fieldDefs;
        this.authHeaders = authHeaders;
        this.paginationConfig = paginationConfig;
        this.objectMapper = new ObjectMapper();
        this.initialized = false;
        this.done = false;
        
        // Initialize pagination state
        this.recordsInCurrentPage = 0;
        this.totalPagesFetched = 0;
        if (paginationConfig != null) {
            this.currentOffset = paginationConfig.getInitialOffset();
            this.currentPage = paginationConfig.getInitialPage();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasNext()
    {
        if (!initialized) {
            try {
                initialize();
            }
            catch (Exception e) {
                LOGGER.error("Failed to initialize HTTP stream for URL: {}", baseUrl, e);
                done = true;
                return false;
            }
        }
        if (nextRecord != null) {
            return true;
        }
        if (done) {
            return false;
        }
        try {
            nextRecord = advance();
        }
        catch (Exception e) {
            LOGGER.error("Error reading next record from stream", e);
            done = true;
            return false;
        }
        return nextRecord != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Record next()
    {
        if (!hasNext()) {
            throw new NoSuchElementException("No more records in stream");
        }
        final Record record = nextRecord;
        nextRecord = null;
        return record;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException
    {
        done = true;
        if (jsonParser != null) {
            try {
                jsonParser.close();
            }
            catch (IOException e) {
                LOGGER.warn("Error closing JSON parser", e);
            }
        }
        if (responseStream != null) {
            try {
                responseStream.close();
            }
            catch (IOException e) {
                LOGGER.warn("Error closing HTTP response stream", e);
            }
        }
    }

    /**
     * Opens the HTTP connection and positions the JSON parser at the first object.
     */
    private void initialize() throws IOException, InterruptedException
    {
        // Build the initial URL with pagination parameters if configured
        final String currentUrl = buildCurrentPageUrl();
        
        LOGGER.info("Opening HTTP stream for URL: {}", currentUrl);
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                .build();

        final HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(currentUrl))
                .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                .header("Accept", "application/json")
                .header("User-Agent", "CP4D-REST-Connector/1.0");

        // Add authentication headers if provided
        if (authHeaders != null && !authHeaders.isEmpty()) {
            for (final java.util.Map.Entry<String, String> header : authHeaders.entrySet()) {
                requestBuilder.header(header.getKey(), header.getValue());
                LOGGER.debug("Adding authentication header: {}", header.getKey());
            }
        }

        final HttpRequest request = requestBuilder.GET().build();

        final HttpResponse<InputStream> response = httpClient.send(request, BodyHandlers.ofInputStream());

        if (response.statusCode() != HTTP_OK) {
            throw new IOException("HTTP request failed with status " + response.statusCode() + " for URL: " + currentUrl);
        }

        // Store response headers for Link header pagination
        lastResponseHeaders = response.headers();
        
        // Extract next page URL from Link header if using link_header pagination
        if (paginationConfig != null && "link_header".equals(paginationConfig.getType())) {
            nextPageUrl = extractLinkHeader(lastResponseHeaders);
        }

        responseStream = response.body();
        totalPagesFetched++;
        final JsonFactory factory = new JsonFactory();
        jsonParser = factory.createParser(responseStream);

        // Navigate to the data array
        navigateToDataArray();

        // Only pre-fetch if not already initialized (single object case)
        if (!initialized) {
            initialized = true;
            // Pre-fetch the first record
            nextRecord = advance();
        }
    }

    /**
     * Navigates the JSON parser to the data array.
     * Handles both root-level arrays and nested arrays specified by dataPath.
     *
     * @throws IOException if an I/O error occurs or the expected structure is not found
     */
    private void navigateToDataArray() throws IOException
    {
        // Advance to the first token
        JsonToken firstToken = jsonParser.nextToken();
        
        // If dataPath is specified, navigate to the nested array
        if (dataPath != null && !dataPath.isEmpty()) {
            LOGGER.debug("Navigating to nested data path: {}", dataPath);
            if (firstToken != JsonToken.START_OBJECT) {
                throw new IOException("Expected START_OBJECT at root when dataPath is specified, but got: " + firstToken);
            }
            
            // Navigate through the JSON structure to find the data path
            boolean found = false;
            while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
                final String fieldName = jsonParser.currentName();
                
                if (dataPath.equals(fieldName)) {
                    // Move to the value of this field
                    firstToken = jsonParser.nextToken();
                    found = true;
                    LOGGER.debug("Found data path '{}', token type: {}", dataPath, firstToken);
                    break;
                }
                // Skip this field's value
                jsonParser.skipChildren();
            }
            
            if (!found) {
                throw new IOException("Data path '" + dataPath + "' not found in JSON response");
            }
        }
        
        if (firstToken == JsonToken.START_ARRAY) {
            // JSON array response — advance past the START_ARRAY token
            // The parser is now positioned before the first object
            LOGGER.debug("Response is a JSON array");
        } else if (firstToken == JsonToken.START_OBJECT) {
            // Single JSON object response — we'll read it as one record
            // Push back by not advancing — we'll handle it in advance()
            LOGGER.debug("Response is a single JSON object");
            // We need to re-read this object, so we set a flag
            // We handle this by reading the current object directly
            final Record record = readCurrentObject();
            nextRecord = record;
            done = true; // Only one record
            initialized = true;
        } else {
            throw new IOException("Unexpected JSON token at start of response: " + firstToken + " for URL: " + baseUrl);
        }
    }

    /**
     * Advances the parser to the next JSON object and converts it to a Record.
     * Handles pagination by fetching the next page when the current page is exhausted.
     *
     * @return the next Record, or null if there are no more records
     */
    private Record advance() throws IOException
    {
        if (done) {
            return null;
        }

        // Peek at the next token
        final JsonToken token = jsonParser.nextToken();
        if (token == null || token == JsonToken.END_ARRAY) {
            // Current page exhausted - check if there are more pages
            if (paginationConfig != null && hasMorePages()) {
                try {
                    fetchNextPage();
                    // Recursively call advance() to read from the new page
                    return advance();
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOGGER.error("Interrupted while fetching next page", e);
                    done = true;
                    return null;
                }
            }
            done = true;
            return null;
        }

        if (token != JsonToken.START_OBJECT) {
            LOGGER.warn("Expected START_OBJECT but got: {}", token);
            done = true;
            return null;
        }

        // Successfully read an object - increment counter for this page
        recordsInCurrentPage++;
        return readCurrentObject();
    }

    /**
     * Checks if there are more pages to fetch based on pagination configuration.
     *
     * @return true if more pages should be fetched, false otherwise
     */
    private boolean hasMorePages()
    {
        // Safety check: don't fetch more than MAX_PAGES
        if (totalPagesFetched >= MAX_PAGES) {
            LOGGER.warn("Reached maximum page limit of {}", MAX_PAGES);
            return false;
        }

        final String type = paginationConfig.getType();
        
        if ("offset".equals(type) || "page".equals(type)) {
            // For offset/page pagination, continue if we got a full page
            // If we got fewer records than page_size, we've reached the end
            return recordsInCurrentPage >= paginationConfig.getPageSize();
        }
        
        if ("cursor".equals(type)) {
            // For cursor pagination, continue if we have a next cursor
            return nextCursor != null && !nextCursor.isEmpty();
        }
        
        if ("link_header".equals(type) || "next_url".equals(type)) {
            // For link_header/next_url pagination, continue if we have a next URL
            return nextPageUrl != null && !nextPageUrl.isEmpty();
        }
        
        return false;
    }

    /**
     * Fetches the next page of data by closing the current stream and opening a new one.
     *
     * @throws IOException if an I/O error occurs
     * @throws InterruptedException if the thread is interrupted
     */
    private void fetchNextPage() throws IOException, InterruptedException
    {
        LOGGER.debug("Fetching next page (current page: {}, records in page: {})",
                     totalPagesFetched, recordsInCurrentPage);

        // Close current parser and stream
        if (jsonParser != null) {
            jsonParser.close();
        }
        if (responseStream != null) {
            responseStream.close();
        }

        // Update pagination state based on type
        final String type = paginationConfig.getType();
        if ("offset".equals(type)) {
            currentOffset += paginationConfig.getPageSize();
        } else if ("page".equals(type)) {
            currentPage++;
        }
        // For cursor/link_header/next_url, the state is already updated

        // Reset page record counter
        recordsInCurrentPage = 0;

        // Build URL for next page
        final String nextUrl = buildCurrentPageUrl();
        LOGGER.debug("Fetching next page from URL: {}", nextUrl);

        // Make HTTP request
        final HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(nextUrl))
                .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                .header("Accept", "application/json")
                .header("User-Agent", "CP4D-REST-Connector/1.0")
                .GET();

        // Add authentication headers
        if (authHeaders != null && !authHeaders.isEmpty()) {
            for (final java.util.Map.Entry<String, String> header : authHeaders.entrySet()) {
                requestBuilder.header(header.getKey(), header.getValue());
            }
        }

        final HttpRequest request = requestBuilder.build();
        final HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != HTTP_OK) {
            throw new IOException("HTTP request failed with status code: " + response.statusCode());
        }

        // Store response headers for Link header extraction
        lastResponseHeaders = response.headers();

        // Extract next page URL from Link header if using link_header pagination
        if ("link_header".equals(type)) {
            nextPageUrl = extractLinkHeader(lastResponseHeaders);
            LOGGER.debug("Extracted next page URL from Link header: {}", nextPageUrl);
        }

        // Open new stream and parser
        responseStream = response.body();
        final JsonFactory factory = new JsonFactory();
        jsonParser = factory.createParser(responseStream);

        // Navigate to the data array
        navigateToDataArray();

        // Increment page counter
        totalPagesFetched++;

        // Extract pagination metadata from response body if needed
        if ("cursor".equals(type) || "next_url".equals(type)) {
            extractPaginationMetadata();
        }
    }

    /**
     * Extracts pagination metadata (next cursor or next URL) from the JSON response.
     * This is called after navigating to the data array, so we need to peek ahead
     * without consuming the data array tokens.
     *
     * @throws IOException if an I/O error occurs
     */
    private void extractPaginationMetadata() throws IOException
    {
        // For cursor and next_url pagination, the metadata is typically in the response
        // alongside the data array. However, since we've already navigated to the data array,
        // we can't easily go back to read sibling fields.
        //
        // Solution: We'll extract this metadata BEFORE navigating to the data array.
        // This requires refactoring the initialize() and fetchNextPage() methods.
        //
        // For now, we'll implement a simplified version that assumes the metadata
        // is available in the response headers or will be extracted in the next iteration.
        
        final String type = paginationConfig.getType();
        
        if ("cursor".equals(type)) {
            final String cursorPath = paginationConfig.getNextCursorPath();
            if (cursorPath != null) {
                // TODO: Extract cursor from JSON response
                // This requires parsing the response before navigating to data array
                LOGGER.debug("Cursor extraction from response body not yet implemented");
            }
        } else if ("next_url".equals(type)) {
            final String nextUrlPath = paginationConfig.getNextUrlPath();
            if (nextUrlPath != null) {
                // TODO: Extract next URL from JSON response
                // This requires parsing the response before navigating to data array
                LOGGER.debug("Next URL extraction from response body not yet implemented");
            }
        }
    }

    /**
     * Reads the current JSON object (parser positioned at START_OBJECT) and
     * converts it to a Record with values in field definition order.
     *
     * @return the Record
     */
    private Record readCurrentObject() throws IOException
    {
        // Read the entire JSON object into an ObjectNode for random field access
        final ObjectNode objectNode = objectMapper.readTree(jsonParser);

        final Record record = new Record(fieldDefs.size());
        for (final RestFieldDefinition fieldDef : fieldDefs) {
            final String fieldName = fieldDef.getName();
            final com.fasterxml.jackson.databind.JsonNode valueNode;
            
            // Check if this is a flattened field (contains dot indicating nested path)
            if (fieldName.contains(".")) {
                valueNode = getNestedValue(objectNode, fieldName);
            } else {
                valueNode = objectNode.get(fieldName);
            }
            
            record.appendValue(convertValue(valueNode, fieldDef));
        }
        return record;
    }
    
    /**
     * Retrieves a value from a nested JSON object using dot-separated path.
     * For example, "rates.currency" will look for objectNode.get("rates").get("currency")
     *
     * @param objectNode the root JSON object
     * @param flattenedName the flattened field name (e.g. "rates.currency")
     * @return the nested value, or null if not found
     */
    private com.fasterxml.jackson.databind.JsonNode getNestedValue(ObjectNode objectNode, String flattenedName)
    {
        final int dotIndex = flattenedName.indexOf('.');
        if (dotIndex < 0) {
            return objectNode.get(flattenedName);
        }
        
        final String parentKey = flattenedName.substring(0, dotIndex);
        final String remainingPath = flattenedName.substring(dotIndex + 1);
        
        final com.fasterxml.jackson.databind.JsonNode parentNode = objectNode.get(parentKey);
        if (parentNode == null || !parentNode.isObject()) {
            return null;
        }
        
        // Recursively handle deeper nesting
        if (remainingPath.contains(".")) {
            return getNestedValue((ObjectNode) parentNode, remainingPath);
        } else {
            return parentNode.get(remainingPath);
        }
    }

    /**
     * Converts a JSON node value to the appropriate Java Serializable type
     * based on the field definition's type string.
     *
     * @param node
     *            the JSON node (may be null if field is absent)
     * @param fieldDef
     *            the field definition
     * @return the Java value, or null if the node is null or JSON null
     */
    private java.io.Serializable convertValue(com.fasterxml.jackson.databind.JsonNode node, RestFieldDefinition fieldDef)
    {
        if (node == null || node.isNull()) {
            return null;
        }

        // For nested arrays and JSON type, serialize back to JSON string
        if (fieldDef.isNestedArray() || node.isObject() || node.isArray()) {
            return node.toString();
        }

        final String typeLower = fieldDef.getTypeString().toLowerCase(Locale.ENGLISH);

        // Handle types with length parameter (e.g. VarChar(50))
        final String baseType = extractBaseType(typeLower);

        switch (baseType) {
        case "integer":
        case "int":
        case "smallint":
        case "tinyint":
            if (node.isNumber()) {
                return node.intValue();
            }
            return parseIntSafe(node.asText());

        case "bigint":
            if (node.isNumber()) {
                return node.longValue();
            }
            return parseLongSafe(node.asText());

        case "boolean":
        case "bool":
        case "bit":
            if (node.isBoolean()) {
                return node.booleanValue();
            }
            return Boolean.parseBoolean(node.asText());

        case "double":
        case "float8":
        case "float":
        case "real":
        case "float4":
            if (node.isNumber()) {
                return node.doubleValue();
            }
            return parseDoubleSafe(node.asText());

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
            // varchar, longvarchar, and all other string types
            return node.asText();
        }
    }

    /**
     * Extracts the base type name from a type string that may include a length parameter.
     * E.g. "varchar(50)" → "varchar", "integer" → "integer"
     */
    private static String extractBaseType(String typeLower)
    {
        final int parenIdx = typeLower.indexOf('(');
        if (parenIdx >= 0) {
            return typeLower.substring(0, parenIdx).trim();
        }
        return typeLower.trim();
    }

    private static Integer parseIntSafe(String text)
    {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(text.trim());
        }
        catch (NumberFormatException e) {
            LOGGER.warn("Cannot parse integer value: '{}'", text);
            return null;
        }
    }

    private static Long parseLongSafe(String text)
    {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(text.trim());
        }
        catch (NumberFormatException e) {
            LOGGER.warn("Cannot parse long value: '{}'", text);
            return null;
        }
    }

    private static Double parseDoubleSafe(String text)
    {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(text.trim());
        }
        catch (NumberFormatException e) {
            LOGGER.warn("Cannot parse double value: '{}'", text);
            return null;
        }
    }

    private static Date parseDateSafe(String text)
    {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            // Try ISO date format: "2006-03-24"
            return Date.valueOf(LocalDate.parse(text.trim()));
        }
        catch (DateTimeParseException e) {
            LOGGER.warn("Cannot parse date value: '{}'", text);
            return null;
        }
    }

    private static Timestamp parseTimestampSafe(String text)
    {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            // Try ISO-8601 format: "2006-03-24T18:30:00.000Z"
            return Timestamp.from(Instant.parse(text.trim()));
        }
        catch (DateTimeParseException e) {
            // Try without timezone
            try {
                return Timestamp.valueOf(text.trim().replace("T", " ").replaceAll("\\.\\d+Z?$", ""));
            }
            catch (Exception ex) {
                LOGGER.warn("Cannot parse timestamp value: '{}'", text);
                return null;
            }
        }
    }

    /**
     * Builds the URL for the current page based on pagination configuration.
     *
     * @return the URL to fetch
     */
    private String buildCurrentPageUrl()
    {
        if (paginationConfig == null) {
            return baseUrl;
        }

        final String type = paginationConfig.getType();
        final StringBuilder urlBuilder = new StringBuilder(baseUrl);

        // Check if URL already has query parameters
        final boolean hasParams = baseUrl.contains("?");
        final String separator = hasParams ? "&" : "?";

        if ("offset".equals(type)) {
            urlBuilder.append(separator);
            urlBuilder.append(paginationConfig.getOffsetParam()).append('=').append(currentOffset);
            if (paginationConfig.getLimitParam() != null) {
                urlBuilder.append('&').append(paginationConfig.getLimitParam()).append('=').append(paginationConfig.getPageSize());
            }
        }
        else if ("page".equals(type)) {
            urlBuilder.append(separator);
            urlBuilder.append(paginationConfig.getPageParam()).append('=').append(currentPage);
            if (paginationConfig.getLimitParam() != null) {
                urlBuilder.append('&').append(paginationConfig.getLimitParam()).append('=').append(paginationConfig.getPageSize());
            }
        }
        else if ("cursor".equals(type)) {
            if (nextCursor != null && !nextCursor.isEmpty()) {
                urlBuilder.append(separator);
                urlBuilder.append(paginationConfig.getCursorParam()).append('=').append(nextCursor);
            }
            if (paginationConfig.getLimitParam() != null) {
                urlBuilder.append(nextCursor != null ? '&' : separator.charAt(0));
                urlBuilder.append(paginationConfig.getLimitParam()).append('=').append(paginationConfig.getPageSize());
            }
        }
        else if ("link_header".equals(type)) {
            // For link_header, use nextPageUrl if available, otherwise use base URL with page size
            if (nextPageUrl != null && !nextPageUrl.isEmpty()) {
                return nextPageUrl;
            }
            if (paginationConfig.getLimitParam() != null) {
                urlBuilder.append(separator);
                urlBuilder.append(paginationConfig.getLimitParam()).append('=').append(paginationConfig.getPageSize());
            }
        }
        else if ("next_url".equals(type)) {
            // For next_url, use nextPageUrl if available, otherwise use base URL with page size
            if (nextPageUrl != null && !nextPageUrl.isEmpty()) {
                return nextPageUrl;
            }
            if (paginationConfig.getLimitParam() != null) {
                urlBuilder.append(separator);
                urlBuilder.append(paginationConfig.getLimitParam()).append('=').append(paginationConfig.getPageSize());
            }
        }

        return urlBuilder.toString();
    }

    /**
     * Extracts the next page URL from Link header (RFC 5988).
     *
     * @param headers the HTTP response headers
     * @return the next page URL, or null if not found
     */
    private String extractLinkHeader(java.net.http.HttpHeaders headers)
    {
        final java.util.Optional<String> linkHeader = headers.firstValue("Link");
        if (!linkHeader.isPresent()) {
            return null;
        }

        // Parse Link header: <url>; rel="next"
        final String[] links = linkHeader.get().split(",");
        for (final String link : links) {
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

}

// Made with Bob
