/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.bundle;

import static org.slf4j.LoggerFactory.getLogger;

import java.sql.Connection;
import java.util.UUID;

import org.apache.arrow.flight.FlightClient;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.slf4j.Logger;

import com.google.gson.JsonObject;
import com.ibm.connect.sdk.jdbc.derby.DerbyDatasourceType;
import com.ibm.connect.sdk.test.CloudClient;
import com.ibm.connect.sdk.test.TestConfig;
import com.ibm.connect.sdk.test.jdbc.derby.DerbyCloudTestSuite;
import com.ibm.connect.sdk.util.ModelMapper;

/**
 * Tests a bundled Apache Derby connector via Cloud Pak for Data.
 */
public class TestBundleFlightProducerDerbyCloud extends DerbyCloudTestSuite
{
    private static final Logger LOGGER = getLogger(TestBundleFlightProducerDerbyCloud.class);

    private static final String DATASOURCE_TYPE_NAME = DerbyDatasourceType.DATASOURCE_TYPE_NAME;

    private static final String CONNECTION_NAME_PREFIX = "bundle-derby-";

    private static CloudClient cloudClient;
    private static BundleTestEnvironment testEnvironment;
    private static String connectionId;

    private static boolean isConfiguredForCloud()
    {
        return CloudClient.getAPIHost() != null;
    }

    /**
     * Setup before tests.
     *
     * @throws Exception
     */
    @BeforeClass
    public static void setUpOnce() throws Exception
    {
        if (isConfiguredForCloud()) {
            cloudClient = new CloudClient();
            testEnvironment = BundleTestEnvironment.getInstance();

            // Register the Flight server.
            final String uri = TestConfig.get("bundle.flight.uri", testEnvironment.getURI().toString());
            final String sslCert = TestConfig.get("bundle.flight.ssl_certificate", testEnvironment.getSSLCert());
            final boolean verifyCert = Boolean.parseBoolean(TestConfig.get("bundle.flight.ssl_certificate_validation", "true"));
            cloudClient.registerFlightServer(uri, sslCert, verifyCert);
            connectionId = createConnection();
        }
    }

    /**
     * Cleanup after tests.
     */
    @AfterClass
    public static void tearDownOnce()
    {
        if (isConfiguredForCloud()) {
            try {
                deleteConnection();
            }
            catch (Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
            testEnvironment.release();
            try {
                cloudClient.close();
            }
            catch (Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }

    private static String createConnection() throws Exception
    {
        final String name = CONNECTION_NAME_PREFIX + UUID.randomUUID().toString();
        return cloudClient.createConnection(name, DATASOURCE_TYPE_NAME, createConnectionPropertiesJsonObject());
    }

    private static JsonObject createConnectionPropertiesJsonObject() throws Exception
    {
        return new ModelMapper().toJsonObject(testEnvironment.createDerbyConnectionProperties());
    }

    private static void deleteConnection() throws Exception
    {
        if (connectionId != null) {
            cloudClient.deleteConnection(connectionId);
        }
    }

    @Override
    protected CloudClient getCloudClient()
    {
        return cloudClient;
    }

    @Override
    protected FlightClient getClient()
    {
        return cloudClient.getFlightClient();
    }

    @Override
    protected String getConnectionId()
    {
        return connectionId;
    }

    @Override
    protected Connection getConnection()
    {
        return testEnvironment.getConnection();
    }

    @Override
    protected String getDatasourceTypeName()
    {
        return DATASOURCE_TYPE_NAME;
    }

    @Override
    protected JsonObject createConnectionProperties()
    {
        try {
            return createConnectionPropertiesJsonObject();
        }
        catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

}
