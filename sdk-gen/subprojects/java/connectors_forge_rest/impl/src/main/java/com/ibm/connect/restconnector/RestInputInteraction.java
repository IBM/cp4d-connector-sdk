/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import static org.slf4j.LoggerFactory.getLogger;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.arrow.flight.Ticket;
import org.apache.arrow.vector.types.pojo.Schema;
import org.slf4j.Logger;

import com.ibm.connect.sdk.api.TicketInfo;
import com.ibm.connect.sdk.util.ModelMapper;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.sdk.connector.RowWriter;
import com.ibm.wdp.connect.sdk.connector.SdkInputInteraction;

/**
 * An interaction with a REST API asset as an input (read) source.
 *
 * <p>Implements {@link SdkInputInteraction} (push-based via {@link #stream(RowWriter)}),
 * used by the Arrow-native path through {@link RestFlightProducer}.
 *
 * <p>Reads data from a REST API endpoint defined in the JSON mapping configuration,
 * converts the JSON response to Arrow format in a streaming fashion.
 */
@SuppressWarnings({ "PMD.AvoidDollarSigns", "PMD.ClassNamingConventions" })
public class RestInputInteraction implements SdkInputInteraction
{
    private static final Logger LOGGER = getLogger(RestInputInteraction.class);

    private static final Pattern BASE64_PATTERN = Pattern.compile("base64\\(([^)]+)\\)");
    private static final Pattern VAR_PATTERN     = Pattern.compile("\\$([A-Za-z_][A-Za-z0-9_]*)");

    private final ModelMapper modelMapper = new ModelMapper();
    private final RestConnector connector;
    private final String tableName;
    private final RestTableDefinition tableDef;
    private final Map<String, Object> connectionProperties;

    /**
     * Creates a REST input interaction.
     *
     * @param connector
     *            the connector managing the connection to the data source
     * @param asset
     *            the asset from which to read
     * @param ticket
     *            a Flight ticket to read a partition or null to get tickets
     * @throws Exception
     */
    public RestInputInteraction(RestConnector connector, CustomFlightAssetDescriptor asset, Ticket ticket)
            throws Exception
    {
        if (connector == null) {
            throw new IllegalArgumentException(RestMsgs.MISSING_CONNECTOR.format());
        }
        this.connector = connector;
        this.tableName = RestConnectorUtils.resolveTableName(asset);
        this.connectionProperties = asset.getConnectionProperties() != null
                ? asset.getConnectionProperties() : Collections.emptyMap();
        LOGGER.debug("Creating input interaction for table: {}", tableName);

        final RestApiMapping apiMapping = connector.getApiMapping();
        if (apiMapping == null) {
            throw new IllegalStateException("API mapping not loaded. Call connect() first.");
        }
        tableDef = apiMapping.getTable(tableName);
        if (tableDef == null) {
            throw new IllegalArgumentException("Table '" + tableName + "' not found in REST API mapping. "
                    + "Available tables: " + apiMapping.getTables().keySet());
        }

        if (ticket != null) {
            final TicketInfo ticketInfo = modelMapper.fromBytes(ticket.getBytes(), TicketInfo.class);
            LOGGER.debug("Ticket info: {}", ticketInfo);
        }
    }

    /** {@inheritDoc} */
    @Override
    public Schema getSchema()
    {
        return ForgeSchemaBuilder.buildSchema(tableDef.getFields());
    }

