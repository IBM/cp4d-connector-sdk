/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.wdp.connect.sdk.connector;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Descriptor for a data asset (table, schema, container, etc.) accessible through a connector.
 *
 * <p>Equivalent in purpose to the SDK's {@code CustomFlightAssetDescriptor} but with no dependency
 * on the SDK model framework. Connector authors use this class to identify the asset they are
 * reading from, writing to, or discovering.
 *
 * <p>Instances are immutable once constructed.
 */
public final class AssetDescriptor
{
    private final String id;
    private final String name;
    private final String path;
    private final String datasourceTypeName;
    private final Map<String, Object> properties;
    private final boolean hasChildren;
    private final int batchSize;

    /**
     * Creates an asset descriptor.
     *
     * @param id
     *            the unique identifier for this asset (may be null for newly discovered assets)
     * @param name
     *            the display name of the asset (e.g. table name)
     * @param path
     *            the hierarchical path to this asset (e.g. "schema/table"), may be null
     * @param datasourceTypeName
     *            the datasource type name identifying the connector to use
     * @param properties
     *            additional asset-specific properties (e.g. schema name, connection options); may be null
     * @param hasChildren
     *            true if this asset is a container (schema, catalog) with child assets
     * @param batchSize
     *            the preferred number of rows per Arrow batch; 0 means use connector default
     */
    public AssetDescriptor(String id, String name, String path, String datasourceTypeName,
            Map<String, Object> properties, boolean hasChildren, int batchSize)
    {
        this.id = id;
        this.name = name;
        this.path = path;
        this.datasourceTypeName = datasourceTypeName;
        this.properties = properties != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(properties))
                : Collections.emptyMap();
        this.hasChildren = hasChildren;
        this.batchSize = batchSize;
    }

    /**
     * Returns the unique identifier for this asset.
     *
     * @return the asset id, or null
     */
    public String getId()
    {
        return id;
    }

    /**
     * Returns the display name of the asset.
     *
     * @return the asset name
     */
    public String getName()
    {
        return name;
    }

    /**
     * Returns the hierarchical path to this asset.
     *
     * @return the path (e.g. "schema/table"), or null
     */
    public String getPath()
    {
        return path;
    }

    /**
     * Returns the datasource type name identifying the connector.
     *
     * @return the datasource type name
     */
    public String getDatasourceTypeName()
    {
        return datasourceTypeName;
    }

    /**
     * Returns the additional asset-specific properties.
     *
     * @return an unmodifiable map of properties; never null
     */
    public Map<String, Object> getProperties()
    {
        return properties;
    }

    /**
     * Returns whether this asset is a container with child assets.
     *
     * @return true if this asset has children (schema, catalog)
     */
    public boolean hasChildren()
    {
        return hasChildren;
    }

    /**
     * Returns the preferred number of rows per Arrow batch.
     *
     * @return the batch size, or 0 to use the connector default
     */
    public int getBatchSize()
    {
        return batchSize;
    }

    @Override
    public String toString()
    {
        return "AssetDescriptor{id='" + id + "', name='" + name + "', path='" + path
                + "', datasourceTypeName='" + datasourceTypeName + "', batchSize=" + batchSize + "}";
    }
}

// Made with Bob
