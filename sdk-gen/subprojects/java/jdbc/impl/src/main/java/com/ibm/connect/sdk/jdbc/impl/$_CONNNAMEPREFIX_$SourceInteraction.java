/* *************************************************** */

/* (C) Copyright IBM Corp. 2025                        */

/* *************************************************** */
package com.ibm.connect.sdk.jdbc.impl;

import org.apache.arrow.flight.Ticket;

import com.ibm.connect.sdk.api.TicketInfo;
import com.ibm.connect.sdk.jdbc.JdbcSourceInteraction;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;

/**
 * An interaction with a JDBC asset as a source.
 */
@SuppressWarnings({ "PMD.AvoidDollarSigns", "PMD.ClassNamingConventions" })
public class $_CONNNAMEPREFIX_$SourceInteraction extends JdbcSourceInteraction
{
    /**
     * Creates a JDBC source interaction.
     *
     * @param connector
     *            the connector managing the connection to the data source
     * @param asset
     *            the asset from which to read
     * @param ticket
     *            a Flight ticket to read a partition or null to get tickets
     * @throws Exception
     */
    public $_CONNNAMEPREFIX_$SourceInteraction($_CONNNAMEPREFIX_$Connector connector, CustomFlightAssetDescriptor asset, Ticket ticket) throws Exception
    {
        super(connector, asset, ticket);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String generateRowLimitPrefix(long rowLimit)
    {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String generateRowLimitSuffix(long rowLimit)
    {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getPartitioningPredicate(TicketInfo partitionInfo)
    {
        return null;
    }

}
