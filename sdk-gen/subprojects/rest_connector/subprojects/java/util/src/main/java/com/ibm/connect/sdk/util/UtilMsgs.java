/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.util;

/**
 * Localized messages for the util package.
 */
public enum UtilMsgs implements ResourceBundleHelper.MessageFormatter<UtilMsgs>
{
    /**
     * Invalid authentication token.
     */
    INVALID_AUTH_TOKEN,

    /**
     * Invalid byte limit.
     */
    INVALID_BYTE_LIMIT,

    /**
     * Missing authentication token.
     */
    MISSING_AUTH_TOKEN,

    /**
     * Missing trust store password.
     */
    MISSING_TRUSTSTORE_PASSWORD;

    private static final ResourceBundleHelper<UtilMsgs> BUNDLE = new ResourceBundleHelper<>(UtilMsgs.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public String format(Object... args)
    {
        return BUNDLE.format(this, args);
    }
}
