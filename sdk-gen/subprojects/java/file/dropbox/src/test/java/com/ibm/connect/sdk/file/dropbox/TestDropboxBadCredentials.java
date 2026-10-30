/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2026                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.dropbox;

import static org.junit.Assert.fail;

import org.junit.Test;

import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;

/**
 * Unit tests verifying that {@link DropboxConnector#connect()} surfaces an
 * authentication error immediately when given invalid credentials.
 *
 * <p>The Dropbox SDK uses lazy authentication and only validates a token on the
 * first real API call. A lightweight call to {@code users().getCurrentAccount()}
 * was added to {@code connect()} so that ACTION_TEST catches bad credentials at
 * connection time. These tests confirm that behaviour.
 *
 * <p>Tests run unconditionally — they do not require a real Dropbox account.
 * A real HTTP call is made to the Dropbox API; the call fails with an auth error
 * (or a connection error in environments without network), and the important
 * invariant is that {@code connect()} throws rather than returning normally.
 */
public class TestDropboxBadCredentials
{

    /**
     * {@link DropboxConnector#connect()} must throw when given an invalid
     * access token. The Dropbox API will reject the token with a 401 error.
     *
     * @throws Exception
     *             not expected — the connector must throw, not return normally
     */
    @Test
    public void testConnectThrowsForInvalidAccessToken() throws Exception
    {
        final ConnectionProperties connProps = new ConnectionProperties();
        connProps.put("access_token", "sl.invalid_access_token_that_will_be_rejected");

        try (DropboxConnector connector = new DropboxConnector(connProps)) {
            assertConnectThrowsTwice(connector);
        }
    }

    /**
     * {@link DropboxConnector#connect()} must throw when given an invalid
     * refresh token. The Dropbox token-refresh endpoint will reject it.
     *
     * @throws Exception
     *             not expected — the connector must throw, not return normally
     */
    @Test
    public void testConnectThrowsForInvalidRefreshToken() throws Exception
    {
        final ConnectionProperties connProps = new ConnectionProperties();
        connProps.put("refresh_token", "invalid_refresh_token");
        connProps.put("app_key", "invalid_app_key");
        connProps.put("app_secret", "invalid_app_secret");

        try (DropboxConnector connector = new DropboxConnector(connProps)) {
            assertConnectThrowsTwice(connector);
        }
    }

    /**
     * {@link DropboxConnector#connect()} must throw an
     * {@link IllegalArgumentException} when no credentials at all are provided.
     *
     * @throws Exception
     *             not expected — the connector must throw, not return normally
     */
    @Test
    public void testConnectThrowsForMissingCredentials() throws Exception
    {
        final ConnectionProperties connProps = new ConnectionProperties();
        // No access_token, no refresh_token.

        try (DropboxConnector connector = new DropboxConnector(connProps)) {
            assertConnectThrowsTwice(connector);
        }
    }

    /**
     * {@link DropboxConnector#connect()} must throw on every call when given a
     * valid-looking refresh token with a wrong app key and app secret. The first
     * failed call must not leave a poisoned client that makes the second call
     * succeed.
     *
     * @throws Exception
     *             not expected — the connector must throw, not return normally
     */
    @Test
    public void testConnectThrowsTwiceForValidLookingRefreshTokenWithWrongAppCredentials() throws Exception
    {
        final ConnectionProperties connProps = new ConnectionProperties();
        connProps.put("refresh_token", "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789AbCdEfGhIjKlMnOpQrStUvWxYz");
        connProps.put("app_key", "wrongappkey12345");
        connProps.put("app_secret", "wrongappsecret123");

        try (DropboxConnector connector = new DropboxConnector(connProps)) {
            assertConnectThrowsTwice(connector);
        }
    }

    /**
     * Calls {@code connect()} twice on the same instance and asserts both calls
     * throw. A failed first call must not leave a non-null client that makes the
     * second call skip validation and wrongly succeed.
     */
    private static void assertConnectThrowsTwice(DropboxConnector connector)
    {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                connector.connect();
                fail("connect() attempt #" + attempt + " must throw for invalid Dropbox credentials");
            }
            catch (Exception e) {
                // Expected — bad credentials must cause every connect() call to throw.
            }
        }
    }
}
