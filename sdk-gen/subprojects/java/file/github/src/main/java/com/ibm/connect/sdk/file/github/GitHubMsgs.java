/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.github;

import com.ibm.connect.sdk.util.ResourceBundleHelper;

/**
 * Localized messages for GitHub.
 */
public enum GitHubMsgs implements ResourceBundleHelper.MessageFormatter<GitHubMsgs>
{
    /**
     * Property is required to have a particular value.
     */
    REQUIRED_PROPERTY_VALUE,

    /**
     * Data source type not supported as a target.
     */
    TARGET_INTERACTION_NOT_SUPPORTED;

    private static final ResourceBundleHelper<GitHubMsgs> BUNDLE = new ResourceBundleHelper<>(GitHubMsgs.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public String format(Object... args)
    {
        return BUNDLE.format(this, args);
    }

}
