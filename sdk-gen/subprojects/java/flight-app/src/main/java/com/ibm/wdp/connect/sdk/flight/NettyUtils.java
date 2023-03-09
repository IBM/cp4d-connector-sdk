/* *************************************************** */

/* (C) Copyright IBM Corp. 2022                        */

/* *************************************************** */
package com.ibm.wdp.connect.sdk.flight;

import java.util.concurrent.Executor;

import javax.net.ssl.SSLContext;

import io.grpc.ManagedChannel;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.NettyServerBuilder;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolConfig.Protocol;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.IdentityCipherSuiteFilter;
import io.netty.handler.ssl.JdkSslContext;
import io.netty.handler.ssl.SslContext;

class NettyUtils
{
    private static final ApplicationProtocolConfig APN = new ApplicationProtocolConfig(Protocol.ALPN, SelectorFailureBehavior.NO_ADVERTISE,
            SelectedListenerFailureBehavior.ACCEPT, ApplicationProtocolNames.HTTP_2);

    private NettyUtils()
    {
        // prevent instantiation
    }

    static ManagedChannel toClientChannel(String host, int port, SSLContext sslContext)
    {
        final NettyChannelBuilder builder
                = NettyChannelBuilder.forAddress(host, port).maxInboundMessageSize(Integer.MAX_VALUE).maxTraceEvents(0);
        if (sslContext == null) {
            builder.usePlaintext();
        } else {
            builder.sslContext(toSslContext(sslContext, true)).useTransportSecurity();
        }
        return builder.build();
    }

    static NettyServerBuilder toServerBuilder(int port, Executor executor, SSLContext sslContext)
    {
        final NettyServerBuilder builder = NettyServerBuilder.forPort(port).executor(executor).maxInboundMessageSize(Integer.MAX_VALUE);
        if (sslContext != null) {
            builder.sslContext(toSslContext(sslContext, false));
        }
        return builder;
    }

    static NettyServerBuilder toServerBuilder(int port, Executor executor, SslContext sslContext)
    {
        final NettyServerBuilder builder = NettyServerBuilder.forPort(port).executor(executor).maxInboundMessageSize(Integer.MAX_VALUE);
        if (sslContext != null) {
            builder.sslContext(sslContext);
        }
        return builder;
    }

    private static SslContext toSslContext(SSLContext sslContext, boolean isClient)
    {
        return new JdkSslContext(sslContext, isClient, null, IdentityCipherSuiteFilter.INSTANCE, APN, ClientAuth.OPTIONAL, null, false);
    }
}
