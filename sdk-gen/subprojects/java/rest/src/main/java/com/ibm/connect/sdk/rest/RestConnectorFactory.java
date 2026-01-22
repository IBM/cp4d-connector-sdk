/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.rest;

import java.util.Arrays;

import com.ibm.connect.sdk.api.ConnectorFactory;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightDatasourceTypes;

@SuppressWarnings({ "PMD.AvoidDollarSigns", "PMD.ClassNamingConventions" })
public class RestConnectorFactory implements ConnectorFactory
{
    private static final RestConnectorFactory INSTANCE = new RestConnectorFactory();

    /**
     * A connector factory instance.
     *
     * @return a connector factory instance
     */
    public static RestConnectorFactory getInstance()
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
    public RestConnector createConnector(String datasourceTypeName, ConnectionProperties properties)
    {
        if ("rest".equals(datasourceTypeName)) {
            return new RestConnector(properties);
        }
        throw new UnsupportedOperationException(RestMsgs.DATASOURCE_TYPE_NOT_SUPPORTED.format(datasourceTypeName));
    }

    @Override
    public CustomFlightDatasourceTypes getDatasourceTypes()
    {
        return new CustomFlightDatasourceTypes().datasourceTypes(Arrays.asList(new RestDatasourceType()));
    }
}
