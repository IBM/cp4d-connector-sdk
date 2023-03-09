/* *************************************************** */

/* (C) Copyright IBM Corp. 2022                        */

/* *************************************************** */
package com.ibm.connect.sdk.basic.impl;

import java.util.Arrays;

import com.ibm.connect.sdk.api.ConnectorFactory;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightDatasourceTypes;

@SuppressWarnings({ "PMD.AvoidDollarSigns", "PMD.ClassNamingConventions" })
public class $_CONNNAMEPREFIX_$ConnectorFactory implements ConnectorFactory
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
     * Creates a connector for the given data source type.
     *
     * @param datasourceTypeName
     *            the name of the data source type
     * @param properties
     *            connection properties
     * @return a connector for the given data source type
     */
    @Override
    public $_CONNNAMEPREFIX_$Connector createConnector(String datasourceTypeName, ConnectionProperties properties)
    {
        if ("$_CONNNAME_$".equals(datasourceTypeName)) {
            return new $_CONNNAMEPREFIX_$Connector(properties);
        }
        throw new UnsupportedOperationException(datasourceTypeName + " is not supported!");
    }

    @Override
    public CustomFlightDatasourceTypes getDatasourceTypes()
    {
        return new CustomFlightDatasourceTypes().datasourceTypes(Arrays.asList(new $_CONNNAMEPREFIX_$DatasourceType()));
    }
}
