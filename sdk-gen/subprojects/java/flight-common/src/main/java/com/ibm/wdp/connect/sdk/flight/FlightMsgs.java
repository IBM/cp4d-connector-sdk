/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.wdp.connect.sdk.flight;

import com.ibm.connect.sdk.util.ResourceBundleHelper;

/**
 * Localized messages for Flight.
 */
public enum FlightMsgs implements ResourceBundleHelper.MessageFormatter<FlightMsgs>
{
    /**
     * Invalid criteria.
     */
    INVALID_CRITERIA,

    /**
     * Missing action body.
     */
    MISSING_ACTION_BODY,

    /**
     * Missing data source type name.
     */
    MISSING_DATASOURCE_TYPE_NAME;

    private static final ResourceBundleHelper<FlightMsgs> BUNDLE = new ResourceBundleHelper<>(FlightMsgs.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public String format(Object... args)
    {
        return BUNDLE.format(this, args);
    }

}
