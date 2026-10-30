/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2026                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.onedrive;

import com.ibm.connect.sdk.api.Connector;
import com.ibm.connect.sdk.api.PooledConnectorFactory;
import com.ibm.connect.sdk.file.FileMsgs;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightDatasourceTypes;

/**
 * A factory for creating OneDrive connectors.
 */
public class OneDriveConnectorFactory extends PooledConnectorFactory
{
    /**
     * A connector factory instance.
     */
    public static final OneDriveConnectorFactory INSTANCE = new OneDriveConnectorFactory();

    /**
     * {@inheritDoc}
     */
    @Override
    public CustomFlightDatasourceTypes getDatasourceTypes()
    {
        return new CustomFlightDatasourceTypes().addDatasourceTypesItem(new OneDriveDatasourceType());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Connector<?, ?> createNewConnector(String datasourceTypeName, ConnectionProperties properties)
    {
        if (OneDriveDatasourceType.INSTANCE.getName().equals(datasourceTypeName)) {
            return new OneDriveConnector(properties);
        }
        throw new UnsupportedOperationException(FileMsgs.DATASOURCE_TYPE_NOT_SUPPORTED.format(datasourceTypeName));
    }
}
