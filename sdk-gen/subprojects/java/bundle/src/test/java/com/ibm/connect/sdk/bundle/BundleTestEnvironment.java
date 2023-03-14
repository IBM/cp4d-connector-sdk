/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.bundle;

import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.sql.Connection;
import java.util.Enumeration;
import java.util.TimeZone;
import java.util.UUID;

import org.apache.arrow.flight.FlightClient;
import org.apache.derby.drda.NetworkServerControl;
import org.slf4j.Logger;

import com.ibm.connect.sdk.test.TestConfig;
import com.ibm.connect.sdk.test.TestFlight;
import com.ibm.connect.sdk.test.jdbc.derby.DerbyUtils;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;

/**
 * A test environment shared by bundle tests.
 */
public class BundleTestEnvironment
{
    private static final Logger LOGGER = getLogger(BundleTestEnvironment.class);

    private static final InetAddress DERBY_HOST = getLocalHost();
    private static final int DERBY_PORT = TestConfig.getPort("bundle.derby.port");
    private static final String DERBY_DATABASE = TestConfig.get("bundle.derby.database_name", "testdb");
    private static final String DERBY_USER = TestConfig.get("bundle.derby.user_name", "testuser");
    private static final String DERBY_PASSWORD = TestConfig.get("bundle.derby.user_pass", UUID.randomUUID().toString());

    private static BundleTestEnvironment instance;
    private static int useCount;

    private TestFlight testFlight;
    private FlightClient client;
    private NetworkServerControl derbyServer;
    private Connection connection;
    private TimeZone defaultTimeZone;

    private static InetAddress getLocalHost()
    {
        try {
            if (!System.getProperty("os.name").contains("Windows")) {
                // Need public IP if using Flight with docker on Linux, getLocalHost doesn't
                // work
                final Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
                while (en.hasMoreElements()) {
                    final NetworkInterface i = en.nextElement();
                    for (final Enumeration<InetAddress> en2 = i.getInetAddresses(); en2.hasMoreElements();) {
                        final InetAddress addr = en2.nextElement();
                        if (!addr.isLinkLocalAddress() && !addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                            return addr;
                        }
                    }
                }
            }

            // No suitable addr found from searching network interfaces, try localhost
            return InetAddress.getLocalHost();
        }
        catch (Exception e) {
            return InetAddress.getLoopbackAddress();
        }
    }

    /**
     * Returns a shared test environment for bundle tests.
     *
     * @return a shared test environment for bundle tests
     * @throws Exception
     */
    public static synchronized BundleTestEnvironment getInstance() throws Exception
    {
        if (instance == null) {
            instance = new BundleTestEnvironment();
        }
        useCount++;
        return instance;
    }

    private static synchronized boolean releaseInstance()
    {
        if (useCount > 0) {
            useCount--;
        }
        if (useCount <= 0) {
            instance = null;
            return true;
        }
        return false;
    }

    /**
     * Constructs a shared test environment for bundle tests.
     *
     * @throws Exception
     */
    private BundleTestEnvironment() throws Exception
    {
        if (Boolean.parseBoolean(TestConfig.get("bundle.flight.createLocal", "true"))) {
            final boolean useSSL = Boolean.parseBoolean(TestConfig.get("bundle.flight.ssl", "true"));
            testFlight = TestFlight.createLocal(TestConfig.getPort("bundle.flight.port"), useSSL, new BundleFlightProducer());
        } else {
            final boolean verifyCert = Boolean.parseBoolean(TestConfig.get("bundle.flight.ssl_certificate_validation", "true"));
            testFlight = TestFlight.createRemote(TestConfig.get("bundle.flight.uri.internal",TestConfig.get("bundle.flight.uri")),
                    TestConfig.get("bundle.flight.ssl_certificate"), verifyCert);
        }
        client = testFlight.getClient();
        derbyServer = DerbyUtils.startServer(DERBY_HOST, DERBY_PORT, DERBY_USER, DERBY_PASSWORD);
        connection = DerbyUtils.createConnection(createDerbyConnectionProperties());
        defaultTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    /**
     * Returns the Flight server URI.
     *
     * @return the Flight server URI
     */
    public URI getURI()
    {
        return testFlight.getURI();
    }

    /**
     * Returns the Flight server SSL certificate or null.
     *
     * @return the Flight server SSL certificate or null
     */
    public String getSSLCert()
    {
        return testFlight.getSSLCert();
    }

    /**
     * Creates connection properties for connecting to Apache Derby.
     *
     * @return connection properties for connecting to Apache Derby
     */
    public ConnectionProperties createDerbyConnectionProperties()
    {
        final ConnectionProperties connectionProperties = new ConnectionProperties();
        connectionProperties.put("host", DERBY_HOST.getHostAddress());
        connectionProperties.put("port", DERBY_PORT);
        connectionProperties.put("database", DERBY_DATABASE);
        connectionProperties.put("username", DERBY_USER);
        connectionProperties.put("password", DERBY_PASSWORD);
        connectionProperties.put("create_database", true);
        return connectionProperties;
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
     * Returns a JDBC connection to Apache Derby.
     *
     * @return a JDBC connection to Apache Derby
     */
    public Connection getConnection()
    {
        return connection;
    }

    /**
     * Releases use of the instance.
     */
    public void release()
    {
        if (releaseInstance()) {
            TimeZone.setDefault(defaultTimeZone);
            try {
                testFlight.close();
            }
            catch (IOException e) {
                LOGGER.error(e.getMessage(), e);
            }
            try {
                connection.close();
            }
            catch (Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
            try {
                derbyServer.shutdown();
            }
            catch (Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }
}
