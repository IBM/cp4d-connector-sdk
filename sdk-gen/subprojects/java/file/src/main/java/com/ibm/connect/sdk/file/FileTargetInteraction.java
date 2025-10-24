/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file;

import java.util.Properties;

import com.ibm.connect.sdk.api.RowBasedTargetInteraction;
import com.ibm.connect.sdk.util.ModelMapper;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;

/**
 * An interaction with a file asset as a target.
 */
public abstract class FileTargetInteraction extends RowBasedTargetInteraction<FileConnector>
{
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
}
