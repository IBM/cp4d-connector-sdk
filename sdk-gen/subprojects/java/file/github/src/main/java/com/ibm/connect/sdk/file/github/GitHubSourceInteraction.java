/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.github;

import java.io.InputStream;
import java.util.Properties;

import org.apache.arrow.flight.Ticket;
import org.apache.http.client.methods.CloseableHttpResponse;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ibm.connect.sdk.file.FileSourceInteraction;
import com.ibm.connect.sdk.file.FileUtils;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;

/**
 * An interaction with a GitHub asset as a source.
 */
public class GitHubSourceInteraction extends FileSourceInteraction
{
    private final GitHubConnector connector;
    private final String branch;
    private final String filePath;

    private String tempFilename;

    /**
     * Creates a GitHub source interaction.
     *
     * @param connector
     *            the connector managing the connection to the data source
     * @param asset
     *            the asset from which to read
     * @param ticket
     *            a Flight ticket to read a partition or null to get tickets
     * @throws Exception
     */
    public GitHubSourceInteraction(GitHubConnector connector, CustomFlightAssetDescriptor asset, Ticket ticket) throws Exception
    {
        super(connector, asset, ticket);
        this.connector = connector;
        final Properties connectionProperties = connector.getConnectionProperties();
        final Properties interactionProperties = getInteractionProperties();

        // If the connection is scoped to a particular branch, then the interaction is
        // not allowed to choose a different branch.
        final String connectionBranch = connectionProperties.getProperty("branch_name");
        branch = interactionProperties.getProperty("branch_name", connectionBranch);
        if (branch == null) {
            throw new IllegalArgumentException(GitHubMsgs.MISSING_PROPERTY.format("branch_name"));
        }
        if (connectionBranch != null && !connectionBranch.equals(branch)) {
            throw new IllegalArgumentException(GitHubMsgs.REQUIRED_PROPERTY_VALUE.format("branch_name", connectionBranch, branch));
        }
        filePath = interactionProperties.getProperty("file_name");
        if (filePath == null) {
            throw new IllegalArgumentException(GitHubMsgs.MISSING_PROPERTY.format("file_name"));
        }

        // If the file format or fields are missing, we need to download the file to get
        // that information.
        if (interactionProperties.getProperty("file_format") == null || asset.getFields() == null) {
            final JsonElement contentElement = connector.getRepositoryContent(branch, filePath);
            if (!contentElement.isJsonObject()) {
                throw new IllegalArgumentException(GitHubMsgs.NOT_A_FILE.format(filePath));
            }
            final JsonObject fileObject = contentElement.getAsJsonObject();
            if (!"file".equals(fileObject.get("type").getAsString())) {
                throw new IllegalArgumentException(GitHubMsgs.NOT_A_FILE.format(filePath));
            }

            // Add details like file_size that can be acquired directly from the JSON
            // object.
            connector.addObjectDetails(asset, fileObject);

            // Add details that require downloading and examining the file content.
            connector.addFileDetails(asset, fileObject);
        }
    }

    /**
     * Download the file to the local file system so that spark can read it.
     *
     * @return the name of the file asset on the local file system
     */
    @Override
    protected String getFilename()
    {
        try {
            final JsonObject fileObject = connector.getRepositoryContent(branch, filePath).getAsJsonObject();
            final String fileName = fileObject.get("name").getAsString();
            final String downloadUrl = fileObject.get("download_url").getAsString();
            try (CloseableHttpResponse response = connector.downloadFile(downloadUrl)) {
                try (InputStream downloadStream = response.getEntity().getContent()) {
                    tempFilename
                            = FileUtils.createTempFile(downloadStream, fileName, getInteractionProperties().getProperty("file_format"));
                }
            }
        }
        catch (Exception e) {
            throw new UnsupportedOperationException(e.getMessage(), e);
        }
        return tempFilename;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws Exception
    {
        super.close();
        if (tempFilename != null) {
            FileUtils.deleteTempFile(tempFilename);
        }
    }
}
