/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.test;

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
import org.apache.arrow.flight.FlightServer;
import org.apache.arrow.flight.Location;
import org.apache.arrow.flight.LocationSchemes;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.slf4j.Logger;

import com.ibm.connect.sdk.util.AuthUtils;
import com.ibm.connect.sdk.util.ClientTokenAuthHandler;
import com.ibm.connect.sdk.util.SSLUtils;
import com.ibm.connect.sdk.util.ServerTokenAuthHandler;
import com.ibm.connect.sdk.util.Utils;

/**
 * A Flight server and client for testing.
 */
public class TestFlight implements Closeable
{

    private static final Logger LOGGER = getLogger(TestFlight.class);

    private final BufferAllocator allocator;
    private final BufferAllocator serverAllocator;
    private final BufferAllocator clientAllocator;
    private final URI uri;
    private final String sslCert;
    private final FlightServer server;
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
    public static TestFlight createLocal(int port, boolean useSSL, FlightProducer producer) throws Exception
    {
        return new TestFlight("localhost", port, useSSL, producer);
    }

    /**
     * Creates a client to a remote Flight server for testing.
     *
     * @param uri
     *            Flight server URI
     * @param sslCert
     *            Flight server SSL certificate
     * @param verifyCert
     *            true if SSL certificate should be validated
     * @return the test flight
     * @throws Exception
     */
    public static TestFlight createRemote(String uri, String sslCert, boolean verifyCert) throws Exception
    {
        return new TestFlight(uri, sslCert, verifyCert);
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
    public TestFlight(String host, int port, boolean useSSL, FlightProducer producer) throws Exception
    {
        allocator = new RootAllocator(Long.MAX_VALUE);
        serverAllocator = allocator.newChildAllocator("server", 0, Long.MAX_VALUE);
        clientAllocator = allocator.newChildAllocator("client", 0, Long.MAX_VALUE);
        final Location location = Utils.createLocation(host, port, useSSL);
        uri = location.getUri();
        final KeyPair authKeyPair = AuthUtils.generateKeyPair();
        final List<PublicKey> verificationKeys = AuthUtils.getVerificationKeys();
        verificationKeys.add(authKeyPair.getPublic());
        final FlightServer.Builder serverBuilder = FlightServer.builder(serverAllocator, location, producer)
                .authHandler(new ServerTokenAuthHandler(verificationKeys.toArray(new PublicKey[0])));
        if (useSSL) {
            final KeyPair sslKeyPair = AuthUtils.generateKeyPair();
            final Certificate selfSignedCert = SSLUtils.generateSelfSignedCert(sslKeyPair, host);
            sslCert = SSLUtils.convertCertToPEM(selfSignedCert);
            final String privateKeyPEM = SSLUtils.convertPrivateKeyToPEM(sslKeyPair.getPrivate());
            serverBuilder.useTls(new ByteArrayInputStream(sslCert.getBytes(StandardCharsets.UTF_8)),
                    new ByteArrayInputStream(privateKeyPEM.getBytes(StandardCharsets.UTF_8)));
        } else {
            sslCert = null;
        }
        server = serverBuilder.build();
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
     * @param flightUri
     *            Flight server URI
     * @param sslCertificate
     *            Flight server SSL certificate
     * @param verifyCert
     *            true if SSL certificate should be validated
     * @throws Exception
     */
    public TestFlight(String flightUri, String sslCertificate, boolean verifyCert) throws Exception
    {
        allocator = new RootAllocator(Long.MAX_VALUE);
        serverAllocator = null;
        clientAllocator = allocator.newChildAllocator("client", 0, Long.MAX_VALUE);
        final Location location = new Location(flightUri);
        uri = location.getUri();
        sslCert = sslCertificate;
        server = null;
        final FlightClient.Builder clientBuilder = FlightClient.builder(clientAllocator, location);
        final boolean useSSL = LocationSchemes.GRPC_TLS.equals(uri.getScheme());
        if (useSSL) {
            clientBuilder.useTls();
            if (!verifyCert) {
                clientBuilder.verifyServer(false);
            } else if (sslCert != null) {
                clientBuilder.trustedCertificates(new ByteArrayInputStream(sslCert.getBytes(StandardCharsets.UTF_8)));
            }
        }
        client = clientBuilder.build();
        try (CloudClient cloudClient = new CloudClient()) {
            client.authenticate(new ClientTokenAuthHandler(cloudClient.getAuthHeader()));
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
        try {
            if (server != null) {
                server.close();
            }
        }
        catch (InterruptedException e) {
            LOGGER.error(e.getMessage(), e);
        }
        clientAllocator.close();
        if (serverAllocator != null) {
            serverAllocator.close();
        }
        allocator.close();
    }

}
