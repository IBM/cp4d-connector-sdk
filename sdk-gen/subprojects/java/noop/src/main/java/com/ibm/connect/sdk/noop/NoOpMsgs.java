/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.noop;

import com.ibm.connect.sdk.util.ResourceBundleHelper;

/**
 * Localized messages for the noop package.
 */
public enum NoOpMsgs implements ResourceBundleHelper.MessageFormatter<NoOpMsgs>
{
    /**
     * Method not implemented.
     */
    METHOD_NOT_IMPLEMENTED;

    private static final ResourceBundleHelper<NoOpMsgs> BUNDLE = new ResourceBundleHelper<>(NoOpMsgs.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public String format(Object... args)
    {
        return BUNDLE.format(this, args);
    }
}
