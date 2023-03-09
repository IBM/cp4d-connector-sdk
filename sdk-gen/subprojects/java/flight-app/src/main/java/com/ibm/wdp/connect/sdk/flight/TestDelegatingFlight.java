/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022                        */
/*                                                     */
/* *************************************************** */
package com.ibm.wdp.connect.sdk.flight;

import static org.slf4j.LoggerFactory.getLogger;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.util.List;

import org.apache.arrow.flight.FlightClient;
import org.apache.arrow.flight.FlightProducer;
import org.apache.arrow.flight.Location;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.slf4j.Logger;

import com.ibm.connect.sdk.util.AuthUtils;
import com.ibm.connect.sdk.util.ClientTokenAuthHandler;
import com.ibm.connect.sdk.util.SSLUtils;
import com.ibm.connect.sdk.util.ServerTokenAuthHandler;
import com.ibm.connect.sdk.util.Utils;

import io.grpc.netty.GrpcSslContexts;
import io.netty.handler.ssl.SslContext;

/**
 * A Flight server and client for testing.
 */
public class TestDelegatingFlight implements Closeable
{

    private static final Logger LOGGER = getLogger(TestDelegatingFlight.class);

    private final BufferAllocator allocator;
    private final BufferAllocator serverAllocator;
    private final BufferAllocator clientAllocator;
    private final URI uri;
    private final String sslCert;
    private final DelegateServer server;
    private final FlightClient client;

    /**
     * Creates a local Flight server and client for testing.
     *
     * @param port
     *            Port number
     * @param useSSL
     *            true if the server should use SSL
     * @param producer
     *            the Arrow Flight producer
     * @return the test flight
     * @throws Exception
     */
    public static TestDelegatingFlight createLocal(int port, boolean useSSL, FlightProducer producer) throws Exception
    {
        return new TestDelegatingFlight("localhost", port, useSSL, producer);
    }

    /**
     * @param host
     *            Host name
     * @param port
     *            Port number
     * @param useSSL
     *            true if the server should use SSL
     * @param producer
     *            the Arrow Flight producer
     * @throws Exception
     */
    public TestDelegatingFlight(String host, int port, boolean useSSL, FlightProducer producer) throws Exception
    {
        allocator = new RootAllocator(Long.MAX_VALUE);
        serverAllocator = allocator.newChildAllocator("server", 0, Long.MAX_VALUE);
        clientAllocator = allocator.newChildAllocator("client", 0, Long.MAX_VALUE);
        final Location location = Utils.createLocation(host, port, useSSL);
        uri = location.getUri();
        final KeyPair authKeyPair = AuthUtils.generateKeyPair();
        final List<PublicKey> verificationKeys = AuthUtils.getVerificationKeys();
        verificationKeys.add(authKeyPair.getPublic());
        if (useSSL) {
            final KeyPair sslKeyPair = AuthUtils.generateKeyPair();
            final Certificate selfSignedCert = SSLUtils.generateSelfSignedCert(sslKeyPair, host);
            sslCert = SSLUtils.convertCertToPEM(selfSignedCert);
            final String privateKeyPEM = SSLUtils.convertPrivateKeyToPEM(sslKeyPair.getPrivate());
            final SslContext sslContext = GrpcSslContexts.forServer(new ByteArrayInputStream(sslCert.getBytes(StandardCharsets.UTF_8)),
                    new ByteArrayInputStream(privateKeyPEM.getBytes(StandardCharsets.UTF_8))).build();
            server = new DelegateServer(serverAllocator, port, sslContext,
                    new ServerTokenAuthHandler(verificationKeys.toArray(new PublicKey[0])), producer, null);
        } else {
            sslCert = null;
            server = new DelegateServer(serverAllocator, port, (javax.net.ssl.SSLContext) null,
                    new ServerTokenAuthHandler(verificationKeys.toArray(new PublicKey[0])), producer, null);
        }
        server.start();
        final FlightClient.Builder clientBuilder = FlightClient.builder(clientAllocator, location);
        if (useSSL) {
            clientBuilder.useTls().trustedCertificates(new ByteArrayInputStream(sslCert.getBytes(StandardCharsets.UTF_8)));
        }
        client = clientBuilder.build();
        client.authenticate(new ClientTokenAuthHandler(AuthUtils.getAuthToken(authKeyPair)));
        LOGGER.info("Flight server started at " + uri);
        if (useSSL) {
            LOGGER.info("SSL certificate:\n" + sslCert);
        }
    }

    /**
     * Returns the Flight server URI.
     *
     * @return the Flight server URI
     */
    public URI getURI()
    {
        return uri;
    }

    /**
     * Returns the Flight server SSL certificate or null.
     *
     * @return the Flight server SSL certificate or null
     */
    public String getSSLCert()
    {
        return sslCert;
    }

    /**
     * Returns the Flight client.
     *
     * @return the Flight client
     */
    public FlightClient getClient()
    {
        return client;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException
    {
        try {
            client.close();
        }
        catch (InterruptedException e) {
            LOGGER.error(e.getMessage(), e);
        }
        server.close();
        clientAllocator.close();
        serverAllocator.close();
        allocator.close();
    }

}
