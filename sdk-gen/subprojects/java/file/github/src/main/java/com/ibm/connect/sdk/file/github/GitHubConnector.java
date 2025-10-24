/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.github;

import static org.slf4j.LoggerFactory.getLogger;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.arrow.flight.Ticket;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ibm.connect.sdk.file.FileConnector;
import com.ibm.connect.sdk.file.FileSourceInteraction;
import com.ibm.connect.sdk.file.FileTargetInteraction;
import com.ibm.connect.sdk.file.FileUtils;
import com.ibm.connect.sdk.util.SSLUtils;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionActionConfiguration;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionActionResponse;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetsCriteria;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveredAssetDetails;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveredAssetInteractionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveredAssetType;

/**
 * A connector for connecting to GitHub.
 */
public class GitHubConnector extends FileConnector
{
    private static final Logger LOGGER = getLogger(GitHubConnector.class);

    private static final int HTTP_CONNECTION_SOCKET_TIMEOUT = 50 * 1000;
    private static final int HTTP_CONNECTION_READ_TIMEOUT = 50 * 1000;

    private static final String HTTPS_PREFIX = "https://";
    private static final String API_HOST_PREFIX = "api.";
    private static final String REPOS_ENDPOINT = "/repos/";
    private static final String TOPICS_ENDPOINT = "/topics";
    private static final String BRANCHES_ENDPOINT = "/branches";
    private static final String CONTENTS_ENDPOINT = "/contents";
    private static final String BEARER_PREFIX = "Bearer ";

    private final String baseUrl;
    private final String authHeader;
    private CloseableHttpClient httpClient;

