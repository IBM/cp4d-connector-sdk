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
     * The directory (key prefix) does not exist.
     */
    DIRECTORY_DOES_NOT_EXIST,

    /**
     * Failed to read the bucket policy.
     */
    BUCKET_POLICY_FETCH_ERROR,

    /**
     * Failed to parse the bucket policy JSON.
     */
    BUCKET_POLICY_PARSE_ERROR,

    /**
     * Bucket policy JSON is missing the Statement array.
     */
    BUCKET_POLICY_STATEMENT_MISSING,

    /**
     * A required attribute is missing from a policy statement.
     */
    BUCKET_POLICY_ATTR_MISSING,

    /**
     * The path does not start with a bucket segment.
     */
    PATH_MUST_START_WITH_BUCKET,

    /**
     * The bucket segment in the path does not match the connection bucket.
     */
    PATH_BUCKET_MISMATCH;

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
