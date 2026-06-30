/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.wdp.connect.sdk.connector;

/**
 * Criteria used to scope an asset discovery request.
 *
 * <p>Passed to {@link SdkConnector#getDiscoveryInteraction(DiscoveryCriteria)} and then to
 * {@link SdkDiscoveryInteraction#discoverAssets(DiscoveryCriteria)} to restrict the set of
 * assets returned (e.g. browse only a specific schema, or browse assets of a specific type).
 *
 * <p>Instances are immutable once constructed.
 */
public final class DiscoveryCriteria
{
    private final String path;
    private final String datasourceTypeName;
    private final ConnectionProperties connectionProperties;

    /**
     * Creates discovery criteria.
     *
     * @param path
     *            optional hierarchical path to browse (e.g. "my_schema"); null means root
     * @param datasourceTypeName
     *            the datasource type name identifying the connector
     * @param connectionProperties
     *            the connection properties to use when connecting to the source
     */
    public DiscoveryCriteria(String path, String datasourceTypeName, ConnectionProperties connectionProperties)
    {
        this.path = path;
        this.datasourceTypeName = datasourceTypeName;
        this.connectionProperties = connectionProperties;
    }

    /**
     * Returns the browse path.
     *
     * @return the path, or null for root-level discovery
     */
    public String getPath()
    {
        return path;
    }

    /**
     * Returns the datasource type name.
     *
     * @return the datasource type name
     */
    public String getDatasourceTypeName()
    {
        return datasourceTypeName;
    }

    /**
     * Returns the connection properties.
     *
     * @return the connection properties
     */
    public ConnectionProperties getConnectionProperties()
    {
        return connectionProperties;
    }

    @Override
    public String toString()
    {
        return "DiscoveryCriteria{path='" + path + "', datasourceTypeName='" + datasourceTypeName + "'}";
    }
}

// Made with Bob
