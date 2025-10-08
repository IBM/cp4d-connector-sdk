/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.bundle;

import com.ibm.connect.sdk.util.ResourceBundleHelper;

/**
 * Localized messages for a bundle of multiple connectors.
 */
public enum BundleMsgs implements ResourceBundleHelper.MessageFormatter<BundleMsgs>
{
    /**
     * Data source type not supported.
     */
    DATASOURCE_TYPE_NOT_SUPPORTED;

    private static final ResourceBundleHelper<BundleMsgs> BUNDLE = new ResourceBundleHelper<>(BundleMsgs.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public String format(Object... args)
    {
        return BUNDLE.format(this, args);
    }

}
