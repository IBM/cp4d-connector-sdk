/* *************************************************** */

/* (C) Copyright IBM Corp. 2025                        */

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

    private final String url;
    private final String dataPath;
    private final List<RestFieldDefinition> fieldDefs;
    private final java.util.Map<String, String> authHeaders;
    private final ObjectMapper objectMapper;

    private HttpClient httpClient;
    private InputStream responseStream;
    private JsonParser jsonParser;

    private Record nextRecord;
    private boolean initialized;
    private boolean done;

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
        this(url, null, fieldDefs, null);
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
        this(url, null, fieldDefs, authHeaders);
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
        this.url = url;
        this.dataPath = dataPath;
        this.fieldDefs = fieldDefs;
        this.authHeaders = authHeaders;
        this.objectMapper = new ObjectMapper();
        this.initialized = false;
        this.done = false;
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
                LOGGER.error("Failed to initialize HTTP stream for URL: {}", url, e);
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
        LOGGER.info("Opening HTTP stream for URL: {}", url);
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                .build();

        final HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                .header("Accept", "application/json");

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
            throw new IOException("HTTP request failed with status " + response.statusCode() + " for URL: " + url);
        }

        responseStream = response.body();
        final JsonFactory factory = new JsonFactory();
        jsonParser = factory.createParser(responseStream);

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
            return;
        } else {
            throw new IOException("Unexpected JSON token at start of response: " + firstToken + " for URL: " + url);
        }

        initialized = true;
        // Pre-fetch the first record
        nextRecord = advance();
    }

    /**
     * Advances the parser to the next JSON object and converts it to a Record.
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
            done = true;
            return null;
        }

        if (token != JsonToken.START_OBJECT) {
            LOGGER.warn("Expected START_OBJECT but got: {}", token);
            done = true;
            return null;
        }

        return readCurrentObject();
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
            final com.fasterxml.jackson.databind.JsonNode valueNode = objectNode.get(fieldDef.getName());
            record.appendValue(convertValue(valueNode, fieldDef));
        }
        return record;
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
}

// Made with Bob
