/* *************************************************** */

/* (C) Copyright IBM Corp. 2025                        */

/* *************************************************** */
package com.ibm.connect.sdk.jdbc.impl;

import com.ibm.connect.sdk.jdbc.JdbcTargetInteraction;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;

/**
 * An interaction with a JDBC asset as a target.
 */
@SuppressWarnings({ "PMD.AvoidDollarSigns", "PMD.ClassNamingConventions" })
public class $_CONNNAMEPREFIX_$TargetInteraction extends JdbcTargetInteraction
{
    /**
     * Creates a JDBC target interaction.
     *
     * @param connector
     *            the connector managing the connection to the data source
     * @param asset
     *            the asset to which to write
     * @throws Exception
     */
    public $_CONNNAMEPREFIX_$TargetInteraction($_CONNNAMEPREFIX_$Connector connector, CustomFlightAssetDescriptor asset) throws Exception
    {
        super(connector, asset);
    }
}
