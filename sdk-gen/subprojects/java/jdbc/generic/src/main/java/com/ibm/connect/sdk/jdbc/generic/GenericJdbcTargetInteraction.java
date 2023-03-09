/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.jdbc.generic;

import com.ibm.connect.sdk.jdbc.JdbcTargetInteraction;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;

/**
 * An interaction with a generic JDBC asset as a target.
 */
public class GenericJdbcTargetInteraction extends JdbcTargetInteraction
{
    /**
     * Creates a generic JDBC target interaction.
     *
     * @param connector
     *            the connector managing the connection to the data source
     * @param asset
     *            the asset to which to write
     * @throws Exception
     */
    public GenericJdbcTargetInteraction(GenericJdbcConnector connector, CustomFlightAssetDescriptor asset) throws Exception
    {
        super(connector, asset);
    }
}
