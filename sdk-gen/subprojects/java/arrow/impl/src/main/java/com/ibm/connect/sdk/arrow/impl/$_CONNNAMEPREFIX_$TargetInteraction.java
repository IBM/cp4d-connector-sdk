/* *************************************************** */

/* (C) Copyright IBM Corp. 2025                        */

/* *************************************************** */
package com.ibm.connect.sdk.arrow.impl;

import java.util.Properties;

import org.apache.arrow.flight.FlightStream;

import com.ibm.connect.sdk.api.Connector;
import com.ibm.connect.sdk.api.TargetInteraction;
import com.ibm.connect.sdk.util.ModelMapper;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;

/**
 * An interaction with an Arrow asset as a target.
 */
@SuppressWarnings({ "PMD.AvoidDollarSigns", "PMD.ClassNamingConventions" })
public class $_CONNNAMEPREFIX_$TargetInteraction implements TargetInteraction<Connector<?, ?>>
{
    private final Properties interactionProperties;

    /**
     * Creates an Arrow target interaction.
     *
     * @param connector
     *            the connector managing the connection to the data source
     * @param asset
     *            the asset to which to write
     * @throws Exception
     */
    public $_CONNNAMEPREFIX_$TargetInteraction($_CONNNAMEPREFIX_$Connector connector, CustomFlightAssetDescriptor asset) throws Exception
    {
        if (connector == null) {
            throw new IllegalArgumentException("Missing connector");
        }
        interactionProperties = ModelMapper.toProperties(asset.getInteractionProperties());
        if (interactionProperties.getProperty("schema_name") == null) {
            throw new IllegalArgumentException("Missing schema_name");
        }
        if (interactionProperties.getProperty("table_name") == null) {
            throw new IllegalArgumentException("Missing table_name");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CustomFlightAssetDescriptor putSetup() throws Exception
    {
        // TODO Perform any setup required before writing
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void putStream(FlightStream flightStream) throws Exception
    {
        // TODO Write a stream to the data asset
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CustomFlightAssetDescriptor putWrapup() throws Exception
    {
        // TODO Perform any wrap-up required after writing
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws Exception
    {
        // TODO Close any open resources
    }
}
