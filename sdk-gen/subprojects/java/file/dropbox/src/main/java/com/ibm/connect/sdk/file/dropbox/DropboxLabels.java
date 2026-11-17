/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2026                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.dropbox;

import com.ibm.connect.sdk.util.ResourceBundleHelper;

/**
 * Localized labels for the Dropbox connector.
 */
public enum DropboxLabels implements ResourceBundleHelper.MessageFormatter<DropboxLabels>
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
     * Label for connection property access_token.
     */
    CONNECTION_ACCESS_TOKEN_LABEL,

    /**
     * Description for connection property access_token.
     */
    CONNECTION_ACCESS_TOKEN_DESCRIPTION,

    /**
     * Label for connection property refresh_token.
     */
    CONNECTION_REFRESH_TOKEN_LABEL,

    /**
     * Description for connection property refresh_token.
     */
    CONNECTION_REFRESH_TOKEN_DESCRIPTION,

    /**
     * Label for connection property app_key.
     */
    CONNECTION_APP_KEY_LABEL,

    /**
     * Description for connection property app_key.
     */
    CONNECTION_APP_KEY_DESCRIPTION,

    /**
     * Label for connection property app_secret.
     */
    CONNECTION_APP_SECRET_LABEL,

    /**
     * Description for connection property app_secret.
     */
    CONNECTION_APP_SECRET_DESCRIPTION,

    /**
     * Label for source interaction property file_name.
     */
    SOURCE_FILE_NAME_LABEL,

    /**
     * Description for source interaction property file_name.
     */
    SOURCE_FILE_NAME_DESCRIPTION,

    /**
     * Label for the binary (raw bytes) file format enum value.
     */
    SOURCE_FILE_FORMAT_BINARY_LABEL,

    /**
     * Label for target interaction property file_name.
     */
    TARGET_FILE_NAME_LABEL,

    /**
     * Description for target interaction property file_name.
     */
    TARGET_FILE_NAME_DESCRIPTION,

    /**
     * Label for the binary (raw bytes) target file format enum value.
     */
    TARGET_FILE_FORMAT_BINARY_LABEL,

    /**
     * Description for action get_acl.
     */
    ACTION_GET_ACL_DESCRIPTION,

    /**
     * Label for action get_acl input property path.
     */
    ACTION_GET_ACL_INPUT_PATH_LABEL,

    /**
     * Description for action get_acl input property path.
     */
    ACTION_GET_ACL_INPUT_PATH_DESCRIPTION,

    /**
     * Label for action get_acl output property allow.
     */
    ACTION_GET_ACL_OUTPUT_ALLOW_LABEL,

    /**
     * Description for action get_acl output property allow.
     */
    ACTION_GET_ACL_OUTPUT_ALLOW_DESCRIPTION,

    /**
     * Label for action get_acl output property deny.
     */
    ACTION_GET_ACL_OUTPUT_DENY_LABEL,

    /**
     * Description for action get_acl output property deny.
     */
    ACTION_GET_ACL_OUTPUT_DENY_DESCRIPTION,

    /**
     * Description for action get_file_metadata.
     */
    ACTION_GET_FILE_METADATA_DESCRIPTION,

    /**
     * Label for action get_file_metadata input property path.
     */
    ACTION_GET_FILE_METADATA_INPUT_PATH_LABEL,

    /**
     * Description for action get_file_metadata input property path.
     */
    ACTION_GET_FILE_METADATA_INPUT_PATH_DESCRIPTION,

    /**
     * Label for action get_file_metadata output property last_modified.
     */
    ACTION_GET_FILE_METADATA_OUTPUT_LAST_MODIFIED_LABEL,

    /**
     * Description for action get_file_metadata output property last_modified.
     */
    ACTION_GET_FILE_METADATA_OUTPUT_LAST_MODIFIED_DESCRIPTION,

    /**
     * Label for action get_file_metadata output property size.
     */
    ACTION_GET_FILE_METADATA_OUTPUT_SIZE_LABEL,

    /**
     * Description for action get_file_metadata output property size.
     */
    ACTION_GET_FILE_METADATA_OUTPUT_SIZE_DESCRIPTION;

    private static final ResourceBundleHelper<DropboxLabels> BUNDLE = new ResourceBundleHelper<>(DropboxLabels.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public String format(Object... args)
    {
        return BUNDLE.format(this, args);
    }

}
