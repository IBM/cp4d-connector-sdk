/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.jdbc.derby;

import org.apache.arrow.flight.Ticket;

import com.ibm.connect.sdk.api.TicketInfo;
import com.ibm.connect.sdk.jdbc.AssetFieldType;
import com.ibm.connect.sdk.jdbc.JdbcSourceInteraction;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetField;

/**
 * An interaction with an Apache Derby asset as a source.
 */
public class DerbySourceInteraction extends JdbcSourceInteraction
{
    private final DerbyConnector connector;
    private final CustomFlightAssetDescriptor asset;

    /**
     * Creates an Apache Derby source interaction.
     *
     * @param connector
     *            the connector managing the connection to the data source
     * @param asset
     *            the asset from which to read
     * @param ticket
     *            a Flight ticket to read a partition or null to get tickets
     * @throws Exception
     */
    public DerbySourceInteraction(DerbyConnector connector, CustomFlightAssetDescriptor asset, Ticket ticket) throws Exception
    {
        super(connector, asset, ticket);
        this.connector = connector;
        this.asset = asset;
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
        return "FETCH FIRST " + rowLimit + " ROWS ONLY";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean isPartitioningSupported()
    {
        return findPartitioningField() != null;
    }

    private CustomFlightAssetField findPartitioningField()
    {
        if (asset.getPartitionCount() != null && asset.getPartitionCount() > 1) {
            for (final CustomFlightAssetField assetField : asset.getFields()) {
                if (AssetFieldType.isNumeric(AssetFieldType.getFieldType(assetField.getType()))) {
                    return assetField;
                }
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getPartitioningPredicate(TicketInfo partitionInfo)
    {
        final CustomFlightAssetField partitioningField = findPartitioningField();
        if (partitioningField != null) {
            final String partitioningColumn = connector.getIdentifierQuote() + partitioningField.getName() + connector.getIdentifierQuote();
            String partitioningScalar = "ABS(BIGINT(" + partitioningColumn + "))";
            if (partitioningField.isNullable()) {
                partitioningScalar = "COALESCE(" + partitioningScalar + ", 0)";
            }
            return "MOD(" + partitioningScalar + ',' + asset.getPartitionCount() + ") = " + partitionInfo.getPartitionIndex();
        }
        return null;
    }
}
