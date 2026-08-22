/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.apache.arrow.flight.Ticket;
import org.apache.arrow.vector.types.pojo.Schema;
import org.slf4j.Logger;

import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetsCriteria;
import com.ibm.wdp.connect.sdk.connector.SdkConnector;

/**
 * An Arrow-based connector for connecting to a REST API data source.
 *
 * <p>Implements the {@link SdkConnector} interface for the Arrow-native path through
 * {@link RestFlightProducer}.
 *
 * <p>The connector reads a JSON configuration file that describes the API endpoints
 * and their field schemas. It uses this configuration to discover assets and read data
 * from the REST API in a streaming fashion.
 *
 * <p>Each connector instance is associated with a specific datasource type (connector name)
 * and loads its configuration from the factory's cache.
 */
public class RestConnector implements SdkConnector<RestInputInteraction, RestOutputInteraction, RestDiscoveryInteraction>
{
    private static final Logger LOGGER = getLogger(RestConnector.class);

    private final String datasourceTypeName;
    private final ConnectionProperties connectionProperties;
    private RestApiMapping apiMapping;

    /**
     * Creates an Arrow-based REST connector.
     *
     * @param datasourceTypeName
     *            the datasource type name (connector name)
     * @param properties
     *            connection properties (currently unused, reserved for future use)
     */
    public RestConnector(String datasourceTypeName, ConnectionProperties properties)
    {
        this.datasourceTypeName = datasourceTypeName;
        this.connectionProperties = properties;
    }

    /**
     * Creates an Arrow-based REST connector with a pre-built API mapping.
     *
     * @param datasourceTypeName
     *            the datasource type name (connector name)
     * @param properties
     *            connection properties (currently unused, reserved for future use)
     * @param apiMapping
     *            a pre-built {@link RestApiMapping} to use instead of the factory cache;
     *            may be {@code null}, in which case behaviour is identical to the two-arg constructor
     */
    public RestConnector(String datasourceTypeName, ConnectionProperties properties, RestApiMapping apiMapping)
    {
        this.datasourceTypeName = datasourceTypeName;
        this.connectionProperties = properties;
        this.apiMapping = apiMapping;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void connect() throws Exception
    {
        if (apiMapping != null) {
            LOGGER.debug("Skipped factory lookup. API mapping already set for connector '{}'",
                    datasourceTypeName);
            return;
        }

        LOGGER.info("Connecting: loading REST API configuration for connector '{}'", datasourceTypeName);
        apiMapping = RestConnectorFactory.getInstance().getConfiguration(datasourceTypeName);

        if (apiMapping == null) {
            throw new IllegalStateException(
                RestMsgs.DATASOURCE_TYPE_NOT_SUPPORTED.format(datasourceTypeName));
        }

        LOGGER.debug("Connected: loaded {} tables for connector '{}' ({})",
                apiMapping.getTables().size(), datasourceTypeName, apiMapping.getConnectorLabel());

        // Verify credentials by making a real HTTP request to the data source.
        // This ensures CP4D "Test Connection" actually validates against the API,
        // not just that the connector pod is reachable.
        probeConnectivity();
    }

    /**
     * Makes a lightweight authenticated HTTP GET to the first configured table endpoint
     * to verify that the supplied credentials are accepted by the data source.
     *
     * <p>Throws {@link IOException} with a clear human-readable message on:
     * <ul>
     *   <li>HTTP 401 — credentials are missing or invalid</li>
     *   <li>HTTP 403 — credentials are valid but lack permission</li>
     *   <li>HTTP 5xx — the data source returned a server error</li>
     * </ul>
     * HTTP 200, 404, 400 and similar responses are accepted — they confirm the host
     * is reachable and the credentials were not rejected.
     *
     * @throws IOException if the HTTP request fails or the data source rejects the credentials
     */
    private void probeConnectivity() throws Exception
    {
        if (apiMapping.getTables().isEmpty()) {
            LOGGER.warn("No tables configured — skipping connectivity probe");
            return;
        }

        // Use the first table's path as the probe endpoint
        final RestTableDefinition probeTable = apiMapping.getTables().values().iterator().next();

        // Build the probe URL using the same logic as RestInputInteraction
        final Map<String, Object> props = new HashMap<>();
        if (connectionProperties != null) {
            props.putAll(connectionProperties);
        }
        final String probeUrl = RestInputInteraction.buildRequestUrl(
                apiMapping.getBaseUrl(), probeTable.getPath(), props);

        LOGGER.info("Probing connectivity to data source: {}", apiMapping.getConnectorLabel());

        // Build auth headers using AuthConfig (same mechanism as actual data reads)
        final Map<String, String> authHeaders = new HashMap<>();
        final AuthConfig authConfig = apiMapping.getAuthConfig();
        if (authConfig.getType() != AuthenticationType.NONE) {
            for (final AuthConfig.HeaderDef hd : authConfig.getHeaders()) {
                if (hd.getHeader() == null || hd.getValue() == null) {
                    continue;
                }
                final String resolved = RestInputInteraction.resolveTemplate(hd.getValue(), props);
                if (resolved != null) {
                    authHeaders.put(hd.getHeader(), resolved);
                }
            }
        }

        // Execute the probe — discard the body, we only care about the status code
        final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        final HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(probeUrl))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "CP4D-REST-Connector/1.0")
                .GET();
        for (final Map.Entry<String, String> h : authHeaders.entrySet()) {
            reqBuilder.header(h.getKey(), h.getValue());
        }

