/* *************************************************** */

/* (C) Copyright IBM Corp. 2025                        */

/* *************************************************** */
package com.ibm.connect.sdk.arrow.impl;

import com.ibm.connect.sdk.api.Connector;
import com.ibm.connect.sdk.api.PooledConnectorFactory;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightDatasourceTypes;

/**
 * A factory for creating connectors.
 */
@SuppressWarnings({ "PMD.AvoidDollarSigns", "PMD.ClassNamingConventions" })
public class $_CONNNAMEPREFIX_$ConnectorFactory extends PooledConnectorFactory
{
    private static final $_CONNNAMEPREFIX_$ConnectorFactory INSTANCE = new $_CONNNAMEPREFIX_$ConnectorFactory();

    /**
     * A connector factory instance.
     *
     * @return a connector factory instance
     */
    public static $_CONNNAMEPREFIX_$ConnectorFactory getInstance()
    {
        return INSTANCE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Connector<?, ?> createNewConnector(String datasourceTypeName, ConnectionProperties properties)
    {
        if ($_CONNNAMEPREFIX_$DatasourceType.INSTANCE.getName().equals(datasourceTypeName)) {
            return new $_CONNNAMEPREFIX_$Connector(properties);
        }
        throw new UnsupportedOperationException($_CONNNAMEPREFIX_$Msgs.DATASOURCE_TYPE_NOT_SUPPORTED.format(datasourceTypeName));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CustomFlightDatasourceTypes getDatasourceTypes()
    {
        // Return localized datasource types.
        return new CustomFlightDatasourceTypes().addDatasourceTypesItem(new $_CONNNAMEPREFIX_$DatasourceType());
    }
}
