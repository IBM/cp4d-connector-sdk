/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.localfs;

import com.ibm.connect.sdk.api.ConnectorFactory;
import com.ibm.connect.sdk.api.ConnectorFlightProducer;

/**
 * A flight producer for the local file system.
 */
public class LocalFSFlightProducer extends ConnectorFlightProducer
{
    /**
     * {@inheritDoc}
     */
    @Override
    protected ConnectorFactory getConnectorFactory()
    {
        return LocalFSConnectorFactory.INSTANCE;
    }
}
