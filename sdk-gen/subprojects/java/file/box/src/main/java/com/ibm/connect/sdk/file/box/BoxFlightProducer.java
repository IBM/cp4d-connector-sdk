/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2026                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.box;

import com.ibm.connect.sdk.api.ConnectorFactory;
import com.ibm.connect.sdk.api.ConnectorFlightProducer;

/**
 * A Flight producer for Box.
 */
public class BoxFlightProducer extends ConnectorFlightProducer
{
    /**
     * {@inheritDoc}
     */
    @Override
    protected ConnectorFactory getConnectorFactory()
    {
        return BoxConnectorFactory.INSTANCE;
    }
}
