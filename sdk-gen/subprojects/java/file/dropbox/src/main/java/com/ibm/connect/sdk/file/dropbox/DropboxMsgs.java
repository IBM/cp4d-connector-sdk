/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2026                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.dropbox;

import com.ibm.connect.sdk.util.ResourceBundleHelper;

/**
 * Localized messages for the Dropbox connector.
 */
public enum DropboxMsgs implements ResourceBundleHelper.MessageFormatter<DropboxMsgs>
{
    /**
     * File not found in Dropbox.
     */
    FILE_NOT_FOUND,

    /**
     * Folder not found in Dropbox.
     */
    FOLDER_NOT_FOUND,

    /**
     * Missing required authentication credentials.
     */
    MISSING_AUTH_CREDENTIALS;

    private static final ResourceBundleHelper<DropboxMsgs> BUNDLE = new ResourceBundleHelper<>(DropboxMsgs.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public String format(Object... args)
    {
        return BUNDLE.format(this, args);
    }

}
