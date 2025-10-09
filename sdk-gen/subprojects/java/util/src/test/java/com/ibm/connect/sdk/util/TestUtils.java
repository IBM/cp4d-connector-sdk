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
 * Test utility methods.
 */
public class TestUtils
{

    /**
     * Test invalid byte limit.
     */
    @Test
    public void testInvalidByteLimit()
    {
        try {
            Utils.parseByteLimit("invalid");
            fail("Exception expected");
        }
        catch (Exception e) {
            assertTrue(e.getMessage().contains("Invalid byte limit"));
        }
    }

}
