/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import com.ibm.connect.sdk.api.ArrowConversions;
import com.ibm.connect.sdk.api.Connector;
import com.ibm.connect.sdk.api.SourceInteraction;
import com.ibm.connect.sdk.api.TicketInfo;
import com.ibm.connect.sdk.util.ModelMapper;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetField;
import com.ibm.wdp.connect.sdk.connector.AssetDescriptor;
import com.ibm.wdp.connect.sdk.connector.RowWriter;
import com.ibm.wdp.connect.sdk.connector.SdkSourceInteraction;
import org.apache.arrow.flight.Ticket;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.types.pojo.Schema;
import org.slf4j.Logger;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * An interaction with a REST API asset as a source.
 *
 * <p>Implements both the legacy {@link SourceInteraction} interface (for API compatibility) and
 * the new {@link SdkSourceInteraction} interface (push-based via {@link #stream(RowWriter)},
 * used by the Arrow-native path through {@link RestFlightProducer}).
 *
 * <p>Reads data from a REST API endpoint defined in the JSON mapping configuration,
 * converts the JSON response to Arrow format in a streaming fashion.
 */
@SuppressWarnings({ "PMD.AvoidDollarSigns", "PMD.ClassNamingConventions" })
public class RestSourceInteraction implements SourceInteraction<Connector<?, ?>>, SdkSourceInteraction
{
    private static final Logger LOGGER = getLogger(RestSourceInteraction.class);

    private final ModelMapper modelMapper = new ModelMapper();
    private final RestConnector connector;
    private final String tableName;
    private final RestTableDefinition tableDef;
    private final List<CustomFlightAssetField> assetFields;
    private final Map<String, Object> connectionProperties;

    /**
     * Creates a REST source interaction from a legacy {@link CustomFlightAssetDescriptor}.
     *
     * @param connector
     *            the connector managing the connection to the data source
     * @param asset
     *            the asset from which to read
     * @param ticket
     *            a Flight ticket to read a partition or null to get tickets
     * @throws Exception
     */
    public RestSourceInteraction(RestConnector connector, CustomFlightAssetDescriptor asset, Ticket ticket)
            throws Exception
    {
        this(connector, RestConnectorUtils.resolveTableName(asset), asset.getConnectionProperties(), ticket);
    }

    /**
     * Creates a REST source interaction from an SDK {@link AssetDescriptor}.
     *
     * @param connector
     *            the connector managing the connection to the data source
     * @param asset
     *            the SDK asset descriptor from which to read
     * @param ticket
     *            a Flight ticket to read a partition or null to get tickets
     * @throws Exception
     */
    public RestSourceInteraction(RestConnector connector, AssetDescriptor asset, Ticket ticket) throws Exception
    {
        this(connector, RestConnectorUtils.resolveTableName(asset.getPath(), asset.getName()),
                asset.getProperties(), ticket);
    }

    /**
     * Common constructor.
     */
    private RestSourceInteraction(RestConnector connector, String resolvedTableName,
            Map<String, Object> connectionProperties, Ticket ticket) throws Exception
    {
        if (connector == null) {
            throw new IllegalArgumentException(RestMsgs.MISSING_CONNECTOR.format());
        }
        this.connector = connector;
        this.tableName = resolvedTableName;
        this.connectionProperties = connectionProperties != null ? connectionProperties : Collections.emptyMap();
        LOGGER.debug("Creating source interaction for table: {}", tableName);

        final RestApiMapping apiMapping = connector.getApiMapping();
        if (apiMapping == null) {
            throw new IllegalStateException("API mapping not loaded. Call connect() first.");
        }
        tableDef = apiMapping.getTable(tableName);
        if (tableDef == null) {
            throw new IllegalArgumentException("Table '" + tableName + "' not found in REST API mapping. "
                    + "Available tables: " + apiMapping.getTables().keySet());
        }

        assetFields = RestFieldTypeMapper.toAssetFields(tableDef.getFields());

        if (ticket != null) {
            final TicketInfo ticketInfo = modelMapper.fromBytes(ticket.getBytes(), TicketInfo.class);
            LOGGER.debug("Ticket info: {}", ticketInfo);
        }
    }

    // ---- SdkSourceInteraction interface (new path) ----

    /**
     * {@inheritDoc}
     */
    @Override
    public Schema getSchema() {
        return ArrowConversions.toArrow(assetFields);
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
    public void beginStream(BufferAllocator allocator) {
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
    public boolean hasNextBatch() {
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * @deprecated The pull-based path is not supported in this implementation.
     */
    @Deprecated
    @Override
    public VectorSchemaRoot nextBatch() {
        throw new UnsupportedOperationException(
                "Pull-based streaming is not supported. Use stream(RowWriter) instead.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        // No persistent resources to close
    }

    // ---- private helpers ----

    private String buildUrl() {
        String host;
        Integer port;

        try {
            final URL configUrl = new URL(connector.getApiMapping().getBaseUrl());
            host = configUrl.getHost();
            port = configUrl.getPort();
            if (port == -1) {
                port = "https".equalsIgnoreCase(configUrl.getProtocol()) ? 443 : 80;
            }
        } catch (MalformedURLException e) {
            LOGGER.error("Failed to parse base URL from config: {}", connector.getApiMapping().getBaseUrl(), e);
            throw new IllegalStateException("Invalid base URL in configuration", e);
        }

        final String protocol = (port == 443) ? "https" : "http";
        final String url = protocol + "://" + host + ":" + port + tableDef.getPath();
        LOGGER.debug("Built request URL from configured host and port");
        return url;
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
