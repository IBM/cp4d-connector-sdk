/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.util;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;

import org.apache.arrow.flight.auth.ClientAuthHandler;

/**
 * Authentication handler for a Flight client.
 */
public class ClientTokenAuthHandler implements ClientAuthHandler
{
    private final byte[] authToken;
    private byte[] callToken = new byte[0];

    /**
     * Constructs an authentication handler for a Flight client.
     *
     * @param authToken
     *            authorization token
     */
    public ClientTokenAuthHandler(String authToken)
    {
        this.authToken = authToken.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void authenticate(ClientAuthSender outgoing, Iterator<byte[]> incoming)
    {
        outgoing.send(authToken);
        callToken = incoming.next();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] getCallToken()
    {
        return callToken.clone();
    }
}
