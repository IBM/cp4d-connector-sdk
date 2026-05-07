/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;

/**
 * Utility methods for REST connector operations.
 */
public final class RestConnectorUtils
{
    private RestConnectorUtils()
    {
        // Prevent instantiation
    }

    /**
     * Resolves the table name from an asset descriptor.
     *
     * <p>The table name is determined by (in order of priority):
     * <ol>
     *   <li>The last non-empty segment of the asset path (e.g. "/ROCKETS/ROCKETS" → "ROCKETS")</li>
     *   <li>The asset name</li>
     *   <li>The asset ID</li>
     * </ol>
     *
     * @param asset
     *            the asset descriptor
     * @return the table name (upper-case)
     * @throws IllegalArgumentException if the table name cannot be determined
     */
    public static String resolveTableName(CustomFlightAssetDescriptor asset)
    {
        if (asset == null) {
            throw new IllegalArgumentException("Asset descriptor cannot be null");
        }

        // Try path first: "/ROCKETS/ROCKETS" → last segment "ROCKETS"
        final String path = asset.getPath();
        if (path != null && !path.isEmpty()) {
            final String[] segments = path.split("/");
            for (int i = segments.length - 1; i >= 0; i--) {
                if (!segments[i].isEmpty()) {
                    return segments[i].toUpperCase(java.util.Locale.ENGLISH);
                }
            }
        }

        // Fall back to asset name
        if (asset.getName() != null && !asset.getName().isEmpty()) {
            return asset.getName().toUpperCase(java.util.Locale.ENGLISH);
        }

        // Fall back to asset ID
        if (asset.getId() != null && !asset.getId().isEmpty()) {
            return asset.getId().toUpperCase(java.util.Locale.ENGLISH);
        }

        throw new IllegalArgumentException("Cannot determine table name from asset: path=" + path
                + ", name=" + asset.getName() + ", id=" + asset.getId());
    }

}

// Made with Bob