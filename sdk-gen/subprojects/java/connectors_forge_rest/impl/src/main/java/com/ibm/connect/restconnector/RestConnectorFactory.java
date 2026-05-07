/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import static org.slf4j.LoggerFactory.getLogger;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;

import com.ibm.connect.sdk.api.Connector;
import com.ibm.connect.sdk.api.PooledConnectorFactory;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightDatasourceTypes;

/**
 * A factory for creating REST connectors.
 * 
 * <p>This factory supports multiple REST connectors, each defined by a separate JSON configuration file
 * in the /config/mappings directory. Each configuration file defines a unique connector with its own
 * name, label, description, and API endpoints.
 */
@SuppressWarnings({ "PMD.AvoidDollarSigns", "PMD.ClassNamingConventions" })
public class RestConnectorFactory extends PooledConnectorFactory
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
        super();
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

        final File[] jsonFiles = configDir.listFiles((dir, name) -> name.toLowerCase(java.util.Locale.ENGLISH).endsWith(".json"));
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

                // Cache the mapping and create datasource type
                configCache.put(connectorName, mapping);
                datasourceTypeCache.put(connectorName, new RestDatasourceType(mapping, filePath));

                LOGGER.info("Loaded REST connector '{}' from file: {}", connectorName, configFile.getName());
            } catch (java.io.IOException e) {
                LOGGER.error("I/O error loading configuration from file '{}': {}", configFile.getName(), e.getMessage(), e);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                LOGGER.error("Invalid JSON in configuration file '{}': {}", configFile.getName(), e.getMessage(), e);
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
     * {@inheritDoc}
     */
    @Override
    protected Connector<?, ?> createNewConnector(String datasourceTypeName, ConnectionProperties properties)
    {
        if (configCache.containsKey(datasourceTypeName)) {
            return new RestConnector(datasourceTypeName, properties);
        }
        throw new UnsupportedOperationException(RestMsgs.DATASOURCE_TYPE_NOT_SUPPORTED.format(datasourceTypeName));
    }

    /**
     * {@inheritDoc}
     * 
     * <p>Returns all datasource types loaded from JSON configuration files.
     */
    @Override
    public CustomFlightDatasourceTypes getDatasourceTypes()
    {
        final CustomFlightDatasourceTypes types = new CustomFlightDatasourceTypes();
        for (final RestDatasourceType datasourceType : datasourceTypeCache.values()) {
            types.addDatasourceTypesItem(datasourceType);
        }
        return types;
    }
}

// Made with Bob
