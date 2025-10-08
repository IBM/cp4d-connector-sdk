/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.api;

import com.ibm.connect.sdk.util.ResourceBundleHelper;

/**
 * Localized messages for the api package.
 */
public enum ApiMsgs implements ResourceBundleHelper.MessageFormatter<ApiMsgs>
{
    /**
     * Cannot construct ArrowLargeDecimalType from underlying type.
     */
    CANNOT_CONSTRUCT_ARROW_DECIMAL_TYPE,

    /**
     * Cannot set bytes for this arrow type.
     */
    CANNOT_SET_BYTES,

    /**
     * Invalid criteria.
     */
    INVALID_CRITERIA,

    /**
     * Missing action body.
     */
    MISSING_ACTION_BODY,

    /**
     * Missing asset.
     */
    MISSING_ASSET,

    /**
     * Missing asset id.
     */
    MISSING_ASSET_ID,

    /**
     * Missing partition index.
     */
    MISSING_PARTITION_INDEX,

    /**
     * No flight descriptor available for the given ticket.
     */
    NO_FLIGHT_DESCRIPTOR_FOR_TICKET,

    /**
     * Unexpected date object type.
     */
    UNEXPECTED_DATE_OBJECT_TYPE,

    /**
     * Unexpected time object type.
     */
    UNEXPECTED_TIME_OBJECT_TYPE,

    /**
     * Unexpected timestamp object type.
     */
    UNEXPECTED_TIMESTAMP_OBJECT_TYPE,

    /**
     * Unrecognized duration value.
     */
    UNRECOGNIZED_DURATION_VALUE,

    /**
     * Unsupported arrow extension type.
     */
    UNSUPPORTED_ARROW_EXTENSION_TYPE,

    /**
     * Unsupported arrow type.
     */
    UNSUPPORTED_ARROW_TYPE;

    private static final ResourceBundleHelper<ApiMsgs> BUNDLE = new ResourceBundleHelper<>(ApiMsgs.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public String format(Object... args)
    {
        return BUNDLE.format(this, args);
    }

}
