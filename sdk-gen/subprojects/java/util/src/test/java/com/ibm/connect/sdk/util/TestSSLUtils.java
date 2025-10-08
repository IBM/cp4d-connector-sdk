/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.util;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.security.KeyPair;
import java.security.cert.Certificate;

import org.junit.Test;

/**
 * Test SSL utilities.
 */
public class TestSSLUtils
{

    /**
     * Test missing trust store password.
     */
    @Test
    public void testMissingTruststorePassword()
    {
        try {
            final KeyPair sslKeyPair = AuthUtils.generateKeyPair();
            final Certificate selfSignedCert = SSLUtils.generateSelfSignedCert(sslKeyPair, "localhost");
            final String sslCert = SSLUtils.convertCertToPEM(selfSignedCert);
            SSLUtils.getTrustStoreFile(sslCert, null);
            fail("Exception expected");
        }
        catch (Exception e) {
            assertTrue(e.getMessage().contains("Missing trust store password"));
        }
    }

}
