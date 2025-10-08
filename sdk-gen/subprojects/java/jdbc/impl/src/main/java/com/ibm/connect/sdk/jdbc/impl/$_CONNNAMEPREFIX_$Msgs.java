/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.jdbc.impl;

import com.ibm.connect.sdk.util.ResourceBundleHelper;

/**
 * Localized messages for a data source using JDBC.
 */
@SuppressWarnings({ "PMD.AvoidDollarSigns", "PMD.ClassNamingConventions" })
public enum $_CONNNAMEPREFIX_$Msgs implements ResourceBundleHelper.MessageFormatter<$_CONNNAMEPREFIX_$Msgs>
{
    /**
     * Data source type not supported.
     */
    DATASOURCE_TYPE_NOT_SUPPORTED,

    /**
     * Missing property.
     */
    MISSING_PROPERTY;

    private static final ResourceBundleHelper<$_CONNNAMEPREFIX_$Msgs> BUNDLE = new ResourceBundleHelper<>($_CONNNAMEPREFIX_$Msgs.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public String format(Object... args)
    {
        return BUNDLE.format(this, args);
    }

}
