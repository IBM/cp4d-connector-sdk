/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.api;

import java.util.Objects;

import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;

/**
 * A connector pool key.
 */
public class ConnectorPoolKey
{
    private final String datasourceTypeName;
    private final ConnectionProperties properties;

    /**
     * Constructs a connector pool key.
     *
     * @param datasourceTypeName
     *            the name of the data source type
     * @param properties
     *            connection properties
     */
    public ConnectorPoolKey(String datasourceTypeName, ConnectionProperties properties)
    {
        this.datasourceTypeName = datasourceTypeName;
        this.properties = properties;
    }

    /**
     * @return the datasourceTypeName
     */
    public String getDatasourceTypeName()
    {
        return datasourceTypeName;
    }

    /**
     * @return the properties
     */
    public ConnectionProperties getProperties()
    {
        return properties;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ConnectorPoolKey other = (ConnectorPoolKey) o;
        return Objects.equals(this.datasourceTypeName, other.datasourceTypeName) && Objects.equals(this.properties, other.properties);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode()
    {
        return Objects.hash(datasourceTypeName, properties);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder(100);
        sb.append("[hashCode=").append(Integer.toHexString(hashCode()));
        sb.append(", datasourceTypeName=").append(datasourceTypeName);
        sb.append(", properties=").append(properties);
        sb.append(']');
        return sb.toString();
    }

}
