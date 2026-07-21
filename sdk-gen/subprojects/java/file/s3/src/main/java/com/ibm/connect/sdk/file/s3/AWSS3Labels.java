/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2026                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.s3;

import com.ibm.connect.sdk.util.ResourceBundleHelper;

/**
 * Localized labels for the Amazon S3 connector.
 */
public enum AWSS3Labels implements ResourceBundleHelper.MessageFormatter<AWSS3Labels>
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
     * Label for connection property bucket.
     */
    CONNECTION_BUCKET_LABEL,

    /**
     * Description for connection property bucket.
     */
    CONNECTION_BUCKET_DESCRIPTION,

    /**
     * Label for connection property region.
     */
    CONNECTION_REGION_LABEL,

    /**
     * Description for connection property region.
     */
    CONNECTION_REGION_DESCRIPTION,

    /**
     * Label for connection property endpoint_url.
     */
    CONNECTION_ENDPOINT_URL_LABEL,

    /**
     * Description for connection property endpoint_url.
     */
    CONNECTION_ENDPOINT_URL_DESCRIPTION,

    /**
     * Label for connection property access_key_id.
     */
    CONNECTION_ACCESS_KEY_ID_LABEL,

    /**
     * Description for connection property access_key_id.
     */
    CONNECTION_ACCESS_KEY_ID_DESCRIPTION,

    /**
     * Label for connection property secret_access_key.
     */
    CONNECTION_SECRET_ACCESS_KEY_LABEL,

    /**
     * Description for connection property secret_access_key.
     */
    CONNECTION_SECRET_ACCESS_KEY_DESCRIPTION,

    /**
     * Label for source property file_name.
     */
    SOURCE_FILE_NAME_LABEL,

    /**
     * Description for source property file_name.
     */
    SOURCE_FILE_NAME_DESCRIPTION,

    /**
     * Label for action get_acl.
     */
    ACTION_GET_ACL_LABEL,

    /**
     * Description for action get_acl.
     */
    ACTION_GET_ACL_DESCRIPTION,

    /**
     * Label for action get_acl input property object_key.
     */
    ACTION_GET_ACL_INPUT_OBJECT_KEY_LABEL,

    /**
     * Description for action get_acl input property object_key.
     */
    ACTION_GET_ACL_INPUT_OBJECT_KEY_DESCRIPTION,

    /**
     * Label for action get_acl output property acl.
     */
    ACTION_GET_ACL_OUTPUT_ACL_LABEL,

    /**
     * Description for action get_acl output property acl.
     */
    ACTION_GET_ACL_OUTPUT_ACL_DESCRIPTION;

    private static final ResourceBundleHelper<AWSS3Labels> BUNDLE = new ResourceBundleHelper<>(AWSS3Labels.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public String format(Object... args)
    {
        return BUNDLE.format(this, args);
    }

}
