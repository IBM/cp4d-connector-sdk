/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightDatasourceTypes;
import com.ibm.wdp.connect.sdk.connector.SdkConnector;
import com.ibm.wdp.connect.sdk.connector.SdkConnectorFactory;

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
    private static final Logger LOGGER = LoggerFactory.getLogger(RestConnectorFactory.class);
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
    public CustomFlightDatasourceTypes getDatasourceTypes()
    {
        final CustomFlightDatasourceTypes types = new CustomFlightDatasourceTypes();
        types.setDatasourceTypes(new ArrayList<>(datasourceTypeCache.values()));
        return types;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SdkConnector<?, ?, ?> createConnector(String datasourceTypeName,
            ConnectionProperties properties) {
        if (configCache.containsKey(datasourceTypeName)) {
            return new RestConnector(datasourceTypeName, properties, configCache.get(datasourceTypeName));
        }
        throw new UnsupportedOperationException(RestMsgs.DATASOURCE_TYPE_NOT_SUPPORTED.format(datasourceTypeName));
    }

    /** {@inheritDoc} */
    @Override
    public void setLogger(Logger logger)
    {
        // no-op: this factory uses its own static logger
    }

    /** {@inheritDoc} */
    @Override
    public Logger getLogger()
    {
        return LOGGER;
    }
}
