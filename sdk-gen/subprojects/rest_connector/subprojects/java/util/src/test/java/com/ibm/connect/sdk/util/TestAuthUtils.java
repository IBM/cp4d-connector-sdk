/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.util;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * Test authentication utilities.
 */
public class TestAuthUtils
{

    /**
     * Test missing authentication token.
     */
    @Test
    public void testMissingAuthToken()
    {
        try {
            AuthUtils.validateAuthToken(null, null);
            fail("Exception expected");
        }
        catch (Exception e) {
            assertTrue(e.getMessage().contains("Missing authentication token"));
        }
    }

    /**
     * Test missing token prefix.
     */
    @Test
    public void testMissingTokenPrefix()
    {
        try {
            AuthUtils.validateAuthToken("invalid", null);
            fail("Exception expected");
        }
        catch (Exception e) {
            assertTrue(e.getMessage().contains("Invalid authentication token"));
        }
    }

    /**
     * Test invalid authentication token.
     */
    @Test
    public void testInvalidAuthenticationToken()
    {
        try {
            AuthUtils.validateAuthToken("Bearer ", null);
            fail("Exception expected");
        }
        catch (Exception e) {
            assertTrue(e.getMessage().contains("Invalid authentication token"));
        }
    }

}
