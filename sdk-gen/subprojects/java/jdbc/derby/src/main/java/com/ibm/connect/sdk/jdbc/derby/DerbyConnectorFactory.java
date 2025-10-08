/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022, 2025                  */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.jdbc.derby;

import com.ibm.connect.sdk.api.Connector;
import com.ibm.connect.sdk.api.PooledConnectorFactory;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightDatasourceTypes;

/**
 * A factory for creating Apache Derby connectors.
 */
public class DerbyConnectorFactory extends PooledConnectorFactory
{
    /**
     * A connector factory instance.
     */
    public static final DerbyConnectorFactory INSTANCE = new DerbyConnectorFactory();

    /**
     * {@inheritDoc}
     */
    @Override
    public CustomFlightDatasourceTypes getDatasourceTypes()
    {
        // Return localized datasource types.
        return new CustomFlightDatasourceTypes().addDatasourceTypesItem(new DerbyDatasourceType());
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
        throw new UnsupportedOperationException(DerbyMsgs.DATASOURCE_TYPE_NOT_SUPPORTED.format(datasourceTypeName));
    }
}