        final int status = client.send(reqBuilder.build(),
                HttpResponse.BodyHandlers.discarding()).statusCode();
        LOGGER.info("Connectivity probe returned HTTP {}", status);

        if (status == 401) {
            throw new IOException(
                "Authentication failed (HTTP 401). The supplied credentials are missing or invalid.");
        }
        if (status == 403) {
            throw new IOException(
                "Access denied (HTTP 403). The credentials do not have permission to access this resource.");
        }
        if (status >= 500) {
            throw new IOException(
                "Data source returned a server error (HTTP " + status
                + "). Check the host and port settings.");
        }
        LOGGER.info("Connectivity probe successful — data source is reachable and credentials accepted");
    }

    /**
     * Returns the loaded API mapping.
     *
     * @return the REST API mapping, or {@code null} if {@link #connect()} has not been called
     */
    public RestApiMapping getApiMapping()
    {
        return apiMapping;
    }

    /**
     * Sets the API mapping directly, bypassing the factory singleton.
     *
     * @param apiMapping
     *            the {@link RestApiMapping} to use; must not be {@code null}
     */
    public void setApiMapping(RestApiMapping apiMapping)
    {
        this.apiMapping = apiMapping;
    }

    /**
     * Returns the datasource type name for this connector.
     *
     * @return the datasource type name (connector name)
     */
    public String getDatasourceTypeName()
    {
        return datasourceTypeName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Schema getSchema(CustomFlightAssetDescriptor asset) throws Exception
    {
        if (apiMapping == null) {
            throw new IllegalStateException("API mapping not loaded. Call connect() first.");
        }
        final String tableName = RestConnectorUtils.resolveTableName(asset);
        final RestTableDefinition tableDef = apiMapping.getTable(tableName);
        if (tableDef == null) {
            throw new IllegalArgumentException("Table '" + tableName + "' not found in REST API mapping.");
        }
        return ForgeSchemaBuilder.buildSchema(tableDef.getFields());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RestInputInteraction getInputInteraction(CustomFlightAssetDescriptor asset, Ticket ticket) throws Exception
    {
        return new RestInputInteraction(this, asset, ticket);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RestOutputInteraction getOutputInteraction(CustomFlightAssetDescriptor asset) throws Exception
    {
        throw new UnsupportedOperationException(RestMsgs.UNSUPPORTED_ACTION.format("write (REST connector is read-only)"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RestDiscoveryInteraction getDiscoveryInteraction(CustomFlightAssetsCriteria criteria) throws Exception
    {
        return new RestDiscoveryInteraction(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws Exception
    {
        LOGGER.debug("RestConnector closed");
    }
}
