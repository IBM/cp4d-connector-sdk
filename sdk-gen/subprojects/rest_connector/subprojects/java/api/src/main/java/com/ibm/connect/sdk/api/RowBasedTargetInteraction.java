/* *************************************************** */

/* (C) Copyright IBM Corp. 2022                        */

/* *************************************************** */
package com.ibm.connect.sdk.api;

import static org.slf4j.LoggerFactory.getLogger;

import java.util.Collections;
import java.util.Iterator;
import java.util.stream.Stream;

import org.apache.arrow.flight.FlightStream;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.slf4j.Logger;

import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;

public abstract class RowBasedTargetInteraction<C extends RowBasedConnector<?, ?>> implements TargetInteraction<C>
{
    private static final Logger LOGGER = getLogger(RowBasedTargetInteraction.class);

    private C connector;
    private CustomFlightAssetDescriptor asset;

    public void setConnector(C connector)
    {
        this.connector = connector;
    }

    public C getConnector()
    {
        return connector;
    };

    public CustomFlightAssetDescriptor getAsset()
    {
        return asset;
    }

    public void setAsset(CustomFlightAssetDescriptor asset)
    {
        this.asset = asset;
    }

    public abstract void putRecord(Record record);

    public void putAll(Iterator<Record> records)
    {
        records.forEachRemaining(this::putRecord);
    }

    public void putAll(Stream<Record> records)
    {
        records.forEach(this::putRecord);
    }

    public void fromArrow(Iterator<VectorSchemaRoot> roots, RowBasedConnector<?, ?> connector)
    {
        fromArrow(roots, Integer.MAX_VALUE, connector);
    };

    public void fromArrow(Iterator<VectorSchemaRoot> roots, int commitFrequency, RowBasedConnector<?, ?> connector)
    {
        putAll(ArrowConversions.fromArrow(asset.getFields(), roots, commitFrequency, connector));
    };

    /**
     * {@inheritDoc}
     */
    @Override
    public void putStream(FlightStream flightStream) throws Exception
    {
        try (VectorSchemaRoot root = flightStream.getRoot()) {
            LOGGER.info("Starting to read input stream");
            while (flightStream.next()) {
                LOGGER.info("Stream has more.");
                fromArrow(Collections.singleton(root).iterator(), connector);
            }
        }
        finally {
            LOGGER.info("Finished reading input stream");
        }
    }

}
