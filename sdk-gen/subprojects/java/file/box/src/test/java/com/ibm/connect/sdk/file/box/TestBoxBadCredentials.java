/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2026                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.box;

import static org.junit.Assert.fail;

import org.junit.Test;

import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;

/**
 * Unit tests verifying that {@link BoxConnector#connect()} surfaces an
 * authentication error immediately when given invalid credentials.
 *
 * <p>{@code connect()} authenticates eagerly (JWT token request) so that
 * ACTION_TEST catches bad credentials at connection time rather than silently
 * succeeding. These tests confirm that behaviour.
 *
 * <p>Tests run unconditionally — they do not require a real Box account.
 * A real HTTP call is made to the Box token endpoint; the call fails fast
 * (with an auth error or connection error) and the important invariant is
 * that {@code connect()} throws rather than returning normally.
 */
public class TestBoxBadCredentials
{

    /**
     * {@link BoxConnector#connect()} must throw when given an obviously invalid
     * RSA private key (the JWT signing step will fail before any network call).
     *
     * @throws Exception
     *             not expected — the connector must throw, not return normally
     */
    @Test
    public void testConnectThrowsForInvalidPrivateKey() throws Exception
    {
        final ConnectionProperties connProps = new ConnectionProperties();
        connProps.put("client_id", "invalid_client_id");
        connProps.put("client_secret", "invalid_client_secret");
        connProps.put("enterprise_id", "invalid_enterprise_id");
        // A syntactically invalid RSA key causes immediate JWT signing failure
        // inside the Box SDK — no network call required.
        connProps.put("private_key", "-----BEGIN RSA PRIVATE KEY-----\ninvalidbase64==\n-----END RSA PRIVATE KEY-----");
        connProps.put("public_key", "invalid_public_key_id");

        try (BoxConnector connector = new BoxConnector(connProps)) {
            assertConnectThrowsTwice(connector);
        }
    }

    /**
     * {@link BoxConnector#connect()} must throw when the private-key PEM is
     * completely absent (empty string). This exercises the fast-fail path inside
     * the Box SDK where a missing/empty key cannot be parsed.
     *
     * @throws Exception
     *             not expected — the connector must throw, not return normally
     */
    @Test
    public void testConnectThrowsForMissingPrivateKey() throws Exception
    {
        final ConnectionProperties connProps = new ConnectionProperties();
        connProps.put("client_id", "fake_client_id");
        connProps.put("client_secret", "fake_client_secret");
        connProps.put("enterprise_id", "fake_enterprise_id");
        connProps.put("private_key", "");
        connProps.put("public_key", "fake_public_key_id");

        try (BoxConnector connector = new BoxConnector(connProps)) {
            assertConnectThrowsTwice(connector);
        }
    }

    /**
     * Calls {@code connect()} twice on the same instance and asserts both calls
     * throw. A failed first call must not leave a non-null API connection that
     * makes the second call skip validation and wrongly succeed.
     */
    private static void assertConnectThrowsTwice(BoxConnector connector)
    {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                connector.connect();
                fail("connect() attempt #" + attempt + " must throw for invalid Box credentials");
            }
            catch (Exception e) {
                // Expected — bad credentials must cause every connect() call to throw.
            }
        }
    }
}
