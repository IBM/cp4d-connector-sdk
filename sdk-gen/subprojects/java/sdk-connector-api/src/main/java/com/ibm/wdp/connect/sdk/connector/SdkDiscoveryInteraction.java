/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.wdp.connect.sdk.connector;

import java.util.List;

import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetsCriteria;

/**
 * Discovery interaction for browsing assets available through a connector.
 *
 * <p>Connector authors implement this interface to expose browseable assets (tables, schemas, etc.)
 * without knowledge of the library's lifecycle management.
 *
 * <p>Instances are obtained from {@link SdkConnector#getDiscoveryInteraction(CustomFlightAssetsCriteria)}.
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
    List<CustomFlightAssetDescriptor> discoverAssets(CustomFlightAssetsCriteria criteria) throws Exception;
}
