/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import static org.slf4j.LoggerFactory.getLogger;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;

import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetsCriteria;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveredAssetInteractionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveredAssetType;
import com.ibm.wdp.connect.sdk.connector.SdkDiscoveryInteraction;

/**
 * Discovery interaction for a REST API connector.
 *
 * <p>Translates the connector's hierarchical path-based discovery into a list of
 * {@link CustomFlightAssetDescriptor} objects:
 * <ul>
 *   <li>Path "/" — returns all tables as containers (no fields)</li>
 *   <li>Path "/{tableName}" — returns the specific table as a dataset.
 *       For tables with a {@link PathKeyDef}, one dataset is emitted per key value
 *       discovered from the lookup endpoint; each dataset carries the resolved
 *       key value in its {@code interactionProperties} under the key
 *       {@link RestInputInteraction#INTERACTION_PROP_PATH_KEY_VALUE}.</li>
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
            // Root: list all tables as containers
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
                if (tableDef.getPathKey() != null) {
                    // Table has a path_key: emit one dataset per discovered key value
                    discoverPathKeyAssets(criteria, apiMapping, tableName, tableDef, assets);
                } else {
                    // Ordinary table: emit a single dataset
                    assets.add(buildDatasetDescriptor(criteria, tableName, tableDef, null));
                }
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

    // ---- private helpers ----

    /**
     * Fetches key values from the lookup endpoint declared in the table's {@link PathKeyDef}
     * and emits one {@link CustomFlightAssetDescriptor} per value into {@code assets}.
     *
     * <p>Each emitted descriptor carries the concrete key value in its
     * {@code interactionProperties} under
     * {@link RestInputInteraction#INTERACTION_PROP_PATH_KEY_VALUE}, so that
     * {@link RestInputInteraction#buildUrl()} can substitute it into the path at read time.
     */
    private void discoverPathKeyAssets(CustomFlightAssetsCriteria criteria,
            RestApiMapping apiMapping, String tableName, RestTableDefinition tableDef,
            List<CustomFlightAssetDescriptor> assets)
    {
        final PathKeyDef pathKey = tableDef.getPathKey();
        final Map<String, Object> connProps = criteria.getConnectionProperties() != null
                ? criteria.getConnectionProperties() : java.util.Collections.emptyMap();

        final String lookupUrl;
        try {
            lookupUrl = RestInputInteraction.buildRequestUrl(
                    apiMapping.getBaseUrl(), pathKey.getSourcePath(), connProps);
        } catch (MalformedURLException e) {
            LOGGER.error("Cannot build lookup URL for table '{}' path_key: {}", tableName, e.getMessage());
            return;
        }

        final Map<String, String> authHeaders = RestInputInteraction.buildAuthHeaders(
                apiMapping.getAuthConfig(), connProps);
        final String acceptHeader = apiMapping.getAcceptHeader();

        final List<String> keyValues;
        try {
            keyValues = JsonToArrowStream.fetchStringValues(
                    lookupUrl, pathKey.getSourceDataPath(), pathKey.getSourceField(),
                    authHeaders, acceptHeader);
        } catch (Exception e) {
            LOGGER.error("Failed to fetch path_key values for table '{}' from '{}': {}",
                    tableName, lookupUrl, e.getMessage());
            return;
        }

        LOGGER.info("Discovered {} key value(s) for table '{}' via path_key variable '{}'",
                keyValues.size(), tableName, pathKey.getVariable());

        for (final String keyValue : keyValues) {
            final DiscoveredAssetInteractionProperties interactionProps =
                    new DiscoveredAssetInteractionProperties();
            interactionProps.put(RestInputInteraction.INTERACTION_PROP_PATH_KEY_VALUE, keyValue);
            assets.add(buildDatasetDescriptor(criteria, tableName, tableDef, interactionProps));
            LOGGER.debug("Discovered path_key asset: table='{}', {}='{}'",
                    tableName, pathKey.getVariable(), keyValue);
        }
    }

    /**
     * Builds a single dataset {@link CustomFlightAssetDescriptor} for a table.
     *
     * @param interactionProps
     *            optional interaction properties to attach; {@code null} for ordinary tables
     */
    private static CustomFlightAssetDescriptor buildDatasetDescriptor(
            CustomFlightAssetsCriteria criteria, String tableName,
            RestTableDefinition tableDef, DiscoveredAssetInteractionProperties interactionProps)
    {
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        descriptor.setId(tableName);
        descriptor.setName(tableName);
        descriptor.setPath("/" + tableName);
        descriptor.setDatasourceTypeName(criteria.getDatasourceTypeName());
        descriptor.setConnectionProperties(criteria.getConnectionProperties());
        descriptor.setHasChildren(false);
        descriptor.setFields(RestFieldTypeMapper.toAssetFields(tableDef.getFields()));
        if (interactionProps != null) {
            descriptor.setInteractionProperties(interactionProps);
        }
        final DiscoveredAssetType assetType = new DiscoveredAssetType();
        assetType.setType("table");
        assetType.setDataset(true);
        assetType.setDatasetContainer(false);
        descriptor.setAssetType(assetType);
        return descriptor;
    }
}
