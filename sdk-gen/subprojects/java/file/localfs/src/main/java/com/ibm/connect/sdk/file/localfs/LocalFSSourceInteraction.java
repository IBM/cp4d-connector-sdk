/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.localfs;

import java.nio.file.Path;
import java.util.Properties;

import org.apache.arrow.flight.Ticket;

import com.ibm.connect.sdk.file.FileMsgs;
import com.ibm.connect.sdk.file.FileSourceInteraction;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;

/**
 * An interaction with a local file system asset as a source.
 */
public class LocalFSSourceInteraction extends FileSourceInteraction
{
    private final Path filePath;

    /**
     * Creates a local file system source interaction.
     *
     * @param connector
     *            the connector managing the connection to the data source
     * @param asset
     *            the asset from which to read
     * @param ticket
     *            a Flight ticket to read a partition or null to get tickets
     * @throws Exception
     */
    public LocalFSSourceInteraction(LocalFSConnector connector, CustomFlightAssetDescriptor asset, Ticket ticket) throws Exception
    {
        super(connector, asset, ticket);
        final Properties interactionProperties = getInteractionProperties();

        final String fileName = interactionProperties.getProperty("file_name");
        if (fileName == null) {
            throw new IllegalArgumentException(FileMsgs.MISSING_PROPERTY.format("file_name"));
        }
        filePath = connector.resolvePath(fileName);

        // If the file format or fields are missing, we need to examine the file to get
        // that information.
        if (interactionProperties.getProperty("file_format") == null || asset.getFields() == null) {
            if (!filePath.toFile().isFile()) {
                throw new IllegalArgumentException(FileMsgs.NOT_A_FILE.format(fileName));
            }

            // Add details like file_size that can be acquired directly from the file information.
            connector.addObjectDetails(asset, filePath);

            // Add details that require examining the file content.
            connector.addFileDetails(asset, filePath);
        }
    }

    /**
     * Return the name of the file asset on the local file system so that spark can read it.
     *
     * @return the name of the file asset on the local file system
     */
    @Override
    protected String getFilename()
    {
        return filePath.toString();
    }
}
