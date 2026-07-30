/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import org.apache.arrow.flight.FlightStream;

import com.ibm.connect.sdk.api.Connector;
import com.ibm.connect.sdk.api.TargetInteraction;
import com.ibm.connect.sdk.util.ModelMapper;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.sdk.connector.RowReader;
import com.ibm.wdp.connect.sdk.connector.SdkOutputInteraction;

/**
 * An interaction with an Arrow asset as an output (write) target.
 *
 * <p>Implements both the legacy {@link TargetInteraction} interface (used by the old connector
 * path) and the new {@link SdkOutputInteraction} interface (used by the Arrow-native path).
 *
 * <p>The REST connector is read-only; all write methods throw {@link UnsupportedOperationException}.
 */
public class RestOutputInteraction implements TargetInteraction<Connector<?, ?>>, SdkOutputInteraction
{
    /**
     * Creates a REST output interaction from a legacy {@link CustomFlightAssetDescriptor}.
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

    // ---- SdkOutputInteraction interface (new path) ----

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

    // ---- TargetInteraction interface (legacy path) ----

    /**
     * {@inheritDoc}
     *
     * @throws UnsupportedOperationException always — write operations are not supported
     */
    @Override
    public CustomFlightAssetDescriptor putSetup() throws Exception
    {
        throw new UnsupportedOperationException("Write operations are not supported by the REST connector.");
    }

    /**
     * {@inheritDoc}
     *
     * @throws UnsupportedOperationException always — write operations are not supported
     */
    @Override
    public void putStream(FlightStream flightStream) throws Exception
    {
        throw new UnsupportedOperationException("Write operations are not supported by the REST connector.");
    }

    /**
     * {@inheritDoc}
     *
     * @throws UnsupportedOperationException always — write operations are not supported
     */
    @Override
    public CustomFlightAssetDescriptor putWrapup() throws Exception
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
