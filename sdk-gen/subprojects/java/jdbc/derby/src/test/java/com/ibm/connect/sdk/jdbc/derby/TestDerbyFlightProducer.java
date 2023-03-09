/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.jdbc.derby;

import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.net.InetAddress;
import java.sql.Connection;
import java.util.TimeZone;
import java.util.UUID;

import org.apache.arrow.flight.FlightClient;
import org.apache.derby.drda.NetworkServerControl;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.slf4j.Logger;

import com.ibm.connect.sdk.test.TestConfig;
import com.ibm.connect.sdk.test.TestFlight;
import com.ibm.connect.sdk.test.jdbc.derby.DerbyTestSuite;
import com.ibm.connect.sdk.test.jdbc.derby.DerbyUtils;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;

/**
 * Tests a flight producer for Apache Derby.
 */
public class TestDerbyFlightProducer extends DerbyTestSuite
{

    private static final Logger LOGGER = getLogger(TestDerbyFlightProducer.class);

    private static final String DATASOURCE_TYPE_NAME = DerbyDatasourceType.DATASOURCE_TYPE_NAME;

    private static final InetAddress DERBY_HOST = InetAddress.getLoopbackAddress();
    private static final int DERBY_PORT = TestConfig.getPort("jdbc_derby.derby.port");
    private static final String DERBY_DATABASE = TestConfig.get("jdbc_derby.derby.database_name", "testdb");
    private static final String DERBY_USER = TestConfig.get("jdbc_derby.derby.user_name", "testuser");
    private static final String DERBY_PASSWORD = TestConfig.get("jdbc_derby.derby.user_pass", UUID.randomUUID().toString());

    private static TestFlight testFlight;
    private static FlightClient client;
    private static NetworkServerControl derbyServer;
    private static Connection connection;
    private static TimeZone defaultTimeZone;

    /**
     * Setup before tests.
     *
     * @throws Exception
     */
    @BeforeClass
    public static void setUpOnce() throws Exception
    {
        testFlight = TestFlight.createLocal(TestConfig.getPort("jdbc_derby.flight.port"), false, new DerbyFlightProducer());
        client = testFlight.getClient();
        derbyServer = DerbyUtils.startServer(DERBY_HOST, DERBY_PORT, DERBY_USER, DERBY_PASSWORD);
        connection = DerbyUtils.createConnection(createDerbyConnectionProperties());
        defaultTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    private static ConnectionProperties createDerbyConnectionProperties()
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
     * Cleanup after tests.
     *
     * @throws Exception
     */
    @AfterClass
    public static void tearDownOnce()
    {
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

    @Override
    protected FlightClient getClient()
    {
        return client;
    }

    @Override
    protected String getDatasourceTypeName()
    {
        return DATASOURCE_TYPE_NAME;
    }

    @Override
    protected ConnectionProperties createConnectionProperties()
    {
        return createDerbyConnectionProperties();
    }

    @Override
    protected Connection getConnection()
    {
        return connection;
    }
}
