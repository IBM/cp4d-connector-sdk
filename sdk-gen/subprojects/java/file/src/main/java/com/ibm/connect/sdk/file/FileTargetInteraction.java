/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file;

import java.util.Iterator;
import java.util.Properties;

import com.ibm.connect.sdk.api.Record;
import com.ibm.connect.sdk.api.RowBasedTargetInteraction;
import com.ibm.connect.sdk.util.ModelMapper;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;

/**
 * An interaction with a file asset as a target.
 */
public abstract class FileTargetInteraction extends RowBasedTargetInteraction<FileConnector>
{
    private final static int DEFAULT_BATCH_SIZE = 1000;

    private final Properties interactionProperties;

    /**
     * Creates a file target interaction.
     *
     * @param connector
     *            the connector managing the connection to the data source
     * @param asset
     *            the asset to which to write
     * @throws Exception
     */
    public FileTargetInteraction(FileConnector connector, CustomFlightAssetDescriptor asset) throws Exception
    {
        super();
        setConnector(connector);
        setAsset(asset);
        interactionProperties = ModelMapper.toProperties(asset.getInteractionProperties());
        if (asset.getBatchSize() == null) {
            asset.setBatchSize(DEFAULT_BATCH_SIZE);
        }
    }

    /**
     * Returns the interaction properties.
     *
     * @return the interaction properties
     */
    public Properties getInteractionProperties()
    {
        return interactionProperties;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("PMD.EmptyMethodInAbstractClassShouldBeAbstract")
    public void putRecord(Record record)
    {
        // Connectors that extend this class do not need to implement this method. This
        // method does nothing, as the row processing is handled by putAll.
    }

    /**
     * Returns the name of the file asset that is accessible by Spark.
     *
     * @return the name of the file asset that is accessible by Spark
     */
    protected abstract String getFilename();

    @Override
    public void putAll(Iterator<Record> records)
    {
        getConnector().putRows(getAsset(), new FileRowList(records), getFilename());
    }
}
