/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.wdp.connect.sdk;

import com.ibm.connect.sdk.util.ResourceBundleHelper;

/**
 * Localized labels for a mock connector.
 */
public enum MockLabels implements ResourceBundleHelper.MessageFormatter<MockLabels>
{
    /**
     * Data source type label.
     */
    DATASOURCE_TYPE_LABEL,

    /**
     * Data source type description.
     */
    DATASOURCE_TYPE_DESCRIPTION,

    /**
     * Label for source property file_name.
     */
    SOURCE_FILE_NAME_LABEL,

    /**
     * Description for source property file_name.
     */
    SOURCE_FILE_NAME_DESCRIPTION,

    /**
     * Label for target property file_name.
     */
    TARGET_FILE_NAME_LABEL,

    /**
     * Description for target property file_name.
     */
    TARGET_FILE_NAME_DESCRIPTION;

    private static final ResourceBundleHelper<MockLabels> BUNDLE = new ResourceBundleHelper<>(MockLabels.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public String format(Object... args)
    {
        return BUNDLE.format(this, args);
    }

}
