/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2026                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.s3;

import com.ibm.connect.sdk.util.ResourceBundleHelper;

/**
 * Localized messages for the Amazon S3 connector.
 */
public enum AWSS3Msgs implements ResourceBundleHelper.MessageFormatter<AWSS3Msgs>
{
    /**
     * The object does not exist.
     */
    OBJECT_DOES_NOT_EXIST,

    /**
     * The object is not a file.
     */
    OBJECT_IS_A_PREFIX;

    private static final ResourceBundleHelper<AWSS3Msgs> BUNDLE = new ResourceBundleHelper<>(AWSS3Msgs.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public String format(Object... args)
    {
        return BUNDLE.format(this, args);
    }

}
