/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import static org.slf4j.LoggerFactory.getLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.arrow.flight.Ticket;
import org.apache.arrow.vector.types.pojo.Schema;
import org.slf4j.Logger;

import com.ibm.connect.sdk.api.ArrowConversions;
import com.ibm.connect.sdk.api.Connector;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionActionConfiguration;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionActionResponse;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetsCriteria;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveredAssetType;

/**
 * An Arrow-based connector for connecting to a REST API data source.
 *
 * <p>The connector reads a JSON configuration file that describes the API endpoints
 * and their field schemas. It uses this configuration to discover assets and read data
 * from the REST API in a streaming fashion.
 * 
 * <p>Each connector instance is associated with a specific datasource type (connector name)
 * and loads its configuration from the factory's cache.
 */
@SuppressWarnings({ "PMD.AvoidDollarSigns", "PMD.ClassNamingConventions" })
public class RestConnector implements Connector<RestSourceInteraction, RestTargetInteraction>
{
    private static final Logger LOGGER = getLogger(RestConnector.class);

    private final String datasourceTypeName;
    private RestApiMapping apiMapping;

    /**
     * Creates an Arrow-based REST connector.
     *
     * @param datasourceTypeName
     *            the datasource type name (connector name)
     * @param properties
     *            connection properties (currently unused, reserved for future use)
     */
    @SuppressWarnings("PMD.UnusedFormalParameter")
    public RestConnector(String datasourceTypeName, ConnectionProperties properties)
    {
        this.datasourceTypeName = datasourceTypeName;
        // Connection properties are currently unused since configurations are loaded from factory cache
        // The parameter is kept for API compatibility and potential future use
    }

    /**
     * {@inheritDoc}
     *
     * <p>Loads the JSON configuration from the factory's cache.
     */
    @Override
    public void connect() throws Exception
    {
        LOGGER.info("Connecting: loading REST API configuration for connector '{}'", datasourceTypeName);
        apiMapping = RestConnectorFactory.getInstance().getConfiguration(datasourceTypeName);
        
        if (apiMapping == null) {
            throw new IllegalStateException(
                RestMsgs.DATASOURCE_TYPE_NOT_SUPPORTED.format(datasourceTypeName));
        }
        
        LOGGER.info("Connected: loaded {} tables for connector '{}' ({})",
                apiMapping.getTables().size(), datasourceTypeName, apiMapping.getConnectorLabel());
    }

    /**
     * Returns the loaded API mapping.
     *
     * @return the REST API mapping, or null if {@link #connect()} has not been called
     */
    public RestApiMapping getApiMapping()
    {
        return apiMapping;
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
     *
     * <p>Hierarchical discovery:
     * <ul>
     * <li>Path "/" - returns all table names as containers (no fields)</li>
     * <li>Path "/{tableName}" - returns the specific table as a dataset with fields</li>
     * </ul>
     */
    @Override
    public List<CustomFlightAssetDescriptor> discoverAssets(CustomFlightAssetsCriteria criteria) throws Exception
    {
        if (apiMapping == null) {
            throw new IllegalStateException("API mapping not loaded. Call connect() first.");
        }

        final String path = criteria.getPath();
        final List<CustomFlightAssetDescriptor> assets = new ArrayList<>();

        if ("/".equals(path)) {
            // Root discovery: return all tables as containers (no fields)
            for (final Map.Entry<String, RestTableDefinition> entry : apiMapping.getTables().entrySet()) {
                final String tableName = entry.getKey();

                final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
                descriptor.setName(tableName);
                descriptor.setId(tableName);
                descriptor.setPath("/" + tableName);
                descriptor.setDatasourceTypeName(datasourceTypeName);
                descriptor.setConnectionProperties(criteria.getConnectionProperties());
                descriptor.setHasChildren(true);
                // No fields at this level

                // Asset type: container, not a dataset
                final DiscoveredAssetType assetType = new DiscoveredAssetType();
                assetType.setType("table");
                assetType.setDataset(false);
                assetType.setDatasetContainer(true);
                descriptor.setAssetType(assetType);

                assets.add(descriptor);
                LOGGER.debug("Discovered table container: {}", tableName);
            }
        }
        else if (path != null && path.startsWith("/") && !path.substring(1).contains("/")) {
            // Table-level discovery: return the specific table with fields
            final String tableName = path.substring(1); // Remove leading "/"
            final RestTableDefinition tableDef = apiMapping.getTables().get(tableName);

            if (tableDef != null) {
                final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
                descriptor.setName(tableName);
                descriptor.setId(tableName);
                descriptor.setPath(path);
                descriptor.setDatasourceTypeName(datasourceTypeName);
                descriptor.setConnectionProperties(criteria.getConnectionProperties());
                descriptor.setHasChildren(false);
                descriptor.setFields(RestFieldTypeMapper.toAssetFields(tableDef.getFields()));

                // Asset type: dataset, not a container
                final DiscoveredAssetType assetType = new DiscoveredAssetType();
                assetType.setType("table");
                assetType.setDataset(true);
                assetType.setDatasetContainer(false);
                descriptor.setAssetType(assetType);

                assets.add(descriptor);
                LOGGER.debug("Discovered table dataset: {}", tableName);
            }
            else {
                LOGGER.warn("Table not found in mapping: {}", tableName);
            }
        }
        else {
            LOGGER.warn("Unsupported discovery path supplied for discovery");
        }

        LOGGER.info("Discovered {} assets", assets.size());
        return assets;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns the Arrow schema for the given asset by looking up the table
     * definition in the API mapping.
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

        return ArrowConversions.toArrow(RestFieldTypeMapper.toAssetFields(tableDef.getFields()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RestSourceInteraction getSourceInteraction(CustomFlightAssetDescriptor asset, Ticket ticket) throws Exception
    {
        return new RestSourceInteraction(this, asset, ticket);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RestTargetInteraction getTargetInteraction(CustomFlightAssetDescriptor asset) throws Exception
    {
        return new RestTargetInteraction(this, asset);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConnectionActionResponse performAction(String action, ConnectionActionConfiguration properties) throws Exception
    {
        throw new UnsupportedOperationException(RestMsgs.UNSUPPORTED_ACTION.format(action));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws Exception
    {
        // No persistent resources to close
        LOGGER.debug("RestConnector closed");
    }

}

// Made with Bob
