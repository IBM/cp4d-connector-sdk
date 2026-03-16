/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.rest;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Locale;

public class RestMsgsTest {
    @Test
    public void testRestMsgs() {
        Locale.setDefault(Locale.ENGLISH);
        Arrays.stream(RestMsgs.values()).iterator().forEachRemaining(restMsg -> {
            try {
                Assert.assertTrue("Message id [" + restMsg.name() + "] should not be empty", restMsg.format() != null && !restMsg.format().isEmpty());
            } catch (Exception e) {
                Assert.fail(e.getMessage() +" Add missing message id into RestMsgs bundle.");
            }
        });
    }
}
