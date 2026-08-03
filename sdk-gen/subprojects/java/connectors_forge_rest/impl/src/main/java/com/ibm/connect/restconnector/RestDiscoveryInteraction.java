/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import static org.slf4j.LoggerFactory.getLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;

import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetsCriteria;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveredAssetType;
import com.ibm.wdp.connect.sdk.connector.SdkDiscoveryInteraction;

/**
 * Discovery interaction for a REST API connector.
 *
 * <p>Translates the connector's hierarchical path-based discovery into a list of
 * {@link CustomFlightAssetDescriptor} objects:
 * <ul>
 *   <li>Path "/" — returns all tables as containers (no fields)</li>
 *   <li>Path "/{tableName}" — returns the specific table as a dataset</li>
 * </ul>
 */
public class RestDiscoveryInteraction implements SdkDiscoveryInteraction
{
    private static final Logger LOGGER = getLogger(RestDiscoveryInteraction.class);

    private final RestConnector connector;

    /**
     * Creates a REST discovery interaction.
     *
     * @param connector
     *            the connector providing the loaded API mapping
     */
    public RestDiscoveryInteraction(RestConnector connector)
    {
        this.connector = connector;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CustomFlightAssetDescriptor> discoverAssets(CustomFlightAssetsCriteria criteria)
    {
        final RestApiMapping apiMapping = connector.getApiMapping();
        if (apiMapping == null) {
            throw new IllegalStateException("API mapping not loaded. Call connect() first.");
        }

        final String path = criteria.getPath();
        final List<CustomFlightAssetDescriptor> assets = new ArrayList<>();

        if ("/".equals(path)) {
            for (final Map.Entry<String, RestTableDefinition> entry : apiMapping.getTables().entrySet()) {
                final String tableName = entry.getKey();
                final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
                descriptor.setId(tableName);
                descriptor.setName(tableName);
                descriptor.setPath("/" + tableName);
                descriptor.setDatasourceTypeName(criteria.getDatasourceTypeName());
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
                descriptor.setId(tableName);
                descriptor.setName(tableName);
                descriptor.setPath(path);
                descriptor.setDatasourceTypeName(criteria.getDatasourceTypeName());
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
            LOGGER.warn("Unsupported discovery path: {}", path);
        }

        LOGGER.info("Discovered {} assets", assets.size());
        return assets;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close()
    {
        // No persistent resources to close
    }
}
