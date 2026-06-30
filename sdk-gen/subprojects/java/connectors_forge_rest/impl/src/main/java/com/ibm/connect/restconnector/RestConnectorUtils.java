/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import java.util.Locale;

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
                    return segments[i].toUpperCase(Locale.ENGLISH);
                }
            }
        }

        // Fall back to asset name
        if (asset.getName() != null && !asset.getName().isEmpty()) {
            return asset.getName().toUpperCase(Locale.ENGLISH);
        }

        // Fall back to asset ID
        if (asset.getId() != null && !asset.getId().isEmpty()) {
            return asset.getId().toUpperCase(Locale.ENGLISH);
        }

        throw new IllegalArgumentException("Cannot determine table name from asset: path=" + path
                + ", name=" + asset.getName() + ", id=" + asset.getId());
    }

    /**
     * Resolves the table name from a path and a fallback name.
     *
     * @param path
     *            the asset path (may be null or empty)
     * @param name
     *            the asset name (fallback)
     * @return the table name (upper-case)
     */
    public static String resolveTableName(String path, String name)
    {
        if (path != null && !path.isEmpty()) {
            final String[] segments = path.split("/");
            for (int i = segments.length - 1; i >= 0; i--) {
                if (!segments[i].isEmpty()) {
                    return segments[i].toUpperCase(Locale.ENGLISH);
                }
            }
        }
        if (name != null && !name.isEmpty()) {
            return name.toUpperCase(Locale.ENGLISH);
        }
        throw new IllegalArgumentException("Cannot determine table name from path=" + path + ", name=" + name);
    }

}

// Made with Bob