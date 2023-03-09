/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.func.rowbased;

import com.ibm.connect.sdk.api.Connector;
import com.ibm.connect.sdk.api.ConnectorFactory;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightDatasourceTypes;

/**
 * A factory for creating test connectors.
 */
public class TestRowBasedConnectorFactory implements ConnectorFactory
{

    /**
     * {@inheritDoc}
     */
    @Override
    public CustomFlightDatasourceTypes getDatasourceTypes()
    {
        return new CustomFlightDatasourceTypes().addDatasourceTypesItem(new TestRowBasedDatasourceType());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Connector<?, ?> createConnector(String datasourceTypeName, ConnectionProperties properties) throws Exception
    {
        return new TestRowBasedConnector(properties);
    }

}
