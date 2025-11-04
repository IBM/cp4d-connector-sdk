/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.localfs;

import java.nio.file.Path;

import com.ibm.connect.sdk.file.FileMsgs;
import com.ibm.connect.sdk.file.FileTargetInteraction;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;

/**
 * An interaction with a local file system asset as a target.
 */
public class LocalFSTargetInteraction extends FileTargetInteraction
{
    private final Path filePath;

    /**
     * Creates a local file system target interaction.
     *
     * @param connector
     *            the connector managing the connection to the data source
     * @param asset
     *            the asset to which to write
     * @throws Exception
     */
    public LocalFSTargetInteraction(LocalFSConnector connector, CustomFlightAssetDescriptor asset) throws Exception
    {
        super(connector, asset);

        final String fileName = getInteractionProperties().getProperty("file_name");
        if (fileName == null) {
            throw new IllegalArgumentException(FileMsgs.MISSING_PROPERTY.format("file_name"));
        }
        filePath = connector.resolvePath(fileName);
    }

    /**
     * Return the name of the file asset on the local file system that spark can write to.
     *
     * @return the name of the file asset on the local file system
     */
    @Override
    protected String getFilename()
    {
        return filePath.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CustomFlightAssetDescriptor putSetup() throws Exception
    {
        return getAsset();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CustomFlightAssetDescriptor putWrapup() throws Exception
    {
        return getAsset();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws Exception
    {
        // Do nothing.
    }

}
