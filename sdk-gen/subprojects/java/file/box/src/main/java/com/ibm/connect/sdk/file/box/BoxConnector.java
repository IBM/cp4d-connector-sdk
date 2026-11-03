/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2026                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.box;

import static org.slf4j.LoggerFactory.getLogger;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

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

    /** Folder path (from the Box root, e.g. {@code /a/b}) to Box folder ID. */
    private final Map<String, String> folderIdCache = new ConcurrentHashMap<>();

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
     * Splits a slash-delimited path into its non-empty segments. Leading, trailing
     * and repeated slashes are ignored.
     */
    private static List<String> splitPath(String path)
    {
        final List<String> segments = new ArrayList<>();
        if (path != null) {
            for (final String segment : path.split("/")) {
                if (!segment.isEmpty()) {
                    segments.add(segment);
                }
            }
        }
        return segments;
    }

    /**
     * Finds the immediate child of {@code parent} with the given name and kind.
     * {@code getChildren} pages through the folder contents automatically.
     *
     * @return the child's Box ID, or {@code null} if there is no such child
     */
    private String findChildId(BoxFolder parent, String name, boolean folder)
    {
        for (final BoxItem.Info itemInfo : parent.getChildren("name", "type")) {
            final boolean kindMatches = folder ? itemInfo instanceof BoxFolder.Info : itemInfo instanceof BoxFile.Info;
            if (kindMatches && name.equals(itemInfo.getName())) {
                return itemInfo.getID();
            }
        }
        return null;
    }

    /**
     * Walks the given folder segments down from the Box root. Folder IDs resolved
     * along the way are cached for the lifetime of this connector, so repeated
     * operations under the same folder do not re-list every ancestor.
     *
     * @param segments
     *            folder names below the root
     * @param create
     *            whether to create missing folders
     * @return the resolved folder, or {@code null} if a folder is missing and
     *         {@code create} is false
     */
    private BoxFolder walkFolders(List<String> segments, boolean create)
    {
        BoxFolder current = BoxFolder.getRootFolder(api);
        final StringBuilder key = new StringBuilder();
        for (final String segment : segments) {
            key.append('/').append(segment);
            final String cachedId = folderIdCache.get(key.toString());
            if (cachedId != null) {
                current = new BoxFolder(api, cachedId);
                continue;
            }
            final String childId = findChildId(current, segment, true);
            if (childId != null) {
                current = new BoxFolder(api, childId);
            } else if (create) {
                current = current.createFolder(segment).getResource();
            } else {
                return null;
            }
            folderIdCache.put(key.toString(), current.getID());
        }
        return current;
    }

    /**
     * Resolves a slash-delimited file path to the corresponding {@link BoxFile}.
     *
     * <p>
     * Leading slashes are ignored. Each intermediate segment is resolved as a
     * sub-folder; the last segment is matched as a file.
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
        final List<String> segments = splitPath(filePath);
        if (segments.isEmpty()) {
            throw new IllegalArgumentException(BoxMsgs.FILE_NOT_FOUND.format(filePath));
        }
        final BoxFolder folder = walkFolders(segments.subList(0, segments.size() - 1), false);
        final String fileId = folder == null ? null : findChildId(folder, segments.get(segments.size() - 1), false);
        if (fileId == null) {
            throw new IllegalArgumentException(BoxMsgs.FILE_NOT_FOUND.format(filePath));
        }
        return new BoxFile(api, fileId);
    }

    /**
     * Resolves a slash-delimited folder path to the corresponding {@link BoxFolder}.
     *
     * @param folderPath
     *            slash-delimited path (e.g. {@code "/folder"} or {@code ""} for root)
     * @return the resolved BoxFolder
     * @throws IllegalArgumentException
     *             if the folder is not found
     */
    private BoxFolder resolveFolder(String folderPath)
    {
        final BoxFolder folder = walkFolders(splitPath(folderPath), false);
        if (folder == null) {
            throw new IllegalArgumentException(BoxMsgs.FOLDER_NOT_FOUND.format(folderPath));
        }
        return folder;
    }

    /**
     * Deletes the files and (recursively) folders directly under {@code folderPath}
     * whose names satisfy {@code nameFilter}. Used to clean up test data without
     * touching other content of a shared folder. Does nothing if the folder does
     * not exist.
     *
     * @param folderPath
     *            slash-delimited path of the parent folder
     * @param nameFilter
     *            selects the child names to delete
     * @return the number of items deleted
     */
    int deleteChildren(String folderPath, Predicate<String> nameFilter)
    {
        final BoxFolder folder = walkFolders(splitPath(folderPath), false);
        if (folder == null) {
            return 0;
        }
        int deleted = 0;
        for (final BoxItem.Info itemInfo : folder.getChildren("name", "type")) {
            if (!nameFilter.test(itemInfo.getName())) {
                continue;
            }
            if (itemInfo instanceof BoxFolder.Info) {
                new BoxFolder(api, itemInfo.getID()).delete(true);
                folderIdCache.clear();
            } else {
                new BoxFile(api, itemInfo.getID()).delete();
            }
            deleted++;
        }
        return deleted;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Opens an InputStream over the content of a Box file identified by path.
     * The download runs in a background thread; if it fails, the returned stream
     * throws an {@link IOException} on reaching end-of-stream.
     *
     * @param filePath
     *            slash-delimited path to the file
     * @return an InputStream over the file content
     * @throws Exception
     */
    @Override
    protected InputStream openFile(String filePath) throws Exception
    {
        final BoxFile boxFile = resolveFile(filePath);
        final PipedOutputStream pipeOut = new PipedOutputStream();
        final PipedInputStream pipeIn = new PipedInputStream(pipeOut, 65536);
        final AtomicReference<Throwable> downloadFailure = new AtomicReference<>();

        final Thread downloadThread = new Thread(() -> {
            try {
                boxFile.download(pipeOut);
            }
            catch (Exception e) {
                // Record the failure so the reader sees an error instead of a silently truncated file.
                downloadFailure.set(e);
                LOGGER.warn("Box download failed for '" + filePath + "': " + e.getMessage(), e);
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

        return new FilterInputStream(pipeIn) {
            @Override
            public int read() throws IOException
            {
                final int b = super.read();
                if (b == -1) {
                    checkDownload();
                }
                return b;
            }

            @Override
            public int read(byte[] buf, int off, int len) throws IOException
            {
                final int n = super.read(buf, off, len);
                if (n == -1) {
                    checkDownload();
                }
                return n;
            }

            private void checkDownload() throws IOException
            {
                final Throwable failure = downloadFailure.get();
                if (failure != null) {
                    throw new IOException("Download of Box file '" + filePath + "' failed: " + failure.getMessage(), failure);
                }
            }
        };
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

        final BoxFolder folder;
        try {
            folder = resolveFolder(path);
        }
        catch (IllegalArgumentException folderNotFound) {
            // The path may identify a single file: describe it, including its detected
            // format and schema, so the framework can complete the asset descriptor.
            try {
                return Collections.singletonList(describeFile(path));
            }
            catch (IllegalArgumentException fileNotFound) {
                throw folderNotFound;
            }
        }
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
            final String trimmedPath = path == null ? "" : path.replaceAll("/+$", "");
            final String normalized = trimmedPath.isEmpty() ? "" : (trimmedPath.startsWith("/") ? trimmedPath : "/" + trimmedPath);
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

    /**
     * Builds a fully described file descriptor (size, MIME type, file format,
     * delimited-file properties and fields) for a single file path.
     */
    private CustomFlightAssetDescriptor describeFile(String path) throws Exception
    {
        final BoxFile boxFile = resolveFile(path);
        final String assetPath = path.startsWith("/") ? path : "/" + path;
        final DiscoveredAssetDetails details = new DiscoveredAssetDetails();
        details.put("file_size", boxFile.getInfo("size").getSize());
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        interactionProperties.put("file_name", assetPath);
        final CustomFlightAssetDescriptor asset = new CustomFlightAssetDescriptor()
                .name(assetPath.substring(assetPath.lastIndexOf('/') + 1))
                .path(assetPath)
                .assetType(fileAssetType())
                .details(details)
                .interactionProperties(interactionProperties);
        addFileDetails(asset, assetPath);
        return asset;
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
        final List<String> segments = splitPath(filePath);
        if (segments.isEmpty()) {
            throw new IllegalArgumentException(FileMsgs.INVALID_PATH.format(filePath));
        }
        final String targetFileName = segments.get(segments.size() - 1);

        // Resolve (or create) all intermediate folder segments.
        final BoxFolder currentFolder = walkFolders(segments.subList(0, segments.size() - 1), true);

        // Check whether the file already exists in the resolved folder.
        final String existingId = findChildId(currentFolder, targetFileName, false);
        final BoxFile existingFile = existingId == null ? null : new BoxFile(api, existingId);

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
     * <p>The path may refer to either a file or folder. Box does not expose
     * collaborations on individual files, so for a file the collaborations of its
     * parent folder are returned; for a folder, its own collaborations are returned.
     * Access inherited from further up the hierarchy (and enterprise-wide or shared
     * link access) is not included. Collaborators are reported as allowed identities
     * only: Box has no deny rules, so the deny lists are always empty and
     * {@code inheritance.enabled} is always {@code false}.
     */
    private ConnectionActionResponse performGetAcl(String path) throws Exception
    {
        final List<String> allowUsers = new ArrayList<>();
        final List<String> allowGroups = new ArrayList<>();

        // Box files do not expose getCollaborations(); we list collaborations on the
        // parent folder instead, as file-level collaborations are inherited. Failures
        // are propagated: an empty ACL returned on error would be indistinguishable
        // from "nobody has access" for ACL consumers.
        BoxFolder targetFolder;
        try {
            final BoxFile.Info fileInfo = resolveFile(path).getInfo("parent");
            final BoxItem.Info parent = fileInfo.getParent();
            targetFolder = parent != null ? new BoxFolder(api, parent.getID()) : null;
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
        folderIdCache.clear();
    }
}
