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
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Collections;
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

    private static final int HTTP_TIMEOUT_SECONDS = 30;
    private static final int HTTP_STATUS_2XX = 2;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final String datasourceTypeName;
    private final Map<String, Object> connectionProperties;
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
        this.connectionProperties = properties != null ? properties : Collections.emptyMap();
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
        this.connectionProperties = properties != null ? properties : Collections.emptyMap();
        this.apiMapping = apiMapping;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void connect() throws Exception
    {
        if (apiMapping == null) {
            LOGGER.info("Connecting: loading REST API configuration for connector '{}'", datasourceTypeName);
            apiMapping = RestConnectorFactory.getInstance().getConfiguration(datasourceTypeName);
        }

        if (apiMapping == null) {
            throw new IllegalStateException(
                RestMsgs.DATASOURCE_TYPE_NOT_SUPPORTED.format(datasourceTypeName));
        }

        LOGGER.debug("Loaded {} tables for connector '{}' ({})",
                apiMapping.getTables().size(), datasourceTypeName, apiMapping.getConnectorLabel());

        // Verify connectivity by sending a real HTTP request to the first configured table.
        final Map.Entry<String, RestTableDefinition> firstEntry = apiMapping.getTables().entrySet().iterator().next();
        final String tableName = firstEntry.getKey();
        final RestTableDefinition tableDef = firstEntry.getValue();

        final String url = RestInputInteraction.buildRequestUrl(
                apiMapping.getBaseUrl(), tableDef.getPath(), connectionProperties);
        final Map<String, String> authHeaders = RestInputInteraction.buildAuthHeaders(
                apiMapping.getAuthConfig(), connectionProperties);

        final HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                .header("Accept", apiMapping.getAcceptHeader())
                .header("User-Agent", "CP4D-REST-Connector/1.0")
                .GET();
        if (authHeaders != null) {
            authHeaders.forEach(builder::header);
        }

        LOGGER.info("Test connection: pinging '{}' (table '{}')", url, tableName);
        final HttpResponse<Void> response = HTTP_CLIENT.send(builder.build(), BodyHandlers.discarding());
        if (response.statusCode() / 100 != HTTP_STATUS_2XX) {
            throw new IOException("Connection test failed: HTTP " + response.statusCode()
                    + " from " + url);
        }
        LOGGER.debug("Connection test passed: HTTP {} from '{}'", response.statusCode(), url);
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
