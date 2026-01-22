/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.rest;

import static com.ibm.connect.sdk.rest.utils.RestConnectionProperties.PROPERTY_AUTH_PASSWORD;
import static com.ibm.connect.sdk.rest.utils.RestConnectionProperties.PROPERTY_AUTH_TYPE;
import static com.ibm.connect.sdk.rest.utils.RestConnectionProperties.PROPERTY_AUTH_USERNAME;
import static com.ibm.connect.sdk.rest.utils.RestConnectionProperties.PROPERTY_BASE_URL;
import static com.ibm.connect.sdk.rest.utils.RestConnectionProperties.PROPERTY_CUSTOM_REST_CONFIG_YAML;
import static com.ibm.connect.sdk.rest.utils.RestConnectionProperties.PROPERTY_PREDEFINED_REST_CONFIG_IDENTIFIER;
import static org.junit.Assert.fail;

import java.util.Properties;

import org.junit.Assert;
import org.junit.Test;

import com.ibm.connect.sdk.rest.utils.models.RestConfiguration;
import com.ibm.connect.sdk.test.helper.MockConnectionProperties;
import com.ibm.connect.sdk.test.helper.RestTestUtils;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;

public class RestConnectionPropertyTest
{
    /**
     * Test connection properties negative.
     */
    @Test
    public void testConnectionPropertiesNegative()
    {
        final String typeName = "UNKNOWN";
        final ConnectionProperties properties = new ConnectionProperties();
        // Setup connection properties
        try {
            RestConnectorFactory.getInstance().createConnector(typeName, properties);
            fail("Expected UnsupportedOperationException to be thrown");
        } catch (UnsupportedOperationException e) {
            Assert.assertEquals(RestMsgs.DATASOURCE_TYPE_NOT_SUPPORTED.format(typeName), e.getMessage());
        }
    }

    /**
     * Test connection properties.
     */
    @Test
    public void testConnectionProperties()
    {
        final String typeName = "rest";
        final Properties props = new Properties();
        props.put(PROPERTY_BASE_URL, "https://api.github.ibm.com/api/v3");
        props.put(PROPERTY_AUTH_TYPE, "basic");
        props.put(PROPERTY_AUTH_USERNAME, "<Add your Username>");
        props.put(PROPERTY_AUTH_PASSWORD, "<Add your credentials here>");
        props.put(PROPERTY_PREDEFINED_REST_CONFIG_IDENTIFIER, "custom_model");
        props.put(PROPERTY_CUSTOM_REST_CONFIG_YAML, RestTestUtils.readResourceFile("com/ibm/connect/sdk/rest/config/github/github_repos.yml"));
        final ConnectionProperties properties = MockConnectionProperties.getConnectionProperties(props);

        RestConnectorFactory.getInstance().createConnector(typeName, properties);
    }

    /**
     * Test connection properties.
     */
    @Test
    public void testConnectionPropertiesWithPredefinedModel() throws Exception {
        final String typeName = "rest";
        final Properties props = new Properties();
        props.put(PROPERTY_BASE_URL, "https://api.github.ibm.com/api/v3");
        props.put(PROPERTY_AUTH_TYPE, "basic");
        props.put(PROPERTY_AUTH_USERNAME, "<Add your Username>");
        props.put(PROPERTY_AUTH_PASSWORD, "<Add your credentials here>");
        props.put(PROPERTY_PREDEFINED_REST_CONFIG_IDENTIFIER, "github_repo_model");
        final ConnectionProperties properties = MockConnectionProperties.getConnectionProperties(props);

        try(RestConnector connector = RestConnectorFactory.getInstance().createConnector(typeName, properties)) {
            final RestConfiguration restConfiguration = connector.getRestConnectionProperties().getRestConfiguration();
            Assert.assertNotNull(restConfiguration);
            Assert.assertTrue(restConfiguration.getEntityTypes().size() == 2);
            Assert.assertTrue("organization".equals(restConfiguration.getEntityTypes().get(0).getName()) );
            Assert.assertTrue("repository".equals(restConfiguration.getEntityTypes().get(1).getName()) );
        }
    }
}
