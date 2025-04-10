/* *************************************************** */

/* (C) Copyright IBM Corp. 2025                        */

/* *************************************************** */
package com.ibm.connect.sdk.arrow.impl;

import static org.slf4j.LoggerFactory.getLogger;

import java.util.List;
import java.util.Properties;

import org.apache.arrow.flight.Ticket;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.types.pojo.Schema;
import org.slf4j.Logger;

import com.ibm.connect.sdk.api.Connector;
import com.ibm.connect.sdk.api.SourceInteraction;
import com.ibm.connect.sdk.api.TicketInfo;
import com.ibm.connect.sdk.util.ModelMapper;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;

/**
 * An interaction with an Arrow asset as a source.
 */
@SuppressWarnings({ "PMD.AvoidDollarSigns", "PMD.ClassNamingConventions" })
public class $_CONNNAMEPREFIX_$SourceInteraction implements SourceInteraction<Connector<?, ?>>
{
    private static final Logger LOGGER = getLogger($_CONNNAMEPREFIX_$SourceInteraction.class);

    private final ModelMapper modelMapper = new ModelMapper();
    private final Properties interactionProperties;

    /**
     * Creates an Arrow source interaction.
     *
     * @param connector
     *            the connector managing the connection to the data source
     * @param asset
     *            the asset from which to read
     * @param ticket
     *            a Flight ticket to read a partition or null to get tickets
     * @throws Exception
     */
    public $_CONNNAMEPREFIX_$SourceInteraction($_CONNNAMEPREFIX_$Connector connector, CustomFlightAssetDescriptor asset, Ticket ticket) throws Exception
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
        final TicketInfo ticketInfo = (ticket != null) ? modelMapper.fromBytes(ticket.getBytes(), TicketInfo.class) : null;
        if (ticketInfo != null) {
            LOGGER.info("Ticket info: " + ticketInfo.toString());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Schema getSchema() throws Exception
    {
        // TODO Return the asset schema
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Ticket> getTickets() throws Exception
    {
        // TODO Return tickets for reading data partitions
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void beginStream(BufferAllocator allocator) throws Exception
    {
        // TODO Start a stream to read data
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasNextBatch() throws Exception
    {
        // TODO Return true if another Arrow batch is available
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VectorSchemaRoot nextBatch() throws Exception
    {
        // TODO Return the next batch of Arrow vectors
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
