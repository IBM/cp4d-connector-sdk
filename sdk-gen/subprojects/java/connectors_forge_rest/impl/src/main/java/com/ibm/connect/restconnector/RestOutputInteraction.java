/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import com.ibm.connect.sdk.util.ModelMapper;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.sdk.connector.RowReader;
import com.ibm.wdp.connect.sdk.connector.SdkOutputInteraction;

/**
 * An interaction with a REST API asset as an output (write) target.
 *
 * <p>Implements {@link SdkOutputInteraction} for the Arrow-native path.
 *
 * <p>The REST connector is read-only; all write methods throw {@link UnsupportedOperationException}.
 */
public class RestOutputInteraction implements SdkOutputInteraction
{
    /**
     * Creates a REST output interaction.
     *
     * @param connector
     *            the connector managing the connection to the data source
     * @param asset
     *            the asset to which to write
     * @throws Exception
     */
    @SuppressWarnings("PMD.UnusedFormalParameter")
    public RestOutputInteraction(RestConnector connector, CustomFlightAssetDescriptor asset) throws Exception
    {
        if (connector == null) {
            throw new IllegalArgumentException(RestMsgs.MISSING_CONNECTOR.format());
        }
        ModelMapper.toProperties(asset.getInteractionProperties()); // validate asset is readable
    }

    /**
     * {@inheritDoc}
     *
     * @throws UnsupportedOperationException always — write operations are not supported
     */
    @Override
    public void setup()
    {
        throw new UnsupportedOperationException("Write operations are not supported by the REST connector.");
    }

    /**
     * {@inheritDoc}
     *
     * @throws UnsupportedOperationException always — write operations are not supported
     */
    @Override
    public void consume(RowReader reader)
    {
        throw new UnsupportedOperationException("Write operations are not supported by the REST connector.");
    }

    /**
     * {@inheritDoc}
     *
     * @throws UnsupportedOperationException always — write operations are not supported
     */
    @Override
    public void wrapup()
    {
        throw new UnsupportedOperationException("Write operations are not supported by the REST connector.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws Exception
    {
        // nothing to close
    }
}
