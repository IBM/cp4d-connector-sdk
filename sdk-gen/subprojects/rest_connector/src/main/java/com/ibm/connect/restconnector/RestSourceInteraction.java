/* *************************************************** */

/* (C) Copyright IBM Corp. 2025                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import static org.slf4j.LoggerFactory.getLogger;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.apache.arrow.flight.Ticket;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.types.pojo.Schema;
import org.slf4j.Logger;

import com.ibm.connect.sdk.api.ArrowConversions;
import com.ibm.connect.sdk.api.Connector;
import com.ibm.connect.sdk.api.SourceInteraction;
import com.ibm.connect.sdk.api.TicketInfo;
import com.ibm.connect.sdk.util.ModelMapper;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetField;

/**
 * An interaction with a REST API asset as a source.
 *
 * <p>Reads data from a REST API endpoint defined in the .rest mapping file,
 * converts the JSON response to Arrow format in a streaming fashion.
 */
@SuppressWarnings({ "PMD.AvoidDollarSigns", "PMD.ClassNamingConventions" })
public class RestSourceInteraction implements SourceInteraction<Connector<?, ?>>
{
    private static final Logger LOGGER = getLogger(RestSourceInteraction.class);

    private static final int DEFAULT_BATCH_SIZE = 1000;

    private final ModelMapper modelMapper = new ModelMapper();
    private final RestConnector connector;
    private final CustomFlightAssetDescriptor asset;
    private final RestTableDefinition tableDef;
    private final List<CustomFlightAssetField> assetFields;

    private VectorSchemaRoot vectorSchemaRoot;
    private Iterator<VectorSchemaRoot> batchIterator;
    private JsonToRecordStream jsonStream;

