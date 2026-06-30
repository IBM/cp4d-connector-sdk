/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2026                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.restconnector;

import com.ibm.connect.sdk.api.AbstractSdkConnectorFlightProducer;
import com.ibm.wdp.connect.sdk.connector.SdkConnectorFactory;

/**
 * A Flight producer for REST API connectors.
 *
 * <p>Uses the Arrow-native path via {@link AbstractSdkConnectorFlightProducer} and
 * {@link RestConnectorFactory}.
 */
public class RestFlightProducer extends AbstractSdkConnectorFlightProducer
{

    /**
     * {@inheritDoc}
     */
    @Override
    protected SdkConnectorFactory getSdkConnectorFactory()
    {
        return RestConnectorFactory.getInstance();
    }

}
