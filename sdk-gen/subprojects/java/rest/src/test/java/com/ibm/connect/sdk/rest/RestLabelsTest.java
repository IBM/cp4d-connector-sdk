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

public class RestLabelsTest {

    @Test
    public void testRestLabels() {
        Locale.setDefault(Locale.ENGLISH);
        Arrays.stream(RestLabels.values()).iterator().forEachRemaining(restLabel -> {
            try {
                Assert.assertTrue("Message id [" + restLabel.name() + "] should not be empty", restLabel.format() != null && !restLabel.format().isEmpty());
            } catch (Exception e) {
                Assert.fail(e.getMessage() +" Add missing message id into RestLabels bundle.");
            }
        });
    }
}
