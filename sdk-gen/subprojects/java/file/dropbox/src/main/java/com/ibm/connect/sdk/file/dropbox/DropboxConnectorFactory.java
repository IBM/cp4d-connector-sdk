/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2026                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.dropbox;

import com.ibm.connect.sdk.api.Connector;
import com.ibm.connect.sdk.api.PooledConnectorFactory;
import com.ibm.connect.sdk.file.FileMsgs;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightDatasourceTypes;

/**
 * A factory for creating Dropbox connectors.
 */
public class DropboxConnectorFactory extends PooledConnectorFactory
{
    /**
     * A connector factory instance.
     */
    public static final DropboxConnectorFactory INSTANCE = new DropboxConnectorFactory();

    /**
     * {@inheritDoc}
     */
    @Override
    public CustomFlightDatasourceTypes getDatasourceTypes()
    {
        return new CustomFlightDatasourceTypes().addDatasourceTypesItem(new DropboxDatasourceType());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Connector<?, ?> createNewConnector(String datasourceTypeName, ConnectionProperties properties)
    {
        if (DropboxDatasourceType.INSTANCE.getName().equals(datasourceTypeName)) {
            return new DropboxConnector(properties);
        }
        throw new UnsupportedOperationException(FileMsgs.DATASOURCE_TYPE_NOT_SUPPORTED.format(datasourceTypeName));
    }
}