    /**
     * Creates a GitHub connector.
     *
     * @param properties
     *            connection properties
     */
    public GitHubConnector(ConnectionProperties properties)
    {
        super(properties);
        final Properties connectionProperties = getConnectionProperties();
        if (connectionProperties.getProperty("host") == null) {
            throw new IllegalArgumentException(GitHubMsgs.MISSING_PROPERTY.format("host"));
        }
        if (connectionProperties.getProperty("repository_owner") == null) {
            throw new IllegalArgumentException(GitHubMsgs.MISSING_PROPERTY.format("repository_owner"));
        }
        if (connectionProperties.getProperty("repository_name") == null) {
            throw new IllegalArgumentException(GitHubMsgs.MISSING_PROPERTY.format("repository_name"));
        }

        // Build the base URL.
        final StringBuilder builder = new StringBuilder(100);
        builder.append(HTTPS_PREFIX);
        final String host = connectionProperties.getProperty("host");
        if (!host.startsWith(API_HOST_PREFIX)) {
            builder.append(API_HOST_PREFIX);
        }
        builder.append(host);
        builder.append(REPOS_ENDPOINT);
        builder.append(connectionProperties.getProperty("repository_owner"));
        builder.append('/');
        builder.append(connectionProperties.getProperty("repository_name"));
        baseUrl = builder.toString();

        // Build the authorization header.
        final String accessToken = connectionProperties.getProperty("access_token");
        authHeader = accessToken != null ? BEARER_PREFIX + accessToken : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void connect() throws Exception
    {
        // If the client is not null, then we're reusing a pooled connection.
        if (httpClient == null) {
            httpClient = createHttpClient();
            executeHttpGet(baseUrl + TOPICS_ENDPOINT);
        }
    }

    private CloseableHttpClient createHttpClient() throws Exception
    {
        final HttpClientBuilder builder = HttpClientBuilder.create().useSystemProperties();
        builder.setSSLContext(SSLUtils.buildSSLContext(null));
        builder.setDefaultRequestConfig(RequestConfig.custom().setNormalizeUri(false).setConnectTimeout(HTTP_CONNECTION_SOCKET_TIMEOUT)
                .setSocketTimeout(HTTP_CONNECTION_READ_TIMEOUT).build());
        return builder.build();
    }

    private String executeHttpGet(String endpoint) throws Exception
    {
        final HttpGet request = new HttpGet(endpoint);
        request.addHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.toString());
        if (authHeader != null) {
            request.addHeader(HttpHeaders.AUTHORIZATION, authHeader);
        }
        LOGGER.info("Executing HTTP request " + request);
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            final HttpEntity entity = response.getEntity();
            if (entity == null) {
                return null;
            }
            final String responseString = EntityUtils.toString(entity);
            EntityUtils.consume(entity);
            final int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != HttpStatus.SC_OK) {
                throw new Exception(responseString != null ? responseString : response.toString());
            }
            return responseString;
        }
    }

    protected CloseableHttpResponse downloadFile(String endpoint) throws Exception
    {
        final HttpGet request = new HttpGet(endpoint);
        request.addHeader(HttpHeaders.ACCEPT, "application/vnd.github.raw+json");
        if (authHeader != null) {
            request.addHeader(HttpHeaders.AUTHORIZATION, authHeader);
        }
        LOGGER.info("Executing HTTP request " + request);
        final CloseableHttpResponse response = httpClient.execute(request);
        final int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode != HttpStatus.SC_OK) {
            throw new Exception(response.toString());
        }
        return response;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CustomFlightAssetDescriptor> discoverAssets(CustomFlightAssetsCriteria criteria) throws Exception
    {
        final String path = normalizePath(criteria.getPath());
        final String[] pathElements = splitPath(path);
        final List<CustomFlightAssetDescriptor> assets;
        final String branch = getConnectionProperties().getProperty("branch");
        if (pathElements.length == 0) {
            assets = branch == null ? listBranches(criteria) : listFiles(criteria, branch, null);
        } else if (pathElements.length == 1) {
            if (branch != null && !branch.equals(pathElements[0])) {
                assets = listFiles(criteria, branch, pathElements[0]);
            } else {
                assets = listFiles(criteria, pathElements[0], null);
            }
        } else if (pathElements.length == 2) {
            if (branch != null && !branch.equals(pathElements[0])) {
                assets = listFiles(criteria, branch, pathElements[0] + '/' + pathElements[1]);
            } else {
                assets = listFiles(criteria, pathElements[0], pathElements[1]);
            }
        } else {
            throw new IllegalArgumentException(GitHubMsgs.INVALID_PATH.format(path));
        }
        return assets;
    }

    private String normalizePath(String path)
    {
        return path.startsWith("/") ? path : "/" + path;
    }

    private String[] splitPath(String path)
    {
        if ("/".equals(path)) {
            return new String[0];
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        return path.split("/", 2);
    }

    private List<CustomFlightAssetDescriptor> listBranches(CustomFlightAssetsCriteria criteria) throws Exception
    {
        final List<CustomFlightAssetDescriptor> descriptors = new ArrayList<>();
        final String responseString = executeHttpGet(baseUrl + BRANCHES_ENDPOINT);
        final JsonArray response = new Gson().fromJson(responseString, JsonArray.class);
        final int offset = criteria.getOffset() == null || criteria.getOffset() < 0 ? 0 : criteria.getOffset();
        final int limit = criteria.getLimit() == null || criteria.getLimit() < 0 ? response.size() : criteria.getLimit();
        final int endIndex = offset + limit > response.size() ? response.size() : offset + limit;
        for (int i = offset; i < endIndex; i++) {
            final String branchName = response.get(i).getAsJsonObject().get("name").getAsString();
            final String path = "/" + branchName;
            descriptors.add(new CustomFlightAssetDescriptor().name(branchName).path(path).assetType(branchAssetType()));
        }
        return descriptors;
    }

    private DiscoveredAssetType branchAssetType()
    {
        return new DiscoveredAssetType().type("branch").dataset(false).datasetContainer(true);
    }

    private List<CustomFlightAssetDescriptor> listFiles(CustomFlightAssetsCriteria criteria, String branch, String filePath)
            throws Exception
    {
        final JsonElement contentElement = getRepositoryContent(branch, filePath);
        final List<CustomFlightAssetDescriptor> descriptors = new ArrayList<>();
        if (contentElement.isJsonArray()) {
            final JsonArray response = contentElement.getAsJsonArray();
            final int offset = criteria.getOffset() == null || criteria.getOffset() < 0 ? 0 : criteria.getOffset();
            final int limit = criteria.getLimit() == null || criteria.getLimit() < 0 ? response.size() : criteria.getLimit();
            final int endIndex = offset + limit > response.size() ? response.size() : offset + limit;
            for (int i = offset; i < endIndex; i++) {
                final JsonObject fileObject = response.get(i).getAsJsonObject();
                final CustomFlightAssetDescriptor asset = createAssetDescriptor(branch, fileObject, false);
                if (asset != null) {
                    descriptors.add(asset);
                }
            }
        } else {
            final CustomFlightAssetDescriptor asset = createAssetDescriptor(branch, contentElement.getAsJsonObject(), true);
            if (asset != null) {
                descriptors.add(asset);
            }
        }
        return descriptors;
    }

    protected JsonElement getRepositoryContent(String branch, String filePath) throws Exception
    {
        // Build the request URL.
        final StringBuilder builder = new StringBuilder(100);
        builder.append(baseUrl);
        builder.append(CONTENTS_ENDPOINT);
        if (filePath != null) {
            builder.append('/');
            builder.append(filePath);
        }
        builder.append("?ref=");
        builder.append(branch);

        final String responseString = executeHttpGet(builder.toString());
        return new Gson().fromJson(responseString, JsonElement.class);
    }

    private CustomFlightAssetDescriptor createAssetDescriptor(String branch, JsonObject fileObject, boolean describeInteraction)
            throws Exception
    {
        final String fileName = fileObject.get("name").getAsString();
        final String filePath = fileObject.get("path").getAsString();
        final String fileType = fileObject.get("type").getAsString();
        final String path = "/" + branch + "/" + filePath;
        if ("dir".equals(fileType)) {
            return new CustomFlightAssetDescriptor().name(fileName).path(path).assetType(folderAssetType());
        }
        if ("file".equals(fileType)) {
            final CustomFlightAssetDescriptor asset
                    = new CustomFlightAssetDescriptor().name(fileName).path(path).assetType(fileAssetType());

            // Add details like file_size that can be acquired directly from the JSON
            // object.
            addObjectDetails(asset, fileObject);

            if (describeInteraction) {
                final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
                interactionProperties.put("branch_name", branch);
                interactionProperties.put("file_name", filePath);
                asset.setInteractionProperties(interactionProperties);

                // Add details that require downloading and examining the file content.
                addFileDetails(asset, fileObject);
            }
            return asset;
        }
        return null;
    }

    private DiscoveredAssetType folderAssetType()
    {
        return new DiscoveredAssetType().type("folder").dataset(false).datasetContainer(true);
    }

    private DiscoveredAssetType fileAssetType()
    {
        return new DiscoveredAssetType().type("file").dataset(true).datasetContainer(false);
    }

    protected void addObjectDetails(CustomFlightAssetDescriptor asset, JsonObject fileObject)
    {
        final long fileSize = fileObject.get("size").getAsLong();
        final DiscoveredAssetDetails details = new DiscoveredAssetDetails();
        details.put("file_size", fileSize);
        asset.setDetails(details);
    }

    protected void addFileDetails(CustomFlightAssetDescriptor asset, JsonObject fileObject) throws Exception
    {
        // Download the file to detect mime_type and file_format.
        final String fileName = fileObject.get("name").getAsString();
        final String downloadUrl = fileObject.get("download_url").getAsString();
        try (CloseableHttpResponse response = downloadFile(downloadUrl)) {
            if (response.getEntity() != null) {
                try (InputStream downloadStream = FileUtils.ensureMarkSupported(response.getEntity().getContent())) {
                    // Detect mime type.
                    final String mimeType = FileUtils.detectMimeType(downloadStream, fileName);
                    if (mimeType != null) {
                        asset.getDetails().put("mime_type", mimeType);
                    }

                    // Detect file format.
                    final String detectedFileFormat = FileUtils.detectFileFormat(mimeType, fileName, downloadStream);
                    final String fileFormat = detectedFileFormat != null ? detectedFileFormat : FileUtils.FILE_FORMAT_DELIMITED;
                    asset.getInteractionProperties().put("file_format", fileFormat);

                    // Detect delimited file properties.
                    if (FileUtils.FILE_FORMAT_CSV.equals(fileFormat) || FileUtils.FILE_FORMAT_DELIMITED.equals(fileFormat)) {
                        FileUtils.detectDelimitedProperties(downloadStream, asset.getInteractionProperties());
                    }

                    // Describe fields.
                    final String tempFilename = FileUtils.createTempFile(downloadStream, fileName, fileFormat);
                    try {
                        addAssetFields(asset, tempFilename);
                    }
                    finally {
                        FileUtils.deleteTempFile(tempFilename);
                    }
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FileSourceInteraction getSourceInteraction(CustomFlightAssetDescriptor asset, Ticket ticket) throws Exception
    {
        return new GitHubSourceInteraction(this, asset, ticket);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FileTargetInteraction getTargetInteraction(CustomFlightAssetDescriptor asset) throws Exception
    {
        throw new UnsupportedOperationException(
                GitHubMsgs.TARGET_INTERACTION_NOT_SUPPORTED.format(GitHubDatasourceType.DATASOURCE_TYPE_NAME));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConnectionActionResponse performAction(String action, ConnectionActionConfiguration properties) throws Exception
    {
        throw new UnsupportedOperationException(GitHubMsgs.UNSUPPORTED_ACTION.format(action));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void commit()
    {
        // Do nothing.
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws Exception
    {
        super.close();
        try {
            if (httpClient != null) {
                httpClient.close();
            }
        }
        finally {
            httpClient = null;
        }
    }
}
