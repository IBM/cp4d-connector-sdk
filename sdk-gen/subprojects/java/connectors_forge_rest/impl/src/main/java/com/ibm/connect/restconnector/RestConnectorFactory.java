/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import static org.slf4j.LoggerFactory.getLogger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;

import com.ibm.wdp.connect.sdk.connector.ConnectionProperties;
import com.ibm.wdp.connect.sdk.connector.SdkConnector;
import com.ibm.wdp.connect.sdk.connector.SdkConnectorFactory;
import com.ibm.wdp.connect.sdk.connector.SdkDatasourceTypes;
import com.ibm.wdp.connect.sdk.connector.forge.RestApiMapping;
import com.ibm.wdp.connect.sdk.connector.forge.RestApiMappingLoader;

/**
 * A factory for creating REST connectors.
 *
 * <p>Implements {@link SdkConnectorFactory} for the Arrow-native path through
 * {@link RestFlightProducer}.
 *
 * <p>This factory supports multiple REST connectors, each defined by a separate JSON configuration
 * file in the /config/mappings directory. Each configuration file defines a unique connector with
 * its own name, label, description, and API endpoints.
 */
public class RestConnectorFactory implements SdkConnectorFactory
{
    private static final Logger LOGGER = getLogger(RestConnectorFactory.class);
    private static final RestConnectorFactory INSTANCE = new RestConnectorFactory();

    /** Default directory for configuration files */
    private static final String CONFIG_DIRECTORY = "/config/mappings";

    /** Cache of loaded configurations: datasourceTypeName -> RestApiMapping */
    private final Map<String, RestApiMapping> configCache = new HashMap<>();

    /** Cache of datasource types: datasourceTypeName -> RestDatasourceType */
    private final Map<String, RestDatasourceType> datasourceTypeCache = new HashMap<>();

    /**
     * Private constructor - loads all configurations at startup.
     */
    private RestConnectorFactory()
    {
        loadAllConfigurations();
    }

    /**
     * A connector factory instance.
     *
     * @return a connector factory instance
     */
    public static RestConnectorFactory getInstance()
    {
        return INSTANCE;
    }

    /**
     * Loads all JSON configuration files from the config directory.
     */
    private void loadAllConfigurations()
    {
        final File configDir = new File(CONFIG_DIRECTORY);
        if (!configDir.exists() || !configDir.isDirectory()) {
            LOGGER.warn("Configuration directory '{}' does not exist. No REST connectors will be available.", CONFIG_DIRECTORY);
            return;
        }

        final File[] jsonFiles = configDir.listFiles((dir, name) -> name.toLowerCase(Locale.ENGLISH).endsWith(".json"));
        if (jsonFiles == null || jsonFiles.length == 0) {
            LOGGER.warn("No .json configuration files found in '{}'. No REST connectors will be available.", CONFIG_DIRECTORY);
            return;
        }

        LOGGER.info("Found {} JSON configuration file(s) in '{}'", jsonFiles.length, CONFIG_DIRECTORY);

        for (final File configFile : jsonFiles) {
            try {
                final String filePath = configFile.getAbsolutePath();
                final RestApiMapping mapping = RestApiMappingLoader.load(filePath);
                final String connectorName = mapping.getConnectorName();

                configCache.put(connectorName, mapping);
                datasourceTypeCache.put(connectorName, new RestDatasourceType(mapping, filePath));

                LOGGER.info("Loaded REST connector '{}' from file: {}", connectorName, configFile.getName());
            } catch (IOException e) {
                LOGGER.error("I/O error loading configuration from file '{}': {}", configFile.getName(), e.getMessage(), e);
            } catch (IllegalArgumentException e) {
                LOGGER.error("Invalid configuration in file '{}': {}", configFile.getName(), e.getMessage(), e);
            } catch (RuntimeException e) {
                LOGGER.error("Unexpected error loading configuration from file '{}': {}", configFile.getName(), e.getMessage(), e);
            }
        }

        LOGGER.info("Successfully loaded {} REST connector(s)", configCache.size());
    }

    /**
     * Returns the cached configuration for a given datasource type name.
     *
     * @param datasourceTypeName
     *            the datasource type name (connector name)
     * @return the REST API mapping, or null if not found
     */
    public RestApiMapping getConfiguration(String datasourceTypeName)
    {
        return configCache.get(datasourceTypeName);
    }

    /**
     * Registers a pre-loaded {@link RestApiMapping} in the factory's cache.
     *
     * <p>This allows alternative loading strategies (e.g. classpath-based factories) to
     * make their mappings available to {@link RestConnector} instances without requiring
     * the configurations to reside on the filesystem at {@value #CONFIG_DIRECTORY}.
     *
     * <p>If a mapping with the same connector name is already registered, it will be
     * replaced.
     *
     * @param mapping
     *            the REST API mapping to register; must not be null
     */
    public void register(RestApiMapping mapping)
    {
        final String connectorName = mapping.getConnectorName();
        configCache.put(connectorName, mapping);
        datasourceTypeCache.put(connectorName, new RestDatasourceType(mapping, "<classpath>"));
        LOGGER.info("Registered REST connector '{}' from external source", connectorName);
    }

    // ---- SdkConnectorFactory interface ----

    /**
     * {@inheritDoc}
     */
    @Override
    public SdkDatasourceTypes getDatasourceTypes()
    {
        final List<String> typeNames = new ArrayList<>(configCache.keySet());
        if (typeNames.isEmpty()) {
            typeNames.add("__rest__");
        }
        return new SdkDatasourceTypes(typeNames);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SdkConnector<?, ?, ?> createConnector(String datasourceTypeName,
            ConnectionProperties properties) {
        if (configCache.containsKey(datasourceTypeName)) {
            // Wrap SDK ConnectionProperties into the model ConnectionProperties
            final com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties modelProps
                    = new com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties();
            if (properties != null) {
                modelProps.putAll(properties.asMap());
            }
            return new RestConnector(datasourceTypeName, modelProps);
        }
        throw new UnsupportedOperationException(RestMsgs.DATASOURCE_TYPE_NOT_SUPPORTED.format(datasourceTypeName));
    }
}
