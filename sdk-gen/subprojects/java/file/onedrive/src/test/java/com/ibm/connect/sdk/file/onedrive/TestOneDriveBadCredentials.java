/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2026                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.onedrive;

import static org.junit.Assert.fail;

import org.junit.Test;

import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;

/**
 * Unit tests verifying that {@link OneDriveConnector#connect()} surfaces an
 * authentication error immediately when given invalid credentials.
 *
 * <p>OneDrive authenticates via an OAuth2 ROPC grant; an invalid password or
 * client secret causes the token endpoint to return an error response
 * (HTTP 400/401) which must be surfaced immediately during {@code connect()}.
 * These tests confirm that behaviour — {@code connect()} must throw for bad
 * credentials rather than silently succeeding.
 *
 * <p>Tests run unconditionally — they do not require a real OneDrive account.
 * A real HTTP call is made to the Microsoft token endpoint; the call fails
 * quickly with an auth error (or a connection error in environments without
 * network), and the important invariant is that {@code connect()} throws.
 */
public class TestOneDriveBadCredentials
{

    /**
     * {@link OneDriveConnector#connect()} must throw when given an invalid
     * password. The Microsoft OAuth2 ROPC token endpoint will return a 400 error.
     *
     * @throws Exception
     *             not expected — the connector must throw, not return normally
     */
    @Test
    public void testConnectThrowsForInvalidPassword() throws Exception
    {
        final ConnectionProperties connProps = new ConnectionProperties();
        connProps.put("tenant_id", "00000000-0000-0000-0000-000000000000");
        connProps.put("client_id", "invalid_client_id");
        connProps.put("client_secret", "invalid_client_secret");
        connProps.put("username", "invalid_user@example.com");
        connProps.put("password", "invalid_password");

        try (OneDriveConnector connector = new OneDriveConnector(connProps)) {
            connector.connect();
            fail("connect() must throw for invalid OneDrive credentials");
        }
        catch (Exception e) {
            // Expected — bad credentials must cause connect() to throw.
        }
    }

    /**
     * {@link OneDriveConnector#connect()} must throw when required credentials
     * are missing. The connector should detect the missing property and throw an
     * {@link IllegalArgumentException}.
     *
     * @throws Exception
     *             not expected — the connector must throw, not return normally
     */
    @Test
    public void testConnectThrowsForMissingRequiredProperty() throws Exception
    {
        final ConnectionProperties connProps = new ConnectionProperties();
        // Omit required properties (tenant_id, client_id, username, password)
        connProps.put("client_secret", "some_secret");

        try (OneDriveConnector connector = new OneDriveConnector(connProps)) {
            connector.connect();
            fail("connect() must throw when required properties are missing");
        }
        catch (Exception e) {
            // Expected — missing required property must cause connect() to throw.
        }
    }
}
