/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.api;

import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightDatasourceTypes;

/**
 * A factory for creating connectors.
 */
public interface ConnectorFactory
{

    /**
     * Returns the data source types supported by this factory.
     *
     * @return the data source types supported by this factory
     */
    CustomFlightDatasourceTypes getDatasourceTypes();

    /**
     * Creates a connector for the given data source type.
     *
     * @param datasourceTypeName
     *            the name of the data source type
     * @param properties
     *            connection properties
     * @return a connector for the given data source type
     * @throws Exception
     */
    Connector<?, ?> createConnector(String datasourceTypeName, ConnectionProperties properties) throws Exception;

}
