/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import static org.slf4j.LoggerFactory.getLogger;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 *       For tables with {@link PathKeyDef path keys}, one dataset is emitted per unique
 *       combination of resolved key values.  Each dataset carries all resolved variable
 *       values serialised as a JSON object in its {@code interactionProperties} under the
 *       key {@link RestInputInteraction#INTERACTION_PROP_PATH_KEY_VALUES}.</li>
 * </ul>
 *
 * <h3>Chained key resolution</h3>
 * <p>Path keys are processed in declared order.  The engine maintains a list of
 * <em>contexts</em> — each context is a {@code Map<String, String>} of variable names to
 * their resolved values accumulated so far.  Processing starts with a single empty context
 * and expands as follows for each {@link PathKeyDef}:
 * <ol>
 *   <li>For every existing context, substitute its variable bindings (plus connection
 *       properties) into the entry's {@code source_path}.</li>
 *   <li>Fetch the values of {@code source_field} from that URL.</li>
 *   <li>For each fetched value, create a new context = existing context ∪ {variable → value}.</li>
 *   <li>Replace the old context list with all newly created contexts.</li>
 * </ol>
 * <p>Because step 2 uses the <em>specific</em> context that resolved the {@code source_path},
 * a dependent variable (e.g. {@code repo} whose path is {@code /orgs/$owner/repos}) only
 * fetches the repos that belong to the specific owner in that context — there is no
 * Cartesian product across unrelated values.
 */
public class RestDiscoveryInteraction implements SdkDiscoveryInteraction
{
    private static final Logger LOGGER = getLogger(RestDiscoveryInteraction.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
                if (tableDef.hasPathKeys()) {
                    discoverPathKeyAssets(criteria, apiMapping, tableName, tableDef, assets);
                } else {
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
     * Resolves all path-key variables for {@code tableDef} using chained context expansion
     * and emits one {@link CustomFlightAssetDescriptor} per final context into {@code assets}.
     *
     * <p>See the class-level Javadoc for a description of the algorithm.
     */
    private void discoverPathKeyAssets(CustomFlightAssetsCriteria criteria,
            RestApiMapping apiMapping, String tableName, RestTableDefinition tableDef,
            List<CustomFlightAssetDescriptor> assets)
    {
        final Map<String, Object> connProps = criteria.getConnectionProperties() != null
                ? criteria.getConnectionProperties() : Collections.emptyMap();
        final Map<String, String> authHeaders = RestInputInteraction.buildAuthHeaders(
                apiMapping.getAuthConfig(), connProps);
        final String acceptHeader = apiMapping.getAcceptHeader();

        // Seed: one empty context
        List<Map<String, String>> contexts = new ArrayList<>();
        contexts.add(new LinkedHashMap<>());

        for (final PathKeyDef pathKeyDef : tableDef.getPathKeys()) {
            final List<Map<String, String>> nextContexts = new ArrayList<>();

            for (final Map<String, String> ctx : contexts) {
                // Build a props map merging connection properties + variables resolved so far
                final Map<String, Object> resolvedProps = mergeProps(connProps, ctx);

                // Resolve any $var placeholders in source_path using already-known variables
                final String resolvedSourcePath;
                try {
                    resolvedSourcePath = RestInputInteraction.resolveTemplate(
                            pathKeyDef.getSourcePath(), resolvedProps);
                } catch (Exception e) {
                    LOGGER.warn("Cannot resolve source_path '{}' for path_keys step '{}' in table '{}': {}",
                            pathKeyDef.getSourcePath(), pathKeyDef.getVariable(), tableName, e.getMessage());
                    continue;
                }
                if (resolvedSourcePath == null) {
                    LOGGER.warn("Unresolved variable in source_path '{}' for step '{}' in table '{}' "
                            + "— required variable not yet resolved at this step",
                            pathKeyDef.getSourcePath(), pathKeyDef.getVariable(), tableName);
                    continue;
                }

                final String lookupUrl;
                try {
                    lookupUrl = RestInputInteraction.buildRequestUrl(
                            apiMapping.getBaseUrl(), resolvedSourcePath, resolvedProps);
                } catch (MalformedURLException e) {
                    LOGGER.error("Cannot build lookup URL for path_keys step '{}' in table '{}': {}",
                            pathKeyDef.getVariable(), tableName, e.getMessage());
                    continue;
                }

                final List<String> values;
                try {
                    values = JsonToArrowStream.fetchStringValues(
                            lookupUrl, pathKeyDef.getSourceDataPath(), pathKeyDef.getSourceField(),
                            authHeaders, acceptHeader);
                } catch (Exception e) {
                    LOGGER.error("Failed to fetch values for path_keys step '{}' in table '{}' from '{}': {}",
                            pathKeyDef.getVariable(), tableName, lookupUrl, e.getMessage());
                    continue;
                }

                LOGGER.debug("Step '{}' for table '{}': fetched {} value(s) from '{}'",
                        pathKeyDef.getVariable(), tableName, values.size(), lookupUrl);

                for (final String value : values) {
                    final Map<String, String> newCtx = new LinkedHashMap<>(ctx);
                    newCtx.put(pathKeyDef.getVariable(), value);
                    nextContexts.add(newCtx);
                }
            }

            contexts = nextContexts;
            if (contexts.isEmpty()) {
                LOGGER.warn("No values found for path_keys step '{}' in table '{}' — no assets will be emitted",
                        pathKeyDef.getVariable(), tableName);
                return;
            }
        }

        LOGGER.info("Discovered {} asset(s) for table '{}' via $path_keys", contexts.size(), tableName);

        for (final Map<String, String> ctx : contexts) {
            final String valuesJson = serializeContext(ctx);
            if (valuesJson == null) {
                continue;
            }
            final DiscoveredAssetInteractionProperties interactionProps =
                    new DiscoveredAssetInteractionProperties();
            interactionProps.put(RestInputInteraction.INTERACTION_PROP_PATH_KEY_VALUES, valuesJson);
            assets.add(buildDatasetDescriptor(criteria, tableName, tableDef, interactionProps));
            LOGGER.debug("Emitted asset for table '{}' with path_key_values='{}'", tableName, valuesJson);
        }
    }

    /**
     * Serialises a resolved-variable context map to a compact JSON string,
     * e.g. {@code {"owner":"acme","repo":"my-service"}}.
     *
     * @return the JSON string, or {@code null} if serialisation fails
     */
    private static String serializeContext(Map<String, String> ctx)
    {
        try {
            return OBJECT_MAPPER.writeValueAsString(ctx);
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to serialise path_key context {}: {}", ctx, e.getMessage());
            return null;
        }
    }

    /**
     * Builds a combined props map from connection properties and resolved path-key variables.
     * Path-key variables take precedence over connection properties with the same name.
     */
    private static Map<String, Object> mergeProps(Map<String, Object> connProps,
            Map<String, String> ctx)
    {
        if (ctx.isEmpty()) {
            return connProps;
        }
        final Map<String, Object> merged = new LinkedHashMap<>(connProps);
        merged.putAll(ctx);
        return merged;
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
