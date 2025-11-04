/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.localfs;

import com.ibm.connect.sdk.api.Connector;
import com.ibm.connect.sdk.api.PooledConnectorFactory;
import com.ibm.connect.sdk.file.FileMsgs;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightDatasourceTypes;

/**
 * A factory for creating local file system connectors.
 */
public class LocalFSConnectorFactory extends PooledConnectorFactory
{
    /**
     * A connector factory instance.
     */
    public static final LocalFSConnectorFactory INSTANCE = new LocalFSConnectorFactory();

    /**
     * {@inheritDoc}
     */
    @Override
    public CustomFlightDatasourceTypes getDatasourceTypes()
    {
        // Return localized datasource types.
        return new CustomFlightDatasourceTypes().addDatasourceTypesItem(new LocalFSDatasourceType());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Connector<?, ?> createNewConnector(String datasourceTypeName, ConnectionProperties properties)
    {
        if (LocalFSDatasourceType.INSTANCE.getName().equals(datasourceTypeName)) {
            return new LocalFSConnector(properties);
        }
        throw new UnsupportedOperationException(FileMsgs.DATASOURCE_TYPE_NOT_SUPPORTED.format(datasourceTypeName));
    }
}
