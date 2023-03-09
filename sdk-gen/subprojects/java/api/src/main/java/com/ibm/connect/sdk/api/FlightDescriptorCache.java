/* *************************************************** */

/* (C) Copyright IBM Corp. 2022                        */

/* *************************************************** */
package com.ibm.connect.sdk.api;

import java.util.concurrent.TimeUnit;

import org.apache.arrow.flight.FlightDescriptor;
import org.apache.arrow.flight.Ticket;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.ibm.connect.sdk.util.ModelMapper;

/**
 * Flight descriptor cache.
 */
public class FlightDescriptorCache
{
    private final ModelMapper modelMapper = new ModelMapper();
    private final Cache<String, FlightDescriptor> descriptorCache;

    /**
     * Constructs a Flight descriptor cache.
     */
    public FlightDescriptorCache()
    {
        descriptorCache = CacheBuilder.newBuilder().maximumSize(100).expireAfterWrite(10, TimeUnit.MINUTES).build();
    }

    /**
     * Adds a Flight descriptor to the cache.
     *
     * @param ticket
     *            a Flight ticket with a request id used as the cache key
     * @param descriptor
     *            the Flight descriptor to cache
     * @throws Exception
     */
    public void put(Ticket ticket, FlightDescriptor descriptor) throws Exception
    {
        final TicketInfo ticketInfo = getTicketInfo(ticket);
        descriptorCache.put(ticketInfo.getRequestId(), descriptor);
    }

    /**
     * Retrieves a Flight descriptor from the cache.
     *
     * @param ticket
     *            a Flight ticket
     * @return a Flight descriptor or null if there is no cached descriptor for the
     *         ticket
     * @throws Exception
     */
    public FlightDescriptor get(Ticket ticket) throws Exception
    {
        final TicketInfo ticketInfo = getTicketInfo(ticket);
        return descriptorCache.getIfPresent(ticketInfo.getRequestId());
    }

    private TicketInfo getTicketInfo(Ticket ticket) throws Exception
    {
        return modelMapper.fromBytes(ticket.getBytes(), TicketInfo.class);
    }
}
