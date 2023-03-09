/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.jdbc.generic;

import java.util.Collections;

import org.apache.arrow.flight.Ticket;

import com.ibm.connect.sdk.api.TicketInfo;
import com.ibm.connect.sdk.jdbc.JdbcSourceInteraction;
import com.ibm.connect.sdk.util.Utils;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;

/**
 * An interaction with a generic JDBC asset as a source.
 */
public class GenericJdbcSourceInteraction extends JdbcSourceInteraction
{
    /**
     * Creates a generic JDBC source interaction.
     *
     * @param connector
     *            the connector managing the connection to the data source
     * @param asset
     *            the asset from which to read
     * @param ticket
     *            a Flight ticket to read a partition or null to get tickets
     * @throws Exception
     */
    public GenericJdbcSourceInteraction(GenericJdbcConnector connector, CustomFlightAssetDescriptor asset, Ticket ticket) throws Exception
    {
        super(connector, asset, ticket);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String generateRowLimitPrefix(long rowLimit)
    {
        final String rowLimitSupport = getConnector().getProperties().getProperty("row_limit_support", "none");
        final String rowLimitPrefix;
        if ("prefix".equals(rowLimitSupport)) {
            rowLimitPrefix = getConnector().getProperties().getProperty("row_limit_prefix");
        } else if ("none".equals(rowLimitSupport)) {
            final String driverName = GenericJdbcConnector.getDriverName(getConnector().getProperties().getProperty("jdbc_url"));
            rowLimitPrefix = GenericJdbcConnector.getRowLimitPrefix(driverName);
        } else {
            rowLimitPrefix = null;
        }
        if (rowLimitPrefix != null) {
            return Utils.substituteTokens(rowLimitPrefix, Collections.singletonMap("row_limit", String.valueOf(rowLimit)));
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String generateRowLimitSuffix(long rowLimit)
    {
        final String rowLimitSupport = getConnector().getProperties().getProperty("row_limit_support", "none");
        final String rowLimitSuffix;
        if ("suffix".equals(rowLimitSupport)) {
            rowLimitSuffix = getConnector().getProperties().getProperty("row_limit_suffix");
        } else if ("none".equals(rowLimitSupport)) {
            final String driverName = GenericJdbcConnector.getDriverName(getConnector().getProperties().getProperty("jdbc_url"));
            rowLimitSuffix = GenericJdbcConnector.getRowLimitSuffix(driverName);
        } else {
            rowLimitSuffix = null;
        }
        if (rowLimitSuffix != null) {
            return Utils.substituteTokens(rowLimitSuffix, Collections.singletonMap("row_limit", String.valueOf(rowLimit)));
        }
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
