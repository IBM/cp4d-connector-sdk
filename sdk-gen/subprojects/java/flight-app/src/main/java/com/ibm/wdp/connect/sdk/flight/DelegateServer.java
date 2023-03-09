/* *************************************************** */

/* (C) Copyright IBM Corp. 2022                        */

/* *************************************************** */

package com.ibm.wdp.connect.sdk.flight;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import org.apache.arrow.flight.FlightGrpcUtils;
import org.apache.arrow.flight.FlightProducer;
import org.apache.arrow.flight.auth.ServerAuthHandler;
import org.apache.arrow.flight.auth.ServerAuthInterceptor;
import org.apache.arrow.flight.grpc.ServerInterceptorAdapter;
import org.apache.arrow.flight.grpc.ServerInterceptorAdapter.KeyFactory;
import org.apache.arrow.memory.BufferAllocator;

import io.grpc.BindableService;
import io.grpc.ServerInterceptors;
import io.grpc.netty.NettyServerBuilder;
import io.netty.handler.ssl.SslContext;

/**
 * Abstract flight server.
 */
public class DelegateServer implements AutoCloseable
{
    private static final long SHUTDOWN_WAIT_SECONDS = 5;

    private final io.grpc.Server delegate;
    private final ExecutorService executor;

    public DelegateServer(BufferAllocator allocator, int port, SSLContext sslContext, ServerAuthHandler authHandler,
            FlightProducer producer, List<KeyFactory<?>> middleware)
    {
        this.executor = Executors.newCachedThreadPool();

        final NettyServerBuilder builder = NettyUtils.toServerBuilder(port, executor, sslContext);
        // Add Flight service to builder
        final BindableService flightService = FlightGrpcUtils.createFlightService(allocator, producer, authHandler, executor);
        builder.addService(ServerInterceptors.intercept(flightService, new ServerAuthInterceptor(authHandler)));

        if (middleware != null) {
            // Add Flight middleware to builder
            builder.intercept(new ServerInterceptorAdapter(middleware));
        }
        this.delegate = builder.build();
    }

    public DelegateServer(BufferAllocator allocator, int port, SslContext sslContext, ServerAuthHandler authHandler,
            FlightProducer producer, List<KeyFactory<?>> middleware)
    {
        this.executor = Executors.newCachedThreadPool();

        final NettyServerBuilder builder = NettyUtils.toServerBuilder(port, executor, sslContext);
        // Add Flight service to builder
        final BindableService flightService = FlightGrpcUtils.createFlightService(allocator, producer, authHandler, executor);
        builder.addService(ServerInterceptors.intercept(flightService, new ServerAuthInterceptor(authHandler)));

        if (middleware != null) {
            // Add Flight middleware to builder
            builder.intercept(new ServerInterceptorAdapter(middleware));
        }
        this.delegate = builder.build();
    }

    /**
     * Bind and start the server.
     *
     * @throws IOException
     */
    public void start() throws IOException
    {
        delegate.start();
    }

    /**
     * Waits for the server to become terminated, giving up if the timeout is
     * reached.
     *
     * @param timeout
     * @param unit
     * @return whether the server is terminated
     * @throws InterruptedException
     */
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException
    {
        return delegate.awaitTermination(timeout, unit);
    }

    /**
     * Waits for the server to become terminated.
     *
     * @throws InterruptedException
     */
    public void awaitTermination() throws InterruptedException
    {
        delegate.awaitTermination();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close()
    {
        delegate.shutdown();
        try {
            if (!delegate.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                delegate.shutdownNow();
                delegate.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS);
            }
        }
        catch (final InterruptedException e) {
            delegate.shutdownNow();
            Thread.currentThread().interrupt();
        }

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
