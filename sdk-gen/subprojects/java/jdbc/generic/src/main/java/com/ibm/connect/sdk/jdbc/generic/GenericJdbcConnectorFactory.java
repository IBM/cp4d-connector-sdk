/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.jdbc.generic;

import com.ibm.connect.sdk.api.Connector;
import com.ibm.connect.sdk.api.PooledConnectorFactory;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightDatasourceTypes;

/**
 * A factory for creating generic JDBC connectors.
 */
public class GenericJdbcConnectorFactory extends PooledConnectorFactory
{
    /**
     * A connector factory instance.
     */
    public static final GenericJdbcConnectorFactory INSTANCE = new GenericJdbcConnectorFactory();

    /**
     * The data source types supported by this factory.
     */
    private static final CustomFlightDatasourceTypes DATASOURCE_TYPES
            = new CustomFlightDatasourceTypes().addDatasourceTypesItem(GenericJdbcDatasourceType.INSTANCE);

    /**
     * {@inheritDoc}
     */
    @Override
    public CustomFlightDatasourceTypes getDatasourceTypes()
    {
        return DATASOURCE_TYPES;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Connector<?, ?> createNewConnector(String datasourceTypeName, ConnectionProperties properties)
    {
        if (GenericJdbcDatasourceType.INSTANCE.getName().equals(datasourceTypeName)) {
            return new GenericJdbcConnector(properties);
        }
        throw new UnsupportedOperationException("Data source type " + datasourceTypeName + " is not supported");
    }
}
