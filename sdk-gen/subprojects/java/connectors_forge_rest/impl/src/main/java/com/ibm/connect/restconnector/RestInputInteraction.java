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

import org.apache.arrow.flight.Ticket;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.types.pojo.Schema;
import org.slf4j.Logger;

import com.ibm.connect.sdk.api.Connector;
import com.ibm.connect.sdk.api.SourceInteraction;
import com.ibm.connect.sdk.api.TicketInfo;
import com.ibm.connect.sdk.util.ModelMapper;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.sdk.connector.RowWriter;
import com.ibm.wdp.connect.sdk.connector.SdkInputInteraction;

/**
 * An interaction with a REST API asset as an input (read) source.
 *
 * <p>Implements both the legacy {@link SourceInteraction} interface (for API compatibility) and
 * the new {@link SdkInputInteraction} interface (push-based via {@link #stream(RowWriter)},
 * used by the Arrow-native path through {@link RestFlightProducer}).
 *
 * <p>Reads data from a REST API endpoint defined in the JSON mapping configuration,
 * converts the JSON response to Arrow format in a streaming fashion.
 */
@SuppressWarnings({ "PMD.AvoidDollarSigns", "PMD.ClassNamingConventions" })
public class RestInputInteraction implements SourceInteraction<Connector<?, ?>>, SdkInputInteraction
{
    private static final Logger LOGGER = getLogger(RestInputInteraction.class);

    private final ModelMapper modelMapper = new ModelMapper();
    private final RestConnector connector;
    private final String tableName;
    private final RestTableDefinition tableDef;
    private final Map<String, Object> connectionProperties;

    /**
     * Creates a REST input interaction from a legacy {@link CustomFlightAssetDescriptor}.
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
        this(connector, RestConnectorUtils.resolveTableName(asset), asset.getConnectionProperties(), ticket);
    }

    /**
     * Common constructor.
     */
    private RestInputInteraction(RestConnector connector, String resolvedTableName,
            Map<String, Object> connectionProperties, Ticket ticket) throws Exception
    {
        if (connector == null) {
            throw new IllegalArgumentException(RestMsgs.MISSING_CONNECTOR.format());
        }
        this.connector = connector;
        this.tableName = resolvedTableName;
        this.connectionProperties = connectionProperties != null ? connectionProperties : Collections.emptyMap();
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

    // ---- SdkInputInteraction interface (new path) ----

    /**
     * {@inheritDoc}
     */
    @Override
    public Schema getSchema()
    {
        return ForgeSchemaBuilder.buildSchema(tableDef.getFields());
    }

    /**
     * {@inheritDoc}
     */
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

        final JsonToArrowStream jsonStream = new JsonToArrowStream(
                url,
                tableDef.getDataPath(),
                tableDef.getFields(),
                authHeaders,
                tableDef.getPaginationConfig());

        try {
            jsonStream.streamTo(writer);
        } finally {
            jsonStream.close();
        }
    }

    // ---- SourceInteraction interface (legacy stubs — pull path not supported) ----

    /**
     * {@inheritDoc}
     *
     * @deprecated The pull-based path is not supported in this implementation.
     *             Use {@link #stream(RowWriter)} instead via the SDK connector path.
     */
    @Deprecated
    @Override
    public void beginStream(BufferAllocator allocator)
    {
        throw new UnsupportedOperationException(
                "Pull-based streaming is not supported. Use stream(RowWriter) instead.");
    }

    /**
     * {@inheritDoc}
     *
     * @deprecated The pull-based path is not supported in this implementation.
     */
    @Deprecated
    @Override
    public boolean hasNextBatch()
    {
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * @deprecated The pull-based path is not supported in this implementation.
     */
    @Deprecated
    @Override
    public VectorSchemaRoot nextBatch()
    {
        throw new UnsupportedOperationException(
                "Pull-based streaming is not supported. Use stream(RowWriter) instead.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close()
    {
        // No persistent resources to close
    }

    // ---- private helpers ----

    private String buildUrl()
    {
        try {
            final URL configUrl = new URL(connector.getApiMapping().getBaseUrl());
            final String protocol = configUrl.getProtocol();
            final String host = configUrl.getHost();
            final int port = configUrl.getPort();
            // Preserve the path prefix from $hostname (e.g. "/api/1.0" in "https://host/api/1.0")
            final String basePath = configUrl.getPath();

            // Omit the port when none was written in the config — avoids sending
            // explicit default ports (e.g. :443) that some servers reject.
            final String authority = port == -1 ? host : host + ":" + port;
            final String url = protocol + "://" + authority + basePath + tableDef.getPath();
            LOGGER.debug("Built request URL: {}", url);
            return url;
        } catch (MalformedURLException e) {
            LOGGER.error("Failed to parse base URL from config: {}", connector.getApiMapping().getBaseUrl(), e);
            throw new IllegalStateException("Invalid base URL in configuration", e);
        }
    }

    private Map<String, String> buildAuthHeaders()
    {
        final AuthenticationType authType = connector.getApiMapping().getAuthenticationTypeEnum();

        if (authType == AuthenticationType.NONE) {
            LOGGER.debug("No authentication configured");
            return null;
        }

        if (connectionProperties.isEmpty()) {
            LOGGER.warn("No connection properties provided for configured authentication");
            return null;
        }

        final Map<String, String> headers = new HashMap<>();

        if (authType == AuthenticationType.API_KEY) {
            final Object apiKeyObj = connectionProperties.get("api_key");
            if (apiKeyObj != null) {
                headers.put("Authorization", "ApiKey " + apiKeyObj.toString());
                LOGGER.debug("Using API Key authentication");
            } else {
                LOGGER.warn("API key not provided in connection properties");
            }
        } else if (authType == AuthenticationType.OAUTH2) {
            final Object tokenObj = connectionProperties.get("bearer_token");
            if (tokenObj != null) {
                headers.put("Authorization", "Bearer " + tokenObj.toString());
                LOGGER.debug("Using OAuth 2.0 Bearer Token authentication");
            } else {
                LOGGER.warn("Bearer token not provided in connection properties");
            }
        } else if (authType == AuthenticationType.BASIC) {
            final Object usernameObj = connectionProperties.get("username");
            final Object passwordObj = connectionProperties.get("password");
            if (usernameObj != null && passwordObj != null) {
                final String credentials = usernameObj.toString() + ":" + passwordObj.toString();
                final String encoded = Base64.getEncoder()
                        .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
                headers.put("Authorization", "Basic " + encoded);
                LOGGER.debug("Using Basic authentication");
            } else {
                LOGGER.warn("Username or password not provided in connection properties");
            }
        } else {
            LOGGER.warn("Unknown authentication type: {}", authType);
        }

        return headers.isEmpty() ? null : headers;
    }
}
