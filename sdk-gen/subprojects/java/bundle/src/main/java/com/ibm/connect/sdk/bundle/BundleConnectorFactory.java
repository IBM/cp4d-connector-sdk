/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.bundle;

import com.ibm.connect.sdk.api.Connector;
import com.ibm.connect.sdk.api.PooledConnectorFactory;
import com.ibm.connect.sdk.jdbc.derby.DerbyConnector;
import com.ibm.connect.sdk.jdbc.derby.DerbyDatasourceType;
import com.ibm.connect.sdk.jdbc.generic.GenericJdbcConnector;
import com.ibm.connect.sdk.jdbc.generic.GenericJdbcDatasourceType;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightDatasourceTypes;

/**
 * A factory for creating connectors from a bundle of multiple connectors.
 */
public class BundleConnectorFactory extends PooledConnectorFactory
{
    /**
     * A connector factory instance.
     */
    public static final BundleConnectorFactory INSTANCE = new BundleConnectorFactory();

    /**
     * The data source types supported by this factory.
     */
    private static final CustomFlightDatasourceTypes DATASOURCE_TYPES = new CustomFlightDatasourceTypes()
            .addDatasourceTypesItem(DerbyDatasourceType.INSTANCE).addDatasourceTypesItem(GenericJdbcDatasourceType.INSTANCE);

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
        if (DerbyDatasourceType.INSTANCE.getName().equals(datasourceTypeName)) {
            return new DerbyConnector(properties);
        }
        if (GenericJdbcDatasourceType.INSTANCE.getName().equals(datasourceTypeName)) {
            return new GenericJdbcConnector(properties);
        }
        throw new UnsupportedOperationException("Data source type " + datasourceTypeName + " is not supported");
    }
}