    /**
     * Creates a REST source interaction.
     *
     * @param connector
     *            the connector managing the connection to the data source
     * @param asset
     *            the asset from which to read
     * @param ticket
     *            a Flight ticket to read a partition or null to get tickets
     * @throws Exception
     */
    public RestSourceInteraction(RestConnector connector, CustomFlightAssetDescriptor asset, Ticket ticket) throws Exception
    {
        if (connector == null) {
            throw new IllegalArgumentException(RestMsgs.MISSING_CONNECTOR.format());
        }
        this.connector = connector;
        this.asset = asset;

        // Resolve the table name from the asset path or interaction properties
        final String tableName = resolveTableName(asset);
        LOGGER.info("Creating source interaction for table: {}", tableName);

        // Look up the table definition from the loaded API mapping
        final RestApiMapping apiMapping = connector.getApiMapping();
        if (apiMapping == null) {
            throw new IllegalStateException("API mapping not loaded. Call connect() first.");
        }
        tableDef = apiMapping.getTable(tableName);
        if (tableDef == null) {
            throw new IllegalArgumentException("Table '" + tableName + "' not found in REST API mapping. "
                    + "Available tables: " + apiMapping.getTables().keySet());
        }

        // Convert field definitions to asset fields
        assetFields = RestFieldTypeMapper.toAssetFields(tableDef.getFields());

        if (ticket != null) {
            final TicketInfo ticketInfo = modelMapper.fromBytes(ticket.getBytes(), TicketInfo.class);
            LOGGER.debug("Ticket info: {}", ticketInfo);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Schema getSchema() throws Exception
    {
        return ArrowConversions.toArrow(assetFields);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Ticket> getTickets() throws Exception
    {
        // Single partition — return one ticket with requestId and partitionIndex
        final String requestId = UUID.randomUUID().toString();
        final TicketInfo ticketInfo = new TicketInfo()
                .requestId(requestId)
                .partitionIndex(0);
        final byte[] ticketBytes = modelMapper.toBytes(ticketInfo);
        return Collections.singletonList(new Ticket(ticketBytes));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void beginStream(BufferAllocator allocator) throws Exception
    {
        final Schema schema = ArrowConversions.toArrow(assetFields);
        vectorSchemaRoot = VectorSchemaRoot.create(schema, allocator);

        // Build the full URL using connection properties (host and port) + path
        final String url = buildUrl();
        LOGGER.info("Starting stream from URL: {}", url);

        // Build authentication headers from connection properties
        final java.util.Map<String, String> authHeaders = buildAuthHeaders();

        // Create the streaming JSON-to-Record iterator with data path and authentication headers
        jsonStream = new JsonToRecordStream(url, tableDef.getDataPath(), tableDef.getFields(), authHeaders);

        // Determine batch size
        final int batchSize = (asset.getBatchSize() != null && asset.getBatchSize() > 0)
                ? asset.getBatchSize()
                : DEFAULT_BATCH_SIZE;

        LOGGER.debug("Using batch size: {}", batchSize);

        // Create the Arrow batch iterator using ArrowConversions
        batchIterator = ArrowConversions.toArrow(vectorSchemaRoot, jsonStream, batchSize);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasNextBatch() throws Exception
    {
        return batchIterator != null && batchIterator.hasNext();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VectorSchemaRoot nextBatch() throws Exception
    {
        return batchIterator.next();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws Exception
    {
        if (jsonStream != null) {
            try {
                jsonStream.close();
            }
            catch (Exception e) {
                LOGGER.warn("Error closing JSON stream", e);
            }
        }
        if (vectorSchemaRoot != null) {
            try {
                vectorSchemaRoot.close();
            }
            catch (Exception e) {
                LOGGER.warn("Error closing VectorSchemaRoot", e);
            }
        }
    }

    /**
     * Resolves the table name from the asset descriptor.
     *
     * <p>The table name is determined by (in order of priority):
     * <ol>
     *   <li>The last segment of the asset path (e.g. "/ROCKETS/ROCKETS" → "ROCKETS")</li>
     *   <li>The asset name</li>
     *   <li>The asset ID</li>
     * </ol>
     *
     * @param asset
     *            the asset descriptor
     * @return the table name
     */
    private static String resolveTableName(CustomFlightAssetDescriptor asset)
    {
        // Try path first: "/ROCKETS/ROCKETS" → last segment "ROCKETS"
        final String path = asset.getPath();
        if (path != null && !path.isEmpty()) {
            final String[] segments = path.split("/");
            for (int i = segments.length - 1; i >= 0; i--) {
                if (!segments[i].isEmpty()) {
                    return segments[i].toUpperCase(java.util.Locale.ENGLISH);
                }
            }
        }

        // Fall back to asset name
        if (asset.getName() != null && !asset.getName().isEmpty()) {
            return asset.getName().toUpperCase(java.util.Locale.ENGLISH);
        }

        // Fall back to asset ID
        if (asset.getId() != null && !asset.getId().isEmpty()) {
            return asset.getId().toUpperCase(java.util.Locale.ENGLISH);
        }

        throw new IllegalArgumentException("Cannot determine table name from asset: path=" + path
                + ", name=" + asset.getName() + ", id=" + asset.getId());
    }

    /**
     * Builds the full URL for the REST API call using connection properties.
     *
     * <p>The URL is constructed from:
     * <ul>
     *   <li>Protocol: determined from port (443 = https, otherwise http)</li>
     *   <li>Host: from connection properties or default from config</li>
     *   <li>Port: from connection properties or default from config</li>
     *   <li>Path: from table definition</li>
     * </ul>
     *
     * @return the full URL
     * @throws Exception if connection properties cannot be retrieved
     */
    private String buildUrl() throws Exception
    {
        // Get connection properties from asset
        final com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties connProps = asset.getConnectionProperties();
        
        // Extract host and port from connection properties
        // ConnectionProperties extends HashMap<String, Object>
        String host = null;
        Integer port = null;
        
        if (connProps != null) {
            final Object hostObj = connProps.get("host");
            final Object portObj = connProps.get("port");
            
            if (hostObj != null) {
                host = hostObj.toString();
            }
            if (portObj != null) {
                if (portObj instanceof Number) {
                    port = ((Number) portObj).intValue();
                } else {
                    try {
                        port = Integer.parseInt(portObj.toString());
                    } catch (NumberFormatException e) {
                        LOGGER.warn("Invalid port value: {}", portObj);
                    }
                }
            }
        }
        
        // Fall back to defaults from config if not provided
        if (host == null || port == null) {
            try {
                final java.net.URL configUrl = new java.net.URL(connector.getApiMapping().getBaseUrl());
                if (host == null) {
                    host = configUrl.getHost();
                }
                if (port == null) {
                    port = configUrl.getPort();
                    if (port == -1) {
                        port = "https".equalsIgnoreCase(configUrl.getProtocol()) ? 443 : 80;
                    }
                }
            } catch (java.net.MalformedURLException e) {
                LOGGER.error("Failed to parse base URL from config: {}", connector.getApiMapping().getBaseUrl(), e);
                throw new IllegalStateException("Invalid base URL in configuration", e);
            }
        }
        
        // Determine protocol based on port
        final String protocol = (port == 443) ? "https" : "http";
        
        // Build the URL
        final String url = protocol + "://" + host + ":" + port + tableDef.getPath();
        LOGGER.debug("Built URL: {} (host={}, port={})", url, host, port);
        
        return url;
    }

    /**
     * Builds authentication headers from connection properties based on the authentication type.
     *
     * @return a map of HTTP headers for authentication, or null if no authentication is configured
     * @throws Exception if authentication properties are missing or invalid
     */
    private java.util.Map<String, String> buildAuthHeaders() throws Exception
    {
        final String authType = connector.getApiMapping().getAuthenticationType();
        
        if ("none".equals(authType)) {
            // No authentication required
            return null;
        }

        // Get connection properties from asset
        final com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties connProps = asset.getConnectionProperties();
        if (connProps == null) {
            LOGGER.warn("No connection properties provided for authentication type: {}", authType);
            return null;
        }

        final java.util.Map<String, String> headers = new java.util.HashMap<>();

        if ("api_key".equals(authType)) {
            // API Key authentication using Authorization header
            final Object apiKeyObj = connProps.get("api_key");
            if (apiKeyObj != null) {
                final String apiKey = apiKeyObj.toString();
                headers.put("Authorization", "ApiKey " + apiKey);
                LOGGER.debug("Using API Key authentication");
            } else {
                LOGGER.warn("API key not provided in connection properties");
            }
        }
        else if ("oauth2".equals(authType)) {
            // OAuth 2.0 Bearer Token authentication
            final Object tokenObj = connProps.get("bearer_token");
            if (tokenObj != null) {
                final String token = tokenObj.toString();
                headers.put("Authorization", "Bearer " + token);
                LOGGER.debug("Using OAuth 2.0 Bearer Token authentication");
            } else {
                LOGGER.warn("Bearer token not provided in connection properties");
            }
        }
        else if ("basic".equals(authType)) {
            // Basic authentication (username:password encoded in Base64)
            final Object usernameObj = connProps.get("username");
            final Object passwordObj = connProps.get("password");
            
            if (usernameObj != null && passwordObj != null) {
                final String username = usernameObj.toString();
                final String password = passwordObj.toString();
                final String credentials = username + ":" + password;
                final String encodedCredentials = java.util.Base64.getEncoder().encodeToString(credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                headers.put("Authorization", "Basic " + encodedCredentials);
                LOGGER.debug("Using Basic authentication for user: {}", username);
            } else {
                LOGGER.warn("Username or password not provided in connection properties");
            }
        }

        return headers.isEmpty() ? null : headers;
    }
}

// Made with Bob
