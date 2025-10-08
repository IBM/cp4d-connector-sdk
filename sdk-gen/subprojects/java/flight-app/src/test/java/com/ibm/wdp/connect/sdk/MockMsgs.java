/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.wdp.connect.sdk;

import com.ibm.connect.sdk.util.ResourceBundleHelper;

/**
 * Localized messages for a mock connector.
 */
public enum MockMsgs implements ResourceBundleHelper.MessageFormatter<MockMsgs>
{
    /**
     * Unsupported action.
     */
    UNSUPPORTED_ACTION;

    private static final ResourceBundleHelper<MockMsgs> BUNDLE = new ResourceBundleHelper<>(MockMsgs.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public String format(Object... args)
    {
        return BUNDLE.format(this, args);
    }

}
