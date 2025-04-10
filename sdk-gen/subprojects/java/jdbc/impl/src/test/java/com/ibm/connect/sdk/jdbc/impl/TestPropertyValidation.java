/* *************************************************** */

/* (C) Copyright IBM Corp. 2025                        */

/* *************************************************** */
package com.ibm.connect.sdk.jdbc.impl;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;

/**
 * Tests property validation for a connector.
 */
public class TestPropertyValidation
{
    /**
     * Test connection properties negative.
     * @throws Exception 
     */
    @Test(expected = UnsupportedOperationException.class)
    public void testConnectionPropertiesNegative() throws Exception
    {
        final String typeName = "TODO";
        final ConnectionProperties properties = new ConnectionProperties();
        // Setup connection properties
        $_CONNNAMEPREFIX_$ConnectorFactory.getInstance().createConnector(typeName, properties);
    }

    /**
     * Test connection properties.
     * @throws Exception 
     */
    @Test
    public void testConnectionProperties() throws Exception
    {
        final String typeName = "$_CONNNAME_$";
        final ConnectionProperties properties = new ConnectionProperties();
        // Setup connection properties
        properties.put("host", "myhost");
        properties.put("port", "1234");
        properties.put("database", "mydatabase");
        properties.put("username", "myusername");
        properties.put("password", "mypassword");
        assertNotNull($_CONNNAMEPREFIX_$ConnectorFactory.getInstance().createConnector(typeName, properties));
    }
}
