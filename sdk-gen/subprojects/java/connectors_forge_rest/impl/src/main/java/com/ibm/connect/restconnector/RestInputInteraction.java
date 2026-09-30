/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import static org.slf4j.LoggerFactory.getLogger;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
     * <p>For plain tables, fetches all data from the single API endpoint and streams it.
     *
     * <p>For tables with {@code $path_keys}, resolves every combination of path-variable
     * values at read time using chained context expansion (see below), then streams data for
     * each resolved URL immediately — rows are pushed to the writer as soon as each response
     * arrives, without waiting for all combinations to complete.
     *
     * <h3>Chained context expansion</h3>
     * <p>Path keys are processed in declared order.  The engine maintains a list of
     * <em>contexts</em> — each context is a {@code Map<String, String>} of variable names to
     * their resolved values accumulated so far.  Processing starts with a single empty context
     * and, for each {@link PathKeyDef}:
     * <ol>
     *   <li>Substitutes the current context's variables into the entry's {@code source_path}.</li>
     *   <li>Fetches the values of {@code source_field} from that URL.</li>
     *   <li>For each fetched value creates a new context = existing context ∪ {variable → value}.</li>
     * </ol>
     * <p>Because step 2 uses the <em>specific</em> context, a dependent variable (e.g. {@code repo}
     * with path {@code /orgs/$owner/repos}) only fetches repos for the specific owner in that
     * context — there is no Cartesian product across unrelated values.
     */
    @Override
    public void stream(RowWriter writer) throws Exception
    {
        LOGGER.info("Starting stream for table: {}", tableName);

        final RestApiMapping apiMapping = connector.getApiMapping();
        final Map<String, String> authHeaders = buildAuthHeaders(apiMapping.getAuthConfig(), connectionProperties);
        final String acceptHeader = apiMapping.getAcceptHeader();

        if (!tableDef.hasPathKeys()) {
            // Plain table — one URL, one stream
            streamUrl(buildSimpleUrl(), writer, authHeaders, acceptHeader);
            return;
        }

        // Tables with $path_keys: resolve all contexts, stream each one immediately
        List<Map<String, String>> contexts = new ArrayList<>();
        contexts.add(new LinkedHashMap<>());

        for (final PathKeyDef pathKeyDef : tableDef.getPathKeys()) {
            final List<Map<String, String>> nextContexts = new ArrayList<>();

            for (final Map<String, String> ctx : contexts) {
                final Map<String, Object> resolvedProps = mergeProps(ctx);

                final String resolvedSourcePath = resolveTemplate(
                        pathKeyDef.getSourcePath(), resolvedProps);
                if (resolvedSourcePath == null) {
                    LOGGER.warn("Unresolved variable in source_path '{}' for step '{}' in table '{}' "
                            + "— skipping context {}",
                            pathKeyDef.getSourcePath(), pathKeyDef.getVariable(), tableName, ctx);
                    continue;
                }

                final String lookupUrl = buildRequestUrl(
                        apiMapping.getBaseUrl(), resolvedSourcePath, resolvedProps);

                final List<String> values;
                try {
                    values = JsonToArrowStream.fetchStringValues(
                            lookupUrl, pathKeyDef.getSourceDataPath(), pathKeyDef.getSourceField(),
                            authHeaders, acceptHeader);
                } catch (Exception e) {
                    LOGGER.error("Failed to fetch values for path_keys step '{}' in table '{}' from '{}': {}",
                            pathKeyDef.getVariable(), tableName, lookupUrl, e.getMessage());
                    continue;
                }

                LOGGER.debug("Step '{}' for table '{}': fetched {} value(s) from '{}'",
                        pathKeyDef.getVariable(), tableName, values.size(), lookupUrl);

                for (final String value : values) {
                    final Map<String, String> newCtx = new LinkedHashMap<>(ctx);
                    newCtx.put(pathKeyDef.getVariable(), value);
                    nextContexts.add(newCtx);
                }
            }

            contexts = nextContexts;
            if (contexts.isEmpty()) {
                LOGGER.warn("No values found for path_keys step '{}' in table '{}' — no data will be streamed",
                        pathKeyDef.getVariable(), tableName);
                return;
            }
        }

        LOGGER.info("Streaming data for table '{}' across {} path-key context(s)", tableName, contexts.size());

        for (final Map<String, String> ctx : contexts) {
            final Map<String, Object> propsForUrl = mergeProps(ctx);
            final String url = buildRequestUrl(apiMapping.getBaseUrl(), tableDef.getPath(), propsForUrl);
            LOGGER.debug("Streaming context {} → {}", ctx, url);
            streamUrl(url, writer, authHeaders, acceptHeader);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void close()
    {
        // No persistent resources to close
    }

    // ---- private helpers ----

    /**
     * Streams data from a single fully-resolved URL into the writer.
     */
    private void streamUrl(String url, RowWriter writer,
            Map<String, String> authHeaders, String acceptHeader) throws Exception
    {
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

    /**
     * Builds the URL for a plain table (no path-key variables), using connection
     * properties as the only substitution source.
     */
    private String buildSimpleUrl() throws MalformedURLException
    {
        final String url = buildRequestUrl(
                connector.getApiMapping().getBaseUrl(),
                tableDef.getPath(),
                connectionProperties);
        LOGGER.debug("Built request URL: {}", url);
        return url;
    }

    /**
     * Merges connection properties with the given resolved path-key variable context.
     * Context values take precedence over connection properties with the same name.
     */
    private Map<String, Object> mergeProps(Map<String, String> ctx)
    {
        if (ctx.isEmpty()) {
            return connectionProperties;
        }
        final Map<String, Object> merged = new LinkedHashMap<>(connectionProperties);
        merged.putAll(ctx);
        return merged;
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

        // Resolve any $variable placeholders in the table path (e.g. /merchants/$merchant_id/customers)
        final String resolvedTablePath = resolveTemplate(tablePath, props);
        if (resolvedTablePath == null) {
            throw new IllegalStateException(
                    "Could not resolve all path variables in '" + tablePath
                    + "'. Ensure all required path properties are set in the connection configuration.");
        }

        return protocol + "://" + authority + basePath + resolvedTablePath;
    }

    /**
     * Builds the HTTP authentication headers from the given {@link AuthConfig} and connection
     * properties. Package-visible so that {@link RestConnector} can reuse it during connection
     * testing.
     *
     * @param authConfig
     *            the authentication configuration from the API mapping
     * @param props
     *            the connection properties supplying placeholder values
     * @return a map of header name to resolved value, or {@code null} if no auth headers apply
     */
    static Map<String, String> buildAuthHeaders(AuthConfig authConfig, Map<String, Object> props)
    {
        if (authConfig.getType() == AuthenticationType.NONE) {
            LOGGER.debug("No authentication configured");
            return null;
        }

        if (props.isEmpty()) {
            LOGGER.warn("No connection properties provided for configured authentication");
            return null;
        }

        final Map<String, String> headers = new HashMap<>();

        for (final AuthConfig.HeaderDef hd : authConfig.getHeaders()) {
            if (hd.getHeader() == null || hd.getValue() == null) {
                // UI-only credential field — used as a $var in another entry's template
                continue;
            }
            final String resolved = resolveTemplate(hd.getValue(), props);
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
