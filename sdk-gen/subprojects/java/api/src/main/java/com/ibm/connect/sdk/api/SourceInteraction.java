/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.api;

import java.util.List;

import org.apache.arrow.flight.Ticket;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.types.pojo.Schema;

/**
 * An interaction with a connector asset as a source.
 *
 * @param <C> a connector class
 */
public interface SourceInteraction<C extends Connector<?, ?>> extends AutoCloseable
{
    /**
     * Returns the asset schema.
     *
     * @return the asset schema
     * @throws Exception
     */
    Schema getSchema() throws Exception;

    /**
     * Returns tickets for reading data partitions.
     *
     * @return tickets for reading data partitions
     * @throws Exception
     */
    List<Ticket> getTickets() throws Exception;

    /**
     * Starts a stream to read data.
     *
     * @param allocator
     *            an allocator for Arrow vectors
     * @throws Exception
     */
    void beginStream(BufferAllocator allocator) throws Exception;

    /**
     * Returns true if another Arrow batch is available.
     *
     * @return true if another Arrow batch is available
     * @throws Exception
     */
    boolean hasNextBatch() throws Exception;

    /**
     * Returns the next batch of Arrow vectors.
     *
     * @return the next batch of Arrow vectors
     * @throws Exception
     */
    VectorSchemaRoot nextBatch() throws Exception;
}
