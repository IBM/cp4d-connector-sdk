/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.wdp.connect.sdk.flight;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.arrow.flight.auth.AuthConstants;
import org.apache.arrow.flight.grpc.MetadataAdapter;
import org.apache.arrow.flight.grpc.ServerInterceptorAdapter;
import org.apache.http.HttpHeaders;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;

/**
 * A server header adapter between ServerHeaderMiddleware and a gRPC
 * interceptor.
 */
public class ServerHeaderInterceptor extends ServerInterceptorAdapter
{
    private static final String ACCEPT_LANGUAGE_HEADER_NAME = HttpHeaders.ACCEPT_LANGUAGE.toLowerCase(Locale.ENGLISH);
    private static final AtomicReference<String> ACCEPT_LANGUAGE_HEADER_VALUE = new AtomicReference<>();

    /**
     * Constructs a server header adapter.
     *
     * @param factories
     *            a list of middleware keys and factories
     */
    public ServerHeaderInterceptor(List<ServerInterceptorAdapter.KeyFactory<?>> factories)
    {
        super(factories);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <ReqT, RespT> Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next)
    {
        // The default ServerInterceptorAdapter isn't working when used with
        // BindableService. It is not forwarding the Accept-Language header from the
        // handshake to the Flight method call headers. To get around this, we are
        // overriding the interceptCall, caching the Accept-Language header from the
        // handshake, and inserting it into the method call headers. This is only an
        // issue for the DelegateServer deployed in Liberty. For standalone Flight
        // services built by FlightServer.Builder, they don't have this problem.
        if (call.getMethodDescriptor().getFullMethodName().equals(AuthConstants.HANDSHAKE_DESCRIPTOR_NAME)) {
            final MetadataAdapter headerAdapter = new MetadataAdapter(headers);
            ACCEPT_LANGUAGE_HEADER_VALUE.set(headerAdapter.get(ACCEPT_LANGUAGE_HEADER_NAME));
            return super.interceptCall(call, headers, next);
        }
        if (ACCEPT_LANGUAGE_HEADER_VALUE.get() != null) {
            final MetadataAdapter headerAdapter = new MetadataAdapter(headers);
            headerAdapter.insert(ACCEPT_LANGUAGE_HEADER_NAME, ACCEPT_LANGUAGE_HEADER_VALUE.get());
        }
        return super.interceptCall(call, headers, next);
    }

}
