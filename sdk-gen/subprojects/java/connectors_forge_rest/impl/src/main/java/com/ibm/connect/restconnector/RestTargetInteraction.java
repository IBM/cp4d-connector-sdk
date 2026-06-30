/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import java.util.Properties;

import org.apache.arrow.flight.FlightStream;

import com.ibm.connect.sdk.api.Connector;
import com.ibm.connect.sdk.api.TargetInteraction;
import com.ibm.connect.sdk.util.ModelMapper;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.sdk.connector.AssetDescriptor;
import com.ibm.wdp.connect.sdk.connector.RowReader;
import com.ibm.wdp.connect.sdk.connector.SdkTargetInteraction;

/**
 * An interaction with an Arrow asset as a target.
 *
 * <p>Implements both the legacy {@link TargetInteraction} interface (used by the old connector
 * path) and the new {@link SdkTargetInteraction} interface (used by the Arrow-native path).
 */
public class RestTargetInteraction implements TargetInteraction<Connector<?, ?>>, SdkTargetInteraction
{
    private final Properties interactionProperties;

    /**
     * Creates an Arrow target interaction from a legacy {@link CustomFlightAssetDescriptor}.
     *
     * @param connector
     *            the connector managing the connection to the data source
     * @param asset
     *            the asset to which to write
     * @throws Exception
     */
    public RestTargetInteraction(RestConnector connector, CustomFlightAssetDescriptor asset) throws Exception
    {
        if (connector == null) {
            throw new IllegalArgumentException(RestMsgs.MISSING_CONNECTOR.format());
        }
        interactionProperties = ModelMapper.toProperties(asset.getInteractionProperties());
        validateProperties();
    }

    /**
     * Creates an Arrow target interaction from an SDK {@link AssetDescriptor}.
     *
     * @param connector
     *            the connector managing the connection to the data source
     * @param asset
     *            the SDK asset to which to write
     */
    public RestTargetInteraction(RestConnector connector, AssetDescriptor asset) {
        if (connector == null) {
            throw new IllegalArgumentException(RestMsgs.MISSING_CONNECTOR.format());
        }
        // Build properties from SDK asset's properties map
        interactionProperties = new Properties();
        if (asset.getProperties() != null) {
            for (final java.util.Map.Entry<String, Object> entry : asset.getProperties().entrySet()) {
                if (entry.getValue() != null) {
                    interactionProperties.setProperty(entry.getKey(), entry.getValue().toString());
                }
            }
        }
        validateProperties();
    }

    private void validateProperties()
    {
        if (interactionProperties.getProperty("schema_name") == null) {
            throw new IllegalArgumentException(RestMsgs.MISSING_PROPERTY.format("schema_name"));
        }
        if (interactionProperties.getProperty("table_name") == null) {
            throw new IllegalArgumentException(RestMsgs.MISSING_PROPERTY.format("table_name"));
        }
    }

    // ---- SdkTargetInteraction interface (new path) ----

    /**
     * {@inheritDoc}
     */
    @Override
    public void setup() {
        // TODO Perform any setup required before writing
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void consume(RowReader reader) {
        // TODO Read rows from reader and write to the target
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void wrapup() {
        // TODO Perform any wrap-up required after writing
    }

    // ---- TargetInteraction interface (legacy path) ----

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
