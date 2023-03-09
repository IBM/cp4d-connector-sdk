/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.jdbc.generic;

import com.ibm.connect.sdk.api.ConnectorFactory;
import com.ibm.connect.sdk.api.ConnectorFlightProducer;

/**
 * A flight producer for generic JDBC.
 */
public class GenericJdbcFlightProducer extends ConnectorFlightProducer
{
    /**
     * {@inheritDoc}
     */
    @Override
    protected ConnectorFactory getConnectorFactory()
    {
        return GenericJdbcConnectorFactory.INSTANCE;
    }
}
