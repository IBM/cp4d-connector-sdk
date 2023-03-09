/* *************************************************** */

/* (C) Copyright IBM Corp. 2022                        */

/* *************************************************** */
package com.ibm.wdp.connect.sdk.flight;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.arrow.flight.FlightGrpcUtils;
import org.apache.arrow.flight.FlightProducer;
import org.apache.arrow.flight.auth.ServerAuthInterceptor;
import org.apache.arrow.memory.RootAllocator;

import com.ibm.connect.sdk.util.ServerTokenAuthHandler;

import io.grpc.BindableService;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;

/**
 * Bindable gRPC flight service.
 */
public class Service implements AutoCloseable, BindableService
{
    private static final long SHUTDOWN_WAIT_SECONDS = 5;

    private final ExecutorService executor;
    private final BindableService flightService;
    private final List<ServerInterceptor> interceptors;

    /**
     * Service constructor.
     */
    public Service()
    {
        this.executor = Executors.newCachedThreadPool();
        this.flightService
                = FlightGrpcUtils.createFlightService(getRootAllocator(), getProducer(), ServerTokenAuthHandler.getInstance(), executor);
        this.interceptors = Arrays.asList(new ServerAuthInterceptor(ServerTokenAuthHandler.getInstance()));
    }

    private static class Holder
    {
        static final RootAllocator ALLOCATOR_INSTANCE = new RootAllocator();
        static final FlightProducer PRODUCER_INSTANCE = new DelegatingFlightProducer();
    }

    /**
     * @return the global RootAllocator.
     */
    public static RootAllocator getRootAllocator()
    {
        return Holder.ALLOCATOR_INSTANCE;
    }

    /**
     * @return the global Producer.
     */
    public static FlightProducer getProducer()
    {
        return Holder.PRODUCER_INSTANCE;
    }

    /**
     * @return the bindable gRPC service.
     */
    @Override
    public ServerServiceDefinition bindService()
    {
        return ServerInterceptors.interceptForward(flightService, interceptors);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close()
    {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                executor.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS);
            }
        }
        catch (final InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
