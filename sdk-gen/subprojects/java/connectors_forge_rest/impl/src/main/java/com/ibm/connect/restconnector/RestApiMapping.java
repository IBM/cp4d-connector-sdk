/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents the full API mapping parsed from a JSON configuration file.
 * Holds connector metadata, the base URL, and a map of table name to table definition.
 */
public class RestApiMapping
{
    private final String connectorName;
    private final String connectorLabel;
    private final String connectorDescription;
    private final String baseUrl;
    private final AuthenticationType authenticationType;
    private final Map<String, RestTableDefinition> tables;
    private final Map<String, String> origin;

    /**
     * Creates an API mapping.
     *
     * @param connectorName
     *            the connector name (from "$connector_name")
     * @param connectorLabel
     *            the connector label (from "$connector_label")
     * @param connectorDescription
     *            the connector description (from "$connector_description")
     * @param baseUrl
     *            the base URL for all API calls (from "$hostname")
     * @param authenticationType
     *            the authentication type: "none", "api_key", "oauth2", or "basic" (from "$authentication")
     * @param tables
     *            a map of table name to table definition
     * @param origin
     *            origin fields from the "$origin" directive: name and
     *            optionally version
     */
    public RestApiMapping(String connectorName, String connectorLabel, String connectorDescription,
            String baseUrl, AuthenticationType authenticationType, Map<String, RestTableDefinition> tables,
            Map<String, String> origin)
    {
        this.connectorName = connectorName;
        this.connectorLabel = connectorLabel;
        this.connectorDescription = connectorDescription;
        this.baseUrl = baseUrl;
        this.authenticationType = authenticationType != null ? authenticationType : AuthenticationType.NONE;
        this.tables = Collections.unmodifiableMap(new LinkedHashMap<>(tables));
        this.origin = origin != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(origin))
                : Collections.emptyMap();
    }

    /**
     * Returns the connector name.
     *
     * @return the connector name
     */
    public String getConnectorName()
    {
        return connectorName;
    }

    /**
     * Returns the connector label.
     *
     * @return the connector label
     */
    public String getConnectorLabel()
    {
        return connectorLabel;
    }

    /**
     * Returns the connector description.
     *
     * @return the connector description
     */
    public String getConnectorDescription()
    {
        return connectorDescription;
    }

    /**
     * Returns the base URL for all API calls.
     *
     * @return the base URL (e.g. "https://api.spacexdata.com:443")
     */
    public String getBaseUrl()
    {
        return baseUrl;
    }

    /**
     * Returns the authentication type for this API.
     *
     * @return the authentication type: "none", "api_key", "oauth2", or "basic"
     */
    public String getAuthenticationType()
    {
        return authenticationType.getValue();
    }

    /**
     * Returns the authentication type enum for this API.
     *
     * @return the authentication type enum
     */
    public AuthenticationType getAuthenticationTypeEnum()
    {
        return authenticationType;
    }

    /**
     * Returns the map of table name to table definition.
     *
     * @return an unmodifiable map of table definitions keyed by table name
     */
    public Map<String, RestTableDefinition> getTables()
    {
        return tables;
    }

    /**
     * Returns the table definition for the given table name (case-insensitive lookup).
     *
     * @param tableName
     *            the table name to look up
     * @return the table definition, or null if not found
     */
    public RestTableDefinition getTable(String tableName)
    {
        if (tableName == null) {
            return null;
        }
        // Try exact match first
        final RestTableDefinition def = tables.get(tableName);
        if (def != null) {
            return def;
        }
        // Try upper-case match
        return tables.get(tableName.toUpperCase(java.util.Locale.ENGLISH));
    }

    /**
     * Returns the origin map from the "$origin" directive: name and
     * optionally version.
     *
     * @return an unmodifiable map with keys "name" and optionally "version"
     */
    public Map<String, String> getOrigin()
    {
        return origin;
    }

    @Override
    public String toString()
    {
        return "RestApiMapping{connectorName='" + connectorName + "', baseUrl='" + baseUrl
                + "', authenticationType='" + authenticationType.getValue() + "', tables=" + tables.keySet() + "}";
    }
}

// Made with Bob
