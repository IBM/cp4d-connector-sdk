/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.noop;

import org.apache.arrow.flight.Action;
import org.apache.arrow.flight.ActionType;
import org.apache.arrow.flight.CallStatus;
import org.apache.arrow.flight.Criteria;
import org.apache.arrow.flight.FlightDescriptor;
import org.apache.arrow.flight.FlightInfo;
import org.apache.arrow.flight.FlightProducer;
import org.apache.arrow.flight.FlightStream;
import org.apache.arrow.flight.PutResult;
import org.apache.arrow.flight.Result;
import org.apache.arrow.flight.Ticket;

/**
 * A no-op Flight producer with no real implementation.
 */
public class NoOpFlightProducer implements FlightProducer
{

    /**
     * {@inheritDoc}
     */
    @Override
    public void getStream(CallContext context, Ticket ticket, ServerStreamListener listener)
    {
        // TODO Implement this
        listener.error(CallStatus.UNIMPLEMENTED.withDescription("getStream not implemented").toRuntimeException());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void listFlights(CallContext context, Criteria criteria, StreamListener<FlightInfo> listener)
    {
        // TODO Implement this
        listener.onError(CallStatus.UNIMPLEMENTED.withDescription("listFlights not implemented").toRuntimeException());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FlightInfo getFlightInfo(CallContext context, FlightDescriptor descriptor)
    {
        // TODO Implement this
        throw CallStatus.UNIMPLEMENTED.withDescription("getFlightInfo not implemented").toRuntimeException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Runnable acceptPut(CallContext context, FlightStream flightStream, StreamListener<PutResult> ackStream)
    {
        // TODO Implement this
        return () -> {
            ackStream.onError(CallStatus.UNIMPLEMENTED.withDescription("acceptPut not implemented").toRuntimeException());
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void doAction(CallContext context, Action action, StreamListener<Result> listener)
    {
        // TODO Implement this
        listener.onError(CallStatus.UNIMPLEMENTED.withDescription("doAction not implemented").toRuntimeException());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void listActions(CallContext context, StreamListener<ActionType> listener)
    {
        // TODO Implement this
        listener.onError(CallStatus.UNIMPLEMENTED.withDescription("listActions not implemented").toRuntimeException());
    }

}
