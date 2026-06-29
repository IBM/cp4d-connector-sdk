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
    private final Map<String, String> metadata;

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
     * @param metadata
     *            metadata from "$metadata" section (connector_source, target_service, etc.)
     */
    public RestApiMapping(String connectorName, String connectorLabel, String connectorDescription,
            String baseUrl, AuthenticationType authenticationType, Map<String, RestTableDefinition> tables,
            Map<String, String> metadata)
    {
        this.connectorName = connectorName;
        this.connectorLabel = connectorLabel;
        this.connectorDescription = connectorDescription;
        this.baseUrl = baseUrl;
        this.authenticationType = authenticationType != null ? authenticationType : AuthenticationType.NONE;
        this.tables = Collections.unmodifiableMap(new LinkedHashMap<>(tables));
        this.metadata = metadata != null ? Collections.unmodifiableMap(new LinkedHashMap<>(metadata)) : Collections.emptyMap();
    }

    public String getConnectorName() { return connectorName; }
    public String getConnectorLabel() { return connectorLabel; }
    public String getConnectorDescription() { return connectorDescription; }
    public String getBaseUrl() { return baseUrl; }
    public String getAuthenticationType() { return authenticationType.getValue(); }
    public AuthenticationType getAuthenticationTypeEnum() { return authenticationType; }
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

    public Map<String, String> getMetadata() { return metadata; }

    @Override
    public String toString()
    {
        return "RestApiMapping{connectorName='" + connectorName + "', baseUrl='" + baseUrl
                + "', authenticationType='" + authenticationType.getValue() + "', tables=" + tables.keySet()
                + ", metadata=" + metadata + "}";
    }
}
