/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2026                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.box;

import static org.slf4j.LoggerFactory.getLogger;

import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.arrow.flight.Ticket;
import org.slf4j.Logger;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxCollaboration;
import com.box.sdk.BoxCollaborator;
import com.box.sdk.BoxConfig;
import com.box.sdk.BoxDeveloperEditionAPIConnection;
import com.box.sdk.BoxFile;
import com.box.sdk.BoxFolder;
import com.box.sdk.BoxItem;
import com.box.sdk.BoxUser;
import com.google.common.collect.ImmutableList;
import com.ibm.connect.sdk.file.FileConnector;
import com.ibm.connect.sdk.file.FileMsgs;
import com.ibm.connect.sdk.file.FileSourceInteraction;
import com.ibm.connect.sdk.file.FileTargetInteraction;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionActionConfiguration;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionActionResponse;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetsCriteria;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveredAssetDetails;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveredAssetInteractionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveredAssetType;

/**
 * A connector for connecting to Box.
 */
public class BoxConnector extends FileConnector
{
    private static final Logger LOGGER = getLogger(BoxConnector.class);

    /**
     * ACL action name.
     */
    static final String ACTION_GET_ACL = "get_acl";

    /**
     * File metadata action name.
     */
    static final String ACTION_GET_FILE_METADATA = "get_file_metadata";

    /**
     * Input path property name for the ACL and file metadata actions.
     */
    static final String ACTION_PATH_PROP = "path";

    private BoxAPIConnection api;

