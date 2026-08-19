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
    private final AuthConfig authConfig;
    private final String acceptHeader;
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
     * @param authConfig
     *            the authentication configuration parsed from "$authentication"
     * @param acceptHeader
     *            the value for the HTTP {@code Accept} header sent with every request
     *            (from "{@code $accept_header}"); defaults to {@code "application/json"} if
     *            {@code null} or blank
     * @param tables
     *            a map of table name to table definition
     * @param origin
     *            origin fields from the "$origin" directive: name and
     *            optionally version
     */
    public RestApiMapping(String connectorName, String connectorLabel, String connectorDescription,
            String baseUrl, AuthConfig authConfig, String acceptHeader,
            Map<String, RestTableDefinition> tables, Map<String, String> origin)
    {
        this.connectorName = connectorName;
        this.connectorLabel = connectorLabel;
        this.connectorDescription = connectorDescription;
        this.baseUrl = baseUrl;
        this.authConfig = authConfig != null ? authConfig : new AuthConfig();
        this.acceptHeader = (acceptHeader != null && !acceptHeader.isBlank())
                ? acceptHeader : "application/json";
        this.tables = Collections.unmodifiableMap(new LinkedHashMap<>(tables));
        this.origin = origin != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(origin))
                : Collections.emptyMap();
    }

    public String getConnectorName() { return connectorName; }
    public String getConnectorLabel() { return connectorLabel; }
    public String getConnectorDescription() { return connectorDescription; }
    public String getBaseUrl() { return baseUrl; }
    public AuthConfig getAuthConfig() { return authConfig; }
    /** Returns the value for the HTTP {@code Accept} header. */
    public String getAcceptHeader() { return acceptHeader; }
    public Map<String, RestTableDefinition> getTables() { return tables; }

    /**
     * Returns the table definition for the given table name.
     * Lookup order:
     * 1. Exact match (preserves the original case from the JSON DSL key)
     * 2. Case-insensitive linear scan (supports callers that normalise to upper/lower case)
     */
    public RestTableDefinition getTable(String tableName)
    {
        if (tableName == null) { return null; }
        final RestTableDefinition exact = tables.get(tableName);
        if (exact != null) { return exact; }
        for (final Map.Entry<String, RestTableDefinition> entry : tables.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(tableName)) { return entry.getValue(); }
        }
        return null;
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
                + "', authenticationType='" + authConfig.getType().getValue() + "', tables=" + tables.keySet() + "}";
    }
}
