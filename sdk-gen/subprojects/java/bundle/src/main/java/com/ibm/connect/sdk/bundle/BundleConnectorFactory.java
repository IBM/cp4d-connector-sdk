/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022, 2025                  */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.bundle;

import com.ibm.connect.sdk.api.Connector;
import com.ibm.connect.sdk.api.PooledConnectorFactory;
import com.ibm.connect.sdk.file.github.GitHubConnector;
import com.ibm.connect.sdk.file.github.GitHubDatasourceType;
import com.ibm.connect.sdk.file.localfs.LocalFSConnector;
import com.ibm.connect.sdk.file.localfs.LocalFSDatasourceType;
import com.ibm.connect.sdk.jdbc.derby.DerbyConnector;
import com.ibm.connect.sdk.jdbc.derby.DerbyDatasourceType;
import com.ibm.connect.sdk.jdbc.generic.GenericJdbcConnector;
import com.ibm.connect.sdk.jdbc.generic.GenericJdbcDatasourceType;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightDatasourceTypes;

/**
 * A factory for creating connectors from a bundle of multiple connectors.
 */
public class BundleConnectorFactory extends PooledConnectorFactory
{
    /**
     * A connector factory instance.
     */
    public static final BundleConnectorFactory INSTANCE = new BundleConnectorFactory();

    /**
     * {@inheritDoc}
     */
    @Override
    public CustomFlightDatasourceTypes getDatasourceTypes()
    {
        // Return localized datasource types.
        return new CustomFlightDatasourceTypes().addDatasourceTypesItem(new DerbyDatasourceType())
                .addDatasourceTypesItem(new GenericJdbcDatasourceType()).addDatasourceTypesItem(new GitHubDatasourceType())
                .addDatasourceTypesItem(new LocalFSDatasourceType());
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
        if (GenericJdbcDatasourceType.INSTANCE.getName().equals(datasourceTypeName)) {
            return new GenericJdbcConnector(properties);
        }
        if (GitHubDatasourceType.INSTANCE.getName().equals(datasourceTypeName)) {
            return new GitHubConnector(properties);
        }
        if (LocalFSDatasourceType.INSTANCE.getName().equals(datasourceTypeName)) {
            return new LocalFSConnector(properties);
        }
        throw new UnsupportedOperationException(BundleMsgs.DATASOURCE_TYPE_NOT_SUPPORTED.format(datasourceTypeName));
    }
}
