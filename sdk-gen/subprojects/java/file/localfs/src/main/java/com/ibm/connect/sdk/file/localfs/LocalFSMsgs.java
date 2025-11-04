/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.localfs;

import com.ibm.connect.sdk.util.ResourceBundleHelper;

/**
 * Localized messages for the local file system.
 */
public enum LocalFSMsgs implements ResourceBundleHelper.MessageFormatter<LocalFSMsgs>
{
    /**
     * The object does not exist.
     */
    DOES_NOT_EXIST,

    /**
     * The object is not a directory.
     */
    NOT_A_DIRECTORY;

    private static final ResourceBundleHelper<LocalFSMsgs> BUNDLE = new ResourceBundleHelper<>(LocalFSMsgs.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public String format(Object... args)
    {
        return BUNDLE.format(this, args);
    }

}
