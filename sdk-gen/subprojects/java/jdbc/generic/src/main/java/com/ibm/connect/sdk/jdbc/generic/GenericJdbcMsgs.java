/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.jdbc.generic;

import com.ibm.connect.sdk.util.ResourceBundleHelper;

/**
 * Localized messages for generic JDBC.
 */
public enum GenericJdbcMsgs implements ResourceBundleHelper.MessageFormatter<GenericJdbcMsgs>
{
    /**
     * Data source type not supported.
     */
    DATASOURCE_TYPE_NOT_SUPPORTED,

    /**
     * Invalid driver.
     */
    INVALID_DRIVER,

    /**
     * Invalid JDBC URL.
     */
    INVALID_JDBC_URL,

    /**
     * Invalid property.
     */
    INVALID_PROPERTY,

    /**
     * Missing property.
     */
    MISSING_PROPERTY;

    private static final ResourceBundleHelper<GenericJdbcMsgs> BUNDLE = new ResourceBundleHelper<>(GenericJdbcMsgs.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public String format(Object... args)
    {
        return BUNDLE.format(this, args);
    }

}
