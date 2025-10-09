/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.jdbc.derby;

import com.ibm.connect.sdk.util.ResourceBundleHelper;

/**
 * Localized messages for Apache Derby.
 */
public enum DerbyMsgs implements ResourceBundleHelper.MessageFormatter<DerbyMsgs>
{
    /**
     * Data source type not supported.
     */
    DATASOURCE_TYPE_NOT_SUPPORTED,

    /**
     * Missing property.
     */
    MISSING_PROPERTY;

    private static final ResourceBundleHelper<DerbyMsgs> BUNDLE = new ResourceBundleHelper<>(DerbyMsgs.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public String format(Object... args)
    {
        return BUNDLE.format(this, args);
    }

}
