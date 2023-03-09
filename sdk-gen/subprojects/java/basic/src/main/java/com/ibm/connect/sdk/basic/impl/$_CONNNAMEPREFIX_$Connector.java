/* *************************************************** */

/* (C) Copyright IBM Corp. 2022                        */

/* *************************************************** */
package com.ibm.connect.sdk.basic.impl;

import java.util.List;

import org.apache.arrow.flight.Ticket;

import com.ibm.connect.sdk.api.RowBasedConnector;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionActionConfiguration;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionActionResponse;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetsCriteria;

@SuppressWarnings({ "PMD.AvoidDollarSigns", "PMD.ClassNamingConventions" })
public class $_CONNNAMEPREFIX_$Connector
        extends RowBasedConnector<$_CONNNAMEPREFIX_$SourceInteraction, $_CONNNAMEPREFIX_$TargetInteraction>
{
    /**
     * Creates a row-based connector.
     *
     * @param properties
     *            connection properties
     */
    public $_CONNNAMEPREFIX_$Connector(ConnectionProperties properties)
    {
        super(properties);
    }

    @Override
    public void close() throws Exception
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void connect()
    {
        // TODO Auto-generated method stub

    }

    @Override
    public List<CustomFlightAssetDescriptor> discoverAssets(CustomFlightAssetsCriteria criteria) throws Exception
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public $_CONNNAMEPREFIX_$SourceInteraction getSourceInteraction(CustomFlightAssetDescriptor asset, Ticket ticket)
    {
        // TODO include your ticket info
        return new $_CONNNAMEPREFIX_$SourceInteraction(this, asset);
    }

    @Override
    public $_CONNNAMEPREFIX_$TargetInteraction getTargetInteraction(CustomFlightAssetDescriptor asset)
    {
        return new $_CONNNAMEPREFIX_$TargetInteraction(this, asset);
    }

    @Override
    public ConnectionActionResponse performAction(String action, ConnectionActionConfiguration conf)
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void commit()
    {
        // TODO Auto-generated method stub

    }

}
