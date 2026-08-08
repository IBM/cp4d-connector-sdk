/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Map;

import com.ibm.wdp.connect.common.sdk.api.models.CustomDatasourceTypeProperty;
import com.ibm.wdp.connect.common.sdk.api.models.CustomDatasourceTypeProperty.TypeEnum;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightDatasourceType;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightDatasourceTypeProperties;
import com.ibm.wdp.connect.common.sdk.api.models.DatasourceTypeDiscovery;
import com.ibm.wdp.connect.common.sdk.api.models.DatasourceTypeOrigin;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveryAssetType;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveryPathProperty;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveryPathSegment;

/**
 * The definition of a REST connector data source type.
 *
 * <p>This connector reads data from REST APIs described by a JSON configuration file.
 * The configuration file defines connector metadata (name, label, description), the base URL,
 * available tables (endpoints), and their field schemas.
 *
 * <p>Each instance of RestDatasourceType represents one connector defined by one JSON configuration file.
 * Multiple instances can be created from multiple configuration files in the /config/mappings directory.
 */
public class RestDatasourceType extends CustomFlightDatasourceType
{
    private final String configFilePath;

    /**
     * Creates a REST connector data source type from a configuration file.
     *
     * @param mapping
     *            the REST API mapping loaded from a JSON configuration file
     * @param configFilePath
     *            the path to the configuration file (for reference)
     */
    public RestDatasourceType(RestApiMapping mapping, String configFilePath)
    {
        super();
        this.configFilePath = configFilePath;

        // Set the data source type attributes from the mapping
        setName(mapping.getConnectorName());
        setLabel(mapping.getConnectorLabel());
        setDescription(mapping.getConnectorDescription());
        setAllowedAsSource(true);
        setAllowedAsTarget(false);
        setStatus(CustomFlightDatasourceType.StatusEnum.ACTIVE);
        setTags(Collections.emptyList());

        // Set origin from $metadata (connector_source → name, forge_version → version)
        final Map<String, String> originMap = mapping.getOrigin();
        if (!originMap.isEmpty()) {
            setOrigin(new DatasourceTypeOrigin()
                    .name(originMap.get("name"))
                    .version(originMap.get("version")));
        }

        final CustomFlightDatasourceTypeProperties properties = new CustomFlightDatasourceTypeProperties();
        setProperties(properties);

        // Parse the base URL to extract host and port
        String defaultHost = "localhost";
        int defaultPort = 443;
        try {
            final URL url = new URL(mapping.getBaseUrl());
            defaultHost = url.getHost();
            defaultPort = url.getPort();
            // If port is not specified in URL, use default based on protocol
            if (defaultPort == -1) {
                defaultPort = "https".equalsIgnoreCase(url.getProtocol()) ? 443 : 80;
            }
        } catch (MalformedURLException e) {
            // Use defaults if URL parsing fails
        }

        // Define the connection properties.
        // host: the hostname or IP address of the REST API server
        properties.addConnectionItem(
                new CustomDatasourceTypeProperty()
                        .name("host")
                        .label("Host")
                        .description("The hostname or IP address of the REST API server")
                        .type(TypeEnum.STRING)
                        .required(true)
                        .defaultValue(defaultHost)
                        .group("domain"));

        // port: the port number of the REST API server
        properties.addConnectionItem(
                new CustomDatasourceTypeProperty()
                        .name("port")
                        .label("Port")
                        .description("The port number of the REST API server")
                        .type(TypeEnum.INTEGER)
                        .required(true)
                        .defaultValue(String.valueOf(defaultPort))
                        .group("domain"));

        // Add one connection property per header definition in the auth config.
        // UI-only fields (header == null) are still shown so the user can supply
        // credentials that are referenced inside another entry's value template.
        final AuthConfig authConfig = mapping.getAuthConfig();
        for (final AuthConfig.HeaderDef hd : authConfig.getHeaders()) {
            final CustomDatasourceTypeProperty prop = new CustomDatasourceTypeProperty()
                    .name(hd.getName())
                    .label(hd.getLabel())
                    .description(hd.getDescription())
                    .type(TypeEnum.STRING)
                    .required(true)
                    .masked(hd.isMasked())
                    .group("credentials");
            properties.addConnectionItem(prop);
        }

        // Define the source interaction properties.
        // table_name: the name of the table (endpoint) to read from
        properties.addSourceItem(
                new CustomDatasourceTypeProperty()
                        .name("table_name")
                        .label("Table Name")
                        .description("The name of the table (API endpoint) to read from, as defined in the JSON configuration file")
                        .type(TypeEnum.STRING)
                        .required(false));

        // row_limit: optional limit on the number of rows to return
        properties.addSourceItem(
                new CustomDatasourceTypeProperty()
                        .name("row_limit")
                        .label("Row Limit")
                        .description("Maximum number of rows to return (0 or empty for no limit)")
                        .type(TypeEnum.INTEGER)
                        .required(false));

        // Define the discovery configuration.
        final DatasourceTypeDiscovery discovery = new DatasourceTypeDiscovery();
        setDiscovery(discovery);

        // Asset types: table (one per API endpoint)
        discovery.addAssetTypesItem(
                new DiscoveryAssetType()
                        .name("table")
                        .label("Table"));

        // Path properties: table_name maps to the table asset type
        discovery.addPathPropertiesItem(
                new DiscoveryPathProperty()
                        .propertyName("table_name")
                        .addSegmentsItem(new DiscoveryPathSegment().assetTypes("table").repeatable(false)));
    }

    /**
     * Returns the path to the configuration file for this datasource type.
     *
     * @return the configuration file path
     */
    public String getConfigFilePath()
    {
        return configFilePath;
    }

}

// Made with Bob
