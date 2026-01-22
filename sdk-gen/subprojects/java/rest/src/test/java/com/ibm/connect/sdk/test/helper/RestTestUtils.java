/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.test.helper;

import static org.junit.Assert.fail;

import java.io.IOException;

import com.ibm.connect.sdk.rest.utils.RestUtils;

public class RestTestUtils {
    public static String readResourceFile(String resourceFilePath) {
        try {
            return RestUtils.readResourceFile(resourceFilePath);
        } catch (IOException e) {
            fail("Unable to read the resource file");
        }
        return null;
    }
}
