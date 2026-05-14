/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2026                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.restconnector;

import com.ibm.connect.sdk.util.ResourceBundleHelper;

/**
 * Localized messages for an Arrow-based connector.
 */
@SuppressWarnings({ "PMD.AvoidDollarSigns", "PMD.ClassNamingConventions" })
public enum RestMsgs implements ResourceBundleHelper.MessageFormatter<RestMsgs>
{
    /**
     * Data source type not supported.
     */
    DATASOURCE_TYPE_NOT_SUPPORTED,

    /**
     * Missing connector.
     */
    MISSING_CONNECTOR,

    /**
     * Missing property.
     */
    MISSING_PROPERTY,

    /**
     * Unsupported action.
     */
    UNSUPPORTED_ACTION;

    private static final ResourceBundleHelper<RestMsgs> BUNDLE = new ResourceBundleHelper<>(RestMsgs.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public String format(Object... args)
    {
        return BUNDLE.format(this, args);
    }

}
