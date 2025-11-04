/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.github;

import com.ibm.connect.sdk.util.ResourceBundleHelper;

/**
 * Localized labels for GitHub.
 */
public enum GitHubLabels implements ResourceBundleHelper.MessageFormatter<GitHubLabels>
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
     * Label for connection property host.
     */
    CONNECTION_HOST_LABEL,

    /**
     * Description for connection property host.
     */
    CONNECTION_HOST_DESCRIPTION,

    /**
     * Label for connection property repository_owner.
     */
    CONNECTION_REPOSITORY_OWNER_LABEL,

    /**
     * Description for connection property repository_owner.
     */
    CONNECTION_REPOSITORY_OWNER_DESCRIPTION,

    /**
     * Label for connection property repository_name.
     */
    CONNECTION_REPOSITORY_NAME_LABEL,

    /**
     * Description for connection property repository_name.
     */
    CONNECTION_REPOSITORY_NAME_DESCRIPTION,

    /**
     * Label for connection property branch_name.
     */
    CONNECTION_BRANCH_NAME_LABEL,

    /**
     * Description for connection property branch_name.
     */
    CONNECTION_BRANCH_NAME_DESCRIPTION,

    /**
     * Label for connection property access_token.
     */
    CONNECTION_ACCESS_TOKEN_LABEL,

    /**
     * Description for connection property access_token.
     */
    CONNECTION_ACCESS_TOKEN_DESCRIPTION,

    /**
     * Label for source property branch_name.
     */
    SOURCE_BRANCH_NAME_LABEL,

    /**
     * Description for source property branch_name.
     */
    SOURCE_BRANCH_NAME_DESCRIPTION,

    /**
     * Label for asset type branch.
     */
    ASSET_TYPE_BRANCH_LABEL;

    private static final ResourceBundleHelper<GitHubLabels> BUNDLE = new ResourceBundleHelper<>(GitHubLabels.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public String format(Object... args)
    {
        return BUNDLE.format(this, args);
    }

}
