/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import com.ibm.wdp.connect.common.sdk.api.models.CustomDatasourceTypeProperty;
import com.ibm.wdp.connect.sdk.connector.forge.AuthenticationType;
import com.ibm.wdp.connect.sdk.connector.forge.RestApiMapping;
import com.ibm.wdp.connect.common.sdk.api.models.CustomDatasourceTypeProperty.TypeEnum;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightDatasourceType;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightDatasourceTypeProperties;
import com.ibm.wdp.connect.common.sdk.api.models.DatasourceTypeDiscovery;
import com.ibm.wdp.connect.common.sdk.api.models.DatasourceTypeMetadata;
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

        // Set metadata from the $metadata section if present
        final Map<String, String> metadataMap = mapping.getMetadata();
        if (!metadataMap.isEmpty()) {
            final DatasourceTypeMetadata metadata = new DatasourceTypeMetadata();
            metadata.setConnectorSource(metadataMap.get("connector_source"));
            metadata.setTargetService(metadataMap.get("target_service"));
            metadata.setConnectorType(metadataMap.get("connector_type"));
            metadata.setCreatedBy(metadataMap.get("created_by"));
            metadata.setForgeVersion(metadataMap.get("forge_version"));
            
            // Parse created_at timestamp from ISO 8601 format string to Date
            final String createdAtStr = metadataMap.get("created_at");
            if (createdAtStr != null && !createdAtStr.isEmpty()) {
                try {
                    // Parse ISO 8601 date-time format (e.g., "2026-05-06T13:00:00Z")
                    final SimpleDateFormat iso8601Format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
                    iso8601Format.setTimeZone(TimeZone.getTimeZone("UTC"));
                    final Date createdAt = iso8601Format.parse(createdAtStr);
                    metadata.setCreatedAt(createdAt);
                } catch (Exception e) {
                    // If parsing fails, skip setting created_at (field remains null)
                }
            }
            
            setMetadata(metadata);
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

        // Add authentication-specific connection properties based on the authentication type
        final AuthenticationType authType = mapping.getAuthenticationTypeEnum();
        switch (authType) {
            case API_KEY:
                // API Key authentication
                properties.addConnectionItem(
                        new CustomDatasourceTypeProperty()
                                .name("api_key")
                                .label("API Key")
                                .description("The API key for authentication")
                                .type(TypeEnum.STRING)
                                .required(true)
                                .masked(true)
                                .group("credentials"));
                break;
            
            case OAUTH2:
                // OAuth 2.0 Bearer Token authentication
                properties.addConnectionItem(
                        new CustomDatasourceTypeProperty()
                                .name("bearer_token")
                                .label("Bearer Token")
                                .description("The OAuth 2.0 bearer token for authentication")
                                .type(TypeEnum.STRING)
                                .required(true)
                                .masked(true)
                                .group("credentials"));
                break;
            
            case BASIC:
                // Basic authentication (username + password)
                properties.addConnectionItem(
                        new CustomDatasourceTypeProperty()
                                .name("username")
                                .label("Username")
                                .description("The username for basic authentication")
                                .type(TypeEnum.STRING)
                                .required(true)
                                .group("credentials"));
                properties.addConnectionItem(
                        new CustomDatasourceTypeProperty()
                                .name("password")
                                .label("Password")
                                .description("The password for basic authentication")
                                .type(TypeEnum.STRING)
                                .required(true)
                                .masked(true)
                                .group("credentials"));
                break;
            
            case NONE:
                // No authentication properties needed
                break;
            
            default:
                // Should never happen if AuthenticationType enum is complete
                break;
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
