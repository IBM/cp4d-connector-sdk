/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file;

import com.ibm.connect.sdk.util.ResourceBundleHelper;

/**
 * Localized messages for files.
 */
public enum FileMsgs implements ResourceBundleHelper.MessageFormatter<FileMsgs>
{
    /**
     * Data source type not supported.
     */
    DATASOURCE_TYPE_NOT_SUPPORTED,

    /**
     * Invalid path.
     */
    INVALID_PATH,

    /**
     * Missing property.
     */
    MISSING_PROPERTY,

    /**
     * The object is not a file.
     */
    NOT_A_FILE,

    /**
     * Unsupported action.
     */
    UNSUPPORTED_ACTION;

    private static final ResourceBundleHelper<FileMsgs> BUNDLE = new ResourceBundleHelper<>(FileMsgs.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public String format(Object... args)
    {
        return BUNDLE.format(this, args);
    }

}
