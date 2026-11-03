/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2026                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.box;

import com.ibm.connect.sdk.util.ResourceBundleHelper;

/**
 * Localized messages for the Box connector.
 */
public enum BoxMsgs implements ResourceBundleHelper.MessageFormatter<BoxMsgs>
{
    /**
     * File not found in Box.
     */
    FILE_NOT_FOUND,

    /**
     * Folder not found in Box.
     */
    FOLDER_NOT_FOUND;

    private static final ResourceBundleHelper<BoxMsgs> BUNDLE = new ResourceBundleHelper<>(BoxMsgs.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public String format(Object... args)
    {
        return BUNDLE.format(this, args);
    }

}
