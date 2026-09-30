/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mockStatic;

import org.junit.Test;
import org.mockito.MockedStatic;

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

    /**
     * Test that isStandalone() returns false by default (no env var set).
     * Relies on the real implementation: ENVIRONMENT_NAME is not set in the test JVM.
     */
    @Test
    public void testIsStandaloneDefaultFalse()
    {
        assertFalse(AuthUtils.isStandalone());
    }

    /**
     * Test that validateAuthToken accepts the "standalone" token in standalone mode.
     */
    @Test
    public void testValidateAuthTokenAcceptsStandaloneInStandaloneMode() throws Exception
    {
        try (MockedStatic<AuthUtils> mocked = mockStatic(AuthUtils.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            mocked.when(AuthUtils::isStandalone).thenReturn(true);
            final String subject = AuthUtils.validateAuthToken(AuthUtils.STANDALONE_TOKEN, null);
            assertEquals(AuthUtils.STANDALONE_TOKEN, subject);
        }
    }

    /**
     * Test that validateAuthToken rejects the "standalone" token when not in standalone mode.
     */
    @Test
    public void testValidateAuthTokenRejectsStandaloneOutsideStandaloneMode()
    {
        try (MockedStatic<AuthUtils> mocked = mockStatic(AuthUtils.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            mocked.when(AuthUtils::isStandalone).thenReturn(false);
            AuthUtils.validateAuthToken(AuthUtils.STANDALONE_TOKEN, null);
            fail("Exception expected");
        }
        catch (Exception e) {
            assertTrue(e.getMessage().contains("Invalid authentication token"));
        }
    }

}
