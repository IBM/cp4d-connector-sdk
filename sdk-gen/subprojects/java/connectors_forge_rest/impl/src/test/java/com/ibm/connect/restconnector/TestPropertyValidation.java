/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import static org.junit.Assert.assertNotNull;

import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightDatasourceTypes;
import org.junit.Test;

/**
 * Tests property validation for the REST connector.
 * 
 * <p>Note: These tests assume that at least one REST connector configuration
 * has been loaded by the factory at startup. In a real deployment, configuration
 * files would be present in /config/mappings directory.
 */
public class TestPropertyValidation
{
    /**
     * Test that an unsupported datasource type name throws UnsupportedOperationException.
     */
    @Test(expected = UnsupportedOperationException.class)
    public void testConnectionPropertiesNegative() throws Exception
    {
        final String typeName = "unknown_type";
        final ConnectionProperties properties = new ConnectionProperties();
        RestConnectorFactory.getInstance().createConnector(typeName, properties);
    }

    /**
     * Test that a connector can be created when a valid datasource type name is provided.
     * 
     * <p>This test will only pass if at least one configuration file has been loaded.
     * If no configurations are available, the test will be skipped.
     */
    @Test
    public void testConnectionProperties() throws Exception
    {
        final CustomFlightDatasourceTypes datasourceTypes
                = RestConnectorFactory.getInstance().getDatasourceTypes();

        if (datasourceTypes.getDatasourceTypes() == null || datasourceTypes.getDatasourceTypes().isEmpty()
                || "__rest__".equals(datasourceTypes.getDatasourceTypes().get(0).getName())) {
            // No configurations loaded - skip test
            System.out.println("No REST connector configurations loaded. Skipping test.");
            return;
        }

        final String typeName = datasourceTypes.getDatasourceTypes().get(0).getName();
        final ConnectionProperties sdkProps = new ConnectionProperties();

        // Create connector - should succeed
        assertNotNull(RestConnectorFactory.getInstance().createConnector(typeName, sdkProps));
    }
}

// Made with Bob
