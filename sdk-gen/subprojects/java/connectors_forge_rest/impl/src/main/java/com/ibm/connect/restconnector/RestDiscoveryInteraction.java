/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import static org.slf4j.LoggerFactory.getLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;

import com.ibm.wdp.connect.sdk.connector.AssetDescriptor;
import com.ibm.wdp.connect.sdk.connector.DiscoveryCriteria;
import com.ibm.wdp.connect.sdk.connector.SdkDiscoveryInteraction;

/**
 * Discovery interaction for a REST API connector.
 *
 * <p>Translates the connector's hierarchical path-based discovery into a list of
 * {@link AssetDescriptor} objects. The discovery logic mirrors the old
 * {@code RestConnector.discoverAssets()} implementation:
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
    public List<AssetDescriptor> discoverAssets(DiscoveryCriteria criteria) {
        final RestApiMapping apiMapping = connector.getApiMapping();
        if (apiMapping == null) {
            throw new IllegalStateException("API mapping not loaded. Call connect() first.");
        }

        final String path = criteria.getPath();
        final List<AssetDescriptor> assets = new ArrayList<>();

        if ("/".equals(path)) {
            // Root discovery: return all tables as containers (no fields)
            for (final Map.Entry<String, RestTableDefinition> entry : apiMapping.getTables().entrySet()) {
                final String tableName = entry.getKey();
                assets.add(new AssetDescriptor(
                        tableName,
                        tableName,
                        "/" + tableName,
                        criteria.getDatasourceTypeName(),
                        criteria.getConnectionProperties() != null ? criteria.getConnectionProperties().asMap() : null,
                        true,  // hasChildren
                        0));
                LOGGER.debug("Discovered table container: {}", tableName);
            }
        } else if (path != null && path.startsWith("/") && !path.substring(1).contains("/")) {
            // Table-level discovery: return the specific table
            final String tableName = path.substring(1);
            final RestTableDefinition tableDef = apiMapping.getTable(tableName);

            if (tableDef != null) {
                assets.add(new AssetDescriptor(
                        tableName,
                        tableName,
                        path,
                        criteria.getDatasourceTypeName(),
                        criteria.getConnectionProperties() != null ? criteria.getConnectionProperties().asMap() : null,
                        false, // not a container
                        0));
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
    public void close() {
        // No persistent resources to close
    }
}