    /** {@inheritDoc} */
    @Override
    public List<Ticket> getTickets() throws Exception
    {
        final String requestId = UUID.randomUUID().toString();
        final TicketInfo ticketInfo = new TicketInfo()
                .requestId(requestId)
                .partitionIndex(0);
        final byte[] ticketBytes = modelMapper.toBytes(ticketInfo);
        return Collections.singletonList(new Ticket(ticketBytes));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Fetches all data from the REST API endpoint and pushes each row into the writer.
     * Uses {@link JsonToArrowStream} from the forge engine.
     */
    @Override
    public void stream(RowWriter writer) throws Exception
    {
        final String url = buildUrl();
        LOGGER.info("Starting stream for table: {}", tableName);

        final Map<String, String> authHeaders = buildAuthHeaders();
        final String acceptHeader = connector.getApiMapping().getAcceptHeader();

        final JsonToArrowStream jsonStream = new JsonToArrowStream(
                url,
                tableDef.getDataPath(),
                tableDef.getFields(),
                authHeaders,
                tableDef.getPaginationConfig(),
                acceptHeader);

        try {
            jsonStream.streamTo(writer);
        } finally {
            jsonStream.close();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void close()
    {
        // No persistent resources to close
    }

    // ---- private helpers ----

    private String buildUrl()
    {
        try {
            final String url = buildRequestUrl(
                    connector.getApiMapping().getBaseUrl(),
                    tableDef.getPath(),
                    connectionProperties);
            LOGGER.debug("Built request URL: {}", url);
            return url;
        } catch (MalformedURLException e) {
            LOGGER.error("Failed to build request URL (baseUrl: {}, host: {}, port: {})",
                    connector.getApiMapping().getBaseUrl(),
                    connectionProperties.get("host"),
                    connectionProperties.get("port"), e);
            throw new IllegalStateException("Invalid base URL in configuration", e);
        }
    }

    /**
     * Builds the full request URL by combining the base URL from the DSL config with an
     * optional host/port override from the connection properties and the table's path segment.
     *
     * <p>The protocol and path prefix are always taken from {@code baseUrl}.  If a {@code host}
     * or {@code port} connection property is present and non-blank it overrides the corresponding
     * value from {@code baseUrl}, allowing a single DSL file to target different environments
     * without editing the JSON.
     *
     * @param baseUrl
     *            the full base URL from the {@code $hostname} DSL field
     *            (e.g. {@code "https://api.example.com/v1"})
     * @param tablePath
     *            the table-specific path segment from the DSL
     *            (e.g. {@code "/users"})
     * @param props
     *            the connection properties map; may contain {@code "host"} and/or {@code "port"}
     *            overrides (both optional; blank values are ignored)
     * @return the fully assembled URL string
     * @throws MalformedURLException
     *            if {@code baseUrl} cannot be parsed
     */
    static String buildRequestUrl(String baseUrl, String tablePath, Map<String, Object> props)
            throws MalformedURLException
    {
        final URL configUrl = new URL(baseUrl);
        final String protocol = configUrl.getProtocol();
        // Preserve the path prefix from baseUrl (e.g. "/api/1.0" in "https://host/api/1.0")
        final String basePath = configUrl.getPath();

        // Use host and port from connection properties if supplied; fall back to the config URL.
        final Object hostProp = props.get("host");
        final Object portProp = props.get("port");

        final String host = (hostProp != null && !hostProp.toString().isBlank())
                ? hostProp.toString()
                : configUrl.getHost();

        final String authority;
        if (portProp != null && !portProp.toString().isBlank()) {
            authority = host + ":" + portProp;
        } else {
            final int configPort = configUrl.getPort();
            authority = configPort == -1 ? host : host + ":" + configPort;
        }

        return protocol + "://" + authority + basePath + tablePath;
    }

    /**
     * Builds the HTTP authentication headers by evaluating each {@link AuthConfig.HeaderDef}
     * value template against the current connection properties.
     *
     * <p><b>Template syntax</b><br>
     * A value string may contain {@code $name} placeholders.  Each placeholder is replaced
     * with the corresponding connection-property value.  The special form
     * {@code base64(expr)} causes the engine to base64-encode the UTF-8 bytes of {@code expr}
     * after all {@code $name} substitutions inside it have been applied — this is the mechanism
     * used for HTTP Basic authentication:
     * <pre>  "Basic base64($username:$password)"</pre>
     *
     * <p>Header definitions whose {@code header} or {@code value} fields are {@code null} are
     * skipped (they are UI-only credential fields used only as inputs to other templates).
     */
    private Map<String, String> buildAuthHeaders()
    {
        final AuthConfig authConfig = connector.getApiMapping().getAuthConfig();

        if (authConfig.getType() == AuthenticationType.NONE) {
            LOGGER.debug("No authentication configured");
            return null;
        }

        if (connectionProperties.isEmpty()) {
            LOGGER.warn("No connection properties provided for configured authentication");
            return null;
        }

        final Map<String, String> headers = new HashMap<>();

        for (final AuthConfig.HeaderDef hd : authConfig.getHeaders()) {
            if (hd.getHeader() == null || hd.getValue() == null) {
                // UI-only credential field — used as a $var in another entry's template
                continue;
            }
            final String resolved = resolveTemplate(hd.getValue(), connectionProperties);
            if (resolved == null) {
                LOGGER.warn("Could not resolve value template '{}' for header '{}' — skipping",
                        hd.getValue(), hd.getHeader());
                continue;
            }
            // Multiple header defs may target the same HTTP header (unusual but allowed);
            // last writer wins — in practice each header name appears only once.
            headers.put(hd.getHeader(), resolved);
            LOGGER.debug("Set auth header '{}' from template '{}'", hd.getHeader(), hd.getValue());
        }

        return headers.isEmpty() ? null : headers;
    }

    /**
     * Evaluates a value template by substituting {@code $name} placeholders and applying
     * any {@code base64(expr)} wrappers.
     *
     * @param template
     *            the template string, e.g. {@code "Bearer $bearer_token"} or
     *            {@code "Basic base64($username:$password)"}
     * @param props
     *            the connection properties map supplying placeholder values
     * @return the fully-resolved string, or {@code null} if a required placeholder is missing
     */
    static String resolveTemplate(String template, Map<String, Object> props)
    {
        // Step 1 — substitute all $name placeholders
        final Matcher varMatcher = VAR_PATTERN.matcher(template);
        final StringBuffer afterVars = new StringBuffer();
        while (varMatcher.find()) {
            final String varName = varMatcher.group(1);
            final Object val = props.get(varName);
            if (val == null) {
                return null; // required placeholder missing
            }
            varMatcher.appendReplacement(afterVars, Matcher.quoteReplacement(val.toString()));
        }
        varMatcher.appendTail(afterVars);

        // Step 2 — apply base64(...) if present
        final Matcher b64Matcher = BASE64_PATTERN.matcher(afterVars.toString());
        final StringBuffer result = new StringBuffer();
        while (b64Matcher.find()) {
            final String inner   = b64Matcher.group(1);
            final String encoded = Base64.getEncoder()
                    .encodeToString(inner.getBytes(StandardCharsets.UTF_8));
            b64Matcher.appendReplacement(result, Matcher.quoteReplacement(encoded));
        }
        b64Matcher.appendTail(result);

        return result.toString();
    }
}
