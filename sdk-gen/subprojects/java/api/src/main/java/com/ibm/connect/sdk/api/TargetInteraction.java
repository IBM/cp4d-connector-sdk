/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.api;

import org.apache.arrow.flight.FlightStream;

import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;

/**
 * An interaction with a connector asset as a target.
 *
 * @param <C> a connector class
 */
public interface TargetInteraction<C extends Connector<?, ?>> extends AutoCloseable
{
    /**
     * Performs any setup required before writing.
     *
     * @return an updated asset descriptor with a potentially adjusted partition
     *         count
     * @throws Exception
     */
    CustomFlightAssetDescriptor putSetup() throws Exception;

    /**
     * Writes a stream to the data asset.
     *
     * @param flightStream
     *            Flight stream
     * @throws Exception
     */
    void putStream(FlightStream flightStream) throws Exception;

    /**
     * Performs any wrap-up required after writing.
     *
     * @return an updated asset descriptor
     * @throws Exception
     */
    CustomFlightAssetDescriptor putWrapup() throws Exception;
}