    /**
     * Creates a Box connector.
     *
     * @param properties
     *            connection properties
     */
    public BoxConnector(ConnectionProperties properties)
    {
        super(properties);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void connect() throws Exception
    {
        // If the api is not null, then we're reusing a pooled connection.
        if (api == null) {
            final Properties connectionProperties = getConnectionProperties();
            final String clientId = getRequiredProperty(connectionProperties, "client_id");
            final String clientSecret = getRequiredProperty(connectionProperties, "client_secret");
            final String enterpriseId = getRequiredProperty(connectionProperties, "enterprise_id");
            final String privateKey = getRequiredProperty(connectionProperties, "private_key");
            final String privateKeyPassword = connectionProperties.getProperty("private_key_password", "");
            final String publicKey = getRequiredProperty(connectionProperties, "public_key");
            final String username = connectionProperties.getProperty("username");

            final BoxConfig boxConfig = new BoxConfig(clientId, clientSecret, enterpriseId, publicKey, privateKey,
                    privateKeyPassword);

            // getAppEnterpriseConnection() and getUserConnection() both authenticate
            // eagerly (JWT token request), so bad credentials throw here. The result is
            // held in a local and only assigned to the field once validated, so a failed
            // connect() does not leave a non-null api that would skip validation on the
            // next connect() call.
            final BoxAPIConnection newApi;
            if (username != null && !username.isEmpty()) {
                // Resolve the email to a Box user ID using the enterprise connection.
                final BoxAPIConnection enterpriseApi = BoxDeveloperEditionAPIConnection.getAppEnterpriseConnection(boxConfig);
                final String userId = resolveUserId(enterpriseApi, username);
                newApi = BoxDeveloperEditionAPIConnection.getUserConnection(userId, boxConfig);
                LOGGER.info("Connected to Box as user " + username + " (id=" + userId + ")");
            } else {
                newApi = BoxDeveloperEditionAPIConnection.getAppEnterpriseConnection(boxConfig);
                LOGGER.info("Connected to Box enterprise " + enterpriseId);
            }
            api = newApi;
        }
    }

    /**
     * Resolves a Box user login (email) to a Box user ID.
     *
     * @param enterpriseApi
     *            an enterprise-level API connection used for the user lookup
     * @param login
     *            the email/login of the user to find
     * @return the Box user ID
     * @throws IllegalArgumentException
     *             if no user with the given login is found
     */
    private static String resolveUserId(BoxAPIConnection enterpriseApi, String login)
    {
        for (final BoxUser.Info userInfo : BoxUser.getAllEnterpriseUsers(enterpriseApi, login)) {
            if (login.equalsIgnoreCase(userInfo.getLogin())) {
                return userInfo.getID();
            }
        }
        throw new IllegalArgumentException("No Box user found with login: " + login);
    }

    /**
     * Returns the value of a required connection property, throwing an
     * {@link IllegalArgumentException} if it is absent.
     */
    private static String getRequiredProperty(Properties properties, String name)
    {
        final String value = properties.getProperty(name);
        if (value == null) {
            throw new IllegalArgumentException(FileMsgs.MISSING_PROPERTY.format(name));
        }
        return value;
    }

    /**
     * Resolves a slash-delimited file path to the corresponding Box folder and
     * returns the file ID for the final path segment.
     *
     * <p>
     * The path may start with a leading "/" which is stripped before processing.
     * Each intermediate segment is resolved as a sub-folder; the last segment is
     * matched as a file.
     *
     * @param filePath
     *            slash-delimited path (e.g. {@code "folder/subfolder/file.txt"} or
     *            {@code "/folder/file.txt"})
     * @return the {@link BoxFile} for the resolved path
     * @throws IllegalArgumentException
     *             if any folder or the file is not found
     */
    BoxFile resolveFile(String filePath) throws Exception
    {
        // Strip leading slash.
        final String normalized = filePath.startsWith("/") ? filePath.substring(1) : filePath;
        final String[] parts = normalized.split("/");
        BoxFolder currentFolder = BoxFolder.getRootFolder(api);

        // Traverse intermediate folder segments.
        for (int i = 0; i < parts.length - 1; i++) {
            final String folderName = parts[i];
            BoxFolder nextFolder = null;
            for (final BoxItem.Info itemInfo : currentFolder.getChildren("name", "type")) {
                if (itemInfo instanceof BoxFolder.Info && folderName.equals(itemInfo.getName())) {
                    nextFolder = new BoxFolder(api, itemInfo.getID());
                    break;
                }
            }
            if (nextFolder == null) {
                throw new IllegalArgumentException(BoxMsgs.FILE_NOT_FOUND.format(filePath));
            }
            currentFolder = nextFolder;
        }

        // Find the file in the resolved folder.
        final String fileName = parts[parts.length - 1];
        for (final BoxItem.Info itemInfo : currentFolder.getChildren("name", "type")) {
            if (itemInfo instanceof BoxFile.Info && fileName.equals(itemInfo.getName())) {
                return new BoxFile(api, itemInfo.getID());
            }
        }
        throw new IllegalArgumentException(BoxMsgs.FILE_NOT_FOUND.format(filePath));
    }

    /**
     * Resolves a slash-delimited folder path to the corresponding {@link BoxFolder}.
     *
     * @param folderPath
     *            slash-delimited path (e.g. {@code "/folder"} or {@code ""} for root)
     * @return the resolved BoxFolder
     */
    private BoxFolder resolveFolder(String folderPath)
    {
        final String normalized = (folderPath == null || folderPath.equals("/")) ? "" : (folderPath.startsWith("/") ? folderPath.substring(1) : folderPath);
        BoxFolder current = BoxFolder.getRootFolder(api);
        if (normalized.isEmpty()) {
            return current;
        }
        for (final String segment : normalized.split("/")) {
            BoxFolder next = null;
            for (final BoxItem.Info itemInfo : current.getChildren("name", "type")) {
                if (itemInfo instanceof BoxFolder.Info && segment.equals(itemInfo.getName())) {
                    next = new BoxFolder(api, itemInfo.getID());
                    break;
                }
            }
            if (next == null) {
                throw new IllegalArgumentException(BoxMsgs.FOLDER_NOT_FOUND.format(folderPath));
            }
            current = next;
        }
        return current;
    }

    /**
     * Opens an InputStream over the content of a Box file identified by path.
     * The download runs in a background thread writing to a {@link PipedOutputStream};
     * the returned {@link PipedInputStream} is the readable end of the pipe.
     *
     * <p>The caller is responsible for closing the returned stream.
     *
     * @param filePath
     *            slash-delimited path to the file
     * @return an InputStream over the file content
     * @throws Exception
     */
    InputStream openFile(String filePath) throws Exception
    {
        final BoxFile boxFile = resolveFile(filePath);
        final PipedOutputStream pipeOut = new PipedOutputStream();
        final PipedInputStream pipeIn = new PipedInputStream(pipeOut, 65536);

        final Thread downloadThread = new Thread(() -> {
            try {
                boxFile.download(pipeOut);
            }
            catch (Exception e) {
                LOGGER.warn("Box download thread failed for '" + filePath + "': " + e.getMessage(), e);
            }
            finally {
                try {
                    pipeOut.close();
                }
                catch (Exception ignored) {
                    // Nothing to do.
                }
            }
        }, "box-download-" + filePath);
        downloadThread.setDaemon(true);
        downloadThread.start();

        return pipeIn;
    }

    /**
     * Returns the file size (in bytes) for a Box file identified by path.
     *
     * @param filePath
     *            slash-delimited path to the file
     * @return size in bytes
     * @throws Exception
     */
    long getFileSize(String filePath) throws Exception
    {
        final BoxFile boxFile = resolveFile(filePath);
        return boxFile.getInfo("size").getSize();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns folders and files at the path specified in {@code criteria.getPath()}.
     */
    @Override
    public List<CustomFlightAssetDescriptor> discoverAssets(CustomFlightAssetsCriteria criteria) throws Exception
    {
        final String path = criteria.getPath();
        final int offset = criteria.getOffset() == null || criteria.getOffset() < 0 ? 0 : criteria.getOffset();
        final int limit = criteria.getLimit() == null || criteria.getLimit() < 0 ? Integer.MAX_VALUE : criteria.getLimit();

        final BoxFolder folder = resolveFolder(path);
        final List<CustomFlightAssetDescriptor> descriptors = new ArrayList<>();
        int totalSeen = 0;
        int added = 0;

        for (final BoxItem.Info itemInfo : folder.getChildren("name", "type", "size", "modified_at")) {
            totalSeen++;
            if (totalSeen <= offset) {
                continue;
            }
            if (added >= limit) {
                break;
            }
            final String itemName = itemInfo.getName();
            // Build canonical asset path.
            final String normalized = (path == null || path.equals("/")) ? "" : (path.startsWith("/") ? path : "/" + path);
            final String assetPath = normalized.isEmpty() ? "/" + itemName : normalized + "/" + itemName;

            if (itemInfo instanceof BoxFolder.Info) {
                descriptors.add(new CustomFlightAssetDescriptor()
                        .name(itemName)
                        .path(assetPath)
                        .assetType(folderAssetType()));
            } else if (itemInfo instanceof BoxFile.Info) {
                final DiscoveredAssetDetails details = new DiscoveredAssetDetails();
                details.put("file_size", ((BoxFile.Info) itemInfo).getSize());
                final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
                interactionProperties.put("file_name", assetPath);
                descriptors.add(new CustomFlightAssetDescriptor()
                        .name(itemName)
                        .path(assetPath)
                        .assetType(fileAssetType())
                        .details(details)
                        .interactionProperties(interactionProperties));
            }
            added++;
        }
        return descriptors;
    }

    private DiscoveredAssetType folderAssetType()
    {
        return new DiscoveredAssetType().type("folder").dataset(false).datasetContainer(true);
    }

    private DiscoveredAssetType fileAssetType()
    {
        return new DiscoveredAssetType().type("file").dataset(true).datasetContainer(false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FileSourceInteraction getSourceInteraction(CustomFlightAssetDescriptor asset, Ticket ticket) throws Exception
    {
        return new BoxSourceInteraction(this, asset, ticket);
    }

    /**
     * Uploads an {@link InputStream} to Box as the file identified by
     * {@code filePath}. If a file with the same name already exists in the
     * resolved folder it is replaced (new version); otherwise a new file is
     * created.
     *
     * <p>The path may contain intermediate folder segments
     * (e.g. {@code "folder1/folder2/file.txt"}) which are traversed (or created
     * when absent) before the upload.
     *
     * @param filePath
     *            slash-delimited destination path
     * @param content
     *            the data to upload; the stream is not closed by this method
     * @throws Exception
     */
    void uploadFile(String filePath, InputStream content) throws Exception
    {
        // Strip leading slash.
        final String normalized = filePath.startsWith("/") ? filePath.substring(1) : filePath;
        final String[] parts = normalized.split("/");
        final String targetFileName = parts[parts.length - 1];

        // Resolve (or create) all intermediate folder segments.
        BoxFolder currentFolder = BoxFolder.getRootFolder(api);
        for (int i = 0; i < parts.length - 1; i++) {
            final String folderName = parts[i];
            BoxFolder next = null;
            for (final BoxItem.Info itemInfo : currentFolder.getChildren("name", "type")) {
                if (itemInfo instanceof BoxFolder.Info && folderName.equals(itemInfo.getName())) {
                    next = new BoxFolder(api, itemInfo.getID());
                    break;
                }
            }
            if (next == null) {
                next = currentFolder.createFolder(folderName).getResource();
            }
            currentFolder = next;
        }

        // Check whether the file already exists in the resolved folder.
        BoxFile existingFile = null;
        for (final BoxItem.Info itemInfo : currentFolder.getChildren("name", "type")) {
            if (itemInfo instanceof BoxFile.Info && targetFileName.equals(itemInfo.getName())) {
                existingFile = new BoxFile(api, itemInfo.getID());
                break;
            }
        }

        if (existingFile != null) {
            existingFile.uploadNewVersion(content);
            LOGGER.info("Uploaded new version of '" + filePath + "'");
        } else {
            currentFolder.uploadFile(content, targetFileName);
            LOGGER.info("Uploaded new file '" + filePath + "'");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FileTargetInteraction getTargetInteraction(CustomFlightAssetDescriptor asset) throws Exception
    {
        return new BoxTargetInteraction(this, asset);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Handles the {@code get_acl} and {@code get_file_metadata} actions.
     */
    @Override
    public ConnectionActionResponse performAction(String action, ConnectionActionConfiguration properties) throws Exception
    {
        if (!ACTION_GET_FILE_METADATA.equals(action) && !ACTION_GET_ACL.equals(action)) {
            throw new UnsupportedOperationException(FileMsgs.UNSUPPORTED_ACTION.format(action));
        }

        final Properties inputProperties = com.ibm.connect.sdk.util.ModelMapper.toProperties(properties);
        final String path = inputProperties.getProperty(ACTION_PATH_PROP);
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException(FileMsgs.MISSING_PROPERTY.format(ACTION_PATH_PROP));
        }

        if (ACTION_GET_FILE_METADATA.equals(action)) {
            final BoxFile boxFile = resolveFile(path);
            final BoxFile.Info info = boxFile.getInfo("size", "modified_at");
            final ConnectionActionResponse response = new ConnectionActionResponse();
            response.put("last_modified", info.getModifiedAt().toInstant().toString());
            response.put("size", info.getSize());
            return response;
        }
        return performGetAcl(path);
    }

    /**
     * Executes the {@code get_acl} action for a given Box item path.
     *
     * <p>The path may refer to either a file or folder. Collaborations on the
     * resolved item are mapped to allow/deny lists in the standard ACLProvider
     * contract.
     */
    private ConnectionActionResponse performGetAcl(String path)
    {
        // Try to resolve as file first; fall back to folder.
        final List<String> allowUsers = new ArrayList<>();
        final List<String> allowGroups = new ArrayList<>();

        try {
            // Locate the item id — we need it for collaboration listing.
            BoxFolder targetFolder = null;

            try {
                // Box files do not expose getCollaborations(); we list collaborations on the
                // parent folder instead, as file-level collaborations are inherited.
                final BoxFile boxFile = resolveFile(path);
                final BoxFile.Info fileInfo = boxFile.getInfo("parent");
                final BoxItem.Info parent = fileInfo.getParent();
                if (parent != null) {
                    targetFolder = new BoxFolder(api, parent.getID());
                }
            }
            catch (IllegalArgumentException e) {
                // Not a file — try as a folder.
                targetFolder = resolveFolder(path);
            }

            if (targetFolder != null) {
                for (final BoxCollaboration.Info collabInfo : targetFolder.getCollaborations()) {
                    final BoxCollaborator.Info collaborator = collabInfo.getAccessibleBy();
                    if (collaborator == null) {
                        continue;
                    }
                    final String identity = collaborator.getLogin() != null ? collaborator.getLogin() : collaborator.getName();
                    if (identity == null) {
                        continue;
                    }
                    if (collaborator.getType() == BoxCollaborator.CollaboratorType.GROUP) {
                        allowGroups.add(identity);
                    } else {
                        allowUsers.add(identity);
                    }
                }
            }
        }
        catch (Exception e) {
            LOGGER.warn("Could not retrieve collaborations for path '" + path + "': " + e.getMessage(), e);
        }

        return buildAclResponse(path, allowUsers, allowGroups);
    }

    /**
     * Builds the standardised ACL response (matches the ACLProvider contract).
     */
    private static ConnectionActionResponse buildAclResponse(String path, List<String> allowUsers, List<String> allowGroups)
    {
        final ConnectionActionResponse response = new ConnectionActionResponse();
        response.put("path", path);

        final Map<String, Object> allow = new LinkedHashMap<>();
        allow.put("users", ImmutableList.copyOf(allowUsers));
        allow.put("groups", ImmutableList.copyOf(allowGroups));
        response.put("allow", allow);

        final Map<String, Object> deny = new LinkedHashMap<>();
        deny.put("users", ImmutableList.of());
        deny.put("groups", ImmutableList.of());
        response.put("deny", deny);

        final Map<String, Object> inheritance = new LinkedHashMap<>();
        inheritance.put("enabled", Boolean.FALSE);
        inheritance.put("parent_precedence", "parent");
        response.put("inheritance", inheritance);

        response.put("precedence", "deny");
        return response;
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
        api = null;
    }
}
