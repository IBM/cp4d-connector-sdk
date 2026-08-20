/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2026                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.s3;

import com.ibm.connect.sdk.api.Connector;
import com.ibm.connect.sdk.api.PooledConnectorFactory;
import com.ibm.connect.sdk.file.FileMsgs;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightDatasourceTypes;

/**
 * A factory for creating Amazon S3 connectors.
 */
public class AWSS3ConnectorFactory extends PooledConnectorFactory
{
    /**
     * A connector factory instance.
     */
    public static final AWSS3ConnectorFactory INSTANCE = new AWSS3ConnectorFactory();

    /**
     * {@inheritDoc}
     */
    @Override
    public CustomFlightDatasourceTypes getDatasourceTypes()
    {
        // Return localized datasource types.
        return new CustomFlightDatasourceTypes().addDatasourceTypesItem(new AWSS3DatasourceType());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Connector<?, ?> createNewConnector(String datasourceTypeName, ConnectionProperties properties)
    {
        if (AWSS3DatasourceType.INSTANCE.getName().equals(datasourceTypeName)) {
            return new AWSS3Connector(properties);
        }
        throw new UnsupportedOperationException(FileMsgs.DATASOURCE_TYPE_NOT_SUPPORTED.format(datasourceTypeName));
    }
}
