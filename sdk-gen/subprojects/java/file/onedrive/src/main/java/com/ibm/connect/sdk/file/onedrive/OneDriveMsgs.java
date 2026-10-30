/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2026                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.onedrive;

import com.ibm.connect.sdk.util.ResourceBundleHelper;

/**
 * Localized messages for the OneDrive connector.
 */
public enum OneDriveMsgs implements ResourceBundleHelper.MessageFormatter<OneDriveMsgs>
{
    /**
     * File not found in OneDrive.
     */
    FILE_NOT_FOUND,

    /**
     * Folder not found in OneDrive.
     */
    FOLDER_NOT_FOUND;

    private static final ResourceBundleHelper<OneDriveMsgs> BUNDLE = new ResourceBundleHelper<>(OneDriveMsgs.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public String format(Object... args)
    {
        return BUNDLE.format(this, args);
    }

}
