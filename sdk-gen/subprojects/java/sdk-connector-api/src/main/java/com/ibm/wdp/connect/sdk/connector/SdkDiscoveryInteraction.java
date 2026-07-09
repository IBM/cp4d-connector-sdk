/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.wdp.connect.sdk.connector;

import java.util.List;

/**
 * Discovery interaction for browsing assets available through a connector.
 *
 * <p>Functionally equivalent to the library's {@code DiscoveryInteraction} but does not require
 * any library-specific types. Connector authors implement this interface to expose browseable
 * assets (tables, schemas, etc.) without knowledge of the library's lifecycle management.
 *
 * <p>Instances are obtained from {@link SdkConnector#getDiscoveryInteraction(DiscoveryCriteria)}.
 */
public interface SdkDiscoveryInteraction extends AutoCloseable
{
    /**
     * Discovers assets matching the given criteria.
     *
     * @param criteria
     *            the criteria scoping this discovery request (path, datasource type, connection properties)
     * @return a list of discovered assets; never null, may be empty
     * @throws Exception
     *             if an error occurs during discovery
     */
    List<AssetDescriptor> discoverAssets(DiscoveryCriteria criteria) throws Exception;
}

// Made with Bob
