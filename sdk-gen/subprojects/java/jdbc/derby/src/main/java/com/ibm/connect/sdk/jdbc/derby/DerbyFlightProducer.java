/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.jdbc.derby;

import com.ibm.connect.sdk.api.ConnectorFactory;
import com.ibm.connect.sdk.api.ConnectorFlightProducer;

/**
 * A flight producer for Apache Derby.
 */
public class DerbyFlightProducer extends ConnectorFlightProducer
{
    /**
     * {@inheritDoc}
     */
    @Override
    protected ConnectorFactory getConnectorFactory()
    {
        return DerbyConnectorFactory.INSTANCE;
    }
}
