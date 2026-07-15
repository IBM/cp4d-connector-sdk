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
import com.ibm.wdp.connect.sdk.connector.AssetDescriptor;
import com.ibm.wdp.connect.sdk.connector.DiscoveryCriteria;
import com.ibm.wdp.connect.sdk.connector.SdkConnector;

/**
 * An Arrow-based connector for connecting to a REST API data source.
 *
 * <p>Implements both the legacy {@link Connector} interface (for backward compatibility with
 * existing SDK tooling) and the new {@link SdkConnector} interface (for the Arrow-native path
 * through {@link AbstractSdkConnectorFlightProducer}).
 *
 * <p>The connector reads a JSON configuration file that describes the API endpoints
 * and their field schemas. It uses this configuration to discover assets and read data
 * from the REST API in a streaming fashion.
 *
 * <p>Each connector instance is associated with a specific datasource type (connector name)
 * and loads its configuration from the factory's cache.
 */
public class RestConnector implements Connector<RestSourceInteraction, RestTargetInteraction>,
        SdkConnector<RestSourceInteraction, RestTargetInteraction, RestDiscoveryInteraction>
{
    private static final Logger LOGGER = getLogger(RestConnector.class);

    private final String datasourceTypeName;
    private RestApiMapping apiMapping;

    /**
     * Creates an Arrow-based REST connector (legacy SCAPI path).
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
    }

    /**
     * Creates an Arrow-based REST connector with a pre-built API mapping.
     *
     * <p>When {@code apiMapping} is non-null, {@link #connect()} will skip the
     * singleton factory lookup and use the supplied mapping directly.
     *
     * @param datasourceTypeName
     *            the datasource type name (connector name)
     * @param properties
     *            connection properties (currently unused, reserved for future use)
     * @param apiMapping
     *            a pre-built {@link RestApiMapping} to use instead of the factory cache;
     *            may be {@code null}, in which case behaviour is identical to the two-arg
     *            constructor
     */
    @SuppressWarnings("PMD.UnusedFormalParameter")
    public RestConnector(String datasourceTypeName, ConnectionProperties properties, RestApiMapping apiMapping)
    {
        this.datasourceTypeName = datasourceTypeName;
        this.apiMapping = apiMapping;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Loads the JSON configuration from the factory's cache, unless a mapping was
     * already supplied via the constructor or {@link #setApiMapping(RestApiMapping)},
     * in which case the factory lookup is skipped entirely.
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
    }

    /**
     * Returns the loaded API mapping.
     *
     * @return the REST API mapping, or {@code null} if {@link #connect()} has not been called
     *         and no mapping was pre-set
     */
    public RestApiMapping getApiMapping()
    {
        return apiMapping;
    }

    /**
     * Sets the API mapping directly, bypassing the factory singleton.
     *
     * <p>Must be called before {@link #connect()} if you want to inject a mapping
     * programmatically (e.g., in tests or embedded use-cases).
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

    // ---- SdkConnector interface (new path) ----

    /**
     * {@inheritDoc}
     */
    @Override
    public Schema getSchema(AssetDescriptor asset) throws Exception
    {
        if (apiMapping == null) {
            throw new IllegalStateException("API mapping not loaded. Call connect() first.");
        }
        final String tableName = RestConnectorUtils.resolveTableName(asset.getPath(), asset.getName());
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
    public RestSourceInteraction getSourceInteraction(AssetDescriptor asset, Ticket ticket) throws Exception
    {
        return new RestSourceInteraction(this, asset, ticket);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RestTargetInteraction getTargetInteraction(AssetDescriptor asset) throws Exception
    {
        return new RestTargetInteraction(this, asset);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RestDiscoveryInteraction getDiscoveryInteraction(DiscoveryCriteria criteria) throws Exception
    {
        return new RestDiscoveryInteraction(this);
    }

    // ---- Connector interface (legacy path) ----

    /**
     * {@inheritDoc}
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
            for (final Map.Entry<String, RestTableDefinition> entry : apiMapping.getTables().entrySet()) {
                final String tableName = entry.getKey();

                final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
                descriptor.setName(tableName);
                descriptor.setId(tableName);
                descriptor.setPath("/" + tableName);
                descriptor.setDatasourceTypeName(datasourceTypeName);
                descriptor.setConnectionProperties(criteria.getConnectionProperties());
                descriptor.setHasChildren(true);

                final DiscoveredAssetType assetType = new DiscoveredAssetType();
                assetType.setType("table");
                assetType.setDataset(false);
                assetType.setDatasetContainer(true);
                descriptor.setAssetType(assetType);

                assets.add(descriptor);
                LOGGER.debug("Discovered table container: {}", tableName);
            }
        } else if (path != null && path.startsWith("/") && !path.substring(1).contains("/")) {
            final String tableName = path.substring(1);
            final RestTableDefinition tableDef = apiMapping.getTable(tableName);

            if (tableDef != null) {
                final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
                descriptor.setName(tableName);
                descriptor.setId(tableName);
                descriptor.setPath(path);
                descriptor.setDatasourceTypeName(datasourceTypeName);
                descriptor.setConnectionProperties(criteria.getConnectionProperties());
                descriptor.setHasChildren(false);
                descriptor.setFields(RestFieldTypeMapper.toAssetFields(tableDef.getFields()));

                final DiscoveredAssetType assetType = new DiscoveredAssetType();
                assetType.setType("table");
                assetType.setDataset(true);
                assetType.setDatasetContainer(false);
                descriptor.setAssetType(assetType);

                assets.add(descriptor);
                LOGGER.debug("Discovered table dataset: {}", tableName);
            } else {
                LOGGER.warn("Table not found in mapping: {}", tableName);
            }
        } else {
            LOGGER.warn("Unsupported discovery path supplied for discovery");
        }

        LOGGER.info("Discovered {} assets", assets.size());
        return assets;
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
        LOGGER.debug("RestConnector closed");
    }
}
