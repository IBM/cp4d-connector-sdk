/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.jdbc;

import com.ibm.connect.sdk.util.ResourceBundleHelper;

/**
 * Localized messages for JDBC.
 */
public enum JdbcMsgs implements ResourceBundleHelper.MessageFormatter<JdbcMsgs>
{
    /**
     * Invalid path.
     */
    INVALID_PATH,

    /**
     * Missing one of properties.
     */
    MISSING_ONE_OF_PROPERTIES,

    /**
     * Missing length for field.
     */
    MISSING_LENGTH_FOR_FIELD,

    /**
     * Missing property.
     */
    MISSING_PROPERTY,

    /**
     * Unknown type.
     */
    UNKNOWN_TYPE,

    /**
     * Unsupported action.
     */
    UNSUPPORTED_ACTION,

    /**
     * Unsupported data type for column.
     */
    UNSUPPORTED_DATA_TYPE_FOR_COLUMN;

    private static final ResourceBundleHelper<JdbcMsgs> BUNDLE = new ResourceBundleHelper<>(JdbcMsgs.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public String format(Object... args)
    {
        return BUNDLE.format(this, args);
    }

}
