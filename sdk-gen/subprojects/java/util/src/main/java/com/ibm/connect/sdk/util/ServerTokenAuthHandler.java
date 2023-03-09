/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.util;

import static org.slf4j.LoggerFactory.getLogger;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.Iterator;
import java.util.Optional;

import org.apache.arrow.flight.CallStatus;
import org.apache.arrow.flight.auth.ServerAuthHandler;
import org.slf4j.Logger;

/**
 * Authentication handler for a Flight server.
 */
public class ServerTokenAuthHandler implements ServerAuthHandler
{
    private static final Logger LOGGER = getLogger(ServerTokenAuthHandler.class);

    private final PublicKey[] publicKeys;

    private static class Holder
    {
        static final ServerTokenAuthHandler INSTANCE = new ServerTokenAuthHandler(AuthUtils.getAllVerificationKeys());
    }

    /**
     * @return the singleton instance.
     */
    public static ServerTokenAuthHandler getInstance()
    {
        return Holder.INSTANCE;
    }

    /**
     * Constructs an authentication handler with no token verification.
     */
    public ServerTokenAuthHandler()
    {
        this.publicKeys = null;
    }

    /**
     * Constructs an authentication handler with token verification.
     *
     * @param publicKeys
     *            public keys for token verification
     */
    public ServerTokenAuthHandler(PublicKey[] publicKeys)
    {
        this.publicKeys = publicKeys != null ? publicKeys.clone() : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<String> isValid(byte[] token)
    {
        if (token == null) {
            return Optional.empty();
        }
        final String peer;
        try {
            final String authToken = new String(token, StandardCharsets.UTF_8);
            peer = AuthUtils.validateAuthToken(authToken, publicKeys);
        }
        catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
            return Optional.empty();
        }
        return Optional.of(peer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean authenticate(ServerAuthSender outgoing, Iterator<byte[]> incoming)
    {
        final byte[] authTokenBytes = incoming.next();
        if (authTokenBytes == null) {
            return false;
        }
        final String authToken = new String(authTokenBytes, StandardCharsets.UTF_8);
        String peer = "";
        try {
            peer = AuthUtils.validateAuthToken(authToken, publicKeys);
            outgoing.send(authTokenBytes);
            // There seems to be a bug in arrow or grpc where sending a response too quickly
            // here will cause the stream to be closed before the client sends another block
            // of data. The bug is that no more data is needed, but if we close too quickly
            // then an error returns.
            try {
                incoming.next();
            }
            catch (final IllegalStateException e) {
                // Ignore it.
            }
        }
        catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
            outgoing.onError(CallStatus.UNAUTHENTICATED.withCause(e).withDescription(e.getMessage()).toRuntimeException());
        }
        LOGGER.info("Authenticated peer " + peer);
        return !peer.isEmpty();
    }
}
