/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.localfs;

import com.ibm.connect.sdk.util.ResourceBundleHelper;

/**
 * Localized labels for the local file system.
 */
public enum LocalFSLabels implements ResourceBundleHelper.MessageFormatter<LocalFSLabels>
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
     * Label for connection property root_path.
     */
    CONNECTION_ROOT_PATH_LABEL,

    /**
     * Description for connection property root_path.
     */
    CONNECTION_ROOT_PATH_DESCRIPTION;

    private static final ResourceBundleHelper<LocalFSLabels> BUNDLE = new ResourceBundleHelper<>(LocalFSLabels.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public String format(Object... args)
    {
        return BUNDLE.format(this, args);
    }

}
